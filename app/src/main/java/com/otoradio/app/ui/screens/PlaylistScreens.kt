package com.otoradio.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otoradio.app.data.RadioRepository.PlaylistSummary
import com.otoradio.app.data.local.LogoStore
import com.otoradio.app.data.model.Station
import com.otoradio.app.ui.LibraryUiState
import com.otoradio.app.ui.components.EmptyState
import com.otoradio.app.ui.components.SectionHeader
import com.otoradio.app.ui.components.StationRow

/**
 * Tab "Playlist": Favorit dan Riwayat tampil sebagai playlist bawaan,
 * di bawahnya playlist buatan pengguna.
 */
@Composable
fun PlaylistScreen(
    state: LibraryUiState,
    playlists: List<PlaylistSummary>,
    logoStore: LogoStore?,
    playingId: String?,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (PlaylistSummary, String) -> Unit,
    onDeletePlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }

    Column(modifier.fillMaxSize()) {
        Text(
            "Playlist",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CollectionRow(
                    icon = Icons.Rounded.Favorite,
                    title = "Favorit",
                    count = state.favorites.size,
                    onClick = onOpenFavorites
                )
            }
            item {
                CollectionRow(
                    icon = Icons.Rounded.History,
                    title = "Baru diputar",
                    count = state.history.size,
                    onClick = onOpenHistory
                )
            }
            item { SectionHeader("Playlist saya") }
            items(playlists, key = { it.id }) { pl ->
                CollectionRow(
                    icon = Icons.Rounded.QueueMusic,
                    title = pl.name,
                    count = pl.count,
                    onClick = { onOpenPlaylist(pl) },
                    trailing = {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi playlist")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Ganti nama") },
                                    onClick = { menu = false; renameTarget = pl }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hapus playlist") },
                                    onClick = { menu = false; deleteTarget = pl }
                                )
                            }
                        }
                    }
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Buat playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Playlist baru",
            initial = "",
            confirmLabel = "Buat",
            onDismiss = { showCreate = false },
            onConfirm = { showCreate = false; onCreatePlaylist(it) }
        )
    }
    renameTarget?.let { pl ->
        NameDialog(
            title = "Ganti nama playlist",
            initial = pl.name,
            confirmLabel = "Simpan",
            onDismiss = { renameTarget = null },
            onConfirm = { renameTarget = null; onRenamePlaylist(pl, it) }
        )
    }
    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus playlist ini?") },
            text = { Text("Stasiun di dalamnya tidak dihapus dari favorit.") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDeletePlaylist(pl) }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun CollectionRow(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$count stasiun",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Isi satu playlist / Favorit / Riwayat. */
@Composable
fun PlaylistDetailScreen(
    title: String,
    stations: List<Station>,
    logoStore: LogoStore?,
    playingId: String?,
    favoriteIds: Set<String>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    onRemove: ((Station) -> Unit)?,
    onMore: ((Station) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Kembali")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (stations.isNotEmpty()) {
                IconButton(onClick = onPlayAll) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Putar semua")
                }
            }
        }
        if (stations.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Masih kosong",
                message = "Tambahkan stasiun lewat menu titik tiga di daftar stasiun."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(stations, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = favoriteIds.contains(station.id),
                        isPlaying = station.id == playingId,
                        onClick = { onPlay(station) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        onMore = if (onMore != null || onRemove != null) {
                            { onMore?.invoke(station) ?: onRemove?.invoke(station) }
                        } else null,
                        logoStore = logoStore
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nama") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Lembar bawah: pilih playlist tujuan saat menambah stasiun. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    stationName: String,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onPick: (PlaylistSummary) -> Unit,
    onCreateAndAdd: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var create by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Tambah ke playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Text(
                stationName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { create = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("Playlist baru", color = MaterialTheme.colorScheme.primary)
            }
            playlists.forEach { pl ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(pl) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(pl.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pl.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (playlists.isEmpty()) {
                Text(
                    "Belum ada playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
    if (create) {
        NameDialog(
            title = "Playlist baru",
            initial = "",
            confirmLabel = "Buat & tambah",
            onDismiss = { create = false },
            onConfirm = { create = false; onCreateAndAdd(it) }
        )
    }
}

/** Antrean putar saat ini (ikon di Now Playing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    label: String?,
    queue: List<Station>,
    currentId: String?,
    logoStore: LogoStore?,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Antrean",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (queue.isEmpty()) {
                Text(
                    "Belum ada antrean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(queue.size) { i ->
                        val s = queue[i]
                        StationRow(
                            station = s,
                            isFavorite = false,
                            isPlaying = s.id == currentId,
                            onClick = { onJump(i) },
                            onToggleFavorite = {},
                            showFavorite = false,
                            logoStore = logoStore
                        )
                    }
                }
            }
        }
    }
}
