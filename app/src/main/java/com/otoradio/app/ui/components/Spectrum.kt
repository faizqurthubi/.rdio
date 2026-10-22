package com.otoradio.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otoradio.app.audio.SpectrumBus
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * Spektrum audio sungguhan untuk siaran langsung.
 *
 * Sumber datanya [SpectrumBus] yang diisi [com.otoradio.app.audio.SpectrumAudioProcessor]
 * dari PCM yang benar-benar dikirim ke speaker. Bila belum ada sinyal (mis. audio belum
 * mulai), tampil datar. Batang memenuhi lebar layar dengan lebar sama rata.
 */
@Composable
fun AudioSpectrum(
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    barColor: Color = Color.White,
    fullHeight: Dp = 28.dp
) {
    val live by SpectrumBus.levels.collectAsState()

    // Nilai yang ditampilkan; dianimasikan menuju data nyata agar tidak berkedip.
    var shown by remember { mutableStateOf(FloatArray(barCount)) }
    var fallbackPhase by remember { mutableStateOf(0f) }

    LaunchedEffect(active) {
        while (true) {
            val target = if (live.size == barCount) live
            else if (active) {
                // Belum ada sampel audio: tampilkan riak tenang sebagai penanda "hidup".
                fallbackPhase += 0.18f
                FloatArray(barCount) { i ->
                    0.16f + 0.16f * abs(sin(fallbackPhase + i * 0.4f))
                }
            } else FloatArray(barCount)

            shown = FloatArray(barCount) { i ->
                val t = target.getOrElse(i) { 0f }
                val prev = shown.getOrElse(i) { 0f }
                if (t > prev) prev * 0.3f + t * 0.7f else prev * 0.7f + t * 0.3f
            }
            delay(50)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(fullHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(barCount) { i ->
            val level = shown.getOrElse(i) { 0f }.coerceIn(0.02f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(fullHeight * level)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = if (active) 0.95f else 0.3f))
            )
        }
    }
}

/**
 * Status siaran:
 * - buffering  → spinner berputar + "MENYAMBUNG"
 * - berbunyi   → titik merah berpendar + "LIVE"
 * - selain itu → titik abu-abu + "OFFLINE"
 */
@Composable
fun LiveStatus(
    isPlaying: Boolean,
    isBuffering: Boolean,
    activeColor: Color = Color(0xFFFF5252),
    idleColor: Color = Color(0x99FFFFFF)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            isBuffering -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer4()
                Text(
                    "MENYAMBUNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            isPlaying -> {
                LiveDot(color = activeColor)
                Spacer4()
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = activeColor)
            }
            else -> {
                Box(Modifier.size(9.dp).background(idleColor, CircleShape))
                Spacer4()
                Text(
                    "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = idleColor
                )
            }
        }
    }
}

@Composable
private fun Spacer4() {
    Box(Modifier.width(6.dp))
}

/** Titik berpendar: halo yang melebar lalu memudar. */
@Composable
fun LiveDot(color: Color = Color(0xFFFF5252), size: Dp = 9.dp) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * (1.6f + pulse * 1.2f))
                .alpha((1f - pulse) * 0.5f)
                .background(color, CircleShape)
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}
