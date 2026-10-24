package com.cadence.player.ui.custom

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.vector.ImageVector
import com.cadence.player.ui.Tab
import com.cadence.player.ui.theme.LineIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Building blocks of the Player screen. Users reorder, resize, configure and hide them. */
enum class BlockType(val label: String, val sizes: Boolean, val hasButtons: Boolean = false) {
    TOP_BAR("DJ & visualizer bar", sizes = false),
    ARTWORK("Artwork", sizes = true),
    TITLE("Title & artist", sizes = true),
    INFO("BPM & key", sizes = false),
    SPECTRUM("Live spectrum", sizes = true),
    ACTIONS("Action buttons", sizes = true, hasButtons = true),
    SEEK("Seek bar", sizes = true),
    TRANSPORT("Playback buttons", sizes = true, hasButtons = true),
    UP_NEXT("Up next", sizes = false),
    MODES("Listening modes", sizes = false),
}

enum class BlockSize(val label: String, val scale: Float) {
    S("Small", 0.8f),
    M("Medium", 1f),
    L("Large", 1.35f),
    XL("Huge", 1.8f),
}

enum class ArtStyle(val label: String) {
    STATIC("Still"),
    BREATHE("Breathes with the beat"),
    DRIFT("Slow zoom"),
    VINYL("Spinning vinyl"),
}

enum class PlayerButton(val label: String, val icon: ImageVector) {
    PREV("Previous", LineIcons.Previous),
    PLAY("Play / pause", LineIcons.Play),
    NEXT("Next", LineIcons.Next),
    SHUFFLE("Shuffle", LineIcons.Shuffle),
    REPEAT("Repeat", LineIcons.Repeat),
    BACK10("Back 10 s", LineIcons.Back10),
    FWD30("Forward 30 s", LineIcons.Forward30),
    VOLUME("Volume", LineIcons.Volume),
    EQ("Equalizer", LineIcons.Equalizer),
    SLEEP("Sleep timer", LineIcons.Sleep),
    FAVORITE("Favorite", LineIcons.Heart),
    INFO("Song info", LineIcons.Info),
    ADD("Add to playlist", LineIcons.PlaylistAdd),
    MORE("More", LineIcons.MoreHorizontal),
    QUEUE("Queue", LineIcons.Queue),
    DJ("Auto DJ", LineIcons.Dj),
    VISUALIZER("Visualizer", LineIcons.Visualizer),
    SPEED("Speed", LineIcons.Speed),
}

data class Block(
    val type: BlockType,
    val visible: Boolean = true,
    val size: BlockSize = BlockSize.M,
    val buttons: List<PlayerButton> = emptyList(),
    val artStyle: ArtStyle = ArtStyle.BREATHE,
)

enum class Gesture(val label: String) {
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    DOUBLE_TAP("Double-tap"),
    LONG_PRESS("Long-press"),
}

enum class GestureAction(val label: String) {
    NONE("Nothing"),
    NEXT("Next song"),
    PREVIOUS("Previous song"),
    PLAY_PAUSE("Play / pause"),
    FAVORITE("Favorite"),
    QUEUE("Open queue"),
    SONG_MENU("Song options"),
    VISUALIZER("Visualizer"),
    MIX_NOW("DJ: mix now"),
    SEEK_FWD("Forward 30 s"),
    SEEK_BACK("Back 10 s"),
    SHUFFLE("Toggle shuffle"),
}

enum class Backdrop(val label: String) {
    AMOLED("AMOLED black"),
    DARK("Dark grey"),
}

enum class AccentSource(val label: String) {
    FIXED("Chosen colour"),
    ARTWORK("From album art"),
    WALLPAPER("From wallpaper (Android 12+)"),
}

data class ThemeConfig(
    val name: String = "Mono",
    val backdrop: Backdrop = Backdrop.AMOLED,
    val accentSource: AccentSource = AccentSource.FIXED,
    /** ARGB used when [accentSource] is FIXED. */
    val accent: Long = 0xFFFFFFFF,
    val author: String = "Cadence",
    /** Button and control style (Minimal, Neon, Solid, Glass, Hi-Fi). */
    val style: com.cadence.player.ui.theme.UiStyle = com.cadence.player.ui.theme.UiStyle.MINIMAL,
)

