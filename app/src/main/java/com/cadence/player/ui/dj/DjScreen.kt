package com.cadence.player.ui.dj

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.QueueOrder
import com.cadence.player.audio.Camelot
import com.cadence.player.audio.TrackAnalysis
import com.cadence.player.data.Song
import com.cadence.player.playback.AutoDj
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.components.ArtImage
import com.cadence.player.ui.components.MinimalButton
import com.cadence.player.ui.components.formatDuration
import com.cadence.player.ui.theme.CadenceColors
import kotlin.math.abs
import kotlin.math.max

private val Dim = Color(0xFF8C8C8C)
private val Hair = Color(0xFF2A2A2A)

/** Interpolates a playback position between the player's periodic updates for smooth scrolling. */
@Composable
fun rememberSmoothPosition(target: Long, playing: Boolean, speed: Float = 1f): State<Long> {
    val state = remember { mutableLongStateOf(target) }
    val base = remember { mutableLongStateOf(target) }
    val baseTime = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(target) {
        base.longValue = target
        baseTime.longValue = System.nanoTime()
        state.longValue = target
    }
    val currentPlaying by rememberUpdatedState(playing)
    val currentSpeed by rememberUpdatedState(speed)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                state.longValue = if (currentPlaying) {
                    base.longValue + ((now - baseTime.longValue) / 1_000_000L * currentSpeed).toLong()
                } else {
                    base.longValue
                }
            }
        }
    }
    return state
}

