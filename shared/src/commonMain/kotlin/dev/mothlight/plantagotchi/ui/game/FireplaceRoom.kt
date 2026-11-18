package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.ui.game.SceneryRenderer.noise
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fireside: a stone chimney with a crackling fire between two windows (rain comes from the game
 * weather), wooden mantel, armchairs, a floor lamp and a rug. Tapping the fire sends sparks.
 */
internal class FireplaceRoom(size: Size, floorTop: Float, pixel: Float) : IndoorBase(size, floorTop, pixel) {
    override val scenery = Scenery.FIREPLACE

    private val beam = px(12)
    val mantel = Rect(snap(w * 0.24f), snap(floorTop - h * 0.2f), snap(w * 0.76f), snap(floorTop - h * 0.2f) + px(10))

    /** The chimney breast below the mantel and the narrower flue above it. */
    val chimney = Rect(snap(w * 0.28f), mantel.top, snap(w * 0.72f), floorTop)
    val flue = Rect(snap(w * 0.34f), beam, snap(w * 0.66f), mantel.top)

    /** Firebox opening: straight sides with a round arch on top. */
    val firebox = Rect(snap(w * 0.38f), snap(floorTop - h * 0.135f), snap(w * 0.62f), floorTop - px(6))
    val hearth = Rect(chimney.left - px(6), floorTop - px(6), chimney.right + px(6), floorTop + px(10))

    val leftGlass = Rect(snap(w * 0.05f), snap(h * 0.2f), snap(w * 0.25f), snap(h * 0.44f))
    val rightGlass = Rect(snap(w * 0.75f), snap(h * 0.2f), snap(w * 0.95f), snap(h * 0.44f))
    override val windows = listOf(leftGlass, rightGlass)
    private val sillTop = leftGlass.bottom + px(6)
    private val hangY = snap(h * 0.33f)
    private val lampX = snap(w * 0.93f)
    private val lampShade = Rect(lampX - px(14), snap(floorTop - h * 0.24f), lampX + px(15), snap(floorTop - h * 0.24f) + px(20))

    /** The fire: tap area. */
    val fire = Rect(firebox.left, firebox.top + px(10), firebox.right, firebox.bottom)

    private val wallParts = around(Rect(0f, 0f, w, floorTop), windows)

    override fun anchor(spot: RoomSpot): Pair<Offset, Float> = when (spot) {
        RoomSpot.SILL_LEFT -> Offset(leftGlass.center.x, sillTop) to w * 0.13f
        RoomSpot.SILL_RIGHT -> Offset(rightGlass.center.x, sillTop) to w * 0.13f
        RoomSpot.SHELF -> Offset(w * 0.36f, mantel.top) to w * 0.11f
        RoomSpot.DESK -> Offset(w * 0.64f, mantel.top) to w * 0.11f
        RoomSpot.HANGING -> Offset(w * 0.5f, hangY) to w * 0.13f
        RoomSpot.FLOOR_LEFT -> Offset(w * 0.25f, floorTop + w * 0.03f) to w * 0.2f
        RoomSpot.FLOOR_RIGHT -> Offset(w * 0.75f, floorTop + w * 0.03f) to w * 0.2f
    }

    override fun toyAt(offset: Offset): String? = if (hit(fire, offset, 6)) IndoorToy.FIRE else null

    override fun draw(scope: DrawScope, tint: Float, time: Float, toys: IndoorToys) = with(scope) {
        beginFrame(tint, Dusk, 0.75f)
        outside(leftGlass, tint, 0)
        outside(rightGlass, tint, 1)
        wall()
        ceiling()
        for (i in 0..1) window(if (i == 0) leftGlass else rightGlass)
        stones(flue, 3)
        stones(chimney, 0)
        firebox(time, toys.sparksAt)
        mantel()
        hanger()
        floor()
        armchair(left = true)
        armchair(left = false)
        floorLamp()
        logBasket()
        sparks(time, toys.sparksAt)
        // Firelight on everything, flickering; stronger at night
        val flare = (1f - ((time - toys.sparksAt) / 1.5f)).coerceIn(0f, 1f)
        val flicker = 0.5f + 0.25f * sin(time * 9f) + 0.15f * sin(time * 23f + 1f) + 0.1f * sin(time * 3.1f)
        val center = Offset(fire.center.x, fire.bottom - px(8))
        val radius = w * (0.75f + 0.05f * flicker + 0.1f * flare)
        drawCircle(
            Brush.radialGradient(listOf(FireGlow.copy(alpha = (0.12f + 0.08f * flicker + 0.22f * night + 0.15f * flare).coerceAtMost(0.6f)), Color.Transparent), center, radius),
            radius, center,
        )
        val lampCenter = Offset(lampShade.center.x, lampShade.bottom)
        val lampRadius = w * 0.35f
        drawCircle(
            Brush.radialGradient(listOf(LampGlow.copy(alpha = 0.1f + 0.25f * night), Color.Transparent), lampCenter, lampRadius),
            lampRadius, lampCenter,
        )
    }

