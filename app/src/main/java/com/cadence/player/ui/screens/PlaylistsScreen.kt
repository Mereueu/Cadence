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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.components.CircleIconButton
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.MosaicArt
import com.cadence.player.ui.components.NameDialog
import com.cadence.player.ui.components.ScreenHeader
import com.cadence.player.ui.components.SectionHeader
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.theme.CadenceColors

@Composable
fun PlaylistsScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playCounts by vm.playCounts.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            ScreenHeader("Playlists") {
                CircleIconButton(
                    Icons.Rounded.Add,
                    "New playlist",
                    onClick = { creating = true },
                    container = accent,
                    tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        item(key = "smart") {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val counts = mapOf(
                    SmartList.FAVORITES to favorites.count { it in library.byId },
                    SmartList.RECENTLY_PLAYED to recent.count { it in library.byId }.coerceAtMost(100),
                    SmartList.MOST_PLAYED to playCounts.keys.count { it in library.byId }.coerceAtMost(100),
                    SmartList.RECENTLY_ADDED to library.songs.size.coerceAtMost(100),
                )
                SmartList.entries.chunked(2).forEach { rowLists ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowLists.forEach { list ->
                            SmartCard(list, counts[list] ?: 0, Modifier.weight(1f)) {
                                actions.navigate(Route.SmartDetail(list))
                            }
                        }
                    }
                }
            }
        }
        item(key = "yours") { SectionHeader("Your playlists") }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Create your first playlist",
                    message = "Group songs for any mood, workout or road trip.",
                    action = "New playlist",
                    onAction = { creating = true },
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                val arts = remember(playlist.songIds, library) {
                    playlist.songIds.asSequence().mapNotNull { library.byId[it]?.art }.take(24).toList()
                }
                val duration = remember(playlist.songIds, library) {
                    playlist.songIds.sumOf { library.byId[it]?.duration ?: 0L }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { actions.navigate(Route.PlaylistDetail(playlist.id)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MosaicArt(arts, Modifier.size(64.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            songCount(playlist.songIds.size) +
                                if (duration > 0) " · ${com.cadence.player.ui.components.formatLongDuration(duration)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadenceColors.TextSecondary,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = CadenceColors.TextTertiary)
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
                val p = vm.createPlaylist(name)
                creating = false
                actions.navigate(Route.PlaylistDetail(p.id))
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SmartCard(list: SmartList, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val (icon, color) = smartStyle(list)
    Box(
        modifier
            .aspectRatio(1.55f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.35f), CadenceColors.Surface)))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(list.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(songCount(count), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}
