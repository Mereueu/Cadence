package com.cadence.player.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.cadence.player.audio.AnalysisManager
import com.cadence.player.audio.LiveAudio
import com.cadence.player.audio.TrackAnalysis
import com.cadence.player.data.Song
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hands-on two-deck mixing: play/cue, pitch, sync, jog nudge and search, hot cues, beat loops,
 * a 3-band EQ and filter per channel, channel faders, a crossfader and a beat-synced echo.
 *
 * Deck A is the app's normal player (so the notification, lock screen and queue keep working);
 * deck B is the second player Auto DJ uses for its transitions. While the decks are open the
 * automatic mixing is paused.
 */
@OptIn(UnstableApi::class)
object ManualDj {

    enum class Side { A, B }

    data class Deck(
        val songId: Long? = null,
        val title: String = "",
        val artist: String = "",
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playing: Boolean = false,
        val ready: Boolean = false,
        /** Pitch fader −1..1 (scaled by the pitch range). */
        val pitch: Float = 0f,
        /** Effective speed including jog bends. */
        val speed: Float = 1f,
        val cueMs: Long = 0L,
        val hotCues: List<Long?> = listOf(null, null, null, null),
        val loopStartMs: Long? = null,
        val loopEndMs: Long? = null,
        val loopBeats: Float = 0f,
        /** Knobs 0..1, 0.5 = centre. */
        val eqHigh: Float = 0.5f,
        val eqMid: Float = 0.5f,
        val eqLow: Float = 0.5f,
        val filter: Float = 0.5f,
        val volume: Float = 1f,
        val echo: Boolean = false,
        val synced: Boolean = false,
        val level: Float = 0f,
    )

