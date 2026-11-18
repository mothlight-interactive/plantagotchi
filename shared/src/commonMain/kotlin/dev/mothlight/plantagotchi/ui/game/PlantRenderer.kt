package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * How the plant moves this frame. Distances are in units of the old 25 px grid
 * (the renderer scales them to [PlantRenderer.GRID]).
 *
 * @param sway sideways bend at the top (wind)
 * @param lean extra bend at the top (behaviors such as turning to the sun)
 * @param flap leaf angle: positive raises leaves, negative lowers them
 * @param stretch extra stem height
 * @param squash vertical scale offset, e.g. 0.1 = 10 % taller
 * @param droop 0 = healthy, 1 = withered: bends over, leaves hang
 * @param time seconds, for per-leaf flutter
 */
data class PlantPose(
    val sway: Float = 0f,
    val lean: Float = 0f,
    val flap: Float = 0f,
    val stretch: Float = 0f,
    val squash: Float = 0f,
    val droop: Float = 0f,
    val flutter: Float = 0.3f,
    val time: Float = 0f,
    /** -1 = sun on the left, 1 = on the right. */
    val sunDirection: Float = 0f,
    /** 0 = night, 1 = day (smoothed). */
    val daylight: Float = 1f,
    /** Flytrap jaws: 0 = open, 1 = shut. */
    val snap: Float = 0f,
    /** Flytrap: a fly buzzes around. */
    val fly: Boolean = false,
)

/**
 * Draws plants procedurally on a fine pixel grid, so they grow continuously and every part can
 * move. Shapes are rasterized into a shared cell buffer and flushed as merged runs; nothing is
 * allocated per frame. All variation comes from the seed (stable per plant).
 */
object PlantRenderer {
    /** Cells across the plant rect: pass `pixel = plantWidth / GRID`. */
    const val GRID = 72

    /** Pose distances were tuned for the old 25 px grid. */
    private const val K = GRID / 25f
    private const val PIF = 3.1415927f
    private const val MASK = 64
    private const val TAU = PIF * 2f

    private val r = PixelRaster(width = 240, height = 220, originX = 120, originY = 80)

    /**
     * Draws a plant with its stem base centered at [base] (bottom edge of the bottom pixel row).
     * [maturity] goes from 0 (seedling) to 1 (fully grown); [seed] varies the shape per plant.
     */
    fun draw(
        scope: DrawScope,
        species: Species,
        maturity: Float,
        seed: Int,
        pose: PlantPose,
        base: Offset,
        pixel: Float,
    ) {
        val m = maturity.coerceIn(0f, 1f)
        when (species) {
            Species.SPROUT -> drawSprout(m, seed, pose)
            Species.CACTUS -> drawCactus(m, seed, pose)
            Species.FERN -> drawFern(m, seed, pose)
            Species.SUNFLOWER -> drawSunflower(m, seed, pose)
            Species.TULIP -> drawTulips(m, seed, pose)
            Species.MONSTERA -> drawMonstera(m, seed, pose)
            Species.BONSAI -> drawBonsai(m, seed, pose)
            Species.FLYTRAP -> drawFlytrap(m, seed, pose)
            Species.POTHOS -> drawPothosCrown(m, seed, pose)
        }
        r.flush(scope, base, pixel)
    }

    /**
     * Parts that hang in front of the pot (pothos vines). Call after drawing the pot, with the
     * same arguments as [draw]. Does nothing for other species.
     */
    fun drawFront(
        scope: DrawScope,
        species: Species,
        maturity: Float,
        seed: Int,
        pose: PlantPose,
        base: Offset,
        pixel: Float,
    ) {
        if (species != Species.POTHOS) return
        drawPothosVines(maturity.coerceIn(0f, 1f), seed, pose)
        r.flush(scope, base, pixel)
    }

    // region Palettes

    private class Pal(val outline: Int, val dark: Int, val mid: Int, val light: Int, val hi: Int, val vein: Int = hi)

    private fun pal(o: Long, d: Long, m: Long, l: Long, h: Long, v: Long = h) =
        Pal(o.toInt(), d.toInt(), m.toInt(), l.toInt(), h.toInt(), v.toInt())

    private val leafPal = pal(0xFF1E3B2A, 0xFF2D6640, 0xFF3D8747, 0xFF5EA84C, 0xFF94CA5C, 0xFF86C05A)
    private val youngPal = pal(0xFF2A4A26, 0xFF4E8C3C, 0xFF6CAE48, 0xFF90CA5A, 0xFFBEE07A)
    private val stemPal = pal(0xFF213F27, 0xFF34633A, 0xFF4A8540, 0xFF68A34C, 0xFF8CC05C)
    private val fernPal = pal(0xFF1D4430, 0xFF2D6A42, 0xFF44904B, 0xFF6AB656, 0xFFA2D66A)
    private val monsteraPal = pal(0xFF22402A, 0xFF3F6E43, 0xFF578C55, 0xFF73A86A, 0xFF97C285, 0xFFC6DA8A)
    private val cactusPal = pal(0xFF143A2E, 0xFF1F5C46, 0xFF2B7754, 0xFF419A66, 0xFF74C484)
    private val cactusPal2 = pal(0xFF1E3E26, 0xFF35683A, 0xFF4B8A44, 0xFF6BAB55, 0xFF9CCB70)
    private val padPal = pal(0xFF223F22, 0xFF3E6E33, 0xFF5A9440, 0xFF7DB352, 0xFFAAD06C)
    private val barkPal = pal(0xFF2B1A11, 0xFF4E3120, 0xFF6B472D, 0xFF8C623E, 0xFFAA7E52)
    private val canopyPal = pal(0xFF1D3D25, 0xFF2D6634, 0xFF46903E, 0xFF6BB64A, 0xFF9ED45E)
    private val sunPal = pal(0xFF8A4A12, 0xFFE08E1E, 0xFFF4B42A, 0xFFFFD447, 0xFFFFEA85)
    private val sunBackPal = pal(0xFF7A3E10, 0xFFC87418, 0xFFE09A22, 0xFFF0B832, 0xFFF8CC55)
    private val discPal = pal(0xFF2A160A, 0xFF4A2A14, 0xFF6A3D1E, 0xFF8C5A2C, 0xFFAE7838)
    private val whitePal = pal(0xFF7C6E86, 0xFFCFC6DA, 0xFFE8E2EE, 0xFFFFFFFF, 0xFFFFFFFF)
    private val trapPal = pal(0xFF1E4520, 0xFF3D7A2C, 0xFF5B9A36, 0xFF80BA48, 0xFFAAD668)
    private val pothosPal = pal(0xFF1C3D22, 0xFF2D6834, 0xFF448F3E, 0xFF66AE48, 0xFF9CCF62, 0xFFD8DE8A)

    private val bloomPals = arrayOf(
        pal(0xFF6A1838, 0xFFC23C6E, 0xFFE8609A, 0xFFF78DB8, 0xFFFFC2DA), // pink
        pal(0xFF5E1420, 0xFFB42838, 0xFFDC4A50, 0xFFF27466, 0xFFFFAA98), // red
        pal(0xFF6E3410, 0xFFD06A24, 0xFFF0903A, 0xFFFFB45E, 0xFFFFD69A), // coral
        pal(0xFF7A5612, 0xFFD89E20, 0xFFF2C434, 0xFFFFDE62, 0xFFFFF0A6), // yellow
    )
    private val tulipPals = arrayOf(
        pal(0xFF5A1020, 0xFFA81E34, 0xFFD43A4C, 0xFFEE6470, 0xFFFFA2A8), // red
        pal(0xFF6A2448, 0xFFC25088, 0xFFE27AAA, 0xFFF5A2C4, 0xFFFFD0E2), // pink
        pal(0xFF7A5210, 0xFFD89A1C, 0xFFF2C232, 0xFFFFDC5A, 0xFFFFF0A0), // yellow
        pal(0xFF6E2C0E, 0xFFC85A1C, 0xFFEE8030, 0xFFFFA454, 0xFFFFCC8E), // orange
        pal(0xFF3E2460, 0xFF7048A8, 0xFF9068C8, 0xFFB290E0, 0xFFD8C2F4), // violet
        pal(0xFF6E4A5A, 0xFFD2B4C2, 0xFFEADAE2, 0xFFFAF2F4, 0xFFFFFFFF), // white
        pal(0xFF6A2A2A, 0xFFD0605C, 0xFFF0837A, 0xFFFFA89A, 0xFFFFD2C6), // salmon
    )

    private const val SPINE = 0xFFF2E3A0.toInt()
    private const val SPINE_DARK = 0xFFB8A050.toInt()
    private const val TRAP_RED = 0xFFC8243C.toInt()
    private const val TRAP_DEEP = 0xFF7A1424.toInt()
    private const val TRAP_TOOTH = 0xFFE4F2A6.toInt()
    private const val FLY = 0xFF222034.toInt()
    private const val WING = 0x99FFFFFF.toInt()
    private const val STAMEN = 0xFF2E1A24.toInt()

