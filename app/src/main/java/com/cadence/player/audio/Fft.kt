package com.cadence.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small radix-2 FFT with a cached Hann window, sized for real-time and offline analysis. */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val bitRev = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }
    val window = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * PI * it / (size - 1))).toFloat() }

    private val re = FloatArray(size)
    private val im = FloatArray(size)

    /**
     * Windowed magnitude spectrum of [input] (length ≥ size, read from [offset]).
     * Writes size/2 magnitudes into [out].
     */
    fun magnitudes(input: FloatArray, offset: Int, out: FloatArray) {
        for (i in 0 until size) {
            val j = bitRev[i]
            re[j] = input[offset + i] * window[i]
            im[j] = 0f
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val l = j + half
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
        val n = size / 2
        for (i in 0 until n) out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
    }
}
