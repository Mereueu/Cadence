package com.cadence.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import com.cadence.player.data.AppStore
import com.cadence.player.data.RadioRepository
import com.cadence.player.data.RadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Radio state shared by the app screens and the playback service, so other apps
 * (Road Buddy, car head units) can change station through the media session
 * even when Cadence's own screen isn't open.
 */
object RadioBridge {

    /** Custom session commands for other apps. Keep these strings stable: other apps depend on them. */
    const val ACTION_NEXT_STATION = "com.cadence.player.NEXT_STATION"
    const val ACTION_PREVIOUS_STATION = "com.cadence.player.PREVIOUS_STATION"
    val NEXT_STATION = SessionCommand(ACTION_NEXT_STATION, Bundle.EMPTY)
    val PREVIOUS_STATION = SessionCommand(ACTION_PREVIOUS_STATION, Bundle.EMPTY)

    /** Bundle key holding the song a radio station is playing (from its ICY stream title). */
    const val EXTRA_RADIO_NOW_PLAYING = "com.cadence.player.RADIO_NOW_PLAYING"

    /** Ask Cadence for the radio song now (reply carries [EXTRA_RADIO_NOW_PLAYING]). */
    const val ACTION_GET_RADIO_NOW_PLAYING = "com.cadence.player.GET_RADIO_NOW_PLAYING"
    val GET_RADIO_NOW_PLAYING = SessionCommand(ACTION_GET_RADIO_NOW_PLAYING, Bundle.EMPTY)

    /** Sent by Cadence to connected apps whenever the radio song changes. */
    const val ACTION_RADIO_NOW_PLAYING_CHANGED = "com.cadence.player.RADIO_NOW_PLAYING_CHANGED"
    val RADIO_NOW_PLAYING_CHANGED = SessionCommand(ACTION_RADIO_NOW_PLAYING_CHANGED, Bundle.EMPTY)

    /** What the current radio station is playing ("Artist - Title"), or null. */
    @Volatile
    var nowPlaying: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stepJob: Job? = null

    /** The list the current station was started from; previous/next move through it. */
    @Volatile
    var stations: List<RadioStation> = emptyList()

    /** Builds the player item for a station. Used by the app and the service so both match. */
    fun mediaItem(station: RadioStation, streamUrl: String): MediaItem {
        val uri = Uri.parse(streamUrl)
        return MediaItem.Builder()
            .setMediaId(station.mediaId)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.subtitle.ifBlank { station.source.label })
                    .setStation(station.name)
                    .setArtworkUri(station.imageUrl?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    /** The station currently in the player, if it is one of [stations]. */
    fun currentStation(): RadioStation? {
        val id = PlaybackBridge.player?.currentMediaItem?.mediaId ?: return null
        return stations.firstOrNull { it.mediaId == id }
    }

    /**
     * Tunes to the previous (-1) or next (+1) station in [stations].
     * Returns false if no radio list is active.
     */
    fun step(delta: Int): Boolean {
        val list = stations
        val current = currentStation() ?: return false
        val i = list.indexOfFirst { it.id == current.id }
        if (i < 0 || list.size < 2) return false
        val target = list[(i + delta).mod(list.size)]
        stepJob?.cancel()
        stepJob = scope.launch {
            val url = runCatching {
                RadioRepository.resolveStream(target, AppStore.settings.value.diListenKey)
            }.getOrNull() ?: return@launch
            val player = PlaybackBridge.player ?: return@launch
            player.setMediaItem(mediaItem(target, url))
            player.prepare()
            player.play()
        }
        return true
    }
}
