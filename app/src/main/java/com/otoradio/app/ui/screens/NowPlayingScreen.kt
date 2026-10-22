package com.otoradio.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otoradio.app.data.model.Station
import com.otoradio.app.playback.PlayerUiState
import com.otoradio.app.ui.components.AnimatedPlayFab
import com.otoradio.app.ui.components.rememberStationLogo
import com.otoradio.app.ui.components.stationGradient
import com.otoradio.app.ui.components.stationInitials
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    station: Station?,
    logoStore: com.otoradio.app.data.local.LogoStore? = null,
    state: PlayerUiState,
    isFavorite: Boolean,
    showIcy: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenQueue: () -> Unit,
    onShare: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleep: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var showSleep by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(30) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissThreshold = with(density) { 220.dp.toPx() }
    val sleepSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bg = station?.let { stationGradient(it) }
        ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surface))

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = offsetY.value }
            .background(bg)
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > dismissThreshold) {
                            scope.launch {
                                offsetY.animateTo(size.height.toFloat(), tween(220))
                                onClose()
                                offsetY.snapTo(0f)
                            }
                        } else {
                            scope.launch { offsetY.animateTo(0f, tween(220)) }
                        }
                    },
                    onVerticalDrag = { _, drag ->
                        scope.launch { offsetY.snapTo((offsetY.value + drag).coerceAtLeast(0f)) }
                    }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup",
                        tint = Color.White)
                }
                Text(
                    "Sedang diputar",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenEqualizer) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = "Bagikan", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
            // Artwork 1:1 (kotak, seperti layar pemutar kaset), pas di ruang tersedia
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            station?.let { stationGradient(it) }
                                ?: Brush.linearGradient(listOf(Color.DarkGray, Color.Black))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = rememberStationLogo(logoStore, station?.logoAsset)
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = station?.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                        )
                    } else {
                        Text(
                            text = station?.let { stationInitials(it) } ?: "—",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 64.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (showIcy && !state.icyTitle.isNullOrBlank()) state.icyTitle!!
                else state.stationName ?: station?.name ?: "Pilih stasiun",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (showIcy && !state.icyTitle.isNullOrBlank())
                    (station?.name ?: state.stationName ?: "")
                else (state.stationSubtitle ?: station?.subtitle ?: "Radio internet"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))
            if (state.isBuffering) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            } else {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB4AB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White)
                }
                AnimatedPlayFab(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    onClick = onToggle
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Nightlight,
                        contentDescription = "Sleep timer",
                        tint = if (state.sleepDeadline != null) MaterialTheme.colorScheme.primary else Color.White)
                }
                IconButton(onClick = { onOpenEqualizer() }) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Antrean", tint = Color.White)
                }
                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
            }
        }
    }

    if (showSleep) {
        ModalBottomSheet(onDismissRequest = { showSleep = false }, sheetState = sleepSheet) {
            Column(Modifier.padding(24.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Putar berhenti otomatis setelah durasi tertentu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 15, 30, 60, 90).forEach { m ->
                        androidx.compose.material3.FilterChip(
                            selected = sleepMinutes == m,
                            onClick = { sleepMinutes = m },
                            label = { Text("$m m") }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            onCancelSleep()
                            showSleep = false
                        }
                    ) { Text("Batalkan") }
                    androidx.compose.material3.Button(
                        onClick = {
                            onSleepTimer(sleepMinutes)
                            showSleep = false
                        }
                    ) { Text("Mulai") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
