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
import dev.mothlight.plantagotchi.ui.game.SceneryRenderer.city
import dev.mothlight.plantagotchi.ui.game.SceneryRenderer.noise
import kotlin.math.sin

/**
 * A little plant shop: string lights under the ceiling (tap to switch them), a chalkboard, wall
 * shelves with goods, a counter with a till, a big shop window with the street outside and crates
 * of flowers. Plants of the player stand on the sill, shelf, counter, floor and hang in the middle.
 */
internal class PlantShopRoom(size: Size, floorTop: Float, pixel: Float) : IndoorBase(size, floorTop, pixel) {
    override val scenery = Scenery.PLANT_SHOP

    private val beam = px(10)
    val glass = Rect(snap(w * 0.57f), snap(h * 0.19f), snap(w * 0.95f), snap(h * 0.45f))
    override val windows = listOf(glass)
    private val sillTop = glass.bottom + px(5)
    val chalkboard = Rect(snap(w * 0.06f), snap(h * 0.165f), snap(w * 0.4f), snap(h * 0.265f))
    val shelves = Rect(snap(w * 0.04f), snap(h * 0.31f), snap(w * 0.47f), snap(h * 0.595f) + px(4))
    private val shelfTops = floatArrayOf(shelves.top, snap(h * 0.405f), snap(h * 0.5f), snap(h * 0.595f))
    val counter = Rect(snap(w * 0.02f), snap(floorTop - h * 0.13f), snap(w * 0.6f), floorTop + px(8))
    private val hangY = snap(h * 0.29f)

    /** Tap area of the string lights. */
    val lights = Rect(0f, beam, w, snap(h * 0.15f))

    private val wallParts = around(Rect(0f, 0f, w, floorTop), windows)

    override fun anchor(spot: RoomSpot): Pair<Offset, Float> = when (spot) {
        RoomSpot.SILL_LEFT -> Offset(w * 0.67f, sillTop) to w * 0.13f
        RoomSpot.SILL_RIGHT -> Offset(w * 0.85f, sillTop) to w * 0.13f
        RoomSpot.SHELF -> Offset(w * 0.33f, shelves.top) to w * 0.12f
        RoomSpot.DESK -> Offset(w * 0.46f, counter.top) to w * 0.13f
        RoomSpot.HANGING -> Offset(w * 0.5f, hangY) to w * 0.13f
        RoomSpot.FLOOR_LEFT -> Offset(w * 0.2f, floorTop + w * 0.03f) to w * 0.22f
        RoomSpot.FLOOR_RIGHT -> Offset(w * 0.8f, floorTop + w * 0.03f) to w * 0.22f
    }

    override fun toyAt(offset: Offset): String? = if (lights.contains(offset)) IndoorToy.STRING_LIGHTS else null

    /** 0 = lights off, 1 = on; flickers for a moment after switching on. */
    private fun lightLevel(time: Float, toys: IndoorToys): Float {
        val since = time - toys.lightsSwitchedAt
        if (!toys.lightsOn) return 0f
        if (since in 0f..0.6f) return if (sin(since * 40f) > 0f) 1f else 0.2f
        return 1f
    }

    override fun draw(scope: DrawScope, tint: Float, time: Float, toys: IndoorToys) = with(scope) {
        val level = lightLevel(time, toys)
        beginFrame(tint, Dusk, 0.35f + 0.65f * level)
        outside(tint, time)
        wall()
        ceiling()
        window()
        chalkboard()
        shelves()
        pegboard()
        crates()
        floor()
        counter()
        hanger()
        stringLights(time, level)
        if (level > 0f) {
            val center = Offset(w * 0.5f, h * 0.12f)
            val radius = w * 0.9f
            drawCircle(
                Brush.radialGradient(listOf(Warm.copy(alpha = (0.08f + 0.26f * night) * level), Color.Transparent), center, radius),
                radius, center,
            )
        }
    }

