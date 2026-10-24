package com.cadence.player.audio

import android.util.Base64
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

enum class SectionType(val label: String) {
    INTRO("Intro"),
    GROOVE("Groove"),
    BUILD("Build-up"),
    DROP("Drop"),
    BREAKDOWN("Breakdown"),
    OUTRO("Outro"),
}

data class Section(val type: SectionType, val startMs: Long, val endMs: Long, val energy: Float)

/** Everything the DJ engine and the visualizer know about one track. */
data class TrackAnalysis(
    val songId: Long,
    val durationMs: Long,
    val bpm: Float,
    /** Time of the first downbeat (beat 1 of bar 1). */
    val firstDownbeatMs: Long,
    /** 0..23: 0-11 = C..B major, 12-23 = C..B minor. */
    val keyIndex: Int,
    val keyConfidence: Float,
    /** End of the intro (0 = no intro). */
    val introEndMs: Long,
    /** Start of the outro (= duration when there is no outro). */
    val outroStartMs: Long,
    val sections: List<Section>,
    /** Mean loudness 0..1 and brightness 0..1. */
    val energy: Float,
    val brightness: Float,
    /** Waveform bins (0..255): overall peak, and low / mid / high band energy. */
    val wavePeak: ByteArray,
    val waveLow: ByteArray,
    val waveMid: ByteArray,
    val waveHigh: ByteArray,
    /** Energy per bar 0..1, for structure-aware visuals. */
    val barEnergy: FloatArray,
) {
    val beatMs: Double get() = 60_000.0 / bpm
    val barMs: Double get() = beatMs * 4
    val isMinor: Boolean get() = keyIndex >= 12
    val camelot: String get() = Camelot.code(keyIndex)
    val keyName: String get() = Camelot.name(keyIndex)

    fun sectionAt(ms: Long): Section? = sections.firstOrNull { ms >= it.startMs && ms < it.endMs } ?: sections.lastOrNull()

    /** Beat position (in beats, may be negative before the first downbeat). */
    fun beatAt(ms: Long): Double = (ms - firstDownbeatMs) / beatMs

    /** Nearest bar boundary at or after [ms]. */
    fun nextBarMs(ms: Long): Long {
        val bars = Math.ceil((ms - firstDownbeatMs) / barMs).coerceAtLeast(0.0)
        return (firstDownbeatMs + bars * barMs).toLong()
    }

    /** Snap to a phrase boundary (multiple of [barsPerPhrase] bars from the first downbeat). */
    fun snapToPhrase(ms: Long, barsPerPhrase: Int = 8, up: Boolean = false): Long {
        val phraseMs = barMs * barsPerPhrase
        val n = (ms - firstDownbeatMs) / phraseMs
        val k = if (up) Math.ceil(n) else Math.floor(n)
        return (firstDownbeatMs + k.coerceAtLeast(0.0) * phraseMs).toLong()
    }

    fun barEnergyAt(ms: Long): Float {
        if (barEnergy.isEmpty()) return energy
        val i = ((ms - firstDownbeatMs) / barMs).toInt().coerceIn(0, barEnergy.lastIndex)
        return barEnergy[i]
    }

    // equals/hashCode by identity of song + version are enough for caching.
    override fun equals(other: Any?): Boolean = other is TrackAnalysis && other.songId == songId && other.durationMs == durationMs && other.bpm == bpm
    override fun hashCode(): Int = songId.hashCode() * 31 + bpm.hashCode()

    fun toJson(): String = JSONObject()
        .put("v", VERSION)
        .put("id", songId)
        .put("dur", durationMs)
        .put("bpm", bpm.toDouble())
        .put("db", firstDownbeatMs)
        .put("key", keyIndex)
        .put("kc", keyConfidence.toDouble())
        .put("intro", introEndMs)
        .put("outro", outroStartMs)
        .put("energy", energy.toDouble())
        .put("bright", brightness.toDouble())
        .put("wp", b64(wavePeak))
        .put("wl", b64(waveLow))
        .put("wm", b64(waveMid))
        .put("wh", b64(waveHigh))
        .put("be", JSONArray(barEnergy.map { (it * 1000).toInt() }))
        .put("sec", JSONArray(sections.map {
            JSONObject().put("t", it.type.name).put("s", it.startMs).put("e", it.endMs).put("en", it.energy.toDouble())
        }))
        .toString()

    companion object {
        const val VERSION = 1

        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

        fun fromJson(text: String): TrackAnalysis? = runCatching {
            val o = JSONObject(text)
            if (o.optInt("v") != VERSION) return null
            val be = o.optJSONArray("be") ?: JSONArray()
            val sec = o.optJSONArray("sec") ?: JSONArray()
            TrackAnalysis(
                songId = o.getLong("id"),
                durationMs = o.getLong("dur"),
                bpm = o.getDouble("bpm").toFloat(),
                firstDownbeatMs = o.getLong("db"),
                keyIndex = o.getInt("key"),
                keyConfidence = o.optDouble("kc", 0.0).toFloat(),
                introEndMs = o.getLong("intro"),
                outroStartMs = o.getLong("outro"),
                sections = List(sec.length()) { i ->
                    val s = sec.getJSONObject(i)
                    Section(
                        runCatching { SectionType.valueOf(s.getString("t")) }.getOrDefault(SectionType.GROOVE),
                        s.getLong("s"), s.getLong("e"), s.optDouble("en", 0.5).toFloat(),
                    )
                },
                energy = o.optDouble("energy", 0.5).toFloat(),
                brightness = o.optDouble("bright", 0.5).toFloat(),
                wavePeak = unb64(o.getString("wp")),
                waveLow = unb64(o.getString("wl")),
                waveMid = unb64(o.getString("wm")),
                waveHigh = unb64(o.getString("wh")),
                barEnergy = FloatArray(be.length()) { be.getInt(it) / 1000f },
            )
        }.getOrNull()
    }
}

