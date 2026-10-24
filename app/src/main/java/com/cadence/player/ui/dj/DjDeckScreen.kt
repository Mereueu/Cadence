package com.cadence.player.ui.dj

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.audio.Camelot
import com.cadence.player.audio.SectionType
import com.cadence.player.audio.TrackAnalysis
import com.cadence.player.data.Song
import com.cadence.player.playback.ManualDj
import com.cadence.player.playback.ManualDj.Side
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.theme.LineIcons
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ------------------------------------------------------------------ Neon Club palette

private val Bg = Color(0xFF05050A)
private val PanelBg = Color(0xFF08080F)
private val PanelBorder = Color(0xFF22223A)
private val PadBg = Color(0xFF07070C)
private val PadBorder = Color(0xFF2A2A44)
private val Ink = Color(0xFFE6E6FF)
private val Dim = Color(0xFF8484A8)
private val Off = Color(0xFF1C1C2C)
private val Cyan = Color(0xFF22E5FF)
private val Magenta = Color(0xFFFF3DCB)
private val Lime = Color(0xFFB6FF3D)
private val Gold = Color(0xFFFFD23D)
private val CueColors = listOf(Cyan, Magenta, Lime, Gold)
private val Mono = FontFamily.Monospace

private fun accent(side: Side) = if (side == Side.A) Cyan else Magenta
private fun waveLow(side: Side) = if (side == Side.A) Color(0xFF0A9FCC) else Color(0xFFB0128A)
private fun waveHigh(side: Side) = if (side == Side.A) Color(0xFFC8FBFF) else Color(0xFFFFD1F3)

