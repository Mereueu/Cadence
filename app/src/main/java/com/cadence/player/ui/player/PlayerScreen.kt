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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.cadence.player.MusicViewModel
import com.cadence.player.data.RadioStation
import com.cadence.player.data.Song
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.SeekBar
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.screens.StationArt
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.LineIcons
import com.cadence.player.playback.AutoDj
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

private val Dim = Color(0xFF8C8C8C)

@Composable
fun PlayerScreen(vm: MusicViewModel) {
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val radio = vm.currentRadio.collectAsStateWithLifecycle().value
    val library by vm.library.collectAsStateWithLifecycle()
    when {
        song != null -> SongPlayer(vm, song)
        radio != null -> RadioPlayer(vm, radio)
        else -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = "Nothing playing",
                message = "Pick a song from Folders, Albums or Playlists, tune in to a station in Radio, or shuffle your whole library.",
                action = if (library.songs.isNotEmpty()) "Shuffle all songs" else null,
                onAction = { vm.shuffle(library.songs, "All songs") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongPlayer(vm: MusicViewModel, song: Song) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val context = LocalContext.current
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    val artColor = rememberArtColor(song.art, settings.tintNowPlaying)
    val top by animateColorAsState(
        if (artColor != null) lerp(CadenceColors.Background, artColor, 0.3f) else CadenceColors.Background,
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

    val dj by vm.dj.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val analysis = analyses[song.id]
    LaunchedEffect(song.id) { vm.requestAnalysis(song.id) }
    val mixing = dj.phase == AutoDj.Phase.MIXING || dj.phase == AutoDj.Phase.SETTLING

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.6f to CadenceColors.Background))
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- DJ / visualizer bar ----
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val on = dj.config.enabled
            Text(
                "AUTO DJ",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                color = if (on) CadenceColors.Background else Dim,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) CadenceColors.TextPrimary else Color.Transparent)
                    .border(1.dp, if (on) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(2.dp))
                    .clickable {
                        AutoDj.updateConfig { it.copy(enabled = !it.enabled) }
                        actions.showMessage(if (on) "Auto DJ off" else "Auto DJ on · songs will be mixed")
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.weight(1f))
            LineButton(LineIcons.Dj, "DJ controls") { actions.navigate(Route.Dj) }
            LineButton(LineIcons.Visualizer, "Visualizer") { actions.openVisualizer() }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
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
                    ArtImage(s.art, Modifier.fillMaxSize(), RoundedCornerShape(2.dp), iconSize = 96.dp)
                }
            }
        }

        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = CadenceColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { actions.navigate(Route.ArtistDetail(song.artist)) },
            )
            Text(" — ", style = MaterialTheme.typography.bodyMedium, color = CadenceColors.TextTertiary)
            Text(
                song.album,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = CadenceColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { if (song.albumId > 0) actions.navigate(Route.AlbumDetail(song.albumId)) },
            )
        }

        val infoLine = when {
            mixing -> dj.plan?.let { p -> vm.songById(p.toId)?.let { "MIXING INTO ${it.title.uppercase()}" } }
            analysis != null -> "${"%.1f".format(analysis.bpm).removeSuffix(".0")} BPM · ${analysis.camelot} · ${analysis.keyName}"
            else -> null
        }
        if (infoLine != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                infoLine,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = if (mixing) CadenceColors.TextPrimary else Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mixing) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(120.dp).height(1.dp).background(Color(0xFF333333))) {
                    Box(Modifier.fillMaxWidth(dj.progress.coerceIn(0f, 1f)).height(1.dp).background(CadenceColors.TextPrimary))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Action row ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LineButton(
                if (isFavorite) LineIcons.HeartFilled else LineIcons.Heart,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                active = isFavorite,
            ) {
                vm.toggleFavorite(song.id)
                actions.showMessage(if (isFavorite) "Removed from favorites" else "Added to favorites")
            }
            LineButton(LineIcons.Info, "Song info") { showInfo = true }
            LineButton(LineIcons.PlaylistAdd, "Add to playlist") { actions.addToPlaylist(listOf(song)) }
            LineButton(LineIcons.MoreHorizontal, "More options") { actions.openSongMenu(song, null) }
            Spacer(Modifier.weight(1f))
            LineButton(LineIcons.Sleep, "Sleep timer", active = sleepEndsAt != null, onClick = actions.openSleepTimer)
            LineButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) LineIcons.RepeatOne else LineIcons.Repeat,
                "Repeat",
                active = state.repeatMode != Player.REPEAT_MODE_OFF,
            ) {
                vm.player.cycleRepeat()
                actions.showMessage(
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_OFF -> "Repeat queue"
                        Player.REPEAT_MODE_ALL -> "Repeat this song"
                        else -> "Repeat off"
                    }
                )
            }
            LineButton(LineIcons.Shuffle, "Shuffle", active = state.shuffle) {
                vm.player.toggleShuffle()
                actions.showMessage(if (state.shuffle) "Shuffle off" else "Shuffle on")
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Seek ----
        SeekBar(
            progress = progress,
            onScrub = { scrub = it },
            onSeek = { f -> vm.player.seekTo((f * duration).toLong()) },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp), fontWeight = FontWeight.Light, color = Dim, modifier = Modifier.weight(1f))
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp), fontWeight = FontWeight.Light, color = Dim)
        }

        Spacer(Modifier.height(14.dp))

        // ---- Transport ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineButton(LineIcons.Volume, "Volume") { showVolume(context) }
            LineButton(LineIcons.Previous, "Previous", size = 60.dp, iconSize = 30.dp, tint = CadenceColors.TextPrimary) { vm.player.previous() }
            PlayCircle(playing = state.isPlaying, buffering = state.isBuffering) { vm.player.togglePlayPause() }
            LineButton(LineIcons.Next, "Next", size = 60.dp, iconSize = 30.dp, tint = CadenceColors.TextPrimary) { vm.player.next() }
            LineButton(LineIcons.Equalizer, "Equalizer") { actions.navigate(Route.Equalizer) }
        }
    }

    if (showInfo) SongInfoDialog(song, onDismiss = { showInfo = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioPlayer(vm: MusicViewModel, station: RadioStation) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val loading by vm.radioLoading.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val context = LocalContext.current
    val nowPlaying = state.streamTitle?.takeIf { it != station.name }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            StationArt(station, Modifier.size(minOf(maxWidth, maxHeight)), corner = 2.dp, textSize = 64.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .border(1.dp, CadenceColors.TextPrimary, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = CadenceColors.TextPrimary)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                station.source.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            station.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            nowPlaying ?: station.subtitle.ifBlank { "Live stream" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Light,
            color = CadenceColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333333)))
        Spacer(Modifier.height(28.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineButton(LineIcons.Volume, "Volume") { showVolume(context) }
            LineButton(LineIcons.Previous, "Previous station", size = 60.dp, iconSize = 30.dp, tint = CadenceColors.TextPrimary) { vm.stepRadio(-1) }
            PlayCircle(
                playing = state.isPlaying,
                buffering = state.isBuffering || loading != null,
            ) { vm.player.togglePlayPause() }
            LineButton(LineIcons.Next, "Next station", size = 60.dp, iconSize = 30.dp, tint = CadenceColors.TextPrimary) { vm.stepRadio(1) }
            LineButton(LineIcons.Sleep, "Sleep timer", active = sleepEndsAt != null, onClick = actions.openSleepTimer)
        }
    }
}

private fun showVolume(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
}

/** Large hairline circle with a thin play/pause glyph. */
@Composable
private fun PlayCircle(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(1.5.dp, CadenceColors.TextPrimary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, label = "play") { p ->
            Icon(
                if (p) LineIcons.Pause else LineIcons.Play,
                contentDescription = if (p) "Pause" else "Play",
                tint = CadenceColors.TextPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        if (buffering) {
            CircularProgressIndicator(Modifier.size(80.dp), color = CadenceColors.TextPrimary, strokeWidth = 1.dp)
        }
    }
}

/** Icon-only button of the Minimal theme: grey when idle, white with a short underline when active. */
@Composable
private fun LineButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(tint ?: if (active) CadenceColors.TextPrimary else Dim, label = "lineBtn")
    Column(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(iconSize))
        if (tint == null) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(12.dp)
                    .height(1.5.dp)
                    .background(if (active) CadenceColors.TextPrimary else Color.Transparent)
            )
        }
    }
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
        shape = RoundedCornerShape(8.dp),
        title = { Text("Song info", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = CadenceColors.TextPrimary) } },
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
