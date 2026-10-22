package com.otoradio.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otoradio.app.data.model.Station
import com.otoradio.app.ui.LibraryUiState
import com.otoradio.app.ui.components.SectionHeader
import com.otoradio.app.ui.components.StationArtwork
import com.otoradio.app.ui.components.StationRow
import com.otoradio.app.ui.components.stationGradient

private enum class HomeFilter { ALL, FAVORITES, JAKARTA }

@Composable
fun HomeScreen(
    state: LibraryUiState,
    logoStore: com.otoradio.app.data.local.LogoStore? = null,
    playingId: String?,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    onAddToPlaylist: (Station) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    val items = remember(state, filter) {
        when (filter) {
            HomeFilter.ALL -> state.stations
            HomeFilter.FAVORITES -> state.favorites
            HomeFilter.JAKARTA -> state.stations.filter { it.city?.contains("Jakarta", true) == true }
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Radio", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                SearchPill(onClick = onSearchClick)
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == HomeFilter.ALL,
                            onClick = { filter = HomeFilter.ALL },
                            label = { Text("Semua") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == HomeFilter.FAVORITES,
                            onClick = { filter = HomeFilter.FAVORITES },
                            label = { Text("Favorit") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == HomeFilter.JAKARTA,
                            onClick = { filter = HomeFilter.JAKARTA },
                            label = { Text("Jakarta") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onHistoryClick,
                            leadingIcon = {
                                Icon(Icons.Outlined.History, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            },
                            label = { Text("Riwayat") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.history.isNotEmpty() && filter == HomeFilter.ALL) {
            item { SectionHeader("Baru diputar") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.history.take(10), key = { "h-${it.id}" }) { station ->
                        RecentCard(
                            station = station,
                            isPlaying = station.id == playingId,
                            logoStore = logoStore,
                            onClick = { onPlay(station) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item { SectionHeader(if (filter == HomeFilter.FAVORITES) "Favorit" else "Semua stasiun") }

        items(items, key = { it.id }) { station ->
            StationRow(
                station = station,
                isFavorite = state.favoriteIds.contains(station.id),
                isPlaying = station.id == playingId,
                onClick = { onPlay(station) },
                onToggleFavorite = { onToggleFavorite(station) },
                onMore = { onAddToPlaylist(station) },
                logoStore = logoStore
            )
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                "Cari stasiun, kota, frekuensi",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentCard(
    station: Station,
    isPlaying: Boolean,
    logoStore: com.otoradio.app.data.local.LogoStore?,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(stationGradient(station)),
            contentAlignment = Alignment.Center
        ) {
            StationArtwork(station = station, size = 132, corner = 20, logoStore = logoStore)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            station.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            station.city ?: station.country,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
