package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.mothlight.plantagotchi.ui.game.SceneryRenderer.city
import dev.mothlight.plantagotchi.ui.game.SceneryRenderer.snap
import kotlin.math.max
import kotlin.math.sin

/**
 * The cozy room, drawn in pixel blocks. Indoors gets darker more slowly than outside, and the desk
 * lamp warms the room at night. The plants and their pots are drawn by the scene on top.
 */
internal object RoomRenderer {
    private val Wall = Color(0xFFE6C796)
    private val WallStripe = Color(0xFFDCBA86)
    private val Dusk = Color(0xFF2B1B14)
    private val Wood = Color(0xFFA9774A)
    private val WoodDark = Color(0xFF7A5131)
    private val WoodLight = Color(0xFFC79563)
    private val Frame = Color(0xFFF2EEE4)
    private val FrameShadow = Color(0xFFC9C1B0)
    private val Warm = Color(0xFFFFD58A)

    fun draw(scope: DrawScope, g: RoomGeometry, block: Float, tint: Float, time: Float, deskLampOn: Boolean, catFlickAt: Float) = with(scope) {
        val night = (tint / 0.4f).coerceIn(0f, 1f)
        // Inside it gets darker towards a warm brown, not the blue night outside.
        fun c(color: Color) = androidx.compose.ui.graphics.lerp(color, Dusk, night * (if (deskLampOn) 0.45f else 0.65f))

        outside(g, block, tint, time)
        wall(g, block, ::c)
        picture(g, block, ::c)
        window(g, block, night, ::c)
        curtain(g, block, time, ::c)
        sill(g, block, ::c)
        shelf(g, block, ::c)
        hanger(g, block, ::c)
        floor(g, block, ::c)
        desk(g, block, ::c)
        deskLamp(g, block, deskLampOn, ::c)
        cup(g, block, time, ::c)
        cat(g, block, time, catFlickAt, ::c)
        if (deskLampOn) {
            // Warm pool of light, stronger at night
            val center = Offset(g.deskLamp.center.x, g.deskLamp.top + block * 6)
            val radius = g.size.width * (0.6f + 0.35f * night)
            drawCircle(
                Brush.radialGradient(listOf(Warm.copy(alpha = 0.16f + 0.38f * night), Color.Transparent), center, radius),
                radius, center,
            )
        }
    }