/** Scales the 844×390 design to whatever landscape screen the phone has. */
private class Sc(val u: Float) {
    fun d(v: Number): Dp = (v.toFloat() * u).dp
    fun s(v: Number): TextUnit = (v.toFloat() * u).sp
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen, landscape two-deck DJ setup in the Neon Club style:
 * jog wheels, pitch, sync & key sync, hot cues, rolls, beat jump, loops with slip,
 * a 3-band EQ + filter, quick stems, echo, smart crossfader and phrase-coloured waveforms.
 */
@Composable
fun DjDeckScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val st by ManualDj.state.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    var loadFor by remember { mutableStateOf<Side?>(null) }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        ManualDj.open(context)
        onDispose {
            ManualDj.close()
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previous
        }
    }
    BackHandler { onClose() }
    LaunchedEffect(st.a.songId, st.b.songId) {
        listOfNotNull(st.a.songId, st.b.songId).forEach { vm.requestAnalysis(it) }
    }
    LaunchedEffect(st.note) {
        st.note?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            ManualDj.consumeNote()
        }
    }

    val aA = st.a.songId?.let { analyses[it] }
    val aB = st.b.songId?.let { analyses[it] }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1B0B2E), Bg), radius = 1400f, center = Offset(1000f, 1600f)))
            .background(Bg.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val sc = Sc(min(maxWidth.value / 844f, maxHeight.value / 390f))
        Column(
            Modifier.width(sc.d(844)).height(sc.d(390)).padding(sc.d(10)),
            verticalArrangement = Arrangement.spacedBy(sc.d(6)),
        ) {
            Header(sc, st, aA, aB, onClose, onLoad = { loadFor = it })
            Column(verticalArrangement = Arrangement.spacedBy(sc.d(3))) {
                DeckWave(sc, Side.A, st.a, aA)
                DeckWave(sc, Side.B, st.b, aB)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Deck(sc, Side.A, st, vm, aA)
                Mixer(sc, st)
                Deck(sc, Side.B, st, vm, aB)
            }
        }
    }

    loadFor?.let { side ->
        LoadSheet(vm, side, st, analyses, onDismiss = { loadFor = null })
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(sc: Sc, st: ManualDj.State, aA: TrackAnalysis?, aB: TrackAnalysis?, onClose: () -> Unit, onLoad: (Side) -> Unit) {
    Row(Modifier.fillMaxWidth().height(sc.d(30)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sc.d(10))) {
        Box(
            Modifier.size(sc.d(28)).clip(CircleShape).border(1.dp, PadBorder, CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Dim, fontSize = sc.s(12)) }
        TrackInfo(sc, Side.A, st.a, Modifier.weight(1f)) { onLoad(Side.A) }
        Readout(sc, Side.A, st.a, aA, Alignment.End)
        Column(Modifier.width(sc.d(170)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(sc.d(4))) {
            Row(horizontalArrangement = Arrangement.spacedBy(sc.d(4))) {
                Toggle(sc, "SMART", st.smartFader, Lime, sc.d(52)) { ManualDj.toggleSmartFader() }
                Toggle(sc, "CUT", st.sharpCurve, Gold, sc.d(40)) { ManualDj.setSharpCurve(!st.sharpCurve) }
                Toggle(sc, "QUANT", st.quantize, Cyan, sc.d(52)) { ManualDj.toggleQuantize() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(sc.d(10))) {
                BeatLeds(sc, aA?.let { beatIndex(it, st.a.positionMs) } ?: -1, Cyan)
                BeatLeds(sc, aB?.let { beatIndex(it, st.b.positionMs) } ?: -1, Magenta)
            }
        }
        Readout(sc, Side.B, st.b, aB, Alignment.Start)
        TrackInfo(sc, Side.B, st.b, Modifier.weight(1f)) { onLoad(Side.B) }
    }
}

private fun beatIndex(a: TrackAnalysis, pos: Long): Int {
    val b = Math.floor(a.beatAt(pos)).toLong()
    return (((b % 4) + 4) % 4).toInt()
}

@Composable
private fun TrackInfo(sc: Sc, side: Side, d: ManualDj.Deck, modifier: Modifier, onLoad: () -> Unit) {
    val right = side == Side.B
    Row(
        modifier.clip(RoundedCornerShape(sc.d(4))).clickable(onClick = onLoad).padding(horizontal = sc.d(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
    ) {
        if (!right) Text("A", color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = sc.s(11))
        if (!right) Spacer(Modifier.width(sc.d(8)))
        Column(horizontalAlignment = if (right) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
            Text(
                d.title.ifEmpty { "Tap to load a track" }, color = if (d.songId == null) Dim else Color.White,
                fontSize = sc.s(12), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (d.songId == null) "Deck ${side.name}" else d.artist, color = Dim, fontSize = sc.s(10),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (right) Spacer(Modifier.width(sc.d(8)))
        if (right) Text("B", color = Magenta, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = sc.s(11))
    }
}

@Composable
private fun Readout(sc: Sc, side: Side, d: ManualDj.Deck, a: TrackAnalysis?, align: Alignment.Horizontal) {
    val acc = accent(side)
    Column(horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a?.let { "%.1f".format(it.bpm * d.speed) } ?: "—", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = sc.s(13))
            Text(" BPM", color = Dim, fontFamily = Mono, fontSize = sc.s(9))
        }
        val key = a?.let { Camelot.code(ManualDj.shiftedKey(it.keyIndex, d.keyShift)) } ?: "—"
        val shift = if (d.keyShift != 0) " (${if (d.keyShift > 0) "+" else ""}${d.keyShift})" else ""
        val remaining = (d.durationMs - d.positionMs).coerceAtLeast(0L)
        Text(
            "$key$shift · -${formatClock(remaining)}",
            color = if (remaining < 30_000 && d.playing) Gold else acc, fontFamily = Mono, fontSize = sc.s(10),
        )
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun BeatLeds(sc: Sc, on: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(sc.d(3))) {
        repeat(4) { i ->
            Box(Modifier.size(sc.d(9), sc.d(4)).clip(RoundedCornerShape(1.dp)).background(if (i == on) color else Off))
        }
    }
}

@Composable
private fun Toggle(sc: Sc, label: String, on: Boolean, color: Color, width: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width, sc.d(16))
            .clip(RoundedCornerShape(sc.d(8)))
            .background(if (on) color.copy(alpha = 0.18f) else PadBg)
            .border(1.dp, if (on) color else PadBorder, RoundedCornerShape(sc.d(8)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) color else Dim, fontSize = sc.s(8), fontWeight = FontWeight.Bold, letterSpacing = sc.s(0.6)) }
}

// ------------------------------------------------------------------ waveforms

@Composable
private fun DeckWave(sc: Sc, side: Side, d: ManualDj.Deck, a: TrackAnalysis?) {
    val pos by rememberSmoothPosition(d.positionMs, d.playing, d.speed)
    val acc = accent(side)
    val low = waveLow(side)
    val high = waveHigh(side)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sc.d(3)))) {
        Canvas(Modifier.fillMaxWidth().height(sc.d(22)).background(Bg)) {
            drawScrollingWave(a, pos, d, acc, low, high)
        }
        // Whole-track overview coloured by phrase (intro, build, drop…); tap anywhere to jump there.
        val currentD by rememberUpdatedState(d)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(sc.d(6))
                .background(Color(0xFF0B0B14))
                .pointerInput(side) {
                    detectTapGestures { o ->
                        val dd = currentD
                        if (dd.durationMs > 0) ManualDj.seek(side, (o.x / size.width * dd.durationMs).toLong())
                    }
                },
        ) {
            val dur = (a?.durationMs ?: d.durationMs).coerceAtLeast(1L)
            a?.sections?.forEach { s ->
                val x0 = s.startMs.toFloat() / dur * size.width
                val x1 = s.endMs.toFloat() / dur * size.width
                drawRect(sectionColor(s.type, acc), Offset(x0, 0f), Size(max(1f, x1 - x0), size.height))
            }
            d.hotCues.forEachIndexed { i, c ->
                if (c != null) drawRect(CueColors[i], Offset(c.toFloat() / dur * size.width, 0f), Size(2f, size.height))
            }
            val x = d.positionMs.toFloat() / dur * size.width
            drawRect(Color.White, Offset(x - 1f, 0f), Size(2f, size.height))
        }
    }
}

