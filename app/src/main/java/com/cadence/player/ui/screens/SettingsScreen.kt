package com.cadence.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.AccentColor
import com.cadence.player.data.SongSort
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Route
import com.cadence.player.ui.components.ConfirmDialog
import com.cadence.player.ui.components.DetailTopBar
import com.cadence.player.ui.components.Pill
import com.cadence.player.ui.sheets.SleepTimerSheet
import com.cadence.player.ui.theme.CadenceColors

@Composable
fun SettingsScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val actions = LocalActions.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val eq by vm.eq.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = "Settings", showTitle = true, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SettingsSection("Appearance")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = accent)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Accent color", style = MaterialTheme.typography.bodyLarge)
                        Text(settings.accent.label, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AccentColor.entries.forEach { option ->
                        val color = Color(option.argb)
                        val selected = option == settings.accent
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(3.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                                .clickable { vm.updateSettings { it.copy(accent = option) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, option.label, tint = Color.White)
                        }
                    }
                }
                Divider()
                SwitchRow(
                    icon = Icons.Rounded.Palette,
                    title = "Artwork colors",
                    subtitle = "Tint the player with colors from the album art",
                    checked = settings.tintNowPlaying,
                    onChange = { v -> vm.updateSettings { it.copy(tintNowPlaying = v) } },
                )
            }

            SettingsSection("Library")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = CadenceColors.TextSecondary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Ignore short tracks", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Hides voice notes, ringtones and other short clips",
                                style = MaterialTheme.typography.bodySmall,
                                color = CadenceColors.TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "Off", 15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min").forEach { (sec, label) ->
                            Pill(label, settings.minDurationSec == sec, { vm.updateSettings { it.copy(minDurationSec = sec) } })
                        }
                    }
                }
                Divider()
                Column(Modifier.padding(16.dp)) {
                    Text("Sort songs by", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SongSort.entries.forEach { sort ->
                            Pill(sort.label, settings.songSort == sort, { vm.updateSettings { it.copy(songSort = sort) } })
                        }
                    }
                }
                Divider()
                ClickRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Rescan library",
                    subtitle = "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
                    onClick = {
                        vm.refreshLibrary()
                        actions.showMessage("Rescanning your music…")
                    },
                )
            }

            SettingsSection("Playback")
            SettingsCard {
                ClickRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Equalizer & effects",
                    subtitle = if (eq.enabled) "On · ${eq.preset}" else "Off",
                    onClick = { actions.navigate(Route.Equalizer) },
                )
                Divider()
                ClickRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "Sleep timer",
                    subtitle = if (sleepEndsAt != null) "Active" else "Fade out and stop after a while",
                    onClick = { showSleep = true },
                )
            }

            SettingsSection("Data")
            SettingsCard {
                ClickRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear listening history",
                    subtitle = "Resets Recently played and Most played",
                    onClick = { confirmClear = true },
                )
            }

            SettingsSection("About")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = CadenceColors.TextSecondary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Cadence $version", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Offline music player. Your files never leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadenceColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear listening history?",
            message = "Recently played and play counts will be reset. Playlists and favorites are kept.",
            confirmLabel = "Clear",
            onConfirm = {
                vm.clearHistory()
                confirmClear = false
                actions.showMessage("History cleared")
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (showSleep) SleepTimerSheet(vm, onDismiss = { showSleep = false })
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CadenceColors.Surface)
    ) { content() }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(start = 56.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(CadenceColors.Outline.copy(alpha = 0.6f))
    )
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CadenceColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = CadenceColors.TextTertiary)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CadenceColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = CadenceColors.TextSecondary,
                uncheckedTrackColor = CadenceColors.SurfaceHighest,
                uncheckedBorderColor = CadenceColors.Outline,
            ),
        )
    }
}
