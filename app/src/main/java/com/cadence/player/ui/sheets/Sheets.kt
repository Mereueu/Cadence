package com.cadence.player.ui.sheets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.Song
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.PlaylistContext
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.MosaicArt
import com.cadence.player.ui.components.NameDialog
import com.cadence.player.ui.components.Pill
import com.cadence.player.ui.components.PlayingBars
import com.cadence.player.ui.components.SheetAction
import com.cadence.player.ui.components.SheetHandle
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.formatLongDuration
import com.cadence.player.ui.components.matchesQuery
import com.cadence.player.ui.components.reorderHandle
import com.cadence.player.ui.components.reorderable
import com.cadence.player.ui.components.rememberReorderState
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.theme.CadenceColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Styled modal bottom sheet used throughout the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CadenceColors.SurfaceHigh,
        contentColor = CadenceColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SongHeader(song: Song) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(song.art, Modifier.size(56.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = CadenceColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(vm: MusicViewModel, song: Song, context: PlaylistContext?, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val isFavorite = song.id in favorites
    val androidContext = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun closeThen(block: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            block()
        }
    }

    CadenceSheet(onDismiss, sheetState) {
        SongHeader(song)
        HorizontalDivider(color = CadenceColors.Outline, modifier = Modifier.padding(vertical = 6.dp))
        SheetAction(Icons.Rounded.QueuePlayNext, "Play next", {
            closeThen {
                vm.player.playNext(listOf(song))
                actions.showMessage("“${song.title}” will play next")
            }
        })
        SheetAction(Icons.Rounded.PlaylistPlay, "Add to queue", {
            closeThen {
                vm.player.addToQueue(listOf(song))
                actions.showMessage("Added to queue")
            }
        })
        SheetAction(Icons.Rounded.PlaylistAdd, "Add to playlist", {
            closeThen { actions.addToPlaylist(listOf(song)) }
        })
        SheetAction(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "Remove from favorites" else "Add to favorites",
            {
                vm.toggleFavorite(song.id)
                closeThen { actions.showMessage(if (isFavorite) "Removed from favorites" else "Added to favorites") }
            },
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else CadenceColors.TextPrimary,
        )
        if (context != null) {
            if (context.index > 0) {
                SheetAction(Icons.Rounded.ArrowUpward, "Move up", {
                    vm.moveInPlaylist(context.playlistId, context.index, context.index - 1)
                    closeThen {}
                })
            }
            if (context.index < context.size - 1) {
                SheetAction(Icons.Rounded.ArrowDownward, "Move down", {
                    vm.moveInPlaylist(context.playlistId, context.index, context.index + 1)
                    closeThen {}
                })
            }
            SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from playlist", {
                vm.removeFromPlaylist(context.playlistId, context.index)
                closeThen { actions.showMessage("Removed from playlist") }
            })
        }
        SheetAction(Icons.Rounded.Album, "Go to album", {
            closeThen { actions.navigate(Route.AlbumDetail(song.albumId)) }
        })
        SheetAction(Icons.Rounded.Person, "Go to artist", {
            closeThen { actions.navigate(Route.ArtistDetail(song.artist)) }
        })
        SheetAction(Icons.Rounded.Share, "Share", {
            closeThen {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("audio/*")
                    .putExtra(Intent.EXTRA_STREAM, song.uri)
                    .putExtra(Intent.EXTRA_TITLE, song.title)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching {
                    androidContext.startActivity(
                        Intent.createChooser(send, "Share ${song.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(vm: MusicViewModel, songs: List<Song>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val ids = songs.map { it.id }

    CadenceSheet(onDismiss) {
        Text(
            "Add to playlist",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            if (songs.size == 1) songs.first().title else songCount(songs.size),
            style = MaterialTheme.typography.bodyMedium,
            color = CadenceColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("New playlist", style = MaterialTheme.typography.titleSmall)
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                val arts = remember(playlist.songIds, library) {
                    playlist.songIds.asSequence().mapNotNull { library.byId[it]?.art }.take(16).toList()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newOnes = ids.filterNot { it in playlist.songIds }
                            vm.addToPlaylist(playlist.id, ids)
                            onDismiss()
                            actions.showMessage(
                                if (newOnes.isEmpty()) "Already in ${playlist.name}"
                                else "Added ${songCount(newOnes.size)} to ${playlist.name}"
                            )
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MosaicArt(arts, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(songCount(playlist.songIds.size), style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary)
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                val p = vm.createPlaylist(name, ids)
                creating = false
                onDismiss()
                actions.showMessage("Created ${p.name}")
            },
            onDismiss = { creating = false },
        )
    }
}

/** Searchable multi-select list for adding songs to a playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(vm: MusicViewModel, playlistId: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val existing = playlist?.songIds?.toSet() ?: emptySet()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(listOf<Long>()) }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter { it.matchesQuery(query) }
    }
    val accent = MaterialTheme.colorScheme.primary

    CadenceSheet(onDismiss) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            vm.addToPlaylist(playlistId, selected)
                            actions.showMessage("Added ${songCount(selected.size)}")
                        }
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}") }
            }
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "Search your library", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { song ->
                    val already = song.id in existing
                    val isSel = song.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) {
                                selected = if (isSel) selected - song.id else selected + song.id
                            }
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(song.art, Modifier.size(46.dp), RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (already) CadenceColors.TextTertiary else CadenceColors.TextPrimary,
                            )
                            Text(
                                if (already) "Already in playlist" else song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = CadenceColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            if (isSel || already) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = when {
                                already -> CadenceColors.TextTertiary
                                isSel -> accent
                                else -> CadenceColors.TextTertiary
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Rounded search input used by Search and the song picker. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CadenceColors.SurfaceHighest)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = CadenceColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = CadenceColors.TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = CadenceColors.TextPrimary),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, "Clear", tint = CadenceColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val QueueRowHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val source by vm.queueSource.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(QueueRowHeight)
    val canReorder = !state.shuffle
    val currentPos = state.queue.indexOfFirst { it.index == state.currentIndex }.coerceAtLeast(0)
    val remainingMs = state.queue.drop(currentPos + 1).sumOf { library.byId[it.songId]?.duration ?: 0L }

    LaunchedEffect(Unit) {
        if (currentPos > 0) listState.scrollToItem((currentPos - 1).coerceAtLeast(0))
    }

    CadenceSheet(onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    val parts = buildList {
                        if (source.isNotBlank()) add(source)
                        add(songCount(state.queue.size))
                        if (remainingMs > 0) add("${formatLongDuration(remainingMs)} left")
                    }
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = CadenceColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = {
                    vm.player.clearQueue()
                    onDismiss()
                }) { Text("Clear", color = CadenceColors.TextSecondary) }
            }
            if (!canReorder) {
                Text(
                    "Turn off shuffle to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = CadenceColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(state.queue, key = { _, e -> "${e.index}:${e.songId}" }) { pos, entry ->
                    val song = library.byId[entry.songId]
                    val isCurrent = entry.index == state.currentIndex
                    val played = pos < currentPos
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(QueueRowHeight)
                            .reorderable(reorder, pos)
                            .clickable { vm.player.skipToIndex(entry.index) }
                            .padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(46.dp)) {
                            ArtImage(song?.art, Modifier.size(46.dp), RoundedCornerShape(8.dp))
                            if (isCurrent) {
                                Box(
                                    Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) { PlayingBars(state.isPlaying, Color.White) }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song?.title ?: "Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                color = when {
                                    isCurrent -> accent
                                    played -> CadenceColors.TextTertiary
                                    else -> CadenceColors.TextPrimary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song?.let { "${it.artist} · ${formatDuration(it.duration)}" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (played) CadenceColors.TextTertiary else CadenceColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isCurrent) {
                            IconButton(onClick = { vm.player.removeAt(entry.index) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = CadenceColors.TextTertiary, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (canReorder) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .reorderHandle(reorder, pos, state.queue.size) { from, to -> vm.player.moveItem(from, to) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.DragHandle, "Reorder", tint = CadenceColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val endsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), endsAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val options = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    CadenceSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
        }
        val end = endsAt
        if (end != null) {
            val left = (end - now).coerceAtLeast(0)
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(18.dp),
            ) {
                Text(formatDuration(left), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Music fades out at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(end))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadenceColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("+10 min", false, {
                        val remainingMin = ((end - System.currentTimeMillis()) / 60_000L).toInt() + 10
                        vm.startSleepTimer(remainingMin.coerceAtLeast(1))
                    })
                    Pill("Turn off", false, {
                        vm.cancelSleepTimer()
                        actions.showMessage("Sleep timer off")
                    })
                }
            }
        }
        Text(
            if (end == null) "Stop playback after" else "Restart with",
            style = MaterialTheme.typography.labelLarge,
            color = CadenceColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
        FlowRow(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { min ->
                Pill(
                    if (min < 60) "$min min" else if (min % 60 == 0) "${min / 60} hr" else "${min / 60} hr ${min % 60}",
                    false,
                    {
                        vm.startSleepTimer(min)
                        actions.showMessage("Music will stop in ${if (min < 60) "$min minutes" else formatLongDuration(min * 60_000L)}")
                        onDismiss()
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val speeds = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
    CadenceSheet(onDismiss) {
        Text("Playback speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        Text(
            "${formatSpeed(state.speed)} · pitch is preserved",
            style = MaterialTheme.typography.bodyMedium,
            color = CadenceColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            speeds.forEach { s ->
                Pill(formatSpeed(s), kotlin.math.abs(state.speed - s) < 0.01f, { vm.player.setSpeed(s) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt() / 100f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() + ".0" else rounded.toString().trimEnd('0')
    return "$text×"
}
