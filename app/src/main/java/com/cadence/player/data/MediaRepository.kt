package com.cadence.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the device's music from MediaStore and groups it into albums, artists and folders. */
object MediaRepository {

    private const val UNKNOWN_ARTIST = "Unknown artist"
    private const val UNKNOWN_ALBUM = "Unknown album"

    suspend fun load(context: Context, minDurationSec: Int): Library = withContext(Dispatchers.IO) {
        val songs = querySongs(context, minDurationSec)
        buildLibrary(songs)
    }

    @Suppress("DEPRECATION")
    private fun querySongs(context: Context, minDurationSec: Int): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATA)
            add("album_artist")
            if (hasRelativePath) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf((minDurationSec * 1000L).toString())
        val result = ArrayList<Song>()

        val cursor = try {
            context.contentResolver.query(collection, projection, selection, args, null)
        } catch (_: Exception) {
            // Some devices lack album_artist; retry without it.
            val fallback = projection.filter { it != "album_artist" }.toTypedArray()
            context.contentResolver.query(collection, fallback, selection, args, null)
        } ?: return result

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val albumArtistCol = c.getColumnIndex("album_artist")
            val relCol = if (hasRelativePath) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = if (dataCol >= 0) c.getString(dataCol) else null
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: data?.let { File(it).nameWithoutExtension } ?: "Unknown"
                val artist = c.getString(artistCol).clean() ?: UNKNOWN_ARTIST
                val albumArtist = (if (albumArtistCol >= 0) c.getString(albumArtistCol).clean() else null) ?: artist
                val folder = when {
                    relCol >= 0 -> c.getString(relCol)?.trimEnd('/')
                    else -> null
                } ?: data?.let { File(it).parent } ?: "Music"
                // TRACK may be encoded as disc * 1000 + track.
                val rawTrack = c.getInt(trackCol)
                result += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = c.getString(albumCol).clean() ?: UNKNOWN_ALBUM,
                    albumId = c.getLong(albumIdCol),
                    albumArtist = albumArtist,
                    duration = c.getLong(durationCol),
                    track = rawTrack,
                    year = c.getInt(yearCol),
                    dateAdded = c.getLong(addedCol),
                    folder = folder,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                )
            }
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun String?.clean(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

    private fun buildLibrary(songs: List<Song>): Library {
        val albums = songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                val sorted = list.sortedWith(compareBy<Song> { it.track }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                val artist = list.groupingBy { it.albumArtist }.eachCount().maxByOrNull { it.value }?.key ?: UNKNOWN_ARTIST
                Album(
                    id = albumId,
                    title = list.first().album,
                    artist = artist,
                    year = list.maxOf { it.year },
                    songs = sorted,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val artists = songs.groupBy { it.artist }
            .map { (name, list) ->
                val ids = list.map { it.albumId }.toSet()
                Artist(
                    name = name,
                    songs = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                    albums = albums.filter { it.id in ids }.sortedByDescending { it.year },
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val folders = songs.groupBy { it.folder }
            .map { (path, list) ->
                Folder(
                    path = path,
                    name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
                    songs = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        return Library(songs = songs, albums = albums, artists = artists, folders = folders)
    }
}
