package com.cadence.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.cadence.player.ui.theme.hifiKey
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cadence.player.data.Song
import com.cadence.player.playback.ArtRequest
import com.cadence.player.ui.theme.CadenceColors

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatLongDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "$h hr $m min"
        else -> "$m min"
    }
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

/** Cover art with a tasteful gradient placeholder for songs without artwork. */
@Composable
fun ArtImage(
    art: ArtRequest?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    iconSize: Dp = 22.dp,
    placeholderIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(lerp(CadenceColors.SurfaceHighest, accent, 0.22f), CadenceColors.SurfaceHigh)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            placeholderIcon,
            contentDescription = null,
            tint = lerp(CadenceColors.TextTertiary, accent, 0.35f),
            modifier = Modifier.size(iconSize),
        )
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 2×2 mosaic of the first four distinct covers, used for playlists. */
@Composable
fun MosaicArt(arts: List<ArtRequest>, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    val distinct = arts.distinctBy { it.albumId }.take(4)
    if (distinct.size < 4) {
        ArtImage(distinct.firstOrNull(), modifier, shape)
        return
    }
    Column(modifier.clip(shape)) {
        Row(Modifier.weight(1f)) {
            ArtImage(distinct[0], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp))
            ArtImage(distinct[1], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp))
        }
        Row(Modifier.weight(1f)) {
            ArtImage(distinct[2], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp))
            ArtImage(distinct[3], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp))
        }
    }
}

/** Animated equalizer bars shown next to the song that is playing. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = listOf(520, 380, 640).mapIndexed { i, duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration + i * 40), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Row(
        modifier = modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { h ->
            val fraction = if (playing) h.value else 0.3f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/** The thin "•••" button used on every row (white when the row is the one playing). */
@Composable
fun MoreButton(onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            com.cadence.player.ui.theme.LineIcons.MoreHorizontal,
            contentDescription = "More",
            tint = if (highlighted) MaterialTheme.colorScheme.primary else CadenceColors.TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Highlight used for the song that is playing: an accent outline around the row. */
@Composable
fun Modifier.currentOutline(isCurrent: Boolean): Modifier =
    if (isCurrent) this.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    subtitle: String = song.artist,
    detail: String = song.album,
    leading: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val primary = if (isCurrent) accent else CadenceColors.TextPrimary
    val secondary = if (isCurrent) accent else CadenceColors.TextSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .currentOutline(isCurrent)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = if (leading != null) 0.dp else 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (trackNumber != null) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) {
                    PlayingBars(isPlaying, accent)
                } else {
                    Text(
                        if (trackNumber > 0) trackNumber.toString() else "–",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CadenceColors.TextTertiary,
                    )
                }
            }
        } else {
            Box(Modifier.size(56.dp)) {
                ArtImage(song.art, Modifier.fillMaxSize(), RoundedCornerShape(2.dp))
                if (isCurrent) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingBars(isPlaying, Color.White) }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(song.duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        MoreButton(onMore, highlighted = isCurrent)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(action, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MinimalButton("Play", com.cadence.player.ui.theme.LineIcons.Play, onPlay, Modifier.weight(1f), primary = true)
        MinimalButton("Shuffle", com.cadence.player.ui.theme.LineIcons.Shuffle, onShuffle, Modifier.weight(1f))
    }
}

/** Hairline-outlined button of the Minimal theme. */
@Composable
fun MinimalButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val uiStyle = com.cadence.player.ui.theme.LocalUiStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val color = when (uiStyle) {
        com.cadence.player.ui.theme.UiStyle.MINIMAL -> if (primary) CadenceColors.TextPrimary else CadenceColors.TextSecondary
        com.cadence.player.ui.theme.UiStyle.NEON -> if (primary) accent else CadenceColors.TextPrimary
        com.cadence.player.ui.theme.UiStyle.SOLID -> if (primary) onAccent else CadenceColors.TextPrimary
        com.cadence.player.ui.theme.UiStyle.GLASS -> Color.White
        com.cadence.player.ui.theme.UiStyle.HIFI -> if (primary) Color(0xFFFFB020) else Color(0xFFBDBDBD)
    }
    val styled = when (uiStyle) {
        com.cadence.player.ui.theme.UiStyle.MINIMAL -> Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (primary) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(4.dp))
        com.cadence.player.ui.theme.UiStyle.NEON -> Modifier
            .clip(RoundedCornerShape(23.dp))
            .border(1.dp, if (primary) accent else Color(0xFF2A2A2A), RoundedCornerShape(23.dp))
        com.cadence.player.ui.theme.UiStyle.SOLID -> Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) accent else Color(0xFF1C1C1C))
        com.cadence.player.ui.theme.UiStyle.GLASS -> Modifier
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = if (primary) 0.24f else 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(23.dp))
        com.cadence.player.ui.theme.UiStyle.HIFI -> Modifier
            .hifiKey(radius = 6.dp)
            .clip(RoundedCornerShape(6.dp))
    }
    Row(
        modifier
            .height(46.dp)
            .then(styled)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
            color = color,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(1.dp, CadenceColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = CadenceColors.TextSecondary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = CadenceColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            MinimalButton(action, null, onAction, primary = true)
        }
    }
}

/** Large square card used in horizontal carousels (albums, recent songs). */
@Composable
fun CoverCard(
    art: ArtRequest?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    circle: Boolean = false,
) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        ArtImage(
            art,
            Modifier.fillMaxWidth().aspectRatio(1f),
            if (circle) CircleShape else RoundedCornerShape(12.dp),
            iconSize = 40.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = CadenceColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

val ListBottomPadding = PaddingValues(bottom = 24.dp)