private fun sectionColor(t: SectionType, acc: Color): Color = when (t) {
    SectionType.INTRO, SectionType.OUTRO -> Color(0xFF2A2A44)
    SectionType.GROOVE -> acc.copy(alpha = 0.35f)
    SectionType.BUILD -> Gold.copy(alpha = 0.6f)
    SectionType.DROP -> acc
    SectionType.BREAKDOWN -> Color(0xFF6B6BA8)
}

private fun DrawScope.drawScrollingWave(a: TrackAnalysis?, positionMs: Long, d: ManualDj.Deck, acc: Color, low: Color, high: Color) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val windowMs = 10_000.0
    if (a == null) {
        drawLine(Off, Offset(0f, mid), Offset(w, mid), 1f)
        drawRect(Color.White, Offset(w / 2f - 1f, 0f), Size(2f, h))
        return
    }
    val startMs = positionMs - windowMs / 2
    fun xOf(t: Double): Float = ((t - startMs) / windowMs * w).toFloat()
    // Loop region.
    val ls = d.loopStartMs; val le = d.loopEndMs
    if (ls != null && le != null) {
        val x0 = xOf(ls.toDouble()); val x1 = xOf(le.toDouble())
        drawRect(Lime.copy(alpha = 0.16f), Offset(x0, 0f), Size(x1 - x0, h))
        drawRect(Lime, Offset(x0, 0f), Size(1.5f, h)); drawRect(Lime, Offset(x1, 0f), Size(1.5f, h))
    }
    val bins = a.wavePeak.size
    val msPerBin = a.durationMs.coerceAtLeast(1).toDouble() / bins
    val first = max(0, (startMs / msPerBin).toInt())
    val last = min(bins - 1, ((startMs + windowMs) / msPerBin).toInt())
    val barW = max(1f, (msPerBin / windowMs * w).toFloat() * 0.75f)
    for (i in first..last) {
        val x = xOf(i * msPerBin)
        val peak = (a.wavePeak[i].toInt() and 0xFF) / 255f
        val lo = (a.waveLow[i].toInt() and 0xFF) / 255f
        val md = (a.waveMid[i].toInt() and 0xFF) / 255f
        val hi = (a.waveHigh[i].toInt() and 0xFF) / 255f
        val alpha = if (i * msPerBin < positionMs) 0.4f else 1f
        val amp = peak * (mid - 1f)
        val total = (lo + md + hi).coerceAtLeast(0.001f)
        val full = amp
        val mids = amp * (md + hi) / total
        val highs = amp * hi / total
        drawRect(low.copy(alpha = alpha), Offset(x, mid - full), Size(barW, full * 2))
        drawRect(acc.copy(alpha = alpha), Offset(x, mid - mids), Size(barW, mids * 2))
        drawRect(high.copy(alpha = alpha), Offset(x, mid - highs), Size(barW, highs * 2))
    }
    // Beat grid.
    val beat = a.beatMs
    var k = Math.ceil((startMs - a.firstDownbeatMs) / beat).toLong()
    while (true) {
        val t = a.firstDownbeatMs + k * beat
        if (t > startMs + windowMs) break
        val bar = ((k % 4) + 4) % 4 == 0L
        val x = xOf(t)
        drawLine(Color.White.copy(alpha = if (bar) 0.55f else 0.25f), Offset(x, 0f), Offset(x, h * if (bar) 0.24f else 0.12f), 1f)
        k++
    }
    // Hot cues.
    d.hotCues.forEachIndexed { i, c ->
        if (c != null) {
            val x = xOf(c.toDouble())
            if (x in 0f..w) {
                drawRect(CueColors[i], Offset(x, 0f), Size(1.5f, h))
                drawRect(CueColors[i], Offset(x, 0f), Size(5f, 5f))
            }
        }
    }
    drawRect(Color.White, Offset(w / 2f - 1f, 0f), Size(2f, h))
}

// ------------------------------------------------------------------ deck

