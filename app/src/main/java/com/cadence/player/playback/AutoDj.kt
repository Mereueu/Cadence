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
import com.cadence.player.audio.Camelot
import com.cadence.player.audio.LiveAudio
import com.cadence.player.audio.TrackAnalysis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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

/**
 * A simplified two-deck DJ. While deck A (the session's player) plays the current song, deck B
 * preloads the next one, starts it on a phrase boundary, tempo-matched and beat-aligned, mixes
 * with the chosen transition, then hands playback back to deck A at the same position.
 */
@OptIn(UnstableApi::class)
object AutoDj {

    enum class Style(val label: String) {
        AUTO("Auto"),
        BLEND("Blend"),
        BASS_SWAP("Bass swap"),
        FILTER("Filter sweep"),
        ECHO("Echo out"),
        CUT("Cut"),
    }

    enum class Phase { IDLE, PLANNED, LOADING, MIXING, SETTLING, HANDOFF }

    data class Config(
        val enabled: Boolean = false,
        val style: Style = Style.AUTO,
        val maxBars: Int = 16,
        val beatMatch: Boolean = true,
        val maxTempoPct: Int = 8,
    )

    data class Plan(
        val fromId: Long,
        val toId: Long,
        val fromIndex: Int,
        val toIndex: Int,
        val outStartMs: Long,
        val lengthMs: Long,
        val inStartMs: Long,
        val speed: Float,
        val style: Style,
        val bars: Int,
        val beatMatched: Boolean,
        val keyScore: Float?,
        val analysed: Boolean,
    )

    data class State(
        val config: Config = Config(),
        val phase: Phase = Phase.IDLE,
        val plan: Plan? = null,
        val progress: Float = 0f,
        val manual: Boolean = false,
        val deckBPositionMs: Long = 0L,
        val deckBSpeed: Float = 1f,
        val note: String? = null,
    )

    private const val PRELOAD_MS = 2500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var main: ExoPlayer? = null
    private var deck: ExoPlayer? = null
    private var loop: Job? = null

    private var config = Config()
    private var phase = Phase.IDLE
    private var plan: Plan? = null
    private var progress = 0f
    private var manualFader: Float? = null
    private var skippedFor: Long? = null
    private var handoffTarget = 0L
    private var handoffStartedAt = 0L
    private var handoffPlayAt = 0L
    private var lastNudge = 0L
    private var note: String? = null

