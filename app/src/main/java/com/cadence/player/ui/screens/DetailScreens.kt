package com.cadence.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.Song
import com.cadence.player.playback.ArtRequest
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.PlaylistContext
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.CarouselSpacing
import com.cadence.player.ui.components.ConfirmDialog
import com.cadence.player.ui.components.CoverCard
import com.cadence.player.ui.components.DetailTopBar
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.MosaicArt
import com.cadence.player.ui.components.NameDialog
import com.cadence.player.ui.components.PlayShuffleButtons
import com.cadence.player.ui.components.PlayingBars
import com.cadence.player.ui.components.SectionHeader
import com.cadence.player.ui.components.SongRow
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.components.formatLongDuration
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.components.rememberReorderState
import com.cadence.player.ui.components.reorderHandle
import com.cadence.player.ui.components.reorderable
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.sheets.AddSongsSheet
import com.cadence.player.ui.theme.CadenceColors

/**
 * Shared layout for albums, artists, folders, playlists and smart lists:
 * a tinted hero header with artwork, play/shuffle buttons and the track list.
 */
@Composable
private fun CollectionScreen(
    vm: MusicViewModel,
    title: String,
    eyebrow: String,
    meta: String,
    songs: List<Song>,
    source: String,
    onBack: () -> Unit,
    art: @Composable (Modifier) -> Unit,
    tintFrom: ArtRequest?,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    trackNumbers: Boolean = false,
    topActions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable () -> Unit = {},
    extraHeader: LazyListScope.() -> Unit = {},
    songItems: (LazyListScope.() -> Unit)? = null,
) {
    val actions = LocalActions.current
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val tint = rememberArtColor(tintFrom, settings.tintNowPlaying) ?: MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.38f), Color.Transparent)))
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        art(Modifier.size(220.dp).shadow(28.dp, RoundedCornerShape(18.dp), spotColor = tint))
                        Spacer(Modifier.height(22.dp))
                        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = CadenceColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary, textAlign = TextAlign.Center)
                        if (songs.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            PlayShuffleButtons(
                                onPlay = {
                                    vm.play(songs, 0, source)
                                    actions.openPlayer()
                                },
                                onShuffle = {
                                    vm.shuffle(songs, source)
                                    actions.openPlayer()
                                },
                            )
                        }
                    }
                }
            }
            extraHeader()
            if (songs.isEmpty()) {
                item(key = "empty") { emptyContent() }
            } else if (songItems != null) {
                songItems()
            } else {
                itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
                    SongRow(
                        song = song,
                        isCurrent = playerState.currentId == song.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { vm.play(songs, i, source) },
                        onMore = { actions.openSongMenu(song, null) },
                        trackNumber = if (trackNumbers) song.track % 1000 else null,
                        subtitle = if (trackNumbers) song.artist else "${song.artist} · ${song.album}",
                    )
                }
            }
        }
        DetailTopBar(title = title, showTitle = scrolled, onBack = onBack, actions = topActions)
    }
}

private fun collectionMeta(songs: List<Song>, vararg extra: String?): String =
    (extra.filterNotNull() + songCount(songs.size) + formatLongDuration(songs.sumOf { it.duration }))
        .joinToString(" · ")

@Composable
private fun IconArt(icon: ImageVector, color: Color, modifier: Modifier, shape: Shape = RoundedCornerShape(18.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f), CadenceColors.SurfaceHigh))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(84.dp))
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        EmptyState(Icons.Rounded.MusicNote, "Not available", "This item is no longer in your library.", Modifier.align(Alignment.Center))
        DetailTopBar(title = "", showTitle = false, onBack = onBack)
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun AlbumScreen(vm: MusicViewModel, albumId: Long, onBack: () -> Unit) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val album = library.albums.firstOrNull { it.id == albumId } ?: return NotFound(onBack)
    CollectionScreen(
        vm = vm,
        title = album.title,
        eyebrow = "Album",
        subtitle = album.artist,
        onSubtitleClick = { actions.navigate(Route.ArtistDetail(album.artist)) },
        meta = collectionMeta(album.songs, album.year.takeIf { it > 0 }?.toString()),
        songs = album.songs,
        source = album.title,
        onBack = onBack,
        art = { m -> ArtImage(album.art, m, RoundedCornerShape(18.dp), iconSize = 72.dp) },
        tintFrom = album.art,
        trackNumbers = true,
        topActions = {
            IconButton(onClick = { actions.addToPlaylist(album.songs) }) {
                Icon(Icons.Rounded.PlaylistAdd, "Add to playlist")
            }
        },
    )
}

