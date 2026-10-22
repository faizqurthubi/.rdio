package com.otoradio.app.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Menyalurkan level spektrum audio yang SEDANG diputar ke UI.
 * Satu-satunya objek yang ditulis dari audio thread; UI hanya membaca [levels].
 */
object SpectrumBus {

    const val BANDS = 28

    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels

    @Volatile private var lastPublish = 0L

    fun publish(values: FloatArray) {
        val now = System.currentTimeMillis()
        if (now - lastPublish < 40) return      // ±25 pembaruan/detik
        lastPublish = now
        if (values.size == BANDS) _levels.value = values
    }

    fun clear() {
        _levels.value = FloatArray(BANDS)
    }
}

/**
 * AudioProcessor yang menyadap PCM tepat sebelum masuk speaker, menghitung spektrum,
 * lalu meneruskan audio apa adanya. Tidak butuh izin RECORD_AUDIO seperti
 * `android.media.audiofx.Visualizer`.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SpectrumAudioProcessor(
    private val bandCount: Int = SpectrumBus.BANDS,
    private val fftSize: Int = 1024
) : BaseAudioProcessor() {

    private val samples = FloatArray(fftSize)
    private val smoothed = FloatArray(bandCount)
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        analyze(inputBuffer.asReadOnlyBuffer())
        val size = inputBuffer.remaining()
        if (size == 0) return
        // Teruskan audio apa adanya: salin ke output buffer milik BaseAudioProcessor.
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onReset() {
        samples.fill(0f)
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
            samples[i] = sum / channels / 32768f
        }

        val raw = SpectrumMath.bands(samples, SAMPLE_RATE, bandCount)
        val out = FloatArray(bandCount)
        for (i in 0 until bandCount) {
            val v = raw[i]
            // Attack cepat, decay lambat: batang tidak berkedip tapi tetap responsif.
            smoothed[i] = if (v > smoothed[i]) smoothed[i] * 0.3f + v * 0.7f
            else smoothed[i] * 0.80f + v * 0.20f
            out[i] = smoothed[i].coerceIn(0f, 1f)
        }
        SpectrumBus.publish(out)
    }

    private companion object {
        // ExoPlayer menormalkan output ke 44,1 kHz untuk sebagian besar stream radio.
        const val SAMPLE_RATE = 44_100
    }
}
