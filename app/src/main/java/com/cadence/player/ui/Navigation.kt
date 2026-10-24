package com.cadence.player.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.cadence.player.data.Song
import com.cadence.player.ui.theme.LineIcons

/** Bottom bar tabs, left to right. "More" is a menu, not a tab. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", LineIcons.Home),
    QUEUE("Queue", LineIcons.Queue),
    PLAYER("Now playing", LineIcons.NowPlaying),
    FOLDERS("Folders", LineIcons.Folder),
    ALBUMS("Albums", LineIcons.Album),
    ARTISTS("Artists", LineIcons.Artist),
    GENRES("Genres", LineIcons.Genre),
    PLAYLISTS("Playlists", LineIcons.Playlists),
    RADIO("Radio", LineIcons.Radio),
    SEARCH("Search", LineIcons.Search),
}

enum class SmartList(val title: String) {
    ALL_SONGS("All songs"),
    FAVORITES("Favorites"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    NOT_PLAYED("Not played"),
}

sealed interface Route {
    data class TabRoot(val tab: Tab) : Route
    data class AlbumDetail(val albumId: Long) : Route
    data class ArtistDetail(val name: String) : Route
    data class GenreDetail(val name: String) : Route
    data class FolderDetail(val path: String) : Route
    data class PlaylistDetail(val id: String) : Route
    data class SmartDetail(val list: SmartList) : Route
    data object Equalizer : Route
    data object Settings : Route
    data object Dj : Route
    data object Themes : Route
}

class Navigator(private val home: Tab = Tab.PLAYLISTS) {
    var tab by mutableStateOf(home)
        private set
    val stack = mutableStateListOf<Route>(Route.TabRoot(home))

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != home

    fun selectTab(newTab: Tab) {
        if (newTab == tab && stack.size > 1) {
            // Re-selecting the current tab returns to its root.
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            return
        }
        tab = newTab
        stack.clear()
        stack.add(Route.TabRoot(newTab))
    }

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    /** Returns false when there is nowhere left to go back to. */
    fun back(): Boolean = when {
        stack.size > 1 -> {
            stack.removeAt(stack.lastIndex); true
        }
        tab != home -> {
            selectTab(home); true
        }
        else -> false
    }
}

/** Where a song row was shown, so its menu can offer context-specific actions. */
data class PlaylistContext(val playlistId: String, val index: Int, val size: Int)

/** App-wide UI actions, provided to every screen through [LocalActions]. */
class UiActions(
    val navigate: (Route) -> Unit,
    val openPlayer: () -> Unit,
    val openSongMenu: (Song, PlaylistContext?) -> Unit,
    val addToPlaylist: (List<Song>) -> Unit,
    val showMessage: (String) -> Unit,
    val selectTab: (Tab) -> Unit = {},
    val openSleepTimer: () -> Unit = {},
    val openSpeed: () -> Unit = {},
    val openVisualizer: () -> Unit = {},
)

val LocalActions = compositionLocalOf<UiActions> { error("UiActions not provided") }
