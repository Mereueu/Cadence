package com.cadence.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One frame of live audio features, refreshed by the visualizer every display frame. */
class AudioFrame {
    /** 32 log-spaced spectrum bands, roughly 0..1. */
    val bands = FloatArray(LiveAudio.BANDS)
    var level = 0f
    var bass = 0f
    var mid = 0f
    var treble = 0f
    /** Spectral brightness 0..1. */
    var brightness = 0f
    /** True on the frame a kick/onset was detected. */
    var beat = false
    /** Slowly averaged loudness, used to tell loud sections from quiet ones. */
    var longEnergy = 0f
    var active = false
}

/**
 * Taps the PCM that the players actually output (no microphone needed).
 * Each deck feeds its own ring buffer; the visualizer mixes them by deck volume.
 */
@OptIn(UnstableApi::class)
object LiveAudio {
    const val BANDS = 32
    private const val FFT_SIZE = 1024
    private const val RING = 1 shl 15

    /** Set to false to stop analysing (saves battery); the tap then just drops buffers. */
    @Volatile var enabled = false

    class Deck internal constructor() : TeeAudioProcessor.AudioBufferSink {
        internal val ring = FloatArray(RING)
        @Volatile internal var writePos = 0L
        @Volatile internal var sampleRate = 44100
        @Volatile internal var channels = 2
        @Volatile internal var encoding = C.ENCODING_PCM_16BIT
        @Volatile var gain = 1f
        @Volatile internal var lastWriteNanos = 0L

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            sampleRate = sampleRateHz
            channels = channelCount.coerceAtLeast(1)
            this.encoding = encoding
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            if (!enabled) return
            val buf = buffer.duplicate().order(ByteOrder.nativeOrder())
            val ch = channels
            var pos = writePos
            when (encoding) {
                C.ENCODING_PCM_FLOAT -> {
                    val frames = buf.remaining() / (4 * ch)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += buf.float
                        ring[(pos and (RING - 1).toLong()).toInt()] = sum / ch
                        pos++
                    }
                }
                else -> {
                    val frames = buf.remaining() / (2 * ch)
                    for (f in 0 until frames) {
                        var sum = 0
                        for (c in 0 until ch) sum += buf.short.toInt()
                        ring[(pos and (RING - 1).toLong()).toInt()] = sum / (ch * 32768f)
                        pos++
                    }
                }
            }
            writePos = pos
            lastWriteNanos = System.nanoTime()
        }
    }

    val deckA = Deck()
    val deckB = Deck()

    /** How far the tap runs ahead of what is heard (the audio track's buffer). */
    @Volatile var latencyMs = 130

    private val fft = Fft(FFT_SIZE)
    private val scratch = FloatArray(FFT_SIZE)
    private val mags = FloatArray(FFT_SIZE / 2)
    private val magsB = FloatArray(FFT_SIZE / 2)
    private val smooth = FloatArray(BANDS)
    private var prevBass = 0f
    private var fluxAvg = 0f
    private var lastBeatNanos = 0L
    private var long = 0f

    private fun readWindow(deck: Deck, out: FloatArray): Boolean {
        val now = System.nanoTime()
        if (now - deck.lastWriteNanos > 1_500_000_000L) return false
        val latency = deck.sampleRate * latencyMs / 1000
        val end = deck.writePos - latency
        if (end < FFT_SIZE) return false
        val start = end - FFT_SIZE
        for (i in 0 until FFT_SIZE) {
            out[i] = deck.ring[((start + i) and (RING - 1).toLong()).toInt()]
        }
        return true
    }

    private val tdA = FloatArray(FFT_SIZE)
    private val tdB = FloatArray(FFT_SIZE)
    private val tdBytes = ByteArray(FFT_SIZE)

    /**
     * The last 1024 samples being heard (both decks mixed), as unsigned bytes centred on 128 and
     * base64-encoded: the format MilkDrop visuals expect. Returns null when nothing is playing.
     */
    @Synchronized
    fun timeDomainBase64(): String? {
        if (!enabled) return null
        val aOk = readWindow(deckA, tdA)
        val bOk = readWindow(deckB, tdB)
        if (!aOk && !bOk) return null
        val ga = deckA.gain; val gb = deckB.gain
        for (i in 0 until FFT_SIZE) {
            val v = (if (aOk) tdA[i] * ga else 0f) + (if (bOk) tdB[i] * gb else 0f)
            tdBytes[i] = ((v.coerceIn(-1f, 1f) * 127f) + 128f).toInt().coerceIn(0, 255).toByte()
        }
        return android.util.Base64.encodeToString(tdBytes, android.util.Base64.NO_WRAP)
    }

    /** Computes features for "now" into [frame]. Call from the UI thread once per frame. */
    fun analyze(frame: AudioFrame) {
        if (!enabled) {
            frame.active = false
            decay(frame)
            return
        }
        val aOk = readWindow(deckA, scratch).also { if (it) fft.magnitudes(scratch, 0, mags) }
        val bOk = readWindow(deckB, scratch).also { if (it) fft.magnitudes(scratch, 0, magsB) }
        if (!aOk && !bOk) {
            frame.active = false
            decay(frame)
            return
        }
        frame.active = true
        val ga = if (aOk) deckA.gain else 0f
        val gb = if (bOk) deckB.gain else 0f
        val n = FFT_SIZE / 2
        for (i in 0 until n) {
            mags[i] = (if (aOk) mags[i] * ga else 0f) + (if (bOk) magsB[i] * gb else 0f)
        }
        val sr = if (aOk) deckA.sampleRate else deckB.sampleRate
        val binHz = sr.toFloat() / FFT_SIZE

        // Log-spaced bands from 30 Hz to 16 kHz.
        var total = 0f
        var centroidNum = 0f
        val minHz = 30.0
        val maxHz = 16000.0
        for (b in 0 until BANDS) {
            val lo = minHz * Math.pow(maxHz / minHz, b.toDouble() / BANDS)
            val hi = minHz * Math.pow(maxHz / minHz, (b + 1).toDouble() / BANDS)
            val i0 = max(1, (lo / binHz).toInt())
            val i1 = min(n - 1, max(i0 + 1, (hi / binHz).toInt()))
            var sum = 0f
            for (i in i0 until i1) sum += mags[i]
            val avg = sum / (i1 - i0)
            val db = (ln(1f + avg * 8f) / ln(1f + 40f)).coerceIn(0f, 1.5f)
            // Fast attack, slower release.
            smooth[b] = if (db > smooth[b]) smooth[b] + (db - smooth[b]) * 0.6f else smooth[b] + (db - smooth[b]) * 0.18f
            frame.bands[b] = smooth[b].coerceIn(0f, 1f)
            total += avg
            centroidNum += avg * b
        }
        frame.bass = (frame.bands[0] + frame.bands[1] + frame.bands[2] + frame.bands[3] + frame.bands[4] + frame.bands[5]) / 6f
        var m = 0f
        for (b in 8 until 20) m += frame.bands[b]
        frame.mid = m / 12f
        var t = 0f
        for (b in 22 until BANDS) t += frame.bands[b]
        frame.treble = t / (BANDS - 22)
        frame.brightness = if (total > 0f) (centroidNum / total / BANDS).coerceIn(0f, 1f) else 0f

        var rms = 0f
        for (i in 0 until FFT_SIZE) rms += scratch[i] * scratch[i]
        val level = (sqrt(rms / FFT_SIZE) * 3.2f).coerceIn(0f, 1f)
        frame.level = frame.level + (level - frame.level) * 0.35f
        long += (frame.level - long) * 0.004f
        frame.longEnergy = long

        // Onset: rise in bass energy above its running average.
        val flux = max(0f, frame.bass - prevBass)
        prevBass = frame.bass
        fluxAvg += (flux - fluxAvg) * 0.08f
        val now = System.nanoTime()
        frame.beat = flux > fluxAvg * 2.2f + 0.02f && now - lastBeatNanos > 230_000_000L
        if (frame.beat) lastBeatNanos = now
    }

    private fun decay(frame: AudioFrame) {
        for (b in 0 until BANDS) {
            smooth[b] *= 0.9f
            frame.bands[b] = smooth[b]
        }
        frame.level *= 0.9f
        frame.bass *= 0.9f
        frame.mid *= 0.9f
        frame.treble *= 0.9f
        frame.beat = false
    }
}
