package com.cadence.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-deck DJ effects applied to 16-bit PCM before output:
 * high-pass and low-pass filter sweeps, a bass kill (low-shelf cut) and a tempo-synced echo.
 * Parameters are written from the main thread and picked up on the next buffer.
 * All effects are bypassed (zero cost) when neutral.
 */
@OptIn(UnstableApi::class)
class DjFxProcessor : BaseAudioProcessor() {

    /** High-pass cutoff in Hz; ≤ 20 means off. */
    @Volatile var highPassHz = 0f
    /** Low-pass cutoff in Hz; ≥ 19000 means off. */
    @Volatile var lowPassHz = 20000f
    /** 0 = full bass, 1 = bass removed (−24 dB shelf at 180 Hz). */
    @Volatile var bassKill = 0f
    /** Echo: wet amount 0..1, delay in ms, feedback 0..0.9. */
    @Volatile var echoWet = 0f
    @Volatile var echoDelayMs = 375f
    @Volatile var echoFeedback = 0.55f
    /** Dry signal gain (lets an echo tail ring out while the dry track fades). */
    @Volatile var dryGain = 1f
    /** Overall gain, used for loudness normalisation (≤ 1). */
    @Volatile var gain = 1f
    /** Three-band DJ EQ in dB (0 = flat; about −40 is a full kill). */
    @Volatile var eqLowDb = 0f
    @Volatile var eqMidDb = 0f
    @Volatile var eqHighDb = 0f
    /**
     * Quick stem isolation without AI separation, using stereo placement and frequency:
     * 0 = full mix, 1 = instrumental (centre-panned vocals reduced, bass kept),
     * 2 = vocal focus (centre channel, voice band only). Approximate by nature.
     */
    @Volatile var stemMode = 0
    /** Channel fader / crossfader level applied inside the chain (1 = unity). */
    @Volatile var level = 1f

    fun clearEffects() {
        highPassHz = 0f
        lowPassHz = 20000f
        bassKill = 0f
        echoWet = 0f
        dryGain = 1f
        eqLowDb = 0f
        eqMidDb = 0f
        eqHighDb = 0f
        level = 1f
        stemMode = 0
    }

    private val isNeutral: Boolean
        get() = highPassHz <= 20f && lowPassHz >= 19000f && bassKill <= 0.001f && echoWet <= 0.001f && dryGain >= 0.999f && gain >= 0.999f &&
            eqOff(eqLowDb) && eqOff(eqMidDb) && eqOff(eqHighDb) && level >= 0.999f && stemMode == 0

    private fun eqOff(db: Float) = db > -0.05f && db < 0.05f

    private var sampleRate = 44100
    private var channels = 2

