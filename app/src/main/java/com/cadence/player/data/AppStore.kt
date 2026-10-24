package com.cadence.player.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class SavedQueue(val ids: List<Long>, val index: Int, val positionMs: Long)

/**
 * Small persistent store for everything the user creates: playlists, favourites,
 * listening history, play counts, the last queue and app settings.
 */
object AppStore {
    private const val MAX_RECENT = 200

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<List<Long>>(emptyList())
    /** Favourite song ids, most recently added first. */
    val favorites: StateFlow<List<Long>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<Long>>(emptyList())
    /** Recently played song ids, newest first. */
    val recent: StateFlow<List<Long>> = _recent.asStateFlow()

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("cadence_store", Context.MODE_PRIVATE)
        _playlists.value = readPlaylists()
        _favorites.value = readIds("favorites")
        _recent.value = readIds("recent")
        _playCounts.value = readCounts()
        _settings.value = readSettings()
    }

    // ---- Playlists ----

    fun createPlaylist(name: String, initial: List<Long> = emptyList()): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "New playlist" },
            songIds = initial.distinct(),
            createdAt = System.currentTimeMillis(),
        )
        _playlists.update { it + playlist }
        writePlaylists()
        return playlist
    }

    fun renamePlaylist(id: String, name: String) = editPlaylist(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun deletePlaylist(id: String) {
        _playlists.update { list -> list.filterNot { it.id == id } }
        writePlaylists()
    }

    fun addToPlaylist(id: String, songIds: List<Long>) = editPlaylist(id) { p ->
        p.copy(songIds = p.songIds + songIds.filterNot { it in p.songIds })
    }

    fun removeFromPlaylist(id: String, index: Int) = editPlaylist(id) { p ->
        if (index !in p.songIds.indices) p else p.copy(songIds = p.songIds.toMutableList().also { it.removeAt(index) })
    }

    fun moveInPlaylist(id: String, from: Int, to: Int) = editPlaylist(id) { p ->
        if (from !in p.songIds.indices || to !in p.songIds.indices) {
            p
        } else {
            val list = p.songIds.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            p.copy(songIds = list)
        }
    }

    private fun editPlaylist(id: String, transform: (Playlist) -> Playlist) {
        _playlists.update { list -> list.map { if (it.id == id) transform(it) else it } }
        writePlaylists()
    }

    // ---- Favourites & history ----

    fun isFavorite(id: Long) = id in _favorites.value

    fun toggleFavorite(id: Long) {
        _favorites.update { if (id in it) it - id else listOf(id) + it }
        writeIds("favorites", _favorites.value)
    }

    fun recordPlay(id: Long) {
        _playCounts.update { it + (id to ((it[id] ?: 0) + 1)) }
        markRecent(id)
        writeCounts()
    }

    fun markRecent(id: Long) {
        _recent.update { (listOf(id) + it.filterNot { x -> x == id }).take(MAX_RECENT) }
        writeIds("recent", _recent.value)
    }

    fun clearHistory() {
        _recent.value = emptyList()
        _playCounts.value = emptyMap()
        writeIds("recent", emptyList())
        writeCounts()
    }

    // ---- Queue ----

    fun saveQueue(ids: List<Long>, index: Int, positionMs: Long) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString("queue", JSONArray(ids).toString())
            .putInt("queue_index", index)
            .putLong("queue_position", positionMs)
            .apply()
    }

    fun savedQueue(): SavedQueue? {
        val ids = readIds("queue")
        if (ids.isEmpty()) return null
        return SavedQueue(ids, prefs.getInt("queue_index", 0), prefs.getLong("queue_position", 0L))
    }

    // ---- Settings ----

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update(transform)
        val s = _settings.value
        prefs.edit()
            .putString("accent", s.accent.name)
            .putInt("min_duration", s.minDurationSec)
            .putString("song_sort", s.songSort.name)
            .putBoolean("tint_now_playing", s.tintNowPlaying)
            .apply()
    }

    private fun readSettings(): Settings {
        val d = Settings()
        return Settings(
            accent = runCatching { AccentColor.valueOf(prefs.getString("accent", d.accent.name)!!) }.getOrDefault(d.accent),
            minDurationSec = prefs.getInt("min_duration", d.minDurationSec),
            songSort = runCatching { SongSort.valueOf(prefs.getString("song_sort", d.songSort.name)!!) }.getOrDefault(d.songSort),
            tintNowPlaying = prefs.getBoolean("tint_now_playing", d.tintNowPlaying),
        )
    }

    // ---- JSON persistence ----

    private fun readIds(key: String): List<Long> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        List(arr.length()) { arr.getLong(it) }
    }.getOrDefault(emptyList())

    private fun writeIds(key: String, ids: List<Long>) {
        prefs.edit().putString(key, JSONArray(ids).toString()).apply()
    }

    private fun readCounts(): Map<Long, Int> = runCatching {
        val obj = JSONObject(prefs.getString("play_counts", "{}") ?: "{}")
        obj.keys().asSequence().associate { it.toLong() to obj.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun writeCounts() {
        val obj = JSONObject()
        _playCounts.value.forEach { (id, count) -> obj.put(id.toString(), count) }
        prefs.edit().putString("play_counts", obj.toString()).apply()
    }

    private fun readPlaylists(): List<Playlist> = runCatching {
        val arr = JSONArray(prefs.getString("playlists", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                songIds = List(songs.length()) { songs.getLong(it) },
                createdAt = o.optLong("createdAt", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun writePlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray(p.songIds))
                    .put("createdAt", p.createdAt)
            )
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }
}
