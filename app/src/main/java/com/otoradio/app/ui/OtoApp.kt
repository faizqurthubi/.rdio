package com.otoradio.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.otoradio.app.data.RadioRepository.PlaylistSummary
import com.otoradio.app.data.local.EqScope
import com.otoradio.app.data.model.Station
import com.otoradio.app.ui.components.MiniPlayer
import com.otoradio.app.ui.screens.AddToPlaylistSheet
import com.otoradio.app.ui.screens.EqualizerScreen
import com.otoradio.app.ui.screens.HomeScreen
import com.otoradio.app.ui.screens.NowPlayingScreen
import com.otoradio.app.ui.screens.PlaylistDetailScreen
import com.otoradio.app.ui.screens.PlaylistScreen
import com.otoradio.app.ui.screens.QueueSheet
import com.otoradio.app.ui.screens.SearchScreen
import com.otoradio.app.ui.screens.SettingsScreen

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Tab("home", "Radio", Icons.Rounded.Radio)
    data object Search : Tab("search", "Cari", Icons.Outlined.Search)
    data object Library : Tab("library", "Playlist", Icons.Rounded.LibraryMusic)
    data object Settings : Tab("settings", "Atur", Icons.Outlined.Settings)
}

private val tabs = listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings)

/** Layar daftar yang dibuka dari tab Playlist. */
private data class DetailRoute(val kind: String, val playlistId: String?, val title: String)

