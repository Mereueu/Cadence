package com.cadence.player.ui.visualizer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cadence.player.audio.LiveAudio
import com.cadence.player.audio.SectionType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface VisualScene {
    fun update(f: VisualFrame, w: Float, h: Float)
    fun draw(scope: DrawScope, f: VisualFrame)
}

fun createScene(scene: Scene): VisualScene = when (scene) {
    Scene.NEBULA -> NebulaScene()
    Scene.LIQUID -> LiquidScene()
    Scene.WARP -> WarpScene()
    Scene.HORIZON -> HorizonScene()
    Scene.AURA -> AuraScene()
    Scene.AMBIENT -> AmbientScene()
}

private fun band(f: VisualFrame, i: Int): Float = f.audio.bands[((i % LiveAudio.BANDS) + LiveAudio.BANDS) % LiveAudio.BANDS]

// ============================================================================================
// Nebula: a flowing particle field. Builds pull it inward, drops blow it apart.
// ============================================================================================

class NebulaScene : VisualScene {
    private val capacity = 700
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val psize = FloatArray(capacity)
    private val tone = FloatArray(capacity)
    private var count = 0
    private var w = 0f
    private var h = 0f

    private fun spawn(i: Int, nearCenter: Boolean) {
        val a = Random.nextFloat() * 2 * PI.toFloat()
        val r = if (nearCenter) Random.nextFloat() * min(w, h) * 0.12f else sqrt(Random.nextFloat()) * max(w, h) * 0.6f
        x[i] = w / 2 + cos(a) * r
        y[i] = h / 2 + sin(a) * r
        px[i] = x[i]; py[i] = y[i]
        vx[i] = 0f; vy[i] = 0f
        life[i] = 0.4f + Random.nextFloat() * 0.6f
        psize[i] = 1f + Random.nextFloat() * 2.2f
        tone[i] = Random.nextFloat()
    }

    override fun update(f: VisualFrame, w: Float, h: Float) {
        if (this.w != w || this.h != h) {
            this.w = w; this.h = h
            for (i in 0 until capacity) spawn(i, false)
        }
        val target = (220 + 420 * f.density * (0.35f + f.energy)).toInt().coerceIn(80, capacity)
        count += ((target - count) * 0.05f).toInt().let { if (it == 0 && count != target) (if (target > count) 1 else -1) else it }
        count = count.coerceIn(0, capacity)
        val cx = w / 2; val cy = h / 2
        val calm = if (f.section == SectionType.BREAKDOWN) 0.35f else 1f
        val speed = (0.5f + f.energy * 2.6f * f.intensity) * calm
        val attract = when (f.section) {
            SectionType.BUILD -> 0.002f + f.sectionProgress * 0.02f
            SectionType.INTRO -> 0.002f
            else -> 0f
        }
        val explode = f.sectionChanged && f.section == SectionType.DROP
        val kick = if (f.newBeat) f.audio.bass * 2.4f * f.intensity + 0.3f else 0f
        val t = f.time
        val step = f.dt * 60f
        for (i in 0 until count) {
            px[i] = x[i]; py[i] = y[i]
            val angle = (sin(x[i] * 0.0035f + t * 0.25f) + cos(y[i] * 0.0032f - t * 0.2f)) * PI.toFloat()
            vx[i] += cos(angle) * 0.07f * speed
            vy[i] += sin(angle) * 0.07f * speed
            val dx = x[i] - cx; val dy = y[i] - cy
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (attract > 0f) { vx[i] -= dx / d * attract * d * 0.02f; vy[i] -= dy / d * attract * d * 0.02f }
            if (kick > 0f) { vx[i] += dx / d * kick; vy[i] += dy / d * kick }
            if (explode) { vx[i] += dx / d * 9f * f.intensity; vy[i] += dy / d * 9f * f.intensity }
            vx[i] *= 0.955f; vy[i] *= 0.955f
            x[i] += vx[i] * step; y[i] += vy[i] * step
            life[i] -= f.dt * 0.12f
            if (life[i] <= 0f || x[i] < -40 || x[i] > w + 40 || y[i] < -40 || y[i] > h + 40) {
                spawn(i, f.section == SectionType.DROP || Random.nextFloat() < 0.3f)
            }
        }
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        drawRect(Brush.radialGradient(listOf(f.c2.copy(alpha = 0.35f + f.sectionPulse * 0.3f), Color.Black), Offset(size.width / 2, size.height / 2), max(size.width, size.height) * 0.75f))
        val boost = 1f + f.audio.bass * 1.6f * f.intensity
        for (i in 0 until count) {
            val c = lerp(f.c1, f.c3, tone[i])
            val a = (life[i] * 0.9f).coerceIn(0f, 1f)
            drawLine(c.copy(alpha = a * 0.6f), Offset(px[i], py[i]), Offset(x[i], y[i]), psize[i] * boost, StrokeCap.Round)
            drawCircle(c.copy(alpha = a), psize[i] * boost * 0.7f, Offset(x[i], y[i]), blendMode = BlendMode.Plus)
        }
    }
}