/** Home dashboard widgets. */
enum class WidgetType(val label: String) {
    NOW_PLAYING("Now playing"),
    MODES("Listening modes"),
    SHORTCUTS("Shortcuts"),
    RECENT("Jump back in"),
    FAVORITES("Favorites"),
    MOST_PLAYED("On repeat"),
    NEW_ALBUMS("Recently added"),
    STATS("Library stats"),
}

data class Widget(val type: WidgetType, val visible: Boolean = true, val size: BlockSize = BlockSize.M)

enum class LayoutPreset(val label: String) {
    STANDARD("Standard"),
    HEADPHONES("Headphones"),
    HOME("Home listening"),
    DRIVE("Driving"),
    WALK("Walking"),
    GYM("Gym"),
    SLEEP("Sleep"),
    FOCUS("Focus"),
    PARTY("Party"),
}

data class UiConfig(
    val preset: LayoutPreset = LayoutPreset.STANDARD,
    val player: List<Block> = Presets.player(LayoutPreset.STANDARD),
    val home: List<Widget> = Presets.home(),
    val tabs: List<Tab> = Presets.defaultTabs,
    val gestures: Map<Gesture, GestureAction> = Presets.gestures(LayoutPreset.STANDARD),
    val theme: ThemeConfig = ThemeConfig(),
)

object Presets {
    val defaultTabs = listOf(Tab.HOME, Tab.QUEUE, Tab.PLAYER, Tab.FOLDERS, Tab.ALBUMS, Tab.ARTISTS, Tab.PLAYLISTS, Tab.RADIO, Tab.SEARCH)

    private val standardActions = listOf(PlayerButton.FAVORITE, PlayerButton.INFO, PlayerButton.ADD, PlayerButton.MORE, PlayerButton.SLEEP, PlayerButton.REPEAT, PlayerButton.SHUFFLE)
    private val standardTransport = listOf(PlayerButton.VOLUME, PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT, PlayerButton.EQ)

