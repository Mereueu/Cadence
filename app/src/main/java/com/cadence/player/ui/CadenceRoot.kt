package com.cadence.player.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.R
import com.cadence.player.data.Song
import com.cadence.player.ui.player.PlayerScreen
import com.cadence.player.ui.player.QueueScreen
import com.cadence.player.ui.screens.AlbumScreen
import com.cadence.player.ui.screens.AlbumsScreen
import com.cadence.player.ui.screens.ArtistScreen
import com.cadence.player.ui.screens.ArtistsScreen
import com.cadence.player.ui.screens.EqualizerScreen
import com.cadence.player.ui.screens.FolderScreen
import com.cadence.player.ui.screens.FoldersScreen
import com.cadence.player.ui.screens.GenreScreen
import com.cadence.player.ui.screens.GenresScreen
import com.cadence.player.ui.screens.PlaylistScreen
import com.cadence.player.ui.screens.PlaylistsScreen
import com.cadence.player.ui.screens.RadioScreen
import com.cadence.player.ui.theme.LineIcons
import com.cadence.player.ui.dj.DjScreen
import com.cadence.player.ui.custom.AccentSource
import com.cadence.player.ui.custom.Backdrop
import com.cadence.player.ui.custom.HomeDashboard
import com.cadence.player.ui.custom.ThemeGalleryScreen
import com.cadence.player.ui.custom.UiStore
import com.cadence.player.ui.components.rememberArtColor
import com.cadence.player.ui.visualizer.VisualizerScreen
import com.cadence.player.ui.screens.SearchScreen
import com.cadence.player.ui.screens.SettingsScreen
import com.cadence.player.ui.screens.SmartListScreen
import com.cadence.player.ui.sheets.AddToPlaylistSheet
import com.cadence.player.ui.sheets.SleepTimerSheet
import com.cadence.player.ui.sheets.SongMenuSheet
import com.cadence.player.ui.sheets.SpeedSheet
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.CadenceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun CadenceRoot(vm: MusicViewModel, openPlayerRequest: MutableStateFlow<Boolean>) {
    val ui by UiStore.config.collectAsStateWithLifecycle()
    val theme = ui.theme
    val context0 = LocalContext.current
    val currentSong = vm.currentSong.collectAsStateWithLifecycle().value
    val artAccent = rememberArtColor(currentSong?.art, theme.accentSource == AccentSource.ARTWORK)
    val wallpaperAccent = remember(theme.accentSource) {
        if (theme.accentSource == AccentSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.compose.material3.dynamicDarkColorScheme(context0).primary
        } else {
            null
        }
    }
    val targetAccent = when (theme.accentSource) {
        AccentSource.FIXED -> Color(theme.accent)
        AccentSource.ARTWORK -> artAccent?.let { androidx.compose.ui.graphics.lerp(it, Color.White, 0.25f) } ?: Color(theme.accent)
        AccentSource.WALLPAPER -> wallpaperAccent ?: Color(theme.accent)
    }
    LaunchedEffect(theme.backdrop) { CadenceColors.applyBackdrop(theme.backdrop == Backdrop.AMOLED) }
    val accent by animateColorAsState(targetAccent, tween(600), label = "accent")
    CadenceTheme(accent = accent) {
        val context = LocalContext.current
        var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = hasAudioPermission(context) }
        LaunchedEffect(hasPermission) { if (hasPermission) vm.onPermissionGranted() }

        Box(Modifier.fillMaxSize().background(CadenceColors.Background)) {
            if (hasPermission) {
                MainScaffold(vm, openPlayerRequest)
            } else {
                PermissionScreen(onGranted = { hasPermission = true })
            }
        }
    }
}

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var deniedForever by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[audioPermission()] == true) {
            onGranted()
        } else {
            val activity = context.findActivity()
            deniedForever = activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to lerp(CadenceColors.Background, accent, 0.28f),
                    0.55f to CadenceColors.Background,
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A1A3A), Color(0xFF0C0B12)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(150.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text("Welcome to Cadence", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your music, beautifully played. Cadence needs access to the audio files on this device to build your library. Nothing ever leaves your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = CadenceColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Button(
                onClick = {
                    if (deniedForever) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        val perms = buildList {
                            add(audioPermission())
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        launcher.launch(perms.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (deniedForever) "Open settings" else "Allow access to music",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (deniedForever) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Permission was denied. Enable “Music and audio” in Cadence’s app permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CadenceColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: MusicViewModel, openPlayerRequest: MutableStateFlow<Boolean>) {
    val navigator = remember { Navigator(UiStore.config.value.tabs.firstOrNull() ?: Tab.PLAYER) }
    val uiConfig by UiStore.config.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Pair<Song, PlaylistContext?>?>(null) }
    var addToPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showVisualizer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wantsPlayer by openPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(wantsPlayer) {
        if (wantsPlayer) {
            navigator.selectTab(Tab.PLAYER)
            openPlayerRequest.value = false
        }
    }

    val actions = remember(navigator) {
        UiActions(
            navigate = { route -> navigator.push(route) },
            openPlayer = { navigator.selectTab(Tab.PLAYER) },
            openSongMenu = { song, ctx -> menuFor = song to ctx },
            addToPlaylist = { songs -> if (songs.isNotEmpty()) addToPlaylistSongs = songs },
            showMessage = { msg ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(msg)
                }
            },
            selectTab = { navigator.selectTab(it) },
            openSleepTimer = { showSleep = true },
            openSpeed = { showSpeed = true },
            openVisualizer = { showVisualizer = true },
        )
    }

    BackHandler(enabled = navigator.canGoBack && !showVisualizer) { navigator.back() }

    androidx.compose.runtime.CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                val stateHolder = rememberSaveableStateHolder()
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = navigator.current,
                        transitionSpec = {
                            if (targetState is Route.TabRoot && initialState is Route.TabRoot) {
                                fadeIn(tween(160)) togetherWith fadeOut(tween(90))
                            } else {
                                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 12 }) togetherWith
                                    fadeOut(tween(120))
                            }
                        },
                        label = "nav",
                    ) { route ->
                        stateHolder.SaveableStateProvider(route.toString()) {
                            RouteContent(route, vm, onBack = { navigator.back() })
                        }
                    }
                }
                BottomTabs(
                    tabs = uiConfig.tabs,
                    selected = navigator.tab,
                    onSelect = { navigator.selectTab(it) },
                    onMore = { item ->
                        when (item) {
                            MoreItem.SETTINGS -> navigator.push(Route.Settings)
                            MoreItem.DJ -> navigator.push(Route.Dj)
                            MoreItem.CUSTOMIZE -> {
                                navigator.selectTab(Tab.PLAYER)
                                UiStore.setEditing(true)
                            }
                            MoreItem.THEMES -> navigator.push(Route.Themes)
                            MoreItem.VISUALIZER -> showVisualizer = true
                            MoreItem.EQUALIZER -> navigator.push(Route.Equalizer)
                            MoreItem.SLEEP -> showSleep = true
                            MoreItem.SPEED -> showSpeed = true
                            MoreItem.RESCAN -> {
                                vm.refreshLibrary()
                                actions.showMessage("Rescanning your music…")
                            }
                        }
                    },
                )
            }

            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp),
            ) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = CadenceColors.SurfaceHighest,
                    contentColor = CadenceColors.TextPrimary,
                    actionColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        AnimatedVisibility(visible = showVisualizer, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            VisualizerScreen(vm, onClose = { showVisualizer = false })
        }

        menuFor?.let { (song, ctx) ->
            SongMenuSheet(vm, song, ctx, onDismiss = { menuFor = null })
        }
        addToPlaylistSongs?.let { songs ->
            AddToPlaylistSheet(vm, songs, onDismiss = { addToPlaylistSongs = null })
        }
        if (showSleep) SleepTimerSheet(vm, onDismiss = { showSleep = false })
        if (showSpeed) SpeedSheet(vm, onDismiss = { showSpeed = false })
    }
}

