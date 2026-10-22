package com.otoradio.app

import com.otoradio.app.data.update.ReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParserTest {

    private fun releaseJson(
        tag: String,
        assetName: String = "OtoRadio-${tag.removePrefix("v")}-release.apk",
        digest: String? = "sha256:abc123",
        immutable: Boolean = true
    ): String = """
        {
          "tag_name": "$tag",
          "name": "OtoRadio $tag",
          "body": "Perbaikan kecil.",
          "published_at": "2026-10-22T09:00:00Z",
          "immutable": $immutable,
          "assets": [
            {
              "name": "SHA256SUMS",
              "size": 91,
              "digest": "sha256:def456",
              "browser_download_url": "https://github.com/x/y/releases/download/$tag/SHA256SUMS"
            },
            {
              "name": "$assetName",
              "size": 5687670,
              "digest": ${if (digest == null) "null" else "\"$digest\""},
              "browser_download_url": "https://github.com/x/y/releases/download/$tag/$assetName"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun versiBaruTerdeteksi() {
        val info = ReleaseParser.parseRelease(releaseJson("v1.3"), currentVersion = "1.2")
        assertNotNull(info)
        assertEquals("1.3", info!!.version)
        assertEquals(5687670L, info.apkSize)
        assertEquals("abc123", info.sha256)
        assertTrue(info.immutable)
        assertTrue(info.apkUrl.endsWith("OtoRadio-1.3-release.apk"))
    }

    @Test
    fun versiSamaAtauLebihLamaDiabaikan() {
        assertNull(ReleaseParser.parseRelease(releaseJson("v1.2"), currentVersion = "1.2"))
        assertNull(ReleaseParser.parseRelease(releaseJson("v1.1"), currentVersion = "1.2"))
    }

    @Test
    fun perbandinganVersiNumerikBukanLeksikal() {
        assertTrue(ReleaseParser.isNewer("1.10", "1.9"))
        assertTrue(ReleaseParser.isNewer("2.0", "1.99"))
        assertFalse(ReleaseParser.isNewer("1.2", "1.2"))
        assertFalse(ReleaseParser.isNewer("1.2", "1.2.1"))
        assertTrue(ReleaseParser.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun hanyaAssetReleaseApkYangDipakai() {
        val info = ReleaseParser.parseRelease(releaseJson("v1.3"), currentVersion = "1.0")
        assertEquals("abc123", info?.sha256)   // bukan digest SHA256SUMS
    }

    @Test
    fun tanpaAssetApkTidakMenawarkanUpdate() {
        val json = releaseJson("v1.3", assetName = "catatan.txt")
        assertNull(ReleaseParser.parseRelease(json, currentVersion = "1.2"))
    }

    @Test
    fun digestNullTetapTerbacaTapiKosong() {
        val info = ReleaseParser.parseRelease(releaseJson("v1.3", digest = null), "1.2")
        assertNotNull(info)
        assertNull(info!!.sha256)    // lapisan unduh yang akan menolaknya
    }

    @Test
    fun urlNonHttpsDitolak() {
        val json = """
            {"tag_name":"v1.3","assets":[{"name":"OtoRadio-1.3-release.apk","size":1,
             "digest":"sha256:aa","browser_download_url":"http://evil.example/x.apk"}]}
        """.trimIndent()
        assertNull(ReleaseParser.parseRelease(json, currentVersion = "1.2"))
    }

    @Test
    fun tagTanpaVJugaDikenali() {
        val info = ReleaseParser.parseRelease(releaseJson("1.4"), currentVersion = "1.2")
        assertEquals("1.4", info?.version)
    }
}
