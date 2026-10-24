package com.cadence.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.components.CarouselSpacing
import com.cadence.player.ui.components.CoverCard
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.ScreenHeader
import com.cadence.player.ui.components.SectionHeader
import com.cadence.player.ui.components.SongRow
import com.cadence.player.ui.components.matchesQuery
import com.cadence.player.ui.components.searchScore
import com.cadence.player.ui.sheets.SearchField
import com.cadence.player.ui.theme.CadenceColors

@Composable
fun SearchScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val q = query.trim()

    val songs = remember(library, q) {
        if (q.isEmpty()) emptyList()
        else library.songs.filter { it.matchesQuery(q) }.sortedBy { searchScore(it.title, q) }.take(200)
    }
    val albums = remember(library, q) {
        if (q.isEmpty()) emptyList()
        else library.albums.filter { it.matchesQuery(q) }.sortedBy { searchScore(it.title, q) }.take(30)
    }
    val artists = remember(library, q) {
        if (q.isEmpty()) emptyList()
        else library.artists.filter { it.matchesQuery(q) }.sortedBy { searchScore(it.name, q) }.take(30)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Songs, artists, albums",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (q.isEmpty()) {
                item(key = "browse") { SectionHeader("Browse") }
                item(key = "browseGrid") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrowseTile("Shuffle everything", Icons.Rounded.Shuffle, Color(0xFFFF6B4A), Modifier.weight(1f)) {
                                vm.shuffle(library.songs, "All songs")
                                actions.openPlayer()
                            }
                            BrowseTile("New arrivals", Icons.Rounded.NewReleases, Color(0xFFFFB020), Modifier.weight(1f)) {
                                actions.navigate(Route.SmartDetail(SmartList.RECENTLY_ADDED))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrowseTile("Albums", Icons.Rounded.Album, Color(0xFF9D7BFF), Modifier.weight(1f)) {
                                library.albums.randomOrNull()?.let { actions.navigate(Route.AlbumDetail(it.id)) }
                            }
                            BrowseTile("Folders", Icons.Rounded.Folder, Color(0xFF3EA6FF), Modifier.weight(1f)) {
                                library.folders.maxByOrNull { it.songs.size }?.let { actions.navigate(Route.FolderDetail(it.path)) }
                            }
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Tip: search ignores accents, so “sa” finds “Șa” and “beyonce” finds “Beyoncé”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CadenceColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    )
                }
            } else if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                item(key = "none") {
                    EmptyState(Icons.Rounded.SearchOff, "No results", "Nothing matches “$q”. Try a different spelling.")
                }
            } else {
                if (artists.isNotEmpty()) {
                    item(key = "artistsH") { SectionHeader("Artists") }
                    item(key = "artists") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                            items(artists, key = { it.name }) { artist ->
                                CoverCard(
                                    art = artist.art,
                                    title = artist.name,
                                    subtitle = "${artist.songs.size} songs",
                                    onClick = {
                                        focusManager.clearFocus()
                                        actions.navigate(Route.ArtistDetail(artist.name))
                                    },
                                    width = 110.dp,
                                    circle = true,
                                )
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "albumsH") { SectionHeader("Albums") }
                    item(key = "albums") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                            items(albums, key = { it.id }) { album ->
                                CoverCard(
                                    art = album.art,
                                    title = album.title,
                                    subtitle = album.artist,
                                    onClick = {
                                        focusManager.clearFocus()
                                        actions.navigate(Route.AlbumDetail(album.id))
                                    },
                                    width = 140.dp,
                                )
                            }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item(key = "songsH") { SectionHeader("Songs") }
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                        SongRow(
                            song = song,
                            isCurrent = playerState.currentId == song.id,
                            isPlaying = playerState.isPlaying,
                            onClick = {
                                focusManager.clearFocus()
                                vm.play(songs, i, "Search: $q")
                            },
                            onMore = { actions.openSongMenu(song, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.4f))))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Icon(
            icon,
            null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(34.dp),
        )
    }
}