/** Musical keys in Camelot notation, as DJs use them for harmonic mixing. */
object Camelot {
    private val noteNames = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    // Camelot number for major keys C..B and minor keys C..B.
    private val majorNum = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val minorNum = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    fun number(keyIndex: Int): Int = if (keyIndex < 12) majorNum[keyIndex] else minorNum[keyIndex - 12]
    fun letter(keyIndex: Int): Char = if (keyIndex < 12) 'B' else 'A'
    fun code(keyIndex: Int): String = "${number(keyIndex)}${letter(keyIndex)}"
    fun name(keyIndex: Int): String = if (keyIndex < 12) "${noteNames[keyIndex]} major" else "${noteNames[keyIndex - 12]} minor"

    /**
     * Harmonic compatibility 0..1: same key 1, ±1 on the wheel or relative major/minor 0.9,
     * energy-boost moves (+2, ±7) 0.6, everything else lower.
     */
    fun compatibility(a: Int, b: Int): Float {
        val na = number(a); val nb = number(b)
        val sameMode = letter(a) == letter(b)
        val d = ((nb - na) % 12 + 12) % 12
        val dist = minOf(d, 12 - d)
        return when {
            sameMode && dist == 0 -> 1f
            !sameMode && dist == 0 -> 0.9f
            sameMode && dist == 1 -> 0.9f
            sameMode && (d == 2 || dist == 7 % 12 || d == 7) -> 0.6f
            !sameMode && dist == 1 -> 0.5f
            else -> (0.4f - dist * 0.05f).coerceAtLeast(0.05f)
        }
    }

    /** Tempo ratio needed to bring [from] to [to], allowing half/double time. */
    fun tempoRatio(fromBpm: Float, toBpm: Float): Float {
        val candidates = listOf(toBpm / fromBpm, toBpm / (fromBpm * 2), (toBpm * 2) / fromBpm)
        return candidates.minByOrNull { abs(it - 1f) } ?: 1f
    }
}
