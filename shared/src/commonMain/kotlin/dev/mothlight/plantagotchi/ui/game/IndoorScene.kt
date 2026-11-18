package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Scenery
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * An indoor scene (cozy room, fireplace, plant shop). Built for one canvas [size] and [floorTop]
 * (where the wall meets the floor); positions are fractions of the canvas, object sizes are in art
 * pixels of [pixel] px, so a shop preview gets the same scene in small.
 *
 * Draw order for the game: sky, weather clipped to each of [windows], then [draw], then the plants
 * on [anchor]s. Taps go to [toyAt] first; a hit is passed to [IndoorToys.tap].
 */
internal interface IndoorScene {
    val scenery: Scenery
    val size: Size
    val floorTop: Float

    /** One art pixel in px (whole px on screen, so neighbouring rects leave no seams). */
    val pixel: Float

    /** Window glass: the sky and weather show through these, clip rain and clouds to them. */
    val windows: List<Rect>

    /** Bottom center of a pot standing on [spot] and the pot width, in px. */
    fun anchor(spot: RoomSpot): Pair<Offset, Float>

    /** The toy at [offset] ([IndoorToy] id), if any. Tap areas are a little larger than the art. */
    fun toyAt(offset: Offset): String?

    /** Draws the room. [tint] is the night tint of the sky (0 = day, 0.4 = night). */
    fun draw(scope: DrawScope, tint: Float, time: Float, toys: IndoorToys)
}

/** Ids of the tappable things in indoor scenes. */
internal object IndoorToy {
    /** Cozy room: switches the desk lamp. */
    const val DESK_LAMP = "desk_lamp"

    /** Cozy room: the cat flicks its tail. */
    const val CAT = "cat"

    /** Fireplace: the fire flares up and sends sparks. */
    const val FIRE = "fire"

    /** Plant shop: switches the string lights. */
    const val STRING_LIGHTS = "string_lights"
}

/** State of the toys in indoor scenes; keep one per game screen (it survives scene switches). */
internal class IndoorToys {
    var deskLampOn = true
    var catFlickAt = -10f
    var sparksAt = -10f
    var lightsOn = true
    var lightsSwitchedAt = -10f

    /** Reacts to a tap on [toy] at scene [time]. */
    fun tap(toy: String, time: Float) {
        when (toy) {
            IndoorToy.DESK_LAMP -> deskLampOn = !deskLampOn
            IndoorToy.CAT -> catFlickAt = time
            IndoorToy.FIRE -> sparksAt = time
            IndoorToy.STRING_LIGHTS -> {
                lightsOn = !lightsOn
                lightsSwitchedAt = time
            }
        }
    }
}

internal object IndoorScenes {
    // A few recent scenes (game plus shop previews), newest last
    private val cache = ArrayList<IndoorScene>()

    /**
     * The indoor scene for [scenery], or null for outdoor scenes. [pixel] is one art pixel in px,
     * normally `PIXEL_DP * density`. The last scene is reused while nothing changes, so this can
     * be called every frame.
     */
    fun forScenery(scenery: Scenery, size: Size, floorTop: Float, pixel: Float): IndoorScene? {
        if (!scenery.indoor) return null
        val whole = wholePixel(pixel)
        for (i in cache.indices) {
            val it = cache[i]
            if (it.scenery == scenery && it.size == size && it.floorTop == floorTop && it.pixel == whole) return it
        }
        val scene = when (scenery) {
            Scenery.FIREPLACE -> FireplaceRoom(size, floorTop, pixel)
            Scenery.PLANT_SHOP -> PlantShopRoom(size, floorTop, pixel)
            else -> CozyRoom(size, floorTop, pixel)
        }
        if (cache.size >= 8) cache.removeAt(0)
        cache += scene
        return scene
    }

    internal fun wholePixel(pixel: Float) = max(1f, round(pixel))
}

/** Maps a color to how it looks in the current light. */
internal fun interface Shade {
    fun c(color: Color): Color
}