// ============================================================================================
// Liquid: glowing blobs that drift, breathe with the bands and gather during builds.
// ============================================================================================

class LiquidScene : VisualScene {
    private val n = 8
    private val phase = FloatArray(n) { Random.nextFloat() * 6.28f }
    private val fx = FloatArray(n) { 0.13f + Random.nextFloat() * 0.25f }
    private val fy = FloatArray(n) { 0.11f + Random.nextFloat() * 0.25f }
    private var clock = 0f
    private var spread = 1f

    override fun update(f: VisualFrame, w: Float, h: Float) {
        val speed = when (f.section) {
            SectionType.BREAKDOWN -> 0.25f
            SectionType.DROP -> 1.4f
            else -> 0.7f
        } * (0.6f + f.energy * 0.8f)
        clock += f.dt * speed
        val target = when (f.section) {
            SectionType.BUILD -> 1f - f.sectionProgress * 0.6f
            SectionType.DROP -> 1.15f
            SectionType.BREAKDOWN -> 0.85f
            else -> 1f
        }
        spread += (target - spread) * min(1f, f.dt * (if (f.sectionChanged) 8f else 1.5f))
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        drawRect(Color.Black)
        val w = size.width; val h = size.height
        val base = min(w, h) * 0.34f
        val colors = listOf(f.c1, f.c2, f.c3)
        for (i in 0 until n) {
            val cx = w / 2 + sin(clock * fx[i] * 6f + phase[i]) * w * 0.32f * spread
            val cy = h / 2 + cos(clock * fy[i] * 6f + phase[i] * 1.3f) * h * 0.3f * spread
            val b = band(f, i * 4 + 1)
            val r = base * (0.55f + b * 0.9f * f.intensity) * (1f + f.beatPulse * 0.12f)
            val c = colors[i % 3]
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0.25f), Color.Transparent), Offset(cx, cy), r),
                r,
                Offset(cx, cy),
                blendMode = BlendMode.Plus,
            )
        }
        if (f.sectionPulse > 0f) drawRect(f.c3.copy(alpha = f.sectionPulse * 0.25f), blendMode = BlendMode.Plus)
    }
}

// ============================================================================================
// Warp: a 3D tunnel of spectrum-shaped rings. One ring passes per beat.
// ============================================================================================

class WarpScene : VisualScene {
    private val rings = 26
    private val shapes = Array(rings) { FloatArray(16) }
    private val z = FloatArray(rings) { it + 1f }
    private val glow = FloatArray(rings)
    private var cam = 0f
    private var rot = 0f
    private val starX = FloatArray(140) { Random.nextFloat() * 2 - 1 }
    private val starY = FloatArray(140) { Random.nextFloat() * 2 - 1 }
    private val starZ = FloatArray(140) { Random.nextFloat() * 20 + 1 }

