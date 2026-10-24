package com.cadence.player.ui.visualizer

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.cadence.player.audio.AudioFrame
import com.cadence.player.audio.SectionType
import com.cadence.player.audio.TrackAnalysis
import kotlin.math.max
import kotlin.math.min

enum class Scene(val label: String) {
    NEBULA("Nebula"),
    LIQUID("Liquid"),
    WARP("Warp"),
    HORIZON("Horizon"),
    AURA("Aura"),
    AMBIENT("Always-on"),
}

enum class PaletteMode(val label: String) {
    ENERGY("Energy & mood"),
    ALBUM("Album art"),
    MONO("Mono"),
}

data class VisualSettings(
    val scene: Scene = Scene.NEBULA,
    val palette: PaletteMode = PaletteMode.ENERGY,
    val intensity: Float = 1f,
    val density: Float = 1f,
    val liveAnalysis: Boolean = true,
    val showInfo: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    companion object {
        fun load(context: Context): VisualSettings {
            val p = context.getSharedPreferences("visualizer", Context.MODE_PRIVATE)
            val d = VisualSettings()
            return VisualSettings(
                scene = runCatching { Scene.valueOf(p.getString("scene", d.scene.name)!!) }.getOrDefault(d.scene),
                palette = runCatching { PaletteMode.valueOf(p.getString("palette", d.palette.name)!!) }.getOrDefault(d.palette),
                intensity = p.getFloat("intensity", d.intensity),
                density = p.getFloat("density", d.density),
                liveAnalysis = p.getBoolean("live", d.liveAnalysis),
                showInfo = p.getBoolean("info", d.showInfo),
                keepScreenOn = p.getBoolean("keep_on", d.keepScreenOn),
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("visualizer", Context.MODE_PRIVATE).edit()
            .putString("scene", scene.name)
            .putString("palette", palette.name)
            .putFloat("intensity", intensity)
            .putFloat("density", density)
            .putBoolean("live", liveAnalysis)
            .putBoolean("info", showInfo)
            .putBoolean("keep_on", keepScreenOn)
            .apply()
    }
}

/**
 * Everything a scene needs for one frame: live audio, where we are in the song's structure,
 * the mood-derived colours, and user settings.
 */
class VisualFrame {
    val audio = AudioFrame()
    var time = 0f
    var dt = 0.016f

    // Structure
    var section: SectionType = SectionType.GROOVE
    var sectionProgress = 0f
    var sectionChanged = false
    var nextSection: SectionType? = null
    var barsToNext: Int? = null
    var bpm = 120f
    var beatPhase = 0f
    var barPhase = 0f
    var beatIndex = 0L
    var newBeat = false
    var newBar = false
    var newPhrase = false
    var barInPhrase = 0
    var structured = false

    /** Combined energy 0..1 (song structure + live loudness), smoothed. */
    var energy = 0.5f
    /** Decaying flash on beats (1 at the beat, fading). */
    var beatPulse = 0f
    /** Decaying flash at section changes, strongest for drops. */
    var sectionPulse = 0f

    // Palette
    var c1 = Color(0xFF6E6EFF)
    var c2 = Color(0xFF3A1B72)
    var c3 = Color(0xFFFF6BC8)

    var intensity = 1f
    var density = 1f

    private var lastSection: SectionType? = null
    private var lastBeat = Long.MIN_VALUE
    private var lastLiveBeatTime = 0f
    private var liveBeatInterval = 0.5f
    private var rising = 0f
    private var prevLevel = 0f

    /**
     * Updates structure fields from the analysis (if available) or, as a fallback for radio and
     * unanalysed songs, from live loudness trends.
     */
    fun updateStructure(analysis: TrackAnalysis?, positionMs: Long) {
        sectionChanged = false
        newBeat = false
        newBar = false
        newPhrase = false
        if (analysis != null) {
            structured = true
            bpm = analysis.bpm
            val sec = analysis.sectionAt(positionMs)
            if (sec != null) {
                section = sec.type
                sectionProgress = ((positionMs - sec.startMs).toFloat() / max(1L, sec.endMs - sec.startMs)).coerceIn(0f, 1f)
                val idx = analysis.sections.indexOf(sec)
                val next = analysis.sections.getOrNull(idx + 1)
                nextSection = next?.type
                barsToNext = next?.let { ((it.startMs - positionMs) / analysis.barMs).toInt().coerceAtLeast(0) }
            }
            val beatPos = analysis.beatAt(positionMs)
            val beatIdx = Math.floor(beatPos).toLong()
            beatPhase = (beatPos - beatIdx).toFloat()
            val barPos = beatPos / 4.0
            barPhase = (barPos - Math.floor(barPos)).toFloat()
            if (lastBeat != Long.MIN_VALUE && beatIdx != lastBeat && beatIdx >= 0) {
                newBeat = true
                if (Math.floorMod(beatIdx, 4L) == 0L) {
                    newBar = true
                    val bar = Math.floorDiv(beatIdx, 4L)
                    barInPhrase = Math.floorMod(bar, 8L).toInt()
                    if (barInPhrase == 0) newPhrase = true
                }
            }
            lastBeat = beatIdx
            beatIndex = beatIdx
            val target = analysis.barEnergyAt(positionMs) * 0.7f + audio.level * 0.6f
            energy += (target.coerceIn(0f, 1f) - energy) * min(1f, dt * 2.5f)
        } else {
            structured = false
            nextSection = null
            barsToNext = null
            // Live fallback: beat timing from detected onsets, sections from loudness trends.
            if (audio.beat) {
                val interval = time - lastLiveBeatTime
                if (interval in 0.28f..1.2f) liveBeatInterval += (interval - liveBeatInterval) * 0.3f
                lastLiveBeatTime = time
                newBeat = true
                beatIndex++
                if (beatIndex % 4 == 0L) newBar = true
            }
            bpm = 60f / liveBeatInterval
            beatPhase = ((time - lastLiveBeatTime) / liveBeatInterval).coerceIn(0f, 1f)
            val ratio = audio.level / max(0.05f, audio.longEnergy)
            rising += ((audio.level - prevLevel) * 30f - rising) * 0.02f
            prevLevel = audio.level
            section = when {
                ratio > 1.3f -> SectionType.DROP
                ratio < 0.6f -> SectionType.BREAKDOWN
                rising > 0.04f -> SectionType.BUILD
                else -> SectionType.GROOVE
            }
            sectionProgress = 0.5f
            energy += ((audio.level * 1.2f).coerceIn(0f, 1f) - energy) * min(1f, dt * 2f)
        }
        if (lastSection != null && section != lastSection) {
            sectionChanged = true
            sectionPulse = if (section == SectionType.DROP) 1f else 0.5f
        }
        lastSection = section
        if (newBeat) beatPulse = 1f
        beatPulse = max(0f, beatPulse - dt * 3.2f)
        sectionPulse = max(0f, sectionPulse - dt * 0.8f)
    }

    /** Smoothly moves the palette towards the colours for the current mood. */
    fun updatePalette(mode: PaletteMode, analysis: TrackAnalysis?, album: List<Color>?) {
        val targets: Triple<Color, Color, Color> = when (mode) {
            PaletteMode.MONO -> Triple(Color.White, Color(0xFF555555), Color(0xFFBBBBBB))
            PaletteMode.ALBUM -> {
                val base = album?.takeIf { it.size >= 3 } ?: listOf(Color(0xFF8E7CFF), Color(0xFF2B1E5C), Color(0xFFFF7AB8))
                val boost = sectionBoost()
                Triple(
                    lighten(base[0], boost),
                    base[1],
                    lighten(base[2], boost * 0.8f),
                )
            }
            PaletteMode.ENERGY -> energyPalette(analysis)
        }
        val k = min(1f, dt * 1.6f)
        c1 = lerp(c1, targets.first, k)
        c2 = lerp(c2, targets.second, k)
        c3 = lerp(c3, targets.third, k)
    }

    private fun sectionBoost(): Float = when (section) {
        SectionType.DROP -> 0.25f
        SectionType.BUILD -> 0.1f + sectionProgress * 0.15f
        SectionType.BREAKDOWN -> -0.1f
        else -> 0f
    }

    private fun lighten(c: Color, amount: Float): Color =
        if (amount >= 0) lerp(c, Color.White, amount) else lerp(c, Color.Black, -amount)

    private fun energyPalette(analysis: TrackAnalysis?): Triple<Color, Color, Color> {
        // Calm = deep blues/violets, mid = magenta, high = warm orange/gold. Minor keys sit cooler.
        val e = energy
        var hue = if (e < 0.55f) 235f + (e / 0.55f) * 85f else 320f + ((e - 0.55f) / 0.45f) * 75f
        if (analysis?.isMinor == true) hue -= 18f
        hue = ((hue % 360f) + 360f) % 360f
        val sat = when (section) {
            SectionType.DROP -> 0.95f
            SectionType.BREAKDOWN -> 0.5f
            else -> 0.75f
        }
        val bright = (analysis?.brightness ?: audio.brightness).coerceIn(0f, 1f)
        val light = (0.48f + bright * 0.12f + sectionBoost() * 0.4f).coerceIn(0.3f, 0.72f)
        return Triple(
            Color.hsl(hue, sat, light),
            Color.hsl((hue + 40f) % 360f, sat * 0.85f, light * 0.45f),
            Color.hsl((hue + 300f) % 360f, sat, (light + 0.15f).coerceAtMost(0.85f)),
        )
    }
}