    // endregion

    // region Primitives

    private fun dot(x: Float, y: Float, c: Int) = r.set(floor(x).toInt(), floor(y).toInt(), c)
    private fun frac(v: Float) = v - floor(v)

    /** Angle for a part pointing to [side] (-1 / 1) at [elev] radians above horizontal. */
    private fun dir(side: Int, elev: Float) = if (side > 0) elev else PIF - elev

    private fun bend(y: Float, h: Float, pose: PlantPose, stiff: Float = 1f): Float {
        val t = (y / max(h, 1f)).coerceIn(0f, 1f)
        return ((pose.sway + pose.lean) * stiff * t.pow(1.6f) + pose.droop * 6f * t * t) * K
    }

    // Polyline scratch for tubes
    private val px = FloatArray(256)
    private val py = FloatArray(256)
    private val pr = FloatArray(256)
    private var pn = 0

    private fun point(x: Float, y: Float, rad: Float) {
        if (pn >= px.size) return
        px[pn] = x; py[pn] = y; pr[pn] = rad; pn++
    }

    /** Thick outlined polyline through the points added with [point]; lit from the upper left. */
    private fun tube(p: Pal, texture: Int = 0) {
        if (pn == 0) return
        if (pn == 1) point(px[0], py[0] + 0.01f, pr[0])
        for (pass in 0..1) {
            val ext = if (pass == 0) 0.95f else 0f
            for (i in 0 until pn - 1) {
                val x0 = px[i]; val y0 = py[i]; val x1 = px[i + 1]; val y1 = py[i + 1]
                val r0 = pr[i] + ext; val r1 = pr[i + 1] + ext
                val rm = max(r0, r1)
                val dx = x1 - x0; val dy = y1 - y0
                val l2 = dx * dx + dy * dy
                for (cy in floor(min(y0, y1) - rm).toInt()..floor(max(y0, y1) + rm).toInt()) {
                    for (cx in floor(min(x0, x1) - rm).toInt()..floor(max(x0, x1) + rm).toInt()) {
                        val qx = cx + 0.5f - x0
                        val qy = cy + 0.5f - y0
                        val t = if (l2 > 0f) ((qx * dx + qy * dy) / l2).coerceIn(0f, 1f) else 0f
                        val ox = qx - dx * t
                        val oy = qy - dy * t
                        val rad = r0 + (r1 - r0) * t
                        if (ox * ox + oy * oy > rad * rad) continue
                        if (pass == 0) {
                            r.set(cx, cy, p.outline)
                            continue
                        }
                        val s = (-0.75f * ox + 0.66f * oy) / max(rad, 0.6f)
                        var col = when {
                            s > 0.3f -> p.light
                            s < -0.35f -> p.dark
                            else -> p.mid
                        }
                        if (texture != 0 && hash01(cx, cy, texture) < 0.14f) col = if (col == p.light) p.mid else p.dark
                        r.set(cx, cy, col)
                    }
                }
            }
        }
        pn = 0
    }

