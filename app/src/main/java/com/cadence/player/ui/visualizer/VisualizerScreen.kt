package com.cadence.player.ui.visualizer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.audio.LiveAudio
import com.cadence.player.ui.sheets.CadenceSheet
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.LineIcons
import kotlinx.coroutines.delay
import org.json.JSONArray

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** What the visualizer remembers between sessions. */
private class MilkPrefs(context: Context) {
    private val p = context.getSharedPreferences("milkdrop", Context.MODE_PRIVATE)
    var favorites: Set<String>
        get() = p.getStringSet("favorites", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("favorites", v).apply()
    var cycleSeconds: Int
        get() = p.getInt("cycle", 30)
        set(v) = p.edit().putInt("cycle", v).apply()
    var favoritesOnly: Boolean
        get() = p.getBoolean("fav_only", false)
        set(v) = p.edit().putBoolean("fav_only", v).apply()
    var perSong: Boolean
        get() = p.getBoolean("per_song", true)
        set(v) = p.edit().putBoolean("per_song", v).apply()
    var quality: Float
        get() = p.getFloat("quality", 0.75f)
        set(v) = p.edit().putFloat("quality", v).apply()
    var last: String?
        get() = p.getString("last", null)
        set(v) = p.edit().putString("last", v).apply()
}

/** JavaScript ↔ app bridge. JS calls arrive on a background thread, so state updates hop to the main thread. */
private class MilkBridge(
    private val qualityOf: () -> Float,
    private val readyCb: (List<String>) -> Unit,
    private val presetCb: (String) -> Unit,
    private val errorCb: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun frame(): String = LiveAudio.timeDomainBase64() ?: ""

    @JavascriptInterface
    fun quality(): String = qualityOf().toString()

    @JavascriptInterface
    fun onReady(json: String) {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: JSONArray()
        val names = List(arr.length()) { arr.getString(it) }
        main.post { readyCb(names) }
    }

    @JavascriptInterface
    fun onPreset(name: String) {
        main.post { presetCb(name) }
    }

    @JavascriptInterface
    fun onError(message: String) {
        main.post { errorCb(message) }
    }
}

private fun jsString(s: String): String = JSONArray().put(s).toString().let { it.substring(1, it.length - 1) }

/**
 * Full-screen MilkDrop visuals (the classic Winamp visualizer) powered by Butterchurn and the
 * community preset packs. Tap to show the controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VisualizerScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { MilkPrefs(context) }
    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val radio = vm.currentRadio.collectAsStateWithLifecycle().value

    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(prefs.favorites) }
    var cycle by remember { mutableStateOf(prefs.cycleSeconds) }
    var favOnly by remember { mutableStateOf(prefs.favoritesOnly) }
    var perSong by remember { mutableStateOf(prefs.perSong) }
    var quality by remember { mutableStateOf(prefs.quality) }
    var controls by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun pool(): List<String> = if (favOnly && favorites.isNotEmpty()) names.filter { it in favorites } else names

    fun load(name: String, blend: Float = 2.7f) {
        webView?.evaluateJavascript("window.cadence && window.cadence.load(${jsString(name)}, $blend)", null)
    }

    fun random() {
        val p = pool().filter { it != current }
        if (p.isNotEmpty()) load(p.random())
    }

    fun next(step: Int) {
        val p = pool()
        if (p.isEmpty()) return
        val i = p.indexOf(current)
        load(p[((if (i < 0) 0 else i + step) % p.size + p.size) % p.size], 1.2f)
    }

    // Live audio, screen on and immersive full-screen only while the visualizer is open.
    DisposableEffect(Unit) {
        LiveAudio.enabled = true
        view.keepScreenOn = true
        val window = context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            LiveAudio.enabled = false
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler { if (showList) showList = false else onClose() }

    // First preset once the engine is ready: the last one used, else a random one.
    LaunchedEffect(names) {
        if (names.isNotEmpty() && current == null) {
            val last = prefs.last
            if (last != null && last in names) load(last, 0f) else random()
        }
    }
    // Automatic preset changes.
    LaunchedEffect(cycle, current, favOnly) {
        if (cycle > 0 && current != null) {
            delay(cycle * 1000L)
            random()
        }
    }
    LaunchedEffect(song?.id) {
        if (perSong && song != null && current != null) random()
    }
    // Controls hide themselves after a few seconds.
    LaunchedEffect(controls, current) {
        if (controls && !showList) {
            delay(5000)
            controls = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = true
                    addJavascriptInterface(
                        MilkBridge(
                            qualityOf = { quality },
                            readyCb = { names = it },
                            presetCb = { n ->
                                current = n
                                prefs.last = n
                                history = (history + n).takeLast(50)
                            },
                            errorCb = { error = it },
                        ),
                        "CadenceMilk",
                    )
                    loadUrl("file:///android_asset/milkdrop/index.html")
                    webView = this
                }
            },
            onRelease = { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            },
        )
        // Touch layer: tap toggles the controls, double-tap jumps to a random preset.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls },
                    onDoubleTap = { random() },
                )
            },
        )

        AnimatedVisibility(visible = controls || names.isEmpty() || error != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Top: close, preset name, favourite, list.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 18.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                error != null -> error!!
                                names.isEmpty() -> "Loading MilkDrop presets…"
                                else -> current ?: "—"
                            },
                            color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val nowPlaying = song?.let { "${it.title} · ${it.artist}" } ?: radio?.name
                        if (nowPlaying != null) {
                            Text(nowPlaying, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val fav = current != null && current in favorites
                    IconButton(onClick = {
                        val c = current ?: return@IconButton
                        favorites = if (c in favorites) favorites - c else favorites + c
                        prefs.favorites = favorites
                    }) {
                        Icon(if (fav) LineIcons.HeartFilled else LineIcons.Heart, "Favourite preset", tint = Color.White)
                    }
                    IconButton(onClick = { showList = true }) { Icon(LineIcons.Queue, "All presets", tint = Color.White) }
                }

                // Bottom: previous / random / next and the options.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val back = history.dropLast(1).lastOrNull()
                            if (back != null) {
                                history = history.dropLast(2)
                                load(back, 1.2f)
                            } else {
                                next(-1)
                            }
                        }) { Icon(LineIcons.Previous, "Previous preset", tint = Color.White) }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.White, RoundedCornerShape(50)).clickable { random() }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) { Text("RANDOM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        IconButton(onClick = { next(1) }) { Icon(LineIcons.Next, "Next preset", tint = Color.White) }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val cycles = listOf(0, 15, 30, 60, 120)
                        Chip(if (cycle == 0) "Auto-change off" else "Change every ${if (cycle >= 60) "${cycle / 60} min" else "$cycle s"}", cycle > 0) {
                            cycle = cycles[(cycles.indexOf(cycle).coerceAtLeast(0) + 1) % cycles.size]
                            prefs.cycleSeconds = cycle
                        }
                        Chip("New song, new look", perSong) { perSong = !perSong; prefs.perSong = perSong }
                        Chip("Favourites only${if (favorites.isNotEmpty()) " (${favorites.size})" else ""}", favOnly) {
                            favOnly = !favOnly
                            prefs.favoritesOnly = favOnly
                        }
                        val qs = listOf(0.5f to "Battery saver", 0.75f to "Balanced", 1f to "Sharpest")
                        Chip(qs.firstOrNull { it.first == quality }?.second ?: "Balanced", false) {
                            val i = qs.indexOfFirst { it.first == quality }
                            quality = qs[(i + 1) % qs.size].first
                            prefs.quality = quality
                            webView?.evaluateJavascript("window.cadence && window.cadence.setQuality($quality)", null)
                        }
                    }
                    Text(
                        "${names.size} MilkDrop presets · Butterchurn (MIT) · tap to hide, double-tap for random",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                    )
                }
            }
        }
    }

    if (showList) {
        PresetList(
            names = names,
            current = current,
            favorites = favorites,
            onPick = { load(it, 1.5f); showList = false },
            onFavorite = { n ->
                favorites = if (n in favorites) favorites - n else favorites + n
                prefs.favorites = favorites
            },
            onDismiss = { showList = false },
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Color.White else Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (on) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PresetList(
    names: List<String>,
    current: String?,
    favorites: Set<String>,
    onPick: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    val shown = remember(names, query, favOnly, favorites) {
        val q = query.trim().lowercase()
        names.filter { (q.isEmpty() || it.lowercase().contains(q)) && (!favOnly || it in favorites) }
    }
    CadenceSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Presets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Chip("Favourites", favOnly) { favOnly = !favOnly }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${names.size} presets") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CadenceColors.TextPrimary,
                    unfocusedBorderColor = CadenceColors.Outline,
                    cursorColor = CadenceColors.TextPrimary,
                ),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 6.dp)) {
                items(shown, key = { it }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            n,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (n == current) MaterialTheme.colorScheme.primary else CadenceColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (n in favorites) LineIcons.HeartFilled else LineIcons.Heart,
                            "Favourite",
                            tint = CadenceColors.TextSecondary,
                            modifier = Modifier.size(22.dp).clickable { onFavorite(n) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
