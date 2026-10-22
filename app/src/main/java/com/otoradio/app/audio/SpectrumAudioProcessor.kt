package com.otoradio.app.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Menyalurkan level spektrum audio yang SEDANG diputar ke UI.
 *
 * Satu-satunya objek yang ditulis dari audio thread; UI hanya membaca [levels].
 */
object SpectrumBus {

    private const val BANDS = 28

    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels

    @Volatile private var lastPublish = 0L
    @Volatile var hasSignal: Boolean = false
        private set

    /** Dipanggil dari audio thread; dibatasi ±25 kali/detik agar UI tidak kebanjiran. */
    fun publish(values: FloatArray) {
        val now = System.currentTimeMillis()
        if (now - lastPublish < 40) return
        lastPublish = now
        if (values.size == BANDS) {
            if (values.any { it > 0.02f }) hasSignal = true
            _levels.value = values
        }
    }

    /** Saat pemutaran berhenti: batang turun kembali ke nol. */
    fun clear() {
        _levels.value = FloatArray(BANDS)
        hasSignal = false
    }
}

/**
 * AudioProcessor yang "mencuri dengar" PCM yang lewat di pipeline ExoPlayer,
 * menghitung magnitudo per pita frekuensi, lalu meneruskannya apa adanya ke output.
 *
 * Kenapa begini, bukan `android.media.audiofx.Visualizer`: Visualizer menuntut izin
 * RECORD_AUDIO yang akan tampil sebagai "merekam audio" di dialog izin. Penyadapan
 * internal ini tidak butuh izin apa pun dan tetap akurat karena mengambil sampel
 * yang benar-benar dikirim ke speaker.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SpectrumAudioProcessor(
    private val bandCount: Int = 28,
    private val fftSize: Int = 1024
) : BaseAudioProcessor() {

    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val window = FloatArray(fftSize) {
        0.5f - 0.5f * cos(2.0 * PI * it / (fftSize - 1)).toFloat()   // Hann
    }
    private val smoothed = FloatArray(bandCount)
    private var sampleRate = 44100
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Analisis dulu lewat salinan read-only (posisi buffer asli tidak tersentuh).
        analyze(inputBuffer.asReadOnlyBuffer())
        val size = inputBuffer.remaining()
        if (size == 0) return
        // Teruskan audio apa adanya: salin ke output buffer milik BaseAudioProcessor.
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onReset() {
        smoothed.fill(0f)
        SpectrumBus.clear()
    }

    private fun analyze(buffer: ByteBuffer) {
        val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = shorts.remaining() / channels
        if (frames < fftSize / 2) return

        val start = shorts.position()
        for (i in 0 until fftSize) {
            val frame = (i * frames / fftSize).coerceAtMost(frames - 1)
            var sum = 0f
            for (c in 0 until channels) {
                sum += shorts.get(start + frame * channels + c).toFloat()
            }
            real[i] = (sum / channels / 32768f) * window[i]
            imag[i] = 0f
        }

        fft()

        val binHz = sampleRate.toFloat() / fftSize
        val lo = 60f
        val hi = min(14000f, sampleRate / 2f - binHz)
        val ratio = ln(hi / lo)
        val out = FloatArray(bandCount)

        for (band in 0 until bandCount) {
            val f0 = lo * kotlin.math.exp(ratio * band / bandCount)
            val f1 = lo * kotlin.math.exp(ratio * (band + 1) / bandCount)
            val b0 = max(1, (f0 / binHz).roundToInt())
            val b1 = max(b0 + 1, (f1 / binHz).roundToInt()).coerceAtMost(fftSize / 2 - 1)
            var peak = 0f
            for (b in b0 until b1) {
                val re = real[b]
                val im = imag[b]
                val mag = sqrt(re * re + im * im)
                if (mag > peak) peak = mag
            }
            // Skala dB lalu dipetakan ke 0..1 (lantai -70 dB)
            val db = 20f * log10(peak.coerceAtLeast(1e-7f))
            val norm = ((db + 70f) / 70f).coerceIn(0f, 1f)
            // Attack cepat, decay lambat supaya batang tidak berkedip
            smoothed[band] = if (norm > smoothed[band])
                smoothed[band] * 0.35f + norm * 0.65f
            else
                smoothed[band] * 0.82f + norm * 0.18f
            out[band] = smoothed[band]
        }
        SpectrumBus.publish(out)
    }

    /** FFT radix-2 in-place (panjang harus pangkat dua). */
    private fun fft() {
        val n = fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = kotlin.math.sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val i0 = i + k
                    val i1 = i0 + len / 2
                    val uRe = real[i0]
                    val uIm = imag[i0]
                    val vRe = real[i1] * curRe - imag[i1] * curIm
                    val vIm = real[i1] * curIm + imag[i1] * curRe
                    real[i0] = uRe + vRe
                    imag[i0] = uIm + vIm
                    real[i1] = uRe - vRe
                    imag[i1] = uIm - vIm
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
