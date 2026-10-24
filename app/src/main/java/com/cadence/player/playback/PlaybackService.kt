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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.cadence.player.data.Library
import com.cadence.player.data.MediaRepository
import com.cadence.player.data.Song
import com.cadence.player.modes.ListeningMode
import com.cadence.player.modes.ModeEngine
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.runBlocking
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
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val browseExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    @Volatile private var library: Library? = null

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
        // Deck A: the session's player, with DJ effects and the visualizer tap in its audio chain.
        val player = PlayerFactory.create(this, deckB = false)

        val sessionId = Util.generateAudioSessionIdV21(this)
        player.audioSessionId = sessionId
        AudioEffects.attach(sessionId)
        AutoDj.attach(this, player, sessionId)

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

        mediaSession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtBitmapLoader(this))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

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
        AutoDj.release()
        PlaybackBridge.player = null
        mediaSession?.run {
            player.removeListener(listener)
            player.release()
            release()
        }
        mediaSession = null
        browseExecutor.shutdown()
        AudioEffects.release()
        super.onDestroy()
    }

    private fun saveQueue() {
        val player = mediaSession?.player ?: PlaybackBridge.player ?: return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        AppStore.saveQueue(ids, player.currentMediaItemIndex, player.currentPosition)
    }

    // ---------------- Browsing (Android Auto, car displays, other media apps) ----------------

    private fun lib(): Library {
        library?.let { return it }
        val loaded = runCatching {
            runBlocking { MediaRepository.load(applicationContext, AppStore.settings.value.minDurationSec) }
        }.getOrDefault(Library())
        if (loaded.songs.isNotEmpty()) library = loaded
        return loaded
    }

    private fun folder(id: String, title: String, subtitle: String? = null, artUri: Uri? = null, playable: Boolean = false): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artUri)
                    .setIsBrowsable(!playable)
                    .setIsPlayable(playable)
                    .setMediaType(if (playable) MediaMetadata.MEDIA_TYPE_PLAYLIST else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun albumArt(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /** A song shown inside [parent]; its id remembers the list so picking it plays the whole list. */
    private fun browseSong(parent: String, song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId("$parent$SEP${song.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(albumArt(song.albumId))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    private fun rootChildren(): List<MediaItem> = listOf(
        folder(NODE_MODES, "Listening modes", "Drive, Gym, Party, Focus…"),
        folder(NODE_SHUFFLE, "Shuffle all", playable = true),
        folder(NODE_FAVORITES, "Favorites"),
        folder(NODE_RECENT, "Recently played"),
        folder(NODE_PLAYLISTS, "Playlists"),
        folder(NODE_ALBUMS, "Albums"),
        folder(NODE_ARTISTS, "Artists"),
    )

    /** Songs a playable node stands for. */
    private fun songsOf(node: String): List<Song> {
        val l = lib()
        return when {
            node == NODE_FAVORITES -> AppStore.favorites.value.mapNotNull { l.byId[it] }
            node == NODE_RECENT -> AppStore.recent.value.mapNotNull { l.byId[it] }
            node == NODE_SHUFFLE -> l.songs.shuffled()
            node.startsWith("pl:") -> AppStore.playlists.value.firstOrNull { it.id == node.removePrefix("pl:") }
                ?.songIds?.mapNotNull { l.byId[it] }.orEmpty()
            node.startsWith("al:") -> l.albums.firstOrNull { it.id.toString() == node.removePrefix("al:") }?.songs.orEmpty()
            node.startsWith("ar:") -> l.artists.firstOrNull { it.name == node.removePrefix("ar:") }?.songs.orEmpty()
            node.startsWith("mode:") -> {
                val mode = runCatching { ListeningMode.valueOf(node.removePrefix("mode:")) }.getOrNull() ?: return emptyList()
                // Mode switches touch playback settings, so they run on the main thread.
                onMain { ModeEngine.activate(mode, l.songs, AppStore.playCounts.value, AppStore.favorites.value) }
            }
            else -> emptyList()
        }
    }

    private fun children(parent: String): List<MediaItem> {
        val l = lib()
        return when (parent) {
            NODE_ROOT -> rootChildren()
            NODE_MODES -> ListeningMode.entries.map { folder("mode:${it.name}", it.label, it.description, playable = true) }
            NODE_PLAYLISTS -> AppStore.playlists.value.map { folder("pl:${it.id}", it.name, "${it.songIds.size} songs") }
            NODE_ALBUMS -> l.albums.sortedBy { it.title.lowercase() }.map { folder("al:${it.id}", it.title, it.artist, albumArt(it.id)) }
            NODE_ARTISTS -> l.artists.sortedBy { it.name.lowercase() }.map { a ->
                folder("ar:${a.name}", a.name, "${a.songs.size} songs", a.albums.firstOrNull()?.let { albumArt(it.id) })
            }
            else -> songsOf(parent).take(MAX_BROWSE).map { browseSong(parent, it) }
        }
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(NODE_ROOT, "Cadence"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = browseExecutor.submit(Callable {
            val all = runCatching { children(parentId) }.getOrDefault(emptyList())
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        })

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = browseExecutor.submit(Callable {
            val song = mediaId.substringAfterLast(SEP).toLongOrNull()?.let { lib().byId[it] }
            if (song != null) LibraryResult.ofItem(song.toMediaItem(), null)
            else LibraryResult.ofItem(folder(mediaId, mediaId), null)
        })

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            browseExecutor.execute {
                val count = runCatching { searchResults(query).size }.getOrDefault(0)
                session.notifySearchResultChanged(browser, query, count, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = browseExecutor.submit(Callable {
            val all = searchResults(query)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        })

        /**
         * Expands what a car (or other controller) asked to play into a full queue: a song picked
         * from a list plays that list from there; a playlist, album, artist or mode plays all of it.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = browseExecutor.submit(Callable {
            val single = mediaItems.singleOrNull()
            val id = single?.mediaId.orEmpty()
            val expanded: MediaSession.MediaItemsWithStartPosition? = when {
                single == null || single.localConfiguration != null -> null
                id.contains(SEP) -> {
                    val parent = id.substringBeforeLast(SEP)
                    val songId = id.substringAfterLast(SEP).toLongOrNull()
                    val songs = if (parent == NODE_SEARCH) listOfNotNull(songId?.let { lib().byId[it] }) else songsOf(parent)
                    val index = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                    if (songs.isEmpty()) null
                    else MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, index, 0L)
                }
                id.toLongOrNull() == null -> {
                    if (!id.startsWith("mode:") && ModeEngine.mode.value != null) onMain { ModeEngine.deactivate() }
                    val songs = songsOf(id)
                    if (songs.isEmpty()) null
                    else MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, 0L)
                }
                else -> null
            }
            expanded ?: MediaSession.MediaItemsWithStartPosition(
                resolve(mediaItems),
                if (startIndex == C.INDEX_UNSET) 0 else startIndex,
                if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs,
            )
        })

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(resolve(mediaItems))
    }

    private fun resolve(mediaItems: List<MediaItem>): MutableList<MediaItem> = mediaItems.map { item ->
        if (item.localConfiguration != null) {
            item
        } else {
            val songId = item.mediaId.substringAfterLast(SEP).toLongOrNull()
            val song = songId?.let { library?.byId?.get(it) }
            when {
                song != null -> song.toMediaItem()
                else -> {
                    val uri = item.requestMetadata.mediaUri ?: songUri(item.mediaId.substringAfterLast(SEP))
                    if (uri == null) item else item.buildUpon().setMediaId(songId?.toString() ?: item.mediaId).setUri(uri).build()
                }
            }
        }
    }.toMutableList()

    private fun <T> onMain(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: Result<T>? = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            result = runCatching(block)
            latch.countDown()
        }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return result?.getOrThrow() ?: throw IllegalStateException("Main thread busy")
    }

    private fun searchResults(query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return lib().songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }.take(MAX_BROWSE).map { browseSong(NODE_SEARCH, it) }
    }

    private fun songUri(mediaId: String): Uri? =
        mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
}

private const val SEP = "|"
private const val MAX_BROWSE = 300
private const val NODE_ROOT = "root"
private const val NODE_MODES = "modes"
private const val NODE_SHUFFLE = "shuffle"
private const val NODE_FAVORITES = "favorites"
private const val NODE_RECENT = "recent"
private const val NODE_PLAYLISTS = "playlists"
private const val NODE_ALBUMS = "albums"
private const val NODE_ARTISTS = "artists"
private const val NODE_SEARCH = "search"

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
