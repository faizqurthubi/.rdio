package com.otoradio.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otoradio.app.audio.EqualizerController
import com.otoradio.app.audio.EqConfig
import com.otoradio.app.data.local.EqPreset
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    config: EqConfig,
    caps: EqualizerController.Capabilities,
    presets: List<EqPreset>,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onPreset: (EqPreset) -> Unit,
    onGain: (Int, Float) -> Unit,
    onBass: (Int) -> Unit,
    onSurround: (Int) -> Unit,
    onReset: () -> Unit,
    onSaveForStation: () -> Unit,
    stationName: String?,
    modifier: Modifier = Modifier
) {
    var presetMenu by remember { mutableStateOf(false) }
    val currentPresetName = presets.firstOrNull { it.id == config.presetId }?.name ?: "Custom"

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.BarChart, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Switch(checked = config.enabled, onCheckedChange = onEnabled)
        }

        if (caps.error != null) {
            Text(
                caps.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Preset dropdown (gaya field outline Oto)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Select preset", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currentPresetName, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { presetMenu = true }) { Text("Ubah") }
                }
            }
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                presets.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        onClick = {
                            onPreset(p)
                            presetMenu = false
                        }
                    )
                }
            }
        }

        EqCurve(
            gains = config.gains,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(72.dp)
        )

        // 5 slider vertikal + label dB di atas, Hz di bawah
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EqPreset.BANDS_HZ.forEachIndexed { index, hz ->
                val gain = config.gains.getOrElse(index) { 0f }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (gain == 0f) "0 db" else "${gain.roundToInt()} db",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    EqVerticalSlider(
                        value = gain,
                        enabled = config.enabled,
                        onValueChange = { onGain(index, it) },
                        modifier = Modifier
                            .width(52.dp)
                            .height(230.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2 knob: Bass boost & Surround sound
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Knob(
                label = "Bass boost",
                value = config.bassBoost / 10,
                enabled = config.enabled && caps.bassBoost,
                onChange = onBass
            )
            Knob(
                label = "Surround sound",
                value = config.virtualizer / 10,
                enabled = config.enabled && caps.virtualizer,
                onChange = onSurround
            )
        }

        if (caps.bands > 0) {
            Text(
                "Perangkat: ${caps.bands} band native · ${caps.nativeBandCentersHz.joinToString(", ")} Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
            androidx.compose.material3.Button(
                onClick = onSaveForStation,
                modifier = Modifier.weight(1f),
                enabled = stationName != null
            ) {
                Text(if (stationName != null) "Simpan untuk stasiun" else "Pilih stasiun")
            }
        }
        Text(
            "Berlaku untuk: ${stationName ?: "semua stasiun (global)"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun EqVerticalSlider(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
    val thumbLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val thumbH = 30.dp
    val range = EqPreset.MAX_DB - EqPreset.MIN_DB
    val fraction = ((value - EqPreset.MIN_DB) / range).coerceIn(0f, 1f)

    Box(modifier.onSizeChanged { heightPx = it.height.toFloat() }) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val top = thumbH.toPx() / 2
            val bottom = size.height - thumbH.toPx() / 2
            // track
            drawLine(
                color = Color.Gray.copy(alpha = 0.35f),
                start = Offset(cx, top),
                end = Offset(cx, bottom),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            // active
            val y = bottom - (bottom - top) * fraction
            drawLine(
                color = activeColor,
                start = Offset(cx, y.coerceAtLeast(top)),
                end = Offset(cx, bottom),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
            // thumb (kotak membulat khas Oto)
            val w = 26.dp.toPx()
            val h = thumbH.toPx()
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(cx - w / 2, y - h / 2),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.2f)
            )
            drawLine(
                color = thumbLineColor,
                start = Offset(cx - 5.dp.toPx(), y),
                end = Offset(cx + 5.dp.toPx(), y),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        val top = thumbH.toPx() / 2
                        val bottom = heightPx - thumbH.toPx() / 2
                        val f = ((bottom - change.position.y) / (bottom - top)).coerceIn(0f, 1f)
                        onValueChange(EqPreset.MIN_DB + f * range)
                    }
                }
        )
    }
}

@Composable
private fun Knob(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(112.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, drag ->
                        change.consume()
                        v = (v - drag.y / 3f).coerceIn(0f, 100f)
                        onChange(v.roundToInt())
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val accent = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = Color.Gray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = 270f * (v / 100f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                val angle = Math.toRadians((135f + 270f * (v / 100f)).toDouble())
                val r = (size.width - inset * 2) / 2
                val cx = size.width / 2 + (r * kotlin.math.cos(angle)).toFloat()
                val cy = size.height / 2 + (r * kotlin.math.sin(angle)).toFloat()
                drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(cx, cy))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${v.roundToInt()}", style = MaterialTheme.typography.titleMedium)
                Text("%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun EqCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val n = gains.size.coerceAtLeast(2)
        val step = w / (n - 1)
        val path = Path()
        val fill = Path()
        gains.forEachIndexed { i, g ->
            val x = i * step
            val y = h / 2 - (g / EqPreset.MAX_DB) * (h / 2 - 4.dp.toPx())
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(w, h)
        fill.close()
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))
        )
        drawPath(path, color = accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, h / 2),
            end = Offset(w, h / 2),
            strokeWidth = 1f
        )
        gains.forEachIndexed { i, g ->
            val x = i * step
            val y = h / 2 - (g / EqPreset.MAX_DB) * (h / 2 - 4.dp.toPx())
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}
