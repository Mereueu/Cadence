package com.cadence.player.ui.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.Song
import com.cadence.player.modes.ModeEngine
import com.cadence.player.playback.AutoDj
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.Tab
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.MinimalButton
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.LineIcons
import java.util.Calendar

private val Dim = Color(0xFF8C8C8C)

/** A home dashboard made of widgets the user can reorder, resize and hide. */
@Composable
fun HomeDashboard(vm: MusicViewModel) {
    val actions = LocalActions.current
    val ui by UiStore.config.collectAsStateWithLifecycle()
    val editing by UiStore.editingHome.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val playCounts by vm.playCounts.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val mode by ModeEngine.mode.collectAsStateWithLifecycle()
    val current = vm.currentSong.collectAsStateWithLifecycle().value

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Good night"
        }
    }
    val widgets = if (editing) ui.home else ui.home.filter { it.visible }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                if (editing) {
                    MinimalButton("Done", null, { UiStore.setEditingHome(false) }, primary = true)
                } else {
                    Icon(
                        LineIcons.Edit,
                        contentDescription = "Customize home",
                        tint = Dim,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { UiStore.setEditingHome(true) }.padding(12.dp).size(20.dp),
                    )
                }
            }
        }
        itemsIndexed(widgets, key = { _, w -> w.type.name }) { index, w ->
            val body: @Composable () -> Unit = {
                when (w.type) {
                    WidgetType.NOW_PLAYING -> NowPlayingCard(current, player.isPlaying, w.size, onOpen = { actions.selectTab(Tab.PLAYER) }, onToggle = { vm.player.togglePlayPause() })
                    WidgetType.MODES -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        WidgetTitle("Listening modes")
                        ModesBlock(mode) { vm.toggleMode(it) }
                    }
                    WidgetType.SHORTCUTS -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MinimalButton("Shuffle", LineIcons.Shuffle, { vm.shuffle(library.songs, "All songs"); actions.selectTab(Tab.PLAYER) }, Modifier.weight(1f))
                        MinimalButton("Auto DJ", LineIcons.Dj, { AutoDj.updateConfig { it.copy(enabled = true) }; actions.navigate(Route.Dj) }, Modifier.weight(1f))
                        MinimalButton("Visuals", LineIcons.Visualizer, { actions.openVisualizer() }, Modifier.weight(1f))
                    }
                    WidgetType.RECENT -> SongCarousel("Jump back in", recent.mapNotNull { vm.songById(it) }.take(20), w.size, onSeeAll = { actions.navigate(Route.SmartDetail(SmartList.RECENTLY_PLAYED)) }) { list, i -> vm.play(list, i, "Recently played") }
                    WidgetType.FAVORITES -> SongCarousel("Favorites", favorites.mapNotNull { vm.songById(it) }.take(20), w.size, onSeeAll = { actions.navigate(Route.SmartDetail(SmartList.FAVORITES)) }) { list, i -> vm.play(list, i, "Favorites") }
                    WidgetType.MOST_PLAYED -> SongCarousel(
                        "On repeat",
                        playCounts.entries.sortedByDescending { it.value }.mapNotNull { vm.songById(it.key) }.take(20),
                        w.size,
                        onSeeAll = { actions.navigate(Route.SmartDetail(SmartList.MOST_PLAYED)) },
                    ) { list, i -> vm.play(list, i, "Most played") }
                    WidgetType.NEW_ALBUMS -> {
                        val albums = remember(library) { library.albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(16) }
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp)) { WidgetTitle("Recently added") }
                            val side = (110 * w.size.scale).dp
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(albums, key = { _, a -> a.id }) { _, a ->
                                    Column(Modifier.width(side).clickable { actions.navigate(Route.AlbumDetail(a.id)) }) {
                                        ArtImage(a.art, Modifier.size(side), RoundedCornerShape(2.dp))
                                        Text(a.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                    WidgetType.STATS -> Row(
                        Modifier.fillMaxWidth().padding(16.dp).border(1.dp, CadenceColors.Outline, RoundedCornerShape(4.dp)).padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat(library.songs.size.toString(), "songs")
                        Stat(library.albums.size.toString(), "albums")
                        Stat(library.artists.size.toString(), "artists")
                        Stat((library.songs.sumOf { it.duration } / 3_600_000L).toString() + "h", "of music")
                    }
                }
            }
            if (editing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = Color.White.copy(alpha = if (w.visible) 0.6f else 0.25f),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            )
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w.type.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (w.visible) CadenceColors.TextPrimary else Dim, modifier = Modifier.weight(1f))
                        EditChip("↑", index > 0) { UiStore.moveWidget(index, index - 1) }
                        EditChip("↓", index < ui.home.size - 1) { UiStore.moveWidget(index, index + 1) }
                        EditChip(w.size.name, true) { UiStore.cycleWidgetSize(w.type) }
                        EditChip(if (w.visible) "Hide" else "Show", true) { UiStore.toggleWidget(w.type) }
                    }
                    if (w.visible) body()
                }
            } else {
                body()
            }
        }
    }
}

@Composable
private fun EditChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) CadenceColors.TextPrimary else Color(0xFF444444),
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) CadenceColors.Outline else Color(0xFF222222), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun WidgetTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Dim)
    }
}

@Composable
private fun NowPlayingCard(song: Song?, playing: Boolean, size: BlockSize, onOpen: () -> Unit, onToggle: () -> Unit) {
    if (song == null) return
    val art = (64 * size.scale).dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, CadenceColors.Outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(song.art, Modifier.size(art), RoundedCornerShape(2.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Dim)
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            if (playing) LineIcons.Pause else LineIcons.Play,
            contentDescription = if (playing) "Pause" else "Play",
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onToggle).padding(12.dp).size(24.dp),
        )
    }
}

@Composable
private fun SongCarousel(title: String, songs: List<Song>, size: BlockSize, onSeeAll: () -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    if (songs.isEmpty()) return
    val side = (120 * size.scale).dp
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { WidgetTitle(title) }
            Text("SEE ALL", style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.clickable(onClick = onSeeAll).padding(bottom = 8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                Column(Modifier.width(side).clickable { onPlay(songs, i) }) {
                    ArtImage(s.art, Modifier.size(side), RoundedCornerShape(2.dp))
                    Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(s.artist, style = MaterialTheme.typography.labelSmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

