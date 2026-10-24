package com.cadence.player.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.cadence.player.MusicViewModel
import com.cadence.player.data.Song
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.MoreButton
import com.cadence.player.ui.components.SeekBar
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.theme.CadenceColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(vm: MusicViewModel) {
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    if (song == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = "Nothing playing",
                message = "Pick a song from Folders, Albums or Playlists, or shuffle your whole library.",
                action = if (library.songs.isNotEmpty()) "Shuffle all songs" else null,
                onAction = { vm.shuffle(library.songs, "All songs") },
            )
        }
        return
    }

    val artColor = rememberArtColor(song.art, settings.tintNowPlaying)
    val top by animateColorAsState(
        if (artColor != null) lerp(CadenceColors.Background, artColor, 0.35f) else CadenceColors.Background,
        tween(600),
        label = "playerTop",
    )
    val isFavorite = song.id in favorites
    val duration = state.durationMs.takeIf { it > 0 } ?: song.duration
    val shownPosition = scrub?.let { (it * duration).toLong() } ?: position
    val progress = if (duration > 0) shownPosition.toFloat() / duration else 0f
    val density = LocalDensity.current
    val skipThreshold = with(density) { 80.dp.toPx() }
    val scope = rememberCoroutineScope()
    val artDrag = remember { Animatable(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.6f to CadenceColors.Background))
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- Artwork ----
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth * 0.94f, maxHeight)
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                contentKey = { it.id },
                label = "art",
            ) { s ->
                Box(
                    Modifier
                        .size(side)
                        .graphicsLayer { translationX = artDrag.value }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val dx = artDrag.value
                                    when {
                                        dx < -skipThreshold -> vm.player.next()
                                        dx > skipThreshold -> vm.player.previous()
                                    }
                                    scope.launch { artDrag.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                },
                                onDragCancel = { scope.launch { artDrag.animateTo(0f) } },
                                onHorizontalDrag = { change, delta ->
                                    change.consume()
                                    scope.launch { artDrag.snapTo(artDrag.value + delta * 0.7f) }
                                },
                            )
                        }
                ) {
                    ArtImage(s.art, Modifier.fillMaxSize(), RoundedCornerShape(4.dp), iconSize = 96.dp)
                }
            }
        }

        // ---- Titles ----
        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = CadenceColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { actions.navigate(Route.ArtistDetail(song.artist)) },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            song.album,
            style = MaterialTheme.typography.bodyLarge,
            color = CadenceColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                if (song.albumId > 0) actions.navigate(Route.AlbumDetail(song.albumId))
            },
        )

        Spacer(Modifier.height(18.dp))

        // ---- Action row ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                vm.toggleFavorite(song.id)
                actions.showMessage(if (isFavorite) "Removed from favorites" else "Added to favorites")
            }) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) accent else CadenceColors.TextPrimary,
                )
            }
            IconButton(onClick = { showInfo = true }) { Icon(Icons.Rounded.Info, "Song info") }
            IconButton(onClick = { actions.addToPlaylist(listOf(song)) }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist") }
            MoreButton(onClick = { actions.openSongMenu(song, null) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = actions.openSleepTimer) {
                Icon(
                    Icons.Rounded.Bedtime,
                    "Sleep timer",
                    tint = if (sleepEndsAt != null) accent else CadenceColors.TextPrimary,
                )
            }
            Box(Modifier.width(1.dp).height(24.dp).background(CadenceColors.Outline))
            ModeButton(
                icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                active = state.repeatMode != Player.REPEAT_MODE_OFF,
                description = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                onClick = {
                    vm.player.cycleRepeat()
                    actions.showMessage(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_OFF -> "Repeat queue"
                            Player.REPEAT_MODE_ALL -> "Repeat this song"
                            else -> "Repeat off"
                        }
                    )
                },
            )
            ModeButton(
                icon = Icons.Rounded.Shuffle,
                active = state.shuffle,
                description = "Shuffle",
                onClick = {
                    vm.player.toggleShuffle()
                    actions.showMessage(if (state.shuffle) "Shuffle off" else "Shuffle on")
                },
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- Seek ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration(shownPosition),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp),
            )
            SeekBar(
                progress = progress,
                onScrub = { scrub = it },
                onSeek = { f -> vm.player.seekTo((f * duration).toLong()) },
                modifier = Modifier.weight(1f),
                inactiveColor = Color.White.copy(alpha = 0.22f),
            )
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- Transport ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }) { Icon(Icons.Rounded.VolumeUp, "Volume", tint = CadenceColors.TextSecondary) }
            IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { vm.player.togglePlayPause() }, modifier = Modifier.size(84.dp)) {
                Crossfade(state.isPlaying, label = "play") { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(68.dp),
                    )
                }
            }
            IconButton(onClick = { vm.player.next() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { actions.navigate(Route.Equalizer) }) {
                Icon(Icons.Rounded.Equalizer, "Equalizer", tint = CadenceColors.TextSecondary)
            }
        }
    }

    if (showInfo) SongInfoDialog(song, onDismiss = { showInfo = false })
}

@Composable
private fun ModeButton(icon: ImageVector, active: Boolean, description: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(if (active) accent else CadenceColors.TextPrimary, label = "mode")
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
}

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val rows = buildList {
        add("Title" to song.title)
        add("Artist" to song.artist)
        add("Album" to song.album)
        if (song.albumArtist != song.artist) add("Album artist" to song.albumArtist)
        if (song.genre.isNotBlank()) add("Genre" to song.genre)
        if (song.track % 1000 > 0) add("Track" to (song.track % 1000).toString())
        if (song.year > 0) add("Year" to song.year.toString())
        add("Length" to formatDuration(song.duration))
        if (song.mimeType.isNotBlank()) add("Format" to formatName(song.mimeType))
        if (song.sizeBytes > 0) add("Size" to "%.1f MB".format(song.sizeBytes / 1_048_576.0))
        add("Folder" to song.folder)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadenceColors.SurfaceHigh,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Song info", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun formatName(mime: String): String = when (mime.lowercase()) {
    "audio/flac", "audio/x-flac" -> "FLAC (lossless)"
    "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "WAV (lossless)"
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/mp4", "audio/aac", "audio/x-m4a", "audio/m4a" -> "AAC / M4A"
    "audio/ogg", "application/ogg", "audio/vorbis" -> "OGG"
    "audio/opus" -> "Opus"
    "audio/aiff", "audio/x-aiff" -> "AIFF (lossless)"
    "audio/alac", "audio/x-alac" -> "ALAC (lossless)"
    else -> mime
}
