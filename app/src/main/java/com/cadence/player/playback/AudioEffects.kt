package com.cadence.player.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqState(
    /** True once the effects are attached to the player's audio session. */
    val available: Boolean = false,
    val enabled: Boolean = false,
    /** Centre frequency of each band, in Hz. */
    val bandFrequencies: List<Int> = emptyList(),
    /** Gain of each band, in millibels (100 mB = 1 dB). */
    val bandLevels: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val preset: String = EqPresets.FLAT,
    /** 0..1000 */
    val bassStrength: Int = 0,
    val bassSupported: Boolean = false,
    /** 0..1000 */
    val virtualizerStrength: Int = 0,
    val virtualizerSupported: Boolean = false,
    /** Extra gain in millibels, 0..1000. */
    val loudnessGain: Int = 0,
    val loudnessSupported: Boolean = false,
)

object EqPresets {
    const val FLAT = "Flat"
    const val CUSTOM = "Custom"

    /** Reference curve frequencies in Hz; every preset gives a gain in dB for each. */
    private val referenceHz = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)

    private val curves: LinkedHashMap<String, List<Double>> = linkedMapOf(
        FLAT to listOf(0.0, 0.0, 0.0, 0.0, 0.0),
        "Bass Boost" to listOf(6.0, 4.0, 0.5, 0.0, 0.0),
        "Treble Boost" to listOf(0.0, 0.0, 0.5, 4.0, 6.0),
        "Rock" to listOf(5.0, 3.0, -1.5, 3.0, 5.0),
        "Pop" to listOf(-1.0, 2.0, 4.0, 2.0, -1.0),
        "Hip-Hop" to listOf(5.0, 4.0, 0.0, 1.5, 3.0),
        "Electronic" to listOf(4.5, 2.5, 0.0, 2.5, 5.0),
        "Jazz" to listOf(4.0, 2.0, -1.5, 2.0, 4.0),
        "Classical" to listOf(4.5, 3.0, -1.5, 3.5, 4.0),
        "Acoustic" to listOf(4.0, 2.0, 1.0, 3.0, 3.0),
        "Vocal" to listOf(-2.0, 0.0, 4.0, 3.0, 0.0),
        "Late Night" to listOf(3.0, 1.5, 0.0, -1.0, -2.5),
    )

    val names: List<String> = curves.keys.toList()

    /** Maps a preset curve onto the device's actual bands, in millibels. */
    fun levelsFor(name: String, bandHz: List<Int>, min: Int, max: Int): List<Int> {
        val curve = curves[name] ?: curves.getValue(FLAT)
        return bandHz.map { hz ->
            val db = interpolate(curve, hz.toDouble().coerceAtLeast(20.0))
            (db * 100).roundToInt().coerceIn(min, max)
        }
    }

    private fun interpolate(curve: List<Double>, hz: Double): Double {
        val x = ln(hz)
        val xs = referenceHz.map { ln(it) }
        if (x <= xs.first()) return curve.first()
        if (x >= xs.last()) return curve.last()
        for (i in 0 until xs.size - 1) {
            if (x <= xs[i + 1]) {
                val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                return curve[i] + (curve[i + 1] - curve[i]) * t
            }
        }
        return 0.0
    }
}

/**
 * Owns the Android audio effects attached to the player's audio session.
 * The playback service attaches it; the UI reads [state] and calls the setters.
 */
object AudioEffects {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "levels"
    private const val KEY_PRESET = "preset"
    private const val KEY_BASS = "bass"
    private const val KEY_VIRT = "virtualizer"
    private const val KEY_LOUD = "loudness"

    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences("audio_effects", Context.MODE_PRIVATE)
        prefs = p
        _state.value = EqState(
            enabled = p.getBoolean(KEY_ENABLED, false),
            preset = p.getString(KEY_PRESET, EqPresets.FLAT) ?: EqPresets.FLAT,
            bassStrength = p.getInt(KEY_BASS, 0),
            virtualizerStrength = p.getInt(KEY_VIRT, 0),
            loudnessGain = p.getInt(KEY_LOUD, 0),
        )
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        releaseEffects()
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