    override fun update(f: VisualFrame, w: Float, h: Float) {
        val mult = when (f.section) {
            SectionType.DROP -> 1.7f
            SectionType.BREAKDOWN -> 0.45f
            SectionType.BUILD -> 0.8f + f.sectionProgress * 0.9f
            else -> 1f
        }
        val speed = f.bpm / 60f * mult * (0.6f + 0.4f * f.intensity)
        cam += f.dt * speed
        rot += f.dt * (0.08f + (if (f.section == SectionType.DROP) 0.5f else 0f) + f.audio.treble * 0.3f)
        for (i in 0 until rings) {
            if (z[i] < cam + 0.25f) {
                z[i] += rings
                val s = shapes[i]
                for (k in 0 until 16) s[k] = band(f, k * 2)
                glow[i] = if (f.newBeat) 1f else 0.4f
            }
            glow[i] = max(0f, glow[i] - f.dt * 0.4f)
        }
        for (s in starZ.indices) {
            starZ[s] -= f.dt * speed * 3f
            if (starZ[s] < 0.2f) {
                starZ[s] = 20f
                starX[s] = Random.nextFloat() * 2 - 1
                starY[s] = Random.nextFloat() * 2 - 1
            }
        }
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        drawRect(Color.Black)
        val w = size.width; val h = size.height
        val fov = min(w, h) * 0.6f
        val cx = w / 2 + sin(f.time * 0.3f) * w * 0.05f
        val cy = h / 2 + cos(f.time * 0.23f) * h * 0.04f
        // Stars.
        for (s in starZ.indices) {
            val sc = fov / starZ[s]
            val sx = cx + starX[s] * sc * 3f
            val sy = cy + starY[s] * sc * 3f
            val a = (1f - starZ[s] / 20f).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = a * 0.7f), 1.2f + a * 1.5f, Offset(sx, sy))
        }
        // Rings, far to near.
        val order = (0 until rings).sortedByDescending { z[it] }
        val path = Path()
        for (i in order) {
            val depth = z[i] - cam
            if (depth <= 0.2f) continue
            val scale = fov / depth
            val far = (depth / rings).coerceIn(0f, 1f)
            val color = lerp(f.c3, f.c1, far)
            val alpha = ((1f - far) * (0.5f + glow[i] * 0.5f)).coerceIn(0f, 1f)
            path.reset()
            val pts = 48
            for (k in 0..pts) {
                val a = 2 * PI.toFloat() * k / pts + rot + i * 0.05f
                val bi = (k % 16).let { if (k / 16 % 2 == 1) 15 - it else it }
                val r = 1f + shapes[i][bi] * 0.55f * f.intensity
                val px = cx + cos(a) * r * scale
                val py = cy + sin(a) * r * scale
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color.copy(alpha = alpha), style = Stroke(width = (2.5f * scale / fov * 2.2f).coerceIn(0.6f, 6f)))
        }
        if (f.sectionPulse > 0f) drawRect(f.c3.copy(alpha = f.sectionPulse * 0.3f), blendMode = BlendMode.Plus)
    }
}

// ============================================================================================
// Horizon: a 3D landscape built from the spectrum, scrolling at the song's tempo, under a sun
// that rises through the build-up.
// ============================================================================================

class HorizonScene : VisualScene {
    private val rows = 34
    private val cols = 33
    private val heights = Array(rows) { FloatArray(cols) }
    private var head = 0
    private var scroll = 0f
    private var sunRise = 0f

