package com.otoradio.app

import com.otoradio.app.data.model.Station
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Memverifikasi asset katalog yang benar-benar dikirim di APK
 * (app/src/main/assets/stations.json hasil Tahap 1) dapat diparse model aplikasi.
 */
class CatalogAssetTest {

    private fun readAsset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val f = candidates.firstOrNull { it.exists() } ?: error("asset $name tidak ditemukan")
        return f.readText()
    }

    @Test
    fun catalogueParsesEveryStation() {
        val root = JSONObject(readAsset("stations.json"))
        val arr = root.getJSONArray("stations")
        var parsed = 0
        var withAlt = 0
        val badUrls = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val st = Station.fromAssetJson(arr.getJSONObject(i))
            if (st == null) continue
            parsed++
            if (st.altUrls.isNotEmpty()) withAlt++
            if (!st.streamUrl.startsWith("http")) badUrls += st.streamUrl
            assertTrue("nama kosong", st.name.isNotBlank())
        }
        assertEquals(741, arr.length())
        assertEquals(741, parsed)
        assertTrue("harus ada stasiun dengan URL cadangan", withAlt > 200)
        assertTrue("semua URL harus http/https: $badUrls", badUrls.isEmpty())
    }

    @Test
    fun loaderParsesAssetThroughSameCodePathAsApp() {
        val stations = com.otoradio.app.data.local.CatalogLoader.parseStations(readAsset("stations.json"))
        assertEquals(741, stations.size)
        // urutan alfabetis (dipakai daftar di layar Beranda)
        assertEquals(stations.map { it.name.lowercase() }.sorted(), stations.map { it.name.lowercase() })
    }

    @Test
    fun mostStationsHaveBundledLogoFile() {
        val stations = com.otoradio.app.data.local.CatalogLoader.parseStations(readAsset("stations.json"))
        val withLogo = stations.filter { !it.logoAsset.isNullOrBlank() }
        assertTrue("minimal 600 stasiun punya logo, dapat ${withLogo.size}", withLogo.size >= 600)
        val assetsDir = listOf(File("src/main/assets"), File("app/src/main/assets"))
            .first { it.exists() }
        withLogo.take(50).forEach { st ->
            assertTrue("file logo hilang: ${st.logoAsset}", File(assetsDir, st.logoAsset!!).exists())
        }
        // favicon situs & logo Radio Browser harus terisi dari dua sumber
        val sources = withLogo.mapNotNull { it.logoSource }.toSet()
        assertTrue("harus ada dua sumber logo, dapat $sources", sources.containsAll(setOf("site-favicon", "radio-browser")))
    }

    @Test
    fun eqPresetsAssetParses() {
        val root = JSONObject(readAsset("eq-presets.json"))
        val arr = root.getJSONArray("presets")
        assertEquals(16, arr.length())
        for (i in 0 until arr.length()) {
            val gains = arr.getJSONObject(i).getJSONArray("gains")
            assertEquals("preset harus 5 band", 5, gains.length())
        }
    }
}
