package com.cadence.player.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cadence.player.playback.ArtRequest
import com.cadence.player.playback.ArtworkLoader

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val duration: Long,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val folder: String,
    val uri: Uri,
    val genre: String = "Unknown genre",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
) {
    val art: ArtRequest get() = ArtRequest(albumId, id)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setArtworkUri(ArtworkLoader.artUri(albumId, id))
                .setIsPlayable(true)
                .build()
        )
        .build()
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val art: ArtRequest get() = songs.first().art
    val duration: Long get() = songs.sumOf { it.duration }
}

data class Artist(
    val name: String,
    val songs: List<Song>,
    val albums: List<Album>,
) {
    val art: ArtRequest? get() = albums.firstOrNull()?.art ?: songs.firstOrNull()?.art
}

data class Folder(
    val path: String,
    val name: String,
    val songs: List<Song>,
)

data class Genre(
    val name: String,
    val songs: List<Song>,
) {
    val art: ArtRequest? get() = songs.firstOrNull()?.art
}

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
)

data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    val byId: Map<Long, Song> by lazy { songs.associateBy { it.id } }
}

enum class SongSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RECENT("Recently added"),
    DURATION("Duration"),
}

enum class AccentColor(val label: String, val argb: Long) {
    GREEN("Green", 0xFF5BE08C),
    CORAL("Coral", 0xFFFF6B4A),
    ROSE("Rose", 0xFFFF4D7D),
    VIOLET("Violet", 0xFF9D7BFF),
    OCEAN("Ocean", 0xFF3EA6FF),
    MINT("Mint", 0xFF2ED3A0),
    AMBER("Amber", 0xFFFFB020),
}

data class Settings(
    val accent: AccentColor = AccentColor.GREEN,
    val minDurationSec: Int = 30,
    val songSort: SongSort = SongSort.TITLE,
    val tintNowPlaying: Boolean = false,
)