    private class Biquad {
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        val x1 = FloatArray(8); val x2 = FloatArray(8); val y1 = FloatArray(8); val y2 = FloatArray(8)
        fun process(c: Int, x: Float): Float {
            val y = b0 * x + b1 * x1[c] + b2 * x2[c] - a1 * y1[c] - a2 * y2[c]
            x2[c] = x1[c]; x1[c] = x
            y2[c] = y1[c]; y1[c] = y
            return y
        }
        fun clear() {
            x1.fill(0f); x2.fill(0f); y1.fill(0f); y2.fill(0f)
        }
        fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
            b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
            a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
        }
    }

    private val hp = Biquad()
    private val lp = Biquad()
    private val shelf = Biquad()
    private val eqLow = Biquad()
    private val eqMid = Biquad()
    private val eqHigh = Biquad()
    private val stemBass = Biquad()
    private val stemHp = Biquad()
    private val stemLp = Biquad()
    private var stemRate = 0
    private val frame = FloatArray(8)
    private var lastLowDb = Float.NaN
    private var lastMidDb = Float.NaN
    private var lastHighDb = Float.NaN
    private var echo = FloatArray(0)
    private var echoPos = 0
    private var lastHp = -1f
    private var lastLp = -1f
    private var lastShelf = -1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // Unusual output formats simply bypass the effects instead of failing playback.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount.coerceIn(1, 8)
        echo = FloatArray(sampleRate * channels * 2)
        echoPos = 0
        return inputAudioFormat
    }

    private fun designHighPass(f: Float) {
        val w0 = 2 * PI * f / sampleRate
        val alpha = sin(w0) / (2 * 0.707)
        val c = cos(w0)
        hp.set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    private fun designLowPass(f: Float) {
        val w0 = 2 * PI * f / sampleRate
        val alpha = sin(w0) / (2 * 0.707)
        val c = cos(w0)
        lp.set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    private fun designShelf(amount: Float) {
        // Low shelf at 180 Hz, gain from 0 dB down to −24 dB.
        val gainDb = -24.0 * amount
        val a = Math.pow(10.0, gainDb / 40)
        val w0 = 2 * PI * 180.0 / sampleRate
        val c = cos(w0)
        val s = sin(w0)
        val alpha = s / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        shelf.set(
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa,
        )
    }

    private fun designLowShelf(q: Biquad, freq: Double, db: Float) {
        val a = Math.pow(10.0, db / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val c = cos(w0); val sn = sin(w0)
        val alpha = sn / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        q.set(
            a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa,
        )
    }

    private fun designHighShelf(q: Biquad, freq: Double, db: Float) {
        val a = Math.pow(10.0, db / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val c = cos(w0); val sn = sin(w0)
        val alpha = sn / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        q.set(
            a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa,
        )
    }

    private fun designPeak(q: Biquad, freq: Double, db: Float, qf: Double) {
        val a = Math.pow(10.0, db / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2 * qf)
        q.set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    private fun designStemFilters() {
        fun lowPass(q: Biquad, freq: Double) {
            val w0 = 2 * PI * freq / sampleRate
            val alpha = sin(w0) / (2 * 0.707)
            val c = cos(w0)
            q.set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }
        fun highPass(q: Biquad, freq: Double) {
            val w0 = 2 * PI * freq / sampleRate
            val alpha = sin(w0) / (2 * 0.707)
            val c = cos(w0)
            q.set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }
        lowPass(stemBass, 160.0)
        highPass(stemHp, 180.0)
        lowPass(stemLp, 6000.0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val neutral = isNeutral
        if (neutral && echoTailSilent()) {
            lastLevel = level
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (!neutral) silentBuffers = 0
        val hpOn = highPassHz > 20f
        val lpOn = lowPassHz < 19000f
        val shelfOn = bassKill > 0.001f
        if (hpOn && highPassHz != lastHp) { designHighPass(highPassHz.coerceAtMost(sampleRate * 0.45f)); lastHp = highPassHz }
        if (lpOn && lowPassHz != lastLp) { designLowPass(lowPassHz.coerceAtMost(sampleRate * 0.45f)); lastLp = lowPassHz }
        if (shelfOn && bassKill != lastShelf) { designShelf(bassKill.coerceIn(0f, 1f)); lastShelf = bassKill }
        val lowDb = eqLowDb; val midDb = eqMidDb; val highDb = eqHighDb
        val lowOn = !eqOff(lowDb); val midOn = !eqOff(midDb); val highOn = !eqOff(highDb)
        if (lowOn && lowDb != lastLowDb) { designLowShelf(eqLow, 250.0, lowDb); lastLowDb = lowDb }
        if (midOn && midDb != lastMidDb) { designPeak(eqMid, 1000.0, midDb, 0.7); lastMidDb = midDb }
        if (highOn && highDb != lastHighDb) { designHighShelf(eqHigh, 4000.0, highDb); lastHighDb = highDb }
        // Fader level ramps smoothly across the buffer so moves never click.
        val lvlTarget = level
        val lvlStart = lastLevel
        lastLevel = lvlTarget
        val wet = echoWet
        val fb = echoFeedback.coerceIn(0f, 0.9f)
        val dry = dryGain
        val g = gain
        val delaySamples = ((echoDelayMs / 1000f) * sampleRate).toInt().coerceIn(1, sampleRate * 2 - 1) * channels
        val echoLen = echo.size

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        val frames = remaining / (2 * channels)
        val stem = if (channels == 2) stemMode else 0
        if (stem != 0 && stemRate != sampleRate) {
            designStemFilters()
            stemRate = sampleRate
        }
        val lvlStep = if (frames > 0) (lvlTarget - lvlStart) / frames else 0f
        var lvl = lvlStart
        for (f in 0 until frames) {
            lvl += lvlStep
            for (c in 0 until channels) {
                var x = input.short / 32768f
                if (hpOn) x = hp.process(c, x)
                if (lpOn) x = lp.process(c, x)
                if (shelfOn) x = shelf.process(c, x)
                if (lowOn) x = eqLow.process(c, x)
                if (midOn) x = eqMid.process(c, x)
                if (highOn) x = eqHigh.process(c, x)
                frame[c] = x
            }
            if (stem != 0) {
                val mid = (frame[0] + frame[1]) * 0.5f
                val side = (frame[0] - frame[1]) * 0.5f
                if (stem == 1) {
                    // Instrumental: drop the centre (where vocals sit) but keep the bass, which is centred too.
                    val bass = stemBass.process(0, mid)
                    frame[0] = side * 1.4f + bass
                    frame[1] = -side * 1.4f + bass
                } else {
                    // Vocal focus: centre channel, band-limited to the voice range.
                    val v = stemLp.process(0, stemHp.process(0, mid - side * 0.5f)) * 1.3f
                    frame[0] = v
                    frame[1] = v
                }
            }
            for (c in 0 until channels) {
                val x = frame[c]
                var y = x * dry
                if (echoLen > 0) {
                    var readPos = echoPos - delaySamples
                    if (readPos < 0) readPos += echoLen
                    val delayed = echo[readPos]
                    echo[echoPos] = x * wet + delayed * fb
                    y += delayed
                    echoPos++
                    if (echoPos >= echoLen) echoPos = 0
                }
                val s = (y * g * lvl * 32768f).coerceIn(-32768f, 32767f)
                out.putShort(s.toInt().toShort())
            }
        }
        out.flip()
    }

    private var silentBuffers = 0
    private var lastLevel = 1f

    private fun echoTailSilent(): Boolean {
        // After the echo is switched off, keep processing ~2 s so the tail decays naturally.
        if (echoWet > 0.001f) {
            silentBuffers = 0
            return false
        }
        silentBuffers++
        if (silentBuffers > 120) {
            if (silentBuffers == 121) {
                echo.fill(0f)
                hp.clear(); lp.clear(); shelf.clear(); eqLow.clear(); eqMid.clear(); eqHigh.clear()
                stemBass.clear(); stemHp.clear(); stemLp.clear()
            }
            return true
        }
        return false
    }

    override fun onFlush() {
        echo.fill(0f)
        echoPos = 0
        hp.clear(); lp.clear(); shelf.clear(); eqLow.clear(); eqMid.clear(); eqHigh.clear()
    }

    override fun onReset() {
        echo = FloatArray(0)
        echoPos = 0
    }
}
