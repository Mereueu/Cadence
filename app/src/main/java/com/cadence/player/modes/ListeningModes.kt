package com.cadence.player.modes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.vector.ImageVector
import com.cadence.player.audio.AnalysisManager
import com.cadence.player.audio.Camelot
import com.cadence.player.audio.TrackAnalysis
import com.cadence.player.data.Song
import com.cadence.player.playback.AutoDj
import com.cadence.player.playback.Normalizer
import com.cadence.player.playback.SleepTimer
import com.cadence.player.ui.custom.LayoutPreset
import com.cadence.player.ui.custom.UiConfig
import com.cadence.player.ui.custom.UiStore
import com.cadence.player.ui.theme.LineIcons
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One tap changes queue, interface and playback behaviour together.
 * Nothing is inferred from location or sensors: modes change only when the user picks one.
 */
enum class ListeningMode(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val preset: LayoutPreset,
) {
    SLEEP("Sleep", "Calm music, volume slowly lowers, then stops", LineIcons.Sleep, LayoutPreset.SLEEP),
    WALK("Walk", "Steady tempo, big controls, a long queue", LineIcons.Walk, LayoutPreset.WALK),
    GYM("Gym", "High energy, no slow intros, no gaps", LineIcons.Bolt, LayoutPreset.GYM),
    DRIVE("Drive", "Familiar songs, huge buttons, even loudness", LineIcons.Car, LayoutPreset.DRIVE),
    FOCUS("Focus", "Smooth energy, no loudness jumps, long blends", LineIcons.Focus, LayoutPreset.FOCUS),
    PARTY("Party", "DJ mixing, harmonic order, never runs dry", LineIcons.Party, LayoutPreset.PARTY),
}

/** Snapshot of the user's own settings so leaving a mode restores them exactly. */
private data class Snapshot(val ui: UiConfig, val dj: AutoDj.Config, val normalize: Boolean)

