package com.otoradio.app.data.local

import android.content.Context
import com.otoradio.app.data.model.Station
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled catalogue (assets/stations.json) produced in Tahap 1 and
 * assets/eq-presets.json (Tahap 1/2).
 */
@Singleton
class CatalogLoader @Inject constructor(@ApplicationContext private val context: Context) {

    val stations: List<Station> by lazy { readStations() }
    val presets: List<EqPreset> by lazy { readPresets() }

    private fun readStations(): List<Station> = runCatching {
        val text = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        parseStations(text)
    }.getOrElse { emptyList() }

    companion object {
        /** Dipisah agar bisa diuji di JVM tanpa Android Context. */
        fun parseStations(text: String): List<Station> {
            val arr = JSONObject(text).getJSONArray("stations")
            return buildList {
                for (i in 0 until arr.length()) {
                    Station.fromAssetJson(arr.getJSONObject(i))?.let(::add)
                }
            }.sortedBy { it.name.lowercase() }
        }
    }

    private fun readPresets(): List<EqPreset> = runCatching {
        val text = context.assets.open("eq-presets.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("presets")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val gains = o.getJSONArray("gains")
                add(
                    EqPreset(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        gains = List(gains.length()) { gains.getDouble(it).toFloat() },
                        builtIn = true
                    )
                )
            }
        }
    }.getOrElse { emptyList() }
}

data class EqPreset(
    val id: String,
    val name: String,
    val gains: List<Float>,
    val builtIn: Boolean = false
) {
    companion object {
        val BANDS_HZ = intArrayOf(60, 230, 910, 3600, 14000)
        const val MIN_DB = -15f
        const val MAX_DB = 15f
        val FLAT = EqPreset("flat", "Flat", List(BANDS_HZ.size) { 0f })
    }
}