@Composable
fun ArtistScreen(vm: MusicViewModel, name: String, onBack: () -> Unit) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val artist = library.artists.firstOrNull { it.name == name } ?: return NotFound(onBack)
    val albums = artist.albums
    CollectionScreen(
        vm = vm,
        title = artist.name,
        eyebrow = "Artist",
        meta = collectionMeta(artist.songs, if (albums.size == 1) "1 album" else if (albums.isNotEmpty()) "${albums.size} albums" else null),
        songs = artist.songs,
        source = artist.name,
        onBack = onBack,
        art = { m -> ArtImage(artist.art, m.clip(CircleShape), CircleShape, iconSize = 72.dp, placeholderIcon = Icons.Rounded.Person) },
        tintFrom = artist.art,
        topActions = {
            IconButton(onClick = { actions.addToPlaylist(artist.songs) }) {
                Icon(Icons.Rounded.PlaylistAdd, "Add to playlist")
            }
        },
        extraHeader = {
            if (albums.isNotEmpty()) {
                item(key = "albumsHeader") { SectionHeader("Albums") }
                item(key = "albumsRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                        items(albums, key = { it.id }) { album ->
                            CoverCard(
                                art = album.art,
                                title = album.title,
                                subtitle = if (album.year > 0) album.year.toString() else songCount(album.songs.size),
                                onClick = { actions.navigate(Route.AlbumDetail(album.id)) },
                            )
                        }
                    }
                }
            }
            item(key = "songsHeader") { SectionHeader("Songs") }
        },
    )
}

@Composable
fun FolderScreen(vm: MusicViewModel, path: String, onBack: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val folder = library.folders.firstOrNull { it.path == path } ?: return NotFound(onBack)
    val accent = MaterialTheme.colorScheme.primary
    CollectionScreen(
        vm = vm,
        title = folder.name,
        eyebrow = "Folder",
        subtitle = folder.path,
        meta = collectionMeta(folder.songs),
        songs = folder.songs,
        source = folder.name,
        onBack = onBack,
        art = { m -> IconArt(Icons.Rounded.Folder, accent, m) },
        tintFrom = null,
    )
}