@Composable
fun OtoApp(vm: MainViewModel) {
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()
    LaunchedEffect(Unit) { updateVm.checkIfStale() }
    val library by vm.library.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val settings by vm.settings.collectAsState()
    val eqConfig by vm.eqConfig.collectAsState()
    val caps by vm.eqCapabilities.collectAsState()
    val loadingRemote by vm.loadingRemoteState.collectAsState()
    val snackbar by vm.snackbar.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val queue by vm.queue.collectAsState()
    val queueLabel by vm.queueLabel.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(Tab.Home.route) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showEq by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DetailRoute?>(null) }
    var addSheetStation by remember { mutableStateOf<Station?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    // Tombol back Android: tutup lapisan teratas dulu, jangan langsung keluar aplikasi.
    // BackHandler selalu aktif: lapisan teratas ditutup dulu; bila sudah di akar
    // aplikasi, munculkan konfirmasi agar tidak keluar karena salah tekan.
    BackHandler(enabled = true) {
        when {
            confirmExit -> confirmExit = false
            addSheetStation != null -> addSheetStation = null
            showQueue -> showQueue = false
            showEq -> showEq = false
            showNowPlaying -> showNowPlaying = false
            detail != null -> detail = null
            tab != Tab.Home.route -> tab = Tab.Home.route
            else -> confirmExit = true
        }
    }

    if (confirmExit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Keluar dari Oto Radio?") },
            text = { Text("Siaran akan berhenti.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmExit = false
                    // Keluar dari aplikasi: hentikan layanan pemutar lalu selesaikan activity.
                    (context as? androidx.activity.ComponentActivity)?.finish()
                }) { Text("Keluar") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmExit = false }) {
                    Text("Batal")
                }
            }
        )
    }

    val context = LocalContext.current
    val currentStation: Station? = remember(playerState.stationId, library.stations) {
        library.stations.firstOrNull { it.id == playerState.stationId }
            ?: library.history.firstOrNull { it.id == playerState.stationId }
            ?: library.favorites.firstOrNull { it.id == playerState.stationId }
    }

    LaunchedEffect(playerState.stationId) { vm.syncQueueIndex(playerState.stationId) }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                Column {
                    if (playerState.hasItem || currentStation != null) {
                        MiniPlayer(
                            title = playerState.stationName ?: currentStation?.name ?: "Radio",
                            subtitle = playerState.icyTitle
                                ?: playerState.stationSubtitle ?: currentStation?.subtitle ?: "LIVE",
                            station = currentStation,
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isBuffering,
                            onClick = { showNowPlaying = true },
                            onToggle = { vm.togglePlayPause() },
                            logoStore = vm.logoStore,
                            onPrev = if (queue.size > 1) vm::previous else null,
                            onNext = if (queue.size > 1) vm::next else null,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t.route && detail == null,
                                onClick = {
                                    tab = t.route
                                    detail = null
                                },
                                icon = {
                                    if (t is Tab.Settings && updateState is UpdateUiState.Available) {
                                        BadgedBox(badge = { Badge() }) {
                                            Icon(t.icon, contentDescription = t.label)
                                        }
                                    } else {
                                        Icon(t.icon, contentDescription = t.label)
                                    }
                                },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
            ) {
                val current = detail
                when {
                    current != null -> {
                        val items: List<Station> = when (current.kind) {
                            "favorites" -> library.favorites
                            "history" -> library.history
                            else -> {
                                val flow = remember(current.playlistId) {
                                    current.playlistId?.let { vm.playlistStations(it) }
                                }
                                val list by (flow?.collectAsState(initial = emptyList())
                                    ?: remember { mutableStateOf(emptyList<Station>()) })
                                list
                            }
                        }
                        PlaylistDetailScreen(
                            title = current.title,
                            stations = items,
                            logoStore = vm.logoStore,
                            playingId = playerState.stationId,
                            favoriteIds = library.favoriteIds,
                            onBack = { detail = null },
                            onPlayAll = {
                                items.firstOrNull()?.let { vm.play(it, items, current.title) }
                            },
                            onPlay = { station -> vm.play(station, items, current.title) },
                            onToggleFavorite = { vm.toggleFavorite(it) },
                            onRemove = if (current.kind == "playlist") {
                                { station ->
                                    current.playlistId?.let { vm.removeFromPlaylist(it, station.id) }
                                }
                            } else if (current.kind == "history") null else null,
                            onMore = { station -> addSheetStation = station }
                        )
                    }
                    tab == Tab.Home.route -> HomeScreen(
                        logoStore = vm.logoStore,
                        state = library,
                        playingId = playerState.stationId,
                        onSearchClick = { tab = Tab.Search.route },
                        onHistoryClick = { detail = DetailRoute("history", null, "Baru diputar") },
                        onPlay = { station -> vm.play(station, library.stations, "Semua stasiun") },
                        onToggleFavorite = { vm.toggleFavorite(it) },
                        onAddToPlaylist = { addSheetStation = it }
                    )
                    tab == Tab.Search.route -> SearchScreen(
                        logoStore = vm.logoStore,
                        state = library,
                        loadingRemote = loadingRemote,
                        playingId = playerState.stationId,
                        onQuery = vm::setQuery,
                        onPlay = { station -> vm.play(station, library.filtered, "Hasil pencarian") },
                        onToggleFavorite = { vm.toggleFavorite(it) },
                        onAddToPlaylist = { addSheetStation = it }
                    )
                    tab == Tab.Library.route -> PlaylistScreen(
                        state = library,
                        playlists = playlists,
                        logoStore = vm.logoStore,
                        playingId = playerState.stationId,
                        onOpenFavorites = { detail = DetailRoute("favorites", null, "Favorit") },
                        onOpenHistory = { detail = DetailRoute("history", null, "Baru diputar") },
                        onOpenPlaylist = { pl: PlaylistSummary ->
                            detail = DetailRoute("playlist", pl.id, pl.name)
                        },
                        onCreatePlaylist = vm::createPlaylist,
                        onRenamePlaylist = { pl, name -> vm.renamePlaylist(pl.id, name) },
                        onDeletePlaylist = { pl -> vm.deletePlaylist(pl.id) }
                    )
                    else -> SettingsScreen(
                        settings = settings,
                        onTheme = vm::setTheme,
                        onDynamic = vm::setDynamicColor,
                        onIcy = vm::setUseIcy,
                        onReconnect = vm::setAutoReconnect,
                        onAutoPlay = vm::setAutoPlayLast,
                        onSleepDefault = vm::setSleepDefault,
                        onEqScope = vm::setEqScope,
                        onOpenEq = { showEq = true },
                        stationCount = library.stations.size,
                        appVersion = updateVm.currentVersion,
                        updateState = updateState,
                        onCheckUpdate = updateVm::check,
                        onInstallUpdate = updateVm::downloadAndInstall
                    )
                }
            }
        }

        // Now Playing: transisi swipe-up ala Oto
        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(animationSpec = tween(350), initialOffsetY = { it }) + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(300), targetOffsetY = { it }) + fadeOut(tween(200))
        ) {
            NowPlayingScreen(
                station = currentStation,
                logoStore = vm.logoStore,
                state = playerState,
                isFavorite = library.favoriteIds.contains(playerState.stationId),
                showIcy = settings.useIcyTitle,
                onClose = { showNowPlaying = false },
                onToggleFavorite = { currentStation?.let { vm.toggleFavorite(it) } },
                onOpenEqualizer = { showEq = true },
                onOpenQueue = { showQueue = true },
                onShare = {
                    val st = currentStation
                    if (st != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "${st.name} — ${st.streamUrl}"
                            )
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Bagikan stasiun")
                        )
                    }
                },
                onSleepTimer = { vm.sleepTimer(it) },
                onCancelSleep = { vm.cancelSleepTimer() },
                onToggle = { vm.togglePlayPause() },
                onNext = { vm.next() },
                onPrev = { vm.previous() }
            )
        }

        // Equalizer sebagai layar penuh
        AnimatedVisibility(
            visible = showEq,
            enter = slideInVertically(animationSpec = tween(300), initialOffsetY = { it }),
            exit = slideOutVertically(animationSpec = tween(250), targetOffsetY = { it })
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    EqualizerScreen(
                        config = eqConfig,
                        caps = caps,
                        presets = vm.presets,
                        onBack = { showEq = false },
                        onEnabled = vm::setEqEnabled,
                        onPreset = vm::applyPreset,
                        onGain = vm::setGain,
                        onBass = vm::setBass,
                        onSurround = vm::setSurround,
                        onReset = vm::resetEq,
                        onSaveForStation = {
                            vm.savePerStation(
                                if (settings.eqScope == EqScope.PER_STATION) playerState.stationId else null
                            )
                        },
                        stationName = if (settings.eqScope == EqScope.PER_STATION) playerState.stationName else null
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            label = queueLabel,
            queue = queue,
            currentId = playerState.stationId,
            logoStore = vm.logoStore,
            onDismiss = { showQueue = false },
            onJump = { index ->
                vm.jumpTo(index)
                showQueue = false
            }
        )
    }

    addSheetStation?.let { station ->
        AddToPlaylistSheet(
            stationName = station.name,
            playlists = playlists,
            onDismiss = { addSheetStation = null },
            onPick = { pl ->
                vm.addToPlaylist(pl.id, station, pl.name)
                addSheetStation = null
            },
            onCreateAndAdd = { name ->
                vm.createPlaylistWith(name, station)
                addSheetStation = null
            }
        )
    }
}