    override fun update(f: VisualFrame, w: Float, h: Float) {
        val mult = when (f.section) {
            SectionType.DROP -> 1.6f
            SectionType.BREAKDOWN -> 0.5f
            else -> 1f
        }
        scroll += f.dt * (f.bpm / 60f) * 2f * mult
        while (scroll >= 1f) {
            scroll -= 1f
            head = (head + 1) % rows
            val row = heights[head]
            val amp = (if (f.section == SectionType.DROP) 1.5f else 1f) * f.intensity
            for (c in 0 until cols) {
                val fromCenter = abs(c - cols / 2)
                val b = band(f, fromCenter * 2)
                val edge = (fromCenter / (cols / 2f))
                row[c] = b * amp * (0.25f + edge * edge * 1.2f)
            }
        }
        val targetSun = when (f.section) {
            SectionType.BUILD -> f.sectionProgress
            SectionType.DROP -> 1f
            SectionType.BREAKDOWN -> 0.2f
            else -> 0.5f
        }
        sunRise += (targetSun - sunRise) * min(1f, f.dt * 0.8f)
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        val w = size.width; val h = size.height
        val horizon = h * 0.46f
        drawRect(Brush.verticalGradient(listOf(Color.Black, f.c2.copy(alpha = 0.6f), Color.Black), 0f, h * 0.62f))
        // Sun with stripes.
        val sunR = min(w, h) * 0.26f * (1f + f.audio.bass * 0.06f)
        val sunY = horizon - sunR * (0.15f + sunRise * 0.75f)
        drawCircle(Brush.verticalGradient(listOf(f.c3, f.c1), sunY - sunR, sunY + sunR), sunR, Offset(w / 2, sunY))
        var sy = sunY + sunR * 0.1f
        var gap = 3f
        while (sy < sunY + sunR) {
            drawRect(Color.Black, Offset(w / 2 - sunR, sy), Size(sunR * 2, gap))
            sy += gap * 3f
            gap += 1.2f
        }
        drawRect(Color.Black, Offset(0f, horizon), Size(w, h - horizon))
        // Terrain rows, far to near.
        val camHeight = h * 0.5f
        val spacing = w * 0.16f
        val bright = 0.55f + f.beatPulse * 0.3f + f.sectionPulse * 0.4f
        var prevXs: FloatArray? = null
        var prevYs: FloatArray? = null
        for (r in rows - 1 downTo 0) {
            val idx = ((head - r) % rows + rows) % rows
            val depth = r + 1f - scroll
            if (depth <= 0.3f) continue
            val sc = 1f / depth
            val xs = FloatArray(cols)
            val ys = FloatArray(cols)
            for (c in 0 until cols) {
                xs[c] = w / 2 + (c - cols / 2) * spacing * sc * 1.8f
                ys[c] = horizon + camHeight * sc * 0.9f - heights[idx][c] * camHeight * 0.8f * sc
            }
            val near = (1f - r / rows.toFloat())
            val color = lerp(f.c2, f.c1, near).copy(alpha = (near * bright).coerceIn(0f, 1f))
            for (c in 0 until cols - 1) drawLine(color, Offset(xs[c], ys[c]), Offset(xs[c + 1], ys[c + 1]), 1.4f)
            val px = prevXs; val py = prevYs
            if (px != null && py != null) {
                for (c in 0 until cols step 2) drawLine(color.copy(alpha = color.alpha * 0.6f), Offset(px[c], py[c]), Offset(xs[c], ys[c]), 1f)
            }
            prevXs = xs; prevYs = ys
        }
    }
}

// ============================================================================================
// Aura: the album art at the centre, breathing on the beat, with ripples on every beat and a
// burst of rings when the drop lands.
// ============================================================================================

class AuraScene : VisualScene {
    var art: ImageBitmap? = null
    private val ringR = FloatArray(24)
    private val ringA = FloatArray(24)
    private val ringW = FloatArray(24)
    private var next = 0
    private val orbit = FloatArray(60) { Random.nextFloat() * 6.28f }
    private val orbitR = FloatArray(60) { 1.05f + Random.nextFloat() * 0.5f }
    private var spin = 0f

    private fun ring(width: Float, alpha: Float) {
        ringR[next] = 0.5f
        ringA[next] = alpha
        ringW[next] = width
        next = (next + 1) % ringR.size
    }

