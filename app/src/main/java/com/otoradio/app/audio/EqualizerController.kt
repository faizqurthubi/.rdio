package com.otoradio.app.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.otoradio.app.data.local.CatalogLoader
import com.otoradio.app.data.local.EqPreset
import com.otoradio.app.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konfigurasi EQ yang dipakai UI + service.
 * Nilai efek disimpan dalam satuan native AudioEffect (millibel / strength).
 */
data class EqConfig(
    val enabled: Boolean = false,
    val presetId: String = "flat",
    val gains: List<Float> = List(EqPreset.BANDS_HZ.size) { 0f },
    val bassBoost: Int = 0,      // 0..1000 mB
    val virtualizer: Int = 0,    // 0..1000
    val loudness: Int = 0,       // 0..2000 mB
    val stationId: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("presetId", presetId)
        put("gains", org.json.JSONArray(gains.map { it.toDouble() }))
        put("bassBoost", bassBoost)
        put("virtualizer", virtualizer)
        put("loudness", loudness)
        put("stationId", stationId ?: JSONObject.NULL)
    }.toString()

    companion object {
        fun fromJson(json: String?): EqConfig? = runCatching {
            if (json.isNullOrBlank()) return null
            val o = JSONObject(json)
            val arr = o.getJSONArray("gains")
            EqConfig(
                enabled = o.optBoolean("enabled", false),
                presetId = o.optString("presetId", "flat"),
                gains = List(arr.length()) { arr.getDouble(it).toFloat() },
                bassBoost = o.optInt("bassBoost", 0),
                virtualizer = o.optInt("virtualizer", 0),
                loudness = o.optInt("loudness", 0),
                stationId = if (o.isNull("stationId")) null else o.optString("stationId")
            )
        }.getOrNull()
    }
}

/**
 * Membungkus android.media.audiofx untuk satu audioSessionId ExoPlayer.
 * Semua efek dibungkus try/catch: banyak perangkat hanya mendukung sebagian efek.
 */
