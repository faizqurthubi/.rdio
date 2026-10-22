package com.otoradio.app

import com.otoradio.app.audio.SpectrumMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Regresi untuk bug 1.4: semua batang spektrum mentok penuh karena magnitudo FFT
 * tidak dinormalisasi (puncak ±N/4 sehingga dB melampaui batas atas).
 */
class SpectrumMathTest {

    private val sampleRate = 44_100
    private val n = 1024

    private fun sine(freq: Double, amplitude: Double = 0.5): FloatArray =
        FloatArray(n) { (amplitude * sin(2 * PI * freq * it / sampleRate)).toFloat() }

    private fun peakBand(levels: FloatArray): Int =
        levels.indices.maxByOrNull { levels[it] } ?: -1

    @Test
    fun heningMenghasilkanSemuaNol() {
        val levels = SpectrumMath.bands(FloatArray(n), sampleRate, 28)
        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun sinusTidakMentokPenuh() {
        // Amplitudo 0.5 (-6 dB) tidak boleh menghasilkan level 1.0 di semua pita.
        val levels = SpectrumMath.bands(sine(1000.0), sampleRate, 28)
        assertTrue("tidak boleh ada pita yang mentok", levels.all { it < 1.0f })
        assertTrue("pita terkuat harus jelas", levels.max() > 0.55f)
        assertTrue("pita lain harus rendah", levels.min() < 0.5f)
    }

    @Test
    fun semuaPitaTidakSamaRata() {
        val levels = SpectrumMath.bands(sine(1000.0), sampleRate, 28)
        val distinct = levels.map { (it * 20).toInt() }.distinct().size
        assertTrue("bentuk harus bervariasi, bukan blok rata", distinct >= 3)
    }

    @Test
    fun frekuensiRendahDanTinggiJatuhDiPitaBerbeda() {
        val low = peakBand(SpectrumMath.bands(sine(150.0), sampleRate, 28))
        val mid = peakBand(SpectrumMath.bands(sine(1000.0), sampleRate, 28))
        val high = peakBand(SpectrumMath.bands(sine(9000.0), sampleRate, 28))
        assertTrue("150 Hz harus di pita bawah", low <= 6)
        assertTrue("1 kHz harus di tengah", mid in 8..18)
        assertTrue("9 kHz harus di pita atas", high >= 22)
        assertTrue("urutan naik", low < mid && mid < high)
    }

    @Test
    fun amplitudoLebihBesarMenghasilkanLevelLebihTinggi() {
        val quiet = SpectrumMath.bands(sine(1000.0, 0.05), sampleRate, 28).max()
        val loud = SpectrumMath.bands(sine(1000.0, 0.9), sampleRate, 28).max()
        assertTrue("sinyal kuat > sinyal lemah", loud > quiet + 0.2f)
        assertTrue("sinyal kuat tetap < 1", loud <= 1f)
    }

    @Test
    fun jumlahPitaSesuaiPermintaan() {
        assertEquals(20, SpectrumMath.bands(sine(440.0), sampleRate, 20).size)
    }
}
