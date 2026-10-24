package com.cadence.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.palette.graphics.Palette
import com.cadence.player.playback.ArtRequest
import com.cadence.player.playback.ArtworkLoader
import com.cadence.player.ui.theme.CadenceColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Large page title used at the top of each tab. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        actions()
    }
}

/** Slim top bar for detail pages; the title fades in once the header scrolls away. */
@Composable
fun DetailTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bgAlpha by animateFloatAsState(if (showTitle) 1f else 0f, label = "barBg")
    Row(
        modifier
            .fillMaxWidth()
            .background(CadenceColors.Background.copy(alpha = 0.96f * bgAlpha))
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = bgAlpha },
        )
        actions()
    }
}

/** Round icon button with a filled background. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = CadenceColors.SurfaceHigh,
    tint: Color = CadenceColors.TextPrimary,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Rounded selectable chip. */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else CadenceColors.SurfaceHigh)
            .border(1.dp, if (selected) accent else CadenceColors.Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else CadenceColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else CadenceColors.TextPrimary,
        )
    }
}

/** One action row inside a bottom sheet. */
@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CadenceColors.TextPrimary,
    trailing: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = CadenceColors.TextSecondary)
        }
    }
}

/** Drag handle shown at the top of bottom sheets. */
@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CadenceColors.Outline)
        )
    }
}

/** Dialog that asks for a name (new playlist, rename). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadenceColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = CadenceColors.Outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.text) },
                enabled = value.text.isNotBlank(),
            ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CadenceColors.TextSecondary) }
        },
    )
}

/** Simple confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadenceColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, color = CadenceColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Color(0xFFFF5A5F), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CadenceColors.TextSecondary) }
        },
    )
}

/**
 * Thin, precise seek bar. Shows [progress] (0..1) and reports the scrub position while
 * dragging; the seek is only committed when the finger lifts.
 */
@Composable
fun SeekBar(
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0xFF333333),
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 4.dp, label = "track")
    val thumbRadius by animateDpAsState(if (dragging) 9.dp else 6.dp, label = "thumb")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragValue)
                    },
                    onDragEnd = {
                        currentOnSeek(dragValue)
                        currentOnScrub(null)
                        dragging = false
                    },
                    onDragCancel = {
                        currentOnScrub(null)
                        dragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragValue)
                    },
                )
            }
    ) {
        // Hairline track with a thin vertical tick as the thumb.
        val line = trackHeight.toPx() / 4f
        val y = size.height / 2f
        drawRect(inactiveColor, Offset(0f, y - line / 2f), Size(size.width, line))
        drawRect(activeColor, Offset(0f, y - line / 2f), Size(size.width * shown, line))
        val tickW = 1.5.dp.toPx()
        val tickH = thumbRadius.toPx() * 2.4f
        drawRect(activeColor, Offset(size.width * shown - tickW / 2f, y - tickH / 2f), Size(tickW, tickH))
    }
}

// ---------------------------------------------------------------------------------------------
// Drag-to-reorder for lists with fixed row heights (queue, playlists).
// ---------------------------------------------------------------------------------------------

@Stable
class ReorderState(private val rowHeightPx: Float) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private val target: Int?
        get() = draggingIndex?.let { it + (dragOffset / rowHeightPx).roundToInt() }

    fun start(index: Int) {
        draggingIndex = index
        dragOffset = 0f
    }

    fun drag(delta: Float, count: Int) {
        val from = draggingIndex ?: return
        val min = -from * rowHeightPx
        val max = (count - 1 - from) * rowHeightPx
        dragOffset = (dragOffset + delta).coerceIn(min, max)
    }

    /** Returns (from, to) when the item actually moved. */
    fun end(count: Int): Pair<Int, Int>? {
        val from = draggingIndex
        val to = target?.coerceIn(0, (count - 1).coerceAtLeast(0))
        draggingIndex = null
        dragOffset = 0f
        return if (from != null && to != null && from != to) from to to else null
    }

    /** Visual offset for the row at [index] while something is being dragged. */
    fun offsetFor(index: Int): Float {
        val from = draggingIndex ?: return 0f
        if (index == from) return dragOffset
        val to = target ?: return 0f
        return when {
            from < to && index in (from + 1)..to -> -rowHeightPx
            from > to && index in to until from -> rowHeightPx
            else -> 0f
        }
    }
}

@Composable
fun rememberReorderState(rowHeight: Dp): ReorderState {
    val px = with(LocalDensity.current) { rowHeight.toPx() }
    return remember(px) { ReorderState(px) }
}

/** Applies the drag offset to a row; the dragged row floats above the others. */
@Composable
fun Modifier.reorderable(state: ReorderState, index: Int): Modifier {
    val isDragged = state.draggingIndex == index
    val anim = remember { Animatable(0f) }
    val target = state.offsetFor(index)
    val active = state.draggingIndex != null
    LaunchedEffect(target, active, isDragged) {
        when {
            !active -> anim.snapTo(0f)
            isDragged -> anim.snapTo(target)
            else -> anim.animateTo(target)
        }
    }
    val offset = if (isDragged) target else anim.value
    return this
        .zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            translationY = offset
            if (isDragged) {
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 16f
            }
        }
        .background(if (isDragged) CadenceColors.SurfaceHighest else Color.Transparent, RoundedCornerShape(10.dp))
}

/** Put on the drag-handle icon of a reorderable row. */
fun Modifier.reorderHandle(
    state: ReorderState,
    index: Int,
    count: Int,
    onMove: (Int, Int) -> Unit,
): Modifier = this.pointerInput(index, count) {
    detectVerticalDragGestures(
        onDragStart = { state.start(index) },
        onDragEnd = { state.end(count)?.let { (from, to) -> onMove(from, to) } },
        onDragCancel = { state.end(count) },
        onVerticalDrag = { change, delta ->
            change.consume()
            state.drag(delta, count)
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Artwork colour
// ---------------------------------------------------------------------------------------------

/** Extracts a vivid colour from a song's cover art, or null when there is no art. */
@Composable
fun rememberArtColor(art: ArtRequest?, enabled: Boolean = true): Color? {
    val context = LocalContext.current.applicationContext
    val color by produceState<Color?>(null, art, enabled) {
        value = if (!enabled || art == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val bitmap = ArtworkLoader.load(context, art, 300) ?: return@withContext null
                val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                swatch?.rgb?.let { Color(it) }
            }
        }
    }
    return color
}

/** Horizontal spacing helper used by carousels. */
val CarouselSpacing = Arrangement.spacedBy(14.dp)
