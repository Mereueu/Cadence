package com.cadence.player.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.Song
import com.cadence.player.playback.ArtRequest
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.FastScroller
import com.cadence.player.ui.components.MoreButton
import com.cadence.player.ui.components.SongRow
import com.cadence.player.ui.components.indexLetter
import com.cadence.player.ui.components.matchesQuery
import com.cadence.player.ui.components.normalizeForSearch
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.theme.CadenceColors

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

data class SortOption(val key: String, val label: String)

/** "Search an album…" bar with sort and settings buttons, used at the top of browse tabs. */
@Composable
fun BrowseSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    sortOptions: List<SortOption>,
    sortKey: String,
    onSort: (String) -> Unit,
) {
    val actions = LocalActions.current
    val accent = MaterialTheme.colorScheme.primary
    var sortOpen by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = CadenceColors.TextSecondary)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = CadenceColors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = CadenceColors.TextPrimary),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear search", tint = CadenceColors.TextSecondary) }
            }
            if (sortOptions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.Sort, "Sort") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        Text(
                            "SORT BY",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = { if (option.key == sortKey) Icon(Icons.Rounded.Check, null, tint = accent) },
                                onClick = {
                                    sortOpen = false
                                    onSort(option.key)
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { actions.navigate(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CadenceColors.Outline))
    }
}

/** Play / shuffle / queue / playlist menu behind a row's "•••" button. */
@Composable
fun CollectionMenu(vm: MusicViewModel, songs: List<Song>, source: String, highlighted: Boolean = false) {
    val actions = LocalActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        MoreButton(onClick = { open = true }, highlighted = highlighted)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Play") },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = { open = false; vm.play(songs, 0, source); actions.openPlayer() },
            )
            DropdownMenuItem(
                text = { Text("Shuffle") },
                leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                onClick = { open = false; vm.shuffle(songs, source); actions.openPlayer() },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
                onClick = {
                    open = false
                    vm.player.addToQueue(songs)
                    actions.showMessage("Added ${songCount(songs.size)} to queue")
                },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                onClick = { open = false; actions.addToPlaylist(songs) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CadenceColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------
// Folders: a real folder tree, starting at Internal Storage
// ---------------------------------------------------------------------------------------------

/** Turns MediaStore paths into "Music/Rap" style paths relative to the storage root. */
private fun normalizeFolder(path: String): String {
    var p = path.replace('\\', '/').trim('/')
    val prefixes = listOf("storage/emulated/0/", "sdcard/", "storage/self/primary/")
    prefixes.firstOrNull { p.startsWith(it) }?.let { p = p.removePrefix(it) }
    if (p.startsWith("storage/")) {
        // Another volume, e.g. an SD card: storage/1234-ABCD/Music → SD card/Music
        val rest = p.removePrefix("storage/").substringAfter('/', "")
        p = if (rest.isEmpty()) "SD card" else "SD card/$rest"
    }
    if (p == "storage/emulated/0") p = ""
    return p
}

@Composable
fun FoldersScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    var path by rememberSaveable { mutableStateOf("") }

    val byFolder = remember(library) { library.songs.groupBy { normalizeFolder(it.folder) } }
    val prefix = if (path.isEmpty()) "" else "$path/"
    val subfolders = remember(byFolder, path) {
        byFolder.entries
            .filter { (folder, _) -> folder.startsWith(prefix) && folder.length > prefix.length }
            .groupBy { (folder, _) -> folder.removePrefix(prefix).substringBefore('/') }
            .map { (name, groups) -> name to groups.sumOf { it.value.size } }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
    }
    val songsHere = remember(byFolder, path) {
        byFolder[path].orEmpty().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    val allBelow = remember(byFolder, path) {
        byFolder.filterKeys { it == path || it.startsWith(prefix) }.values.flatten()
            .sortedWith(compareBy<Song> { normalizeFolder(it.folder) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    BackHandler(enabled = path.isNotEmpty()) { path = path.substringBeforeLast('/', "") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (path.isNotEmpty()) {
                IconButton(onClick = { path = path.substringBeforeLast('/', "") }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Up one folder")
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FolderSpecial, null, tint = CadenceColors.TextSecondary)
                }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (path.isEmpty()) "Internal Storage" else path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (path.isNotEmpty()) {
                    Text(
                        "Internal Storage/$path",
                        style = MaterialTheme.typography.bodySmall,
                        color = CadenceColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (allBelow.isNotEmpty()) {
                CollectionMenu(vm, allBelow, if (path.isEmpty()) "Internal Storage" else path.substringAfterLast('/'))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CadenceColors.Outline))

        if (library.songs.isEmpty()) {
            EmptyState(Icons.Rounded.FolderSpecial, "No music folders", "Folders that contain audio files will appear here.")
        } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(subfolders, key = { "dir:${it.first}" }) { (name, count) ->
                BrowseRow(
                    title = name,
                    subtitle = songCount(count),
                    onClick = { path = prefix + name },
                    leading = {
                        Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(36.dp), tint = CadenceColors.TextPrimary)
                    },
                )
            }
            itemsIndexed(songsHere, key = { _, s -> "song:${s.id}" }) { i, song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.currentId == song.id,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(songsHere, i, path.substringAfterLast('/').ifEmpty { "Internal Storage" }) },
                    onMore = { actions.openSongMenu(song, null) },
                )
            }
        }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Albums: 3-column grid
// ---------------------------------------------------------------------------------------------

private val AlbumSorts = listOf(
    SortOption("title", "Title"),
    SortOption("artist", "Artist"),
    SortOption("year", "Year (newest)"),
    SortOption("added", "Recently added"),
    SortOption("songs", "Most songs"),
)

@Composable
fun AlbumsScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("title") }
    val currentAlbum = vm.songById(playerState.currentId ?: Long.MIN_VALUE)?.albumId

    val albums = remember(library, query, sort) {
        val filtered = if (query.isBlank()) library.albums else library.albums.filter { it.matchesQuery(query) }
        when (sort) {
            "artist" -> filtered.sortedWith(compareBy<com.cadence.player.data.Album, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy { it.title })
            "year" -> filtered.sortedByDescending { it.year }
            "added" -> filtered.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }
            "songs" -> filtered.sortedByDescending { it.songs.size }
            else -> filtered
        }
    }

    Column(Modifier.fillMaxSize()) {
        BrowseSearchBar(query, { query = it }, "Search an album…", AlbumSorts, sort, { sort = it })
        if (albums.isEmpty()) {
            EmptyState(Icons.Rounded.Album, if (query.isBlank()) "No albums" else "No matches", if (query.isBlank()) "Albums from your music files will appear here." else "No album matches “$query”.")
        } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                GridCard(
                    art = album.art,
                    title = album.title,
                    highlighted = album.id == currentAlbum,
                    onClick = { actions.navigate(Route.AlbumDetail(album.id)) },
                )
            }
        }
        }
    }
}

