package com.cadence.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.SongSort
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.CircleIconButton
import com.cadence.player.ui.components.CoverCard
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.FastScroller
import com.cadence.player.ui.components.Pill
import com.cadence.player.ui.components.ScreenHeader
import com.cadence.player.ui.components.SongRow
import com.cadence.player.ui.components.indexLetter
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.theme.CadenceColors
import kotlinx.coroutines.launch

private val LibraryTabs = listOf("Songs", "Albums", "Artists", "Folders")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val pagerState = rememberPagerState { LibraryTabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Library") {
            IconButton(onClick = {
                vm.refreshLibrary()
                actions.showMessage("Rescanning your music…")
            }) { Icon(Icons.Rounded.Refresh, "Rescan") }
            IconButton(onClick = { actions.navigate(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTabs.forEachIndexed { i, label ->
                Pill(label, pagerState.currentPage == i, { scope.launch { pagerState.animateScrollToPage(i) } })
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> SongsPage(vm)
                1 -> AlbumsPage(vm)
                2 -> ArtistsPage(vm)
                else -> FoldersPage(vm)
            }
        }
    }
}

@Composable
private fun SongsPage(vm: MusicViewModel) {
    val actions = LocalActions.current
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var sortOpen by remember { mutableStateOf(false) }

    if (songs.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "No songs", "Songs on this device will appear here.")
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "controls") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        songCount(songs.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = CadenceColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { sortOpen = true }) {
                            Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(18.dp), tint = CadenceColors.TextSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(settings.songSort.label, color = CadenceColors.TextPrimary)
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SongSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = {
                                        vm.updateSettings { it.copy(songSort = sort) }
                                        sortOpen = false
                                    },
                                    trailingIcon = {
                                        if (sort == settings.songSort) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    CircleIconButton(
                        Icons.Rounded.Shuffle,
                        "Shuffle all",
                        onClick = {
                            vm.shuffle(songs, "All songs")
                            actions.openPlayer()
                        },
                        container = MaterialTheme.colorScheme.primary,
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.currentId == song.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(songs, i, "All songs") },
                    onMore = { actions.openSongMenu(song, null) },
                )
            }
        }
        FastScroller(
            listState = listState,
            itemCount = songs.size,
            headerItems = 1,
            modifier = Modifier.align(Alignment.CenterEnd),
            labelFor = { i ->
                val s = songs.getOrNull(i) ?: return@FastScroller null
                when (settings.songSort) {
                    SongSort.TITLE -> indexLetter(s.title)
                    SongSort.ARTIST -> indexLetter(s.artist)
                    SongSort.ALBUM -> indexLetter(s.album)
                    else -> null
                }
            },
        )
    }
}

@Composable
private fun AlbumsPage(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    if (library.albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "No albums", "Albums from your music files will appear here.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${library.albums.size} albums",
                style = MaterialTheme.typography.labelLarge,
                color = CadenceColors.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        items(library.albums, key = { it.id }) { album ->
            CoverCard(
                art = album.art,
                title = album.title,
                subtitle = album.artist + if (album.year > 0) " · ${album.year}" else "",
                onClick = { actions.navigate(Route.AlbumDetail(album.id)) },
                modifier = Modifier.fillMaxWidth(),
                width = 400.dp,
            )
        }
    }
}

@Composable
private fun ArtistsPage(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    if (library.artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "No artists", "Artists from your music files will appear here.")
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(library.artists, key = { it.name }) { artist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { actions.navigate(Route.ArtistDetail(artist.name)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(artist.art, Modifier.size(56.dp), CircleShape, placeholderIcon = Icons.Rounded.Person)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                if (artist.albums.isNotEmpty()) {
                                    append(if (artist.albums.size == 1) "1 album · " else "${artist.albums.size} albums · ")
                                }
                                append(songCount(artist.songs.size))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CadenceColors.TextSecondary,
                        )
                    }
                }
            }
        }
        FastScroller(
            listState = listState,
            itemCount = library.artists.size,
            modifier = Modifier.align(Alignment.CenterEnd),
            labelFor = { i -> library.artists.getOrNull(i)?.let { indexLetter(it.name) } },
        )
    }
}

@Composable
private fun FoldersPage(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    if (library.folders.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, "No folders", "Folders that contain music will appear here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(library.folders, key = { it.path }) { folder ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.navigate(Route.FolderDetail(folder.path)) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = CadenceColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    songCount(folder.songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = CadenceColors.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