@Composable
fun DjScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val actions = LocalActions.current
    val dj by vm.dj.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val position by vm.player.position.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val working by vm.analysisWorking.collectAsStateWithLifecycle()
    val pending by vm.analysisPending.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val config = dj.config

    val current: Song? = player.currentId?.let { vm.songById(it) }
    val curPos = player.queue.indexOfFirst { it.index == player.currentIndex }
    val nextSong: Song? = dj.plan?.let { vm.songById(it.toId) }
        ?: player.queue.getOrNull(curPos + 1)?.let { vm.songById(it.songId) }
    val a = current?.let { analyses[it.id] }
    val b = nextSong?.let { analyses[it.id] }

    LaunchedEffect(current?.id, nextSong?.id) {
        listOfNotNull(current?.id, nextSong?.id).forEach { vm.requestAnalysis(it) }
    }
    LaunchedEffect(dj.note) {
        dj.note?.let {
            actions.showMessage(it)
            AutoDj.consumeNote()
        }
    }

    val smoothA by rememberSmoothPosition(position, player.isPlaying)
    val mixing = dj.phase == AutoDj.Phase.MIXING || dj.phase == AutoDj.Phase.SETTLING || dj.phase == AutoDj.Phase.HANDOFF
    val smoothB by rememberSmoothPosition(
        if (mixing) dj.deckBPositionMs else (dj.plan?.inStartMs ?: b?.firstDownbeatMs ?: 0L),
        mixing && player.isPlaying,
        dj.deckBSpeed,
    )

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Auto DJ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(if (config.enabled) "On" else "Off", color = if (config.enabled) CadenceColors.TextPrimary else Dim, modifier = Modifier.padding(end = 10.dp))
            Switch(
                checked = config.enabled,
                onCheckedChange = { on -> AutoDj.updateConfig { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CadenceColors.Background,
                    checkedTrackColor = CadenceColors.TextPrimary,
                    uncheckedThumbColor = Dim,
                    uncheckedTrackColor = CadenceColors.Background,
                    uncheckedBorderColor = CadenceColors.Outline,
                ),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            DeckCard("NOW", current, a, smoothA, dj, isDeckA = true, working = working)
            MixInfo(dj, a, b)
            DeckCard("NEXT", nextSong, b, smoothB, dj, isDeckA = false, working = working)

            // ---- Crossfader ----
            Text("CROSSFADER", style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.padding(start = 20.dp, top = 18.dp))
            Crossfader(
                value = if (mixing) dj.progress else 0f,
                enabled = dj.phase == AutoDj.Phase.MIXING,
                onChange = { AutoDj.setFader(it) },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("A", style = MaterialTheme.typography.labelMedium, color = Dim)
                Text(
                    if (dj.manual) "MANUAL · tap to return to auto" else "AUTO",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dj.manual) CadenceColors.TextPrimary else Dim,
                    modifier = Modifier.clickable(enabled = dj.manual) { AutoDj.setFader(null) },
                )
                Text("B", style = MaterialTheme.typography.labelMedium, color = Dim)
            }

            // ---- Controls ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MinimalButton("Mix now", null, { AutoDj.mixNow() }, Modifier.weight(1f), primary = true)
                MinimalButton("+8 bars", null, { AutoDj.extend(8) }, Modifier.weight(1f))
                MinimalButton("Skip", null, { AutoDj.skipTransition() }, Modifier.weight(1f))
            }

            // ---- Settings ----
            SettingLabel("TRANSITION")
            ChipRow(AutoDj.Style.entries.map { it.label }, AutoDj.Style.entries.indexOf(config.style)) { i ->
                AutoDj.updateConfig { it.copy(style = AutoDj.Style.entries[i]) }
            }
            SettingLabel("LONGEST MIX")
            val lengths = listOf(4, 8, 16, 32)
            ChipRow(lengths.map { "$it bars" }, lengths.indexOf(config.maxBars).coerceAtLeast(0)) { i ->
                AutoDj.updateConfig { it.copy(maxBars = lengths[i]) }
            }
            SettingLabel("BEAT-MATCHING")
            val tempos = listOf(0, 4, 6, 8, 12)
            ChipRow(
                tempos.map { if (it == 0) "Off" else "±$it%" },
                if (!config.beatMatch) 0 else tempos.indexOf(config.maxTempoPct).coerceAtLeast(1),
            ) { i ->
                AutoDj.updateConfig { if (i == 0) it.copy(beatMatch = false) else it.copy(beatMatch = true, maxTempoPct = tempos[i]) }
            }
            Text(
                "Tempo is changed without changing pitch. Songs further apart than the limit are blended without matching.",
                style = MaterialTheme.typography.bodySmall,
                color = CadenceColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            SettingLabel("QUEUE")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MinimalButton("Harmonic order", null, {
                    vm.sortQueue(QueueOrder.HARMONIC)
                    actions.showMessage("Queue ordered by key and tempo")
                }, Modifier.weight(1f))
                MinimalButton("Analyse library", null, {
                    vm.analyseLibrary()
                    actions.showMessage("Analysing ${library.songs.size} songs in the background")
                }, Modifier.weight(1f))
            }
            val status = buildString {
                val w = working
                if (w != null) {
                    append("Analysing “${vm.songById(w.first)?.title ?: "song"}” ${(w.second * 100).toInt()}%")
                    if (pending > 0) append(" · $pending waiting")
                } else {
                    append("${analyses.size} songs analysed")
                    if (pending > 0) append(" · $pending waiting")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (sel) CadenceColors.Background else CadenceColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) CadenceColors.TextPrimary else Color.Transparent)
                    .border(1.dp, if (sel) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun DeckCard(
    label: String,
    song: Song?,
    analysis: TrackAnalysis?,
    positionMs: Long,
    dj: AutoDj.State,
    isDeckA: Boolean,
    working: Pair<Long, Float>?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtImage(song?.art, Modifier.size(44.dp), RoundedCornerShape(2.dp), iconSize = 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
                Text(song?.title ?: "—", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "", style = MaterialTheme.typography.bodySmall, color = CadenceColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (analysis != null) {
                    val tempo = if (!isDeckA && dj.deckBSpeed != 1f && dj.phase != AutoDj.Phase.IDLE) {
                        " ${if (dj.deckBSpeed > 1f) "+" else ""}${"%.1f".format((dj.deckBSpeed - 1f) * 100)}%"
                    } else ""
                    Text("${"%.1f".format(analysis.bpm)} BPM$tempo", style = MaterialTheme.typography.labelLarge)
                    Text("${analysis.camelot} · ${analysis.keyName}", style = MaterialTheme.typography.labelMedium, color = Dim)
                } else if (song != null && working?.first == song.id) {
                    Text("Analysing ${((working?.second ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Dim)
                } else if (song != null) {
                    Text("Waiting for analysis", style = MaterialTheme.typography.labelMedium, color = Dim)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Waveform(
            analysis = analysis,
            positionMs = positionMs,
            transitionStart = if (isDeckA) dj.plan?.outStartMs else dj.plan?.inStartMs,
            transitionLength = dj.plan?.let { if (isDeckA) it.lengthMs else (it.lengthMs * it.speed).toLong() },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        if (analysis != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    analysis.sectionAt(positionMs)?.type?.label?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    buildString {
                        if (analysis.introEndMs > 0) append("Intro ${formatDuration(analysis.introEndMs)}  ")
                        if (analysis.outroStartMs < analysis.durationMs) append("Outro ${formatDuration(analysis.outroStartMs)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                )
            }
        }
    }
}

@Composable
private fun MixInfo(dj: AutoDj.State, a: TrackAnalysis?, b: TrackAnalysis?) {
    val plan = dj.plan
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hair)) {
            Box(Modifier.fillMaxWidth(dj.progress.coerceIn(0f, 1f)).height(1.dp).background(CadenceColors.TextPrimary))
        }
        Spacer(Modifier.height(8.dp))
        val text = when {
            !dj.config.enabled -> "Auto DJ is off — songs change normally"
            plan == null -> "Waiting for a next song"
            dj.phase == AutoDj.Phase.MIXING -> "Mixing · ${plan.style.label}${if (plan.bars > 0) " · ${plan.bars} bars" else ""}"
            dj.phase == AutoDj.Phase.SETTLING || dj.phase == AutoDj.Phase.HANDOFF -> "Finishing the mix"
            else -> "Next mix at ${formatDuration(plan.outStartMs)} · ${plan.style.label}${if (plan.bars > 0) " · ${plan.bars} bars" else ""}"
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (plan != null && a != null && b != null) {
            val keyScore = Camelot.compatibility(a.keyIndex, b.keyIndex)
            val keyText = when {
                keyScore >= 0.85f -> "keys compatible"
                keyScore >= 0.55f -> "keys OK"
                else -> "keys clash (filtered)"
            }
            val tempoText = if (plan.beatMatched) "beat-matched ${if (plan.speed >= 1f) "+" else ""}${"%.1f".format((plan.speed - 1f) * 100)}%" else "tempo too far apart, blended"
            Text(
                "${a.camelot} → ${b.camelot} · $keyText · $tempoText",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        } else if (plan != null && !plan.analysed) {
            Text("Analysing songs for beat-matching… using a smooth crossfade until ready", style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
    }
}

/** Scrolling 3-band waveform around the playhead with beat grid, phrases, intro/outro and the mix window. */
@Composable
fun Waveform(
    analysis: TrackAnalysis?,
    positionMs: Long,
    transitionStart: Long?,
    transitionLength: Long?,
    modifier: Modifier = Modifier,
    windowMs: Long = 12_000,
) {
    Canvas(modifier.background(Color(0xFF0B0B0B))) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        if (analysis == null) {
            drawLine(Hair, Offset(0f, mid), Offset(w, mid), 1.dp.toPx())
            drawLine(Color.White, Offset(w / 2f, 0f), Offset(w / 2f, h), 1.dp.toPx())
            return@Canvas
        }
        val dur = analysis.durationMs.coerceAtLeast(1)
        val startMs = positionMs - windowMs / 2
        fun xOf(t: Double): Float = ((t - startMs) / windowMs * w).toFloat()

        // Intro / outro shading.
        if (analysis.introEndMs > 0) {
            val x1 = xOf(analysis.introEndMs.toDouble())
            if (x1 > 0) drawRect(Color.White.copy(alpha = 0.05f), Offset(0f, 0f), Size(x1.coerceAtMost(w), h))
        }
        if (analysis.outroStartMs < dur) {
            val x0 = xOf(analysis.outroStartMs.toDouble()).coerceAtLeast(0f)
            if (x0 < w) drawRect(Color.White.copy(alpha = 0.05f), Offset(x0, 0f), Size(w - x0, h))
        }
        // Transition window.
        if (transitionStart != null && transitionLength != null) {
            val x0 = xOf(transitionStart.toDouble())
            val x1 = xOf((transitionStart + transitionLength).toDouble())
            if (x1 > 0 && x0 < w) {
                drawRect(
                    Color.White.copy(alpha = 0.5f),
                    Offset(x0, 1f),
                    Size(x1 - x0, h - 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
            }
        }
        // Waveform bins.
        val bins = analysis.wavePeak.size
        val msPerBin = dur.toDouble() / bins
        val firstBin = max(0, (startMs / msPerBin).toInt())
        val lastBin = ((startMs + windowMs) / msPerBin).toInt().coerceAtMost(bins - 1)
        val barW = max(1f, (msPerBin / windowMs * w).toFloat() * 0.7f)
        for (i in firstBin..lastBin) {
            val x = xOf(i * msPerBin)
            val peak = (analysis.wavePeak[i].toInt() and 0xFF) / 255f
            val lo = (analysis.waveLow[i].toInt() and 0xFF) / 255f
            val md = (analysis.waveMid[i].toInt() and 0xFF) / 255f
            val hi = (analysis.waveHigh[i].toInt() and 0xFF) / 255f
            val played = i * msPerBin < positionMs
            val alpha = if (played) 0.45f else 1f
            val total = (lo + md + hi).coerceAtLeast(0.001f)
            val amp = peak * (h / 2f - 2f)
            val loH = amp * lo / total
            val mdH = amp * md / total
            val hiH = amp * hi / total
            drawRect(Color.White.copy(alpha = 0.92f * alpha), Offset(x, mid - loH), Size(barW, loH * 2))
            drawRect(Color.White.copy(alpha = 0.5f * alpha), Offset(x, mid - loH - mdH), Size(barW, mdH))
            drawRect(Color.White.copy(alpha = 0.5f * alpha), Offset(x, mid + loH), Size(barW, mdH))
            drawRect(Color.White.copy(alpha = 0.25f * alpha), Offset(x, mid - loH - mdH - hiH), Size(barW, hiH))
            drawRect(Color.White.copy(alpha = 0.25f * alpha), Offset(x, mid + loH + mdH), Size(barW, hiH))
        }
        // Beat grid: beats short, bars taller, 8-bar phrases full height.
        val beat = analysis.beatMs
        var k = Math.ceil((startMs - analysis.firstDownbeatMs) / beat).toLong()
        while (true) {
            val t = analysis.firstDownbeatMs + k * beat
            if (t > startMs + windowMs) break
            if (t >= 0) {
                val x = xOf(t)
                val beatInBar = ((k % 4) + 4) % 4
                val barIndex = Math.floorDiv(k, 4L)
                val phrase = beatInBar == 0L && ((barIndex % 8) + 8) % 8 == 0L
                val len = when {
                    phrase -> h
                    beatInBar == 0L -> h * 0.22f
                    else -> h * 0.1f
                }
                val c = when {
                    phrase -> Color.White.copy(alpha = 0.55f)
                    beatInBar == 0L -> Color.White.copy(alpha = 0.6f)
                    else -> Color.White.copy(alpha = 0.3f)
                }
                drawLine(c, Offset(x, 0f), Offset(x, len), 1f)
            }
            k++
        }
        // Playhead.
        drawLine(Color.White, Offset(w / 2f, 0f), Offset(w / 2f, h), 1.5.dp.toPx())
    }
}

/** Hairline crossfader with a tick handle; drag to take over the automatic mix. */
@Composable
private fun Crossfader(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(value) }
    val shown = if (dragging) local else value
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    local = (o.x / size.width).coerceIn(0f, 1f)
                    current(local)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; local = (o.x / size.width).coerceIn(0f, 1f); current(local) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        local = (change.position.x / size.width).coerceIn(0f, 1f)
                        current(local)
                    },
                )
            }
    ) {
        val y = size.height / 2f
        drawLine(Color(0xFF333333), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        drawLine(Color(0xFF555555), Offset(size.width / 2f, y - 6.dp.toPx()), Offset(size.width / 2f, y + 6.dp.toPx()), 1.dp.toPx())
        val x = shown * size.width
        val handleW = 14.dp.toPx()
        drawRect(
            if (enabled) Color.White else Color(0xFF6E6E6E),
            Offset(x - handleW / 2f, y - 12.dp.toPx()),
            Size(handleW, 24.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
        )
        drawLine(if (enabled) Color.White else Color(0xFF6E6E6E), Offset(x, y - 8.dp.toPx()), Offset(x, y + 8.dp.toPx()), 1.dp.toPx())
    }
}
