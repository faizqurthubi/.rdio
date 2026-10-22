package com.otoradio.app.playback

import com.otoradio.app.data.model.Station
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Jembatan state ringan antara RadioPlayerService dan UI (proses yang sama).
 * MediaController tetap jadi sumber kebenaran untuk state pemutaran.
 */
object PlaybackBus {
    val icyTitle = MutableStateFlow<String?>(null)
    val icyStation = MutableStateFlow<String?>(null)
    val lastError = MutableStateFlow<String?>(null)
    val reconnectAttempt = MutableStateFlow(0)
    val currentStation = MutableStateFlow<Station?>(null)
    val sleepDeadline = MutableStateFlow<Long?>(null)
    val bufferedPercent = MutableStateFlow(0)
}