@Composable
private fun GridCard(art: ArtRequest?, title: String, highlighted: Boolean, onClick: () -> Unit, placeholder: ImageVector = Icons.Rounded.Album) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) accent else CadenceColors.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        ArtImage(art, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(0.dp), iconSize = 40.dp, placeholderIcon = placeholder)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) accent else CadenceColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Artists
// ---------------------------------------------------------------------------------------------

private val ArtistSorts = listOf(SortOption("name", "Name"), SortOption("songs", "Most songs"), SortOption("albums", "Most albums"))

@Composable
fun ArtistsScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("name") }
    val listState = rememberLazyListState()
    val artists = remember(library, query, sort) {
        val filtered = if (query.isBlank()) library.artists else library.artists.filter { it.matchesQuery(query) }
        when (sort) {
            "songs" -> filtered.sortedByDescending { it.songs.size }
            "albums" -> filtered.sortedByDescending { it.albums.size }
            else -> filtered
        }
    }
    Column(Modifier.fillMaxSize()) {
        BrowseSearchBar(query, { query = it }, "Search an artist…", ArtistSorts, sort, { sort = it })
        if (artists.isEmpty()) {
            EmptyState(Icons.Rounded.Person, if (query.isBlank()) "No artists" else "No matches", if (query.isBlank()) "Artists from your music files will appear here." else "No artist matches “$query”.")
        } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(artists, key = { it.name }) { artist ->
                    BrowseRow(
                        title = artist.name,
                        subtitle = buildString {
                            if (artist.albums.isNotEmpty()) append(if (artist.albums.size == 1) "1 album · " else "${artist.albums.size} albums · ")
                            append(songCount(artist.songs.size))
                        },
                        onClick = { actions.navigate(Route.ArtistDetail(artist.name)) },
                        leading = { ArtImage(artist.art, Modifier.size(52.dp), CircleShape, placeholderIcon = Icons.Rounded.Person) },
                        trailing = { CollectionMenu(vm, artist.songs, artist.name) },
                    )
                }
            }
            if (sort == "name" && query.isBlank()) {
                FastScroller(
                    listState = listState,
                    itemCount = artists.size,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    labelFor = { i -> artists.getOrNull(i)?.let { indexLetter(it.name) } },
                )
            }
        }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Genres
// ---------------------------------------------------------------------------------------------

private val GenreSorts = listOf(SortOption("name", "Name"), SortOption("songs", "Most songs"))

@Composable
fun GenresScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("name") }
    val accent = MaterialTheme.colorScheme.primary
    val genres = remember(library, query, sort) {
        val q = normalizeForSearch(query)
        val filtered = if (q.isBlank()) library.genres else library.genres.filter { normalizeForSearch(it.name).contains(q) }
        if (sort == "songs") filtered.sortedByDescending { it.songs.size } else filtered
    }
    Column(Modifier.fillMaxSize()) {
        BrowseSearchBar(query, { query = it }, "Search a genre…", GenreSorts, sort, { sort = it })
        if (genres.isEmpty()) {
            EmptyState(
                Icons.Rounded.LocalOffer,
                if (query.isBlank()) "No genres" else "No matches",
                if (query.isBlank()) "Genres come from your files' tags (Android 11 and newer)." else "No genre matches “$query”.",
            )
        } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(genres, key = { it.name }) { genre ->
                BrowseRow(
                    title = genre.name,
                    subtitle = songCount(genre.songs.size),
                    onClick = { actions.navigate(Route.GenreDetail(genre.name)) },
                    leading = {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.LocalOffer, null, tint = accent) }
                    },
                    trailing = { CollectionMenu(vm, genre.songs, genre.name) },
                )
            }
        }
        }
    }
}