    fun player(p: LayoutPreset): List<Block> = when (p) {
        LayoutPreset.STANDARD -> listOf(
            Block(BlockType.TOP_BAR),
            Block(BlockType.ARTWORK, artStyle = ArtStyle.BREATHE),
            Block(BlockType.TITLE),
            Block(BlockType.INFO),
            Block(BlockType.ACTIONS, buttons = standardActions),
            Block(BlockType.SEEK),
            Block(BlockType.TRANSPORT, buttons = standardTransport),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.UP_NEXT, visible = false),
            Block(BlockType.MODES, visible = false),
        )
        LayoutPreset.HEADPHONES -> listOf(
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.ARTWORK, size = BlockSize.L, artStyle = ArtStyle.DRIFT),
            Block(BlockType.TITLE, size = BlockSize.L),
            Block(BlockType.SEEK),
            Block(BlockType.TRANSPORT, buttons = listOf(PlayerButton.SHUFFLE, PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT, PlayerButton.REPEAT)),
            Block(BlockType.INFO, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.UP_NEXT, visible = false),
            Block(BlockType.MODES, visible = false),
        )
        LayoutPreset.HOME -> listOf(
            Block(BlockType.TOP_BAR),
            Block(BlockType.ARTWORK, size = BlockSize.M, artStyle = ArtStyle.VINYL),
            Block(BlockType.TITLE),
            Block(BlockType.SPECTRUM, size = BlockSize.M),
            Block(BlockType.SEEK),
            Block(BlockType.TRANSPORT, buttons = standardTransport),
            Block(BlockType.UP_NEXT),
            Block(BlockType.INFO, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.MODES, visible = false),
        )
        LayoutPreset.DRIVE -> listOf(
            Block(BlockType.ARTWORK, size = BlockSize.S, artStyle = ArtStyle.STATIC),
            Block(BlockType.TITLE, size = BlockSize.XL),
            Block(BlockType.SEEK, size = BlockSize.L),
            Block(BlockType.TRANSPORT, size = BlockSize.XL, buttons = listOf(PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT)),
            Block(BlockType.ACTIONS, size = BlockSize.L, buttons = listOf(PlayerButton.FAVORITE, PlayerButton.VOLUME, PlayerButton.SHUFFLE)),
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.INFO, visible = false),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.UP_NEXT, visible = false),
            Block(BlockType.MODES, visible = false),
        )
        LayoutPreset.WALK -> listOf(
            Block(BlockType.ARTWORK, size = BlockSize.M, artStyle = ArtStyle.BREATHE),
            Block(BlockType.TITLE, size = BlockSize.L),
            Block(BlockType.INFO),
            Block(BlockType.SEEK, size = BlockSize.L),
            Block(BlockType.TRANSPORT, size = BlockSize.L, buttons = listOf(PlayerButton.BACK10, PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT, PlayerButton.FWD30)),
            Block(BlockType.MODES),
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.UP_NEXT, visible = false),
        )
        LayoutPreset.GYM -> listOf(
            Block(BlockType.SPECTRUM, size = BlockSize.L),
            Block(BlockType.TITLE, size = BlockSize.L),
            Block(BlockType.INFO),
            Block(BlockType.TRANSPORT, size = BlockSize.XL, buttons = listOf(PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT)),
            Block(BlockType.UP_NEXT),
            Block(BlockType.MODES),
            Block(BlockType.ARTWORK, visible = false),
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.SEEK, visible = false),
        )
        LayoutPreset.SLEEP -> listOf(
            Block(BlockType.ARTWORK, size = BlockSize.S, artStyle = ArtStyle.DRIFT),
            Block(BlockType.TITLE, size = BlockSize.S),
            Block(BlockType.TRANSPORT, size = BlockSize.M, buttons = listOf(PlayerButton.SLEEP, PlayerButton.PLAY, PlayerButton.VOLUME)),
            Block(BlockType.MODES),
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.INFO, visible = false),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.SEEK, visible = false),
            Block(BlockType.UP_NEXT, visible = false),
        )
        LayoutPreset.FOCUS -> listOf(
            Block(BlockType.ARTWORK, size = BlockSize.M, artStyle = ArtStyle.STATIC),
            Block(BlockType.TITLE, size = BlockSize.S),
            Block(BlockType.SEEK, size = BlockSize.S),
            Block(BlockType.TRANSPORT, size = BlockSize.S, buttons = listOf(PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT)),
            Block(BlockType.MODES),
            Block(BlockType.TOP_BAR, visible = false),
            Block(BlockType.INFO, visible = false),
            Block(BlockType.SPECTRUM, visible = false),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.UP_NEXT, visible = false),
        )
        LayoutPreset.PARTY -> listOf(
            Block(BlockType.TOP_BAR),
            Block(BlockType.ARTWORK, size = BlockSize.M, artStyle = ArtStyle.VINYL),
            Block(BlockType.TITLE, size = BlockSize.L),
            Block(BlockType.INFO),
            Block(BlockType.SPECTRUM, size = BlockSize.M),
            Block(BlockType.TRANSPORT, buttons = listOf(PlayerButton.DJ, PlayerButton.PREV, PlayerButton.PLAY, PlayerButton.NEXT, PlayerButton.VISUALIZER)),
            Block(BlockType.UP_NEXT),
            Block(BlockType.ACTIONS, visible = false, buttons = standardActions),
            Block(BlockType.SEEK, visible = false),
            Block(BlockType.MODES, visible = false),
        )
    }

    fun gestures(p: LayoutPreset): Map<Gesture, GestureAction> = when (p) {
        LayoutPreset.DRIVE -> mapOf(
            Gesture.SWIPE_LEFT to GestureAction.NEXT,
            Gesture.SWIPE_RIGHT to GestureAction.PREVIOUS,
            Gesture.DOUBLE_TAP to GestureAction.PLAY_PAUSE,
            Gesture.SWIPE_UP to GestureAction.NONE,
            Gesture.SWIPE_DOWN to GestureAction.NONE,
            Gesture.LONG_PRESS to GestureAction.NONE,
        )
        LayoutPreset.HEADPHONES, LayoutPreset.WALK, LayoutPreset.GYM -> mapOf(
            Gesture.SWIPE_LEFT to GestureAction.NEXT,
            Gesture.SWIPE_RIGHT to GestureAction.PREVIOUS,
            Gesture.SWIPE_UP to GestureAction.QUEUE,
            Gesture.SWIPE_DOWN to GestureAction.SEEK_BACK,
            Gesture.DOUBLE_TAP to GestureAction.PLAY_PAUSE,
            Gesture.LONG_PRESS to GestureAction.FAVORITE,
        )
        else -> mapOf(
            Gesture.SWIPE_LEFT to GestureAction.NEXT,
            Gesture.SWIPE_RIGHT to GestureAction.PREVIOUS,
            Gesture.SWIPE_UP to GestureAction.QUEUE,
            Gesture.SWIPE_DOWN to GestureAction.NONE,
            Gesture.DOUBLE_TAP to GestureAction.FAVORITE,
            Gesture.LONG_PRESS to GestureAction.VISUALIZER,
        )
    }

    fun home(): List<Widget> = listOf(
        Widget(WidgetType.NOW_PLAYING),
        Widget(WidgetType.MODES),
        Widget(WidgetType.SHORTCUTS),
        Widget(WidgetType.RECENT),
        Widget(WidgetType.FAVORITES, size = BlockSize.S),
        Widget(WidgetType.MOST_PLAYED),
        Widget(WidgetType.NEW_ALBUMS),
        Widget(WidgetType.STATS),
    )
}