@Composable
private fun Deck(sc: Sc, side: Side, st: ManualDj.State, vm: MusicViewModel, a: TrackAnalysis?) {
    val d = st.deck(side)
    val acc = accent(side)
    val pitch = @Composable { PitchColumn(sc, side, d, st) }
    val center = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(sc.d(8))) {
            Jog(sc, side, d, vm, sc.d(168))
            Row(horizontalArrangement = Arrangement.spacedBy(sc.d(14))) {
                RoundButton(sc, sc.d(42), Gold, { ManualDj.cue(side) }) {
                    Text("CUE", color = Gold, fontSize = sc.s(10), fontWeight = FontWeight.Bold)
                }
                RoundButton(sc, sc.d(42), Lime, { ManualDj.togglePlay(side) }) {
                    Icon(if (d.playing) LineIcons.Pause else LineIcons.Play, "Play or pause", tint = Lime, modifier = Modifier.size(sc.d(20)))
                }
            }
        }
    }
    val pads = @Composable { PadColumn(sc, side, d, acc) }
    Row(horizontalArrangement = Arrangement.spacedBy(sc.d(8)), verticalAlignment = Alignment.Top) {
        if (side == Side.A) { pitch(); center(); pads() } else { pads(); center(); pitch() }
    }
}

@Composable
private fun RoundButton(sc: Sc, size: Dp, color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(PadBg)
            .border(1.5.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Jog(sc: Sc, side: Side, d: ManualDj.Deck, vm: MusicViewModel, size: Dp) {
    val acc = accent(side)
    val pos by rememberSmoothPosition(d.positionMs, d.playing, d.speed)
    val angle = ((pos % 1800L) / 1800f) * 360f
    val progress = if (d.durationMs > 0) (pos.toFloat() / d.durationMs).coerceIn(0f, 1f) else 0f
    var touching by remember { mutableStateOf(false) }
    val song = d.songId?.let { vm.songById(it) }
    Box(
        Modifier
            .size(size)
            .pointerInput(side) {
                var last = 0f
                fun angleOf(o: Offset) = Math.toDegrees(atan2((o.y - this.size.height / 2f).toDouble(), (o.x - this.size.width / 2f).toDouble())).toFloat()
                detectDragGestures(
                    onDragStart = { o -> last = angleOf(o); touching = true },
                    onDragEnd = { touching = false },
                    onDragCancel = { touching = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val now = angleOf(change.position)
                        var delta = now - last
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        last = now
                        ManualDj.jog(side, delta)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            drawCircle(Color(0xFF0B0B14), r, c)
            for (i in 0 until 72) {
                val t = Math.toRadians(i * 5.0)
                val r0 = r * 0.89f; val r1 = r * 0.945f
                drawLine(Color(0xFF262640), Offset(c.x + r0 * sin(t).toFloat(), c.y - r0 * cos(t).toFloat()), Offset(c.x + r1 * sin(t).toFloat(), c.y - r1 * cos(t).toFloat()), 1f)
            }
            // Position ring with a neon glow.
            val ringR = r * 0.955f
            val tl = Offset(c.x - ringR, c.y - ringR)
            val sz = Size(ringR * 2, ringR * 2)
            drawArc(acc.copy(alpha = 0.22f), -90f, progress * 360f, false, tl, sz, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            drawArc(acc, -90f, progress * 360f, false, tl, sz, style = Stroke(r * 0.025f, cap = StrokeCap.Round))
            // Platter (brighter while touched).
            drawCircle(if (touching) Color(0xFF14142A) else Color(0xFF08080F), r * 0.82f, c)
            drawCircle(acc.copy(alpha = if (touching) 0.5f else 0.18f), r * 0.82f, c, style = Stroke(1.5f))
            // Rotating marker.
            val t = Math.toRadians(angle.toDouble())
            val m0 = r * 0.36f; val m1 = r * 0.8f
            drawLine(acc, Offset(c.x + m0 * sin(t).toFloat(), c.y - m0 * cos(t).toFloat()), Offset(c.x + m1 * sin(t).toFloat(), c.y - m1 * cos(t).toFloat()), r * 0.03f, cap = StrokeCap.Round)
            // Hot cue dots around the rim.
            if (d.durationMs > 0) d.hotCues.forEachIndexed { i, cue ->
                if (cue != null) {
                    val ct = Math.toRadians(cue.toDouble() / d.durationMs * 360.0)
                    drawCircle(CueColors[i], r * 0.03f, Offset(c.x + ringR * sin(ct).toFloat(), c.y - ringR * cos(ct).toFloat()))
                }
            }
        }
        // Album art spinning in the centre.
        ArtImage(
            song?.art,
            Modifier.size(size * 0.32f).rotate(angle).clip(CircleShape),
            CircleShape,
            iconSize = size * 0.12f,
        )
    }
}

@Composable
private fun PitchColumn(sc: Sc, side: Side, d: ManualDj.Deck, st: ManualDj.State) {
    val acc = accent(side)
    val pct = d.pitch * st.pitchRange
    Column(Modifier.width(sc.d(32)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(sc.d(3))) {
        Text("PITCH", color = Dim, fontSize = sc.s(8), letterSpacing = sc.s(0.8))
        VFader(
            sc, sc.d(30), sc.d(160), (d.pitch + 1f) / 2f, capColor = Cyan.takeIf { d.synced } ?: Ink, center = true,
            onChange = { ManualDj.setPitch(side, it * 2f - 1f) }, onReset = { ManualDj.setPitch(side, 0f) },
        )
        Text("${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%", color = acc, fontFamily = Mono, fontSize = sc.s(9))
        SmallKey(sc, "±${st.pitchRange}", false) { ManualDj.setPitchRange(if (st.pitchRange == 8) 16 else if (st.pitchRange == 16) 4 else 8) }
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(2))) {
            SmallKey(sc, "♭", false, sc.d(15)) { ManualDj.shiftKey(side, -1) }
            SmallKey(sc, "♯", false, sc.d(15)) { ManualDj.shiftKey(side, 1) }
        }
    }
}

@Composable
private fun SmallKey(sc: Sc, label: String, on: Boolean, width: Dp = sc.d(32), color: Color = Ink, onClick: () -> Unit) {
    Box(
        Modifier.size(width, sc.d(18)).clip(RoundedCornerShape(sc.d(4))).background(if (on) color.copy(alpha = 0.2f) else PadBg)
            .border(1.dp, if (on) color else PadBorder, RoundedCornerShape(sc.d(4))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) color else Ink, fontSize = sc.s(9), fontWeight = FontWeight.Bold) }
}

private enum class PadMode(val label: String) { CUE("CUE"), ROLL("ROLL"), JUMP("JUMP") }

@Composable
private fun PadColumn(sc: Sc, side: Side, d: ManualDj.Deck, acc: Color) {
    var mode by remember { mutableStateOf(PadMode.CUE) }
    val w = 96
    Column(Modifier.width(sc.d(w)), verticalArrangement = Arrangement.spacedBy(sc.d(4))) {
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(4))) {
            PadButton(sc, "SYNC", sc.d(46), sc.d(22), if (d.synced) acc else null, acc, onTap = { ManualDj.sync(side) })
            PadButton(sc, "KEY", sc.d(46), sc.d(22), if (d.keyShift != 0) Gold else null, Gold, onTap = { ManualDj.keySync(side) }, onLong = { ManualDj.resetKey(side) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(3))) {
            PadMode.entries.forEach { m ->
                Box(
                    Modifier.size(sc.d(30), sc.d(16)).clip(RoundedCornerShape(sc.d(8)))
                        .background(if (m == mode) acc else PadBg).border(1.dp, if (m == mode) acc else PadBorder, RoundedCornerShape(sc.d(8)))
                        .clickable { mode = m },
                    contentAlignment = Alignment.Center,
                ) { Text(m.label, color = if (m == mode) Bg else Dim, fontSize = sc.s(7), fontWeight = FontWeight.Bold) }
            }
        }
        val padW = sc.d((w - 4) / 2)
        val padH = sc.d(32)
        Column(verticalArrangement = Arrangement.spacedBy(sc.d(4))) {
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(sc.d(4))) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        when (mode) {
                            PadMode.CUE -> {
                                val set = d.hotCues.getOrNull(i) != null
                                PadButton(
                                    sc, if (set && d.autoCues) "${i + 1}·A" else "${i + 1}", padW, padH,
                                    if (set) CueColors[i] else null, CueColors[i],
                                    onPress = { ManualDj.hotCue(side, i) }, onRelease = { ManualDj.hotCueRelease(side) },
                                    onLong = { ManualDj.clearHotCue(side, i) },
                                )
                            }
                            PadMode.ROLL -> {
                                val beats = listOf(0.25f, 0.5f, 1f, 2f)[i]
                                val label = listOf("¼", "½", "1", "2")[i]
                                PadButton(
                                    sc, label, padW, padH, if (d.rolling && d.loopBeats == beats) Lime else null, Lime,
                                    onPress = { ManualDj.rollStart(side, beats) }, onRelease = { ManualDj.rollEnd(side) },
                                )
                            }
                            PadMode.JUMP -> {
                                val beats = listOf(-4, 4, -16, 16)[i]
                                PadButton(sc, if (beats > 0) "+$beats" else "$beats", padW, padH, null, acc, onTap = { ManualDj.beatJump(side, beats) })
                            }
                        }
                    }
                }
            }
        }
        Text("LOOP · BEATS", color = Dim, fontSize = sc.s(8), letterSpacing = sc.s(0.8), modifier = Modifier.padding(top = sc.d(2)))
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(4))) {
            listOf(1f, 2f, 4f, 8f).forEach { b ->
                val on = !d.rolling && d.loopStartMs != null && d.loopBeats == b
                PadButton(sc, b.toInt().toString(), sc.d(21), sc.d(22), if (on) Lime else null, Lime,
                    onTap = { if (on) ManualDj.clearLoop(side) else ManualDj.loop(side, b) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(4))) {
            PadButton(sc, "½", sc.d(29), sc.d(22), null, Ink, onTap = { ManualDj.halveLoop(side) })
            PadButton(sc, "EXIT", sc.d(30), sc.d(22), null, Ink, onTap = { ManualDj.clearLoop(side) })
            PadButton(sc, "×2", sc.d(29), sc.d(22), null, Ink, onTap = { ManualDj.doubleLoop(side) })
        }
        PadButton(sc, "SLIP", sc.d(w), sc.d(20), if (d.slip) Gold else null, Gold, onTap = { ManualDj.toggleSlip(side) })
    }
}

