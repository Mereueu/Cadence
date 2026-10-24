package com.cadence.player.ui.custom

import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.data.AccentColor
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.Tab
import com.cadence.player.ui.components.DetailTopBar
import com.cadence.player.ui.components.MinimalButton
import com.cadence.player.ui.components.Pill
import com.cadence.player.ui.sheets.CadenceSheet
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.LineIcons
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val COMMUNITY_INDEX = "https://raw.githubusercontent.com/Mereueu/Cadence/main/themes/index.json"

data class CommunityTheme(val theme: ThemeConfig, val description: String)

private sealed interface Gallery {
    data object Loading : Gallery
    data class Loaded(val themes: List<CommunityTheme>) : Gallery
    data class Failed(val message: String) : Gallery
}

private suspend fun fetchCommunityThemes(): List<CommunityTheme> = withContext(Dispatchers.IO) {
    val conn = URL(COMMUNITY_INDEX).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    try {
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("themes")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CommunityTheme(UiStore.parseTheme(o), o.optString("description"))
        }
    } finally {
        conn.disconnect()
    }
}

/** Themes, layouts, tabs and gestures in one place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val config by UiStore.config.collectAsStateWithLifecycle()
    val theme = config.theme
    var gallery by remember { mutableStateOf<Gallery>(Gallery.Loading) }
    var reload by remember { mutableStateOf(0) }
    var showImport by remember { mutableStateOf(false) }
    var gestureToEdit by remember { mutableStateOf<Gesture?>(null) }

    LaunchedEffect(reload) {
        gallery = Gallery.Loading
        gallery = runCatching { fetchCommunityThemes() }
            .fold({ Gallery.Loaded(it) }, { Gallery.Failed("Couldn't load community themes. Check your connection.") })
    }

    fun share(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = "Customize", showTitle = true, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // ---------- Theme ----------
            Section("Theme")
            Label("Button style")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.cadence.player.ui.theme.UiStyle.entries.forEach { st ->
                    StyleCard(st, selected = theme.style == st) {
                        UiStore.setTheme(
                            theme.copy(
                                style = st,
                                name = st.label,
                                accent = st.defaultAccent,
                                accentSource = AccentSource.FIXED,
                            )
                        )
                    }
                }
            }
            Hint(theme.style.description)
            Label("Background")
            PillRow {
                Backdrop.entries.forEach { b ->
                    Pill(b.label, theme.backdrop == b, { UiStore.setTheme(theme.copy(backdrop = b, name = "Custom")) })
                }
            }
            Label("Accent colour")
            PillRow {
                AccentSource.entries.forEach { s ->
                    val available = s != AccentSource.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    if (available) Pill(s.label, theme.accentSource == s, { UiStore.setTheme(theme.copy(accentSource = s, name = "Custom")) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AccentColor.entries.forEach { option ->
                    val selected = theme.accentSource == AccentSource.FIXED && theme.accent == option.argb
                    Swatch(Color(option.argb), selected, 32) {
                        UiStore.setTheme(theme.copy(accent = option.argb, accentSource = AccentSource.FIXED, name = "Custom"))
                        vm.updateSettings { it.copy(accent = option) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MinimalButton("Share theme", LineIcons.Palette, {
                    share(UiStore.themeJson(theme).toString(2), "Share Cadence theme")
                }, Modifier.weight(1f))
                MinimalButton("Import", LineIcons.PlaylistAdd, { showImport = true }, Modifier.weight(1f))
            }

            // ---------- Community ----------
            Section("Community themes")
            when (val g = gallery) {
                Gallery.Loading -> Hint("Loading themes…")
                is Gallery.Failed -> Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(g.message, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    MinimalButton("Try again", null, { reload++ })
                }
                is Gallery.Loaded -> Column {
                    g.themes.forEach { ct ->
                        CommunityRow(ct, applied = ct.theme == theme) {
                            UiStore.setTheme(ct.theme)
                            actions.showMessage("Applied ${ct.theme.name}")
                        }
                    }
                    Hint("Made a theme you like? Share it and send a pull request to the themes folder on GitHub (Mereueu/Cadence).")
                }
            }

            // ---------- Layout ----------
            Section("Interface")
            Label("Layout for")
            PillRow {
                listOf(LayoutPreset.STANDARD, LayoutPreset.HEADPHONES, LayoutPreset.HOME, LayoutPreset.DRIVE).forEach { p ->
                    Pill(p.label, config.preset == p, { UiStore.applyPreset(p) })
                }
            }
            Hint("Each layout arranges the player differently. You can fine-tune any of them.")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MinimalButton("Edit player", LineIcons.Edit, {
                    actions.selectTab(Tab.PLAYER)
                    UiStore.setEditing(true)
                }, Modifier.weight(1f), primary = true)
                MinimalButton("Edit home", LineIcons.Home, {
                    if (Tab.HOME !in config.tabs) UiStore.update { it.copy(tabs = listOf(Tab.HOME) + it.tabs) }
                    actions.selectTab(Tab.HOME)
                    UiStore.setEditingHome(true)
                }, Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MinimalButton("Share layout", null, { share(UiStore.toJson(config), "Share Cadence layout") }, Modifier.weight(1f))
                MinimalButton("Reset all", null, {
                    UiStore.update { UiConfig(theme = it.theme) }
                    actions.showMessage("Layout reset")
                }, Modifier.weight(1f))
            }

            // ---------- Tabs ----------
            Section("Tab bar")
            Hint("Tap to show or hide. Use the arrows to reorder.")
            val shown = config.tabs
            (shown + Tab.entries.filter { it !in shown }).forEach { tab ->
                val index = shown.indexOf(tab)
                TabRow(
                    tab = tab,
                    visible = index >= 0,
                    canUp = index > 0,
                    canDown = index in 0 until shown.lastIndex,
                    onToggle = { UiStore.toggleTab(tab) },
                    onUp = { UiStore.moveTab(index, index - 1) },
                    onDown = { UiStore.moveTab(index, index + 1) },
                )
            }

            // ---------- Gestures ----------
            Section("Player gestures")
            Gesture.entries.forEach { g ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { gestureToEdit = g }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        (config.gestures[g] ?: GestureAction.NONE).label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadenceColors.TextSecondary,
                    )
                }
            }
        }
    }

    gestureToEdit?.let { g ->
        CadenceSheet(onDismiss = { gestureToEdit = null }) {
            Text(
                g.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            GestureAction.entries.forEach { a ->
                val sel = (config.gestures[g] ?: GestureAction.NONE) == a
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            UiStore.setGesture(g, a)
                            gestureToEdit = null
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        a.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sel) MaterialTheme.colorScheme.primary else CadenceColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (sel) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val result = runCatching { JSONObject(text.trim()) }.getOrNull()
                when {
                    result == null -> actions.showMessage("That doesn't look like a Cadence theme")
                    result.has("player") -> {
                        val c = UiStore.fromJson(result.toString())
                        if (c != null) {
                            UiStore.update { c }
                            actions.showMessage("Layout imported")
                        } else {
                            actions.showMessage("Couldn't read that layout")
                        }
                    }
                    else -> {
                        val t = UiStore.parseTheme(result)
                        UiStore.setTheme(t)
                        actions.showMessage("Theme \"${t.name}\" applied")
                    }
                }
                showImport = false
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = CadenceColors.TextTertiary,
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 6.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = CadenceColors.TextSecondary,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = CadenceColors.TextTertiary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .border(1.dp, if (selected) CadenceColors.TextPrimary else Color.Transparent, CircleShape)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CommunityRow(ct: CommunityTheme, applied: Boolean, onApply: () -> Unit) {
    val t = ct.theme
    val bg = if (t.backdrop == Backdrop.AMOLED) Color.Black else Color(0xFF1A1A1D)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .border(1.dp, CadenceColors.Outline, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (t.accentSource == AccentSource.FIXED) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(t.accent)))
            } else {
                Icon(LineIcons.Palette, null, tint = CadenceColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val sub = buildString {
                append("by ${t.author}")
                if (ct.description.isNotBlank()) append(" · ${ct.description}")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary, maxLines = 2)
        }
        Text(
            if (applied) "APPLIED" else "APPLY",
            style = MaterialTheme.typography.labelSmall,
            color = if (applied) CadenceColors.TextTertiary else CadenceColors.TextPrimary,
        )
    }
}

@Composable
private fun TabRow(
    tab: Tab,
    visible: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, null, tint = if (visible) CadenceColors.TextPrimary else CadenceColors.TextTertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (visible) CadenceColors.TextPrimary else CadenceColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        if (visible) {
            ArrowButton("↑", canUp, onUp)
            ArrowButton("↓", canDown, onDown)
        } else {
            Text("HIDDEN", style = MaterialTheme.typography.labelSmall, color = CadenceColors.TextTertiary, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = if (enabled) CadenceColors.TextSecondary else CadenceColors.Outline)
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadenceColors.SurfaceHigh,
        title = { Text("Import theme or layout") },
        text = {
            Column {
                Text(
                    "Paste a theme or layout someone shared with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CadenceColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CadenceColors.TextPrimary,
                        unfocusedBorderColor = CadenceColors.Outline,
                        cursorColor = CadenceColors.TextPrimary,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text("Import", color = CadenceColors.TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CadenceColors.TextSecondary) }
        },
    )
}

/** A live preview of one style: its play button and two side buttons, drawn with that style. */
@Composable
private fun StyleCard(style: com.cadence.player.ui.theme.UiStyle, selected: Boolean, onClick: () -> Unit) {
    val bg = when (style) {
        com.cadence.player.ui.theme.UiStyle.GLASS -> androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF7A3A8F), Color(0xFF3A1B52), Color(0xFF0D0712)))
        com.cadence.player.ui.theme.UiStyle.HIFI -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B1B1B), Color(0xFF121212)))
        else -> androidx.compose.ui.graphics.SolidColor(Color.Black)
    }
    val accent = Color(style.defaultAccent)
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else CadenceColors.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(com.cadence.player.ui.theme.LocalUiStyle provides style) {
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                    primary = accent,
                    onPrimary = Color.Black,
                ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.cadence.player.ui.theme.StyledIconButton(LineIcons.Previous, "Previous", false, 32.dp, 16.dp, emphasis = true) { onClick() }
                    com.cadence.player.ui.theme.StyledPlayButton(playing = false, buffering = false, size = 48.dp) { onClick() }
                    com.cadence.player.ui.theme.StyledIconButton(LineIcons.Next, "Next", false, 32.dp, 16.dp, emphasis = true) { onClick() }
                }
            }
        }
        Text(style.label, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
