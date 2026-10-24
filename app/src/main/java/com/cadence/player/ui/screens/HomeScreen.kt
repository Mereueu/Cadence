package com.cadence.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.components.CarouselSpacing
import com.cadence.player.ui.components.CoverCard
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.ListBottomPadding
import com.cadence.player.ui.components.ScreenHeader
import com.cadence.player.ui.components.SectionHeader
import com.cadence.player.ui.components.SongRow
import com.cadence.player.ui.theme.CadenceColors
import java.util.Calendar

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun HomeScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val library by vm.library.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val hasLoaded by vm.hasLoadedOnce.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playCounts by vm.playCounts.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()

    val recentSongs = remember(recent, library) { recent.mapNotNull { library.byId[it] }.take(20) }
    val mostPlayed = remember(playCounts, library) {
        playCounts.entries.sortedByDescending { it.value }.mapNotNull { library.byId[it.key] }.take(5)
    }
    val recentAlbums = remember(library) {
        library.albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(14)
    }
    val topArtists = remember(library, playCounts) {
        library.artists
            .sortedByDescending { a -> a.songs.sumOf { playCounts[it.id] ?: 0 } * 10_000 + a.songs.size }
            .take(14)
    }
    val totalDuration = remember(library) { library.songs.sumOf { it.duration } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottomPadding) {
        item(key = "header") {
            ScreenHeader(title = greeting(), subtitle = "Cadence") {
                IconButton(onClick = { actions.navigate(Route.Equalizer) }) {
                    Icon(Icons.Rounded.GraphicEq, "Equalizer")
                }
                IconButton(onClick = { actions.navigate(Route.Settings) }) {
                    Icon(Icons.Rounded.Settings, "Settings")
                }
            }
        }

        when {
            !hasLoaded || (loading && library.songs.isEmpty()) -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Scanning your music…", color = CadenceColors.TextSecondary)
                    }
                }
            }

            library.songs.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "No music found",
                    message = "Copy audio files (MP3, FLAC, M4A, OGG, WAV…) to your phone and they'll show up here automatically.",
                    action = "Scan again",
                    onAction = { vm.refreshLibrary() },
                )
            }

            else -> {
                item(key = "tiles") {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile(Icons.Rounded.Shuffle, "Shuffle all", Color(0xFFFF6B4A), Modifier.weight(1f)) {
                                vm.shuffle(library.songs, "All songs")
                                actions.openPlayer()
                            }
                            QuickTile(Icons.Rounded.Favorite, "Favorites", Color(0xFFFF4D7D), Modifier.weight(1f)) {
                                actions.navigate(Route.SmartDetail(SmartList.FAVORITES))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile(Icons.Rounded.History, "Recently played", Color(0xFF3EA6FF), Modifier.weight(1f)) {
                                actions.navigate(Route.SmartDetail(SmartList.RECENTLY_PLAYED))
                            }
                            QuickTile(Icons.Rounded.TrendingUp, "Most played", Color(0xFF2ED3A0), Modifier.weight(1f)) {
                                actions.navigate(Route.SmartDetail(SmartList.MOST_PLAYED))
                            }
                        }
                    }
                }

                if (recentSongs.isNotEmpty()) {
                    item(key = "recentHeader") {
                        SectionHeader("Jump back in", action = "See all", onAction = {
                            actions.navigate(Route.SmartDetail(SmartList.RECENTLY_PLAYED))
                        })
                    }
                    item(key = "recentRow") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                            itemsIndexed(recentSongs, key = { _, s -> s.id }) { i, song ->
                                CoverCard(
                                    art = song.art,
                                    title = song.title,
                                    subtitle = song.artist,
                                    onClick = { vm.play(recentSongs, i, "Recently played") },
                                    width = 140.dp,
                                )
                            }
                        }
                    }
                }

                item(key = "addedHeader") {
                    SectionHeader("Recently added", action = "See all", onAction = {
                        actions.navigate(Route.SmartDetail(SmartList.RECENTLY_ADDED))
                    })
                }
                item(key = "addedRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                        items(recentAlbums, key = { it.id }) { album ->
                            CoverCard(
                                art = album.art,
                                title = album.title,
                                subtitle = album.artist,
                                onClick = { actions.navigate(Route.AlbumDetail(album.id)) },
                                width = 156.dp,
                            )
                        }
                    }
                }

                if (mostPlayed.isNotEmpty()) {
                    item(key = "repeatHeader") {
                        SectionHeader("On repeat", action = "See all", onAction = {
                            actions.navigate(Route.SmartDetail(SmartList.MOST_PLAYED))
                        })
                    }
                    itemsIndexed(mostPlayed, key = { _, s -> "mp${s.id}" }) { i, song ->
                        val plays = playCounts[song.id] ?: 0
                        SongRow(
                            song = song,
                            isCurrent = playerState.currentId == song.id,
                            isPlaying = playerState.isPlaying,
                            onClick = { vm.play(mostPlayed, i, "Most played") },
                            onMore = { actions.openSongMenu(song, null) },
                            subtitle = "${song.artist} · $plays ${if (plays == 1) "play" else "plays"}",
                        )
                    }
                }

                item(key = "artistsHeader") {
                    SectionHeader("Artists")
                }
                item(key = "artistsRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = CarouselSpacing) {
                        items(topArtists, key = { it.name }) { artist ->
                            CoverCard(
                                art = artist.art,
                                title = artist.name,
                                subtitle = "${artist.songs.size} songs",
                                onClick = { actions.navigate(Route.ArtistDetail(artist.name)) },
                                width = 120.dp,
                                circle = true,
                            )
                        }
                    }
                }

                item(key = "stats") {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CadenceColors.Surface)
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat(library.songs.size.toString(), "Songs")
                        Stat(library.albums.size.toString(), "Albums")
                        Stat(library.artists.size.toString(), "Artists")
                        val hours = totalDuration / 3_600_000L
                        Stat(if (hours > 0) "${hours}h" else "${totalDuration / 60_000L}m", "Of music")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CadenceColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = CadenceColors.TextSecondary)
    }
}