/** Shared helpers of the indoor scenes: pixel grid, night shading, a few primitives. */
internal abstract class IndoorBase(
    final override val size: Size,
    final override val floorTop: Float,
    pixel: Float,
) : IndoorScene, Shade {
    final override val pixel: Float = IndoorScenes.wholePixel(pixel)
    protected val p: Float get() = pixel
    protected val w = size.width
    protected val h = size.height

    /** Snaps to the pixel grid. */
    protected fun snap(v: Float) = floor(v / pixel) * pixel

    /** [n] art pixels in px. */
    protected fun px(n: Int) = n * pixel
    protected fun px(n: Float) = snap(n * pixel)

    // Light of the current frame, set by [beginFrame].
    private var dim = 0f
    private var dusk = Color.Black
    protected var night = 0f
        private set

    /** Darkens towards [duskColor] at night; [lit] (0..1) is how much the room's lamps make up for it. */
    protected fun beginFrame(tint: Float, duskColor: Color, lit: Float) {
        night = (tint / 0.4f).coerceIn(0f, 1f)
        dusk = duskColor
        dim = night * (0.68f - 0.26f * lit.coerceIn(0f, 1f))
    }

    override fun c(color: Color): Color = if (dim <= 0f) color else lerp(color, dusk, dim)

    /** Shaded rect at ([x], [y]) px, [wPx] x [hPx] art pixels. */
    protected fun DrawScope.box(color: Color, x: Float, y: Float, wPx: Int, hPx: Int) =
        drawRect(c(color), Offset(x, y), Size(wPx * pixel, hPx * pixel))

    /** Shaded rect in px. */
    protected fun DrawScope.fill(color: Color, left: Float, top: Float, right: Float, bottom: Float) {
        if (right <= left || bottom <= top) return
        drawRect(c(color), Offset(left, top), Size(right - left, bottom - top))
    }

    protected fun DrawScope.fill(color: Color, r: Rect) = fill(color, r.left, r.top, r.right, r.bottom)

    /** Unshaded rect (light sources). */
    protected fun DrawScope.glow(color: Color, x: Float, y: Float, wPx: Int, hPx: Int, alpha: Float = 1f) =
        drawRect(color, Offset(x, y), Size(wPx * pixel, hPx * pixel), alpha)

    /** A line [thick] art pixels wide, drawn as merged pixel runs (ropes, wires, stems). */
    protected fun DrawScope.line(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, thick: Int = 1, shaded: Boolean = true) {
        val col = if (shaded) c(color) else color
        val t = thick * pixel
        if (abs(y1 - y0) >= abs(x1 - x0)) {
            // Mostly vertical: one run per column
            val down = y0 <= y1
            val xa = if (down) x0 else x1
            val ya = if (down) y0 else y1
            val xb = if (down) x1 else x0
            val yb = if (down) y1 else y0
            val top = snap(ya)
            val n = ((yb - ya) / pixel).toInt().coerceAtLeast(1)
            var start = 0
            var cur = snap(xa)
            for (i in 1..n) {
                val x = snap(xa + (xb - xa) * i / n)
                if (x != cur) {
                    drawRect(col, Offset(cur, top + start * pixel), Size(t, (i - start) * pixel))
                    start = i
                    cur = x
                }
            }
            drawRect(col, Offset(cur, top + start * pixel), Size(t, (n + 1 - start) * pixel))
        } else {
            val right = x0 <= x1
            val xa = if (right) x0 else x1
            val ya = if (right) y0 else y1
            val xb = if (right) x1 else x0
            val yb = if (right) y1 else y0
            val left = snap(xa)
            val n = ((xb - xa) / pixel).toInt().coerceAtLeast(1)
            var start = 0
            var cur = snap(ya)
            for (i in 1..n) {
                val y = snap(ya + (yb - ya) * i / n)
                if (y != cur) {
                    drawRect(col, Offset(left + start * pixel, cur), Size((i - start) * pixel, t))
                    start = i
                    cur = y
                }
            }
            drawRect(col, Offset(left + start * pixel, cur), Size((n + 1 - start) * pixel, t))
        }
    }

    /** Splits [area] into rects that leave out [holes] (window glass), computed once per scene. */
    protected fun around(area: Rect, holes: List<Rect>): List<Rect> {
        val ys = (listOf(area.top, area.bottom) + holes.flatMap { listOf(it.top, it.bottom) })
            .map { it.coerceIn(area.top, area.bottom) }.distinct().sorted()
        val result = ArrayList<Rect>()
        for (i in 0 until ys.size - 1) {
            val y0 = ys[i]
            val y1 = ys[i + 1]
            var x = area.left
            holes.filter { it.top <= y0 && it.bottom >= y1 }.sortedBy { it.left }.forEach { hole ->
                if (hole.left > x) result += Rect(x, y0, hole.left, y1)
                x = maxOf(x, hole.right)
            }
            if (x < area.right) result += Rect(x, y0, area.right, y1)
        }
        return result
    }

    /** Fills precomputed [parts] (see [around]). */
    protected fun DrawScope.fillParts(color: Color, parts: List<Rect>) {
        for (i in parts.indices) fill(color, parts[i])
    }

    /** Hit test helper: [r] grown by [slopPx] art pixels. */
    protected fun hit(r: Rect, offset: Offset, slopPx: Int = 4) = r.inflate(slopPx * pixel).contains(offset)
}

/**
 * Pixel art from strings, stored as horizontal runs so it draws with few rects. Each char is one
 * pixel, '.' and unknown chars are transparent.
 */
internal class PixelArt(rows: List<String>, palette: Map<Char, Color>) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size
    private val colors: List<Color> = palette.values.toList()
    private val keys: List<Char> = palette.keys.toList()
    private val runX: IntArray
    private val runY: IntArray
    private val runLength: IntArray
    private val runColor: IntArray

    init {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val ls = ArrayList<Int>()
        val cs = ArrayList<Int>()
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                val index = keys.indexOf(row[x])
                if (index < 0) {
                    x++
                    continue
                }
                var end = x + 1
                while (end < row.length && row[end] == row[x]) end++
                xs += x; ys += y; ls += end - x; cs += index
                x = end
            }
        }
        runX = xs.toIntArray(); runY = ys.toIntArray(); runLength = ls.toIntArray(); runColor = cs.toIntArray()
    }

    /** Draws with the top-left corner at ([x], [y]); [flip] mirrors horizontally. */
    fun draw(scope: DrawScope, x: Float, y: Float, pixel: Float, shade: Shade?, flip: Boolean = false, alpha: Float = 1f) {
        for (i in runX.indices) {
            val color = colors[runColor[i]]
            val left = if (flip) width - runX[i] - runLength[i] else runX[i]
            scope.drawRect(
                shade?.c(color) ?: color,
                Offset(x + left * pixel, y + runY[i] * pixel),
                Size(runLength[i] * pixel, pixel),
                alpha,
            )
        }
    }
}
