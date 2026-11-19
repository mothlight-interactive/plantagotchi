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
import kotlin.math.sin

/**
 * The cozy room (default scene): window with the city outside, windowsill with radiator, curtain,
 * shelf with books, picture, hanging planter, desk with a switchable lamp and a steaming cup, a
 * cat on the sill. Indoors gets darker more slowly than outside, the desk lamp warms it at night.
 */
internal class CozyRoom(size: Size, floorTop: Float, pixel: Float) : IndoorBase(size, floorTop, pixel) {
    override val scenery = Scenery.ROOM

    val glass = Rect(snap(w * 0.45f), snap(h * 0.16f), snap(w * 0.94f), snap(h * 0.42f))
    override val windows = listOf(glass)
    val frame = Rect(glass.left - px(8), glass.top - px(8), glass.right + px(8), glass.bottom + px(5))

    /** Top surface and front edge of the windowsill board. */
    val sill = Rect(glass.left - px(13), frame.bottom, glass.right + px(13), frame.bottom + px(10))
    val radiator = Rect(
        snap(glass.left + glass.width * 0.1f), sill.bottom + px(8),
        snap(glass.right - glass.width * 0.1f), minOf(floorTop - px(16), snap(sill.bottom + px(8) + h * 0.11f)),
    )
    val shelf = Rect(snap(w * 0.03f), snap(h * 0.47f), snap(w * 0.38f), snap(h * 0.47f) + px(5))
    val picture = Rect(snap(w * 0.05f), snap(h * 0.25f), snap(w * 0.17f), snap(h * 0.34f))

    /** The hook in the ceiling and the bottom of the hanging planter. */
    val hangX = snap(w * 0.25f)
    val hangBottom = snap(h * 0.31f)
    val desk = Rect(snap(w * 0.03f), snap(floorTop - h * 0.15f), snap(w * 0.42f), snap(floorTop - h * 0.15f) + px(7))

    /** Desk lamp: shade and stem (also the tap area). */
    val deskLamp = Rect(desk.left + px(3), desk.top - px(46), desk.left + px(35), desk.top)
    val cup = Offset(snap(w * 0.33f), desk.top)

    /** The cat sits at the right end of the windowsill. */
    val cat = Rect(sill.right - px(32), sill.top - px(CatArt.height), sill.right - px(32 - CatArt.width), sill.top)

    /** The wall around the window frame. */
    private val wallParts = listOf(
        Rect(0f, 0f, w, frame.top),
        Rect(0f, frame.bottom, w, floorTop),
        Rect(0f, frame.top, frame.left, frame.bottom),
        Rect(frame.right, frame.top, w, frame.bottom),
    )
    private val rug = Rect(snap(w * 0.46f), floorTop + px(22), snap(w * 0.98f), floorTop + px(52))

    override fun anchor(spot: RoomSpot): Pair<Offset, Float> = when (spot) {
        RoomSpot.SILL_LEFT -> Offset(glass.left + glass.width * 0.24f, sill.top) to w * 0.15f
        RoomSpot.SILL_RIGHT -> Offset(glass.left + glass.width * 0.6f, sill.top) to w * 0.15f
        RoomSpot.SHELF -> Offset(w * 0.27f, shelf.top) to w * 0.13f
        RoomSpot.DESK -> Offset(w * 0.25f, desk.top) to w * 0.14f
        RoomSpot.HANGING -> Offset(hangX, hangBottom) to w * 0.14f
        RoomSpot.FLOOR_LEFT -> Offset(w * 0.6f, floorTop + w * 0.02f) to w * 0.24f
        RoomSpot.FLOOR_RIGHT -> Offset(w * 0.87f, floorTop + w * 0.02f) to w * 0.2f
    }

    override fun toyAt(offset: Offset): String? = when {
        hit(deskLamp, offset) -> IndoorToy.DESK_LAMP
        hit(cat, offset) -> IndoorToy.CAT
        else -> null
    }

