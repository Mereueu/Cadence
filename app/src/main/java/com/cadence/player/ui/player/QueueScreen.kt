package com.cadence.player.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.QueueOrder
import com.cadence.player.data.QueueEntry
import com.cadence.player.data.Song
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Tab
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.ConfirmDialog
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.NameDialog
import com.cadence.player.ui.components.PlayingBars
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.matchesQuery
import com.cadence.player.ui.components.rememberReorderState
import com.cadence.player.ui.components.reorderHandle
import com.cadence.player.ui.components.reorderable
import com.cadence.player.ui.theme.CadenceColors
import kotlinx.coroutines.launch

private val QueueRowHeight = 78.dp

@Composable
fun QueueScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val state by vm.player.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val source by vm.queueSource.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = rememberReorderState(QueueRowHeight)
    var query by rememberSaveable { mutableStateOf("") }
    var titleMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // Play order (shuffle-aware) with the songs resolved.
    val entries: List<Pair<QueueEntry, Song>> = remember(state.queue, library) {
        state.queue.mapNotNull { e -> vm.songById(e.songId)?.let { e to it } }
    }
    val currentPos = entries.indexOfFirst { it.first.index == state.currentIndex }
    val totalMs = remember(entries) { entries.sumOf { it.second.duration } }
    val posByIndex = remember(entries) { entries.withIndex().associate { it.value.first.index to it.index } }
    val filtering = query.isNotBlank()
    val shown = if (filtering) entries.filter { it.second.matchesQuery(query) } else entries
    val canReorder = !filtering && !state.shuffle

    LaunchedEffect(currentPos >= 0) {
        if (currentPos > 0) listState.scrollToItem((currentPos - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- Title row ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CadenceColors.Outline, RoundedCornerShape(12.dp))
                        .clickable { titleMenu = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (entries.isEmpty()) "Queue" else source.ifBlank { "Queue" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ExpandMore, null)
                }
                DropdownMenu(expanded = titleMenu, onDismissRequest = { titleMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save queue as playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                        enabled = entries.isNotEmpty(),
                        onClick = { titleMenu = false; saving = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Go to now playing") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        enabled = entries.isNotEmpty(),
                        onClick = { titleMenu = false; actions.selectTab(Tab.PLAYER) },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear queue") },
                        leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        enabled = entries.isNotEmpty(),
                        onClick = { titleMenu = false; confirmClear = true },
                    )
                }
            }
            IconButton(onClick = { if (entries.isNotEmpty()) confirmClear = true }) {
                Icon(Icons.Rounded.Close, "Clear queue", modifier = Modifier.size(28.dp))
            }
        }

        // ---- Controls row ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.player.togglePlayPause() }) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", modifier = Modifier.size(30.dp))
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort queue") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Text(
                        "SORT QUEUE BY",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    QueueOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                sortMenu = false
                                vm.sortQueue(order)
                                actions.showMessage("Queue sorted: ${order.label.lowercase()}")
                            },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (entries.isEmpty()) "0 / 0" else "${currentPos + 1} / ${entries.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(14.dp), tint = CadenceColors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(formatDuration(totalMs), style = MaterialTheme.typography.bodyMedium, color = CadenceColors.TextSecondary)
                }
            }
            IconButton(onClick = { if (entries.isNotEmpty()) saving = true }) { Icon(Icons.Rounded.Save, "Save as playlist") }
            IconButton(onClick = {
                if (currentPos >= 0) scope.launch { listState.animateScrollToItem((currentPos - 1).coerceAtLeast(0)) }
            }) { Icon(Icons.Rounded.MyLocation, "Jump to current song") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CadenceColors.Outline))

        // ---- List ----
        val radio by vm.currentRadio.collectAsStateWithLifecycle()
        if (entries.isEmpty() && radio != null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Listening to ${radio?.name}",
                    message = "Live radio has no queue. Play songs from your library to build one.",
                    action = "Go to now playing",
                    onAction = { actions.selectTab(Tab.PLAYER) },
                )
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Queue is empty",
                    message = "Play a folder, album or playlist and its songs line up here.",
                    action = if (library.songs.isNotEmpty()) "Shuffle all songs" else null,
                    onAction = { vm.shuffle(library.songs, "All songs") },
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(shown, key = { _, e -> "${e.first.index}:${e.first.songId}" }) { pos, (entry, song) ->
                    QueueRow(
                        song = song,
                        isCurrent = entry.index == state.currentIndex,
                        isPlaying = state.isPlaying,
                        played = currentPos >= 0 && (posByIndex[entry.index] ?: 0) < currentPos,
                        modifier = Modifier.reorderable(reorder, pos),
                        handle = if (canReorder) {
                            Modifier.reorderHandle(reorder, pos, shown.size) { from, to -> vm.player.moveItem(from, to) }
                        } else {
                            null
                        },
                        onClick = { vm.player.skipToIndex(entry.index) },
                        onRemove = { vm.player.removeAt(entry.index) },
                        onPlayNext = {
                            val cur = state.currentIndex
                            if (entry.index != cur) {
                                vm.player.moveItem(entry.index, if (entry.index > cur) cur + 1 else cur)
                                actions.showMessage("Will play next")
                            }
                        },
                        onOptions = { actions.openSongMenu(song, null) },
                    )
                }
            }
        }

        // ---- Search ----
        Box(Modifier.fillMaxWidth().height(1.dp).background(CadenceColors.Outline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = CadenceColors.TextSecondary)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search in this queue…", style = MaterialTheme.typography.bodyLarge, color = CadenceColors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = CadenceColors.TextPrimary),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = CadenceColors.TextSecondary) }
            }
        }
    }

    if (saving) {
        NameDialog(
            title = "Save queue as playlist",
            initial = source.takeIf { it.isNotBlank() && !it.startsWith("Search") } ?: "",
            confirmLabel = "Save",
            onConfirm = { name ->
                val p = vm.saveQueueAsPlaylist(name)
                saving = false
                actions.showMessage("Saved “${p.name}”")
            },
            onDismiss = { saving = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear the queue?",
            message = "Playback stops and the queue is emptied. Your songs and playlists are not affected.",
            confirmLabel = "Clear",
            onConfirm = {
                vm.player.clearQueue()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    played: Boolean,
    modifier: Modifier,
    handle: Modifier?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onOptions: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    var menu by remember { mutableStateOf(false) }
    val primary = when {
        isCurrent -> accent
        played -> CadenceColors.TextSecondary
        else -> CadenceColors.TextPrimary
    }
    val secondary = if (isCurrent) accent else CadenceColors.TextSecondary
    Row(
        modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .then(if (isCurrent) Modifier.border(1.5.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            (handle ?: Modifier).size(width = 40.dp, height = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = if (handle != null) "Drag to reorder" else null,
                tint = when {
                    handle == null -> CadenceColors.Outline
                    isCurrent -> accent
                    else -> CadenceColors.TextPrimary
                },
            )
        }
        Box(Modifier.size(56.dp)) {
            ArtImage(song.art, Modifier.size(56.dp), RoundedCornerShape(4.dp))
            if (isCurrent) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { PlayingBars(isPlaying, Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(song.duration), style = MaterialTheme.typography.bodyMedium, color = secondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            com.cadence.player.ui.components.MoreButton(onClick = { menu = true }, highlighted = isCurrent)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!isCurrent) {
                    DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Remove from queue") }, leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = { menu = false; onRemove() })
                }
                DropdownMenuItem(text = { Text("More options…") }, leadingIcon = { Icon(Icons.Rounded.MoreHoriz, null) }, onClick = { menu = false; onOptions() })
            }
        }
    }
}
