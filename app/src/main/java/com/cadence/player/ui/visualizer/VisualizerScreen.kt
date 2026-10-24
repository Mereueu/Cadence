package com.cadence.player.ui.visualizer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.cadence.player.MusicViewModel
import com.cadence.player.audio.LiveAudio
import com.cadence.player.audio.SectionType
import com.cadence.player.playback.ArtworkLoader
import com.cadence.player.ui.dj.rememberSmoothPosition
import com.cadence.player.ui.theme.CadenceColors
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun VisualizerScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var settings by remember { mutableStateOf(VisualSettings.load(context)) }
    fun update(t: (VisualSettings) -> VisualSettings) {
        settings = t(settings)
        settings.save(context)
    }

    val song = vm.currentSong.collectAsStateWithLifecycle().value
    val radio = vm.currentRadio.collectAsStateWithLifecycle().value
    val player by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val analysis = song?.let { analyses[it.id] }
    val smoothPos by rememberSmoothPosition(position, player.isPlaying)

    LaunchedEffect(song?.id) { song?.let { vm.requestAnalysis(it.id) } }

    val art: ImageBitmap? by produceState<ImageBitmap?>(null, song?.id) {
        value = withContext(Dispatchers.IO) {
            song?.let { ArtworkLoader.load(context.applicationContext, it.art, 640)?.asImageBitmap() }
        }
    }
    val albumColors: List<Color>? by produceState<List<Color>?>(null, song?.id) {
        value = withContext(Dispatchers.Default) {
            val bmp = song?.let { ArtworkLoader.load(context.applicationContext, it.art, 300) } ?: return@withContext null
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val main = p.vibrantSwatch ?: p.dominantSwatch ?: return@withContext null
            val dark = p.darkVibrantSwatch ?: p.darkMutedSwatch ?: main
            val light = p.lightVibrantSwatch ?: p.mutedSwatch ?: main
            listOf(Color(main.rgb), Color(dark.rgb), Color(light.rgb))
        }
    }

    // Live analysis of the playing audio runs only while the visualizer is open.
    DisposableEffect(settings.liveAnalysis) {
        LiveAudio.enabled = settings.liveAnalysis
        onDispose { LiveAudio.enabled = false }
    }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val ambient = settings.scene == Scene.AMBIENT
    DisposableEffect(ambient) {
        val window = context.activity()?.window
        val previous = window?.attributes?.screenBrightness
        if (ambient && window != null) {
            val lp = window.attributes
            lp.screenBrightness = 0.04f
            window.attributes = lp
        }
        onDispose {
            if (window != null && previous != null) {
                val lp = window.attributes
                lp.screenBrightness = previous
                window.attributes = lp
            }
        }
    }
    DisposableEffect(Unit) {
        val window = context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler { onClose() }

    val frame = remember { VisualFrame() }
    val scene = remember(settings.scene) { createScene(settings.scene) }
    (scene as? AuraScene)?.art = art
    var tick by remember { mutableLongStateOf(0L) }
    var canvasW by remember { mutableFloatStateOf(1f) }
    var canvasH by remember { mutableFloatStateOf(1f) }
    val currentAnalysis by rememberUpdatedState(analysis)
    val currentSettings by rememberUpdatedState(settings)
    val currentColors by rememberUpdatedState(albumColors)
    val currentPos by rememberUpdatedState(smoothPos)

    LaunchedEffect(scene) {
        var last = 0L
        var acc = 0f
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                acc += dt
                val s = currentSettings
                // The always-on scene runs at ~12 fps to save power.
                if (s.scene == Scene.AMBIENT && acc < 1f / 12f) return@withFrameNanos
                frame.dt = acc.coerceIn(0.001f, 0.1f)
                acc = 0f
                frame.time += frame.dt
                frame.intensity = s.intensity
                frame.density = s.density
                if (s.liveAnalysis) {
                    LiveAudio.analyze(frame.audio)
                } else {
                    frame.audio.active = false
                }
                frame.updateStructure(currentAnalysis, currentPos)
                frame.updatePalette(s.palette, currentAnalysis, currentColors)
                scene.update(frame, canvasW, canvasH)
                tick++
            }
        }
    }

    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, panel, touch) {
        if (controls && !panel) {
            delay(4000)
            controls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                canvasW = it.width.toFloat()
                canvasH = it.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controls = !controls
                        touch++
                    },
                    onDoubleTap = {
                        val next = Scene.entries[(settings.scene.ordinal + 1) % Scene.entries.size]
                        update { it.copy(scene = next) }
                        controls = true
                        touch++
                    },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            tick // read to redraw every frame
            scene.draw(this, frame)
        }

        // ---- Song info and structure cue ----
        if (settings.showInfo || ambient) {
            val title = song?.title ?: radio?.name ?: ""
            val subtitle = song?.artist ?: player.streamTitle ?: radio?.subtitle ?: ""
            if (ambient) {
                val amb = scene as? AmbientScene
                val clock = remember(tick / 120) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) }
                Column(
                    Modifier.offset {
                        tick
                        val c = amb?.center
                        IntOffset(((c?.x ?: 0f) - 130.dp.toPx()).roundToInt(), ((c?.y ?: 0f) + canvasH * 0.2f).roundToInt())
                    }.width(260.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(clock, color = Color(0xFF6A6A6A), fontSize = 42.sp, fontWeight = FontWeight.Light)
                    Text(title, color = Color(0xFF555555), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(24.dp),
                ) {
                    Text(structureLabel(frame, tick), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- Controls ----
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close visualizer", tint = Color.White) }
                Text(
                    settings.scene.label.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = {
                    panel = !panel
                    touch++
                }) { Icon(Icons.Rounded.Tune, "Visualizer settings", tint = Color.White) }
            }
        }

        AnimatedVisibility(panel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                PanelLabel("SCENE")
                Chips(Scene.entries.map { it.label }, settings.scene.ordinal) { i -> update { it.copy(scene = Scene.entries[i]) } }
                PanelLabel("COLOURS")
                Chips(PaletteMode.entries.map { it.label }, settings.palette.ordinal) { i -> update { it.copy(palette = PaletteMode.entries[i]) } }
                PanelLabel("INTENSITY")
                val levels = listOf(0.5f, 0.8f, 1f, 1.3f, 1.6f)
                Chips(listOf("Gentle", "Soft", "Normal", "Strong", "Wild"), levels.indexOfFirst { kotlin.math.abs(it - settings.intensity) < 0.05f }) { i ->
                    update { it.copy(intensity = levels[i]) }
                }
                PanelLabel("DETAIL")
                val dens = listOf(0.5f, 1f, 1.5f)
                Chips(listOf("Light", "Normal", "Dense"), dens.indexOfFirst { kotlin.math.abs(it - settings.density) < 0.05f }) { i ->
                    update { it.copy(density = dens[i]) }
                }
                ToggleRow("Analyse the playing audio", "Reacts to the sound itself. No microphone is used.", settings.liveAnalysis) { v -> update { it.copy(liveAnalysis = v) } }
                ToggleRow("Show song info", null, settings.showInfo) { v -> update { it.copy(showInfo = v) } }
                ToggleRow("Keep screen on", null, settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
                Text(
                    "Tip: double-tap to switch scenes.",
                    color = CadenceColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun structureLabel(f: VisualFrame, tick: Long): String {
    if (tick < 0) return ""
    if (!f.structured) return "LIVE · ${f.section.label.uppercase()}"
    val base = f.section.label.uppercase()
    val next = f.nextSection
    val bars = f.barsToNext
    return when {
        next == SectionType.DROP && bars != null && bars in 1..16 -> "$base · DROP IN $bars BARS"
        next != null && bars != null && bars in 1..4 -> "$base · ${next.label.uppercase()} IN $bars"
        else -> "$base · BAR ${f.barInPhrase + 1}/8 · ${f.bpm.roundToInt()} BPM"
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, color = Color(0xFF8C8C8C), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (sel) Color.Black else Color(0xFFB3B3B3),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.Transparent)
                    .border(1.dp, if (sel) Color.White else Color(0xFF333333), RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, color = Color(0xFF8C8C8C), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFF8C8C8C),
                uncheckedTrackColor = Color.Black,
                uncheckedBorderColor = Color(0xFF333333),
            ),
        )
    }
}