/** A neon pad: lit when [lit] is set; supports tap, press-and-hold (with release) and long-press. */
@Composable
private fun PadButton(
    sc: Sc,
    label: String,
    width: Dp,
    height: Dp,
    lit: Color?,
    color: Color,
    onTap: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val long by rememberUpdatedState(onLong)
    val active = lit != null || pressed
    val shape = RoundedCornerShape(sc.d(6))
    Box(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(if (active) (lit ?: color).copy(alpha = if (pressed) 0.35f else 0.2f) else PadBg)
            .border(1.dp, if (active) (lit ?: color) else PadBorder, shape)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        press?.invoke()
                        tryAwaitRelease()
                        pressed = false
                        release?.invoke()
                    },
                    onTap = { tap?.invoke() },
                    onLongPress = long?.let { { _: Offset -> it() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) (lit ?: color) else Ink, fontSize = sc.s(10), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------ mixer

@Composable
private fun Mixer(sc: Sc, st: ManualDj.State) {
    Column(
        Modifier
            .width(sc.d(196))
            .clip(RoundedCornerShape(sc.d(10)))
            .background(PanelBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(sc.d(10)))
            .padding(horizontal = sc.d(6), vertical = sc.d(6)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sc.d(6)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(12))) {
            Channel(sc, Side.A, st.a)
            Channel(sc, Side.B, st.b)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(5)), verticalAlignment = Alignment.CenterVertically) {
            PadButton(sc, "ECHO", sc.d(52), sc.d(20), if (st.a.echo) Cyan else null, Cyan, onTap = { ManualDj.toggleEcho(Side.A) })
            val beats = listOf(0.25f, 0.5f, 0.75f, 1f)
            val label = mapOf(0.25f to "¼", 0.5f to "½", 0.75f to "¾", 1f to "1").getValue(st.echoBeats.takeIf { it in beats } ?: 0.5f)
            PadButton(sc, "$label BEAT", sc.d(56), sc.d(20), null, Ink, onTap = {
                ManualDj.setEchoBeats(beats[(beats.indexOf(st.echoBeats).coerceAtLeast(0) + 1) % beats.size])
            })
            PadButton(sc, "ECHO", sc.d(52), sc.d(20), if (st.b.echo) Magenta else null, Magenta, onTap = { ManualDj.toggleEcho(Side.B) })
        }
        HFader(sc, sc.d(176), sc.d(28), st.crossfader, onChange = { ManualDj.setCrossfader(it) }, onReset = { ManualDj.setCrossfader(0.5f) })
    }
}

