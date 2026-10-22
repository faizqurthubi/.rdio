package com.otoradio.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.otoradio.app.data.model.Station
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasItem: Boolean = false,
    val stationId: String? = null,
    val stationName: String? = null,
    val stationSubtitle: String? = null,
    val icyTitle: String? = null,
    val error: String? = null,
    val reconnectAttempt: Int = 0,
    val sleepDeadline: Long? = null
)

@Singleton
class PlayerConnection @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private var pendingStation: Station? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        scope.launch {
            PlaybackBus.icyTitle.collect { title ->
                _state.value = _state.value.copy(icyTitle = title)
            }
        }
        scope.launch {
            PlaybackBus.lastError.collect { err -> _state.value = _state.value.copy(error = err) }
        }
        scope.launch {
            PlaybackBus.reconnectAttempt.collect { n -> _state.value = _state.value.copy(reconnectAttempt = n) }
        }
        scope.launch {
            PlaybackBus.sleepDeadline.collect { d -> _state.value = _state.value.copy(sleepDeadline = d) }
        }
        scope.launch {
            PlaybackBus.currentStation.collect { st ->
                if (st != null) {
                    _state.value = _state.value.copy(
                        stationId = st.id,
                        stationName = st.name,
                        stationSubtitle = st.subtitle,
                        hasItem = true
                    )
                }
            }
        }
        connect()
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, RadioPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get().also { c ->
                    c.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            syncFromController()
                        }
                    })
                    syncFromController()
                    _state.value = _state.value.copy(connected = true)
                    pendingStation?.let { station -> playStation(station) }
                    pendingStation = null
                }
            }.onFailure {
                _state.value = _state.value.copy(error = "Gagal terhubung ke pemutar: ${it.message}")
            }
            connecting = false
        }, MoreExecutors.directExecutor())
    }

    private fun syncFromController() {
        val c = controller ?: return
        val meta = c.currentMediaItem?.mediaMetadata
        _state.value = _state.value.copy(
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            hasItem = c.currentMediaItem != null,
            stationId = _state.value.stationId ?: c.currentMediaItem?.mediaId,
            stationName = meta?.title?.toString() ?: _state.value.stationName,
            stationSubtitle = meta?.artist?.toString() ?: _state.value.stationSubtitle
        )
    }

    fun playStation(station: Station) {
        val c = controller
        if (c == null) {
            pendingStation = station
            connect()
            return
        }
        PlaybackBus.currentStation.value = station
        PlaybackBus.icyTitle.value = null
        PlaybackBus.lastError.value = null
        _state.value = _state.value.copy(
            stationId = station.id,
            stationName = station.name,
            stationSubtitle = station.subtitle,
            hasItem = true,
            error = null
        )
        val args = Bundle().apply {
            putString("id", station.id)
            putString("name", station.name)
            putString("url", station.streamUrl)
            putStringArrayList("alts", ArrayList(station.altUrls))
            putString("frequency", station.frequency)
            putString("city", station.city)
            putString("website", station.website)
        }
        val sent = runCatching {
            c.sendCustomCommand(SessionCommand(RadioPlayerService.ACTION_PLAY_STATION, Bundle.EMPTY), args)
        }.isSuccess
        if (!sent) {
            // Fallback: mainkan langsung lewat controller bila custom command ditolak.
            c.setMediaItem(
                MediaItem.Builder().setUri(station.streamUrl).setMediaId(station.id).build()
            )
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun stop() {
        controller?.stop()
    }

    fun seekToNextFallback() {
        val st = PlaybackBus.currentStation.value ?: return
        playStation(st)
    }

    fun startSleepTimer(minutes: Int) {
        val c = controller ?: return
        val args = Bundle().apply { putInt("minutes", minutes) }
        runCatching {
            c.sendCustomCommand(SessionCommand(RadioPlayerService.ACTION_SLEEP_TIMER, Bundle.EMPTY), args)
        }.onSuccess { PlaybackBus.sleepDeadline.value = System.currentTimeMillis() + minutes * 60_000L }
    }

    fun cancelSleepTimer() {
        val c = controller ?: return
        runCatching {
            c.sendCustomCommand(SessionCommand(RadioPlayerService.ACTION_CANCEL_SLEEP, Bundle.EMPTY), Bundle.EMPTY)
        }
        PlaybackBus.sleepDeadline.value = null
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