@Composable
private fun RouteContent(route: Route, vm: MusicViewModel, onBack: () -> Unit) {
    when (route) {
        is Route.TabRoot -> when (route.tab) {
            Tab.HOME -> HomeDashboard(vm)
            Tab.QUEUE -> QueueScreen(vm)
            Tab.PLAYER -> PlayerScreen(vm)
            Tab.FOLDERS -> FoldersScreen(vm)
            Tab.ALBUMS -> AlbumsScreen(vm)
            Tab.ARTISTS -> ArtistsScreen(vm)
            Tab.GENRES -> GenresScreen(vm)
            Tab.PLAYLISTS -> PlaylistsScreen(vm)
            Tab.RADIO -> RadioScreen(vm)
            Tab.SEARCH -> SearchScreen(vm)
        }
        is Route.AlbumDetail -> AlbumScreen(vm, route.albumId, onBack)
        is Route.ArtistDetail -> ArtistScreen(vm, route.name, onBack)
        is Route.GenreDetail -> GenreScreen(vm, route.name, onBack)
        is Route.FolderDetail -> FolderScreen(vm, route.path, onBack)
        is Route.PlaylistDetail -> PlaylistScreen(vm, route.id, onBack)
        is Route.SmartDetail -> SmartListScreen(vm, route.list, onBack)
        Route.Equalizer -> EqualizerScreen(vm, onBack)
        Route.Settings -> SettingsScreen(vm, onBack)
        Route.Dj -> DjScreen(vm, onBack)
        Route.Themes -> ThemeGalleryScreen(vm, onBack)
    }
}

private enum class MoreItem(val label: String, val icon: ImageVector) {
    DJ("Auto DJ", LineIcons.Dj),
    CUSTOMIZE("Customize player", LineIcons.Edit),
    THEMES("Themes & layout", LineIcons.Palette),
    VISUALIZER("Visualizer", LineIcons.Visualizer),
    EQUALIZER("Equalizer", LineIcons.Equalizer),
    SLEEP("Sleep timer", LineIcons.Sleep),
    SPEED("Playback speed", Icons.Rounded.Speed),
    RESCAN("Rescan library", Icons.Rounded.Refresh),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** Minimal tab bar: thin icons, a hairline on top and a short line under the selected tab. */
@Composable
private fun BottomTabs(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit, onMore: (MoreItem) -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CadenceColors.Background)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F1F)))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val tint by animateColorAsState(
                    if (isSelected) CadenceColors.TextPrimary else Color(0xFF6E6E6E),
                    label = "tabTint",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(1.5.dp)
                            .background(if (isSelected) CadenceColors.TextPrimary else Color.Transparent)
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { moreOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LineIcons.MoreVertical, contentDescription = "More", tint = Color(0xFF6E6E6E), modifier = Modifier.size(23.dp).padding(bottom = 0.dp))
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    MoreItem.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            leadingIcon = { Icon(item.icon, null) },
                            onClick = {
                                moreOpen = false
                                onMore(item)
                            },
                        )
                    }
                }
            }
        }
    }
}
