package com.cadence.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** First letter used by the fast-scroll bubble ("#" for digits and symbols). */
fun indexLetter(text: String): String {
    val c = normalizeForSearch(text).firstOrNull { !it.isWhitespace() } ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

/**
 * Draggable scroll thumb for long lists, with a letter bubble while dragging.
 * Place it inside a Box on top of the list, aligned to the end.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    headerItems: Int = 0,
    labelFor: ((Int) -> String?)? = null,
) {
    if (itemCount < 40) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val currentLabel by rememberUpdatedState(labelFor)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableStateOf(0) }

    BoxWithConstraints(modifier.fillMaxHeight().width(28.dp)) {
        val trackPx = constraints.maxHeight.toFloat()
        val thumbPx = with(density) { 48.dp.toPx() }
        val visible = listState.layoutInfo.visibleItemsInfo.size
        val maxFirst = (itemCount + headerItems - visible).coerceAtLeast(1)
        val fraction = if (dragging) {
            dragFraction
        } else {
            (listState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        }
        val show = dragging || listState.isScrollInProgress
        val alpha by animateFloatAsState(
            if (show) 1f else 0f,
            tween(durationMillis = if (show) 150 else 600, delayMillis = if (show) 0 else 900),
            label = "fastScroll",
        )
        val thumbY = (fraction * (trackPx - thumbPx)).coerceAtLeast(0f)

        fun update(y: Float) {
            val travel = (trackPx - thumbPx).coerceAtLeast(1f)
            dragFraction = ((y - thumbPx / 2f) / travel).coerceIn(0f, 1f)
            dragIndex = (dragFraction * (itemCount - 1)).roundToInt()
            scope.launch { listState.scrollToItem(dragIndex + headerItems) }
        }

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(itemCount, trackPx, headerItems) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            update(offset.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            update(change.position.y)
                        },
                    )
                }
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY.roundToInt()) }
                .padding(end = 4.dp)
                .alpha(alpha)
                .size(width = if (dragging) 8.dp else 5.dp, height = 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (dragging) accent else Color.White.copy(alpha = 0.45f))
        )
    }

    val label = if (dragging) currentLabel?.invoke(dragIndex) else null
    if (label != null) {
        BoxWithConstraints(modifier.fillMaxHeight()) {
            val trackPx = constraints.maxHeight.toFloat()
            val thumbPx = with(density) { 48.dp.toPx() }
            val bubblePx = with(density) { 64.dp.toPx() }
            val y = dragFraction * (trackPx - thumbPx) + thumbPx / 2f - bubblePx / 2f
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, y.coerceIn(0f, (trackPx - bubblePx).coerceAtLeast(0f)).roundToInt()) }
                    .padding(end = 40.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 32.dp, bottomEnd = 6.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