    /** The street outside: houses across the road and the pavement. The sky comes from the game. */
    private fun DrawScope.outside(tint: Float, time: Float) {
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            city(Rect(glass.left, glass.top + glass.height * 0.3f, glass.right, glass.bottom - px(8)), p, p * 4f / PIXEL_DP, tint, time)
            drawRect(SceneryRenderer.shade(Color(0xFF7A7A82), tint), Offset(glass.left, glass.bottom - px(8)), Size(glass.width, px(8)))
            drawRect(SceneryRenderer.shade(Color(0xFF9A9AA2), tint), Offset(glass.left, glass.bottom - px(8)), Size(glass.width, p))
            // Street lamp
            val lx = snap(glass.left + glass.width * 0.7f)
            drawRect(SceneryRenderer.shade(Color(0xFF2F3A36), tint), Offset(lx, glass.top + glass.height * 0.35f), Size(px(2), glass.bottom - glass.top - glass.height * 0.35f))
            val on = night > 0.3f
            drawRect(if (on) Color(0xFFFFE08A) else SceneryRenderer.shade(Color(0xFFDDE3E6), tint), Offset(lx - px(3), glass.top + glass.height * 0.33f), Size(px(8), px(3)))
        }
    }

    private fun DrawScope.wall() {
        fillParts(Wall, wallParts)
        // Vertical paneling, interrupted by the window
        var x = px(4)
        while (x < w) {
            if (x + px(2) < glass.left || x > glass.right) {
                fill(WallSeam, x, beam, x + p, floorTop)
                fill(WallLight, x + p, beam, x + px(2), floorTop)
            } else {
                fill(WallSeam, x, beam, x + p, glass.top)
                fill(WallLight, x + p, beam, x + px(2), glass.top)
                fill(WallSeam, x, glass.bottom, x + p, floorTop)
                fill(WallLight, x + p, glass.bottom, x + px(2), floorTop)
            }
            x += px(12)
        }
        // Wainscot: wood up to the counter height
        val top = snap(floorTop - h * 0.1f)
        fill(Wood, 0f, top, w, floorTop)
        fill(WoodLight, 0f, top, w, top + px(2))
        fill(WoodDark, 0f, top + px(2), w, top + px(3))
        var wx = px(6)
        while (wx < w) {
            fill(WoodDark, wx, top + px(3), wx + p, floorTop)
            wx += px(16)
        }
    }

    private fun DrawScope.ceiling() {
        fill(Beam, 0f, 0f, w, beam)
        fill(BeamLight, 0f, beam - px(2), w, beam - p)
        fill(Color(0x50000000), 0f, beam, w, beam + px(2))
    }

    private fun DrawScope.window() {
        val f = px(5)
        fill(Metal, glass.left - f, glass.top - f, glass.right + f, glass.top)
        fill(Metal, glass.left - f, glass.top, glass.left, glass.bottom)
        fill(Metal, glass.right, glass.top, glass.right + f, glass.bottom)
        // Mullions: three columns, a transom
        for (i in 1..2) {
            val x = snap(glass.left + glass.width * i / 3f) - p
            fill(Metal, x, glass.top, x + px(3), glass.bottom)
        }
        val ty = snap(glass.top + glass.height * 0.22f)
        fill(Metal, glass.left, ty, glass.right, ty + px(3))
        // Lettering arc on the transom panes: a leaf sign
        val sx = snap(glass.center.x)
        box(Color(0xCCF4EBDD), sx - px(9), ty - px(10), 18, 1)
        box(Color(0xCCF4EBDD), sx - px(2), ty - px(16), 4, 6)
        box(Color(0xCC9BC48A), sx - px(5), ty - px(18), 4, 3)
        box(Color(0xCC9BC48A), sx + px(1), ty - px(19), 4, 3)
        // Sill board
        fill(Wood, glass.left - px(8), glass.bottom, glass.right + px(8), sillTop)
        fill(WoodLight, glass.left - px(8), glass.bottom, glass.right + px(8), glass.bottom + p)
        fill(WoodDark, glass.left - px(8), sillTop - p, glass.right + px(8), sillTop)
        fill(Color(0x40000000), glass.left - px(6), sillTop, glass.right + px(8), sillTop + px(3))
        // Shine
        val shine = Color.White.copy(alpha = 0.12f * (1f - night * 0.7f))
        line(shine, glass.left + px(8), glass.bottom - px(10), glass.left + px(22), glass.bottom - px(24), thick = 2, shaded = false)
    }

    private fun DrawScope.chalkboard() {
        val b = chalkboard
        fill(WoodDark, b)
        fill(WoodLight, b.left, b.top, b.right, b.top + p)
        val inner = Rect(b.left + px(3), b.top + px(3), b.right - px(3), b.bottom - px(3))
        fill(Chalkboard, inner)
        // Chalk "text": lines of short strokes, a leaf doodle and a heart
        var y = inner.top + px(4)
        var row = 0
        while (y < inner.bottom - px(4)) {
            var x = inner.left + px(if (row == 0) 18 else 5)
            var k = 0
            val end = inner.right - px(if (row == 0) 18 else 26)
            while (x < end) {
                val len = 3 + ((noise(k + row * 9, 71) * 6f).toInt())
                box(if (row == 0) ChalkYellow else Chalk, x, y, minOf(len, ((end - x) / p).toInt()), 1)
                x += px(len + 2)
                k++
            }
            y += px(if (row == 0) 7 else 5)
            row++
        }
        // Leaf on the right
        val lx = inner.right - px(18)
        val ly = inner.top + px(12)
        line(ChalkGreen, lx, ly + px(12), lx + px(10), ly, shaded = true)
        for (i in 0 until 4) {
            box(ChalkGreen, lx + px(2 + i * 2), ly + px(9 - i * 3), 4 - i / 2, 2)
            box(ChalkGreen, lx + px(1 + i * 2), ly + px(6 - i * 2), 2, 3)
        }
        // Heart
        val hx = inner.right - px(9)
        val hy = inner.bottom - px(8)
        box(ChalkPink, hx, hy, 2, 2)
        box(ChalkPink, hx + px(3), hy, 2, 2)
        box(ChalkPink, hx, hy + px(1), 5, 2)
        box(ChalkPink, hx + p, hy + px(3), 3, 1)
        box(ChalkPink, hx + px(2), hy + px(4), 1, 1)
        // Chalk tray and hanging chains
        fill(WoodDark, b.left + px(4), b.bottom, b.right - px(4), b.bottom + px(2))
        box(Chalk, b.left + px(10), b.bottom - p, 4, 1)
        line(Cord, b.left + px(6), beam, b.left + px(6), b.top)
        line(Cord, b.right - px(7), beam, b.right - px(7), b.top)
    }

    private fun DrawScope.shelves() {
        val s = shelves
        // Back panel and sides
        fill(ShelfBack, s.left + px(3), s.top, s.right - px(3), s.bottom)
        fill(WoodDark, s.left, s.top - px(4), s.left + px(3), s.bottom)
        fill(WoodDark, s.right - px(3), s.top - px(4), s.right, s.bottom)
        for (i in shelfTops.indices) {
            val y = shelfTops[i]
            fill(Wood, s.left, y, s.right, y + px(4))
            fill(WoodLight, s.left, y, s.right, y + p)
            fill(Color(0x40000000), s.left + px(3), y + px(4), s.right - px(3), y + px(6))
            if (i > 0) goods(i, s.left + px(5), y, s.right - px(5))
        }
        // Top shelf: a watering can and a stack of pots at the left, the right end stays free for a plant
        val ty = shelfTops[0]
        val tx = s.left + px(6)
        box(Can, tx, ty - px(10), 12, 10)
        box(CanLight, tx + p, ty - px(9), 2, 8)
        box(Can, tx + px(12), ty - px(9), 6, 2)
        box(Can, tx + px(16), ty - px(11), 2, 2)
        box(Can, tx + px(3), ty - px(13), 6, 2)
        for (i in 0 until 3) {
            box(Terracotta, tx + px(24 - i), ty - px(5 + i * 3), 12 + i * 2, 3)
            box(TerracottaDark, tx + px(24 - i), ty - px(3 + i * 3), 12 + i * 2, 1)
        }
    }

    /** Goods standing on shelf [index] between [left] and [right]: jars, seed packets, pots, books. */
    private fun DrawScope.goods(index: Int, left: Float, y: Float, right: Float) {
        var x = left
        var k = 0
        while (x < right - gp(8)) {
            val kind = ((noise(k + index * 17, 73) * 5f).toInt() + index) % 5
            val used = when (kind) {
                0 -> { // Jar with seeds
                    gbox(Glass, x, y - gp(11), 8, 11)
                    gbox(SeedColors[(k + index) % SeedColors.size], x + p, y - gp(7), 6, 6)
                    gbox(Lid, x, y - gp(12), 8, 2)
                    gbox(Label, x + gp(2), y - gp(6), 4, 3)
                    9
                }
                1 -> { // Seed packets leaning
                    for (j in 0 until 3) {
                        gbox(PacketColors[(j + k) % PacketColors.size], x + gp(j * 5), y - gp(12 - j), 5, 12 - j)
                        gbox(Label, x + gp(j * 5 + 1), y - gp(9 - j), 3, 3)
                    }
                    16
                }
                2 -> { // Small pot with a succulent
                    gbox(Terracotta, x, y - gp(6), 10, 6)
                    gbox(TerracottaDark, x, y - gp(6), 10, 1)
                    gbox(Succulent, x + gp(2), y - gp(10), 6, 4)
                    gbox(SucculentLight, x + gp(3), y - gp(12), 4, 2)
                    11
                }
                3 -> { // Books
                    for (j in 0 until 3) {
                        val bh = 11 + (j * 3 + k) % 4
                        gbox(BookColors[(j + k + index) % BookColors.size], x + gp(j * 4), y - gp(bh), 4, bh)
                    }
                    13
                }
                else -> { // Bottle of fertilizer
                    gbox(Bottle, x + p, y - gp(13), 6, 13)
                    gbox(BottleCap, x + gp(2), y - gp(15), 4, 2)
                    gbox(Label, x + gp(2), y - gp(9), 4, 4)
                    9
                }
            }
            x += gp(used + 2)
            k++
        }
    }

    /** Goods are drawn 1.5 times their pixel counts so they read well on the shelves. */
    private fun gp(n: Int) = px((n * 3 + 1) / 2)
    private fun DrawScope.gbox(color: Color, x: Float, y: Float, wPx: Int, hPx: Int) = box(color, x, y, (wPx * 3 + 1) / 2, (hPx * 3 + 1) / 2)

    /** Pegboard with garden tools under the window. */
    private fun DrawScope.pegboard() {
        val b = Rect(glass.left + px(10), snap(h * 0.5f), glass.right - px(10), snap(h * 0.6f))
        fill(WoodDark, b)
        fill(Peg, b.left + px(2), b.top + px(2), b.right - px(2), b.bottom - px(2))
        var y = b.top + px(6)
        while (y < b.bottom - px(4)) {
            var x = b.left + px(6)
            while (x < b.right - px(4)) {
                box(PegHole, x, y, 1, 1)
                x += px(8)
            }
            y += px(8)
        }
        val x0 = b.left + snap(b.width * 0.1f)
        // Trowel
        box(WoodDark, x0, b.top + px(8), 3, 10)
        box(Steel, x0 - px(2), b.top + px(18), 7, 10)
        box(SteelLight, x0 - px(1), b.top + px(19), 1, 7)
        // Shears
        val x1 = b.left + snap(b.width * 0.32f)
        line(Steel, x1, b.top + px(8), x1 + px(8), b.top + px(30), thick = 2)
        line(Steel, x1 + px(8), b.top + px(8), x1, b.top + px(30), thick = 2)
        box(Till, x1 - px(2), b.top + px(26), 5, 5)
        box(Till, x1 + px(6), b.top + px(26), 5, 5)
        // Twine and gloves
        val x2 = b.left + snap(b.width * 0.55f)
        box(Twine, x2, b.top + px(10), 12, 12)
        box(TwineDark, x2, b.top + px(13), 12, 1)
        box(TwineDark, x2, b.top + px(17), 12, 1)
        line(Twine, x2 + px(11), b.top + px(20), x2 + px(14), b.top + px(32))
        val x3 = b.left + snap(b.width * 0.78f)
        box(Glove, x3, b.top + px(10), 9, 14)
        box(Glove, x3 - px(3), b.top + px(14), 3, 5)
        box(GloveDark, x3, b.top + px(22), 9, 2)
    }

    private fun DrawScope.crates() {
        // Stacked crates with flowers under the window
        val left = glass.left - px(4)
        val right = glass.right + px(4)
        val cw = snap((right - left) / 3f)
        for (i in 0 until 3) {
            val x = left + cw * i
            for (level in 0..1) {
                val cy = if (level == 0) floorTop - px(26) else floorTop - px(56)
                if (level == 1 && i == 1) continue
                fill(Crate, x + p, cy, x + cw - p, cy + px(24))
                fill(CrateDark, x + p, cy + px(11), x + cw - p, cy + px(13))
                fill(CrateLight, x + p, cy, x + cw - p, cy + p)
                fill(CrateDark, x + p, cy, x + px(3), cy + px(24))
                fill(CrateDark, x + cw - px(3), cy, x + cw - p, cy + px(24))
                // Flowers peeking out of the crate
                for (f in 0 until 4) {
                    val fx = x + px(5) + snap((cw - px(12)) * f / 3f)
                    box(Leaf, fx, cy - px(4), 2, 5)
                    box(Leaf, fx - px(2), cy - px(2), 2, 2)
                    box(FlowerColors[(f + i + level) % FlowerColors.size], fx - p, cy - px(7), 4, 3)
                    box(Color(0xFFFFE08A), fx, cy - px(6), 2, 1)
                }
            }
        }
    }

    private fun DrawScope.floor() {
        // Terracotta tiles
        val tile = px(20)
        var y = floorTop
        var row = 0
        while (y < h) {
            var x = -px(if (row % 2 == 0) 0 else 10)
            var k = 0
            while (x < w) {
                val v = noise(k + row * 31, 77)
                fill(if (v < 0.33f) TileA else if (v < 0.66f) TileB else TileC, x, y, x + tile - p, y + tile - p)
                fill(TileLight, x, y, x + tile - p, y + p)
                x += tile
                k++
            }
            fill(Grout, 0f, y + tile - p, w, y + tile)
            x = -px(if (row % 2 == 0) 0 else 10) + tile - p
            while (x < w) {
                fill(Grout, x, y, x + p, y + tile)
                x += tile
            }
            y += tile
            row++
        }
        fill(Color(0x50000000), 0f, floorTop, w, floorTop + px(2))
        // Door mat
        val mat = Rect(snap(w * 0.62f), floorTop + px(40), snap(w * 0.98f), floorTop + px(56))
        fill(Mat, mat)
        fill(MatDark, mat.left + px(2), mat.top + px(2), mat.right - px(2), mat.top + px(3))
        fill(MatDark, mat.left + px(2), mat.bottom - px(3), mat.right - px(2), mat.bottom - px(2))
    }

    private fun DrawScope.counter() {
        val c = counter
        // Front: wooden slats in a muted green
        fill(CounterFront, c.left, c.top + px(6), c.right, c.bottom)
        var x = c.left + px(5)
        while (x < c.right - px(3)) {
            fill(CounterSlat, x, c.top + px(9), x + p, c.bottom - px(4))
            x += px(7)
        }
        fill(WoodDark, c.left, c.bottom - px(4), c.right, c.bottom)
        // Top
        fill(Wood, c.left - px(3), c.top, c.right + px(3), c.top + px(6))
        fill(WoodLight, c.left - px(3), c.top, c.right + px(3), c.top + px(2))
        fill(WoodDark, c.left - px(3), c.top + px(5), c.right + px(3), c.top + px(6))
        fill(Color(0x40000000), c.left, c.top + px(6), c.right, c.top + px(8))
        fill(Color(0x40000000), c.left, c.bottom, c.right + px(4), c.bottom + px(3))
        // Till
        val tx = snap(w * 0.07f)
        val ty = c.top
        box(Till, tx, ty - px(14), 26, 14)
        box(TillLight, tx, ty - px(14), 26, 2)
        box(TillDark, tx + px(2), ty - px(10), 22, 2)
        for (r in 0..1) for (k in 0..3) box(Key, tx + px(3 + k * 6), ty - px(7 - r * 3), 4, 2)
        box(Till, tx + px(14), ty - px(22), 12, 8)
        box(Display, tx + px(16), ty - px(20), 8, 4)
        // Bell and paper bag
        val bx = snap(w * 0.24f)
        box(Brass, bx, ty - px(5), 8, 4)
        box(Brass, bx + px(3), ty - px(7), 2, 2)
        box(BrassLight, bx + p, ty - px(5), 2, 1)
        box(Iron, bx - p, ty - p, 10, 1)
        val px0 = snap(w * 0.31f)
        box(Paper, px0, ty - px(16), 12, 16)
        box(PaperDark, px0, ty - px(16), 12, 2)
        box(PaperDark, px0 + px(9), ty - px(14), 3, 14)
        box(Leaf, px0 + px(3), ty - px(9), 4, 4)
    }

    private fun DrawScope.hanger() {
        val potWidth = w * 0.13f
        val potTop = hangY - potWidth * 0.62f
        val x = snap(w * 0.5f)
        box(Beam, x - px(3), beam, 7, 3)
        val ring = Offset(x, snap(potTop - potWidth * 0.85f))
        fill(Rope, x, beam + px(3), x + p, ring.y)
        for (i in 0..2) {
            val end = Offset(x + potWidth * (i - 1) * 0.45f, potTop + px(3))
            line(Rope, ring.x, ring.y, end.x, end.y)
        }
        box(Rope, ring.x - px(2), ring.y - px(2), 5, 3)
    }

    /** Garlands of bulbs sagging between the beams; they twinkle and glow. */
    private fun DrawScope.stringLights(time: Float, level: Float) {
        for (g in 0 until 3) {
            val x0 = w * GarlandX0[g]
            val x1 = w * GarlandX1[g]
            val y0 = h * GarlandY[g]
            val sag = h * GarlandSag[g]
            val n = ((x1 - x0) / px(4)).toInt()
            // Wire: points on a parabola, one rect per step
            var prevX = x0
            var prevY = y0
            for (i in 1..n) {
                val t = i / n.toFloat()
                val x = x0 + (x1 - x0) * t
                val y = y0 + sag * 4f * t * (1f - t)
                line(Wire, prevX, prevY, x, y)
                prevX = x
                prevY = y
            }
            // Bulbs every 14 px
            val bulbs = ((x1 - x0) / px(14)).toInt()
            for (i in 1 until bulbs) {
                val t = i / bulbs.toFloat()
                val bx = snap(x0 + (x1 - x0) * t)
                val by = snap(y0 + sag * 4f * t * (1f - t)) + p
                box(Wire, bx, by, 2, 1)
                if (level > 0f) {
                    val twinkle = 0.75f + 0.25f * sin(time * (1.3f + noise(i + g * 50, 79) * 2f) + i * 1.7f)
                    val a = level * twinkle
                    glow(BulbHalo, bx - px(2), by - p, 6, 7, alpha = a * (0.25f + 0.25f * night))
                    glow(Bulb, bx, by + p, 2, 3, alpha = a)
                    glow(BulbCore, bx, by + px(2), 1, 1, alpha = a)
                } else {
                    box(BulbOff, bx, by + p, 2, 3)
                }
            }
        }
    }

    private companion object {
        val Dusk = Color(0xFF1A1820)
        val Warm = Color(0xFFFFD58A)
        val Wall = Color(0xFF6A6573)
        val WallSeam = Color(0xFF5A5563)
        val WallLight = Color(0xFF77727F)
        val Beam = Color(0xFF3A2A22)
        val BeamLight = Color(0xFF5A4232)
        val Cord = Color(0xFF2A2226)
        val Wood = Color(0xFFA9774A)
        val WoodDark = Color(0xFF6E4A2E)
        val WoodLight = Color(0xFFC79563)
        val Metal = Color(0xFF2F3A36)
        val Chalkboard = Color(0xFF2E3B34)
        val Chalk = Color(0xFFDCE3DA)
        val ChalkYellow = Color(0xFFF2DC8A)
        val ChalkGreen = Color(0xFF9BC48A)
        val ChalkPink = Color(0xFFF2A6B4)
        val ShelfBack = Color(0xFF4A3A30)
        val Can = Color(0xFF5FA4B4)
        val CanLight = Color(0xFF8FCAD6)
        val Terracotta = Color(0xFFC9714A)
        val TerracottaDark = Color(0xFFA0583A)
        val Glass = Color(0xFFCFE3E0)
        val Lid = Color(0xFF8A6A4A)
        val Label = Color(0xFFF4EBDD)
        val Succulent = Color(0xFF6FA06A)
        val SucculentLight = Color(0xFF9BC48A)
        val Bottle = Color(0xFF7A5A9A)
        val BottleCap = Color(0xFF2E2A2E)
        val Crate = Color(0xFFB8935E)
        val CrateDark = Color(0xFF8E6E42)
        val CrateLight = Color(0xFFD2AE78)
        val Leaf = Color(0xFF4F8A4A)
        val TileA = Color(0xFFB8643E)
        val TileB = Color(0xFFAA5A36)
        val TileC = Color(0xFFC06C44)
        val TileLight = Color(0xFFCC7C52)
        val Grout = Color(0xFF7E4A32)
        val Mat = Color(0xFFD9A441)
        val MatDark = Color(0xFFB58632)
        val CounterFront = Color(0xFF5E7E6A)
        val CounterSlat = Color(0xFF4E6A58)
        val Till = Color(0xFF8E3B34)
        val TillLight = Color(0xFFB0544A)
        val TillDark = Color(0xFF5E2622)
        val Key = Color(0xFFF4EBDD)
        val Display = Color(0xFF9BE08A)
        val Brass = Color(0xFFD9A441)
        val BrassLight = Color(0xFFFFE08A)
        val Iron = Color(0xFF2E2A2E)
        val Paper = Color(0xFFD8B98A)
        val PaperDark = Color(0xFFB8996A)
        val Rope = Color(0xFF8B6B4A)
        val Peg = Color(0xFFC9A574)
        val PegHole = Color(0xFF8E6E42)
        val Steel = Color(0xFF9AA4A8)
        val SteelLight = Color(0xFFD0D8DA)
        val Twine = Color(0xFFD8B98A)
        val TwineDark = Color(0xFFB8996A)
        val Glove = Color(0xFF6FA0C8)
        val GloveDark = Color(0xFF4E7A9C)
        val Wire = Color(0xFF2A2226)
        val Bulb = Color(0xFFFFD27A)
        val BulbCore = Color(0xFFFFF6D6)
        val BulbHalo = Color(0xFFFFC25A)
        val BulbOff = Color(0xFF8A8270)
        val SeedColors = listOf(Color(0xFF8E5A3A), Color(0xFFD9A441), Color(0xFF6E8F4E), Color(0xFF3A2A22))
        val PacketColors = listOf(Color(0xFFE48A5A), Color(0xFF6FA0C8), Color(0xFFF2C14E), Color(0xFFB5524A), Color(0xFF8A6FA8))
        val BookColors = listOf(Color(0xFF4E7A8C), Color(0xFFB5524A), Color(0xFF6E8F4E), Color(0xFFD9A441), Color(0xFF8A6FA8))
        val FlowerColors = listOf(Color(0xFFF2A6B4), Color(0xFFFFE08A), Color(0xFFE85A5A), Color(0xFFB9A6F2), Color(0xFFFFFFFF))
        val GarlandX0 = floatArrayOf(-0.05f, 0.42f, 0.1f)
        val GarlandX1 = floatArrayOf(0.58f, 1.05f, 0.9f)
        val GarlandY = floatArrayOf(0.035f, 0.03f, 0.02f)
        val GarlandSag = floatArrayOf(0.06f, 0.07f, 0.11f)
    }
}