        val saved = _state.value
        val eq = equalizer
        var freqs = emptyList<Int>()
        var min = -1500
        var max = 1500
        var levels = emptyList<Int>()
        if (eq != null) {
            runCatching {
                val bands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                min = range[0].toInt()
                max = range[1].toInt()
                freqs = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
                val savedLevels = prefs?.getString(KEY_LEVELS, null)
                    ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                levels = if (saved.preset == EqPresets.CUSTOM && savedLevels != null && savedLevels.size == bands) {
                    savedLevels.map { it.coerceIn(min, max) }
                } else {
                    EqPresets.levelsFor(saved.preset, freqs, min, max)
                }
            }
        }
        _state.value = saved.copy(
            available = eq != null && freqs.isNotEmpty(),
            bandFrequencies = freqs,
            bandLevels = levels,
            minLevel = min,
            maxLevel = max,
            bassSupported = bassBoost?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: false,
            virtualizerSupported = virtualizer?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: false,
            loudnessSupported = loudness != null,
        )
        applyAll()
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBandLevel(band: Int, level: Int) = update { s ->
        if (band !in s.bandLevels.indices) return@update s
        val newLevels = s.bandLevels.toMutableList().also { it[band] = level.coerceIn(s.minLevel, s.maxLevel) }
        s.copy(bandLevels = newLevels, preset = EqPresets.CUSTOM, enabled = true)
    }

    fun applyPreset(name: String) = update { s ->
        s.copy(
            preset = name,
            bandLevels = EqPresets.levelsFor(name, s.bandFrequencies, s.minLevel, s.maxLevel),
            enabled = true,
        )
    }

    fun setBassStrength(value: Int) = update { it.copy(bassStrength = value.coerceIn(0, 1000), enabled = true) }

    fun setVirtualizerStrength(value: Int) =
        update { it.copy(virtualizerStrength = value.coerceIn(0, 1000), enabled = true) }

    fun setLoudnessGain(value: Int) = update { it.copy(loudnessGain = value.coerceIn(0, 1000), enabled = true) }

    fun reset() = update { s ->
        s.copy(
            preset = EqPresets.FLAT,
            bandLevels = EqPresets.levelsFor(EqPresets.FLAT, s.bandFrequencies, s.minLevel, s.maxLevel),
            bassStrength = 0,
            virtualizerStrength = 0,
            loudnessGain = 0,
        )
    }

    @Synchronized
    fun release() {
        releaseEffects()
        _state.value = _state.value.copy(available = false)
    }

    private inline fun update(transform: (EqState) -> EqState) {
        synchronized(this) {
            _state.value = transform(_state.value)
            save()
            applyAll()
        }
    }

    private fun applyAll() {
        val s = _state.value
        equalizer?.let { eq ->
            runCatching {
                s.bandLevels.forEachIndexed { i, level -> eq.setBandLevel(i.toShort(), level.toShort()) }
                eq.setEnabled(s.enabled)
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                if (s.bassSupported) bb.setStrength(s.bassStrength.toShort())
                bb.setEnabled(s.enabled && s.bassStrength > 0)
            }
        }
        virtualizer?.let { v ->
            runCatching {
                if (s.virtualizerSupported) v.setStrength(s.virtualizerStrength.toShort())
                v.setEnabled(s.enabled && s.virtualizerStrength > 0)
            }
        }
        loudness?.let { l ->
            runCatching {
                l.setTargetGain(s.loudnessGain)
                l.setEnabled(s.enabled && s.loudnessGain > 0)
            }
        }
    }

    private fun save() {
        val s = _state.value
        prefs?.edit()
            ?.putBoolean(KEY_ENABLED, s.enabled)
            ?.putString(KEY_PRESET, s.preset)
            ?.putString(KEY_LEVELS, s.bandLevels.joinToString(","))
            ?.putInt(KEY_BASS, s.bassStrength)
            ?.putInt(KEY_VIRT, s.virtualizerStrength)
            ?.putInt(KEY_LOUD, s.loudnessGain)
            ?.apply()
    }

    private fun releaseEffects() {
        listOfNotNull(equalizer, bassBoost, virtualizer, loudness).forEach { runCatching { it.release() } }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }
}