@Composable
private fun Channel(sc: Sc, side: Side, d: ManualDj.Deck) {
    val acc = accent(side)
    val knobs = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(sc.d(1))) {
            Knob(sc, d.eqHigh, "HI", acc) { ManualDj.setEq(side, 0, it) }
            Knob(sc, d.eqMid, "MID", acc) { ManualDj.setEq(side, 1, it) }
            Knob(sc, d.eqLow, "LOW", acc) { ManualDj.setEq(side, 2, it) }
            Knob(sc, d.filter, "FILTER", acc) { ManualDj.setEq(side, 3, it) }
        }
    }
    val fader = @Composable {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(sc.d(3))) {
            if (side == Side.B) VFader(sc, sc.d(26), sc.d(128), d.volume, capColor = Ink, center = false, onChange = { ManualDj.setVolume(side, it) }, onReset = { ManualDj.setVolume(side, 1f) })
            Meter(sc, d.level, acc)
            if (side == Side.A) VFader(sc, sc.d(26), sc.d(128), d.volume, capColor = Ink, center = false, onChange = { ManualDj.setVolume(side, it) }, onReset = { ManualDj.setVolume(side, 1f) })
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(sc.d(4))) {
        Row(horizontalArrangement = Arrangement.spacedBy(sc.d(3))) {
            PadButton(sc, "INST", sc.d(40), sc.d(17), if (d.stem == 1) acc else null, acc, onTap = { ManualDj.setStem(side, 1) })
            PadButton(sc, "VOX", sc.d(38), sc.d(17), if (d.stem == 2) acc else null, acc, onTap = { ManualDj.setStem(side, 2) })
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(sc.d(5))) {
            if (side == Side.A) { knobs(); fader() } else { fader(); knobs() }
        }
    }
}

