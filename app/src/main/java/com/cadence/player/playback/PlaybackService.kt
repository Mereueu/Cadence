package com.cadence.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cadence.player.MainActivity
import com.cadence.player.data.AppStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private var lastCountedId: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) lastCountedId = null
            countCurrentPlay()
            saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) countCurrentPlay() else saveQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }
    }

    /** Counts a play once per track start, feeding "Recently played" and "Most played". */
    private fun countCurrentPlay() {
        val player = PlaybackBridge.player ?: return
        if (!player.playWhenReady) return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id == lastCountedId) return
        lastCountedId = id
        id.toLongOrNull()?.let { AppStore.recordPlay(it) }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val sessionId = Util.generateAudioSessionIdV21(this)
        player.audioSessionId = sessionId
        AudioEffects.attach(sessionId)

        player.addListener(listener)
        PlaybackBridge.player = player

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setBitmapLoader(ArtBitmapLoader(this))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            saveQueue()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        SleepTimer.cancel()
        PlaybackBridge.player = null
        mediaSession?.run {
            player.removeListener(listener)
            player.release()
            release()
        }
        mediaSession = null
        AudioEffects.release()
        super.onDestroy()
    }

    private fun saveQueue() {
        val player = mediaSession?.player ?: PlaybackBridge.player ?: return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        AppStore.saveQueue(ids, player.currentMediaItemIndex, player.currentPosition)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val uri = item.requestMetadata.mediaUri ?: songUri(item.mediaId)
                    if (uri == null) item else item.buildUpon().setUri(uri).build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun songUri(mediaId: String): Uri? =
        mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
}

/** Supplies cover art for the notification, lock screen and connected devices. */
@OptIn(UnstableApi::class)
private class ArtBitmapLoader(
    private val context: Context,
    private val delegate: BitmapLoader = DataSourceBitmapLoader(context),
) : BitmapLoader by delegate {

    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val request = ArtworkLoader.parse(uri) ?: return delegate.loadBitmap(uri)
        return executor.submit(Callable {
            ArtworkLoader.load(context, request, 512) ?: throw IOException("No artwork")
        })
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val data = metadata.artworkData
        if (data != null) return delegate.decodeBitmap(data)
        val uri = metadata.artworkUri ?: return null
        return loadBitmap(uri)
    }
}