@Singleton
class EqualizerController @Inject constructor(
    private val settingsStore: SettingsStore,
    private val catalog: CatalogLoader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _config = MutableStateFlow(EqConfig())
    val config: StateFlow<EqConfig> = _config.asStateFlow()

    private val _capabilities = MutableStateFlow(Capabilities())
    val capabilities: StateFlow<Capabilities> = _capabilities.asStateFlow()

    data class Capabilities(
        val equalizer: Boolean = false,
        val bassBoost: Boolean = false,
        val virtualizer: Boolean = false,
        val loudness: Boolean = false,
        val bands: Int = 0,
        val minLevelMb: Int = 0,
        val maxLevelMb: Int = 0,
        val nativeBandCentersHz: List<Int> = emptyList(),
        val error: String? = null
    )

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedSessionId: Int = -1
    private var persistJob: kotlinx.coroutines.Job? = null

    init {
        scope.launch {
            if (!settingsStore.eqRawDefaultApplied()) {
                // versi 1.1: default = tanpa proses EQ sama sekali (audio asli stasiun)
                val raw = EqConfig()
                _config.value = raw
                settingsStore.setEqConfig(raw.toJson())
                settingsStore.setEqRawDefaultApplied()
            }
            settingsStore.eqConfigJson.collect { json ->
                EqConfig.fromJson(json)?.let { _config.value = it }
            }
        }
    }

    /** Dipanggil service setiap kali audioSessionId baru tersedia. */
    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == attachedSessionId) return
        release()
        attachedSessionId = sessionId
        // Audio biarkan RAW (tanpa efek apa pun) selama EQ tidak diminta pengguna.
        if (_config.value.enabled) createEffects(sessionId)
    }

    /** Efek hanya dibuat saat benar-benar dibutuhkan. */
    private fun createEffects(sessionId: Int) {
        var err: String? = null
        try {
            equalizer = Equalizer(0, sessionId).also { eq ->
                val range = eq.bandLevelRange // [minMb, maxMb]
                val centers = (0 until eq.numberOfBands).map { band ->
                    runCatching { eq.getCenterFreq(band.toShort()) / 1000 }.getOrDefault(0)
                }
                _capabilities.value = _capabilities.value.copy(
                    equalizer = true,
                    bands = eq.numberOfBands.toInt(),
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                    nativeBandCentersHz = centers
                )
            }
        } catch (t: Throwable) {
            err = "Equalizer tidak tersedia: ${t.message}"
            Log.w(TAG, "Equalizer unavailable", t)
        }
        bassBoost = runCatching { BassBoost(0, sessionId) }.onFailure { Log.w(TAG, "BassBoost", it) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.onFailure { Log.w(TAG, "Virtualizer", it) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(sessionId) }.onFailure { Log.w(TAG, "Loudness", it) }.getOrNull()

        _capabilities.value = _capabilities.value.copy(
            bassBoost = bassBoost != null,
            virtualizer = virtualizer != null,
            loudness = loudness != null,
            error = err
        )
        applyToEffects(_config.value)
    }

    fun release() {
        runCatching { equalizer?.enabled = false; equalizer?.release() }
        runCatching { bassBoost?.enabled = false; bassBoost?.release() }
        runCatching { virtualizer?.enabled = false; virtualizer?.release() }
        runCatching { loudness?.enabled = false; loudness?.release() }
        equalizer = null; bassBoost = null; virtualizer = null; loudness = null
        attachedSessionId = -1
        _capabilities.value = _capabilities.value.copy(equalizer = false, bassBoost = false, virtualizer = false, loudness = false)
    }

    /** Terapkan konfigurasi. Efek dibuat saat pertama kali diaktifkan. */
    fun apply(cfg: EqConfig) {
        _config.value = cfg
        if (cfg.enabled && equalizer == null && attachedSessionId > 0) {
            createEffects(attachedSessionId)
        }
        applyToEffects(cfg)
        persist(cfg)
    }

    private fun persist(cfg: EqConfig) {
        persistJob?.cancel()
        persistJob = scope.launch {
            kotlinx.coroutines.delay(350)
            settingsStore.setEqConfig(cfg.toJson())
        }
    }

    private fun applyToEffects(cfg: EqConfig) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.enabled = cfg.enabled
                if (cfg.enabled) {
                    val bands = eq.numberOfBands.toInt()
                    // Map 5 UI band -> band native perangkat (perkiraan terdekat)
                    val uiHz = EqPreset.BANDS_HZ
                    for (b in 0 until bands) {
                        val centerHz = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(uiHz[b % uiHz.size])
                        val idx = uiHz.indices.minByOrNull { kotlin.math.abs(uiHz[it] - centerHz) } ?: b
                        val gainDb = cfg.gains.getOrElse(idx) { 0f }
                        val levelMb = (gainDb * 100).toInt()
                        eq.setBandLevel(b.toShort(), levelMb.coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt()).toShort())
                    }
                }
            }.onFailure { Log.w(TAG, "apply equalizer", it) }
        }
        bassBoost?.let { bb ->
            runCatching {
                bb.enabled = cfg.enabled && cfg.bassBoost > 0 && bb.strengthSupported
                bb.setStrength(cfg.bassBoost.coerceIn(0, 1000).toShort())
            }
        }
        virtualizer?.let { vz ->
            runCatching {
                vz.enabled = cfg.enabled && cfg.virtualizer > 0 && vz.strengthSupported
                vz.setStrength(cfg.virtualizer.coerceIn(0, 1000).toShort())
            }
        }
        loudness?.let { le ->
            runCatching {
                le.enabled = cfg.enabled && cfg.loudness > 0
                le.setTargetGain(cfg.loudness.coerceIn(0, 2000))
            }
        }
    }

    fun setGain(index: Int, db: Float) {
        val gains = _config.value.gains.toMutableList()
        if (index in gains.indices) {
            gains[index] = db.coerceIn(EqPreset.MIN_DB, EqPreset.MAX_DB)
            apply(_config.value.copy(gains = gains, presetId = "custom"))
        }
    }

    fun setBass(percent: Int) = apply(_config.value.copy(bassBoost = percent.coerceIn(0, 100) * 10))
    fun setVirtualizer(percent: Int) = apply(_config.value.copy(virtualizer = percent.coerceIn(0, 100) * 10))
    fun setEnabled(on: Boolean) = apply(_config.value.copy(enabled = on))

    fun applyPreset(preset: EqPreset) = apply(
        _config.value.copy(presetId = preset.id, gains = preset.gains.take(EqPreset.BANDS_HZ.size))
    )

    fun reset() = apply(EqConfig(stationId = _config.value.stationId))

    fun presets(): List<EqPreset> = catalog.presets.ifEmpty { listOf(EqPreset.FLAT) }

    companion object {
        private const val TAG = "EqualizerController"
    }
}
