package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Pots drawn in code on a fixed pixel grid ([GRID] cells across the pot rect, about the same
 * pixel size as the plant above it). Ids: `pot<shape><color>` from [PotId] (shape 1 = wide bowl,
 * 2 = classic tapered pot, 3 = round vase; color 1 = terracotta, 2 = lavender glaze,
 * 3 = rose glaze, 4 = charcoal) plus `tray<color>`, the footed bonsai tray (see [potFor]).
 * The pot is centered and bottom aligned in its rect; its top edge is the rect top.
 */
object PotRenderer {
    /** Cells across the pot rect. Plants are 25/19 of the rect wide on a 72 grid, so pixels match. */
    const val GRID = 56

    /** Prefix of the bonsai tray ids (`tray1` .. `tray4`). */
    const val BONSAI_TRAY = "tray"

    /** The pot to draw for [species] in the owned pot [potId]: bonsai get a tray in the pot's color. */
    fun potFor(species: Species, potId: String): String =
        if (species == Species.BONSAI) BONSAI_TRAY + PotId.color(potId) else potId

    /** Height / width of the pot rect. */
    fun aspect(potId: String): Float = sprite(potId).rows / GRID.toFloat()

    /** Visible pot width as a fraction of the rect width (the rect has transparent margins). */
    fun visibleWidth(potId: String): Float = sprite(potId).visible / GRID.toFloat()

    fun draw(scope: DrawScope, potId: String, rect: Rect) {
        val s = sprite(potId)
        val pixel = rect.width / GRID
        val runs = s.runs
        var i = 0
        while (i < runs.size) {
            val x = runs[i]; val y = runs[i + 1]; val len = runs[i + 2]; val c = runs[i + 3]
            val l = snap(rect.left + x * pixel)
            val r = snap(rect.left + (x + len) * pixel)
            val t = snap(rect.top + y * pixel)
            val b = snap(rect.top + (y + 1) * pixel)
            scope.drawRect(Color(c), Offset(l, t), Size(r - l, b - t))
            i += 4
        }
    }

    private fun snap(v: Float) = floor(v + 0.5f)

    // region Designs

    private class Sprite(val rows: Int, val visible: Int, val runs: IntArray)

    private val cache = HashMap<String, Sprite>()

    private fun sprite(id: String): Sprite = cache.getOrPut(id) { build(id) }

    private class Glaze(val outline: Int, val dark: Int, val mid: Int, val light: Int, val hi: Int, val glazed: Boolean)

    private fun glaze(o: Long, d: Long, m: Long, l: Long, h: Long, glazed: Boolean) =
        Glaze(o.toInt(), d.toInt(), m.toInt(), l.toInt(), h.toInt(), glazed)

    private val clay = glaze(0xFF3B1F16, 0xFF7A3E25, 0xFFA4552F, 0xFFC4703D, 0xFFE09A62, false)
    private val glazes = arrayOf(
        clay,
        glaze(0xFF2A2340, 0xFF5B4E8C, 0xFF7C6DB5, 0xFF9E90D4, 0xFFD6CCF4, true), // lavender
        glaze(0xFF3D1A26, 0xFF9C4766, 0xFFC8678A, 0xFFE08EAA, 0xFFF8CADA, true), // rose
        glaze(0xFF0E0E12, 0xFF1D1D24, 0xFF2B2B33, 0xFF3C3C46, 0xFF5E5E6B, false), // charcoal
    )

    private const val SOIL_DARK = 0xFF24170E.toInt()
    private const val SOIL = 0xFF3A2618.toInt()
    private const val SOIL_SPECK = 0xFF5C4029.toInt()

    // Row roles
    private const val RIM_EDGE = 0
    private const val RIM_SOIL = 1
    private const val RIM_FACE = 2
    private const val BODY = 3
    private const val BODY_BOTTOM = 4
    private const val FOOT = 5

