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

    fun clearEffects() {
        highPassHz = 0f
        lowPassHz = 20000f
        bassKill = 0f
        echoWet = 0f
        dryGain = 1f
    }

    private val isNeutral: Boolean
        get() = highPassHz <= 20f && lowPassHz >= 19000f && bassKill <= 0.001f && echoWet <= 0.001f && dryGain >= 0.999f

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

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val neutral = isNeutral
        if (neutral && echoTailSilent()) {
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
        val wet = echoWet
        val fb = echoFeedback.coerceIn(0f, 0.9f)
        val dry = dryGain
        val delaySamples = ((echoDelayMs / 1000f) * sampleRate).toInt().coerceIn(1, sampleRate * 2 - 1) * channels
        val echoLen = echo.size

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        val frames = remaining / (2 * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                var x = input.short / 32768f
                if (hpOn) x = hp.process(c, x)
                if (lpOn) x = lp.process(c, x)
                if (shelfOn) x = shelf.process(c, x)
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
                val s = (y * 32768f).coerceIn(-32768f, 32767f)
                out.putShort(s.toInt().toShort())
            }
        }
        out.flip()
    }

    private var silentBuffers = 0

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
                hp.clear(); lp.clear(); shelf.clear()
            }
            return true
        }
        return false
    }

    override fun onFlush() {
        echo.fill(0f)
        echoPos = 0
        hp.clear(); lp.clear(); shelf.clear()
    }

    override fun onReset() {
        echo = FloatArray(0)
        echoPos = 0
    }
}
