package com.cadence.player.data

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cadence.player.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One entry of the play queue, in the order it will actually play (shuffle-aware). */
data class QueueEntry(val index: Int, val songId: Long)

data class PlayerState(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val speed: Float = 1f,
    /** Raw media id of the current item (also set for radio stations: "radio:…"). */
    val mediaId: String? = null,
    /** Title from the live stream's metadata (ICY), e.g. "Artist - Track" on radio. */
    val streamTitle: String? = null,
    val error: String? = null,
) {
    val hasMedia: Boolean get() = currentId != null
    val isRadio: Boolean get() = mediaId?.startsWith("radio:") == true
}

/** Talks to [PlaybackService] through a Media3 [MediaController]. */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))

        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                }
                delay(if (_state.value.isPlaying) 250L else 1000L)
            }
        }
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    val isEmpty: Boolean get() = (controller?.mediaItemCount ?: 0) == 0

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun refresh() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        val shuffle = c.shuffleModeEnabled
        val order = ArrayList<QueueEntry>(c.mediaItemCount)
        if (!timeline.isEmpty) {
            var i = timeline.getFirstWindowIndex(shuffle)
            var guard = 0
            while (i != C.INDEX_UNSET && guard < c.mediaItemCount) {
                c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { order += QueueEntry(i, it) }
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
                guard++
            }
        }
        val duration = c.duration
        _state.value = PlayerState(
            connected = true,
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = if (duration == C.TIME_UNSET) 0L else duration,
            shuffle = shuffle,
            repeatMode = c.repeatMode,
            queue = order,
            currentIndex = c.currentMediaItemIndex,
            speed = c.playbackParameters.speed,
            mediaId = c.currentMediaItem?.mediaId,
            streamTitle = c.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() },
            error = c.playerError?.let { e -> e.cause?.message ?: e.message ?: "Playback error" },
        )
        _position.value = c.currentPosition.coerceAtLeast(0L)
    }

    // ---- Commands ----

    fun play(
        songs: List<Song>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex), startPositionMs)
            c.prepare()
            if (playWhenReady) c.play()
        }
    }

    /** Plays ready-made media items (used for radio streams). */
    fun playItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = false
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.setMediaItems(items, 0, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
    }

    fun stop() = withController { it.stop() }

    /** Current position, read straight from the controller. */
    val currentPositionMs: Long get() = controller?.currentPosition ?: _position.value

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        play(songs, startIndex = songs.indices.random(), shuffle = true)
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(songs)
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(songs.map { it.toMediaItem() })
        }
    }

    fun restore(items: List<MediaItem>, index: Int, positionMs: Long) {
        if (items.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount > 0) return@withController
            c.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs.coerceAtLeast(0L))
            c.prepare()
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _position.value = positionMs
    }

    fun seekBy(deltaMs: Long) = withController {
        val target = (it.currentPosition + deltaMs).coerceIn(0L, it.duration.coerceAtLeast(0L))
        it.seekTo(target)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun skipToIndex(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeAt(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    fun moveItem(from: Int, to: Int) = withController {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }

    fun clearQueue() = withController {
        it.stop()
        it.clearMediaItems()
    }
}