    private fun build(id: String): Sprite {
        val tray = id.startsWith(BONSAI_TRAY)
        val shape = if (tray) 0 else PotId.shape(id)
        val color = (if (tray) id.getOrNull(BONSAI_TRAY.length)?.digitToIntOrNull() ?: 1 else PotId.color(id)).coerceIn(1, 4)
        val g = glazes[color - 1]

        // half widths and roles per row, top to bottom
        val half = FloatArray(48)
        val role = IntArray(48)
        var n = 0
        fun row(h: Float, r: Int) { half[n] = h; role[n] = r; n++ }
        fun rim(h: Float, faceRows: Int) {
            row(h, RIM_EDGE); row(h, RIM_SOIL); row(h, RIM_SOIL)
            repeat(faceRows) { row(h, RIM_FACE) }
            row(h, RIM_EDGE)
        }
        var footInner = 0f
        var footOuter = 0f
        when (shape) {
            0 -> { // bonsai tray: wide, shallow, on little feet
                rim(25f, 1)
                for (i in 0 until 7) row(24f - i * 0.35f, BODY)
                row(21.5f, BODY_BOTTOM)
                footInner = 14f; footOuter = 19f
                repeat(3) { row(19f, FOOT) }
            }
            1 -> { // wide bowl
                rim(22f, 1)
                for (i in 0 until 12) {
                    val t = i / 11f
                    row(21f - 4.5f * t * t, BODY)
                }
                row(15.5f, BODY_BOTTOM)
                footInner = 9f; footOuter = 13f
                repeat(2) { row(13f, FOOT) }
            }
            3 -> { // round vase
                rim(12f, 0)
                row(10f, BODY); row(10f, BODY)
                for (i in 0 until 26) {
                    val t = i / 25f
                    row(10f + 5.5f * sin(t * 3.1415927f * 0.92f + 0.12f) - t * 1.5f, BODY)
                }
                row(9f, BODY_BOTTOM)
                footInner = 0f; footOuter = 10f
                repeat(2) { row(10f, FOOT) }
            }
            else -> { // classic tapered pot
                rim(18f, 2)
                for (i in 0 until 26) row(16f - 4f * i / 25f, BODY)
                row(12f, BODY_BOTTOM)
            }
        }

        val grid = IntArray(GRID * n)
        val cx = GRID / 2f
        var bodyRow = 0
        val bodyRows = (0 until n).count { role[it] == BODY }
        for (y in 0 until n) {
            val h = half[y]
            val prevHalf = if (y > 0) half[y - 1] else 0f
            if (role[y] == BODY) bodyRow++
            for (x in 0 until GRID) {
                val d = x + 0.5f - cx
                val a = abs(d)
                var c = 0
                when (role[y]) {
                    FOOT -> {
                        if (footInner == 0f) {
                            if (a <= h) c = if (a > h - 1f || y == n - 1) g.outline else if (d < -h * 0.4f) g.mid else g.dark
                        } else if (a in footInner..footOuter) {
                            c = if (a < footInner + 1f || a > footOuter - 1f || y == n - 1) g.outline else g.dark
                        }
                    }
                    else -> if (a <= h) {
                        val u = d / h
                        val outer = a > h - 1f
                        c = when (role[y]) {
                            RIM_EDGE -> g.outline
                            BODY_BOTTOM -> g.outline
                            RIM_SOIL -> when {
                                outer -> g.outline
                                abs(u) < (if (role[y - 1] == RIM_EDGE) 0.84f else 0.76f) -> {
                                    val hs = hash01(x, y, 77)
                                    when {
                                        role[y - 1] == RIM_EDGE -> if (hs < 0.25f) SOIL else SOIL_DARK
                                        hs < 0.18f -> SOIL_SPECK
                                        else -> SOIL
                                    }
                                }
                                u < 0f -> g.hi
                                else -> g.light
                            }
                            RIM_FACE -> when {
                                outer -> g.outline
                                role[y - 1] == RIM_SOIL -> if (u < 0.3f) g.hi else g.light
                                u < -0.55f -> g.light
                                u > 0.45f -> g.dark
                                else -> g.mid
                            }
                            else -> { // BODY
                                // body sides may step outwards: cells outside the previous row get an outline too
                                if (outer) g.outline
                                else shadeBody(g, u, x, y, bodyRow, bodyRows, a > prevHalf - 0.5f && role[y - 1] == BODY)
                            }
                        }
                    }
                }
                grid[y * GRID + x] = c
            }
            // unglazed clay foot band on glazed pots
            if (g.glazed && role[y] == BODY && bodyRow > bodyRows - 3) {
                for (x in 0 until GRID) {
                    val i = y * GRID + x
                    val c = grid[i]
                    if (c == 0 || c == g.outline) continue
                    val u = (x + 0.5f - cx) / h
                    // wavy glaze drip edge on the first clay row
                    if (bodyRow == bodyRows - 2 && hash01(x, 3, 91) < 0.4f) continue
                    grid[i] = when {
                        u < -0.55f -> clay.light
                        u > 0.45f -> clay.dark
                        else -> clay.mid
                    }
                }
            }
        }

        // run-length encode
        var count = 0
        for (y in 0 until n) {
            var x = 0
            while (x < GRID) {
                val c = grid[y * GRID + x]
                var e = x + 1
                while (e < GRID && grid[y * GRID + e] == c) e++
                if (c != 0) count++
                x = e
            }
        }
        val runs = IntArray(count * 4)
        var k = 0
        for (y in 0 until n) {
            var x = 0
            while (x < GRID) {
                val c = grid[y * GRID + x]
                var e = x + 1
                while (e < GRID && grid[y * GRID + e] == c) e++
                if (c != 0) {
                    runs[k] = x; runs[k + 1] = y; runs[k + 2] = e - x; runs[k + 3] = c
                    k += 4
                }
                x = e
            }
        }
        var maxHalf = 0f
        for (y in 0 until n) maxHalf = maxOf(maxHalf, half[y])
        return Sprite(n, min(GRID, (maxHalf * 2).toInt() + 1), runs)
    }

    private fun shadeBody(g: Glaze, u: Float, x: Int, y: Int, row: Int, rows: Int, stepOut: Boolean): Int {
        if (stepOut) return g.outline
        // shadow under the rim
        if (row <= 2) return if (u < -0.6f) g.mid else g.dark
        var c = when {
            u < -0.72f -> g.light
            u < -0.5f -> if (g.glazed) g.hi else g.light
            u > 0.72f -> g.dark
            u > 0.42f -> if ((x + y) % 2 == 0) g.dark else g.mid // dithered core shadow
            else -> g.mid
        }
        // a soft glossy reflection on glazed pots
        if (g.glazed && u > -0.42f && u < -0.32f && row in 5 until rows - 6) c = g.light
        // speckled clay texture
        val h = hash01(x, y, 31)
        if (!g.glazed && h < 0.05f) c = if (c == g.dark) g.outline else g.dark
        else if (!g.glazed && h > 0.97f && c != g.dark) c = g.hi
        return c
    }

    // endregion
}

/** A pot for lists (shop): the visible pot fills the box width or height, bottom aligned. */
@Composable
fun PotPreview(potId: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val aspect = PotRenderer.aspect(potId)
        val visible = PotRenderer.visibleWidth(potId)
        val w = min(size.width / visible, size.height / aspect)
        val left = (size.width - w) / 2
        PotRenderer.draw(this, potId, Rect(left, size.height - w * aspect, left + w, size.height))
    }
}
