package com.otoradio.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.otoradio.app.data.model.Station
import com.otoradio.app.ui.LibraryUiState
import com.otoradio.app.ui.components.EmptyState
import com.otoradio.app.ui.components.PillButton
import com.otoradio.app.ui.components.StationRow

@Composable
fun SearchScreen(
    state: LibraryUiState,
    loadingRemote: Boolean,
    logoStore: com.otoradio.app.data.local.LogoStore? = null,
    playingId: String?,
    onQuery: (String) -> Unit,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    onAddToPlaylist: (Station) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text(
            "Cari",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp)
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Nama stasiun, kota, frekuensi") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
        if (loadingRemote) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .height(2.dp),
                strokeWidth = 2.dp
            )
        }
        val results = state.filtered
        if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = "Tidak ada hasil",
                message = "Coba kata kunci lain atau periksa koneksi internet."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { station ->
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
    }
}

@Composable
fun FavoritesScreen(
    state: LibraryUiState,
    logoStore: com.otoradio.app.data.local.LogoStore? = null,
    playingId: String?,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text(
            "Favorit",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
        )
        if (state.favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FavoriteBorder,
                title = "Belum ada favorit",
                message = "Tekan ikon hati pada stasiun untuk menyimpannya di sini."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.favorites, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = true,
                        isPlaying = station.id == playingId,
                        onClick = { onPlay(station) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        logoStore = logoStore
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    state: LibraryUiState,
    logoStore: com.otoradio.app.data.local.LogoStore? = null,
    playingId: String?,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Riwayat", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            if (state.history.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Hapus") }
            }
        }
        if (state.history.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "Riwayat kosong",
                message = "Stasiun yang pernah Anda putar akan muncul di sini."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.history, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = state.favoriteIds.contains(station.id),
                        isPlaying = station.id == playingId,
                        onClick = { onPlay(station) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        logoStore = logoStore
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}
