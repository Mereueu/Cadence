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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cadence.player.modes.ModeEngine
import com.cadence.player.ui.Tab
import com.cadence.player.ui.components.MinimalButton
import com.cadence.player.ui.custom.*
import com.cadence.player.ui.dj.rememberSmoothPosition
import com.cadence.player.ui.sheets.CadenceSheet
import kotlinx.coroutines.launch

private val Dim = Color(0xFF8C8C8C)

@Composable
fun PlayerScreen(vm: MusicViewModel) {
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val radio = vm.currentRadio.collectAsStateWithLifecycle().value
    val library by vm.library.collectAsStateWithLifecycle()
    when {
        song != null -> ModularSongPlayer(vm, song)
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
private fun PlayCircle(playing: Boolean, buffering: Boolean, size: Dp = 80.dp, onClick: () -> Unit) {
    com.cadence.player.ui.theme.StyledPlayButton(playing, buffering, size, onClick)
}

/** Icon button drawn in the chosen style (Minimal, Neon, Solid, Glass or Hi-Fi). */
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
    com.cadence.player.ui.theme.StyledIconButton(icon, description, active, size, iconSize, emphasis = tint != null, onClick = onClick)
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

// =============================================================================================
// Modular player: blocks the user can reorder, resize, configure and hide (v1.4)
// =============================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModularSongPlayer(vm: MusicViewModel, song: Song) {
    val ui by UiStore.config.collectAsStateWithLifecycle()
    val editing by UiStore.editingPlayer.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val dj by vm.dj.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val mode by ModeEngine.mode.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val context = LocalContext.current
    val analysis = analyses[song.id]
    LaunchedEffect(song.id) { vm.requestAnalysis(song.id) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<BlockType?>(null) }
    val smoothPos by rememberSmoothPosition(position, state.isPlaying)

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
    val mixing = dj.phase == AutoDj.Phase.MIXING || dj.phase == AutoDj.Phase.SETTLING
    val curPos = state.queue.indexOfFirst { it.index == state.currentIndex }
    val nextSong = state.queue.getOrNull(curPos + 1)?.let { vm.songById(it.songId) }

    fun press(b: PlayerButton) {
        when (b) {
            PlayerButton.PREV -> vm.player.previous()
            PlayerButton.PLAY -> vm.player.togglePlayPause()
            PlayerButton.NEXT -> vm.player.next()
            PlayerButton.SHUFFLE -> {
                vm.player.toggleShuffle()
                actions.showMessage(if (state.shuffle) "Shuffle off" else "Shuffle on")
            }
            PlayerButton.REPEAT -> {
                vm.player.cycleRepeat()
                actions.showMessage(
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_OFF -> "Repeat queue"
                        Player.REPEAT_MODE_ALL -> "Repeat this song"
                        else -> "Repeat off"
                    }
                )
            }
            PlayerButton.BACK10 -> vm.player.seekBy(-10_000)
            PlayerButton.FWD30 -> vm.player.seekBy(30_000)
            PlayerButton.VOLUME -> showVolume(context)
            PlayerButton.EQ -> actions.navigate(Route.Equalizer)
            PlayerButton.SLEEP -> actions.openSleepTimer()
            PlayerButton.FAVORITE -> {
                vm.toggleFavorite(song.id)
                actions.showMessage(if (isFavorite) "Removed from favorites" else "Added to favorites")
            }
            PlayerButton.INFO -> showInfo = true
            PlayerButton.ADD -> actions.addToPlaylist(listOf(song))
            PlayerButton.MORE -> actions.openSongMenu(song, null)
            PlayerButton.QUEUE -> actions.selectTab(Tab.QUEUE)
            PlayerButton.DJ -> actions.navigate(Route.Dj)
            PlayerButton.VISUALIZER -> actions.openVisualizer()
            PlayerButton.SPEED -> actions.openSpeed()
        }
    }

    fun gesture(g: Gesture) {
        when (ui.gestures[g] ?: GestureAction.NONE) {
            GestureAction.NONE -> Unit
            GestureAction.NEXT -> vm.player.next()
            GestureAction.PREVIOUS -> vm.player.previous()
            GestureAction.PLAY_PAUSE -> vm.player.togglePlayPause()
            GestureAction.FAVORITE -> press(PlayerButton.FAVORITE)
            GestureAction.QUEUE -> actions.selectTab(Tab.QUEUE)
            GestureAction.SONG_MENU -> actions.openSongMenu(song, null)
            GestureAction.VISUALIZER -> actions.openVisualizer()
            GestureAction.MIX_NOW -> AutoDj.mixNow()
            GestureAction.SEEK_FWD -> vm.player.seekBy(30_000)
            GestureAction.SEEK_BACK -> vm.player.seekBy(-10_000)
            GestureAction.SHUFFLE -> press(PlayerButton.SHUFFLE)
        }
    }

    fun isActive(b: PlayerButton): Boolean = when (b) {
        PlayerButton.SHUFFLE -> state.shuffle
        PlayerButton.REPEAT -> state.repeatMode != Player.REPEAT_MODE_OFF
        PlayerButton.FAVORITE -> isFavorite
        PlayerButton.SLEEP -> sleepEndsAt != null
        PlayerButton.DJ -> dj.config.enabled
        PlayerButton.SPEED -> state.speed != 1f
        else -> false
    }

    fun iconFor(b: PlayerButton) = when (b) {
        PlayerButton.FAVORITE -> if (isFavorite) LineIcons.HeartFilled else LineIcons.Heart
        PlayerButton.REPEAT -> if (state.repeatMode == Player.REPEAT_MODE_ONE) LineIcons.RepeatOne else LineIcons.Repeat
        else -> b.icon
    }

    val density = LocalDensity.current
    val swipe = with(density) { 70.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.6f to CadenceColors.Background))
            .statusBarsPadding()
            .then(
                if (editing) Modifier else Modifier
                    .pointerInput(ui.gestures) {
                        detectTapGestures(
                            onDoubleTap = { gesture(Gesture.DOUBLE_TAP) },
                            onLongPress = { gesture(Gesture.LONG_PRESS) },
                        )
                    }
                    .pointerInput(ui.gestures) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDragEnd = {
                                val (dx, dy) = total
                                when {
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx < -swipe -> gesture(Gesture.SWIPE_LEFT)
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > swipe -> gesture(Gesture.SWIPE_RIGHT)
                                    kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy < -swipe -> gesture(Gesture.SWIPE_UP)
                                    kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy > swipe -> gesture(Gesture.SWIPE_DOWN)
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            total += amount
                        }
                    }
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) EditHeader(ui)

        val blocks = if (editing) ui.player else ui.player.filter { it.visible }
        val hasArt = blocks.any { it.type == BlockType.ARTWORK && it.visible }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (editing) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (hasArt || editing) Arrangement.Top else Arrangement.SpaceEvenly,
        ) {
            blocks.forEachIndexed { index, block ->
                val scale = block.size.scale
                val content: @Composable (Modifier) -> Unit = { m ->
                    when (block.type) {
                        BlockType.TOP_BAR -> Row(m.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val on = dj.config.enabled
                            Text(
                                if (mode != null) "${mode!!.label.uppercase()} MODE" else "AUTO DJ",
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                                color = if (on || mode != null) CadenceColors.Background else Dim,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (on || mode != null) CadenceColors.TextPrimary else Color.Transparent)
                                    .border(1.dp, if (on || mode != null) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(2.dp))
                                    .clickable {
                                        if (mode != null) {
                                            vm.exitMode()
                                            actions.showMessage("Back to your own settings")
                                        } else {
                                            AutoDj.updateConfig { it.copy(enabled = !it.enabled) }
                                            actions.showMessage(if (on) "Auto DJ off" else "Auto DJ on · songs will be mixed")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            LineButton(LineIcons.Dj, "DJ controls") { actions.navigate(Route.Dj) }
                            LineButton(LineIcons.Visualizer, "Visualizer") { actions.openVisualizer() }
                            LineButton(LineIcons.Edit, "Customize layout") { UiStore.setEditing(true) }
                        }
                        BlockType.ARTWORK -> BoxWithConstraints(
                            m.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val frac = when (block.size) {
                                BlockSize.S -> 0.55f
                                BlockSize.M -> 0.8f
                                BlockSize.L -> 0.92f
                                BlockSize.XL -> 1f
                            }
                            val side = if (editing) maxWidth * frac * 0.6f else minOf(maxWidth * frac, maxHeight)
                            AnimatedContent(
                                targetState = song,
                                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                                contentKey = { it.id },
                                label = "art",
                            ) { s ->
                                AnimatedArtwork(s, block.artStyle, state.isPlaying, analysis, smoothPos, Modifier.size(side))
                            }
                        }
                        BlockType.TITLE -> TitleBlock(
                            song,
                            scale,
                            onArtist = { actions.navigate(Route.ArtistDetail(song.artist)) },
                            onAlbum = { if (song.albumId > 0) actions.navigate(Route.AlbumDetail(song.albumId)) },
                        ).let { }
                        BlockType.INFO -> {
                            val infoLine = when {
                                mixing -> dj.plan?.let { p -> vm.songById(p.toId)?.let { "MIXING INTO ${it.title.uppercase()}" } }
                                analysis != null -> "${"%.1f".format(analysis.bpm).removeSuffix(".0")} BPM · ${analysis.camelot} · ${analysis.keyName}"
                                else -> if (editing) "BPM · KEY" else null
                            }
                            if (infoLine != null) {
                                Column(m.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                            }
                        }
                        BlockType.SPECTRUM -> SpectrumBlock(scale, m.padding(vertical = 10.dp))
                        BlockType.ACTIONS -> Row(
                            m.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            block.buttons.forEach { b ->
                                LineButton(iconFor(b), b.label, active = isActive(b), size = (44 * scale).dp, iconSize = (21 * scale).dp) { press(b) }
                            }
                        }
                        BlockType.SEEK -> Column(m.fillMaxWidth().padding(top = 8.dp)) {
                            SeekBar(
                                progress = progress,
                                onScrub = { scrub = it },
                                onSeek = { f -> vm.player.seekTo((f * duration).toLong()) },
                            )
                            Row(Modifier.fillMaxWidth()) {
                                val st = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp, fontSize = (12 * scale).sp)
                                Text(formatDuration(shownPosition), style = st, fontWeight = FontWeight.Light, color = Dim, modifier = Modifier.weight(1f))
                                Text(formatDuration(duration), style = st, fontWeight = FontWeight.Light, color = Dim)
                            }
                        }
                        BlockType.TRANSPORT -> Row(
                            m.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                            horizontalArrangement = if (block.buttons.size <= 3) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            block.buttons.forEach { b ->
                                if (b == PlayerButton.PLAY) {
                                    PlayCircle(state.isPlaying, state.isBuffering, size = (80 * scale).dp) { press(b) }
                                } else {
                                    val main = b == PlayerButton.PREV || b == PlayerButton.NEXT
                                    LineButton(
                                        iconFor(b),
                                        b.label,
                                        active = isActive(b),
                                        size = ((if (main) 60 else 44) * scale).dp,
                                        iconSize = ((if (main) 30 else 21) * scale).dp,
                                        tint = if (main) CadenceColors.TextPrimary else null,
                                    ) { press(b) }
                                }
                            }
                        }
                        BlockType.UP_NEXT -> Box(m.padding(top = 10.dp)) {
                            UpNextBlock(nextSong) { actions.selectTab(Tab.QUEUE) }
                        }
                        BlockType.MODES -> Box(m.padding(vertical = 10.dp)) {
                            ModesBlock(mode) { picked -> vm.toggleMode(picked) }
                        }
                    }
                }
                if (editing) {
                    EditFrame(block, index, ui.player.size, onConfigure = { configuring = block.type }) { content(Modifier) }
                } else if (block.type == BlockType.ARTWORK) {
                    content(Modifier.weight(1f))
                } else {
                    content(Modifier)
                }
            }
        }
    }

    if (showInfo) SongInfoDialog(song, onDismiss = { showInfo = false })
    configuring?.let { type -> BlockConfigSheet(type, onDismiss = { configuring = null }) }
}

