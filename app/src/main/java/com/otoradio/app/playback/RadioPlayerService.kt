package com.otoradio.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.otoradio.app.MainActivity
import com.otoradio.app.audio.EqualizerController
import com.otoradio.app.data.RadioRepository
import com.otoradio.app.data.local.SettingsStore
import com.otoradio.app.data.model.Station
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class RadioPlayerService : MediaSessionService() {

    @Inject lateinit var equalizer: EqualizerController
    @Inject lateinit var repository: RadioRepository
    @Inject lateinit var logoStore: com.otoradio.app.data.local.LogoStore
    @Inject lateinit var settingsStore: SettingsStore

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentStation: Station? = null
    private var urlIndex = 0
    private var softRetry = 0
    private var reconnectJob: Job? = null
    private var sleepJob: Job? = null
    private var autoReconnect = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS, MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                Log.d(TAG, "audioSessionId=$audioSessionId")
                equalizer.attach(audioSessionId)
            }

            override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo && !entry.title.isNullOrBlank()) {
                        PlaybackBus.icyTitle.value = entry.title
                        PlaybackBus.icyStation.value = entry.url
                    }
                }
            }

        })

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "player error ${error.errorCode} ${error.message}")
                PlaybackBus.lastError.value = describe(error)
                handlePlaybackFailure()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    softRetry = 0
                    PlaybackBus.lastError.value = null
                    PlaybackBus.reconnectAttempt.value = 0
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) PlaybackBus.lastError.value = null
            }
        })

        player = exo
        runCatching {
            setMediaNotificationProvider(
                androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build()
                    .also { it.setSmallIcon(com.otoradio.app.R.drawable.ic_stat_radio) }
            )
        }
        mediaSession = MediaSession.Builder(this, exo)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_PLAY_STATION, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SLEEP_TIMER, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_CANCEL_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(available, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_PLAY_STATION -> {
                            val name = args.getString("name") ?: return immediate(SessionResult.RESULT_ERROR_BAD_VALUE)
                            val url = args.getString("url") ?: return immediate(SessionResult.RESULT_ERROR_BAD_VALUE)
                            val id = args.getString("id") ?: name
                            val alt = args.getStringArrayList("alts") ?: arrayListOf()
                            val station = repository.stationById(id) ?: Station(
                                id = id,
                                name = name,
                                streamUrl = url,
                                altUrls = alt,
                                frequency = args.getString("frequency"),
                                city = args.getString("city"),
                                website = args.getString("website"),
                                source = "session"
                            )
                            playStation(station)
                            return immediate(SessionResult.RESULT_SUCCESS)
                        }
                        ACTION_SLEEP_TIMER -> {
                            startSleepTimer(args.getInt("minutes", 30))
                            return immediate(SessionResult.RESULT_SUCCESS)
                        }
                        ACTION_CANCEL_SLEEP -> {
                            cancelSleepTimer()
                            return immediate(SessionResult.RESULT_SUCCESS)
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        scope.launch {
            autoReconnect = settingsStore.settings.first().autoReconnect
            settingsStore.settings.collect { autoReconnect = it.autoReconnect }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepJob?.cancel()
        reconnectJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        equalizer.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- playback

    /** Dipanggil dari MediaSession custom command maupun dari UI lewat controller. */
    fun playStation(station: Station) {
        val p = player ?: return
        currentStation = station
        urlIndex = 0
        softRetry = 0
        PlaybackBus.currentStation.value = station
        PlaybackBus.icyTitle.value = null
        PlaybackBus.lastError.value = null
        p.setMediaItem(buildItem(station, station.allUrls.first()))
        p.prepare()
        p.playWhenReady = true
        scope.launch {
            repository.recordHistory(station)
            settingsStore.setLastStation(station.id)
        }
    }

    private fun buildItem(station: Station, url: String): MediaItem {
        val mime = when {
            Station.isHls(url) -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.BASE_TYPE_AUDIO
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(mime)
            .setMediaId(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkData(artworkFor(station), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            )
            .build()
    }

    private fun handlePlaybackFailure() {
        val station = currentStation ?: return
        val p = player ?: return
        val urls = station.allUrls
        if (urlIndex + 1 < urls.size) {
            urlIndex++
            Log.i(TAG, "fallback URL #${urlIndex + 1} untuk ${station.name}")
            PlaybackBus.reconnectAttempt.value = urlIndex
            p.setMediaItem(buildItem(station, urls[urlIndex]))
            p.prepare()
            p.playWhenReady = true
            return
        }
        if (!autoReconnect || softRetry >= MAX_SOFT_RETRIES) {
            PlaybackBus.lastError.value = "Stream tidak dapat diputar. Coba stasiun lain."
            return
        }
        softRetry++
        urlIndex = 0
        val waitMs = min(2_000L * softRetry, 12_000L) + Random.nextLong(0, 500)
        PlaybackBus.reconnectAttempt.value = softRetry
        PlaybackBus.lastError.value = "Menyambung ulang (${softRetry}/${MAX_SOFT_RETRIES})…"
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(waitMs)
            val s = currentStation ?: return@launch
            p.setMediaItem(buildItem(s, s.allUrls.first()))
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server menolak (HTTP). Coba lagi nanti."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Jaringan bermasalah."
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "Stream terputus."
        else -> "Gagal memutar stream (${e.errorCodeName})."
    }

    // ------------------------------------------------------------- sleep timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        PlaybackBus.sleepDeadline.value = deadline
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player?.pause()
            PlaybackBus.sleepDeadline.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        PlaybackBus.sleepDeadline.value = null
    }

    // ------------------------------------------------------------ notification

    private fun artworkFor(station: Station): ByteArray {
        // 1) logo asli stasiun (assets/logos) bila ada
        logoStore.loadBitmapBlocking(station.logoAsset)?.let { bmp ->
            runCatching {
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 92, out)
                return out.toByteArray()
            }
        }
        // 2) fallback: tile gradien + inisial
        return generatedArtwork(station)
    }

    private fun generatedArtwork(station: Station): ByteArray = runCatching {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val seed = station.accentSeed
        val c1 = Color.HSVToColor(floatArrayOf((seed % 360).toFloat(), 0.45f, 0.85f))
        val c2 = Color.HSVToColor(floatArrayOf(((seed / 7) % 360).toFloat(), 0.55f, 0.45f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 220f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initials = station.name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
        canvas.drawText(initials, size / 2f, size / 2f + 80f, text)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrElse { ByteArray(0) }

    private fun immediate(result: Int): ListenableFuture<SessionResult> =
        Futures.immediateFuture(SessionResult(result))

    companion object {
        const val ACTION_PLAY_STATION = "com.otoradio.app.PLAY_STATION"
        const val ACTION_SLEEP_TIMER = "com.otoradio.app.SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP = "com.otoradio.app.CANCEL_SLEEP"
        const val ACTION_STOP = "com.otoradio.app.STOP"

        private const val TAG = "RadioPlayerService"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 50_000
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
        private const val MAX_SOFT_RETRIES = 6
    }
}
