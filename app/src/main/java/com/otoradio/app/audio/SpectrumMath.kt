package com.otoradio.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Matematika spektrum, sengaja murni (tanpa kelas Android) supaya bisa diuji unit test.
 *
 * Catatan kalibrasi — ini yang salah di 1.4 dan membuat semua batang mentok penuh:
 * FFT dari sinus amplitudo `A` menghasilkan puncak sekitar `A * N / 4` (N = ukuran FFT).
 * Kalau nilai mentah itu langsung diubah ke dB, hasilnya ±+48 dB dan seluruh pita
 * mentok di batas atas. Karena itu magnitudo dinormalisasi dulu (`mag * 4 / N`) sehingga
 * berskala 0..1 seperti amplitudo audio, baru dipetakan ke dB.
 */
object SpectrumMath {

    const val DB_FLOOR = -58f
    const val DB_CEIL = 0f
    private const val SILENCE = 1e-7f

    fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { 0.5f - 0.5f * cos(2.0 * PI * it / (size - 1)).toFloat() }

    /**
     * @param samples PCM mono (sudah dinormalisasi ke -1..1), panjang = ukuran FFT
     * @return level per pita, masing-masing 0..1, terdistribusi logaritmis 60 Hz–14 kHz
     */
    fun bands(
        samples: FloatArray,
        sampleRate: Int,
        bandCount: Int,
        loHz: Float = 60f,
        hiHz: Float = 14_000f
    ): FloatArray {
        val n = samples.size
        val re = FloatArray(n)
        val im = FloatArray(n)
        val win = hannWindow(n)
        for (i in 0 until n) {
            re[i] = samples[i] * win[i]
            im[i] = 0f
        }
        fft(re, im)

        val binHz = sampleRate.toFloat() / n
        val hi = min(hiHz, sampleRate / 2f - binHz)
        val ratio = ln(hi / loHz)
        val out = FloatArray(bandCount)
        val norm = 4f / n          // skala magnitudo -> amplitudo 0..1

        for (band in 0 until bandCount) {
            val f0 = loHz * kotlin.math.exp(ratio * band / bandCount)
            val f1 = loHz * kotlin.math.exp(ratio * (band + 1) / bandCount)
            val b0 = max(1, (f0 / binHz).roundToInt())
            val b1 = max(b0 + 1, (f1 / binHz).roundToInt()).coerceAtMost(n / 2 - 1)

            var peak = 0f
            for (b in b0 until b1) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b]) * norm
                if (mag > peak) peak = mag
            }
            val db = 20f * log10(peak.coerceAtLeast(SILENCE))
            out[band] = ((db - DB_FLOOR) / (DB_CEIL - DB_FLOOR)).coerceIn(0f, 1f)
        }
        return out
    }

    /** FFT radix-2 in-place; panjang harus pangkat dua. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val i0 = i + k
                    val i1 = i0 + len / 2
                    val uRe = re[i0]
                    val uIm = im[i0]
                    val vRe = re[i1] * curRe - im[i1] * curIm
                    val vIm = re[i1] * curIm + im[i1] * curRe
                    re[i0] = uRe + vRe
                    im[i0] = uIm + vIm
                    re[i1] = uRe - vRe
                    im[i1] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
