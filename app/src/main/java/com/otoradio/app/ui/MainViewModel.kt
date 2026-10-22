package com.otoradio.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otoradio.app.audio.EqualizerController
import com.otoradio.app.data.RadioRepository
import com.otoradio.app.data.local.AppSettings
import com.otoradio.app.data.local.EqPreset
import com.otoradio.app.data.local.EqScope
import com.otoradio.app.data.local.SettingsStore
import com.otoradio.app.data.local.ThemeMode
import com.otoradio.app.data.RadioRepository.PlaylistSummary
import com.otoradio.app.data.model.Station
import com.otoradio.app.playback.PlayerConnection
import com.otoradio.app.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val stations: List<Station> = emptyList(),
    val filtered: List<Station> = emptyList(),
    val favorites: List<Station> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val history: List<Station> = emptyList(),
    val query: String = "",
    val cities: List<String> = emptyList(),
    val remoteCount: Int = 0,
    val loadingRemote: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val settingsStore: SettingsStore,
    val logoStore: com.otoradio.app.data.local.LogoStore,
    private val equalizer: EqualizerController,
    val player: PlayerConnection
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = player.state

    private val query = MutableStateFlow("")
    private val remoteCount = MutableStateFlow(0)
    private val loadingRemote = MutableStateFlow(false)

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val eqConfig = equalizer.config
    val eqCapabilities = equalizer.capabilities
    val presets: List<EqPreset> get() = equalizer.presets()

    val library: StateFlow<LibraryUiState> = combine(
        repository.favorites(),
        repository.favoriteIds(),
        repository.history(),
        query,
        remoteCount
    ) { favs, ids, hist, q, remoteN ->
        val all = repository.allStations()
        LibraryUiState(
            stations = all,
            filtered = repository.search(q),
            favorites = favs,
            favoriteIds = ids,
            history = hist,
            query = q,
            cities = repository.cities(),
            remoteCount = remoteN
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val loadingRemoteState: StateFlow<Boolean> = loadingRemote.asStateFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = repository.playlistSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Antrean yang sedang aktif: menentukan tujuan tombol next/prev (mis. daftar Favorit). */
    private val _queue = MutableStateFlow<List<Station>>(emptyList())
    val queue: StateFlow<List<Station>> = _queue.asStateFlow()

    private val _queueLabel = MutableStateFlow<String?>(null)
    val queueLabel: StateFlow<String?> = _queueLabel.asStateFlow()

    private var queueIndex: Int = -1

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    init {
        viewModelScope.launch {
            // Muat katalog Radio Browser sekali di latar (fallback bila offline: tetap pakai katalog lokal)
            loadingRemote.value = true
            val n = runCatching { repository.refreshRemote() }.getOrDefault(0)
            remoteCount.value = n
            loadingRemote.value = false
        }
    }

    fun setQuery(q: String) { query.value = q }

    /**
     * Memutar stasiun sekaligus menetapkan antrean konteks.
     * [context] = daftar asal (Favorit, playlist, hasil cari, ...) sehingga tombol
     * next/prev melanjutkan di daftar itu, bukan di seluruh katalog.
     */
    fun play(station: Station, context: List<Station>? = null, label: String? = null) {
        // Tanpa konteks eksplisit, antrean = seluruh katalog. Dengan begitu mini player
        // tahu sejak awal bahwa ada stasiun lain dan langsung menampilkan tombol next/prev.
        val list = context?.takeIf { it.isNotEmpty() }
            ?: repository.allStations().takeIf { it.isNotEmpty() }
            ?: listOf(station)
        _queue.value = list
        _queueLabel.value = label
        queueIndex = list.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        playStation(station)
    }

    private fun playStation(station: Station) {
        player.playStation(station)
        viewModelScope.launch {
            settingsStore.setLastStation(station.id)
            if (settingsStore.settings.first().eqScope == EqScope.PER_STATION) {
                val saved = repository.eqProfile(station.id)
                if (saved != null) {
                    val gains = saved.gains.split(',').mapNotNull { it.toFloatOrNull() }
                    equalizer.apply(
                        com.otoradio.app.audio.EqConfig(
                            enabled = true,
                            presetId = saved.presetId,
                            gains = if (gains.size == EqPreset.BANDS_HZ.size) gains
                            else List(EqPreset.BANDS_HZ.size) { 0f },
                            bassBoost = saved.bassBoost,
                            virtualizer = saved.virtualizer,
                            loudness = saved.loudness,
                            stationId = station.id
                        )
                    )
                }
            }
        }
    }

    fun next() {
        val q = _queue.value
        if (q.size < 2) { _snackbar.value = "Tidak ada stasiun berikutnya di daftar ini"; return }
        queueIndex = (queueIndex + 1) % q.size
        playStation(q[queueIndex])
    }

    fun previous() {
        val q = _queue.value
        if (q.size < 2) { _snackbar.value = "Tidak ada stasiun sebelumnya di daftar ini"; return }
        queueIndex = if (queueIndex - 1 < 0) q.size - 1 else queueIndex - 1
        playStation(q[queueIndex])
    }

    fun jumpTo(index: Int) {
        val q = _queue.value
        if (index in q.indices) {
            queueIndex = index
            playStation(q[index])
        }
    }

    /** Dipakai saat katalog/daftar favorit berubah agar index tetap menunjuk stasiun yang benar. */
    fun syncQueueIndex(stationId: String?) {
        if (stationId == null) return
        val i = _queue.value.indexOfFirst { it.id == stationId }
        if (i >= 0) queueIndex = i
    }

    fun toggleFavorite(station: Station) {
        viewModelScope.launch { repository.toggleFavorite(station) }
    }

    // ---------- playlist ----------
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            _snackbar.value = "Playlist \"${name.trim()}\" dibuat"
        }
    }

    /** Buat playlist lalu langsung masukkan stasiun (dipakai lembar "Tambah ke playlist"). */
    fun createPlaylistWith(name: String, station: Station) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            repository.addToPlaylist(id, station)
            _snackbar.value = "Ditambahkan ke ${name.trim()}"
        }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            _snackbar.value = "Playlist dihapus"
        }
    }

    fun addToPlaylist(playlistId: String, station: Station, playlistName: String) {
        viewModelScope.launch {
            val added = repository.addToPlaylist(playlistId, station)
            _snackbar.value = if (added) "Ditambahkan ke $playlistName" else "Sudah ada di $playlistName"
        }
    }

    fun removeFromPlaylist(playlistId: String, stationId: String) {
        viewModelScope.launch {
            repository.removeFromPlaylist(playlistId, stationId)
            _snackbar.value = "Dihapus dari playlist"
        }
    }

    fun playlistStations(id: String): kotlinx.coroutines.flow.Flow<List<Station>> =
        repository.playlistStations(id)

    fun togglePlayPause() = player.togglePlayPause()

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _snackbar.value = "Riwayat dibersihkan"
        }
    }

    fun snackbarShown() { _snackbar.value = null }

    // ---- settings ----
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(on) }
    fun setUseIcy(on: Boolean) = viewModelScope.launch { settingsStore.setUseIcy(on) }
    fun setAutoReconnect(on: Boolean) = viewModelScope.launch { settingsStore.setAutoReconnect(on) }
    fun setAutoPlayLast(on: Boolean) = viewModelScope.launch { settingsStore.setAutoPlayLast(on) }
    fun setSleepDefault(min: Int) = viewModelScope.launch { settingsStore.setSleepDefault(min) }
    fun setEqScope(scope: EqScope) = viewModelScope.launch { settingsStore.setEqScope(scope) }

    // ---- equalizer ----
    fun setEqEnabled(on: Boolean) = equalizer.setEnabled(on)
    fun setGain(index: Int, db: Float) = equalizer.setGain(index, db)
    fun setBass(percent: Int) = equalizer.setBass(percent)
    fun setSurround(percent: Int) = equalizer.setVirtualizer(percent)
    fun applyPreset(preset: EqPreset) = equalizer.applyPreset(preset)
    fun resetEq() = equalizer.reset()

    fun savePerStation(stationId: String?) {
        val cfg = equalizer.config.value.copy(stationId = stationId)
        viewModelScope.launch {
            if (stationId != null) {
                repository.saveEqProfile(
                    com.otoradio.app.data.local.EqProfileEntity(
                        stationId = stationId,
                        presetId = cfg.presetId,
                        gains = cfg.gains.joinToString(","),
                        bassBoost = cfg.bassBoost,
                        virtualizer = cfg.virtualizer,
                        loudness = cfg.loudness
                    )
                )
                _snackbar.value = "EQ disimpan untuk stasiun ini"
            }
            equalizer.apply(cfg)
        }
    }

    fun sleepTimer(minutes: Int) = player.startSleepTimer(minutes)
    fun cancelSleepTimer() = player.cancelSleepTimer()

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
