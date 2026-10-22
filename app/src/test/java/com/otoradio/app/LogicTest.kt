package com.otoradio.app

import com.otoradio.app.audio.EqConfig
import com.otoradio.app.data.local.EqPreset
import com.otoradio.app.data.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class LogicTest {

    @Test
    fun stationParsedFromCatalogueJson() {
        val json = JSONObject(
            """{"id":18787,"name":"103.4 Radio DFM","stream_url":"https://i.klikhost.com/9000/stream",
               "stream_url_alt":["http://alt.example/stream"],"logo_file":"radio1034dfm.png",
               "frequency":"103.4 FM","city":"Jakarta","country":"Indonesia",
               "website":"https://www.1034dfm.com/","geoblocked":true,"genre":null,"source":"radiofmapp-api-v2"}"""
        )
        val st = Station.fromAssetJson(json)!!
        assertEquals("103.4 Radio DFM", st.name)
        assertEquals(2, st.allUrls.size)
        assertEquals("103.4 FM · Jakarta", st.subtitle)
        assertTrue(st.geoblocked)
    }

    @Test
    fun stationWithoutNameOrUrlIsSkipped() {
        assertNull(Station.fromAssetJson(JSONObject("""{"name":"","stream_url":"http://x"}""")))
        assertNull(Station.fromAssetJson(JSONObject("""{"name":"X","stream_url":""}""")))
    }

    @Test
    fun hlsDetection() {
        assertTrue(Station.isHls("https://x/y/stream.m3u8?token=1"))
        assertTrue(!Station.isHls("https://x/y/stream.mp3"))
    }

    @Test
    fun eqConfigRoundTrip() {
        val cfg = EqConfig(
            enabled = true,
            presetId = "rock",
            gains = listOf(5f, 3f, -1f, 3f, 5f),
            bassBoost = 400,
            virtualizer = 250,
            loudness = 0,
            stationId = "18787"
        )
        val back = EqConfig.fromJson(cfg.toJson())!!
        assertEquals(cfg, back)
    }

    @Test
    fun eqConfigHandlesNullStationAndGarbage() {
        val cfg = EqConfig(gains = listOf(0f, 0f, 0f, 0f, 0f))
        val back = EqConfig.fromJson(cfg.toJson())!!
        assertNull(back.stationId)
        assertNull(EqConfig.fromJson("not json"))
        assertNull(EqConfig.fromJson(null))
    }

    @Test
    fun bandLayoutMatchesOtoLabels() {
        assertEquals(listOf(60, 230, 910, 3600, 14000), EqPreset.BANDS_HZ.toList())
        assertEquals(-15f, EqPreset.MIN_DB)
        assertEquals(15f, EqPreset.MAX_DB)
    }
}