    override fun draw(scope: DrawScope, tint: Float, time: Float, toys: IndoorToys) = with(scope) {
        beginFrame(tint, Dusk, if (toys.deskLampOn) 1f else 0f)
        outside(tint, time)
        wall()
        picture()
        window()
        curtain(time)
        sill()
        shelf()
        hanger()
        floor()
        desk()
        deskLamp(toys.deskLampOn)
        cup(time)
        cat(time, toys.catFlickAt)
        if (toys.deskLampOn) {
            // Warm pool of light, stronger at night
            val center = Offset(deskLamp.left + px(16), deskLamp.top + px(20))
            val radius = w * (0.6f + 0.35f * night)
            drawCircle(
                Brush.radialGradient(listOf(Warm.copy(alpha = 0.14f + 0.36f * night), Color.Transparent), center, radius),
                radius, center,
            )
        }
    }

    /** City behind the glass; the sky is already drawn by the scene. */
    private fun DrawScope.outside(tint: Float, time: Float) {
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            city(Rect(glass.left, glass.top + glass.height * 0.38f, glass.right, glass.bottom), p, p * 4f / PIXEL_DP, tint, time)
        }
    }

    private fun DrawScope.wall() {
        fun paint(r: Rect) {
            if (r.width <= 0f || r.height <= 0f) return
            fill(Wall, r)
            // Wallpaper: soft stripes with a small diamond pattern
            var x = px(9)
            var col = 0
            while (x < w) {
                if (x + px(4) > r.left && x < r.right) {
                    val sx = maxOf(x, r.left)
                    fill(WallStripe, sx, r.top, minOf(x + px(4), r.right), r.bottom)
                }
                val dx = x + px(11)
                if (dx > r.left && dx + px(2) < r.right) {
                    var y = snap(r.top / px(24)) * px(24) + px(if (col % 2 == 0) 6 else 18)
                    while (y < r.bottom - px(2)) {
                        if (y > r.top) {
                            box(WallDot, dx, y, 2, 1)
                            box(WallDot, dx + p * 0.5f, y - p, 1, 3)
                        }
                        y += px(24)
                    }
                }
                x += px(26)
                col++
            }
        }
        for (i in wallParts.indices) paint(wallParts[i])
        // Ceiling with crown molding, shadow under it
        fill(Color(0xFFD7BD95), 0f, 0f, w, px(5))
        fill(Color(0xFFF4EBDD), 0f, px(5), w, px(7))
        fill(Color(0xFFCDB089), 0f, px(7), w, px(8))
        // Baseboard
        fill(Color(0xFFF4EBDD), 0f, floorTop - px(8), w, floorTop)
        fill(Color(0xFFFFF8EC), 0f, floorTop - px(8), w, floorTop - px(7))
        fill(Color(0xFFD9CBB4), 0f, floorTop - px(2), w, floorTop)
        // Light switch next to the desk
        box(Color(0xFFF4EBDD), snap(w * 0.44f), snap(floorTop - h * 0.24f), 5, 8)
        box(Color(0xFFC9BBA4), snap(w * 0.44f) + px(2), snap(floorTop - h * 0.24f) + px(2), 1, 4)
    }

    private fun DrawScope.picture() {
        val pic = picture
        fill(Color(0xFF5A3A26), pic)
        fill(WoodLight, pic.left, pic.top, pic.right, pic.top + p)
        val inner = Rect(pic.left + px(3), pic.top + px(3), pic.right - px(3), pic.bottom - px(3))
        // Sunset: sky bands, sun, hills and a little tree
        val bands = inner.height * 0.5f
        fill(Color(0xFFF7D9A6), inner.left, inner.top, inner.right, inner.top + snap(bands * 0.35f))
        fill(Color(0xFFF2C38B), inner.left, inner.top + snap(bands * 0.35f), inner.right, inner.top + snap(bands * 0.7f))
        fill(Color(0xFFE9A06E), inner.left, inner.top + snap(bands * 0.7f), inner.right, inner.top + snap(bands))
        box(Color(0xFFFFF1C2), snap(inner.left + inner.width * 0.55f), snap(inner.top + bands * 0.45f), 6, 6)
        fill(Color(0xFFE48A5A), inner.left, inner.top + snap(bands), inner.right, inner.top + snap(inner.height * 0.62f))
        fill(Color(0xFF8BA466), inner.left, inner.top + snap(inner.height * 0.62f), inner.right, inner.bottom)
        fill(Color(0xFF6F8F5A), inner.left, inner.top + snap(inner.height * 0.8f), inner.right, inner.bottom)
        val tx = snap(inner.left + inner.width * 0.25f)
        box(Color(0xFF4A3424), tx + px(2), snap(inner.top + inner.height * 0.55f), 1, 6)
        box(Color(0xFF4F7A3A), tx, snap(inner.top + inner.height * 0.45f), 5, 5)
        // Nail and string
        line(Color(0xFF6B4A33), pic.center.x, pic.top - px(10), pic.left + px(4), pic.top)
        line(Color(0xFF6B4A33), pic.center.x, pic.top - px(10), pic.right - px(4), pic.top)
        box(Color(0xFF3A2A20), pic.center.x - p, pic.top - px(11), 2, 2)
        // Shadow on the wall
        fill(ShadowOnWall, pic.right, pic.top + px(2), pic.right + px(2), pic.bottom + px(2))
        fill(ShadowOnWall, pic.left + px(2), pic.bottom, pic.right, pic.bottom + px(2))
    }

    private fun DrawScope.window() {
        // Frame with bevels: light on top-left, shadow on bottom-right
        fill(Frame, frame.left, frame.top, frame.right, glass.top)
        fill(Frame, frame.left, glass.top, glass.left, frame.bottom)
        fill(Frame, glass.right, glass.top, frame.right, frame.bottom)
        fill(Frame, glass.left, glass.bottom, glass.right, frame.bottom)
        fill(FrameLight, frame.left, frame.top, frame.right, frame.top + p)
        fill(FrameLight, frame.left, frame.top, frame.left + p, frame.bottom)
        fill(FrameShadow, frame.right - p, frame.top, frame.right, frame.bottom)
        // Inner reveal
        fill(FrameShadow, glass.left, glass.top, glass.right, glass.top + px(2))
        fill(FrameShadow, glass.left, glass.top, glass.left + px(2), glass.bottom)
        // Mullion, transom and handle
        val midX = snap(glass.center.x) - px(2)
        val transom = snap(glass.top + glass.height * 0.3f)
        fill(Frame, midX, glass.top, midX + px(5), glass.bottom)
        fill(FrameShadow, midX + px(5), glass.top, midX + px(6), glass.bottom)
        fill(Frame, glass.left, transom, glass.right, transom + px(4))
        fill(FrameShadow, glass.left, transom + px(4), glass.right, transom + px(5))
        fill(FrameLight, midX, glass.top, midX + p, glass.bottom)
        box(Color(0xFFB9B2A4), midX - px(3), snap(glass.center.y + glass.height * 0.1f), 2, 7)
        box(Color(0xFFD8D2C5), midX - px(4), snap(glass.center.y + glass.height * 0.1f), 3, 2)
        // Reflections: two diagonal streaks per pane
        val shine = Color.White.copy(alpha = 0.16f * (1f - night * 0.7f))
        for (pane in 0..1) {
            val x0 = if (pane == 0) glass.left + px(10) else midX + px(14)
            val y0 = transom + px(20)
            line(shine, x0, y0, x0 + px(12), y0 - px(12), thick = 2, shaded = false)
            line(shine, x0 + px(7), y0, x0 + px(15), y0 - px(8), shaded = false)
        }
    }

    private fun DrawScope.curtain(time: Float) {
        // Rod with finials
        val rodY = frame.top - px(10)
        fill(Rod, frame.left - px(16), rodY, frame.right + px(14), rodY + px(3))
        box(Rod, frame.left - px(20), rodY - px(2), 5, 7)
        box(Rod, frame.right + px(12), rodY - px(2), 5, 7)
        for (i in 0 until 4) box(RodRing, frame.right - px(4 + i * 5), rodY - p, 3, 5)
        // One heavy curtain on the right, tied back a third of the way down
        val right = frame.right + px(10)
        val top = rodY + px(3)
        val tie = snap(glass.top + glass.height * 0.62f)
        val sway = snap(sin(time * 0.5f) * p * 2.5f)
        var y = top
        while (y < sill.top) {
            val width = when {
                y < tie -> px(22) - snap((y - top) / (tie - top) * px(8))
                else -> px(14) + snap((y - tie) / (sill.top - tie) * px(10)) + sway
            }
            val left = right - width
            val rows = px(3)
            fill(Curtain, left, y, right, y + rows)
            // Folds: dark and light vertical lines
            var fx = left + px(2)
            var i = 0
            while (fx < right - p) {
                fill(if (i % 2 == 0) CurtainFold else CurtainLight, fx, y, fx + p, y + rows)
                fx += px(4)
                i++
            }
            y += rows
        }
        fill(CurtainFold, right - px(2), top, right, sill.top)
        // Tie-back
        box(Color(0xFFD9A441), right - px(15), tie - px(2), 15, 3)
        box(Color(0xFFB58632), right - px(15), tie + p, 15, 1)
    }

    private fun DrawScope.sill() {
        fill(Color(0xFFF2EEE4), sill.left, sill.top, sill.right, sill.top + px(4))
        fill(Color(0xFFFFFBF2), sill.left, sill.top, sill.right, sill.top + p)
        fill(Color(0xFFD9D2C3), sill.left, sill.top + px(4), sill.right, sill.bottom)
        fill(Color(0xFFC4BCAB), sill.left, sill.bottom - p, sill.right, sill.bottom)
        fill(ShadowOnWall, sill.left + px(2), sill.bottom, sill.right - px(2), sill.bottom + px(3))
        // Radiator with fins, pipes and a valve
        val r = radiator
        if (r.height > px(6)) {
            fill(Color(0xFFEDEAE2), r)
            fill(Color(0xFFFAF8F2), r.left, r.top, r.right, r.top + p)
            var x = r.left + px(2)
            while (x < r.right - px(3)) {
                fill(Color(0xFFCFCBC1), x + px(3), r.top + px(2), x + px(4), r.bottom - px(2))
                fill(Color(0xFFFAF8F2), x, r.top + px(2), x + p, r.bottom - px(2))
                x += px(6)
            }
            fill(Color(0xFFBDB8AC), r.left, r.bottom - px(2), r.right, r.bottom)
            box(Color(0xFFBDB8AC), r.left - px(6), r.bottom - px(5), 6, 2)
            box(Color(0xFFBDB8AC), r.left - px(6), r.bottom - px(5), 2, ((floorTop - r.bottom) / p).toInt() + 5)
            box(Color(0xFFB5524A), r.left - px(8), r.bottom - px(8), 6, 3)
            fill(ShadowOnWall, r.left + px(2), r.bottom, r.right, r.bottom + px(2))
        }
    }

    private fun DrawScope.shelf() {
        val s = shelf
        fill(Wood, s)
        fill(WoodLight, s.left, s.top, s.right, s.top + p)
        fill(WoodDark, s.left, s.bottom - p, s.right, s.bottom)
        fill(ShadowOnWall, s.left + px(2), s.bottom, s.right, s.bottom + px(2))
        // Brackets
        for (i in 0..1) {
            val bx = if (i == 0) s.left + px(6) else s.right - px(9)
            box(WoodDark, bx, s.bottom, 3, 9)
            box(WoodDark, bx - px(3), s.bottom, 9, 2)
            line(WoodDark, bx - px(2), s.bottom + px(2), bx, s.bottom + px(6))
        }
        // Books, a leaning one and a small clock
        var x = s.left + px(5)
        for (i in Books.indices) {
            val bh = BookHeights[i]
            box(Books[i], x, s.top - px(bh), 5, bh)
            box(BookBand, x, s.top - px(bh - 3), 5, 1)
            box(BookBand, x, s.top - px(5), 5, 1)
            box(Color(0x33000000), x + px(4), s.top - px(bh), 1, bh)
            x += px(5)
        }
        line(Color(0xFF8A6FA8), x + px(2), s.top - p, x + px(9), s.top - px(18), thick = 4)
        x += px(14)
        box(Color(0xFF6B4A33), x, s.top - px(11), 11, 11)
        box(Color(0xFFF4EBDD), x + px(2), s.top - px(9), 7, 7)
        box(Color(0xFF3A2A20), x + px(5), s.top - px(8), 1, 4)
        box(Color(0xFF3A2A20), x + px(5), s.top - px(5), 3, 1)
    }

    override val hangRing: Offset get() {
        val potWidth = w * 0.14f
        return Offset(hangX, snap(hangBottom - potWidth * 0.62f - potWidth * 0.9f))
    }

    private fun DrawScope.hanger() {
        box(Rod, hangX - px(3), 0f, 7, 7)
        // A rope down to the ring; the cords around a pot are drawn with the plant hanging here
        val ring = hangRing
        fill(Rope, hangX, px(7), hangX + p, ring.y)
        box(Rope, ring.x - px(2), ring.y - px(2), 5, 3)
        box(RopeDark, ring.x - px(2), ring.y + p, 5, 1)
    }

    private fun DrawScope.floor() {
        val top = floorTop
        var y = top
        var row = 0
        while (y < h) {
            val plank = px(11)
            fill(if (row % 2 == 0) FloorA else FloorB, 0f, y, w, y + plank)
            fill(FloorLight, 0f, y, w, y + p)
            fill(WoodDark, 0f, y + plank, w, y + plank + p)
            var x = px(((row * 37) % 60) - 20)
            var k = 0
            while (x < w) {
                fill(WoodDark, x, y, x + p, y + plank)
                // Grain
                val gx = x + px(12 + (k * 7 + row * 5) % 30)
                fill(FloorGrain, gx, y + px(4 + (k + row) % 4), gx + px(9), y + px(5 + (k + row) % 4))
                x += px(70)
                k++
            }
            y += plank + p
            row++
        }
        // Shadow line along the wall
        fill(Color(0x40000000), 0f, top, w, top + px(2))
        // Rug in front of the floor plants, with fringes and a pattern
        fill(RugBase, rug)
        fill(RugRed, rug.left + px(2), rug.top + px(2), rug.right - px(2), rug.top + px(4))
        fill(RugRed, rug.left + px(2), rug.bottom - px(4), rug.right - px(2), rug.bottom - px(2))
        var rx = rug.left + px(6)
        var k = 0
        while (rx < rug.right - px(8)) {
            val c = if (k % 2 == 0) RugBlue else RugRed
            box(c, rx, rug.top + px(10), 4, 2)
            box(c, rx + p, rug.top + px(9), 2, 4)
            box(RugGold, rx + px(6), rug.top + px(18), 2, 2)
            rx += px(12)
            k++
        }
        var fx = rug.left - px(2)
        while (fx < rug.right + px(1)) {
            box(RugBase, fx, rug.top + px(1), 1, 2)
            box(RugBase, fx, rug.bottom - px(1), 1, 2)
            fx += px(3)
        }
        fill(Color(0x26000000), rug.left + px(2), rug.bottom + px(1), rug.right, rug.bottom + px(3))
    }

    private fun DrawScope.desk() {
        val d = desk
        val legBottom = floorTop + px(12)
        // Shadow under the desk
        fill(Color(0x30000000), d.left + px(2), floorTop + px(8), d.right + px(4), legBottom + px(2))
        for (i in 0..1) {
            val x = if (i == 0) d.left + px(4) else d.right - px(9)
            fill(WoodDark, x, d.bottom, x + px(5), legBottom)
            fill(Wood, x, d.bottom, x + px(2), legBottom)
        }
        // Drawer unit
        val dw = snap(d.width * 0.45f)
        val dl = d.left + px(10)
        fill(Wood, dl, d.bottom, dl + dw, d.bottom + px(17))
        fill(WoodDark, dl, d.bottom + px(16), dl + dw, d.bottom + px(17))
        fill(WoodDark, dl, d.bottom + px(8), dl + dw, d.bottom + px(9))
        box(WoodLight, dl + dw / 2 - px(3), d.bottom + px(3), 6, 2)
        box(WoodLight, dl + dw / 2 - px(3), d.bottom + px(12), 6, 2)
        // Top
        fill(WoodLight, d.left, d.top, d.right, d.top + px(2))
        fill(Wood, d.left, d.top + px(2), d.right, d.bottom)
        fill(WoodDark, d.left, d.bottom - p, d.right, d.bottom)
        // Notebook and pencil
        box(Color(0xFFF4EBDD), snap(w * 0.12f), d.top - px(2), 18, 2)
        box(Color(0xFF4E7A8C), snap(w * 0.12f), d.top - px(3), 18, 1)
        box(Color(0xFFD9A441), snap(w * 0.12f) + px(4), d.top - px(4), 12, 1)
    }

    private fun DrawScope.deskLamp(on: Boolean) {
        val l = deskLamp
        val x = l.left + px(16) // stem
        val metal = Color(0xFF6B4A33)
        box(metal, x - px(8), l.bottom - px(3), 17, 3)
        box(Color(0xFF8A6448), x - px(8), l.bottom - px(3), 17, 1)
        // Stem with a joint
        fill(metal, x, l.top + px(18), x + px(2), l.bottom - px(3))
        box(Color(0xFF8A6448), x - p, l.top + px(30), 4, 3)
        val shade = if (on) Color(0xFFFFE3A0) else Color(0xFFE8D9B8)
        val shadeDark = if (on) Color(0xFFF2C46A) else Color(0xFFCDB997)
        val shadeEdge = if (on) Color(0xFFE6AE52) else Color(0xFFB9A684)
        // Shade: trapezoid widening downwards, with vertical pleats
        for (row in 0 until 17) {
            val half = 7 + row / 2
            val left = x + p - px(half)
            val color = if (on) shade else c(shade)
            drawRect(color, Offset(left, l.top + px(row)), Size(px(half * 2), p))
            var k = 0
            while (k < half * 2) {
                drawRect(if (on) shadeDark else c(shadeDark), Offset(left + px(k + 1), l.top + px(row)), Size(p, p))
                k += 4
            }
        }
        drawRect(if (on) shadeEdge else c(shadeEdge), Offset(x + p - px(15), l.top + px(16)), Size(px(30), px(2)))
        drawRect(if (on) shadeEdge else c(shadeEdge), Offset(x + p - px(7), l.top), Size(px(14), p))
        if (on) {
            glow(Color(0xFFFFF6D6), x - px(3), l.top + px(18), 8, 2)
            // Soft cone of light down to the desk
            drawRect(Color(0xFFFFE9B0), Offset(x - px(14), l.top + px(18)), Size(px(32), l.bottom - l.top - px(18)), alpha = 0.10f + 0.12f * night)
        }
    }

    private fun DrawScope.cup(time: Float) {
        val pt = cup
        val mug = Color(0xFFF4F0E8)
        box(mug, pt.x, pt.y - px(15), 14, 15)
        box(Color(0xFFFFFFFF), pt.x + p, pt.y - px(14), 2, 12)
        box(Color(0xFFD9D2C3), pt.x, pt.y - px(3), 14, 3)
        box(Color(0xFFD9D2C3), pt.x + px(12), pt.y - px(15), 2, 13)
        // Handle
        box(mug, pt.x + px(14), pt.y - px(12), 4, 2)
        box(mug, pt.x + px(17), pt.y - px(11), 2, 6)
        box(mug, pt.x + px(14), pt.y - px(6), 4, 2)
        // Coffee and a heart
        box(Color(0xFF6B4A33), pt.x + px(2), pt.y - px(15), 10, 2)
        box(Color(0xFFB5524A), pt.x + px(4), pt.y - px(10), 2, 2)
        box(Color(0xFFB5524A), pt.x + px(7), pt.y - px(10), 2, 2)
        box(Color(0xFFB5524A), pt.x + px(5), pt.y - px(8), 3, 2)
        // Steam: wisps rising and fading
        for (i in 0 until 4) {
            val phase = (time * 0.5f + i / 4f) % 1f
            val y = pt.y - px(18) - snap(phase * px(26))
            val x = pt.x + px(3 + (i % 3) * 3) + snap(sin(time * 2f + i * 2f + phase * 4f) * px(2))
            glow(Color.White, x, y, 1, 2, alpha = 0.55f * (1f - phase))
        }
    }

    private fun DrawScope.cat(time: Float, flickAt: Float) {
        CatArt.draw(this, cat.left, cat.top, p, this@CozyRoom)
        // Ear twitch now and then, eyes blink
        val blink = (time % 4.3f) < 0.15f
        if (!blink) {
            glow(c(Color(0xFFE8D25A)), cat.left + px(5), cat.top + px(6), 2, 1)
            glow(c(Color(0xFFE8D25A)), cat.left + px(10), cat.top + px(6), 2, 1)
        }
        // Tail hangs over the edge of the sill and swishes now and then (faster after a tap)
        val since = time - flickAt
        val swish = if (since in 0f..1.5f) sin(since * 14f) * 2.2f else sin(time * 0.8f) * 0.9f
        val tailX = cat.left + px(17)
        val body = Color(0xFF1F1A22)
        for (i in 0 until 18) {
            val dx = snap((swish * i * i / 120f) * px(3))
            box(body, tailX + dx, sill.top + px(i - 2), 3, 1)
        }
    }

    private companion object {
        val Wall = Color(0xFFE6C796)
        val WallStripe = Color(0xFFDFBF8C)
        val WallDot = Color(0xFFD4B07E)
        val ShadowOnWall = Color(0x30000000)
        val Dusk = Color(0xFF2B1B14)
        val Wood = Color(0xFFA9774A)
        val WoodDark = Color(0xFF7A5131)
        val WoodLight = Color(0xFFC79563)
        val Frame = Color(0xFFF2EEE4)
        val FrameLight = Color(0xFFFFFFFA)
        val FrameShadow = Color(0xFFC9C1B0)
        val Warm = Color(0xFFFFD58A)
        val Rod = Color(0xFF5A3A2A)
        val RodRing = Color(0xFF7A5A40)
        val Curtain = Color(0xFF8C5A3C)
        val CurtainFold = Color(0xFF6E4430)
        val CurtainLight = Color(0xFFA06A48)
        val Rope = Color(0xFF8B6B4A)
        val RopeDark = Color(0xFF6B4F35)
        val FloorA = Color(0xFFB07A4C)
        val FloorB = Color(0xFFA06E44)
        val FloorLight = Color(0xFFBE8A5A)
        val FloorGrain = Color(0xFF946540)
        val RugBase = Color(0xFFE3D3B4)
        val RugRed = Color(0xFFB5524A)
        val RugBlue = Color(0xFF4E7A8C)
        val RugGold = Color(0xFFD9A441)
        val BookBand = Color(0x55FFFFFF)
        val Books = listOf(Color(0xFF4E7A8C), Color(0xFFB5524A), Color(0xFF6E8F4E), Color(0xFFD9A441), Color(0xFF3F5A78))
        val BookHeights = intArrayOf(22, 27, 20, 25, 23)

        val CatArt = PixelArt(
            listOf(
                "...x.........x...",
                "...xx.......xx...",
                "...xxx.....xxx...",
                "...xxxxxxxxxxx...",
                "..xxxxxxxxxxxxx..",
                "..xxxxxxxxxxxxx..",
                "..xxxxxxxxxxxxx..",
                "..xxxxxpxpxxxxx..",
                "...xxxxxpxxxxx...",
                "....xxxxxxxxx....",
                ".....xxxxxxx.....",
                "....xxxxxxxxx....",
                "...xxxxxxxxxxx...",
                "...xxxxxxxxxxx...",
                "..xxxxxxxxxxxxx..",
                "..xxxxxxxxxxxxx..",
                "..xxxxxxxxxxxxx..",
                ".xxxxxxxxxxxxxxx.",
                ".xxxxxxxxxxxxxxx.",
                ".xxxxxxxxxxxxxxx.",
                "xxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxx",
                ".xxxxxxxxxxxxxxx.",
                ".xxwxxxxxxxxxwxx.",
                "..xxxxx...xxxxx..",
            ),
            mapOf('x' to Color(0xFF1F1A22), 'p' to Color(0xFF3A2A33), 'w' to Color(0xFF2E2730)),
        )
    }
}
