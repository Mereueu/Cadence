package com.cadence.player.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatListBulleted
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.SmartList
import com.cadence.player.ui.components.NameDialog
import com.cadence.player.ui.components.songCount
import com.cadence.player.ui.theme.CadenceColors

@Composable
fun PlaylistsScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        items(SmartList.entries, key = { "smart:${it.name}" }) { list ->
            ListRow(
                icon = smartStyle(list).first,
                title = list.title,
                onClick = { actions.navigate(Route.SmartDetail(list)) },
            )
        }
        item(key = "header") {
            Text(
                "Your playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            val available = remember(playlist.songIds, library) { playlist.songIds.count { it in library.byId } }
            ListRow(
                icon = Icons.Rounded.FormatListBulleted,
                title = playlist.name,
                subtitle = songCount(available),
                onClick = { actions.navigate(Route.PlaylistDetail(playlist.id)) },
            )
        }
        item(key = "new") {
            Row(
                Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CadenceColors.Outline, RoundedCornerShape(8.dp))
                    .clickable { creating = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Text("New Playlist", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
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
private fun ListRow(icon: ImageVector, title: String, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CadenceColors.TextSecondary)
        }
    }
}
