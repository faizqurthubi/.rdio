package com.otoradio.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Station model. Field names mirror /data/stations.json (hasil Tahap 1).
 */
data class Station(
    val id: String,
    val name: String,
    val streamUrl: String,
    val altUrls: List<String> = emptyList(),
    val logoFile: String? = null,
    val logoAsset: String? = null,
    val logoSource: String? = null,
    val frequency: String? = null,
    val city: String? = null,
    val country: String = "Indonesia",
    val website: String? = null,
    val geoblocked: Boolean = false,
    val genre: String? = null,
    val source: String = "oto-radio"
) {
    val subtitle: String
        get() = listOfNotNull(frequency, city).joinToString(" · ").ifBlank { country }

    val allUrls: List<String>
        get() = (listOf(streamUrl) + altUrls).distinct()

    /** Deterministic accent used for the artwork tile (Oto-style fallback art). */
    val accentSeed: Int get() = (name.hashCode() * 31 + id.hashCode()) and 0x7fffffff

    companion object {
        fun fromAssetJson(json: JSONObject): Station? {
            val name = json.optString("name").ifBlank { return null }
            val url = json.optString("stream_url").ifBlank { return null }
            val alts = mutableListOf<String>()
            json.optJSONArray("stream_url_alt")?.let { arr: JSONArray ->
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { alts += it }
            }
            return Station(
                id = json.opt("id")?.toString() ?: name,
                name = name,
                streamUrl = url,
                altUrls = alts,
                logoFile = json.optString("logo_file").takeIf { it.isNotBlank() && it != "null" },
                logoAsset = json.optString("logo_asset").takeIf { it.isNotBlank() && it != "null" },
                logoSource = json.optString("logo_source").takeIf { it.isNotBlank() && it != "null" },
                frequency = json.optString("frequency").takeIf { it.isNotBlank() && it != "null" },
                city = json.optString("city").takeIf { it.isNotBlank() && it != "null" },
                country = json.optString("country").ifBlank { "Indonesia" },
                website = json.optString("website").takeIf { it.isNotBlank() && it != "null" },
                geoblocked = json.optBoolean("geoblocked", false),
                genre = json.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                source = json.optString("source").ifBlank { "oto-radio" }
            )
        }

        fun isHls(url: String): Boolean {
            // Tanpa android.net.Uri agar bisa diuji di JVM (unit test).
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return path.contains(".m3u8")
        }
    }
}

/** One line in "Riwayat". */
data class HistoryItem(val station: Station, val playedAt: Long)
