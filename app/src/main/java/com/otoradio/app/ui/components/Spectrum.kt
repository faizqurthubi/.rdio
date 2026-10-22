package com.otoradio.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Spektrum dekoratif untuk siaran langsung.
 *
 * Radio internet tidak punya durasi total, jadi garis progres yang merayap tidak
 * bermakna. Spektrum ini **bukan** FFT dari audio sebenarnya: untuk analisis
 * sungguhan dibutuhkan `android.media.audiofx.Visualizer` yang menuntut izin
 * RECORD_AUDIO — izin yang tidak pantas diminta hanya untuk hiasan. Karena itu
 * tinggi batang diturunkan dari fungsi sinus dengan fase berbeda per batang,
 * dan hanya beranimasi saat audio benar-benar berbunyi.
 */
@Composable
fun AudioSpectrum(
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 27,
    barColor: Color = Color.White,
    fullHeight: Dp = 26.dp
) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier.height(fullHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(barCount) { i ->
            // Bentuk gelombang: amplop lembut di tepi, variasi acak-tetap di tengah.
            val t = phase + i * 0.62f
            val envelope = 0.45f + 0.55f * sin(Math.PI * (i + 1) / (barCount + 1)).toFloat()
            val wave = (0.5f + 0.5f * sin(t)) * (0.55f + 0.45f * abs(sin(t * 1.7f + i * 0.3f)))
            val level = if (active) (0.12f + envelope * wave) else 0.10f + 0.04f * (i % 3)
            Box(
                Modifier
                    .width(3.dp)
                    .height(fullHeight * level.coerceIn(0.08f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = if (active) 0.9f else 0.35f))
            )
        }
    }
}

/** Titik LIVE yang berpendar saat audio berbunyi. */
@Composable
fun LiveDot(active: Boolean, color: Color = Color(0xFFFF5A5A), size: Dp = 9.dp) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastInverseEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center) {
        // Halo
        Box(
            Modifier
                .size(size * 2.2f)
                .scale(if (active) pulse else 1f)
                .alpha(if (active) (1f - pulse) * 0.45f else 0f)
                .background(color, CircleShape)
        )
        Box(
            Modifier
                .size(size)
                .alpha(if (active) pulse else 0.5f)
                .background(color, CircleShape)
        )
    }
}

private val FastInverseEasing = FastOutSlowInEasing
