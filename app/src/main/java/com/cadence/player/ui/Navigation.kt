package com.cadence.player.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.cadence.player.data.Song

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    LIBRARY("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    PLAYLISTS("Playlists", Icons.Outlined.QueueMusic, Icons.Rounded.QueueMusic),
    SEARCH("Search", Icons.Outlined.Search, Icons.Rounded.Search),
}

enum class SmartList(val title: String) {
    FAVORITES("Favorites"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    RECENTLY_ADDED("Recently added"),
}

sealed interface Route {
    data class TabRoot(val tab: Tab) : Route
    data class AlbumDetail(val albumId: Long) : Route
    data class ArtistDetail(val name: String) : Route
    data class FolderDetail(val path: String) : Route
    data class PlaylistDetail(val id: String) : Route
    data class SmartDetail(val list: SmartList) : Route
    data object Equalizer : Route
    data object Settings : Route
}

class Navigator {
    var tab by mutableStateOf(Tab.HOME)
        private set
    val stack = mutableStateListOf<Route>(Route.TabRoot(Tab.HOME))

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != Tab.HOME

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
        tab != Tab.HOME -> {
            selectTab(Tab.HOME); true
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
)

val LocalActions = compositionLocalOf<UiActions> { error("UiActions not provided") }