/** Presets and the Done button shown while editing the layout. */
@Composable
private fun EditHeader(ui: UiConfig) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Customize player", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            MinimalButton("Done", null, { UiStore.setEditing(false) }, primary = true)
        }
        Text(
            "Move, resize, configure or hide each block. Start from a preset:",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LayoutPreset.entries.forEach { p ->
                val sel = p == ui.preset
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) CadenceColors.Background else CadenceColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) CadenceColors.TextPrimary else Color.Transparent)
                        .border(1.dp, if (sel) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(50))
                        .clickable { UiStore.applyPreset(p) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Dashed frame around a block in edit mode, with move / resize / configure / hide controls. */
@Composable
private fun EditFrame(
    block: Block,
    index: Int,
    count: Int,
    onConfigure: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .drawBehind {
                drawRoundRect(
                    color = if (block.visible) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                )
            }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.type.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (block.visible) CadenceColors.TextPrimary else Dim,
                modifier = Modifier.weight(1f),
            )
            SmallEditButton("↑", index > 0) { UiStore.moveBlock(index, index - 1) }
            SmallEditButton("↓", index < count - 1) { UiStore.moveBlock(index, index + 1) }
            if (block.type.sizes) SmallEditButton(block.size.name, true) { UiStore.cycleSize(block.type) }
            if (block.type.hasButtons || block.type == BlockType.ARTWORK) SmallEditButton("⚙", true, onConfigure)
            SmallEditButton(if (block.visible) "Hide" else "Show", true) { UiStore.toggleBlock(block.type) }
        }
        if (block.visible) {
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 0.9f }) { content() }
        }
    }
}

