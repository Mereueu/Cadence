package com.cadence.player

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.player.data.AppStore
import com.cadence.player.data.Library
import com.cadence.player.data.MediaRepository
import com.cadence.player.data.PlayerConnection
import com.cadence.player.data.Playlist
import com.cadence.player.data.Settings
import com.cadence.player.data.Song
import com.cadence.player.data.SongSort
import com.cadence.player.playback.ArtworkLoader
import com.cadence.player.playback.AudioEffects
import com.cadence.player.playback.SleepTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    val player = PlayerConnection(app, viewModelScope)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hasLoadedOnce = MutableStateFlow(false)
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce.asStateFlow()

    val settings: StateFlow<Settings> = AppStore.settings
    val playlists: StateFlow<List<Playlist>> = AppStore.playlists
    val favorites: StateFlow<List<Long>> = AppStore.favorites
    val recent: StateFlow<List<Long>> = AppStore.recent
    val playCounts: StateFlow<Map<Long, Int>> = AppStore.playCounts
    val eq = AudioEffects.state
    val sleepTimerEndsAt = SleepTimer.endsAt

    /** All songs in the user's chosen sort order. */
    val sortedSongs: StateFlow<List<Song>> = combine(library, settings) { lib, s -> sort(lib.songs, s.songSort) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Songs opened from other apps ("Open with Cadence") that are not part of the library. */
    private val externalSongs = MutableStateFlow<Map<Long, Song>>(emptyMap())

    val currentSong: StateFlow<Song?> = combine(library, player.state, externalSongs) { lib, st, ext ->
        st.currentId?.let { lib.byId[it] ?: ext[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Plays an audio file handed to us by a file manager or another app. */
    fun playExternal(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            // A MediaStore link to a song we already know: play it from the library.
            val known = if (uri.authority == MediaStore.AUTHORITY) {
                uri.lastPathSegment?.toLongOrNull()?.let { library.value.byId[it] }
            } else {
                null
            }
            val song = known ?: MediaRepository.songFromUri(app, uri) ?: return@launch
            if (known == null) externalSongs.value = externalSongs.value + (song.id to song)
            play(listOf(song), 0, "Opened file")
        }
    }

    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null
    private var restored = false

    init {
        player.connect()
        // Re-scan when the minimum track length setting changes.
        viewModelScope.launch {
            settings.map { it.minDurationSec }.distinctUntilChanged().drop(1).collect {
                if (_hasLoadedOnce.value) refreshLibrary()
            }
        }
    }

    /** Called once storage permission is granted. */
    fun onPermissionGranted() {
        if (observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleReload()
            }.also {
                getApplication<Application>().contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, it
                )
            }
        }
        if (!_hasLoadedOnce.value) refreshLibrary()
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(1500)
            refreshLibrary(clearArtCache = false)
        }
    }

    fun refreshLibrary(clearArtCache: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            if (clearArtCache) ArtworkLoader.clearMissingCache()
            val lib = runCatching {
                MediaRepository.load(getApplication<Application>(), settings.value.minDurationSec)
            }.getOrDefault(_library.value)
            _library.value = lib
            _loading.value = false
            _hasLoadedOnce.value = true
            restoreQueueOnce(lib)
        }
    }

    private fun restoreQueueOnce(lib: Library) {
        if (restored) return
        restored = true
        val saved = AppStore.savedQueue() ?: return
        if (!player.isEmpty) return
        val items = saved.ids.mapNotNull { lib.byId[it]?.toMediaItem() }
        if (items.isEmpty()) return
        // Keep the saved index pointing at the same song even if some files were removed.
        val savedSongId = saved.ids.getOrNull(saved.index)
        val index = items.indexOfFirst { it.mediaId == savedSongId?.toString() }
        player.restore(items, if (index >= 0) index else 0, if (index >= 0) saved.positionMs else 0L)
    }

    fun songsFor(ids: List<Long>): List<Song> {
        val map = library.value.byId
        return ids.mapNotNull { map[it] }
    }

    fun mostPlayed(limit: Int = 50): List<Song> {
        val map = library.value.byId
        return playCounts.value.entries
            .sortedByDescending { it.value }
            .mapNotNull { map[it.key] }
            .take(limit)
    }

    fun recentlyAdded(limit: Int = 50): List<Song> =
        library.value.songs.sortedByDescending { it.dateAdded }.take(limit)

    // ---- Playback ----

    private val _queueSource = MutableStateFlow("")
    /** Label for where the current queue came from, shown on the Now Playing screen. */
    val queueSource: StateFlow<String> = _queueSource.asStateFlow()

    fun play(songs: List<Song>, index: Int = 0, source: String) {
        _queueSource.value = source
        player.play(songs, index)
    }

    fun shuffle(songs: List<Song>, source: String) {
        _queueSource.value = source
        player.shuffleAll(songs)
    }

    // ---- Favourites & playlists ----

    fun toggleFavorite(id: Long) = AppStore.toggleFavorite(id)
    fun createPlaylist(name: String, songs: List<Long> = emptyList()) = AppStore.createPlaylist(name, songs)
    fun renamePlaylist(id: String, name: String) = AppStore.renamePlaylist(id, name)
    fun deletePlaylist(id: String) = AppStore.deletePlaylist(id)
    fun addToPlaylist(id: String, songs: List<Long>) = AppStore.addToPlaylist(id, songs)
    fun removeFromPlaylist(id: String, index: Int) = AppStore.removeFromPlaylist(id, index)
    fun moveInPlaylist(id: String, from: Int, to: Int) = AppStore.moveInPlaylist(id, from, to)

    fun updateSettings(transform: (Settings) -> Settings) = AppStore.updateSettings(transform)
    fun clearHistory() = AppStore.clearHistory()

    fun startSleepTimer(minutes: Int) = SleepTimer.start(minutes)
    fun cancelSleepTimer() = SleepTimer.cancel()

    override fun onCleared() {
        observer?.let { getApplication<Application>().contentResolver.unregisterContentObserver(it) }
        player.release()
        super.onCleared()
    }

    companion object {
        fun sort(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
            SongSort.TITLE -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SongSort.ARTIST -> songs.sortedWith(
                compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
                    .thenBy { it.track }
            )
            SongSort.ALBUM -> songs.sortedWith(
                compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.track }
            )
            SongSort.RECENT -> songs.sortedByDescending { it.dateAdded }
            SongSort.DURATION -> songs.sortedByDescending { it.duration }
        }
    }
}
