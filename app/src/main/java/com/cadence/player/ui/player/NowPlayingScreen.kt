package com.cadence.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.SeekBar
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.sheets.QueueSheet
import com.cadence.player.ui.sheets.SleepTimerSheet
import com.cadence.player.ui.sheets.SpeedSheet
import com.cadence.player.ui.sheets.formatSpeed
import com.cadence.player.ui.theme.CadenceColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val source by vm.queueSource.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val eq by vm.eq.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val accent = MaterialTheme.colorScheme.primary

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    val artColor = rememberArtColor(song?.art, settings.tintNowPlaying)
    val top by animateColorAsState(
        lerp(CadenceColors.Background, artColor ?: lerp(CadenceColors.SurfaceHigh, accent, 0.25f), 0.62f),
        tween(700),
        label = "npTop",
    )
    val highlight = artColor?.let { lerp(it, Color.White, 0.15f) } ?: accent

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissOffset = remember { Animatable(0f) }
    val dismissThreshold = with(density) { 140.dp.toPx() }
    val skipThreshold = with(density) { 80.dp.toPx() }
    val artDrag = remember { Animatable(0f) }

    if (song == null) {
        Box(Modifier.fillMaxSize().background(CadenceColors.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent)
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffset.value
                val shrink = (dismissOffset.value / (size.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
                scaleX = 1f - shrink * 0.08f
                scaleY = 1f - shrink * 0.08f
                shape = RoundedCornerShape((shrink * 120).dp)
                clip = shrink > 0f
            }
            .background(CadenceColors.Background)
            .background(Brush.verticalGradient(0f to top, 0.72f to CadenceColors.Background))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissOffset.value > dismissThreshold) {
                            onClose()
                        } else {
                            scope.launch { dismissOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        }
                    },
                    onDragCancel = { scope.launch { dismissOffset.animateTo(0f) } },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        scope.launch { dismissOffset.snapTo((dismissOffset.value + delta).coerceAtLeast(0f)) }
                    },
                )
            },
    ) {
        val isFavorite = song.id in favorites
        val duration = state.durationMs.takeIf { it > 0 } ?: song.duration
        val shownPosition = scrub?.let { (it * duration).toLong() } ?: position
        val progress = if (duration > 0) shownPosition.toFloat() / duration else 0f

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Top bar ----
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadenceColors.TextSecondary,
                    )
                    Text(
                        source.ifBlank { song.album },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { actions.openSongMenu(song, null) }) {
                    Icon(Icons.Rounded.MoreVert, "More")
                }
            }

            // ---- Artwork ----
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight - 16.dp).coerceAtLeast(120.dp)
                val artScale by animateFloatAsState(
                    if (state.isPlaying) 1f else 0.86f,
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
                    label = "artScale",
                )
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 8 }) togetherWith fadeOut(tween(200))
                    },
                    contentKey = { it.id },
                    label = "art",
                ) { s ->
                    Box(
                        Modifier
                            .size(side)
                            .graphicsLayer {
                                scaleX = artScale
                                scaleY = artScale
                                translationX = artDrag.value
                                rotationZ = artDrag.value / 60f
                            }
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
                            .shadow(32.dp, RoundedCornerShape(22.dp), spotColor = artColor ?: Color.Black)
                    ) {
                        ArtImage(s.art, Modifier.fillMaxSize(), RoundedCornerShape(22.dp), iconSize = 96.dp)
                    }
                }
            }

            // ---- Title + favourite ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CadenceColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { actions.navigate(Route.ArtistDetail(song.artist)) },
                    )
                }
                val heartScale by animateFloatAsState(
                    if (isFavorite) 1f else 0.9f,
                    spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
                    label = "heart",
                )
                IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) highlight else CadenceColors.TextPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- Seek bar ----
            SeekBar(
                progress = progress,
                onScrub = { scrub = it },
                onSeek = { f -> vm.player.seekTo((f * duration).toLong()) },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(shownPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = CadenceColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "-" + formatDuration((duration - shownPosition).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelMedium,
                    color = CadenceColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- Transport controls ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(
                    icon = Icons.Rounded.Shuffle,
                    active = state.shuffle,
                    activeColor = highlight,
                    description = "Shuffle",
                    onClick = { vm.player.toggleShuffle() },
                )
                IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(40.dp))
                }
                PlayPauseButton(
                    playing = state.isPlaying,
                    buffering = state.isBuffering,
                    onClick = { vm.player.togglePlayPause() },
                )
                IconButton(onClick = { vm.player.next() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(40.dp))
                }
                ToggleIcon(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    activeColor = highlight,
                    description = "Repeat",
                    onClick = { vm.player.cycleRepeat() },
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---- Extras ----
            val sleepLabel = sleepEndsAt?.let { end ->
                val now by produceState(System.currentTimeMillis(), end) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(15_000)
                    }
                }
                val min = ((end - now) / 60_000L + 1).coerceAtLeast(1)
                "$min min"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ExtraButton(
                    Icons.Rounded.Speed,
                    if (state.speed == 1f) "Speed" else formatSpeed(state.speed),
                    active = state.speed != 1f,
                    activeColor = highlight,
                ) { showSpeed = true }
                ExtraButton(
                    Icons.Rounded.Bedtime,
                    sleepLabel ?: "Sleep",
                    active = sleepLabel != null,
                    activeColor = highlight,
                ) { showSleep = true }
                ExtraButton(
                    Icons.Rounded.GraphicEq,
                    "Equalizer",
                    active = eq.enabled,
                    activeColor = highlight,
                ) { actions.navigate(Route.Equalizer) }
                ExtraButton(
                    Icons.Rounded.QueueMusic,
                    "Queue",
                    active = false,
                    activeColor = highlight,
                ) { showQueue = true }
            }
        }
    }

    if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
    if (showSleep) SleepTimerSheet(vm, onDismiss = { showSleep = false })
    if (showSpeed) SpeedSheet(vm, onDismiss = { showSpeed = false })
}

@Composable
private fun PlayPauseButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    val pressScale by animateFloatAsState(if (playing) 1f else 0.94f, spring(dampingRatio = 0.5f), label = "pp")
    Box(
        Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(CadenceColors.TextPrimary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, label = "ppIcon") { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (p) "Pause" else "Play",
                tint = CadenceColors.Background,
                modifier = Modifier.size(42.dp),
            )
        }
        if (buffering) {
            CircularProgressIndicator(
                Modifier.size(78.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun ToggleIcon(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    description: String,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (active) activeColor else CadenceColors.TextSecondary, label = "toggle")
    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(24.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else Color.Transparent)
        )
    }
}

@Composable
private fun ExtraButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val tint = if (active) activeColor else CadenceColors.TextSecondary
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