    data class State(
        val active: Boolean = false,
        val a: Deck = Deck(),
        val b: Deck = Deck(),
        val crossfader: Float = 0.5f,
        val sharpCurve: Boolean = false,
        val pitchRange: Int = 8,
        val echoBeats: Float = 0.5f,
        val note: String? = null,
    ) {
        fun deck(side: Side) = if (side == Side.A) a else b
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loop: Job? = null
    private var prefs: SharedPreferences? = null

    // Mutable engine state mirrored into [state] on every tick.
    private val decks = mutableMapOf(Side.A to Deck(), Side.B to Deck())
    private var crossfader = 0.5f
    private var sharp = false
    private var range = 8
    private var echoBeats = 0.5f
    private var note: String? = null
    private val bend = mutableMapOf(Side.A to 0f, Side.B to 0f)
    private val bendUntil = mutableMapOf(Side.A to 0L, Side.B to 0L)

    private fun player(side: Side): ExoPlayer? = if (side == Side.A) AutoDj.playerA else AutoDj.playerB
    private fun fx(side: Side) = if (side == Side.A) Decks.fxA else Decks.fxB
    private fun analysis(side: Side): TrackAnalysis? = decks[side]?.songId?.let { AnalysisManager.get(it) }

    // ------------------------------------------------------------------ lifecycle

    fun open(context: Context) {
        if (_state.value.active) return
        prefs = context.applicationContext.getSharedPreferences("manual_dj", Context.MODE_PRIVATE)
        AutoDj.suspendForManual()
        val a = AutoDj.playerA
        decks[Side.A] = Deck(volume = 1f).withSong(a?.currentMediaItem?.mediaId?.toLongOrNull(), a)
        decks[Side.B] = decks[Side.B]!!.copy(volume = 1f)
        crossfader = if (AutoDj.playerB?.isPlaying == true) 0.5f else 0f
        note = null
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                runCatching { tick() }
                publish(true)
                delay(16L)
            }
        }
        publish(true)
    }

    /**
     * Leaves the decks. If deck B is the one the crowd is hearing, its song moves onto the main
     * player at the same position so the music carries on without a gap.
     */
    fun close() {
        if (!_state.value.active) return
        loop?.cancel()
        loop = null
        val pa = AutoDj.playerA
        val pb = AutoDj.playerB
        val bAudible = pb != null && pb.isPlaying && (pa?.isPlaying != true || crossfader > 0.5f)
        if (bAudible && pa != null && pb != null) {
            val item = pb.currentMediaItem
            val pos = pb.currentPosition
            val speed = pb.playbackParameters.speed
            if (item != null) {
                val insertAt = if (pa.mediaItemCount == 0) 0 else pa.currentMediaItemIndex + 1
                pa.addMediaItem(insertAt, item)
                pa.seekTo(insertAt, pos + 120)
                pa.playbackParameters = PlaybackParameters(speed)
                pa.play()
            }
        }
        pb?.pause()
        pb?.volume = 0f
        pa?.volume = 1f
        if (pa != null && abs(pa.playbackParameters.speed - 1f) > 0.001f && !bAudible) pa.playbackParameters = PlaybackParameters(1f)
        Decks.fxA.clearEffects()
        Decks.fxB.clearEffects()
        LiveAudio.deckA.gain = 1f
        LiveAudio.deckB.gain = 0f
        AutoDj.resumeAfterManual()
        _state.value = _state.value.copy(active = false)
    }

    // ------------------------------------------------------------------ loading

    fun load(side: Side, song: Song) {
        val p = player(side) ?: return
        clearLoop(side)
        if (side == Side.A) {
            // Keep the queue: put the song right after the current one and jump to it, paused.
            val insertAt = if (p.mediaItemCount == 0) 0 else p.currentMediaItemIndex + 1
            p.addMediaItem(insertAt, song.toMediaItem())
            p.playWhenReady = false
            p.seekTo(insertAt, 0L)
        } else {
            p.setMediaItem(song.toMediaItem())
            p.prepare()
            p.playWhenReady = false
        }
        AnalysisManager.requestUrgent(song.id)
        val cues = loadCues(song.id)
        val start = AnalysisManager.get(song.id)?.firstDownbeatMs ?: 0L
        decks[side] = decks[side]!!.copy(
            songId = song.id, title = song.title, artist = song.artist, durationMs = song.duration,
            positionMs = start, cueMs = start, hotCues = cues, playing = false, synced = false, pitch = 0f,
        )
        p.playbackParameters = PlaybackParameters(1f)
        if (start > 0) p.seekTo(start)
        note = "Loaded “${song.title}” on deck ${side.name}"
    }

    private fun Deck.withSong(id: Long?, p: ExoPlayer?): Deck {
        if (id == null || p == null) return this
        val meta = p.currentMediaItem?.mediaMetadata
        return copy(
            songId = id, title = meta?.title?.toString() ?: "", artist = meta?.artist?.toString() ?: "",
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L, positionMs = p.currentPosition,
            playing = p.isPlaying, hotCues = loadCues(id), cueMs = p.currentPosition,
            pitch = ((p.playbackParameters.speed - 1f) * 100f / range).coerceIn(-1f, 1f),
        )
    }

    // ------------------------------------------------------------------ transport

    fun togglePlay(side: Side) {
        val p = player(side) ?: return
        if (decks[side]?.songId == null) return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(decks[side]!!.cueMs)
            p.play()
        }
    }

    /** CDJ-style cue: while paused, sets the cue point here; while playing, jumps back to it and stops. */
    fun cue(side: Side) {
        val p = player(side) ?: return
        val d = decks[side] ?: return
        if (p.isPlaying) {
            p.pause()
            p.seekTo(d.cueMs)
        } else {
            val at = snapBeat(side, p.currentPosition)
            decks[side] = d.copy(cueMs = at)
            p.seekTo(at)
        }
    }

    fun seek(side: Side, ms: Long) {
        val p = player(side) ?: return
        val d = decks[side] ?: return
        p.seekTo(ms.coerceIn(0L, (d.durationMs - 100).coerceAtLeast(0L)))
    }

    // ------------------------------------------------------------------ tempo

    fun setPitch(side: Side, value: Float) {
        decks[side] = decks[side]!!.copy(pitch = value.coerceIn(-1f, 1f), synced = false)
    }

    fun setPitchRange(pct: Int) {
        range = pct
    }

    /** Matches this deck's tempo to the other deck and lines its beats up with the other deck's. */
    fun sync(side: Side) {
        val other = if (side == Side.A) Side.B else Side.A
        val me = analysis(side)
        val them = analysis(other)
        val po = player(other)
        val pm = player(side)
        if (me == null || them == null || po == null || pm == null) {
            note = "Sync needs both songs analysed — open the song once on Auto DJ or wait a moment"
            return
        }
        val otherSpeed = po.playbackParameters.speed
        val targetBpm = them.bpm * otherSpeed
        val ratio = com.cadence.player.audio.Camelot.tempoRatio(me.bpm, targetBpm)
        val pct = (ratio - 1f) * 100f
        if (abs(pct) > range) {
            note = "Tempo too far apart (${"%.1f".format(pct)}%) for ±$range% — widen the pitch range"
            return
        }
        decks[side] = decks[side]!!.copy(pitch = pct / range, synced = true)
        // Phase: put this deck at the same position within the beat as the other deck.
        if (pm.isPlaying && po.isPlaying) {
            val fo = frac(them.beatAt(po.currentPosition))
            val fm = frac(me.beatAt(pm.currentPosition))
            var diff = fo - fm
            if (diff > 0.5) diff -= 1.0
            if (diff < -0.5) diff += 1.0
            pm.seekTo(pm.currentPosition + (diff * me.beatMs).roundToLong())
        } else if (!pm.isPlaying && po.isPlaying) {
            // Paused: align to the other deck's bar so pressing play lands on the beat.
            val fo = frac(them.beatAt(po.currentPosition) / 4.0)
            val bar = Math.floor(me.beatAt(pm.currentPosition) / 4.0)
            pm.seekTo((me.firstDownbeatMs + (bar + fo) * me.barMs).roundToLong().coerceAtLeast(0L))
        }
        note = null
    }

    /**
     * Jog wheel. While playing, turning bends the tempo for a moment (nudge); while paused it
     * scrubs through the track like searching on a record. [degrees] is the rotation since the last call.
     */
    fun jog(side: Side, degrees: Float) {
        val p = player(side) ?: return
        if (p.isPlaying) {
            bend[side] = (degrees / 40f).coerceIn(-0.12f, 0.12f)
            bendUntil[side] = SystemClock.elapsedRealtime() + 90
        } else {
            // One full turn ≈ 1.8 s, like a record at 33⅓ rpm.
            val ms = (degrees / 360f * 1800f).toLong()
            p.seekTo((p.currentPosition + ms).coerceIn(0L, (decks[side]!!.durationMs - 50).coerceAtLeast(0L)))
        }
    }

    // ------------------------------------------------------------------ hot cues & loops

    fun hotCue(side: Side, index: Int) {
        val p = player(side) ?: return
        val d = decks[side] ?: return
        val existing = d.hotCues.getOrNull(index)
        if (existing == null) {
            val at = snapBeat(side, p.currentPosition)
            val cues = d.hotCues.toMutableList().also { it[index] = at }
            decks[side] = d.copy(hotCues = cues)
            d.songId?.let { saveCues(it, cues) }
        } else {
            p.seekTo(existing)
            if (!p.isPlaying) p.play()
        }
    }

    fun clearHotCue(side: Side, index: Int) {
        val d = decks[side] ?: return
        val cues = d.hotCues.toMutableList().also { it[index] = null }
        decks[side] = d.copy(hotCues = cues)
        d.songId?.let { saveCues(it, cues) }
    }

    /** Starts (or resizes) a loop of [beats] beats from the current beat. */
    fun loop(side: Side, beats: Float) {
        val p = player(side) ?: return
        val d = decks[side] ?: return
        val a = analysis(side)
        val beatMs = a?.beatMs ?: 500.0
        val start = d.loopStartMs ?: snapBeat(side, p.currentPosition, floor = true)
        val end = start + (beats * beatMs).roundToLong()
        decks[side] = d.copy(loopStartMs = start, loopEndMs = end, loopBeats = beats)
    }

    fun halveLoop(side: Side) = decks[side]?.loopBeats?.takeIf { it > 0f }?.let { loop(side, (it / 2f).coerceAtLeast(0.125f)) }
    fun doubleLoop(side: Side) = decks[side]?.loopBeats?.takeIf { it > 0f }?.let { loop(side, (it * 2f).coerceAtMost(32f)) }

    fun clearLoop(side: Side) {
        decks[side] = decks[side]!!.copy(loopStartMs = null, loopEndMs = null, loopBeats = 0f)
    }

    // ------------------------------------------------------------------ mixer

    fun setEq(side: Side, band: Int, value: Float) {
        val d = decks[side]!!
        val v = value.coerceIn(0f, 1f)
        decks[side] = when (band) {
            0 -> d.copy(eqHigh = v)
            1 -> d.copy(eqMid = v)
            2 -> d.copy(eqLow = v)
            else -> d.copy(filter = v)
        }
    }

    fun setVolume(side: Side, value: Float) {
        decks[side] = decks[side]!!.copy(volume = value.coerceIn(0f, 1f))
    }

    fun setCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
    }

    fun setSharpCurve(on: Boolean) {
        sharp = on
    }

    fun toggleEcho(side: Side) {
        decks[side] = decks[side]!!.copy(echo = !decks[side]!!.echo)
    }

    fun setEchoBeats(beats: Float) {
        echoBeats = beats
    }

    fun consumeNote() {
        note = null
        publish(_state.value.active)
    }

    // ------------------------------------------------------------------ engine

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        for (side in Side.entries) {
            val p = player(side) ?: continue
            var d = decks[side] ?: continue

            // Deck A can change song underneath us (end of track, notification skip).
            if (side == Side.A) {
                val id = p.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != d.songId) d = Deck(volume = d.volume, eqHigh = d.eqHigh, eqMid = d.eqMid, eqLow = d.eqLow, filter = d.filter).withSong(id, p)
            }

            // Tempo: pitch fader plus any jog bend.
            if (now > bendUntil[side]!!) bend[side] = 0f
            val speed = (1f + d.pitch * range / 100f) * (1f + bend[side]!!)
            if (abs(p.playbackParameters.speed - speed) > 0.0004f) p.playbackParameters = PlaybackParameters(speed)

            // Loop.
            val ls = d.loopStartMs; val le = d.loopEndMs
            val pos = p.currentPosition
            if (ls != null && le != null && p.isPlaying && pos >= le - 8) p.seekTo(ls + (pos - le).coerceAtLeast(0L))

            // EQ, filter, echo.
            val fx = fx(side)
            fx.eqHighDb = knobDb(d.eqHigh)
            fx.eqMidDb = knobDb(d.eqMid)
            fx.eqLowDb = knobDb(d.eqLow)
            val fv = d.filter
            when {
                fv < 0.47f -> { fx.lowPassHz = (200.0 * 100.0.pow((fv / 0.47f).toDouble())).toFloat(); fx.highPassHz = 0f }
                fv > 0.53f -> { fx.highPassHz = (20.0 * 10.0.pow(((fv - 0.53f) / 0.47f * 2.3).toDouble())).toFloat(); fx.lowPassHz = 20000f }
                else -> { fx.lowPassHz = 20000f; fx.highPassHz = 0f }
            }
            val beatMs = analysis(side)?.beatMs ?: 500.0
            fx.echoDelayMs = (beatMs * echoBeats / speed).toFloat().coerceIn(40f, 1900f)
            fx.echoFeedback = 0.5f
            fx.echoWet = if (d.echo) 0.6f else 0f
            fx.gain = Normalizer.gainFor(d.songId)

            // Channel fader × crossfader curve.
            val xf = if (side == Side.A) curve(1f - crossfader) else curve(crossfader)
            val vol = (d.volume * d.volume) * xf
            p.volume = vol
            if (side == Side.A) LiveAudio.deckA.gain = vol else LiveAudio.deckB.gain = vol

            val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: d.durationMs
            decks[side] = d.copy(
                positionMs = pos, durationMs = dur, playing = p.isPlaying,
                ready = p.playbackState == Player.STATE_READY, speed = speed,
                level = if (p.isPlaying) vol else 0f,
            )
        }
    }

    /** Crossfader gain for one side: constant power, or a near-instant cut for scratch-style mixing. */
    private fun curve(x: Float): Float = if (sharp) {
        if (x >= 0.06f) 1f else x / 0.06f
    } else if (x >= 0.5f) 1f else sin(x * PI).toFloat()

    /** Knob 0..1 → dB: full left ≈ kill (−40 dB), centre 0 dB, full right +6 dB. */
    private fun knobDb(v: Float): Float = when {
        v < 0.48f -> -40f * (1f - v / 0.48f).pow(0.6f)
        v > 0.52f -> 6f * (v - 0.52f) / 0.48f
        else -> 0f
    }

    private fun snapBeat(side: Side, ms: Long, floor: Boolean = false): Long {
        val a = analysis(side) ?: return ms
        val b = a.beatAt(ms)
        val n = if (floor) Math.floor(b) else Math.round(b).toDouble()
        return (a.firstDownbeatMs + n * a.beatMs).roundToLong().coerceAtLeast(0L)
    }

    private fun frac(v: Double): Double = v - Math.floor(v)

    private fun publish(active: Boolean) {
        _state.value = State(
            active = active, a = decks[Side.A]!!, b = decks[Side.B]!!, crossfader = crossfader,
            sharpCurve = sharp, pitchRange = range, echoBeats = echoBeats, note = note,
        )
    }

    // ------------------------------------------------------------------ hot cue storage

    private fun loadCues(songId: Long): List<Long?> = runCatching {
        val arr = JSONArray(prefs?.getString("cues_$songId", null) ?: return listOf(null, null, null, null))
        List(4) { i -> if (i < arr.length() && !arr.isNull(i)) arr.getLong(i) else null }
    }.getOrDefault(listOf(null, null, null, null))

    private fun saveCues(songId: Long, cues: List<Long?>) {
        val arr = JSONArray()
        cues.forEach { arr.put(it ?: JSONObject.NULL) }
        prefs?.edit()?.putString("cues_$songId", arr.toString())?.apply()
    }
}