    /** City and hills behind the glass; the sky is already drawn by the scene. */
    private fun DrawScope.outside(g: RoomGeometry, block: Float, tint: Float, time: Float) {
        val glass = g.glass
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            city(Rect(glass.left, glass.top + glass.height * 0.4f, glass.right, glass.bottom), block, tint, time)
        }
    }

    private fun DrawScope.wall(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val hole = g.frame
        val w = g.size.width
        fun paint(r: Rect) {
            if (r.width <= 0f || r.height <= 0f) return
            drawRect(c(Wall), r.topLeft, r.size)
            var x = block * 4
            while (x < w) {
                if (x + block * 2 > r.left && x < r.right) {
                    val sx = max(x, r.left)
                    drawRect(c(WallStripe), Offset(sx, r.top), Size(minOf(x + block * 2, r.right) - sx, r.height))
                }
                x += block * 10
            }
        }
        val bottom = g.floorTop
        paint(Rect(0f, 0f, w, hole.top))
        paint(Rect(0f, hole.bottom, w, bottom))
        paint(Rect(0f, hole.top, hole.left, hole.bottom))
        paint(Rect(hole.right, hole.top, w, hole.bottom))
        // Ceiling edge and baseboard
        drawRect(c(Color(0xFFD7BD95)), Offset.Zero, Size(w, block * 2))
        drawRect(c(Color(0xFFF4EBDD)), Offset(0f, bottom - block * 3), Size(w, block * 3))
    }

    private fun DrawScope.picture(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val p = g.picture
        drawRect(c(WoodDark), p.topLeft, p.size)
        val inner = Rect(p.left + block, p.top + block, p.right - block, p.bottom - block)
        drawRect(c(Color(0xFFF2C38B)), inner.topLeft, inner.size)
        // Sunset hills
        drawRect(c(Color(0xFFE48A5A)), Offset(inner.left, inner.top + inner.height * 0.45f), Size(inner.width, inner.height * 0.2f))
        drawRect(c(Color(0xFF6F8F5A)), Offset(inner.left, inner.top + inner.height * 0.65f), Size(inner.width, inner.height * 0.35f))
        drawRect(c(Color(0xFFFFE9A8)), Offset(inner.left + inner.width * 0.6f, inner.top + inner.height * 0.2f), Size(block * 2, block * 2))
    }

    private fun DrawScope.window(g: RoomGeometry, block: Float, night: Float, c: (Color) -> Color) {
        val glass = g.glass
        val frame = g.frame
        drawRect(c(Frame), frame.topLeft, Size(frame.width, block * 3))
        drawRect(c(Frame), frame.topLeft, Size(block * 3, frame.height))
        drawRect(c(Frame), Offset(frame.right - block * 3, frame.top), Size(block * 3, frame.height))
        drawRect(c(Frame), Offset(frame.left, glass.bottom), Size(frame.width, block * 2))
        drawRect(c(FrameShadow), glass.topLeft, Size(glass.width, block))
        drawRect(c(FrameShadow), glass.topLeft, Size(block, glass.height))
        val midX = snap(glass.center.x, block) - block
        drawRect(c(Frame), Offset(midX, glass.top), Size(block * 2, glass.height))
        drawRect(c(FrameShadow), Offset(midX + block * 2, glass.top), Size(block, glass.height))
        // Handle
        drawRect(c(Color(0xFFB9B2A4)), Offset(midX - block, glass.center.y), Size(block, block * 3))
        // Reflections
        val shine = Color.White.copy(alpha = 0.16f * (1f - night * 0.7f))
        listOf(glass.left + block * 4, midX + block * 6).forEach { x0 ->
            for (i in 0 until 5) {
                drawRect(shine, Offset(x0 + i * block, glass.top + block * (7 - i)), Size(block, block))
                drawRect(shine, Offset(x0 + block * 3 + i * block, glass.top + block * (7 - i)), Size(block, block))
            }
        }
    }

    private fun DrawScope.curtain(g: RoomGeometry, block: Float, time: Float, c: (Color) -> Color) {
        val frame = g.frame
        val rod = c(Color(0xFF5A3A2A))
        drawRect(rod, Offset(frame.left - block * 6, frame.top - block * 4), Size(frame.width + block * 10, block * 2))
        drawRect(rod, Offset(frame.left - block * 7, frame.top - block * 5), Size(block * 2, block * 4))
        // One heavy curtain on the right, gathered at the bottom
        val curtain = c(Color(0xFF8C5A3C))
        val fold = c(Color(0xFF6E4430))
        val sway = snap(sin(time * 0.5f) * block, block)
        val right = frame.right + block * 4
        var y = frame.top - block * 2
        var row = 0
        while (y < g.sill.top) {
            val width = block * (7 + minOf(row / 14, 3)) + if (row > 20) sway else 0f
            val left = right - width
            drawRect(curtain, Offset(left, y), Size(width, block))
            var fx = left + block
            while (fx < right - block) {
                drawRect(fold, Offset(fx, y), Size(block, block))
                fx += block * 3
            }
            y += block
            row++
        }
    }

    private fun DrawScope.sill(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val sill = g.sill
        drawRect(c(Color(0xFFF2EEE4)), sill.topLeft, Size(sill.width, block * 2))
        drawRect(c(Color(0xFFD9D2C3)), Offset(sill.left, sill.top + block * 2), Size(sill.width, block * 2))
        drawRect(c(Color(0x33000000)), Offset(sill.left + block, sill.bottom), Size(sill.width - block * 2, block))
        // Radiator with fins
        val r = g.radiator
        if (r.height > block * 2) {
            drawRect(c(Color(0xFFEDEAE2)), r.topLeft, r.size)
            var x = r.left + block
            while (x < r.right - block) {
                drawRect(c(Color(0xFFCFCBC1)), Offset(x, r.top + block), Size(block, r.height - block * 2))
                x += block * 3
            }
            drawRect(c(Color(0xFFBDB8AC)), Offset(r.left, r.bottom - block), Size(r.width, block))
        }
    }

    private fun DrawScope.shelf(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val s = g.shelf
        drawRect(c(Wood), s.topLeft, s.size)
        drawRect(c(WoodDark), Offset(s.left, s.bottom), Size(s.width, block))
        // Brackets
        listOf(s.left + block * 2, s.right - block * 3).forEach { x ->
            drawRect(c(WoodDark), Offset(x, s.bottom), Size(block, block * 3))
            drawRect(c(WoodDark), Offset(x - block, s.bottom + block * 3), Size(block * 3, block))
        }
        // Books on the left half
        val books = listOf(Color(0xFF4E7A8C) to 9, Color(0xFFB5524A) to 11, Color(0xFF6E8F4E) to 8, Color(0xFFD9A441) to 10)
        var x = s.left + block * 2
        books.forEach { (color, height) ->
            val bh = block * height
            drawRect(c(color), Offset(x, s.top - bh), Size(block * 2, bh))
            drawRect(c(Color(0x22FFFFFF)), Offset(x, s.top - bh + block * 2), Size(block * 2, block))
            x += block * 2
        }
        // One book leaning
        drawRect(c(Color(0xFF8A6FA8)), Offset(x + block, s.top - block * 7), Size(block * 2, block * 7))
    }

    private fun DrawScope.hanger(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val (anchor, potWidth) = g.anchor(dev.mothlight.plantagotchi.data.state.RoomSpot.HANGING)
        val rope = c(Color(0xFF8B6B4A))
        val potTop = anchor.y - potWidth * 0.62f
        drawRect(c(Color(0xFF5A3A2A)), Offset(g.hangX - block, 0f), Size(block * 3, block * 2))
        // Three ropes from a ring to the rim of the planter
        val ring = Offset(g.hangX, potTop - potWidth * 0.9f)
        drawRect(rope, Offset(g.hangX, block * 2), Size(block, ring.y - block * 2))
        listOf(-0.45f, 0f, 0.45f).forEach { dx ->
            val end = Offset(g.hangX + potWidth * dx, potTop + block * 2)
            val steps = ((end.y - ring.y) / block).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                drawRect(rope, Offset(snap(ring.x + (end.x - ring.x) * t, block), ring.y + i * block), Size(block, block))
            }
        }
        drawRect(rope, Offset(ring.x - block, ring.y - block), Size(block * 3, block * 2))
    }

    private fun DrawScope.floor(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val top = g.floorTop
        val w = g.size.width
        var y = top
        var row = 0
        while (y < g.size.height) {
            val color = if (row % 2 == 0) Color(0xFFB07A4C) else Color(0xFFA06E44)
            drawRect(c(color), Offset(0f, y), Size(w, block * 5))
            drawRect(c(WoodDark), Offset(0f, y + block * 5), Size(w, block))
            var x = (row % 3) * block * 9f
            while (x < w) {
                drawRect(c(WoodDark), Offset(x, y), Size(block, block * 5))
                x += block * 26
            }
            y += block * 6
            row++
        }
        // Rug in front of the floor plants
        val rug = Rect(snap(w * 0.46f, block), top + block * 8, snap(w * 0.98f, block), top + block * 20)
        drawRect(c(Color(0xFFD8C7A8)), rug.topLeft, rug.size)
        drawRect(c(Color(0xFFB5524A)), Offset(rug.left + block, rug.top + block), Size(rug.width - block * 2, block))
        drawRect(c(Color(0xFFB5524A)), Offset(rug.left + block, rug.bottom - block * 2), Size(rug.width - block * 2, block))
    }

    private fun DrawScope.desk(g: RoomGeometry, block: Float, c: (Color) -> Color) {
        val d = g.desk
        val legBottom = g.floorTop + block * 4
        listOf(d.left + block * 2, d.right - block * 4).forEach { x ->
            drawRect(c(WoodDark), Offset(x, d.bottom), Size(block * 2, legBottom - d.bottom))
        }
        // Drawer
        drawRect(c(Wood), Offset(d.left + block * 4, d.bottom), Size(d.width * 0.45f, block * 6))
        drawRect(c(WoodLight), Offset(d.left + block * 4 + d.width * 0.2f, d.bottom + block * 2), Size(block * 3, block))
        drawRect(c(WoodLight), d.topLeft, Size(d.width, block))
        drawRect(c(Wood), Offset(d.left, d.top + block), Size(d.width, block * 2))
    }

    private fun DrawScope.deskLamp(g: RoomGeometry, block: Float, on: Boolean, c: (Color) -> Color) {
        val l = g.deskLamp
        val x = l.left + block * 5 // stem
        val metal = c(Color(0xFF6B4A33))
        drawRect(metal, Offset(x - block * 3, l.bottom - block * 2), Size(block * 7, block * 2))
        drawRect(metal, Offset(x, l.top + block * 7), Size(block, l.bottom - l.top - block * 9))
        val shade = if (on) Color(0xFFFFE3A0) else c(Color(0xFFE8D9B8))
        val shadeEdge = if (on) Color(0xFFF2C46A) else c(Color(0xFFCDBB97))
        // Shade: a trapezoid, 7 rows widening downwards, with a darker rim
        for (row in 0 until 7) {
            val half = 2 + row / 2
            drawRect(shade, Offset(x - block * half, l.top + block * row), Size(block * (half * 2 + 1), block))
        }
        drawRect(shadeEdge, Offset(x - block * 5, l.top + block * 6), Size(block * 11, block))
        if (on) drawRect(Color(0xFFFFF6D6), Offset(x - block, l.top + block * 7), Size(block * 3, block))
    }

    private fun DrawScope.cup(g: RoomGeometry, block: Float, time: Float, c: (Color) -> Color) {
        val p = g.cup
        val mug = c(Color(0xFFF4F0E8))
        drawRect(mug, Offset(p.x, p.y - block * 6), Size(block * 6, block * 6))
        drawRect(c(Color(0xFFD9D2C3)), Offset(p.x, p.y - block), Size(block * 6, block))
        drawRect(mug, Offset(p.x + block * 6, p.y - block * 5), Size(block * 2, block))
        drawRect(mug, Offset(p.x + block * 7, p.y - block * 4), Size(block, block * 2))
        drawRect(mug, Offset(p.x + block * 6, p.y - block * 2), Size(block * 2, block))
        drawRect(c(Color(0xFF6B4A33)), Offset(p.x + block, p.y - block * 6), Size(block * 4, block))
        drawRect(c(Color(0xFFB5524A)), Offset(p.x + block * 2, p.y - block * 4), Size(block * 2, block * 2))
        // Steam: three wisps rising and fading
        for (i in 0 until 3) {
            val phase = (time * 0.6f + i / 3f) % 1f
            val y = p.y - block * 7 - phase * block * 10
            val x = p.x + block * (1 + i * 2) + snap(sin(time * 2f + i * 2f) * block, block)
            drawRect(Color.White.copy(alpha = 0.6f * (1f - phase)), Offset(x, y), Size(block, block))
        }
    }

    private val catRows = listOf(
        "..x...x..",
        "..xx.xx..",
        "..xxxxx..",
        "..xxxxx..",
        "...xxx...",
        "..xxxxx..",
        ".xxxxxxx.",
        ".xxxxxxx.",
        "xxxxxxxxx",
        "xxxxxxxxx",
        "xxxxxxxxx",
        ".xxxxxxx.",
    )

    private fun DrawScope.cat(g: RoomGeometry, block: Float, time: Float, flickAt: Float, c: (Color) -> Color) {
        val body = c(Color(0xFF1F1A22))
        val origin = Offset(g.cat.left, g.cat.bottom - catRows.size * block)
        catRows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == 'x') drawRect(body, origin + Offset(x * block, y * block), Size(block, block))
            }
        }
        // Tail hangs over the edge of the sill and swishes now and then (faster after a tap)
        val since = time - flickAt
        val swish = if (since in 0f..1.5f) sin(since * 14f) * 2f else sin(time * 0.8f) * 0.8f
        val tailX = origin.x + block * 8
        for (i in 0 until 7) {
            val dx = snap((swish * i / 6f) * block, block)
            drawRect(body, Offset(tailX + dx + (if (i < 2) block else 0f), g.sill.top + block * (i - 1)), Size(block, block))
        }
    }
}
