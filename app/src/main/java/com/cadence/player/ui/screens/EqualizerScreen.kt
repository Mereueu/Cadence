package com.cadence.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.player.MusicViewModel
import com.cadence.player.playback.AudioEffects
import com.cadence.player.playback.EqPresets
import com.cadence.player.ui.components.EmptyState
import com.cadence.player.ui.components.Pill
import com.cadence.player.ui.theme.CadenceColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun formatHz(hz: Int): String = when {
    hz >= 10_000 -> "${hz / 1000}k"
    hz >= 1000 -> {
        val k = (hz / 100) / 10f
        if (k % 1f == 0f) "${k.toInt()}k" else "${k}k"
    }
    else -> hz.toString()
}

private fun formatDb(millibels: Int): String {
    val db = millibels / 100f
    val rounded = (db * 10).roundToInt() / 10f
    return when {
        rounded > 0f -> "+$rounded"
        rounded < 0f -> "$rounded"
        else -> "0"
    }
}

@Composable
fun EqualizerScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val eq by vm.eq.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val contentAlpha by animateFloatAsState(if (eq.enabled) 1f else 0.45f, label = "eqAlpha")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Equalizer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                if (eq.enabled) "On" else "Off",
                style = MaterialTheme.typography.labelLarge,
                color = if (eq.enabled) accent else CadenceColors.TextSecondary,
                modifier = Modifier.padding(end = 10.dp),
            )
            Switch(
                checked = eq.enabled,
                onCheckedChange = { AudioEffects.setEnabled(it) },
                enabled = eq.available,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = CadenceColors.TextSecondary,
                    uncheckedTrackColor = CadenceColors.SurfaceHighest,
                    uncheckedBorderColor = CadenceColors.Outline,
                ),
            )
        }

        if (!eq.available) {
            EmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = "Equalizer is getting ready",
                message = "Start playing a song to wake up the audio engine. If this message stays, your device doesn't allow audio effects for third-party players.",
            )
        } else Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // ---- Response curve ----
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CadenceColors.Surface)
                    .padding(vertical = 16.dp)
                    .alpha(contentAlpha),
            ) {
                Column {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (eq.preset == EqPresets.CUSTOM) "Custom" else eq.preset,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatDb(eq.minLevel)} to ${formatDb(eq.maxLevel)} dB",
                            style = MaterialTheme.typography.labelMedium,
                            color = CadenceColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    EqCurve(
                        levels = eq.bandLevels,
                        min = eq.minLevel,
                        max = eq.maxLevel,
                        color = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(horizontal = 12.dp),
                    )
                }
            }

            // ---- Bands ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .alpha(contentAlpha),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                eq.bandLevels.forEachIndexed { band, level ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatDb(level),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (level != 0) accent else CadenceColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        BandSlider(
                            fraction = (level - eq.minLevel).toFloat() / (eq.maxLevel - eq.minLevel).coerceAtLeast(1),
                            color = accent,
                            onChange = { f ->
                                val value = eq.minLevel + f * (eq.maxLevel - eq.minLevel)
                                // Snap to 0.5 dB steps, and to 0 near the centre.
                                var mb = ((value / 50f).roundToInt() * 50)
                                if (kotlin.math.abs(mb) < 60) mb = 0
                                AudioEffects.setBandLevel(band, mb)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .width(44.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            formatHz(eq.bandFrequencies.getOrElse(band) { 0 }),
                            style = MaterialTheme.typography.labelMedium,
                            color = CadenceColors.TextSecondary,
                        )
                        Text("Hz", style = MaterialTheme.typography.labelSmall, color = CadenceColors.TextTertiary)
                    }
                }
            }

            // ---- Presets ----
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Presets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { AudioEffects.reset() }) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp), tint = CadenceColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text("Reset all", color = CadenceColors.TextSecondary)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val names = if (eq.preset == EqPresets.CUSTOM) listOf(EqPresets.CUSTOM) + EqPresets.names else EqPresets.names
                items(names) { name ->
                    Pill(
                        label = name,
                        selected = eq.enabled && eq.preset == name,
                        onClick = { if (name != EqPresets.CUSTOM) AudioEffects.applyPreset(name) else AudioEffects.setEnabled(true) },
                    )
                }
            }

            // ---- Effects ----
            Text(
                "Effects",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CadenceColors.Surface)
                    .padding(vertical = 20.dp)
                    .alpha(contentAlpha),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Knob(
                    label = "Bass",
                    value = eq.bassStrength,
                    enabled = eq.bassSupported,
                    color = accent,
                    onChange = { AudioEffects.setBassStrength(it) },
                )
                Knob(
                    label = "Surround",
                    value = eq.virtualizerStrength,
                    enabled = eq.virtualizerSupported,
                    color = accent,
                    onChange = { AudioEffects.setVirtualizerStrength(it) },
                )
                Knob(
                    label = "Loudness",
                    value = eq.loudnessGain,
                    enabled = eq.loudnessSupported,
                    color = accent,
                    onChange = { AudioEffects.setLoudnessGain(it) },
                )
            }
            Text(
                "Drag a knob up or down to adjust. Surround works best with headphones.",
                style = MaterialTheme.typography.bodySmall,
                color = CadenceColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Smooth frequency-response curve through the band levels. */
@Composable
private fun EqCurve(levels: List<Int>, min: Int, max: Int, color: Color, modifier: Modifier) {
    val animated = levels.mapIndexed { i, level ->
        animateFloatAsState(level.toFloat(), tween(250), label = "curve$i").value
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val range = (max - min).coerceAtLeast(1).toFloat()
        val grid = Color.White.copy(alpha = 0.07f)
        for (f in listOf(0.25f, 0.75f)) {
            drawLine(grid, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1.dp.toPx())
        }
        drawLine(
            Color.White.copy(alpha = 0.16f),
            Offset(0f, h / 2f),
            Offset(w, h / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        if (animated.isEmpty()) return@Canvas
        val n = animated.size
        val pts = animated.mapIndexed { i, v ->
            Offset(w * (i + 0.5f) / n, h * (1f - (v - min) / range))
        }
        val line = Path().apply {
            moveTo(0f, pts.first().y)
            lineTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2f
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
            lineTo(w, pts.last().y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { p ->
            drawCircle(CadenceColors.Background, 5.dp.toPx(), p)
            drawCircle(color, 3.5.dp.toPx(), p)
        }
    }
}

/** Vertical slider for one EQ band; [fraction] 0 = minimum gain, 1 = maximum. */
@Composable
private fun BandSlider(fraction: Float, color: Color, onChange: (Float) -> Unit, modifier: Modifier) {
    val currentOnChange by rememberUpdatedState(onChange)
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(90), label = "band")
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnChange(1f - (offset.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
    ) {
        val trackW = 6.dp.toPx()
        val cx = size.width / 2f
        val thumbR = 11.dp.toPx()
        val top = thumbR
        val bottom = size.height - thumbR
        val usable = bottom - top
        val centerY = top + usable / 2f
        val y = top + usable * (1f - shown)
        drawRoundRect(
            CadenceColors.SurfaceHighest,
            Offset(cx - trackW / 2f, top),
            Size(trackW, usable),
            CornerRadius(trackW / 2f),
        )
        val fillTop = minOf(y, centerY)
        val fillH = kotlin.math.abs(centerY - y)
        drawRoundRect(color, Offset(cx - trackW / 2f, fillTop), Size(trackW, fillH), CornerRadius(trackW / 2f))
        drawLine(Color.White.copy(alpha = 0.25f), Offset(cx - 10.dp.toPx(), centerY), Offset(cx + 10.dp.toPx(), centerY), 1.dp.toPx())
        drawCircle(color.copy(alpha = 0.25f), thumbR + 4.dp.toPx(), Offset(cx, y))
        drawCircle(Color.White, thumbR, Offset(cx, y))
        drawCircle(color, thumbR * 0.45f, Offset(cx, y))
    }
}

/** Rotary knob, 0..1000. Drag up/right to increase. */
@Composable
private fun Knob(label: String, value: Int, enabled: Boolean, color: Color, onChange: (Int) -> Unit) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    val frac by animateFloatAsState(value / 1000f, tween(80), label = "knob")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.35f),
    ) {
        Canvas(
            Modifier
                .size(88.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var acc = 0f
                    detectDragGestures(
                        onDragStart = { acc = currentValue.toFloat() },
                    ) { change, drag ->
                        change.consume()
                        acc = (acc + (drag.x - drag.y) * 3f).coerceIn(0f, 1000f)
                        currentOnChange(acc.roundToInt())
                    }
                }
        ) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = CadenceColors.SurfaceHighest,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (frac > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * frac,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            val knobR = size.minDimension / 2f - stroke - 8.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(CadenceColors.SurfaceHighest, CadenceColors.SurfaceHigh), center, knobR), knobR)
            val angle = Math.toRadians((135f + 270f * frac).toDouble())
            val r = knobR - 9.dp.toPx()
            drawCircle(
                if (frac > 0.001f) color else CadenceColors.TextTertiary,
                3.5.dp.toPx(),
                Offset(center.x + (cos(angle) * r).toFloat(), center.y + (sin(angle) * r).toFloat()),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (enabled) "${(value / 10f).roundToInt()}%" else "N/A",
            style = MaterialTheme.typography.titleSmall,
            color = if (value > 0) color else CadenceColors.TextPrimary,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = CadenceColors.TextSecondary)
    }
}