    /** A 1-cell line. */
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int) {
        val n = max(1, (max(abs(x1 - x0), abs(y1 - y0)) * 1.5f).toInt())
        for (i in 0..n) {
            val t = i.toFloat() / n
            dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)
        }
    }

    private const val OVATE = 0
    private const val STRAP = 1
    private const val ROUND = 2
    private const val PETAL = 3
    private const val WING_SHAPE = 4
    private const val HEART = 5

    private const val PLAIN = 0
    private const val VEINS = 1
    private const val SLITS = 2
    private const val MIDRIB = 4

    private fun profile(kind: Int, t: Float): Float = when (kind) {
        OVATE -> sin(PIF * t.pow(0.75f))
        STRAP -> sqrt(max(0f, 1f - t.pow(2.5f))) * min(1f, 0.45f + t * 4f)
        ROUND -> sqrt(max(0f, sin(PIF * (0.05f + 0.93f * t))))
        PETAL -> max(0f, sin(PIF * t.pow(0.6f))).pow(0.8f)
        WING_SHAPE -> 0.25f + 0.75f * t.pow(0.7f)
        else -> max(0f, sin(PIF * t.pow(0.6f))).pow(0.55f) // HEART
    }

    private val lx = FloatArray(512)
    private val ly = FloatArray(512)
    private val lh = FloatArray(512)

    /**
     * A leaf (or petal) from ([x0], [y0]) in direction [angle] that curls by [curl] radians
     * towards its tip. Drawn as an outline pass and a shaded fill pass.
     */
    private fun leaf(
        x0: Float, y0: Float, angle: Float, len: Float, width: Float, curl: Float,
        p: Pal, shape: Int, style: Int = PLAIN, phase: Float = 0f,
    ) {
        if (len < 0.6f || width < 0.2f) return
        val steps = min(lx.size - 1, (len / 0.4f).toInt() + 1)
        val ds = len / steps
        var x = x0; var y = y0; var h = angle
        for (i in 0..steps) {
            lx[i] = x; ly[i] = y; lh[i] = h
            x += cos(h) * ds; y += sin(h) * ds; h += curl / steps
        }
        for (i in 0..steps) {
            val hw = width * 0.5f * profile(shape, i.toFloat() / steps) + 0.9f
            val nx = -sin(lh[i]); val ny = cos(lh[i])
            var s = -hw
            while (s <= hw) {
                dot(lx[i] + nx * s, ly[i] + ny * s, p.outline)
                s += 0.4f
            }
        }
        dot(lx[steps] + cos(lh[steps]) * 0.9f, ly[steps] + sin(lh[steps]) * 0.9f, p.outline)
        for (pass in 1..2) {
            if (pass == 2 && style and SLITS == 0) break
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val hw = width * 0.5f * profile(shape, t)
                val nx = -sin(lh[i]); val ny = cos(lh[i])
                if (hw < 0.3f) {
                    if (pass == 1) dot(lx[i], ly[i], p.mid)
                    continue
                }
                val lit = if (-0.6f * nx + 0.8f * ny >= 0f) 1f else -1f
                val along = t * len
                var s = -hw
                while (s <= hw) {
                    val q = s / hw * lit
                    val a = abs(s)
                    if (pass == 2) {
                        // Monstera: slits from the edge and holes near the midrib
                        val slit = abs(q) > 0.5f && frac(along / 3.8f + a * 0.12f + phase) < 0.2f && along > 2.5f && t < 0.88f
                        val hole = abs(q) in 0.2f..0.36f && frac(along / 3.8f + 0.5f + phase) < 0.17f && along > 3f && t < 0.78f
                        if (slit || hole) dot(lx[i] + nx * s, ly[i] + ny * s, p.outline)
                    } else {
                        var col = when {
                            q > 0.3f -> if (q < 0.75f && t > 0.2f && t < 0.62f) p.hi else p.light
                            q < -0.4f -> p.dark
                            else -> p.mid
                        }
                        if (style and (VEINS or MIDRIB) != 0 && len > 5f && a < 0.45f && t < 0.9f) col = p.vein
                        if (style and VEINS != 0 && len > 6f && a > 1f && a < hw - 0.9f &&
                            frac((along - a * 0.9f) / 3.4f) < 0.2f
                        ) col = if (q > 0f) p.vein else p.mid
                        dot(lx[i] + nx * s, ly[i] + ny * s, col)
                    }
                    s += 0.4f
                }
            }
        }
    }

    // Ellipse union scratch
    private val ex = FloatArray(10)
    private val ey = FloatArray(10)
    private val erx = FloatArray(10)
    private val ery = FloatArray(10)
    private var en = 0

    private fun lump(x: Float, y: Float, rx: Float, ry: Float) {
        if (en >= ex.size || rx < 0.4f || ry < 0.4f) return
        ex[en] = x; ey[en] = y; erx[en] = rx; ery[en] = ry; en++
    }

    /** Fills the union of the lumps added with [lump]; later lumps are in front. */
    private fun blob(p: Pal, texture: Int = 0, ox: Float = 0f, oy: Float = 0f) {
        if (en == 0) return
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (k in 0 until en) {
            x0 = min(x0, ex[k] - erx[k] - 1.5f); x1 = max(x1, ex[k] + erx[k] + 1.5f)
            y0 = min(y0, ey[k] - ery[k] - 1.5f); y1 = max(y1, ey[k] + ery[k] + 1.5f)
        }
        for (cy in floor(y0).toInt()..floor(y1).toInt()) {
            for (cx in floor(x0).toInt()..floor(x1).toInt()) {
                var inside = -1
                var near = false
                for (k in 0 until en) {
                    val dx = cx + 0.5f - ex[k]; val dy = cy + 0.5f - ey[k]
                    val u = dx / erx[k]; val v = dy / ery[k]
                    if (u * u + v * v <= 1f) inside = k
                    else {
                        val u2 = dx / (erx[k] + 1f); val v2 = dy / (ery[k] + 1f)
                        if (u2 * u2 + v2 * v2 <= 1f) near = true
                    }
                }
                if (inside >= 0) {
                    val k = inside
                    val u = (cx + 0.5f - ex[k]) / erx[k]
                    val v = (cy + 0.5f - ey[k]) / ery[k]
                    val s = -0.55f * u + 0.83f * v
                    var col = when {
                        s > 0.62f -> p.hi
                        s > 0.2f -> p.light
                        s < -0.45f -> p.dark
                        else -> p.mid
                    }
                    if (texture != 0) {
                        val hsh = hash01(floor(cx - ox).toInt(), floor(cy - oy).toInt(), texture)
                        if (hsh < 0.12f) col = when (col) { p.hi -> p.light; p.light -> p.mid; else -> p.dark }
                        else if (hsh > 0.9f && col != p.dark) col = if (col == p.mid) p.light else p.hi
                    }
                    r.set(cx, cy, col)
                } else if (near) r.set(cx, cy, p.outline)
            }
        }
        en = 0
    }

    private fun disc(x: Float, y: Float, rx: Float, ry: Float, p: Pal) {
        lump(x, y, rx, ry)
        blob(p)
    }

    /** Small flower facing up: petals fan out of ([x], [y]). */
    private fun blossom(x: Float, y: Float, size: Float, p: Pal, open: Float, turn: Float = 0f) {
        if (open < 0.4f) {
            disc(x, y + size * 0.4f, size * 0.35f + 0.6f, size * 0.45f + 0.6f, p)
            return
        }
        val petals = 7
        val len = size * (0.6f + 0.4f * open)
        for (k in 0 until petals) {
            val a = turn + PIF * (0.05f + 0.9f * k / (petals - 1))
            leaf(x, y, a, len, size * 0.55f, 0f, p, PETAL)
        }
        leaf(x, y, PIF / 2 + turn, len * 1.05f, size * 0.6f, 0f, p, PETAL)
        disc(x, y + size * 0.25f, size * 0.22f + 0.5f, size * 0.18f + 0.5f, bloomPals[3])
    }

    /** Index of the [rank]-th largest key (ties by index), without allocating. */
    private inline fun byRank(rank: Int, count: Int, key: (Int) -> Float): Int {
        for (j in 0 until count) {
            val a = key(j)
            var bigger = 0
            for (k in 0 until count) {
                val b = key(k)
                if (b > a || (b == a && k < j)) bigger++
            }
            if (bigger == rank) return j
        }
        return 0
    }

    // endregion

    // region Sprout

    private fun drawSprout(m: Float, seed: Int, pose: PlantPose) {
        val first = if (hash01(seed, 1) < 0.5f) 1 else -1
        val scaleY = 1f + pose.squash
        val h = ((8f + m * 40f + pose.stretch * K) * scaleY).coerceAtLeast(5f)
        var y = 0f
        while (true) {
            point(bend(y, h, pose), y, 0.55f + m * 0.6f * (1f - y / h))
            if (y >= h) break
            y = min(h, y + 2f)
        }
        tube(stemPal)

        var node = 0
        var ly = 6f * scaleY
        val step = max(5f, (8f + hash01(seed, 2) * 2f) * scaleY)
        while (ly <= h - 6f) {
            val side = if (node % 2 == 0) first else -first
            val age = ((h - ly) / (16f * scaleY)).coerceIn(0f, 1f)
            val len = 3f + age * (6f + m * 8f)
            val fl = sin(pose.time * (2.6f + node * 0.37f) + node * 1.9f) * pose.flutter
            val elev = 0.45f + (pose.flap + fl - pose.droop * 2.5f) * 0.3f
            leaf(bend(ly, h, pose) + side * 0.4f, ly, dir(side, elev), len, len * 0.62f, -side * (0.5f + pose.droop), leafPal, OVATE, VEINS)
            node++
            ly += step
        }

        val tx = bend(h, h, pose)
        val crown = 2.5f + m * 4.5f
        val fl = sin(pose.time * 3.3f) * pose.flutter
        val shape = if (m < 0.25f) ROUND else OVATE
        val elev = 0.95f + pose.flap * 0.3f - pose.droop * 1.6f
        leaf(tx, h - 0.5f, dir(-1, elev + fl * 0.3f), crown, crown * 0.65f, 0.3f, youngPal, shape, MIDRIB)
        leaf(tx, h - 0.5f, dir(1, elev - fl * 0.3f), crown, crown * 0.65f, -0.3f, youngPal, shape, MIDRIB)

        if (m > 0.88f) {
            val open = ((m - 0.88f) / 0.12f).coerceIn(0f, 1f)
            line(tx, h, tx, h + 2f, stemPal.mid)
            if (open < 0.5f) disc(tx, h + 3f, 1.3f, 1.6f, bloomPals[0])
            else {
                val fy = h + 4.5f
                for (k in 0 until 5) leaf(tx, fy, PIF / 2 + k * TAU / 5, 4.6f, 4f, 0f, whitePal, PETAL)
                disc(tx, fy, 1.6f, 1.6f, bloomPals[3])
            }
        }
    }

    // endregion

    // region Cactus

    /**
     * A ribbed cactus body, row by row. [width] and [center] give half width and center x per
     * row index (0 = bottom); cells not covered by the neighbor rows get an outline.
     */
    private inline fun ribbed(
        y0: Int, rows: Int, p: Pal, ribs: Int, spines: Int,
        width: (Int) -> Float, center: (Int) -> Float,
    ) {
        for (j in 0 until rows) {
            val w = width(j)
            if (w < 0.5f) continue
            val cx = center(j)
            val wa = if (j + 1 < rows) width(j + 1) else 0f
            val ca = center(j + 1)
            val wb = if (j > 0) width(j - 1) else Float.MAX_VALUE
            val cb = center(j - 1)
            val y = y0 + j
            for (x in floor(cx - w).toInt()..floor(cx + w).toInt()) {
                val c = x + 0.5f
                val d = c - cx
                if (abs(d) > w) continue
                val u = d / w
                val edge = abs(d) > w - 1f || abs(c - ca) > wa || abs(c - cb) > wb
                if (edge) {
                    r.set(x, y, p.outline)
                    if (abs(d) > w - 1f && (y + spines) % 4 == 0 && wa > 0f) {
                        val out = if (d > 0) 1 else -1
                        r.set(x + out, y, SPINE)
                        if ((y + spines) % 8 == 0) r.set(x + out * 2, y + 1, SPINE)
                    }
                    continue
                }
                val phase = (u + 1f) * 0.5f * ribs
                val f = frac(phase)
                var col = when {
                    u < -0.45f -> p.light
                    u > 0.4f -> p.dark
                    else -> p.mid
                }
                if (f < 0.17f && abs(u) < 0.88f) col = if (u < -0.45f) p.mid else p.dark
                else if (f in 0.45f..0.62f && u < 0.35f) {
                    col = if (col == p.light) p.hi else p.light
                    if ((y + floor(phase).toInt() * 3 + spines) % 5 == 0) col = SPINE
                }
                r.set(x, y, col)
            }
        }
    }

    private fun dome(j: Int, rows: Int, half: Float, cap: Float): Float {
        if (j < 0 || j >= rows) return 0f
        val yy = j + 0.5f
        if (yy < rows - cap) return half
        val k = (yy - (rows - cap)) / cap
        return half * sqrt(max(0f, 1f - k * k))
    }

    private fun drawCactus(m: Float, seed: Int, pose: PlantPose) {
        when ((hash01(seed, 7) * 4f).toInt()) {
            0 -> drawSaguaro(m, seed, pose)
            1 -> drawBarrel(m, seed, pose)
            2 -> drawPricklyPear(m, seed, pose)
            else -> drawCluster(m, seed, pose)
        }
    }

    private fun flowersOn(seed: Int) = hash01(seed, 8) < 0.75f
    private fun flowerPal(seed: Int) = bloomPals[(hash01(seed, 9) * bloomPals.size).toInt()]

    private fun drawSaguaro(m: Float, seed: Int, pose: PlantPose) {
        val scaleY = 1f + pose.squash * 0.6f
        val hw = if (m < 0.3f) 3.5f + m * 6f else 5.3f + (m - 0.3f) * (3f + hash01(seed, 10) * 2f)
        val h = ((8f + m * (30f + hash01(seed, 11) * 8f) + pose.stretch * 0.5f * K) * scaleY).coerceAtLeast(4f)
        val rows = h.toInt()
        val sway = (pose.sway * 0.35f + pose.lean * 0.4f) * K
        val droop = pose.droop * 2.5f * K
        val cap = min(hw, h * 0.5f)

        // Arms: one to three, positions and lengths from the seed
        val arms = 1 + (hash01(seed, 12) * 3f).toInt()
        var side = if (hash01(seed, 13) < 0.5f) 1 else -1
        for (a in 0 until arms) {
            val start = 0.4f + a * 0.14f
            if (m <= start) continue
            val grow = ((m - start) / 0.3f).coerceIn(0f, 1f)
            val attach = h * (0.32f + hash01(seed, 20 + a) * 0.3f)
            val armHw = (2.4f + grow * 1.6f + m * 0.6f).coerceAtMost(hw * 0.75f)
            val out = hw + 1.5f + grow * (3f + hash01(seed, 30 + a) * 3f)
            val up = grow * (8f + hash01(seed, 40 + a) * 10f)
            val ft = attach / h
            val bx = sway * ft.pow(1.6f) + droop * ft * ft
            val ax = bx + side * out
            val wave = (sin(pose.time * 1.7f + side + a) * pose.flutter * 1.5f + pose.flap * 0.6f - pose.droop * 3f) * K * 0.3f
            point(bx, attach, armHw)
            point(ax, attach, armHw)
            tube(cactusPal)
            if (up >= 1f) {
                val y0 = floor(attach - armHw * 0.6f).toInt()
                val armRows = (up + armHw * 1.6f).toInt()
                val capA = min(armHw, armRows * 0.5f)
                ribbed(y0, armRows, cactusPal, 3, seed and 3,
                    width = { j -> dome(j, armRows, armHw, capA) },
                    center = { j -> ax + wave * side * (j / max(1f, up)).coerceIn(0f, 1f).pow(2f) })
                if (flowersOn(seed) && m > 0.9f && a == 0) {
                    blossom(ax + wave * side, y0 + armRows - 0.5f, 3.5f, flowerPal(seed), (m - 0.9f) / 0.1f)
                }
            }
            side = -side
        }
        ribbed(0, rows, cactusPal, 5, (seed ushr 3) and 7,
            width = { j -> dome(j, rows, hw, cap) },
            center = { j -> val t = (j / h).coerceIn(0f, 1f); sway * t.pow(1.6f) + droop * t * t })
        if (flowersOn(seed) && m > 0.85f) {
            val open = ((m - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val bob = sin(pose.time * 2f) * 0.4f
            blossom(sway + droop, rows - 1f + bob, 4.5f, flowerPal(seed), open)
        }
    }

    private fun drawBarrel(m: Float, seed: Int, pose: PlantPose) {
        val scaleY = 1f + pose.squash * 0.5f
        val rad = 4f + m * (9f + hash01(seed, 10) * 3f)
        val tall = 0.85f + hash01(seed, 11) * 0.35f
        val rows = (rad * 1.9f * tall * scaleY).toInt().coerceAtLeast(3)
        val cyc = rows * 0.5f
        val ry = rows * 0.52f
        val sway = pose.sway * 0.15f * K
        ribbed(0, rows, cactusPal2, 7, (seed ushr 2) and 7,
            width = { j ->
                if (j < 0 || j >= rows) 0f else {
                    val k = (j + 0.5f - cyc) / ry
                    rad * sqrt(max(0f, 1f - k * k)) * (if (j < 2) 0.92f else 1f)
                }
            },
            center = { j -> sway * (j / rows.toFloat()) })
        // Woolly crown and a ring of flowers
        if (m > 0.5f) {
            for (k in -2..2) dot(sway + k * 1.2f, rows - 1.5f + (2 - abs(k)) * 0.3f, SPINE)
        }
        if (flowersOn(seed) && m > 0.82f) {
            val open = ((m - 0.82f) / 0.18f).coerceIn(0f, 1f)
            val n = 1 + (hash01(seed, 14) * 3f).toInt()
            for (k in 0 until n) {
                val off = if (n == 1) 0f else (k / (n - 1f) - 0.5f) * rad * 0.9f
                blossom(sway + off, rows - 1.5f - abs(off) * 0.25f, 3.6f, flowerPal(seed), open, -off * 0.04f)
            }
        }
    }

    private fun pad(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float, p: Pal, seed: Int) {
        val ca = cos(angle); val sa = sin(angle)
        val ext = max(rx, ry) + 1.6f
        for (y in floor(cy - ext).toInt()..floor(cy + ext).toInt()) {
            for (x in floor(cx - ext).toInt()..floor(cx + ext).toInt()) {
                val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                // v along the pad's long axis, u across
                val v = dx * ca + dy * sa
                val u = -dx * sa + dy * ca
                val e = (u / rx) * (u / rx) + (v / ry) * (v / ry)
                if (e <= 1f) {
                    val s = (u / rx) * 0.8f * (if (ca >= 0f) -1f else 1f) + v / ry * 0.35f
                    var col = when {
                        s > 0.5f -> p.hi
                        s > 0.15f -> p.light
                        s < -0.45f -> p.dark
                        else -> p.mid
                    }
                    // areoles on a staggered lattice
                    val gu = u + 50f
                    val gv = v + 50f + (floor(gu / 3f) % 2f) * 1.5f
                    if (frac(gu / 3f) < 0.34f && frac(gv / 3f) < 0.34f && e < 0.8f) col = if (hash01(x, y, seed) < 0.5f) SPINE else SPINE_DARK
                    r.set(x, y, col)
                } else {
                    val e2 = (u / (rx + 1f)) * (u / (rx + 1f)) + (v / (ry + 1f)) * (v / (ry + 1f))
                    if (e2 <= 1f) r.set(x, y, p.outline)
                }
            }
        }
    }

    // Pad tree scratch
    private val padX = FloatArray(8)
    private val padY = FloatArray(8)
    private val padA = FloatArray(8)
    private val padR = FloatArray(8)

    private fun drawPricklyPear(m: Float, seed: Int, pose: PlantPose) {
        val rate = 3f + hash01(seed, 10) * 3f
        val n = (1 + (m * rate).toInt()).coerceAtMost(7)
        val scale = 0.55f + m * 0.45f
        val wind = (pose.sway + pose.lean) * 0.03f
        // Pads stand on the rims of earlier pads; angle is measured from vertical.
        for (i in 0 until n) {
            val newest = i == n - 1 && m < 1f
            val grow = if (newest) (frac(m * rate) * 1.3f).coerceIn(0.35f, 1f) else 1f
            val ry = (6f + hash01(seed, 50 + i) * 3f) * scale * grow * (if (i == 0) 1.15f else 0.9f)
            padR[i] = ry
            if (i == 0) {
                padA[i] = (hash01(seed, 60) - 0.5f) * 0.3f + wind - pose.droop * 0.3f
                padX[i] = -sin(padA[i]) * ry
                padY[i] = cos(padA[i]) * ry
            } else {
                val parent = (hash01(seed, 70 + i) * i).toInt()
                val side = if (hash01(seed, 80 + i) < 0.5f) -1f else 1f
                val pa = padA[parent]
                val tilt = pa - side * (0.35f + hash01(seed, 90 + i) * 0.4f) + wind * (i + 1) + pose.droop * 0.5f * side
                val rr = padR[parent]
                val ax = padX[parent] - sin(pa - side * 0.7f) * rr * 0.9f
                val ay = padY[parent] + cos(pa - side * 0.7f) * rr * 0.9f
                padA[i] = tilt
                padX[i] = ax - sin(tilt) * ry * 0.85f
                padY[i] = ay + cos(tilt) * ry * 0.85f
            }
        }
        for (i in 0 until n) {
            val p = if (i == n - 1 && m < 1f) cactusPal2 else padPal
            pad(padX[i], padY[i], padR[i] * 0.66f, padR[i], padA[i] + PIF / 2, p, seed + i)
        }
        if (flowersOn(seed) && m > 0.85f) {
            val open = ((m - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val fp = flowerPal(seed)
            for (i in 0 until n) {
                if (hash01(seed, 100 + i) > 0.6f) continue
                val fx = padX[i] - sin(padA[i]) * padR[i]
                val fy = padY[i] + cos(padA[i]) * padR[i]
                blossom(fx, fy - 1f, 3.2f, fp, open, padA[i] * 0.5f)
            }
        }
    }

    private fun drawCluster(m: Float, seed: Int, pose: PlantPose) {
        val count = 3 + (hash01(seed, 10) * 3f).toInt()
        val scaleY = 1f + pose.squash * 0.5f
        val sway = pose.sway * 0.25f * K
        // Back row first
        for (pass in 0 until 2) {
            for (i in 0 until count) {
                val back = hash01(seed, 20 + i) > 0.45f
                if ((pass == 0) != back) continue
                val start = i * 0.12f
                if (m < start) continue
                val g = ((m - start) / (1f - start)).coerceIn(0f, 1f)
                val off = (i - (count - 1) / 2f) * (5.5f + hash01(seed, 30) * 2f) + (hash01(seed, 40 + i) - 0.5f) * 3f
                val hw = (2.5f + g * (2.5f + hash01(seed, 50 + i) * 2f)) * (if (back) 1f else 0.85f)
                val rows = ((4f + g * (8f + hash01(seed, 60 + i) * (if (back) 22f else 10f))) * scaleY).toInt().coerceAtLeast(3)
                val cap = min(hw, rows * 0.5f)
                val baseY = if (back) 0 else -1
                ribbed(baseY, rows, if (i % 2 == 0) cactusPal else cactusPal2, 3, (seed + i) and 7,
                    width = { j -> dome(j, rows, hw, cap) },
                    center = { j -> off + sway * (j / 30f) * (j / 30f) })
                if (flowersOn(seed) && m > 0.85f && hash01(seed, 70 + i) < 0.5f) {
                    blossom(off + sway * (rows / 30f) * (rows / 30f), baseY + rows - 1f, 3f, flowerPal(seed + i), (m - 0.85f) / 0.15f)
                }
            }
        }
    }

    // endregion

    // region Fern

    private fun drawFern(m: Float, seed: Int, pose: PlantPose) {
        val scaleY = 1f + pose.squash * 0.8f
        val grown = floor(m * 5).toInt()
        val count = 2 + grown
        val newest = if (m >= 1f) 1f else m * 5 - grown
        val maxLen = 10f + m * 36f + pose.stretch * K
        var youngest = 0
        for (i in 0 until count) if (abs(slot(i, count, seed)) < abs(slot(youngest, count, seed))) youngest = i
        // Outer fronds first so inner ones overlap them
        for (rank in 0 until count) {
            val i = byRank(rank, count) { abs(slot(it, count, seed)) }
            val young = i == youngest
            val unroll = if (young) newest.coerceIn(0.2f, 1f) else 1f
            val variation = 0.8f + hash01(seed, 200 + i) * 0.2f
            val s = slot(i, count, seed)
            val len = maxLen * variation * (0.7f + 0.3f * (1 - abs(s) / 60f)) * (if (young) unroll else 1f)
            val flutter = sin(pose.time * (2.2f + i * 0.3f) + i) * pose.flutter * 5f
            drawFrond(s + flutter, len, unroll, m, pose, scaleY)
        }
        disc(0f, 0.5f, 2f + m * 1.5f, 1.2f, stemPal)
    }

    private fun slot(i: Int, count: Int, seed: Int): Float {
        val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
        return spread * 58f + (hash01(seed, 300 + i) - 0.5f) * 10f
    }

    private fun drawFrond(angleDeg: Float, length: Float, unroll: Float, m: Float, pose: PlantPose, scaleY: Float) {
        val side = if (angleDeg >= 0) 1 else -1
        val lean = abs(angleDeg) / 90f
        val total = -side * (0.2f + lean * 1.5f) * (1f + pose.droop * 1.3f) + side * pose.flap * 0.2f - (pose.sway + pose.lean) * 0.08f
        val h0 = PIF / 2 - angleDeg * PIF / 180f
        val visible = length * unroll
        var x = 0f
        var y = 1f
        var s = 0f
        val ds = 0.5f
        var nextPinna = 2.5f
        pn = 0
        while (s < visible) {
            val t = s / max(length, 1f)
            val h = h0 + total * t.pow(1.5f)
            x += cos(h) * ds
            y += sin(h) * ds * scaleY
            s += ds
            if (pn < px.size) { px[pn] = x; py[pn] = y; pn++ }
            if (s >= nextPinna) {
                nextPinna += 2.3f
                val taper = max(0f, sin(PIF * (0.14f + 0.86f * t))).pow(0.8f)
                val plen = (1.2f + m * 6f) * taper
                if (plen < 0.8f) continue
                for (k in -1..1 step 2) {
                    var a = h + k * 1.0f - pose.droop * 0.5f * k * side
                    val upper = sin(a) > sin(h - k * 1.0f)
                    val main = if (upper) fernPal.light else fernPal.mid
                    val tip = if (upper) fernPal.hi else fernPal.dark
                    val under = if (upper) fernPal.mid else fernPal.dark
                    var qx = x; var qy = y
                    var d = 0f
                    while (d < plen) {
                        qx += cos(a) * 0.5f; qy += sin(a) * 0.5f * scaleY
                        d += 0.5f
                        dot(qx, qy, if (d > plen - 1f) tip else main)
                        if (plen > 3f && d < plen * 0.7f) {
                            dot(qx + sin(a) * k * 0.8f, qy - cos(a) * k * 0.8f, under)
                        }
                        a -= k * 0.02f
                    }
                }
            }
        }
        // Rachis on top
        for (i in 0 until pn) {
            dot(px[i], py[i], stemPal.dark)
            if (i < pn / 3) dot(px[i] - 0.7f, py[i], stemPal.mid)
        }
        pn = 0
        if (unroll < 1f) {
            // Fiddlehead curl at the tip
            val rad = 1.8f + m * 1.6f
            val cxF = x + side * rad * 0.6f
            val cyF = y + rad * 0.6f
            var a = 0f
            while (a < TAU * 1.3f) {
                val rr = rad * (1f - a / (TAU * 1.6f))
                val ang = -side * a + (if (side > 0) PIF else 0f)
                dot(cxF + cos(ang) * rr, cyF + sin(ang) * rr, if (a < TAU * 0.5f) fernPal.mid else fernPal.light)
                a += 0.25f
            }
        }
    }

    // endregion

    // region Sunflower

    private fun drawSunflower(m: Float, seed: Int, pose: PlantPose) {
        val first = if (hash01(seed, 1) < 0.5f) 1 else -1
        val scaleY = 1f + pose.squash
        val h = ((8f + m * 44f + pose.stretch * K) * scaleY).coerceAtLeast(5f)
        val night = 1f - pose.daylight
        val open = m >= 0.7f
        val headR = if (m < 0.85f) 5.5f else 5.5f + (m - 0.85f) / 0.15f * 2.5f
        val face = pose.sunDirection * pose.daylight
        val hang = (night * 0.7f + pose.droop * 1.1f) * headR * 1.3f
        val tx = bend(h, h, pose)
        val headX = tx + face * 2.5f
        val headY = h + (if (open) headR * 0.55f - hang else 0f)

        var y = 0f
        while (true) {
            point(bend(y, h, pose), y, 0.8f + m * 0.7f * (1f - y / h * 0.5f))
            if (y >= h) break
            y = min(h, y + 2f)
        }
        if (open) point(headX, headY, 1f)
        tube(stemPal)

        var node = 0
        var ly = 7f * scaleY
        val step = max(6f, 10f * scaleY)
        while (ly <= h - 10f) {
            val side = if (node % 2 == 0) first else -first
            val age = ((h - ly) / (20f * scaleY)).coerceIn(0f, 1f)
            val len = 4f + age * (4f + m * 7f)
            val fl = sin(pose.time * (2.4f + node * 0.3f) + node) * pose.flutter
            val elev = 0.4f + (pose.flap + fl - pose.droop * 2.5f) * 0.3f
            leaf(bend(ly, h, pose) + side * 0.6f, ly, dir(side, elev), len, len * 0.6f, -side * (0.6f + pose.droop), leafPal, OVATE, MIDRIB)
            node++
            ly += step
        }

        when {
            m < 0.5f -> {
                val c = 2.5f + m * 5f
                leaf(tx, h - 0.5f, dir(-1, 1.0f + pose.flap * 0.3f), c, c * 0.6f, 0.3f, youngPal, OVATE, MIDRIB)
                leaf(tx, h - 0.5f, dir(1, 1.0f + pose.flap * 0.3f), c, c * 0.6f, -0.3f, youngPal, OVATE, MIDRIB)
            }
            m < 0.7f -> {
                val g = (m - 0.5f) / 0.2f
                val br = 2f + g * 2.5f
                for (k in 0 until 5) leaf(tx, h + br * 0.6f, PIF * (0.1f + 0.2f * k), br * 1.1f, br * 0.6f, 0f, stemPal, PETAL)
                disc(tx, h + br * 0.9f, br, br * 0.9f, youngPal)
                if (g > 0.7f) dot(tx, h + br * 1.8f, sunPal.light)
            }
            else -> {
                val sx = 1f - 0.3f * abs(face)
                val sy = 1f - 0.4f * night - 0.3f * pose.droop
                val petalLen = (4f + m * 3.5f) * (0.5f + 0.5f * pose.daylight) * (1f - pose.droop * 0.4f)
                val n = 13
                // back layer, offset by half a petal
                for (k in 0 until n) {
                    val a = (k + 0.5f) * TAU / n
                    val da = atan2(sin(a) * sy, cos(a) * sx)
                    leaf(headX + cos(a) * headR * 0.75f * sx, headY + sin(a) * headR * 0.75f * sy, da, petalLen * 1.05f, 3.8f, 0f, sunBackPal, PETAL)
                }
                for (k in 0 until n) {
                    val a = k * TAU / n + 0.1f
                    val da = atan2(sin(a) * sy, cos(a) * sx)
                    leaf(headX + cos(a) * headR * 0.8f * sx, headY + sin(a) * headR * 0.8f * sy, da, petalLen, 3.6f, 0f, sunPal, PETAL, MIDRIB)
                }
                headDisc(headX + face * headR * 0.2f, headY - night * headR * 0.15f, headR * sx, headR * sy)
            }
        }
    }

    private fun headDisc(dcx: Float, dcy: Float, rx: Float, ry: Float) {
        for (cy in floor(dcy - ry - 1.5f).toInt()..floor(dcy + ry + 1.5f).toInt()) {
            for (cx in floor(dcx - rx - 1.5f).toInt()..floor(dcx + rx + 1.5f).toInt()) {
                val u = (cx + 0.5f - dcx) / rx
                val v = (cy + 0.5f - dcy) / ry
                val e = u * u + v * v
                if (e > 1f) {
                    val u2 = (cx + 0.5f - dcx) / (rx + 1f)
                    val v2 = (cy + 0.5f - dcy) / (ry + 1f)
                    if (u2 * u2 + v2 * v2 <= 1f) r.set(cx, cy, discPal.outline)
                    continue
                }
                val lit = -0.6f * u + 0.8f * v
                var col = if (((cx + cy) and 1) == 0) discPal.dark else discPal.mid
                if (e > 0.62f) col = if (lit > 0f) discPal.light else discPal.dark
                else if (e < 0.12f) col = discPal.light
                if (lit > 0.55f && e > 0.25f) col = discPal.hi
                r.set(cx, cy, col)
            }
        }
    }

    // endregion

    // region Tulips

    private fun drawTulips(m: Float, seed: Int, pose: PlantPose) {
        val count = 3 + (hash01(seed, 3) * 3f).toInt()
        val scaleY = 1f + pose.squash
        val leafLen = 9f + m * 20f
        for (k in 0 until 2) {
            val side = if (k == 0) -1 else 1
            val fl = sin(pose.time * 2f + side) * pose.flutter
            leaf(side * 2f, 0f, PIF / 2 - side * (0.35f + hash01(seed, 400 + k) * 0.2f), leafLen * 0.85f, 4f + m * 3f,
                -side * (0.4f + pose.droop * 1.5f + fl * 0.3f - pose.flap * 0.15f), leafPal, STRAP, MIDRIB)
        }
        // Tallest first so shorter ones stand in front
        for (rank in 0 until count) {
            val i = byRank(rank, count) { hash01(seed, 410 + it) }
            val center = i - (count - 1) / 2f
            val hf = 0.68f + hash01(seed, 410 + i) * 0.32f
            val mi = (m * 1.08f - i * 0.03f).coerceIn(0f, 1f)
            val h = ((8f + mi * 32f * hf + pose.stretch * K) * scaleY).coerceAtLeast(4f)
            val spread = center * (4.5f + m * 4.5f) + (hash01(seed, 420 + i) - 0.5f) * 2.5f
            val ownSway = sin(pose.time * (1.3f + i * 0.21f) + i * 2f) * pose.flutter * 0.6f
            val x0 = center * 1.2f
            var y = 0f
            var topX = x0
            while (true) {
                val t = y / h
                topX = x0 + (spread - x0) * t.pow(0.8f) + bend(y, h, pose) + ownSway * K * t * t
                point(topX, y, 0.65f)
                if (y >= h) break
                y = min(h, y + 2.5f)
            }
            tube(stemPal)
            val p = tulipPals[(hash01(seed, 430 + i) * tulipPals.size).toInt()]
            tulipHead(topX, h, mi, p, pose, ownSway * 0.15f + (topX - x0) * 0.01f)
        }
        val fs = if (hash01(seed, 5) < 0.5f) -1 else 1
        leaf(-fs * 1f, 0f, PIF / 2 + fs * 0.25f, leafLen * 0.7f, 4.5f + m * 2.5f, fs * (0.5f + pose.droop * 1.5f), leafPal, STRAP, MIDRIB)
    }

    private fun tulipHead(cx: Float, top: Float, m: Float, p: Pal, pose: PlantPose, tilt: Float) {
        val up = PIF / 2 - tilt
        if (m < 0.6f) {
            val bud = if (m < 0.4f) youngPal else p
            leaf(cx, top - 0.5f, up, 3f + m * 9f, 2f + m * 5f, 0f, bud, PETAL)
            return
        }
        val grown = ((m - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val open = (pose.daylight * grown - pose.droop).coerceIn(0f, 1f)
        val cupH = 6.5f + grown * 3f
        val cupW = 5.5f + grown * 2.2f
        // back petal, inside of the cup, side petals, front petal
        leaf(cx, top - 0.5f, up, cupH * 1.02f, cupW * 0.9f, 0f, p, ROUND)
        if (open > 0.35f) {
            disc(cx, top + cupH * 0.6f, cupW * 0.32f * open + 0.4f, cupH * 0.22f, p)
            for (k in -1..1) dot(cx + k * 1.2f, top + cupH * 0.65f + (1 - abs(k)) * 0.8f, STAMEN)
        }
        val spread = 0.1f + open * 0.28f
        leaf(cx - 0.8f, top - 0.5f, up + spread, cupH, cupW * 0.62f, open * 0.35f, p, PETAL)
        leaf(cx + 0.8f, top - 0.5f, up - spread, cupH, cupW * 0.62f, -open * 0.35f, p, PETAL)
        leaf(cx, top - 0.8f, up, cupH * (0.92f - open * 0.12f), cupW * (0.7f - 0.12f * open), 0f, p, PETAL, MIDRIB)
    }

    // endregion

    // region Monstera

    private fun drawMonstera(m: Float, seed: Int, pose: PlantPose) {
        val grown = floor(m * 4).toInt()
        val count = 1 + grown + (if (m > 0.7f && hash01(seed, 4) < 0.5f) 1 else 0)
        val newest = if (m >= 1f) 1f else m * 4 - grown
        var youngest = 0
        for (i in 0 until count) if (abs(mSlot(i, count, seed)) < abs(mSlot(youngest, count, seed))) youngest = i
        for (rank in 0 until count) {
            val i = byRank(rank, count) { abs(mSlot(it, count, seed)) }
            val slot = mSlot(i, count, seed)
            val young = i == youngest && m < 1f
            val growth = if (young) newest else 1f
            val side = if (slot > 0f || (slot == 0f && hash01(seed, 5) < 0.5f)) 1 else -1
            val lean = abs(slot) / 90f
            val len = (8f + m * 28f) * (0.75f + hash01(seed, 500 + i) * 0.25f) * (0.6f + 0.4f * growth) * (1f - abs(slot) / 220f)
            val h0 = PIF / 2 - slot * PIF / 180f * 0.85f
            val total = -side * (0.15f + lean * 0.5f) * (1f + pose.droop * 1.5f) + side * pose.flap * 0.12f
            val wind = ((pose.sway + pose.lean) * 0.6f + sin(pose.time * (1.8f + i * 0.4f) + i) * pose.flutter * 1.5f) * K
            var x = 0f; var y = 0f; var s = 0f; var h = h0
            var endX = 0f; var endY = 0f
            point(x, y, 0.7f)
            while (s < len) {
                val t = s / len
                h = h0 + total * t * t
                x += cos(h) * 2f
                y += sin(h) * 2f
                s += 2f
                endX = x + wind * t * t
                endY = y
                point(endX, endY, 0.7f)
            }
            tube(stemPal)
            val sz = growth * (0.45f + 0.55f * m)
            val leafLen = 5f + sz * 17f
            val rolled = young && growth < 0.35f
            val angle = h - side * (0.5f + pose.droop * 0.7f - pose.flap * 0.12f)
            if (rolled) {
                leaf(endX, endY, angle, leafLen, leafLen * 0.32f, -side * 0.2f, youngPal, PETAL, MIDRIB)
            } else {
                val p = if (young && growth < 0.6f) youngPal else monsteraPal
                monsteraLeaf(endX, endY, angle, leafLen, leafLen * 0.92f, p, sz > 0.45f, hash01(seed, 510 + i))
            }
        }
    }

    private val mask = BooleanArray(MASK * MASK)

    /**
     * Heart shaped monstera leaf with see-through slits between the side veins and a few holes
     * near the midrib. Rasterized into a mask first so slits and holes get proper outlines.
     */
    private fun monsteraLeaf(x0: Float, y0: Float, angle: Float, len: Float, width: Float, p: Pal, split: Boolean, phase: Float) {
        val ca = cos(angle); val sa = sin(angle)
        val mx = x0 + ca * len * 0.42f
        val my = y0 + sa * len * 0.42f
        val ext = min((MASK - 3) / 2f, len * 0.6f + width * 0.5f + 1f)
        val bx = floor(mx - ext).toInt() - 1
        val by = floor(my - ext).toInt() - 1
        val n = min(MASK, (ext * 2).toInt() + 3)
        val period = max(6f, len * 0.3f)
        val lit = if (-0.6f * -sa + 0.8f * ca >= 0f) 1f else -1f
        for (j in 0 until n) for (i in 0 until n) {
            val dx = bx + i + 0.5f - x0
            val dy = by + j + 0.5f - y0
            val pp = dx * ca + dy * sa
            val q = -dx * sa + dy * ca
            val t = pp / len
            var inside = false
            if (t > -0.16f && t < 1f) {
                val b = (t + 0.16f) / 1.16f
                val hw = width * 0.5f * max(0f, sin(PIF * b.pow(0.66f))).pow(0.7f)
                val aq = abs(q)
                inside = aq <= hw && !(t < 0.04f && aq < (0.04f - t) * width * 1.3f)
                if (inside && split && t > 0.08f && t < 0.9f) {
                    val f = frac((pp - aq * 0.6f) / period + phase)
                    if (aq > hw * 0.4f && f < 2.6f / period) inside = false
                    if (aq > hw * 0.18f && aq < hw * 0.34f && f > 0.55f && f < 0.55f + 2.2f / period && t < 0.75f) inside = false
                }
            }
            mask[j * MASK + i] = inside
        }
        for (j in 0 until n) for (i in 0 until n) {
            val x = bx + i; val y = by + j
            if (mask[j * MASK + i]) {
                val dx = x + 0.5f - x0; val dy = y + 0.5f - y0
                val pp = dx * ca + dy * sa
                val q = (-dx * sa + dy * ca) * lit
                val t = pp / len
                val b = (t + 0.16f) / 1.16f
                val hw = max(0.5f, width * 0.5f * max(0f, sin(PIF * b.pow(0.66f))).pow(0.7f))
                val qq = q / hw
                var col = when {
                    qq > 0.25f -> p.light
                    qq < -0.45f -> p.dark
                    else -> p.mid
                }
                if (qq > 0.45f && qq < 0.8f && t > 0.2f && t < 0.55f) col = p.hi
                val f = frac((pp - abs(q) * 0.6f) / period + phase)
                if (abs(q) < 0.55f && t > 0f && t < 0.92f) col = p.vein
                else if (split && abs(f - 0.7f) < 0.6f / period && abs(q) > 1f && abs(qq) < 0.85f) col = if (qq > 0f) p.vein else p.light
                r.set(x, y, col)
            } else {
                val e = (i > 0 && mask[j * MASK + i - 1]) || (i < n - 1 && mask[j * MASK + i + 1]) ||
                    (j > 0 && mask[(j - 1) * MASK + i]) || (j < n - 1 && mask[(j + 1) * MASK + i])
                if (e) r.set(x, y, p.outline)
            }
        }
    }

    private fun mSlot(i: Int, count: Int, seed: Int): Float {
        val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
        return spread * 58f + (hash01(seed, 520 + i) - 0.5f) * 14f
    }

    // endregion

    // region Bonsai

    private val tx = FloatArray(8)
    private val ty = FloatArray(8)
    private var segs = 0
    private var trunkSway = 0f
    private var trunkDroop = 0f

    /** Point on the angular trunk at fraction [f]: x when [wantX], else y. */
    private fun trunk(f: Float, wantX: Boolean): Float {
        val pos = f.coerceIn(0f, 1f) * segs
        val i = min(segs - 1, pos.toInt())
        val t = pos - i
        return if (wantX) tx[i] + (tx[i + 1] - tx[i]) * t + trunkSway * f.pow(1.6f) + trunkDroop * f * f
        else ty[i] + (ty[i + 1] - ty[i]) * t
    }

    private fun drawBonsai(m: Float, seed: Int, pose: PlantPose) {
        val scaleY = 1f + pose.squash * 0.5f
        val h = ((8f + m * 26f + pose.stretch * 0.4f * K) * scaleY).coerceAtLeast(4f)
        segs = 3 + (hash01(seed, 600) * 2.5f).toInt() // 3..5 angular segments
        val lean = (hash01(seed, 601) - 0.5f) * 1.1f
        val breathe = sin(pose.time * 1.3f) * 0.35f
        trunkSway = (pose.sway * 0.3f + pose.lean * 0.5f) * K
        trunkDroop = pose.droop * 4f * K
        // Angular trunk: each segment kinks the other way
        var x = 0f; var y = 0f
        var a = PIF / 2 - lean * 0.5f
        tx[0] = 0f; ty[0] = 0f
        var sign = if (hash01(seed, 602) < 0.5f) 1f else -1f
        for (i in 1..segs) {
            val segLen = h / segs * (0.8f + hash01(seed, 610 + i) * 0.4f)
            a += sign * (0.35f + hash01(seed, 620 + i) * 0.5f) * (0.4f + 0.6f * m)
            a = a.coerceIn(0.5f, PIF - 0.5f)
            x = (x + cos(a) * segLen).coerceIn(-16f, 16f)
            y += sin(a) * segLen * scaleY
            tx[i] = x; ty[i] = y
            sign = -sign
        }
        val baseR = 1f + m * 2.6f
        val topR = 0.7f + m * 0.8f
        // Roots grip the soil
        if (m > 0.3f) {
            for (k in -1..1 step 2) {
                point(0f, 1f, baseR * 0.6f)
                point(k * (baseR + 2.5f + m * 2f), 0f, 0.6f)
                tube(barkPal, 3)
            }
        }
        for (i in 0..segs) {
            val f = i.toFloat() / segs
            point(trunk(f, true), trunk(f, false), baseR + (topR - baseR) * f.pow(0.7f))
        }
        tube(barkPal, 3)

        // Branches with pads; count and positions from the seed
        val branches = 2 + (hash01(seed, 630) * 3f).toInt()
        var side = if (hash01(seed, 631) < 0.5f) 1 else -1
        for (b in 0 until branches) {
            val from = 0.25f + b * 0.15f
            if (m <= from) continue
            val grow = ((m - from) / 0.3f).coerceIn(0f, 1f)
            val at = 0.38f + (b + hash01(seed, 640 + b)) / branches * 0.5f
            val bx = trunk(at, true); val by = trunk(at, false)
            val len = 2f + grow * (6f + hash01(seed, 650 + b) * 7f)
            val rise = 0.15f + hash01(seed, 660 + b) * 0.5f
            val kink = (hash01(seed, 670 + b) - 0.5f) * 0.8f
            val mx = bx + side * cos(rise) * len * 0.5f
            val my = by + sin(rise) * len * 0.5f
            val endX = mx + side * cos(rise + kink) * len * 0.5f
            val endY = my + sin(rise + kink) * len * 0.5f + pose.flap * 0.4f
            point(bx, by, 0.6f + m * 0.6f)
            point(mx, my, 0.5f + m * 0.3f)
            point(endX, endY, 0.5f)
            tube(barkPal, 3)
            canopy(endX, endY + 1.2f, grow * (0.55f + hash01(seed, 680 + b) * 0.45f), seed + b * 7, breathe)
            side = -side
        }
        canopy(trunk(1f, true), trunk(1f, false) + 1.5f, 0.35f + m * 0.75f, seed, breathe)
    }

    /** A cloud-like foliage pad made of a few lumps placed by the seed. */
    private fun canopy(cx: Float, cy: Float, size: Float, seed: Int, breathe: Float) {
        if (size <= 0.05f) return
        val rx = 2.5f + size * 7f + breathe
        val ry = 1.8f + size * 3.4f
        val lumps = 3 + (hash01(seed, 700) * 3f).toInt()
        lump(cx, cy, rx, ry)
        for (k in 0 until lumps) {
            val ang = PIF * (0.1f + 0.8f * hash01(seed, 710 + k))
            val dist = 0.45f + hash01(seed, 720 + k) * 0.35f
            val lx2 = cx + cos(ang) * rx * dist * (if (k % 2 == 0) 1f else -1f)
            val ly2 = cy + sin(ang) * ry * dist * 0.6f - ry * 0.2f
            lump(lx2, ly2, rx * (0.4f + hash01(seed, 730 + k) * 0.25f), ry * (0.55f + hash01(seed, 740 + k) * 0.25f))
        }
        lump(cx + (hash01(seed, 750) - 0.5f) * rx * 0.5f, cy + ry * 0.45f, rx * 0.55f, ry * 0.65f)
        blob(canopyPal, 11 + (seed and 7), cx, cy)
    }

    // endregion

    // region Flytrap

    private fun drawFlytrap(m: Float, seed: Int, pose: PlantPose) {
        val count = 1 + floor(m * 4).toInt()
        val big = 0.5f + m * 0.6f
        val openness = (0.55f + pose.daylight * 0.45f) * (1f - pose.snap) * (1f - pose.droop)
        var flyX = 0f
        var flyY = 10f
        var bestSlot = Float.MAX_VALUE
        for (rank in 0 until count) {
            val i = byRank(rank, count) { abs(fSlot(it, count, seed)) }
            val slot = fSlot(i, count, seed)
            val side = if (slot >= 0f) 1 else -1
            val len = (6f + m * 12f) * (0.8f + hash01(seed, 800 + i) * 0.2f) * (1f - abs(slot) / 200f)
            val sway = ((pose.sway + pose.lean) * 0.5f + sin(pose.time * 2f + i) * pose.flutter) * 0.05f
            val ang = PIF / 2 - slot * PIF / 180f + sway * K - pose.droop * side * 0.9f
            // Winged petiole
            leaf(0f, 0f, ang, len, 2.2f + m * 1.6f, -side * 0.15f, trapPal, WING_SHAPE, MIDRIB)
            val hx = cos(ang) * len
            val hy = sin(ang) * len
            trap(hx, hy, ang - side * 0.25f, big, openness)
            if (abs(slot) < bestSlot) {
                bestSlot = abs(slot)
                flyX = hx; flyY = hy + 6f * big
            }
        }
        if (pose.fly) {
            val fx = flyX + cos(pose.time * 3.1f) * 11f + sin(pose.time * 7f) * 1.5f
            val fy = flyY + 4f + sin(pose.time * 2.3f) * 5f
            dot(fx, fy, FLY); dot(fx + 1f, fy, FLY)
            if ((pose.time * 20).toInt() % 2 == 0) { dot(fx, fy + 1f, WING); dot(fx + 1f, fy + 1f, WING) }
            else { dot(fx - 1f, fy + 1f, WING); dot(fx + 2f, fy + 1f, WING) }
        }
    }

    private fun fSlot(i: Int, count: Int, seed: Int): Float {
        val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
        return spread * 58f + (hash01(seed, 820 + i) - 0.5f) * 10f
    }

    /** Two lobes hinged at ([hx], [hy]) along [axis], opened by [open] (0 = shut). */
    private fun trap(hx: Float, hy: Float, axis: Float, size: Float, open: Float) {
        val len = 7f * size + 2f
        val wid = 3.6f * size + 1.5f
        val spread = 0.03f + open * 0.24f
        val ext = len + wid + 3f
        val tanS = tan(spread)
        for (y in floor(hy - ext).toInt()..floor(hy + ext).toInt()) {
            for (x in floor(hx - ext).toInt()..floor(hx + ext).toInt()) {
                val dx = x + 0.5f - hx; val dy = y + 0.5f - hy
                var col = 0
                var outline = false
                for (lobe in -1..1 step 2) {
                    val a = axis + lobe * spread
                    val ca = cos(a); val sa = sin(a)
                    val p = dx * ca + dy * sa
                    val q = (-dx * sa + dy * ca) * lobe // q > 0 = outer side
                    val pu = (p - len / 2) / (len / 2)
                    val qv = q / wid
                    if (q >= -0.3f && pu * pu + qv * qv <= 1f) {
                        col = when {
                            q < 0.8f && open > 0.15f -> TRAP_RED
                            qv > 0.6f -> trapPal.dark
                            pu < -0.4f -> trapPal.light
                            else -> trapPal.mid
                        }
                    } else {
                        val pu2 = (p - len / 2) / (len / 2 + 1f)
                        val qv2 = q / (wid + 1f)
                        if (q >= -1.2f && pu2 * pu2 + qv2 * qv2 <= 1f) outline = true
                        // Cilia along the rim
                        if (col == 0 && q < -0.3f && q > -2.4f && p > len * 0.3f && p < len - 0.5f && frac(p / 2f) < 0.35f) col = TRAP_TOOTH
                    }
                }
                if (col == 0 && open > 0.15f) {
                    // dark red inside between the lobes
                    val p = dx * cos(axis) + dy * sin(axis)
                    val q = -dx * sin(axis) + dy * cos(axis)
                    if (p > 0.5f && p < len * 0.95f && abs(q) < p * tanS * 0.9f) col = TRAP_DEEP
                }
                if (col != 0) r.set(x, y, col) else if (outline) r.set(x, y, trapPal.outline)
            }
        }
    }

    // endregion

    // region Pothos

    private fun drawPothosCrown(m: Float, seed: Int, pose: PlantPose) {
        val count = 3 + (m * 9f).toInt()
        val wind = (pose.sway + pose.lean) * 0.04f
        for (rank in 0 until count) {
            // outer leaves first
            val i = if (rank % 2 == 0) rank / 2 else count - 1 - rank / 2
            val spread = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
            val side = if (spread >= 0f) 1 else -1
            val ang = PIF / 2 - spread * 1.15f + wind + (hash01(seed, 900 + i) - 0.5f) * 0.3f - pose.droop * side * 0.6f
            val stalk = (2f + m * 9f) * (0.6f + hash01(seed, 910 + i) * 0.4f) * (1f - abs(spread) * 0.4f)
            val sx = cos(ang) * stalk
            val sy = sin(ang) * stalk
            line(0f, 0.5f, sx, sy, stemPal.mid)
            val fl = sin(pose.time * (2f + i * 0.3f) + i) * pose.flutter * 0.25f
            val len = (3.5f + m * 5.5f) * (0.8f + hash01(seed, 920 + i) * 0.3f)
            val young = i == count / 2 && m < 1f
            leaf(sx, sy, ang - side * (0.4f - pose.flap * 0.1f) + fl - pose.droop * side * 0.6f, len, len * 0.78f, -side * 0.3f,
                if (young) youngPal else pothosPal, HEART, VEINS)
        }
    }

    private fun drawPothosVines(m: Float, seed: Int, pose: PlantPose) {
        val vines = 3 + (hash01(seed, 950) * 4f).toInt() // 3..6
        for (v in 0 until vines) {
            val side = if (v % 2 == 0) -1 else 1
            val start = v * 0.08f
            if (m <= start) continue
            val g = ((m - start) / (1f - start)).coerceIn(0f, 1f)
            val length = 3f + g * (34f + hash01(seed, 960 + v) * 34f)
            val x0 = side * (5f + hash01(seed, 970 + v) * 7f)
            val edge = side * (15f + hash01(seed, 980 + v) * 7f)
            val swayAmp = ((pose.sway + pose.lean) * 0.9f + sin(pose.time * (1.1f + v * 0.17f) + v) * (0.6f + pose.flutter)) * K * 0.5f
            var x = x0; var y = 1.5f
            var s = 0f
            var nextLeaf = 2f
            var leafSide = if (hash01(seed, 990 + v) < 0.5f) 1 else -1
            var leafNo = 0
            pn = 0
            val arc = abs(edge - x0)
            val ds = 0.5f
            while (s < length) {
                s += ds
                if (s < arc) {
                    // over the rim: outwards, a little up, then down
                    val t = s / arc
                    x = x0 + (edge - x0) * t
                    y = 1.5f + sin(t * PIF) * 2.5f - t * 2f
                } else {
                    val d = s - arc
                    val t = d / max(length - arc, 1f)
                    y = -0.5f - d
                    x = edge + side * d * 0.12f + swayAmp * t.pow(1.5f) + sin(d * 0.35f + v) * 0.7f
                }
                if (pn < px.size) { px[pn] = x; py[pn] = y; pn++ }
                if (s >= nextLeaf) {
                    nextLeaf += 4.5f + hash01(seed, v, leafNo) * 2f
                    leafNo++
                    val tipNear = length - s < 9f && g < 1f
                    val ll = if (length - s < 5f) 2.5f else 3f + m * 1.6f
                    val a = if (s > arc) -PIF / 2 + leafSide * (1.15f + pose.flap * 0.1f) else PIF / 2 + leafSide * 0.9f
                    leaf(x + leafSide * 0.5f, y, a, ll, ll * 0.72f, -leafSide * 0.5f, if (tipNear) youngPal else pothosPal, HEART, MIDRIB)
                    leafSide = -leafSide
                }
            }
            for (i in 0 until pn) dot(px[i], py[i], stemPal.dark)
            pn = 0
            // fresh leaf at the tip
            leaf(x, y, -PIF / 2 + side * 0.4f, 2.5f + m, 2f + m * 0.6f, 0f, youngPal, HEART, PLAIN)
        }
    }

    // endregion
}