    /** Hills and pines behind the glass; the sky and the rain come from the game. */
    private fun DrawScope.outside(glass: Rect, tint: Float, side: Int) {
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            val hill = SceneryRenderer.shade(Color(0xFF3F6B55), tint)
            val far = SceneryRenderer.shade(Color(0xFF6E8F86), tint)
            val pine = SceneryRenderer.shade(Color(0xFF2A4A3A), tint)
            var x = glass.left
            var k = side * 40
            while (x < glass.right) {
                val farH = snap(glass.height * (0.45f + 0.08f * sin(k * 0.09f)))
                drawRect(far, Offset(x, glass.bottom - farH), Size(p, farH))
                val hillH = snap(glass.height * (0.25f + 0.06f * sin(k * 0.13f + 2f)))
                drawRect(hill, Offset(x, glass.bottom - hillH), Size(p, hillH))
                x += p
                k++
            }
            // A few pines
            for (i in 0 until 3) {
                val px0 = snap(glass.left + glass.width * (0.15f + i * 0.33f + side * 0.08f))
                val base = glass.bottom - snap(glass.height * 0.18f)
                val th = 18 + i * 4
                for (r in 0 until th) {
                    val half = (th - r) / 3
                    drawRect(pine, Offset(px0 - px(half), base - px(r)), Size(px(half * 2 + 1), p))
                }
            }
        }
    }

    private fun DrawScope.wall() {
        fillParts(Plaster, wallParts)
        // Exposed bricks where the plaster is worn
        for (patch in 0..3) {
            val px0 = snap(w * PatchX[patch])
            val py0 = snap(h * PatchY[patch])
            for (r in 0 until 4) {
                val off = if (r % 2 == 0) 0 else 4
                for (b in 0 until 3 - (r % 2)) {
                    val bx = px0 + px(off + b * 9)
                    val by = py0 + px(r * 5)
                    fill(PlasterDark, bx, by, bx + px(9), by + px(5))
                    fill(Brick, bx + p, by + p, bx + px(8), by + px(4))
                }
            }
        }
        // Plaster texture specks
        for (i in 0 until 40) {
            val x = snap(noise(i, 81) * w)
            val y = snap(beam + noise(i, 82) * (floorTop - beam))
            if (x > flue.left - p && x < flue.right) continue
            if (leftGlass.contains(Offset(x, y)) || rightGlass.contains(Offset(x, y))) continue
            box(PlasterDark, x, y, 2, 1)
        }
        // Baseboard
        fill(WoodDark, 0f, floorTop - px(6), chimney.left, floorTop)
        fill(WoodDark, chimney.right, floorTop - px(6), w, floorTop)
        fill(Wood, 0f, floorTop - px(6), w, floorTop - px(5))
        // Small shelf with books under the left window
        val sy = snap(h * 0.53f)
        val sx = snap(w * 0.04f)
        fill(Wood, sx, sy, sx + px(34), sy + px(3))
        fill(WoodDark, sx, sy + px(3), sx + px(34), sy + px(4))
        box(WoodDark, sx + px(4), sy + px(4), 2, 5)
        box(WoodDark, sx + px(28), sy + px(4), 2, 5)
        var bx = sx + px(3)
        for (i in 0 until 4) {
            val bh = 11 + (i * 5) % 6
            box(BookColors[i], bx, sy - px(bh), 4, bh)
            box(Color(0x40FFFFFF), bx, sy - px(bh - 2), 4, 1)
            bx += px(4)
        }
        box(BookColors[4], bx + px(2), sy - px(3), 14, 3)
        box(BookColors[1], bx + px(3), sy - px(6), 12, 3)
        // Picture on the right wall
        val pr = Rect(snap(w * 0.8f), snap(h * 0.5f), snap(w * 0.92f), snap(h * 0.57f))
        fill(WoodDark, pr)
        fill(Color(0xFFE9B97A), pr.left + px(2), pr.top + px(2), pr.right - px(2), pr.bottom - px(2))
        fill(Color(0xFF8E5A3A), pr.left + px(2), pr.top + px(2) + snap(pr.height * 0.5f), pr.right - px(2), pr.bottom - px(2))
        box(Color(0xFFFFE2A8), pr.left + px(6), pr.top + px(5), 4, 4)
    }

    private fun DrawScope.ceiling() {
        fill(WoodDark, 0f, 0f, w, beam)
        fill(Wood, 0f, beam - px(3), w, beam - px(2))
        fill(Color(0x40000000), 0f, beam, w, beam + px(2))
        // Beam ends
        var x = px(10)
        while (x < w) {
            fill(Wood, x, 0f, x + px(14), beam - px(3))
            fill(WoodLight, x, 0f, x + px(14), p)
            x += px(52)
        }
    }

    private fun DrawScope.window(glass: Rect) {
        val f = px(6)
        fill(WoodDark, glass.left - f, glass.top - f, glass.right + f, glass.top)
        fill(WoodDark, glass.left - f, glass.top, glass.left, glass.bottom)
        fill(WoodDark, glass.right, glass.top, glass.right + f, glass.bottom)
        fill(Wood, glass.left - f, glass.top - f, glass.right + f, glass.top - f + p)
        // Cross mullion
        val mx = snap(glass.center.x) - p
        val my = snap(glass.center.y) - p
        fill(WoodDark, mx, glass.top, mx + px(3), glass.bottom)
        fill(WoodDark, glass.left, my, glass.right, my + px(3))
        fill(Wood, mx, glass.top, mx + p, glass.bottom)
        // Sill
        fill(Wood, glass.left - px(9), glass.bottom, glass.right + px(9), sillTop)
        fill(WoodLight, glass.left - px(9), glass.bottom, glass.right + px(9), glass.bottom + p)
        fill(WoodDark, glass.left - px(9), sillTop - p, glass.right + px(9), sillTop)
        fill(Color(0x38000000), glass.left - px(7), sillTop, glass.right + px(9), sillTop + px(3))
        // Condensation at the bottom of the glass
        val fog = Color.White.copy(alpha = 0.10f)
        drawRect(fog, Offset(glass.left, glass.bottom - px(6)), Size(glass.width, px(6)))
    }

    private fun DrawScope.stones(c: Rect, seed: Int) {
        fill(Mortar, c)
        // Rows of rounded stones, staggered, lit from the fire below
        var y = c.top + px(2)
        var row = 0
        while (y < c.bottom - px(4)) {
            val rh = 11 + ((noise(row + seed * 20, 90) * 7f).toInt())
            var x = c.left - px((noise(row + seed * 20, 89) * 16f).toInt())
            var k = 0
            while (x < c.right) {
                val sw = 16 + ((noise(k + row * 11 + seed * 7, 91) * 16f).toInt())
                val left = maxOf(x, c.left)
                val right = minOf(x + px(sw), c.right)
                if (right - left > px(3)) stone(left, y, right, minOf(y + px(rh), c.bottom), k + row * 7 + seed)
                x += px(sw + 2)
                k++
            }
            y += px(rh + 2)
            row++
        }
        // Edges of the breast
        fill(MortarDark, c.left, c.top, c.left + p, c.bottom)
        fill(MortarDark, c.right - p, c.top, c.right, c.bottom)
    }

    private fun DrawScope.stone(l: Float, t: Float, r: Float, b: Float, k: Int) {
        val v = noise(k, 92)
        val body = when {
            v < 0.33f -> StoneA
            v < 0.66f -> StoneB
            else -> StoneC
        }
        fill(body, l + p, t, r - p, b)
        fill(body, l, t + p, r, b - p)
        fill(StoneLight, l + p, t, r - px(2), t + p)
        fill(StoneShadow, l + p, b - p, r - p, b)
        if (v > 0.5f) fill(StoneShadow, l + px(4), t + px(5), l + px(7), t + px(6))
    }

    private fun DrawScope.firebox(time: Float, sparksAt: Float) {
        val fb = firebox
        val archR = fb.width / 2
        val archTop = fb.top
        val straightTop = fb.top + archR
        // Arch of light stones around the opening
        val rim = px(7)
        archRows(fb.left - rim, fb.right + rim, archTop - rim, StoneLight)
        fill(StoneLight, fb.left - rim, straightTop, fb.left, fb.bottom)
        fill(StoneLight, fb.right, straightTop, fb.right + rim, fb.bottom)
        // Voussoir joints
        for (i in 0..8) {
            val a = kotlin.math.PI.toFloat() * i / 8f
            val cx = fb.center.x - kotlin.math.cos(a) * (archR + rim / 2)
            val cy = straightTop - sin(a) * (archR + rim / 2)
            box(MortarDark, snap(cx), snap(cy), 1, 2)
        }
        var jy = straightTop + px(6)
        while (jy < fb.bottom) {
            fill(MortarDark, fb.left - rim, jy, fb.left, jy + p)
            fill(MortarDark, fb.right, jy, fb.right + rim, jy + p)
            jy += px(10)
        }
        // Dark opening with a warm back wall
        archRows(fb.left, fb.right, archTop, Soot)
        fill(Soot, fb.left, straightTop, fb.right, fb.bottom)
        val back = Rect(fb.left + px(6), straightTop - px(4), fb.right - px(6), fb.bottom)
        var by = back.top
        var r = 0
        while (by < back.bottom) {
            var bx = back.left + px(if (r % 2 == 0) 0 else 5)
            while (bx < back.right - px(2)) {
                fill(BackBrick, bx, by, minOf(bx + px(9), back.right), by + px(4))
                bx += px(10)
            }
            by += px(5)
            r++
        }
        // Flames: three layers that waver; tapping makes them flare
        val flare = (1f - ((time - sparksAt) / 1.5f)).coerceIn(0f, 1f)
        val base = fb.bottom - px(9)
        val height = (fb.bottom - straightTop) * (0.95f + 0.35f * flare)
        flame(fb.center.x, base, height, fb.width * 0.36f, time, FlameRed, 0f)
        flame(fb.center.x, base, height * 0.78f, fb.width * 0.28f, time * 1.3f, FlameOrange, 1.7f)
        flame(fb.center.x, base, height * 0.5f, fb.width * 0.17f, time * 1.7f, FlameYellow, 3.1f)
        glow(FlameCore, fb.center.x - px(5), base - px(4), 10, 4)
        // Logs and grate
        val logY = fb.bottom - px(9)
        drawRect(c(Log), Offset(fb.left + px(7), logY), Size(fb.width - px(14), px(5)))
        drawRect(c(LogDark), Offset(fb.left + px(7), logY + px(4)), Size(fb.width - px(14), p))
        line(Log, fb.left + px(10), logY - px(4), fb.right - px(14), logY + px(2), thick = 4)
        glow(Ember, fb.left + px(8), logY + p, 3, 3)
        glow(EmberLight, fb.left + px(9), logY + px(2), 1, 1)
        glow(Ember, fb.center.x + px(6), logY + px(2), 6, 1, alpha = 0.6f + 0.4f * sin(time * 5f))
        fill(Iron, fb.left + px(4), fb.bottom - px(4), fb.right - px(4), fb.bottom - px(2))
        for (i in 0 until 5) box(Iron, fb.left + px(6) + snap((fb.width - px(14)) * i / 4f), fb.bottom - px(6), 1, 6)
        // Hearth slab
        fill(HearthStone, hearth)
        fill(StoneLight, hearth.left, hearth.top, hearth.right, hearth.top + p)
        fill(StoneShadow, hearth.left, hearth.bottom - px(2), hearth.right, hearth.bottom)
        var hx = hearth.left + px(22)
        while (hx < hearth.right) {
            fill(MortarDark, hx, hearth.top + p, hx + p, hearth.bottom - px(2))
            hx += px(24)
        }
    }

    /** Fills the top half circle of an arch spanning [left]..[right] whose top is at [top]. */
    private fun DrawScope.archRows(left: Float, right: Float, top: Float, color: Color) {
        val r = (right - left) / 2
        val cx = left + r
        val rows = (r / p).toInt()
        for (i in 0 until rows) {
            val dy = r - i * p
            val half = snap(sqrt((r * r - dy * dy).coerceAtLeast(0f)))
            fill(color, cx - half, top + i * p, cx + half, top + (i + 1) * p)
        }
    }

    /** One flame layer: rows of varying width that narrow upwards, with tongues that waver. */
    private fun DrawScope.flame(cx: Float, base: Float, height: Float, halfWidth: Float, time: Float, color: Color, seed: Float) {
        val rows = (height / p).toInt()
        for (i in 0 until rows) {
            val t = i / rows.toFloat()
            val tongue = sin(t * 9f - time * 7f + seed) * 0.35f + sin(t * 4f + time * 3f + seed) * 0.2f
            val profile = (1f - t) * (0.75f + 0.25f * sin(t * 3.14f))
            val half = snap(halfWidth * (profile + tongue * t * 0.6f).coerceAtLeast(0f))
            if (half < p) continue
            val shift = snap(sin(t * 5f + time * 4f + seed) * px(3) * t)
            drawRect(color, Offset(cx - half + shift, base - (i + 1) * p), Size(half * 2, p))
        }
    }

    private fun DrawScope.sparks(time: Float, sparksAt: Float) {
        val since = time - sparksAt
        val burst = since in 0f..2f
        val count = if (burst) 14 else 3
        for (i in 0 until count) {
            val life = if (burst) since / (1.2f + noise(i, 95) * 0.8f) else ((time * 0.35f + noise(i, 96)) % 1f)
            if (life !in 0f..1f) continue
            val x = fire.center.x + snap((noise(i, 97) - 0.5f) * fire.width * 0.8f + sin(time * 3f + i) * px(4) * life)
            val y = fire.bottom - px(14) - snap(life * (if (burst) h * 0.2f else fire.height * 0.8f))
            glow(if (i % 3 == 0) FlameYellow else Ember, x, y, 1, 1, alpha = 1f - life)
        }
    }

    private fun DrawScope.mantel() {
        val m = mantel
        // Corbels
        for (i in 0..1) {
            val x = if (i == 0) m.left + px(4) else m.right - px(12)
            fill(WoodDark, x, m.bottom, x + px(8), m.bottom + px(8))
            fill(WoodDark, x + px(2), m.bottom + px(8), x + px(6), m.bottom + px(12))
        }
        fill(Wood, m)
        fill(WoodLight, m.left, m.top, m.right, m.top + px(2))
        fill(WoodDark, m.left, m.bottom - px(2), m.right, m.bottom)
        var gx = m.left + px(8)
        var k = 0
        while (gx < m.right - px(14)) {
            fill(WoodGrain, gx, m.top + px(4 + k % 3), gx + px(12), m.top + px(5 + k % 3))
            gx += px(22)
            k++
        }
        fill(Color(0x40000000), m.left + px(2), m.bottom, m.right, m.bottom + px(2))
        // Candles and a small framed photo in the middle
        val cx = snap(m.center.x)
        box(Color(0xFF5A3A26), cx - px(9), m.top - px(16), 18, 16)
        box(Color(0xFFE9D7B4), cx - px(7), m.top - px(14), 14, 12)
        box(Color(0xFF8BA466), cx - px(7), m.top - px(7), 14, 5)
        box(Color(0xFFB5524A), cx - px(2), m.top - px(11), 4, 4)
        for (i in 0..1) {
            val x = if (i == 0) cx - px(18) else cx + px(13)
            val ch = if (i == 0) 12 else 8
            box(Color(0xFFF4EBDD), x, m.top - px(ch), 4, ch)
            box(Color(0xFFD9CBB4), x + px(3), m.top - px(ch), 1, ch)
            glow(FlameYellow, x + p, m.top - px(ch + 3), 2, 3)
            glow(FlameCore, x + p, m.top - px(ch + 2), 1, 1)
        }
        // A bottle and a jar towards the ends, clear of the plant spots
        box(Color(0xFF3E6B5A), m.left + px(4), m.top - px(13), 5, 13)
        box(Color(0xFF3E6B5A), m.left + px(5), m.top - px(17), 3, 4)
        box(Color(0x55FFFFFF), m.left + px(5), m.top - px(11), 1, 7)
        box(Color(0xFFB86F50), m.right - px(10), m.top - px(9), 7, 9)
        box(Color(0xFF8E4A35), m.right - px(10), m.top - px(10), 7, 2)
    }

    private fun DrawScope.hanger() {
        val potWidth = w * 0.13f
        val potTop = hangY - potWidth * 0.62f
        val x = snap(w * 0.5f)
        box(Iron, x - px(4), beam, 9, 3)
        val ring = Offset(x, snap(potTop - potWidth * 0.8f))
        fill(Chain, x, beam + px(3), x + p, ring.y)
        var cy = beam + px(5)
        while (cy < ring.y) {
            box(ChainDark, x - p, cy, 3, 1)
            cy += px(5)
        }
        for (i in 0..2) {
            val end = Offset(x + potWidth * (i - 1) * 0.45f, potTop + px(3))
            line(Chain, ring.x, ring.y, end.x, end.y)
        }
        box(Chain, ring.x - px(2), ring.y - px(2), 5, 3)
    }

    private fun DrawScope.floor() {
        var y = floorTop
        var row = 0
        while (y < h) {
            val plank = px(10)
            fill(if (row % 2 == 0) FloorA else FloorB, 0f, y, w, y + plank)
            fill(FloorLight, 0f, y, w, y + p)
            fill(FloorSeam, 0f, y + plank, w, y + plank + p)
            var x = px((row * 41) % 50 - 25)
            while (x < w) {
                fill(FloorSeam, x, y, x + p, y + plank)
                x += px(64)
            }
            y += plank + p
            row++
        }
        fill(Color(0x50000000), 0f, floorTop, w, floorTop + px(2))
        // Round rug in front of the hearth
        val rug = Rect(snap(w * 0.2f), hearth.bottom + px(6), snap(w * 0.8f), hearth.bottom + px(46))
        val rows = ((rug.height) / p).toInt()
        for (i in 0 until rows) {
            val t = (i + 0.5f) / rows * 2f - 1f
            val half = snap(rug.width / 2 * sqrt(1f - t * t))
            val ring = when {
                i < 2 || i >= rows - 2 -> RugEdge
                i < 5 || i >= rows - 5 -> RugRed
                i in rows / 2 - 2..rows / 2 + 1 -> RugGold
                else -> RugBase
            }
            fill(ring, rug.center.x - half, rug.top + i * p, rug.center.x + half, rug.top + (i + 1) * p)
            if (ring != RugEdge && half > px(8)) {
                fill(RugEdge, rug.center.x - half, rug.top + i * p, rug.center.x - half + px(2), rug.top + (i + 1) * p)
                fill(RugEdge, rug.center.x + half - px(2), rug.top + i * p, rug.center.x + half, rug.top + (i + 1) * p)
            }
        }
    }

    private fun DrawScope.armchair(left: Boolean) {
        // Seen from the front at the canvas edge, half cut off
        val cw = snap(w * 0.26f)
        val x0 = if (left) -snap(w * 0.06f) else w - cw + snap(w * 0.06f)
        val back = floorTop - px(58)
        val seat = floorTop - px(14)
        val bottom = floorTop + px(30)
        val inner = if (left) x0 + cw else x0
        // Left edge of a part [fromInner] px away from the inner side, [width] px wide
        fun xs(fromInner: Float, width: Float): Float = if (left) inner - fromInner - width else inner + fromInner
        // Back
        val bl = xs(px(8), cw - px(8))
        val br = bl + cw - px(8)
        fill(Chair, bl, back + px(3), br, seat)
        fill(Chair, bl + px(3), back, br - px(3), back + px(3))
        fill(ChairLight, bl + px(3), back, br - px(3), back + p)
        // Buttons
        for (i in 0..2) box(ChairDark, bl + px(10 + i * 14), back + px(18), 2, 2)
        // Cushion and seat
        val sl = xs(px(10), cw - px(10))
        val sr = sl + cw - px(10)
        fill(ChairLight, sl, seat - px(6), sr, seat + px(4))
        fill(Chair, sl, seat + px(4), sr, bottom - px(6))
        fill(ChairDark, sl, bottom - px(8), sr, bottom - px(6))
        // Arm on the inner side
        val al = xs(0f, px(12))
        val ar = al + px(12)
        fill(Chair, al, seat - px(18), ar, bottom - px(6))
        fill(ChairLight, al + p, seat - px(18), ar - p, seat - px(16))
        fill(ChairDark, if (left) ar - px(2) else al, seat - px(14), if (left) ar else al + px(2), bottom - px(6))
        // Throw pillow
        val pl = xs(px(14), px(20))
        val pr = pl + px(20)
        fill(Pillow, pl, seat - px(22), pr, seat - px(6))
        fill(PillowDark, pl, seat - px(8), pr, seat - px(6))
        // Feet and shadow
        val fl = xs(px(4), px(4))
        box(WoodDark, fl, bottom - px(6), 4, 6)
        fill(Color(0x38000000), minOf(al, sl), bottom, maxOf(ar, sr), bottom + px(3))
    }

    private fun DrawScope.floorLamp() {
        val x = lampX
        box(Iron, x - px(7), floorTop + px(18), 15, 3)
        fill(Iron, x, lampShade.bottom, x + p * 2, floorTop + px(18))
        // Shade: glowing fabric
        for (row in 0 until 20) {
            val half = 10 + row / 3
            glow(if (row < 2) LampShadeEdge else LampShade, x + p - px(half), lampShade.top + px(row), half * 2, 1, alpha = 0.75f + 0.25f * night)
            drawRect(c(LampShadeEdge), Offset(x + p - px(half), lampShade.top + px(row)), Size(p, p), alpha = 1f - night)
        }
        glow(LampShadeEdge, x + p - px(16), lampShade.bottom - px(2), 32, 2)
    }

    private fun DrawScope.logBasket() {
        val x = chimney.right + px(8)
        val y = floorTop + px(2)
        fill(Basket, x, y - px(14), x + px(26), y + px(4))
        for (i in 0 until 5) fill(BasketDark, x, y - px(12 - i * 4), x + px(26), y - px(11 - i * 4))
        for (i in 0..2) {
            val lx = x + px(2 + i * 8)
            box(Log, lx, y - px(20 + i % 2 * 2), 7, 7)
            box(LogEnd, lx + px(2), y - px(18 + i % 2 * 2), 3, 3)
        }
    }

    private companion object {
        val Dusk = Color(0xFF241418)
        val Plaster = Color(0xFFD9B88C)
        val PlasterDark = Color(0xFFC9A57A)
        val Brick = Color(0xFFA9654A)
        val Wood = Color(0xFF7A4E32)
        val WoodDark = Color(0xFF55331F)
        val WoodLight = Color(0xFF9A6A45)
        val WoodGrain = Color(0xFF6A422A)
        val Mortar = Color(0xFF3E2A2E)
        val MortarDark = Color(0xFF2E1E22)
        val StoneA = Color(0xFF7A5552)
        val StoneB = Color(0xFF8A625A)
        val StoneC = Color(0xFF6C4A4C)
        val StoneLight = Color(0xFFA27A6C)
        val StoneShadow = Color(0xFF55393C)
        val HearthStone = Color(0xFF7E6460)
        val Soot = Color(0xFF1E1214)
        val BackBrick = Color(0xFF3A1F1C)
        val FlameRed = Color(0xFFE2512A)
        val FlameOrange = Color(0xFFF58A2E)
        val FlameYellow = Color(0xFFFFC94A)
        val FlameCore = Color(0xFFFFF1B8)
        val FireGlow = Color(0xFFFF9A3C)
        val Ember = Color(0xFFFF6A2A)
        val EmberLight = Color(0xFFFFD27A)
        val Log = Color(0xFF5A3A26)
        val LogDark = Color(0xFF3A2416)
        val LogEnd = Color(0xFFC79563)
        val Iron = Color(0xFF2E2A2E)
        val Chain = Color(0xFF6E6A6A)
        val ChainDark = Color(0xFF4E4A4A)
        val FloorA = Color(0xFF7A4A30)
        val FloorB = Color(0xFF6C4129)
        val FloorLight = Color(0xFF8A5A3A)
        val FloorSeam = Color(0xFF452717)
        val RugBase = Color(0xFF9E3B2E)
        val RugRed = Color(0xFFC8573A)
        val RugGold = Color(0xFFE0A24A)
        val RugEdge = Color(0xFF6E2620)
        val Chair = Color(0xFFC0643A)
        val ChairLight = Color(0xFFD87A4A)
        val ChairDark = Color(0xFF8E4529)
        val Pillow = Color(0xFF8A7A4A)
        val PillowDark = Color(0xFF6A5C36)
        val LampShade = Color(0xFFFFE2A8)
        val LampShadeEdge = Color(0xFFF2C46A)
        val LampGlow = Color(0xFFFFD58A)
        val Basket = Color(0xFFB08850)
        val BasketDark = Color(0xFF8A6A3A)
        val BookColors = listOf(Color(0xFF4E7A8C), Color(0xFFB5524A), Color(0xFF6E8F4E), Color(0xFFD9A441), Color(0xFF8A6FA8))
        val PatchX = floatArrayOf(0.02f, 0.8f, 0.12f, 0.83f)
        val PatchY = floatArrayOf(0.1f, 0.12f, 0.6f, 0.62f)
    }
}