/** Persists the user's interface configuration and exposes it as state. */
object UiStore {
    private var prefs: SharedPreferences? = null
    private val _config = MutableStateFlow(UiConfig())
    val config: StateFlow<UiConfig> = _config.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        prefs = p
        p.getString("config", null)?.let { json -> fromJson(json)?.let { _config.value = it } }
    }

    fun update(transform: (UiConfig) -> UiConfig) {
        _config.value = transform(_config.value)
        prefs?.edit()?.putString("config", toJson(_config.value))?.apply()
    }

    fun applyPreset(p: LayoutPreset) = update {
        it.copy(preset = p, player = Presets.player(p), gestures = Presets.gestures(p))
    }

    // ---- Edit mode ----

    private val _editingPlayer = MutableStateFlow(false)
    val editingPlayer: StateFlow<Boolean> = _editingPlayer.asStateFlow()
    fun setEditing(on: Boolean) { _editingPlayer.value = on }

    private val _editingHome = MutableStateFlow(false)
    val editingHome: StateFlow<Boolean> = _editingHome.asStateFlow()
    fun setEditingHome(on: Boolean) { _editingHome.value = on }

    // ---- Player blocks ----

    private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices) return this
        val m = toMutableList()
        val x = m.removeAt(from)
        m.add(to, x)
        return m
    }

    private fun editBlock(type: BlockType, t: (Block) -> Block) = update { c ->
        c.copy(player = c.player.map { if (it.type == type) t(it) else it })
    }

    fun moveBlock(from: Int, to: Int) = update { it.copy(player = it.player.moved(from, to)) }
    fun toggleBlock(type: BlockType) = editBlock(type) { it.copy(visible = !it.visible) }
    fun cycleSize(type: BlockType) = editBlock(type) { it.copy(size = BlockSize.entries[(it.size.ordinal + 1) % BlockSize.entries.size]) }
    fun setArtStyle(style: ArtStyle) = editBlock(BlockType.ARTWORK) { it.copy(artStyle = style) }
    fun toggleButton(type: BlockType, b: PlayerButton) = editBlock(type) { blk ->
        val limit = if (type == BlockType.TRANSPORT) 7 else 8
        when {
            b in blk.buttons -> blk.copy(buttons = blk.buttons - b)
            blk.buttons.size < limit -> blk.copy(buttons = blk.buttons + b)
            else -> blk
        }
    }
    fun moveButton(type: BlockType, from: Int, to: Int) = editBlock(type) { it.copy(buttons = it.buttons.moved(from, to)) }

    // ---- Home widgets ----

    private fun editWidget(type: WidgetType, t: (Widget) -> Widget) = update { c ->
        c.copy(home = c.home.map { if (it.type == type) t(it) else it })
    }

    fun moveWidget(from: Int, to: Int) = update { it.copy(home = it.home.moved(from, to)) }
    fun toggleWidget(type: WidgetType) = editWidget(type) { it.copy(visible = !it.visible) }
    fun cycleWidgetSize(type: WidgetType) = editWidget(type) {
        it.copy(size = listOf(BlockSize.S, BlockSize.M, BlockSize.L)[(listOf(BlockSize.S, BlockSize.M, BlockSize.L).indexOf(it.size) + 1) % 3])
    }

    // ---- Tabs & gestures ----

    fun toggleTab(tab: Tab) = update { c ->
        val tabs = if (tab in c.tabs) {
            if (c.tabs.size <= 2 || tab == Tab.PLAYER) c.tabs else c.tabs - tab
        } else {
            c.tabs + tab
        }
        c.copy(tabs = tabs)
    }
    fun moveTab(from: Int, to: Int) = update { it.copy(tabs = it.tabs.moved(from, to)) }
    fun setGesture(g: Gesture, a: GestureAction) = update { it.copy(gestures = it.gestures + (g to a)) }
    fun setTheme(t: ThemeConfig) = update { it.copy(theme = t) }

    // ---- JSON (also used for sharing themes) ----

    fun toJson(c: UiConfig): String = JSONObject()
        .put("v", 1)
        .put("preset", c.preset.name)
        .put("player", JSONArray(c.player.map { b ->
            JSONObject()
                .put("type", b.type.name)
                .put("visible", b.visible)
                .put("size", b.size.name)
                .put("art", b.artStyle.name)
                .put("buttons", JSONArray(b.buttons.map { it.name }))
        }))
        .put("home", JSONArray(c.home.map { w -> JSONObject().put("type", w.type.name).put("visible", w.visible).put("size", w.size.name) }))
        .put("tabs", JSONArray(c.tabs.map { it.name }))
        .put("gestures", JSONObject().apply { c.gestures.forEach { (g, a) -> put(g.name, a.name) } })
        .put("theme", themeJson(c.theme))
        .toString()

    fun themeJson(t: ThemeConfig): JSONObject = JSONObject()
        .put("name", t.name)
        .put("author", t.author)
        .put("backdrop", t.backdrop.name)
        .put("accentSource", t.accentSource.name)
        .put("accent", String.format("#%08X", t.accent))
        .put("style", t.style.name)

    fun parseTheme(o: JSONObject): ThemeConfig {
        val d = ThemeConfig()
        val accent = o.optString("accent").removePrefix("#").let { hex ->
            runCatching { (if (hex.length == 6) "FF$hex" else hex).toLong(16) }.getOrNull()
        } ?: d.accent
        return ThemeConfig(
            name = o.optString("name", d.name),
            author = o.optString("author", d.author),
            backdrop = enumOr(o.optString("backdrop"), d.backdrop),
            accentSource = enumOr(o.optString("accentSource"), d.accentSource),
            accent = accent,
            style = enumOr(o.optString("style"), d.style),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)

    fun fromJson(text: String): UiConfig? = runCatching {
        val o = JSONObject(text)
        val d = UiConfig()
        val playerArr = o.optJSONArray("player")
        val player = if (playerArr == null) d.player else List(playerArr.length()) { i ->
            val b = playerArr.getJSONObject(i)
            val btns = b.optJSONArray("buttons") ?: JSONArray()
            Block(
                type = enumOr(b.optString("type"), BlockType.TITLE),
                visible = b.optBoolean("visible", true),
                size = enumOr(b.optString("size"), BlockSize.M),
                artStyle = enumOr(b.optString("art"), ArtStyle.BREATHE),
                buttons = List(btns.length()) { k -> enumOr(btns.optString(k), PlayerButton.PLAY) }.distinct(),
            )
        }.distinctBy { it.type }.let { list ->
            // Keep every block type present (hidden if missing) so the editor can re-add it.
            list + BlockType.entries.filter { t -> list.none { it.type == t } }.map { Block(it, visible = false) }
        }
        val homeArr = o.optJSONArray("home")
        val home = if (homeArr == null) d.home else List(homeArr.length()) { i ->
            val w = homeArr.getJSONObject(i)
            Widget(enumOr(w.optString("type"), WidgetType.STATS), w.optBoolean("visible", true), enumOr(w.optString("size"), BlockSize.M))
        }.distinctBy { it.type }.let { list -> list + WidgetType.entries.filter { t -> list.none { it.type == t } }.map { Widget(it, visible = false) } }
        val tabsArr = o.optJSONArray("tabs")
        val tabs = if (tabsArr == null) d.tabs else List(tabsArr.length()) { enumOr(tabsArr.optString(it), Tab.PLAYER) }.distinct()
        val g = o.optJSONObject("gestures")
        val gestures = if (g == null) d.gestures else Gesture.entries.associateWith { enumOr(g.optString(it.name), GestureAction.NONE) }
        UiConfig(
            preset = enumOr(o.optString("preset"), LayoutPreset.STANDARD),
            player = player,
            home = home,
            tabs = tabs.ifEmpty { d.tabs },
            gestures = gestures,
            theme = o.optJSONObject("theme")?.let { parseTheme(it) } ?: d.theme,
        )
    }.getOrNull()
}