    override fun update(f: VisualFrame, w: Float, h: Float) {
        if (f.newBeat) ring(1.5f + f.audio.bass * 3f, 0.5f + f.audio.bass * 0.4f)
        if (f.newPhrase) ring(4f, 0.8f)
        if (f.sectionChanged && f.section == SectionType.DROP) {
            ring(6f, 1f); ring(3f, 0.9f); ring(2f, 0.8f)
        }
        val grow = if (f.section == SectionType.BREAKDOWN) 0.25f else 0.6f + f.energy * 0.6f
        for (i in ringR.indices) {
            if (ringA[i] <= 0f) continue
            ringR[i] += f.dt * grow
            ringA[i] -= f.dt * 0.45f
        }
        spin += f.dt * (0.15f + f.energy * 0.8f)
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        val w = size.width; val h = size.height
        val img = art
        drawRect(Color.Black)
        if (img != null) {
            val cover = max(w / img.width, h / img.height) * 1.2f
            val dw = img.width * cover; val dh = img.height * cover
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(((w - dw) / 2).toInt(), ((h - dh) / 2).toInt()),
                dstSize = IntSize(dw.toInt(), dh.toInt()),
                alpha = 0.22f,
            )
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), Offset(w / 2, h / 2), max(w, h) * 0.7f))
        val side = min(w, h) * 0.6f * (1f + f.beatPulse * 0.035f * f.intensity + f.audio.bass * 0.025f)
        val c = Offset(w / 2, h * 0.45f)
        for (i in ringR.indices) {
            if (ringA[i] <= 0f) continue
            drawCircle(lerp(f.c1, f.c3, (i % 3) / 2f).copy(alpha = ringA[i].coerceIn(0f, 1f)), side * ringR[i] * 1.1f, c, style = Stroke(ringW[i]))
        }
        for (i in orbit.indices) {
            val a = orbit[i] + spin * (if (i % 2 == 0) 1f else -0.7f)
            val r = side * 0.5f * orbitR[i] * (1f + band(f, i) * 0.3f)
            drawCircle(f.c3.copy(alpha = 0.6f), 1.5f + band(f, i) * 3f, Offset(c.x + cos(a) * r, c.y + sin(a) * r), blendMode = BlendMode.Plus)
        }
        val left = c.x - side / 2; val top = c.y - side / 2
        if (img != null) {
            val clip = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(6f, 6f))) }
            clipPath(clip) {
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(side.toInt(), side.toInt()),
                )
            }
        } else {
            drawRect(Brush.linearGradient(listOf(f.c1, f.c2)), Offset(left, top), Size(side, side))
        }
        if (f.sectionPulse > 0f) drawRect(f.c3.copy(alpha = f.sectionPulse * 0.2f), blendMode = BlendMode.Plus)
    }
}

// ============================================================================================
// Always-on: a dim, thin, slowly drifting ring. Few lit pixels, low frame rate, burn-in safe.
// ============================================================================================

class AmbientScene : VisualScene {
    var center = Offset.Zero
        private set

    override fun update(f: VisualFrame, w: Float, h: Float) {
        center = Offset(w / 2 + sin(f.time / 40f) * w * 0.15f, h * 0.42f + cos(f.time / 53f) * h * 0.08f)
    }

    override fun draw(scope: DrawScope, f: VisualFrame) = with(scope) {
        drawRect(Color.Black)
        val c = this@AmbientScene.center
        val r0 = min(size.width, size.height) * 0.16f
        val path = Path()
        val pts = 96
        for (k in 0..pts) {
            val a = 2 * PI.toFloat() * k / pts
            val bi = (k % 32).let { if (k / 32 % 2 == 1) 31 - it else it }
            val wobble = if (f.audio.active) band(f, bi) * 0.18f else sin(a * 3 + f.time) * 0.02f + f.beatPulse * 0.03f
            val r = r0 * (1f + wobble)
            val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, Color.White.copy(alpha = 0.32f), style = Stroke(1.2f))
    }
}
