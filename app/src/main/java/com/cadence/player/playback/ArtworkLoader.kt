package com.cadence.player.playback

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Identifies the cover art for one song (album art first, embedded picture as fallback). */
data class ArtRequest(val albumId: Long, val songId: Long)

object ArtworkLoader {
    private const val SCHEME = "cadence-art"
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun artUri(albumId: Long, songId: Long): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority("art")
        .appendPath(albumId.toString())
        .appendPath(songId.toString())
        .build()

    fun parse(uri: Uri): ArtRequest? {
        if (uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val albumId = segments[0].toLongOrNull() ?: return null
        val songId = segments[1].toLongOrNull() ?: return null
        return ArtRequest(albumId, songId)
    }

    /** Blocking. Call from a background thread. Returns null when the song has no artwork. */
    fun load(context: Context, request: ArtRequest, sizePx: Int): Bitmap? {
        val key = "${request.albumId}:${request.songId}"
        if (key in missing) return null
        val bitmap = loadAlbumArt(context, request.albumId, sizePx)
            ?: loadEmbedded(context, request.songId, sizePx)
        if (bitmap == null) missing.add(key)
        return bitmap
    }

    fun clearMissingCache() = missing.clear()

    private fun loadAlbumArt(context: Context, albumId: Long, sizePx: Int): Bitmap? {
        if (albumId <= 0) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { scaleDown(it, sizePx) }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadEmbedded(context: Context, songId: Long, sizePx: Int): Bitmap? {
        if (songId <= 0) return null
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { scaleDown(it, sizePx) }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun scaleDown(bitmap: Bitmap, sizePx: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= sizePx) return bitmap
        val scale = sizePx.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun bucketFor(requestedPx: Int): Int = if (requestedPx in 1..300) 300 else 900
}

/** Coil integration: lets `AsyncImage(model = ArtRequest(...))` load cover art. */
class ArtFetcher(
    private val data: ArtRequest,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val requested = (options.size.width as? Dimension.Pixels)?.px ?: 900
        val bitmap = ArtworkLoader.load(options.context, data, ArtworkLoader.bucketFor(requested))
            ?: throw IOException("No artwork for $data")
        DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<ArtRequest> {
        override fun create(data: ArtRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(data, options)
    }
}

class ArtKeyer : Keyer<ArtRequest> {
    override fun key(data: ArtRequest, options: Options): String {
        val requested = (options.size.width as? Dimension.Pixels)?.px ?: 900
        return "art:${data.albumId}:${data.songId}:${ArtworkLoader.bucketFor(requested)}"
    }
}
