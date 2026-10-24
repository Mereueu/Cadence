package com.cadence.player.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Gives app-wide code access to the service's player while the service is alive. */
object PlaybackBridge {
    @Volatile
    var player: Player? = null
}

/** Pauses playback after a delay, fading the volume out over the last few seconds. */
object SleepTimer {
    private const val FADE_MS = 8_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _endsAt = MutableStateFlow<Long?>(null)
    /** Wall-clock time (ms) at which playback will stop, or null when no timer is set. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    fun start(minutes: Int) {
        cancel()
        val duration = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + duration
        job = scope.launch {
            delay((duration - FADE_MS).coerceAtLeast(0))
            val player = PlaybackBridge.player
            if (player != null && player.isPlaying) {
                val startVolume = player.volume
                val steps = 40
                for (i in 1..steps) {
                    player.volume = startVolume * (1f - i.toFloat() / steps)
                    delay(FADE_MS / steps)
                }
                player.pause()
                player.volume = startVolume
            } else {
                delay(FADE_MS)
                PlaybackBridge.player?.pause()
            }
            _endsAt.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        PlaybackBridge.player?.let { if (it.volume < 1f) it.volume = 1f }
        _endsAt.value = null
    }
}