@Composable
fun PlaylistScreen(vm: MusicViewModel, id: String, onBack: () -> Unit) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == id } ?: return NotFound(onBack)

    // Keep each song's position in the stored list so edits stay correct if a file went missing.
    val entries = remember(playlist.songIds, library) {
        playlist.songIds.mapIndexedNotNull { stored, songId -> library.byId[songId]?.let { stored to it } }
    }
    val songs = entries.map { it.second }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingSongs by remember { mutableStateOf(false) }
    val reorder = rememberReorderState(64.dp)
    val accent = MaterialTheme.colorScheme.primary

    CollectionScreen(
        vm = vm,
        title = playlist.name,
        eyebrow = "Playlist",
        meta = collectionMeta(songs),
        songs = songs,
        source = playlist.name,
        onBack = onBack,
        art = { m -> MosaicArt(songs.map { it.art }.take(24), m, RoundedCornerShape(18.dp)) },
        tintFrom = songs.firstOrNull()?.art,
        topActions = {
            IconButton(onClick = { addingSongs = true }) { Icon(Icons.Rounded.Add, "Add songs") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add songs") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { menuOpen = false; addingSongs = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                        enabled = songs.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            vm.player.addToQueue(songs)
                            actions.showMessage("Added ${songCount(songs.size)} to queue")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist", color = Color(0xFFFF5A5F)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5A5F)) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        },
        emptyContent = {
            EmptyState(
                icon = Icons.Rounded.QueueMusic,
                title = "Nothing here yet",
                message = "Add songs from your library, or use “Add to playlist” on any song.",
                action = "Add songs",
                onAction = { addingSongs = true },
            )
        },
        songItems = {
            itemsIndexed(entries, key = { _, e -> "${e.first}:${e.second.id}" }) { pos, (stored, song) ->
                val isCurrent = playerState.currentId == song.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .reorderable(reorder, pos)
                        .clickable { vm.play(songs, pos, playlist.name) }
                        .padding(start = 16.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp)) {
                        ArtImage(song.art, Modifier.size(48.dp), RoundedCornerShape(8.dp))
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) { PlayingBars(playerState.isPlaying, Color.White) }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) accent else CadenceColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${song.artist} · ${formatDuration(song.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadenceColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { actions.openSongMenu(song, PlaylistContext(playlist.id, stored, playlist.songIds.size)) }) {
                        Icon(Icons.Rounded.MoreVert, "More", tint = CadenceColors.TextSecondary)
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .reorderHandle(reorder, pos, entries.size) { from, to ->
                                vm.moveInPlaylist(playlist.id, entries[from].first, entries[to].first)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.DragHandle, "Reorder", tint = CadenceColors.TextTertiary)
                    }
                }
            }
        },
    )

    if (renaming) {
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onConfirm = {
                vm.renamePlaylist(playlist.id, it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete “${playlist.name}”?",
            message = "The playlist will be removed. Your songs stay on your device.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onBack()
                vm.deletePlaylist(playlist.id)
                actions.showMessage("Playlist deleted")
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (addingSongs) {
        AddSongsSheet(vm, playlist.id, onDismiss = { addingSongs = false })
    }
}

@Composable
fun SmartListScreen(vm: MusicViewModel, list: SmartList, onBack: () -> Unit) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playCounts by vm.playCounts.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    val songs = remember(list, library, favorites, recent, playCounts) {
        when (list) {
            SmartList.FAVORITES -> favorites.mapNotNull { library.byId[it] }
            SmartList.RECENTLY_PLAYED -> recent.mapNotNull { library.byId[it] }.take(100)
            SmartList.MOST_PLAYED -> playCounts.entries.sortedByDescending { it.value }.mapNotNull { library.byId[it.key] }.take(100)
            SmartList.RECENTLY_ADDED -> library.songs.sortedByDescending { it.dateAdded }.take(100)
        }
    }
    val (icon, color) = smartStyle(list)
    val emptyMessage = when (list) {
        SmartList.FAVORITES -> "Tap the heart on any song to save it here."
        SmartList.RECENTLY_PLAYED -> "Songs you play will show up here."
        SmartList.MOST_PLAYED -> "Your most-played songs will collect here over time."
        SmartList.RECENTLY_ADDED -> "New music on your device will appear here."
    }

    CollectionScreen(
        vm = vm,
        title = list.title,
        eyebrow = "Smart playlist",
        meta = collectionMeta(songs),
        songs = songs,
        source = list.title,
        onBack = onBack,
        art = { m -> IconArt(icon, color, m) },
        tintFrom = null,
        topActions = {
            if (songs.isNotEmpty()) {
                IconButton(onClick = { actions.addToPlaylist(songs) }) { Icon(Icons.Rounded.PlaylistAdd, "Save as playlist") }
            }
            if ((list == SmartList.RECENTLY_PLAYED || list == SmartList.MOST_PLAYED) && songs.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear history") }
            }
        },
        emptyContent = { EmptyState(icon, "Nothing here yet", emptyMessage) },
    )

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear listening history?",
            message = "Recently played and play counts will be reset.",
            confirmLabel = "Clear",
            onConfirm = {
                vm.clearHistory()
                confirmClear = false
                actions.showMessage("History cleared")
            },
            onDismiss = { confirmClear = false },
        )
    }
}

fun smartStyle(list: SmartList): Pair<ImageVector, Color> = when (list) {
    SmartList.FAVORITES -> Icons.Rounded.Favorite to Color(0xFFFF4D7D)
    SmartList.RECENTLY_PLAYED -> Icons.Rounded.History to Color(0xFF3EA6FF)
    SmartList.MOST_PLAYED -> Icons.Rounded.TrendingUp to Color(0xFF2ED3A0)
    SmartList.RECENTLY_ADDED -> Icons.Rounded.NewReleases to Color(0xFFFFB020)
}

