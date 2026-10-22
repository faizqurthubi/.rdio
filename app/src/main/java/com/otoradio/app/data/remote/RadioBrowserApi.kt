package com.otoradio.app.data.remote

import com.otoradio.app.data.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Katalog tambahan dari Radio Browser (open API, tanpa API key).
 * Dipakai sebagai pelengkap katalog bawaan /data/stations.json.
 * Endpoint bisa gagal/berubah: semua error ditelan dan menghasilkan list kosong.
 */
@Singleton
class RadioBrowserApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )

    suspend fun topIndonesia(limit: Int = 120): List<Station> = withContext(Dispatchers.IO) {
        val path = "/json/stations/search?limit=$limit&countrycode=ID&hidebroken=true" +
            "&order=clickcount&reverse=true"
        request(path)
    }

    suspend fun search(query: String, limit: Int = 60): List<Station> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        request("/json/stations/search?limit=$limit&hidebroken=true&order=clickcount&reverse=true&name=$q")
    }

    private fun request(path: String): List<Station> {
        for (base in servers) {
            val result = runCatching {
                val req = Request.Builder()
                    .url(base + path)
                    .header("User-Agent", "OtoRadio/1.0 (personal sideload)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<Station>()
                    val body = resp.body?.string() ?: return@use emptyList<Station>()
                    parse(JSONArray(body))
                }
            }.getOrElse { emptyList() }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun parse(arr: JSONArray): List<Station> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (url.isBlank() || name.isBlank()) continue
            add(
                Station(
                    id = "rb-" + o.optString("stationuuid", name.hashCode().toString()),
                    name = name,
                    streamUrl = url,
                    logoFile = null,
                    frequency = null,
                    city = o.optString("state").ifBlank { o.optString("country") },
                    country = o.optString("country").ifBlank { "Unknown" },
                    website = o.optString("homepage").takeIf { it.isNotBlank() },
                    genre = o.optString("tags").split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                    source = "radio-browser"
                )
            )
        }
    }
}