@Composable
private fun Knob(sc: Sc, value: Float, label: String, acc: Color, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var dragValue by remember { mutableFloatStateOf(value) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(sc.d(30))
                .pointerInput(label) {
                    detectTapGestures(onDoubleTap = { change(0.5f) })
                }
                .pointerInput(label + "drag") {
                    detectVerticalDragGestures(
                        onDragStart = { dragValue = current },
                        onVerticalDrag = { ch, dy ->
                            ch.consume()
                            dragValue = (dragValue - dy / (size.height * 4f)).coerceIn(0f, 1f)
                            // Snap to the centre detent.
                            change(if (abs(dragValue - 0.5f) < 0.025f) 0.5f else dragValue)
                        },
                    )
                },
        ) {
            val r = size.minDimension / 2f
            val tl = Offset(center.x - r * 0.9f, center.y - r * 0.9f)
            val sz = Size(r * 1.8f, r * 1.8f)
            drawArc(Color(0xFF1A1A2A), 135f, 270f, false, tl, sz, style = Stroke(r * 0.1f, cap = StrokeCap.Round))
            val a = 135f + 270f * value
            if (abs(value - 0.5f) > 0.01f) {
                val start = if (value < 0.5f) a else 270f
                drawArc(acc, start, abs(a - 270f), false, tl, sz, style = Stroke(r * 0.1f, cap = StrokeCap.Round))
            }
            drawCircle(Color(0xFF0C0C16), r * 0.66f, center)
            drawCircle(Color(0xFF2E2E4A), r * 0.66f, center, style = Stroke(1f))
            val t = Math.toRadians(a.toDouble())
            drawLine(
                Color.White,
                Offset(center.x + r * 0.2f * cos(t).toFloat(), center.y + r * 0.2f * sin(t).toFloat()),
                Offset(center.x + r * 0.58f * cos(t).toFloat(), center.y + r * 0.58f * sin(t).toFloat()),
                r * 0.1f, cap = StrokeCap.Round,
            )
        }
        Text(label, color = Dim, fontSize = sc.s(7), letterSpacing = sc.s(0.6))
    }
}

@Composable
private fun VFader(sc: Sc, width: Dp, height: Dp, value: Float, capColor: Color, center: Boolean, onChange: (Float) -> Unit, onReset: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val reset by rememberUpdatedState(onReset)
    Canvas(
        Modifier
            .size(width, height)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
            .pointerInput("drag") {
                val capH = 18.dp.toPx()
                fun valueAt(y: Float) = (1f - (y - capH / 2f) / (size.height - capH)).coerceIn(0f, 1f)
                detectDragGestures(
                    onDragStart = { o -> change(valueAt(o.y)) },
                    onDrag = { ch, _ -> ch.consume(); change(valueAt(ch.position.y)) },
                )
            },
    ) {
        val capH = 18.dp.toPx()
        val top = capH / 2f; val bot = size.height - capH / 2f
        val cx = size.width / 2f
        for (i in 0..10) {
            val y = top + i * (bot - top) / 10f
            val lw = if (i == 0 || i == 5 || i == 10) 6f else 3.5f
            drawLine(Color(0xFF2A2A44), Offset(cx - 6f - lw, y), Offset(cx - 6f, y), 1f)
            drawLine(Color(0xFF2A2A44), Offset(cx + 6f, y), Offset(cx + 6f + lw, y), 1f)
        }
        drawRoundRect(Color(0xFF11111C), Offset(cx - 2.5f, top), Size(5f, bot - top), androidx.compose.ui.geometry.CornerRadius(2.5f))
        val y = top + (1f - value) * (bot - top)
        if (center && abs(value - 0.5f) > 0.005f) {
            val mid = (top + bot) / 2f
            drawRect(capColor.copy(alpha = 0.5f), Offset(cx - 1.5f, min(mid, y)), Size(3f, abs(mid - y)))
        }
        val cw = size.width - 4f
        drawRoundRect(capColor, Offset(cx - cw / 2f, y - capH / 2f), Size(cw, capH), androidx.compose.ui.geometry.CornerRadius(4f))
        drawLine(Bg, Offset(cx - cw / 2f + 3f, y), Offset(cx + cw / 2f - 3f, y), 2f)
    }
}