    fun attach(context: Context, player: ExoPlayer, audioSessionId: Int) {
        val p = context.getSharedPreferences("auto_dj", Context.MODE_PRIVATE)
        prefs = p
        config = Config(
            enabled = p.getBoolean("enabled", false),
            style = runCatching { Style.valueOf(p.getString("style", Style.AUTO.name)!!) }.getOrDefault(Style.AUTO),
            maxBars = p.getInt("max_bars", 16),
            beatMatch = p.getBoolean("beat_match", true),
            maxTempoPct = p.getInt("max_tempo", 8),
        )
        main = player
        deck = PlayerFactory.create(context, deckB = true).also {
            it.audioSessionId = audioSessionId
            it.volume = 0f
        }
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                runCatching { tick() }
                publish()
                delay(if (phase == Phase.IDLE || phase == Phase.PLANNED) 80L else 16L)
            }
        }
    }

    fun release() {
        loop?.cancel()
        loop = null
        runCatching { deck?.release() }
        deck = null
        main = null
        Decks.fxA.reset()
        Decks.fxB.reset()
        phase = Phase.IDLE
        plan = null
        publish()
    }

    // ------------------------------------------------------------------ settings

    fun updateConfig(transform: (Config) -> Config) {
        config = transform(config)
        prefs?.edit()
            ?.putBoolean("enabled", config.enabled)
            ?.putString("style", config.style.name)
            ?.putInt("max_bars", config.maxBars)
            ?.putBoolean("beat_match", config.beatMatch)
            ?.putInt("max_tempo", config.maxTempoPct)
            ?.apply()
        if (phase == Phase.PLANNED || phase == Phase.IDLE) plan = null
        if (!config.enabled && phase == Phase.LOADING) cancelMix()
        publish()
    }

    // ------------------------------------------------------------------ manual controls

    /** Starts a transition at the next bar. */
    fun mixNow() {
        val p = main ?: return
        if (phase != Phase.IDLE && phase != Phase.PLANNED) return
        val curId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val nextIndex = p.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) {
            note = "Nothing queued after this song"
            return
        }
        val nextId = p.getMediaItemAt(nextIndex).mediaId.toLongOrNull() ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: return
        val base = computePlan(curId, nextId, p.currentMediaItemIndex, nextIndex, dur, config)
        val a = AnalysisManager.get(curId)
        val pos = p.currentPosition
        val start = a?.nextBarMs(pos + 1200) ?: (pos + 1500)
        val length = min(base.lengthMs, max(1000L, dur - start - 300))
        plan = base.copy(outStartMs = start, lengthMs = length)
        skippedFor = null
        phase = Phase.PLANNED
        note = null
        publish()
    }

    /** Lengthens the running or upcoming transition by [bars] bars. */
    fun extend(bars: Int = 8) {
        val p = main ?: return
        val pl = plan ?: return
        if (phase != Phase.PLANNED && phase != Phase.LOADING && phase != Phase.MIXING) return
        val a = AnalysisManager.get(pl.fromId)
        val add = ((a?.barMs ?: 2000.0) * bars).toLong()
        val dur = p.duration
        if (pl.outStartMs + pl.lengthMs + add > dur - 300) {
            note = "Not enough of this song left to extend"
        } else {
            plan = pl.copy(lengthMs = pl.lengthMs + add, bars = pl.bars + bars)
            note = "Transition extended by $bars bars"
        }
        publish()
    }

    /** Jumps straight to the next song (during a mix), or skips the planned mix. */
    fun skipTransition() {
        val p = main ?: return
        when (phase) {
            Phase.MIXING, Phase.SETTLING -> {
                deck?.playbackParameters = PlaybackParameters(1f)
                beginHandoff()
            }
            Phase.PLANNED, Phase.LOADING -> {
                skippedFor = p.currentMediaItem?.mediaId?.toLongOrNull()
                cancelMix()
                note = "Mix skipped for this song"
            }
            else -> Unit
        }
        publish()
    }

    /** Manual crossfader 0..1 while mixing; null hands control back to the automation. */
    fun setFader(value: Float?) {
        manualFader = value?.coerceIn(0f, 1f)
        publish()
    }

    fun consumeNote() {
        note = null
        publish()
    }

    // ------------------------------------------------------------------ engine

    private fun tick() {
        val p = main ?: return
        val d = deck ?: return
        when (phase) {
            Phase.IDLE, Phase.PLANNED -> planAndPreload(p, d)
            Phase.LOADING -> {
                val pl = plan ?: return cancelMix()
                if (p.currentMediaItemIndex != pl.fromIndex) return cancelMix()
                val pos = p.currentPosition
                if (pos >= pl.outStartMs && d.playbackState == Player.STATE_READY) {
                    if (p.isPlaying) {
                        d.play()
                        phase = Phase.MIXING
                    }
                } else if (pos >= pl.outStartMs + 2500) {
                    // The next song didn't load in time; fall back to a normal track change.
                    cancelMix()
                }
            }
            Phase.MIXING -> mixing(p, d)
            Phase.SETTLING -> settling(p, d)
            Phase.HANDOFF -> handoff(p, d)
        }
    }

    private fun planAndPreload(p: ExoPlayer, d: ExoPlayer) {
        if (!config.enabled) {
            plan = null
            phase = Phase.IDLE
            return
        }
        val curId = p.currentMediaItem?.mediaId?.toLongOrNull()
        val nextIndex = p.nextMediaItemIndex
        if (curId == null || nextIndex == C.INDEX_UNSET || p.repeatMode == Player.REPEAT_MODE_ONE) {
            plan = null
            phase = Phase.IDLE
            return
        }
        val nextId = p.getMediaItemAt(nextIndex).mediaId.toLongOrNull()
        val dur = p.duration
        if (nextId == null || dur == C.TIME_UNSET || dur < 30_000 || skippedFor == curId) {
            plan = null
            phase = Phase.IDLE
            return
        }
        AnalysisManager.requestUrgent(curId, nextId)
        val existing = plan
        val analysedNow = AnalysisManager.get(curId) != null && AnalysisManager.get(nextId) != null
        if (existing == null || existing.fromId != curId || existing.toId != nextId ||
            existing.fromIndex != p.currentMediaItemIndex || (!existing.analysed && analysedNow)
        ) {
            plan = computePlan(curId, nextId, p.currentMediaItemIndex, nextIndex, dur, config)
        }
        phase = Phase.PLANNED
        val pl = plan ?: return
        val pos = p.currentPosition
        if (p.isPlaying && pos >= pl.outStartMs - PRELOAD_MS && pos < pl.outStartMs + pl.lengthMs) {
            d.setMediaItem(p.getMediaItemAt(pl.toIndex))
            d.volume = 0f
            d.playbackParameters = PlaybackParameters(pl.speed)
            d.prepare()
            d.seekTo(pl.inStartMs)
            d.playWhenReady = false
            Decks.fxB.reset()
            manualFader = null
            applyMix(0f, pl, p, d)
            d.volume = 0f
            phase = Phase.LOADING
        }
    }

    private fun mixing(p: ExoPlayer, d: ExoPlayer) {
        val pl = plan ?: return cancelMix()
        if (p.currentMediaItemIndex == pl.toIndex) {
            // Deck A already moved on (song ended or user skipped): hand over right away.
            return beginHandoff()
        }
        if (p.currentMediaItemIndex != pl.fromIndex) return cancelMix()
        if (!p.isPlaying) {
            if (d.isPlaying) d.pause()
            return
        } else if (!d.isPlaying && d.playbackState == Player.STATE_READY) {
            d.play()
        }
        val pos = p.currentPosition
        val auto = ((pos - pl.outStartMs).toFloat() / pl.lengthMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        progress = manualFader ?: auto
        applyMix(progress, pl, p, d)
        if (pl.beatMatched) nudge(p, d, pl)
        val done = if (manualFader != null) progress >= 0.995f else auto >= 1f
        if (done || pos >= p.duration - 250) {
            phase = Phase.SETTLING
        }
    }

    private fun settling(p: ExoPlayer, d: ExoPlayer) {
        // Fade out whatever is left of deck A (e.g. an echo tail), then bring deck B back to normal speed.
        p.volume = max(0f, p.volume - 0.05f)
        LiveAudio.deckA.gain = p.volume
        d.volume = 1f
        LiveAudio.deckB.gain = 1f
        val s = d.playbackParameters.speed
        if (abs(s - 1f) > 0.002f) {
            val step = min(0.0015f, abs(1f - s))
            d.playbackParameters = PlaybackParameters(s + if (s < 1f) step else -step)
        } else if (p.volume <= 0f) {
            d.playbackParameters = PlaybackParameters(1f)
            beginHandoff()
        }
    }

    private fun beginHandoff() {
        val p = main ?: return
        val d = deck ?: return
        val pl = plan ?: return cancelMix()
        Decks.fxA.reset()
        p.volume = 0f
        LiveAudio.deckA.gain = 0f
        d.volume = 1f
        handoffTarget = d.currentPosition + 350
        p.playWhenReady = false
        p.seekTo(pl.toIndex, handoffTarget)
        handoffStartedAt = SystemClock.elapsedRealtime()
        handoffPlayAt = 0L
        phase = Phase.HANDOFF
    }

    private fun handoff(p: ExoPlayer, d: ExoPlayer) {
        val now = SystemClock.elapsedRealtime()
        if (handoffPlayAt == 0L) {
            val ready = p.playbackState == Player.STATE_READY
            val deckThere = d.currentPosition >= handoffTarget - 15 || !d.isPlaying
            if ((ready && deckThere) || now - handoffStartedAt > 5000) {
                p.play()
                handoffPlayAt = now
            }
            return
        }
        val t = ((now - handoffPlayAt) / 140f).coerceIn(0f, 1f)
        p.volume = t
        d.volume = 1f - t
        LiveAudio.deckA.gain = t
        LiveAudio.deckB.gain = 1f - t
        if (t >= 1f) finish()
    }

    private fun finish() {
        val p = main
        val d = deck
        d?.stop()
        d?.clearMediaItems()
        d?.volume = 0f
        d?.playbackParameters = PlaybackParameters(1f)
        p?.volume = 1f
        Decks.fxA.reset()
        Decks.fxB.reset()
        LiveAudio.deckA.gain = 1f
        LiveAudio.deckB.gain = 0f
        manualFader = null
        plan = null
        progress = 0f
        phase = Phase.IDLE
    }

    private fun cancelMix() {
        finish()
    }

    /** Small tempo corrections that keep deck B's beats on top of deck A's. */
    private fun nudge(p: ExoPlayer, d: ExoPlayer, pl: Plan) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNudge < 250) return
        lastNudge = now
        val a = AnalysisManager.get(pl.fromId) ?: return
        val b = AnalysisManager.get(pl.toId) ?: return
        val direct = abs(b.bpm * pl.speed - a.bpm) < 1.5f
        if (!direct) return
        val fa = frac(a.beatAt(p.currentPosition))
        val fb = frac(b.beatAt(d.currentPosition))
        var err = fb - fa
        if (err > 0.5) err -= 1.0
        if (err < -0.5) err += 1.0
        val target = if (abs(err) < 0.012) pl.speed else (pl.speed * (1 - (err * 0.2).coerceIn(-0.03, 0.03))).toFloat()
        if (abs(d.playbackParameters.speed - target) > 0.0005f) d.playbackParameters = PlaybackParameters(target)
    }

    private fun frac(v: Double): Double = v - Math.floor(v)

    // ------------------------------------------------------------------ transitions

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun applyMix(prog: Float, pl: Plan, p: ExoPlayer, d: ExoPlayer) {
        val fa = Decks.fxA
        val fb = Decks.fxB
        var va = cos(prog * PI / 2).toFloat()
        var vb = sin(prog * PI / 2).toFloat()
        when (pl.style) {
            Style.AUTO, Style.BLEND -> Unit
            Style.BASS_SWAP -> {
                // Bring B in without bass, swap the bass at the halfway point, then fade A.
                vb = if (prog < 0.5f) sin(prog * PI).toFloat() else 1f
                va = if (prog < 0.5f) 1f else cos((prog - 0.5f) * PI).toFloat()
                fb.bassKill = 1f - smooth(0.42f, 0.55f, prog)
                fa.bassKill = smooth(0.45f, 0.58f, prog)
            }
            Style.FILTER -> {
                fa.highPassHz = if (prog < 0.05f) 0f else (20.0 * Math.pow(120.0, prog.toDouble())).toFloat()
                fb.lowPassHz = if (prog > 0.97f) 20000f else (220.0 * Math.pow(90.0, prog.toDouble())).toFloat()
            }
            Style.ECHO -> {
                val a = AnalysisManager.get(pl.fromId)
                fa.echoDelayMs = ((a?.beatMs ?: 500.0) * 0.75).toFloat()
                fa.echoFeedback = 0.55f
                fa.echoWet = if (prog < 0.45f) 0f else 0.7f
                fa.dryGain = 1f - smooth(0.5f, 0.62f, prog)
                va = 1f
                vb = smooth(0.35f, 0.75f, prog)
            }
            Style.CUT -> {
                va = 1f - prog
                vb = prog
            }
        }
        p.volume = va
        d.volume = vb
        LiveAudio.deckA.gain = va
        LiveAudio.deckB.gain = vb
    }

    private fun autoStyle(a: TrackAnalysis, b: TrackAnalysis, matched: Boolean, keyScore: Float, introBars: Int, outroBars: Int): Style = when {
        !matched -> if (introBars >= 4) Style.BLEND else Style.ECHO
        keyScore < 0.5f -> Style.FILTER
        introBars >= 8 && outroBars >= 8 -> Style.BASS_SWAP
        else -> Style.BLEND
    }

    private fun computePlan(curId: Long, nextId: Long, fromIndex: Int, toIndex: Int, durA: Long, cfg: Config): Plan {
        val a = AnalysisManager.get(curId)
        val b = AnalysisManager.get(nextId)
        if (a == null || b == null) {
            val len = 6000L
            return Plan(
                fromId = curId, toId = nextId, fromIndex = fromIndex, toIndex = toIndex,
                outStartMs = max(0L, durA - len - 400), lengthMs = len, inStartMs = 0L, speed = 1f,
                style = if (cfg.style == Style.AUTO) Style.BLEND else cfg.style, bars = 0,
                beatMatched = false, keyScore = null, analysed = false,
            )
        }
        val barA = a.barMs
        val ratio = Camelot.tempoRatio(b.bpm, a.bpm)
        val matched = cfg.beatMatch && abs(ratio - 1f) <= cfg.maxTempoPct / 100f
        val speed = if (matched) ratio else 1f
        val keyScore = Camelot.compatibility(a.keyIndex, b.keyIndex)
        val outroBarsA = ((durA - a.outroStartMs) / barA).toInt()
        val introBarsB = if (b.introEndMs > 0) ((b.introEndMs - b.firstDownbeatMs) / b.barMs).roundToInt() else 0
        var bars = cfg.maxBars
        if (outroBarsA >= 4) bars = min(bars, outroBarsA)
        if (introBarsB >= 4) bars = min(bars, introBarsB)
        if (!matched) bars = min(bars, 8)
        bars = listOf(32, 16, 8, 4).firstOrNull { it <= max(bars, 4) } ?: 4
        val style = if (cfg.style == Style.AUTO) autoStyle(a, b, matched, keyScore, introBarsB, outroBarsA) else cfg.style
        val lengthMs = if (style == Style.CUT) a.beatMs.toLong() else (bars * barA).toLong()

        // Out point: the start of A's outro on a phrase boundary, so the mix fills the outro.
        val latest = durA - lengthMs - 300
        var outStart = if (a.outroStartMs < durA - barA * 4) a.snapToPhrase(a.outroStartMs, 8) else a.snapToPhrase(latest, 4)
        if (outStart > latest) {
            outStart = a.snapToPhrase(latest, 4)
            if (outStart > latest) outStart = (outStart - barA * 4).toLong()
        }
        if (outStart < durA / 2) outStart = max(outStart, (a.firstDownbeatMs + Math.floor((latest - a.firstDownbeatMs) / barA) * barA).toLong())
        outStart = outStart.coerceIn(0L, max(0L, latest))

        // In point: start B so that its intro ends exactly when the mix ends (its drop lands after A is gone).
        val wantIn = if (b.introEndMs > 0) b.introEndMs - (bars * b.barMs).toLong() else b.firstDownbeatMs
        val barsFromFirst = Math.round((max(wantIn, b.firstDownbeatMs) - b.firstDownbeatMs) / b.barMs)
        val inStart = (b.firstDownbeatMs + barsFromFirst * b.barMs).toLong()

        return Plan(
            fromId = curId, toId = nextId, fromIndex = fromIndex, toIndex = toIndex,
            outStartMs = outStart, lengthMs = lengthMs, inStartMs = inStart, speed = speed,
            style = style, bars = if (style == Style.CUT) 0 else bars,
            beatMatched = matched, keyScore = keyScore, analysed = true,
        )
    }

    private fun publish() {
        val d = deck
        _state.value = State(
            config = config,
            phase = phase,
            plan = plan,
            progress = progress,
            manual = manualFader != null,
            deckBPositionMs = if (phase == Phase.MIXING || phase == Phase.SETTLING || phase == Phase.HANDOFF) (d?.currentPosition ?: 0L) else 0L,
            deckBSpeed = d?.playbackParameters?.speed ?: 1f,
            note = note,
        )
    }
}
