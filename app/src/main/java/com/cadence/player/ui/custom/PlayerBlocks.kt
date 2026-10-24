package com.cadence.player.ui.custom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.player.audio.AudioFrame
import com.cadence.player.audio.LiveAudio
import com.cadence.player.audio.TrackAnalysis
import com.cadence.player.data.Song
import com.cadence.player.modes.ListeningMode
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.theme.CadenceColors

/**
 * Album art with optional motion:
 *  - BREATHE: scales gently on every beat (beat grid from analysis, or a slow pulse),
 *  - DRIFT: a slow pan-and-zoom,
 *  - VINYL: a spinning record with the art as its label.
 */
@Composable
fun AnimatedArtwork(
    song: Song,
    style: ArtStyle,
    playing: Boolean,
    analysis: TrackAnalysis?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "art")
    val drift by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1800 * 1, easing = LinearEasing)), label = "spin")
    val slow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "slow")

    when (style) {
        ArtStyle.STATIC -> ArtImage(song.art, modifier, RoundedCornerShape(2.dp), iconSize = 96.dp)
        ArtStyle.BREATHE -> {
            val pulse = if (!playing) {
                0f
            } else if (analysis != null) {
                val beat = analysis.beatAt(positionMs)
                val frac = (beat - Math.floor(beat)).toFloat()
                (1f - frac).let { it * it * it }
            } else {
                slow * 0.4f
            }
            ArtImage(
                song.art,
                modifier.graphicsLayer {
                    val s = 1f + pulse * 0.025f
                    scaleX = s
                    scaleY = s
                },
                RoundedCornerShape(2.dp),
                iconSize = 96.dp,
            )
        }
        ArtStyle.DRIFT -> Box(modifier.clip(RoundedCornerShape(2.dp))) {
            ArtImage(
                song.art,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1.12f + drift * 0.08f
                        scaleX = s
                        scaleY = s
                        translationX = (drift - 0.5f) * size.width * 0.06f
                        translationY = (0.5f - drift) * size.height * 0.04f
                    },
                RoundedCornerShape(0.dp),
                iconSize = 96.dp,
            )
        }
        ArtStyle.VINYL -> Box(modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = if (playing) spin else 0f }
                    .clip(CircleShape)
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    var g = r * 0.98f
                    while (g > r * 0.4f) {
                        drawCircle(Color.White.copy(alpha = 0.05f), g, style = Stroke(1f))
                        g -= r * 0.035f
                    }
                    drawCircle(Color.White.copy(alpha = 0.12f), r * 0.97f, style = Stroke(1.5f))
                }
                ArtImage(song.art, Modifier.fillMaxSize(0.42f).clip(CircleShape), CircleShape, iconSize = 40.dp)
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Black))
            }
        }
    }
}

/** Small live spectrum (microphone-free, from the playing audio). */
@Composable
fun SpectrumBlock(scale: Float, modifier: Modifier = Modifier) {
    val frame = remember { AudioFrame() }
    var tick by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        LiveAudio.enabled = true
        onDispose { LiveAudio.enabled = false }
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                LiveAudio.analyze(frame)
                tick++
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height((56 * scale).dp)) {
        tick
        val n = LiveAudio.BANDS
        val gap = 3f
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val v = frame.bands[i]
            val h = (v * size.height).coerceAtLeast(1.5f)
            drawRect(Color.White.copy(alpha = 0.35f + v * 0.65f), Offset(i * (bw + gap), size.height - h), Size(bw, h))
        }
    }
}

@Composable
fun TitleBlock(song: Song, scale: Float, onArtist: () -> Unit, onAlbum: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = (21 * scale).sp, lineHeight = (27 * scale).sp),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = if (scale > 1.3f) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * scale).sp),
            fontWeight = FontWeight.Light,
            color = CadenceColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onArtist),
        )
        if (scale < 1.6f) {
            Text(
                song.album,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp),
                fontWeight = FontWeight.Light,
                color = CadenceColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onAlbum),
            )
        }
    }
}

@Composable
fun UpNextBlock(next: Song?, onClick: () -> Unit) {
    if (next == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, CadenceColors.Outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("UP NEXT", style = MaterialTheme.typography.labelSmall, color = CadenceColors.TextTertiary)
        Spacer(Modifier.width(12.dp))
        ArtImage(next.art, Modifier.size(32.dp), RoundedCornerShape(2.dp), iconSize = 14.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(next.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(next.artist, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ModesBlock(current: ListeningMode?, onPick: (ListeningMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListeningMode.entries.forEach { m ->
            val sel = m == current
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) CadenceColors.TextPrimary else Color.Transparent)
                    .border(1.dp, if (sel) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(50))
                    .clickable { onPick(m) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(m.icon, null, tint = if (sel) CadenceColors.Background else CadenceColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(m.label, style = MaterialTheme.typography.labelLarge, color = if (sel) CadenceColors.Background else CadenceColors.TextSecondary)
            }
        }
    }
}