@Composable
private fun SmallEditButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) CadenceColors.TextPrimary else Color(0xFF444444),
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) CadenceColors.Outline else Color(0xFF222222), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Button picker (for button rows) or artwork style picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockConfigSheet(type: BlockType, onDismiss: () -> Unit) {
    val ui by UiStore.config.collectAsStateWithLifecycle()
    val block = ui.player.firstOrNull { it.type == type } ?: return
    CadenceSheet(onDismiss) {
        Text(block.type.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (type == BlockType.ARTWORK) {
            ArtStyle.entries.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().clickable { UiStore.setArtStyle(st) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(st.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (st == block.artStyle) Text("✓", color = CadenceColors.TextPrimary)
                }
            }
        } else {
            Text(
                "Tap to add or remove. Use the arrows to change the order.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            val chosen = block.buttons
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(chosen + PlayerButton.entries.filter { it !in chosen }, key = { it.name }) { b ->
                    val on = b in chosen
                    val i = chosen.indexOf(b)
                    Row(
                        Modifier.fillMaxWidth().clickable { UiStore.toggleButton(type, b) }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(b.icon, null, tint = if (on) CadenceColors.TextPrimary else Dim, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(b.label, style = MaterialTheme.typography.bodyLarge, color = if (on) CadenceColors.TextPrimary else Dim, modifier = Modifier.weight(1f))
                        if (on) {
                            SmallEditButton("↑", i > 0) { UiStore.moveButton(type, i, i - 1) }
                            SmallEditButton("↓", i < chosen.size - 1) { UiStore.moveButton(type, i, i + 1) }
                        }
                    }
                }
            }
        }
    }
}
