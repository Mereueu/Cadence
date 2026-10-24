package com.cadence.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.theme.CadenceColors
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val tint = rememberArtColor(song?.art, settings.tintNowPlaying)
    val bg by animateColorAsState(
        lerp(CadenceColors.SurfaceHigh, tint ?: CadenceColors.SurfaceHigh, 0.2f),
        tween(500),
        label = "miniBg",
    )
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val progress = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .clickable(onClick = onOpen)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val dx = dragX.value
                            when {
                                dx < -swipeThreshold -> vm.player.next()
                                dx > swipeThreshold -> vm.player.previous()
                            }
                            scope.launch { dragX.animateTo(0f, tween(220)) }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { dragX.snapTo(dragX.value + delta) }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onDragEnd = { if (dragY < -40f) onOpen() },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            dragY += delta
                        },
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = dragX.value * 0.6f
                            alpha = 1f - (kotlin.math.abs(dragX.value) / (swipeThreshold * 3f)).coerceIn(0f, 0.6f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(song?.art, Modifier.size(46.dp), RoundedCornerShape(10.dp), iconSize = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                        label = "miniTitle",
                        modifier = Modifier.weight(1f),
                    ) { s ->
                        Column {
                            Text(
                                s?.title ?: "Loading…",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s?.artist ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = CadenceColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = { vm.player.togglePlayPause() }) {
                    Crossfade(state.isPlaying, label = "miniPlay") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CadenceColors.TextPrimary.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(accent)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