@Composable
private fun HFader(sc: Sc, width: Dp, height: Dp, value: Float, onChange: (Float) -> Unit, onReset: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val reset by rememberUpdatedState(onReset)
    Canvas(
        Modifier
            .size(width, height)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
            .pointerInput("drag") {
                val capW = 24.dp.toPx()
                fun valueAt(x: Float) = ((x - capW / 2f) / (size.width - capW)).coerceIn(0f, 1f)
                detectDragGestures(
                    onDragStart = { o -> change(valueAt(o.x)) },
                    onDrag = { ch, _ -> ch.consume(); change(valueAt(ch.position.x)) },
                )
            },
    ) {
        val capW = 24.dp.toPx()
        val l = capW / 2f; val r = size.width - capW / 2f
        val cy = size.height / 2f
        for (i in 0..10) {
            val x = l + i * (r - l) / 10f
            drawLine(Color(0xFF2A2A44), Offset(x, cy + 6f), Offset(x, cy + if (i % 5 == 0) 12f else 9f), 1f)
        }
        drawRoundRect(Color(0xFF11111C), Offset(l, cy - 2.5f), Size(r - l, 5f), androidx.compose.ui.geometry.CornerRadius(2.5f))
        drawRoundRect(Magenta.copy(alpha = 0.55f), Offset(l, cy - 2.5f), Size(r - l, 5f), androidx.compose.ui.geometry.CornerRadius(2.5f), style = Stroke(1f))
        val x = l + value * (r - l)
        drawRoundRect(Color.White, Offset(x - capW / 2f, 2f), Size(capW, size.height - 4f), androidx.compose.ui.geometry.CornerRadius(4f))
        drawLine(Bg, Offset(x, 5f), Offset(x, size.height - 5f), 2f)
    }
}

@Composable
private fun Meter(sc: Sc, level: Float, acc: Color) {
    Canvas(Modifier.size(sc.d(5), sc.d(120))) {
        val segs = 14
        val gap = 1.5f
        val h = (size.height - gap * (segs - 1)) / segs
        val lit = (level * segs).toInt()
        for (i in 0 until segs) {
            val y = size.height - (i + 1) * h - i * gap
            val c = when {
                i >= lit -> Color(0xFF161624)
                i < segs * 0.65 -> acc
                i < segs * 0.88 -> Magenta
                else -> Color.White
            }
            drawRect(c, Offset(0f, y), Size(size.width, h))
        }
    }
}

// ------------------------------------------------------------------ loading tracks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadSheet(
    vm: MusicViewModel,
    side: Side,
    st: ManualDj.State,
    analyses: Map<Long, TrackAnalysis>,
    onDismiss: () -> Unit,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(true) }
    val other = if (side == Side.A) st.b else st.a
    val otherA = other.songId?.let { analyses[it] }
    val acc = accent(side)

    // Track suggestions: songs whose key and tempo mix well with what's playing on the other deck.
    fun score(s: Song): Float? {
        val a = analyses[s.id] ?: return null
        val o = otherA ?: return null
        val key = Camelot.compatibility(a.keyIndex, ManualDj.shiftedKey(o.keyIndex, other.keyShift))
        val ratio = Camelot.tempoRatio(a.bpm, o.bpm * other.speed)
        val tempo = (1f - abs(ratio - 1f) / 0.08f).coerceIn(0f, 1f)
        return key * 0.6f + tempo * 0.4f
    }

    val list = remember(library, query, matches, analyses, otherA) {
        val q = query.trim().lowercase()
        val base = library.songs.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        if (matches && otherA != null) {
            base.mapNotNull { s -> score(s)?.let { s to it } }.sortedByDescending { it.second }.take(80).map { it.first }
        } else {
            base.sortedBy { it.title.lowercase() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PanelBg,
        contentColor = Ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Load deck ${side.name}", color = acc, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (otherA != null) {
                    Toggle(Sc(1f), "MATCHES", matches, Lime, 72.dp) { matches = true }
                    Toggle(Sc(1f), "ALL", !matches, Ink, 44.dp) { matches = false }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs or artists", color = Dim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = acc, unfocusedBorderColor = PadBorder, cursorColor = acc,
                    focusedTextColor = Ink, unfocusedTextColor = Ink,
                ),
            )
            if (matches && otherA != null) {
                Text(
                    "Best matches for deck ${if (side == Side.A) "B" else "A"} (${"%.1f".format(otherA.bpm * other.speed)} BPM · ${Camelot.code(ManualDj.shiftedKey(otherA.keyIndex, other.keyShift))}). Only analysed songs are listed.",
                    color = Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                items(list, key = { it.id }) { s ->
                    val a = analyses[s.id]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                ManualDj.load(side, s)
                                vm.requestAnalysis(s.id)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(s.art, Modifier.size(40.dp), RoundedCornerShape(4.dp), iconSize = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (a != null) {
                            val compat = otherA?.let { Camelot.compatibility(a.keyIndex, ManualDj.shiftedKey(it.keyIndex, other.keyShift)) }
                            Text(
                                "${"%.0f".format(a.bpm)} · ${a.camelot}",
                                color = when {
                                    compat == null -> Dim
                                    compat >= 0.85f -> Lime
                                    compat >= 0.55f -> Gold
                                    else -> Dim
                                },
                                fontFamily = Mono, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