object ModeEngine {
    private var prefs: SharedPreferences? = null
    private val _mode = MutableStateFlow<ListeningMode?>(null)
    val mode: StateFlow<ListeningMode?> = _mode.asStateFlow()
    private var snapshot: Snapshot? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("modes", Context.MODE_PRIVATE)
        prefs = p
        _mode.value = p.getString("mode", null)?.let { runCatching { ListeningMode.valueOf(it) }.getOrNull() }
    }

    /**
     * Activates [mode]: applies its layout and playback behaviour and returns the queue to play.
     * Passing the active mode again turns it off.
     */
    fun activate(mode: ListeningMode, library: List<Song>, playCounts: Map<Long, Int>, favorites: List<Long>): List<Song> {
        if (snapshot == null) {
            snapshot = Snapshot(UiStore.config.value, AutoDj.state.value.config, Normalizer.enabled)
        }
        _mode.value = mode
        prefs?.edit()?.putString("mode", mode.name)?.apply()

        // Interface.
        UiStore.update { it.copy(preset = mode.preset, player = com.cadence.player.ui.custom.Presets.player(mode.preset), gestures = com.cadence.player.ui.custom.Presets.gestures(mode.preset)) }

        // Playback behaviour.
        SleepTimer.cancel()
        when (mode) {
            ListeningMode.SLEEP -> {
                AutoDj.updateConfig { it.copy(enabled = true, style = AutoDj.Style.BLEND, maxBars = 16, beatMatch = false, skipIntros = false) }
                Normalizer.enabled = true
                SleepTimer.startGradual(45)
            }
            ListeningMode.WALK -> {
                AutoDj.updateConfig { it.copy(enabled = true, style = AutoDj.Style.AUTO, maxBars = 8, beatMatch = true, skipIntros = false) }
                Normalizer.enabled = true
            }
            ListeningMode.GYM -> {
                AutoDj.updateConfig { it.copy(enabled = true, style = AutoDj.Style.AUTO, maxBars = 8, beatMatch = true, maxTempoPct = 8, skipIntros = true) }
                Normalizer.enabled = true
            }
            ListeningMode.DRIVE -> {
                AutoDj.updateConfig { it.copy(enabled = false, skipIntros = false) }
                Normalizer.enabled = true
            }
            ListeningMode.FOCUS -> {
                AutoDj.updateConfig { it.copy(enabled = true, style = AutoDj.Style.BLEND, maxBars = 32, beatMatch = true, skipIntros = false) }
                Normalizer.enabled = true
            }
            ListeningMode.PARTY -> {
                AutoDj.updateConfig { it.copy(enabled = true, style = AutoDj.Style.AUTO, maxBars = 16, beatMatch = true, skipIntros = false) }
                Normalizer.enabled = true
            }
        }
        AnalysisManager.requestBackground(library.map { it.id })
        return buildQueue(mode, library, playCounts, favorites, exclude = emptySet(), limit = initialLength(mode))
    }

    /** Leaves the current mode and restores the user's own layout and playback settings. */
    fun deactivate() {
        val s = snapshot
        _mode.value = null
        prefs?.edit()?.remove("mode")?.apply()
        SleepTimer.cancel()
        if (s != null) {
            UiStore.update { s.ui }
            AutoDj.updateConfig { s.dj }
            Normalizer.enabled = s.normalize
        }
        snapshot = null
    }

    /** Modes that should never run out of music. */
    fun keepsQueueFull(mode: ListeningMode?): Boolean =
        mode == ListeningMode.PARTY || mode == ListeningMode.WALK || mode == ListeningMode.GYM || mode == ListeningMode.DRIVE

    private fun initialLength(mode: ListeningMode) = when (mode) {
        ListeningMode.SLEEP -> 18
        ListeningMode.WALK -> 150
        ListeningMode.GYM -> 60
        ListeningMode.DRIVE -> 100
        ListeningMode.FOCUS -> 60
        ListeningMode.PARTY -> 80
    }

    /** Picks and orders songs for a mode. Uses analysis when available, listening history otherwise. */
    fun buildQueue(
        mode: ListeningMode,
        library: List<Song>,
        playCounts: Map<Long, Int>,
        favorites: List<Long>,
        exclude: Set<Long>,
        limit: Int,
    ): List<Song> {
        val pool = library.filter { it.id !in exclude && it.duration > 60_000 }
        if (pool.isEmpty()) return emptyList()
        val favSet = favorites.toSet()
        val maxPlays = (playCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)

        fun familiarity(s: Song): Float =
            (playCounts[s.id] ?: 0).toFloat() / maxPlays * 0.7f + if (s.id in favSet) 0.3f else 0f

        fun fit(s: Song, a: TrackAnalysis?): Float {
            if (a == null) {
                // No analysis yet: lean on what the listener likes.
                return 0.35f + familiarity(s) * 0.5f + (Math.random() * 0.15).toFloat()
            }
            val e = a.energy
            val bpm = a.bpm
            return when (mode) {
                ListeningMode.SLEEP -> (1f - e) * 0.7f + (if (bpm < 105) 0.3f else 0f) - (if (e > 0.6f) 0.5f else 0f)
                ListeningMode.WALK -> band(bpm, 98f, 132f) * 0.55f + band(e, 0.35f, 0.8f) * 0.35f + familiarity(s) * 0.1f
                ListeningMode.GYM -> e * 0.6f + band(bpm, 118f, 178f) * 0.4f
                ListeningMode.DRIVE -> familiarity(s) * 0.6f + band(e, 0.3f, 0.9f) * 0.4f
                ListeningMode.FOCUS -> band(e, 0.15f, 0.55f) * 0.7f + (1f - a.brightness) * 0.15f + (if (a.sections.none { it.type == com.cadence.player.audio.SectionType.DROP }) 0.15f else 0f)
                ListeningMode.PARTY -> e * 0.45f + familiarity(s) * 0.35f + band(bpm, 110f, 140f) * 0.2f
            }
        }

        val scored = pool.map { s -> s to fit(s, AnalysisManager.get(s.id)) }
            .sortedByDescending { it.second }
        val picked = scored.take((limit * 1.5f).toInt().coerceAtLeast(limit)).shuffled().take(limit).map { it.first }

        // Order within the mode.
        return when (mode) {
            ListeningMode.SLEEP -> picked.sortedByDescending { AnalysisManager.get(it.id)?.energy ?: 0.3f } // wind down
            ListeningMode.GYM -> {
                // Warm up with a few mid-energy songs, then keep it high.
                val sorted = picked.sortedBy { AnalysisManager.get(it.id)?.energy ?: 0.6f }
                val warm = sorted.take(3)
                warm + harmonic(sorted.drop(3))
            }
            ListeningMode.FOCUS -> harmonic(picked.sortedBy { AnalysisManager.get(it.id)?.energy ?: 0.4f })
            ListeningMode.PARTY, ListeningMode.WALK -> harmonic(picked)
            ListeningMode.DRIVE -> picked
        }
    }

    private fun band(v: Float, lo: Float, hi: Float): Float = when {
        v in lo..hi -> 1f
        v < lo -> (1f - (lo - v) / (lo * 0.3f)).coerceIn(0f, 1f)
        else -> (1f - (v - hi) / (hi * 0.3f)).coerceIn(0f, 1f)
    }

    /** Greedy chain by key compatibility and tempo; unanalysed songs keep their order at the end. */
    fun harmonic(songs: List<Song>): List<Song> {
        val analysed = songs.filter { AnalysisManager.get(it.id) != null }.toMutableList()
        val rest = songs.filter { AnalysisManager.get(it.id) == null }
        if (analysed.isEmpty()) return songs
        val result = mutableListOf(analysed.removeAt(0))
        while (analysed.isNotEmpty()) {
            val from = AnalysisManager.get(result.last().id)!!
            val next = analysed.maxByOrNull { s ->
                val a = AnalysisManager.get(s.id)!!
                Camelot.compatibility(from.keyIndex, a.keyIndex) * 0.55f +
                    (1f - (abs(Camelot.tempoRatio(a.bpm, from.bpm) - 1f) / 0.08f).coerceIn(0f, 1f)) * 0.35f +
                    (1f - abs(a.energy - from.energy)) * 0.1f
            }!!
            result += next
            analysed.remove(next)
        }
        return result + rest
    }
}
