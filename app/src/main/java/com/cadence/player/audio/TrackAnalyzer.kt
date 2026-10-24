package com.cadence.player.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline analysis of one audio file: tempo, beat grid, downbeats, key, intro/outro,
 * sections, energy and a 3-band waveform. Runs on a background thread, ~1–2 s per song.
 */
object TrackAnalyzer {
    private const val TARGET_RATE = 11025
    private const val MAX_SECONDS = 12 * 60
    private const val WAVE_BINS = 2400

    private const val FRAME = 512
    private const val HOP = 128

    // Krumhansl–Schmuckler key profiles.
    private val majorProfile = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val minorProfile = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    class Decoded(val samples: FloatArray, val sampleRate: Int, val durationMs: Long)

    fun analyze(context: Context, songId: Long, uri: Uri, onProgress: (Float) -> Unit = {}): TrackAnalysis? {
        val decoded = decode(context, uri) { onProgress(it * 0.6f) } ?: return null
        if (decoded.samples.size < decoded.sampleRate * 10) return null
        onProgress(0.65f)
        return compute(songId, decoded, onProgress)
    }

    // ------------------------------------------------------------------ decoding

    fun decode(context: Context, uri: Uri, onProgress: (Float) -> Unit): Decoded? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmFloat = false
            var factor = max(1, (sampleRate.toFloat() / TARGET_RATE).roundToInt())
            val maxOut = (MAX_SECONDS.toLong() * sampleRate / factor + 1).toInt()
            var out = FloatArray(min(maxOut, (durationUs / 1_000_000L * sampleRate / factor + sampleRate).toInt().coerceAtLeast(sampleRate)))
            var n = 0
            var acc = 0f
            var accCount = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastProgress = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > MAX_SECONDS * 1_000_000L) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        factor = max(1, (sampleRate.toFloat() / TARGET_RATE).roundToInt())
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val b = buf.order(ByteOrder.nativeOrder())
                            val ch = channels.coerceAtLeast(1)
                            if (pcmFloat) {
                                val fb = b.asFloatBuffer()
                                val frames = fb.remaining() / ch
                                for (f in 0 until frames) {
                                    var s = 0f
                                    for (c in 0 until ch) s += fb.get()
                                    acc += s / ch
                                    if (++accCount == factor) {
                                        if (n >= out.size) out = out.copyOf(min(maxOut, out.size * 2).coerceAtLeast(out.size + 1))
                                        if (n < out.size) out[n++] = acc / factor
                                        acc = 0f; accCount = 0
                                    }
                                }
                            } else {
                                val sb = b.asShortBuffer()
                                val frames = sb.remaining() / ch
                                for (f in 0 until frames) {
                                    var s = 0
                                    for (c in 0 until ch) s += sb.get().toInt()
                                    acc += s / (ch * 32768f)
                                    if (++accCount == factor) {
                                        if (n >= out.size) out = out.copyOf(min(maxOut, out.size * 2).coerceAtLeast(out.size + 1))
                                        if (n < out.size) out[n++] = acc / factor
                                        acc = 0f; accCount = 0
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (durationUs > 0 && info.presentationTimeUs - lastProgress > 2_000_000L) {
                            lastProgress = info.presentationTimeUs
                            onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                        if (n >= maxOut) outputDone = true
                    }
                }
            }
            val rate = sampleRate / factor
            return Decoded(out.copyOf(n), rate, n * 1000L / rate)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    // ------------------------------------------------------------------ analysis

    private fun compute(songId: Long, d: Decoded, onProgress: (Float) -> Unit): TrackAnalysis {
        val x = d.samples
        val sr = d.sampleRate
        val fps = sr.toDouble() / HOP
        val frames = max(1, (x.size - FRAME) / HOP)
        val fft = Fft(FRAME)
        val mag = FloatArray(FRAME / 2)
        val prev = FloatArray(FRAME / 2)
        val onset = FloatArray(frames)
        val lowOnset = FloatArray(frames)
        val low = FloatArray(frames)
        val mid = FloatArray(frames)
        val high = FloatArray(frames)
        val binHz = sr.toFloat() / FRAME
        val lowBin = (180f / binHz).toInt().coerceAtLeast(2)
        val midBin = (2000f / binHz).toInt().coerceAtMost(FRAME / 2 - 1)
        var centroidSum = 0.0
        var centroidW = 0.0

        for (f in 0 until frames) {
            fft.magnitudes(x, f * HOP, mag)
            var flux = 0f
            var lflux = 0f
            var l = 0f; var m = 0f; var h = 0f
            for (i in 1 until FRAME / 2) {
                val v = ln(1f + 100f * mag[i])
                val dlt = v - prev[i]
                if (dlt > 0) {
                    flux += dlt
                    if (i <= lowBin) lflux += dlt
                }
                prev[i] = v
                val e = mag[i] * mag[i]
                when {
                    i <= lowBin -> l += e
                    i <= midBin -> m += e
                    else -> h += e
                }
                centroidSum += (i * mag[i]).toDouble()
                centroidW += mag[i].toDouble()
            }
            onset[f] = flux
            lowOnset[f] = lflux
            low[f] = sqrt(l); mid[f] = sqrt(m); high[f] = sqrt(h)
        }
        onProgress(0.75f)

        // Normalise onset envelope: subtract local mean (removes slow loudness changes).
        val env = FloatArray(frames)
        val win = (fps * 0.5).toInt().coerceAtLeast(1)
        var running = 0f
        for (f in 0 until frames) {
            running += onset[f]
            if (f >= win) running -= onset[f - win]
            val mean = running / min(f + 1, win)
            env[f] = max(0f, onset[f] - mean)
        }

        // ---- Tempo: autocorrelation, weighted towards 120 BPM, folded to 85..175.
        val minLag = (60.0 * fps / 200.0).toInt()
        val maxLag = (60.0 * fps / 60.0).toInt()
        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var s = 0.0
            var i = 0
            while (i + lag < frames) {
                s += env[i] * env[i + lag]
                i += 2
            }
            val bpm = 60.0 * fps / lag
            val weight = exp(-0.5 * Math.pow(ln(bpm / 120.0) / ln(2.0) / 0.9, 2.0))
            val score = s * weight
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        var bpm = 60.0 * fps / bestLag
        while (bpm < 85) bpm *= 2
        while (bpm > 175) bpm /= 2

        // ---- Refine tempo and phase with a comb over the whole track.
        fun envAt(t: Double): Float {
            val i = t.toInt()
            if (i < 0 || i + 1 >= frames) return 0f
            val fr = (t - i).toFloat()
            return env[i] * (1 - fr) + env[i + 1] * fr
        }
        var bestBpm = bpm
        var bestPhase = 0.0
        var best = -1.0
        var cand = bpm - 1.5
        while (cand <= bpm + 1.5) {
            val period = 60.0 * fps / cand
            val phaseSteps = 32
            for (p in 0 until phaseSteps) {
                val phase = period * p / phaseSteps
                var s = 0.0
                var t = phase
                while (t < frames) {
                    s += envAt(t)
                    t += period
                }
                if (s > best) {
                    best = s
                    bestBpm = cand
                    bestPhase = phase
                }
            }
            cand += 0.05
        }
        // Prefer a "round" tempo when it's within 0.15 BPM (most dance music is).
        val rounded = Math.round(bestBpm * 2) / 2.0
        if (abs(rounded - bestBpm) < 0.15) bestBpm = rounded
        val period = 60.0 * fps / bestBpm
        onProgress(0.85f)

        // ---- Downbeat: of the 4 beat offsets, the one with the strongest kicks.
        val beatCount = ((frames - bestPhase) / period).toInt()
        val offsetScore = DoubleArray(4)
        for (b in 0 until beatCount) {
            val t = bestPhase + b * period
            val i = t.roundToInt().coerceIn(0, frames - 1)
            var s = 0f
            for (k in max(0, i - 2)..min(frames - 1, i + 2)) s = max(s, lowOnset[k])
            offsetScore[b % 4] += s.toDouble()
        }
        val downOffset = offsetScore.indices.maxByOrNull { offsetScore[it] } ?: 0
        val firstDownbeatFrame = bestPhase + downOffset * period
        val beatMs = 60_000.0 / bestBpm
        val barMs = beatMs * 4
        val firstDownbeatMs = (firstDownbeatFrame / fps * 1000).toLong()

        // ---- Energy per bar (RMS from samples).
        val totalBars = max(1, ((d.durationMs - firstDownbeatMs) / barMs).toInt())
        val barEnergyRaw = FloatArray(totalBars)
        for (b in 0 until totalBars) {
            val s0 = ((firstDownbeatMs + b * barMs) / 1000.0 * sr).toInt().coerceIn(0, x.size)
            val s1 = ((firstDownbeatMs + (b + 1) * barMs) / 1000.0 * sr).toInt().coerceIn(s0, x.size)
            var sum = 0.0
            var i = s0
            while (i < s1) {
                sum += x[i] * x[i]
                i += 4
            }
            barEnergyRaw[b] = sqrt(sum / max(1, (s1 - s0) / 4)).toFloat()
        }
        val sorted = barEnergyRaw.sorted()
        val p10 = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.lastIndex)]
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.lastIndex)]
        val span = max(1e-6f, p95 - p10)
        val barEnergy = FloatArray(totalBars) { ((barEnergyRaw[it] - p10) / span).coerceIn(0f, 1f) }
        fun smoothBar(i: Int): Float {
            var s = 0f; var c = 0
            for (k in max(0, i - 1)..min(totalBars - 1, i + 1)) { s += barEnergy[k]; c++ }
            return s / c
        }

        // ---- Intro / outro: quiet-ish start and end, snapped to 8-bar phrases.
        val threshold = 0.55f
        var introBars = 0
        while (introBars < totalBars && smoothBar(introBars) < threshold) introBars++
        introBars = (introBars / 8) * 8
        if (introBars > 64 || introBars >= totalBars / 2) introBars = 0
        var outroBar = totalBars
        while (outroBar > 0 && smoothBar(outroBar - 1) < threshold) outroBar--
        outroBar = ((outroBar + 7) / 8) * 8
        if (totalBars - outroBar > 64 || outroBar <= totalBars / 2) outroBar = totalBars
        val introEndMs = if (introBars > 0) (firstDownbeatMs + introBars * barMs).toLong() else 0L
        val outroStartMs = min(d.durationMs, (firstDownbeatMs + outroBar.coerceAtMost(totalBars) * barMs).toLong())

        // ---- Sections in 8-bar blocks.
        val sections = ArrayList<Section>()
        val blocks = (totalBars + 7) / 8
        val blockEnergy = FloatArray(blocks) { b ->
            var s = 0f; var c = 0
            for (i in b * 8 until min(totalBars, b * 8 + 8)) { s += barEnergy[i]; c++ }
            if (c == 0) 0f else s / c
        }
        val labels = Array(blocks) { SectionType.GROOVE }
        for (b in 0 until blocks) {
            val startBar = b * 8
            labels[b] = when {
                introBars > 0 && startBar < introBars -> SectionType.INTRO
                startBar >= outroBar -> SectionType.OUTRO
                blockEnergy[b] >= 0.72f -> SectionType.DROP
                blockEnergy[b] <= 0.42f -> SectionType.BREAKDOWN
                else -> SectionType.GROOVE
            }
        }
        for (b in 0 until blocks - 1) {
            val rising = blockEnergy[b + 1] - blockEnergy[b] > 0.12f
            if (labels[b + 1] == SectionType.DROP && labels[b] != SectionType.DROP && labels[b] != SectionType.INTRO && rising) {
                labels[b] = SectionType.BUILD
            }
        }
        if (firstDownbeatMs > 500) {
            sections += Section(if (labels.firstOrNull() == SectionType.INTRO) SectionType.INTRO else SectionType.INTRO, 0, firstDownbeatMs, 0.2f)
        }
        var b = 0
        while (b < blocks) {
            var e = b
            while (e + 1 < blocks && labels[e + 1] == labels[b]) e++
            val start = (firstDownbeatMs + b * 8 * barMs).toLong()
            val end = if (e == blocks - 1) d.durationMs else (firstDownbeatMs + (e + 1) * 8 * barMs).toLong()
            var en = 0f
            for (k in b..e) en += blockEnergy[k]
            sections += Section(labels[b], start, end, en / (e - b + 1))
            b = e + 1
        }

        // ---- Key from a chromagram (long frames for frequency resolution).
        val keyFft = Fft(4096)
        val keyMag = FloatArray(2048)
        val chroma = DoubleArray(12)
        val keyBinHz = sr.toDouble() / 4096
        var pos = 0
        while (pos + 4096 < x.size) {
            keyFft.magnitudes(x, pos, keyMag)
            for (i in 1 until 2048) {
                val hz = i * keyBinHz
                if (hz < 55 || hz > 2000) continue
                val midi = 69 + 12 * ln(hz / 440.0) / ln(2.0)
                val pc = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pc] += keyMag[i].toDouble()
            }
            pos += 2048
        }
        var bestKey = 9 + 12 // A minor default
        var bestCorr = -2.0
        var secondCorr = -2.0
        for (k in 0 until 24) {
            val profile = if (k < 12) majorProfile else minorProfile
            val tonic = k % 12
            val corr = correlation(chroma) { pc -> profile[((pc - tonic) % 12 + 12) % 12].toDouble() }
            if (corr > bestCorr) {
                secondCorr = bestCorr
                bestCorr = corr
                bestKey = k
            } else if (corr > secondCorr) {
                secondCorr = corr
            }
        }
        onProgress(0.95f)

        // ---- Waveform bins.
        val wavePeak = ByteArray(WAVE_BINS)
        val waveLow = ByteArray(WAVE_BINS)
        val waveMid = ByteArray(WAVE_BINS)
        val waveHigh = ByteArray(WAVE_BINS)
        val lMax = low.maxOrNull()?.takeIf { it > 0 } ?: 1f
        val mMax = mid.maxOrNull()?.takeIf { it > 0 } ?: 1f
        val hMax = high.maxOrNull()?.takeIf { it > 0 } ?: 1f
        var peakMax = 1e-6f
        for (v in x) peakMax = max(peakMax, abs(v))
        for (w in 0 until WAVE_BINS) {
            val s0 = (w.toLong() * x.size / WAVE_BINS).toInt()
            val s1 = ((w + 1).toLong() * x.size / WAVE_BINS).toInt().coerceAtMost(x.size)
            var pk = 0f
            var i = s0
            while (i < s1) { pk = max(pk, abs(x[i])); i += 2 }
            wavePeak[w] = ((pk / peakMax) * 255).toInt().coerceIn(0, 255).toByte()
            val f0 = (w.toLong() * frames / WAVE_BINS).toInt()
            val f1 = ((w + 1).toLong() * frames / WAVE_BINS).toInt().coerceIn(f0 + 1, frames)
            var ls = 0f; var ms = 0f; var hs = 0f
            for (f in f0 until f1) { ls += low[f]; ms += mid[f]; hs += high[f] }
            val c = (f1 - f0).toFloat()
            waveLow[w] = (sqrtNorm(ls / c / lMax) * 255).toInt().coerceIn(0, 255).toByte()
            waveMid[w] = (sqrtNorm(ms / c / mMax) * 255).toInt().coerceIn(0, 255).toByte()
            waveHigh[w] = (sqrtNorm(hs / c / hMax) * 255).toInt().coerceIn(0, 255).toByte()
        }

        val brightness = if (centroidW > 0) ((centroidSum / centroidW) / (FRAME / 2) * 2.2).toFloat().coerceIn(0f, 1f) else 0.5f
        val meanEnergy = barEnergy.average().toFloat()

        return TrackAnalysis(
            songId = songId,
            durationMs = d.durationMs,
            bpm = bestBpm.toFloat(),
            firstDownbeatMs = firstDownbeatMs,
            keyIndex = bestKey,
            keyConfidence = (bestCorr - secondCorr).toFloat().coerceIn(0f, 1f),
            introEndMs = introEndMs,
            outroStartMs = outroStartMs,
            sections = sections,
            energy = meanEnergy,
            brightness = brightness,
            wavePeak = wavePeak,
            waveLow = waveLow,
            waveMid = waveMid,
            waveHigh = waveHigh,
            barEnergy = barEnergy,
        )
    }

    private fun sqrtNorm(v: Float) = sqrt(v.coerceIn(0f, 1f))

    private inline fun correlation(chroma: DoubleArray, profile: (Int) -> Double): Double {
        val meanC = chroma.average()
        var meanP = 0.0
        for (i in 0 until 12) meanP += profile(i)
        meanP /= 12
        var num = 0.0; var dc = 0.0; var dp = 0.0
        for (i in 0 until 12) {
            val a = chroma[i] - meanC
            val p = profile(i) - meanP
            num += a * p; dc += a * a; dp += p * p
        }
        return if (dc <= 0 || dp <= 0) 0.0 else num / sqrt(dc * dp)
    }
}
