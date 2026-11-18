package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Landscapes and grounds drawn in pixel blocks of size [block]. [tint] darkens colors at night
 * (0 = day, ~0.4 = night).
 */
internal object SceneryRenderer {
    private val nightColor = Color(0xFF0B0B1E)

    private fun shade(c: Color, tint: Float) = lerp(c, nightColor, (tint * 1.5f).coerceIn(0f, 0.7f))

    /** Snaps to the pixel grid. */
    private fun snap(v: Float, block: Float) = floor(v / block) * block

    /** Draws [scenery] into [area] (the part above the ground). */
    fun drawScenery(scope: DrawScope, scenery: Scenery, area: Rect, block: Float, tint: Float, time: Float, wind: Float, sprites: SceneSprites?) = with(scope) {
        when (scenery) {
            Scenery.MOUNTAINS -> {
                if (sprites != null) {
                    val img = sprites.mountains
                    val h = area.width * img.height / img.width
                    // The original art is aligned to the bottom of the screen, behind the ground.
                    drawBitmap(
                        img,
                        Rect(area.left, size.height - h, area.right, size.height),
                        colorFilter = ColorFilter.tint(Color.Black.copy(alpha = tint), BlendMode.SrcAtop),
                    )
                } else {
                    // Simple stand-in for previews
                    ridge(area, block, area.height * 0.45f, area.height * 0.25f, 0.015f, 1f, shade(Color(0xFF2F5A4A), tint))
                    ridge(area, block, area.height * 0.25f, area.height * 0.15f, 0.03f, 3f, shade(Color(0xFF244538), tint))
                }
            }
            Scenery.DESERT -> desert(area, block, tint)
            Scenery.BEACH -> beach(area, block, tint, time, wind)
            Scenery.FOREST -> forest(area, block, tint, time, wind)
            Scenery.CITY -> city(area, block, tint, time)
            Scenery.ZEN -> zen(area, block, tint, time, wind)
            Scenery.WINDOW -> window(area, block, tint, time)
        }
    }

    /** The glass of the [Scenery.WINDOW] scenery; the weather is clipped to it. */
    fun windowGlass(area: Rect, block: Float): Rect {
        val left = snap(area.left + area.width * 0.12f, block)
        val right = snap(area.left + area.width * 0.88f, block)
        val top = snap(area.bottom * 0.2f, block)
        val bottom = snap(area.bottom - block * 3, block)
        return Rect(left, top, right, bottom)
    }

    private val wallColor = Color(0xFFE9D8BC)
    private val wallStripe = Color(0xFFE0CCAC)

    /** Wallpaper from the top of the canvas down to [bottom], leaving out [hole]. */
    private fun DrawScope.wall(left: Float, right: Float, bottom: Float, hole: Rect?, block: Float, tint: Float) {
        fun paint(r: Rect) {
            if (r.width <= 0f || r.height <= 0f) return
            drawRect(shade(wallColor, tint), r.topLeft, r.size)
            // Vertical stripes
            var x = snap(left, block) + block * 4
            while (x < right) {
                if (x + block > r.left && x < r.right) {
                    val sx = maxOf(x, r.left)
                    drawRect(shade(wallStripe, tint), Offset(sx, r.top), Size(minOf(x + block * 2, r.right) - sx, r.height))
                }
                x += block * 10
            }
        }
        if (hole == null) {
            paint(Rect(left, 0f, right, bottom))
        } else {
            paint(Rect(left, 0f, right, hole.top))
            paint(Rect(left, hole.bottom, right, bottom))
            paint(Rect(left, hole.top, hole.left, hole.bottom))
            paint(Rect(hole.right, hole.top, right, hole.bottom))
        }
    }

    private fun DrawScope.window(area: Rect, block: Float, tint: Float, time: Float) {
        val glass = windowGlass(area, block)
        // Far hills behind the glass
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            ridge(Rect(glass.left, glass.top, glass.right, glass.bottom), block, glass.height * 0.18f, glass.height * 0.06f, 0.02f, 2f, shade(Color(0xFF6F9A7A), tint))
            ridge(Rect(glass.left, glass.top, glass.right, glass.bottom), block, glass.height * 0.1f, glass.height * 0.04f, 0.035f, 0.5f, shade(Color(0xFF557D62), tint))
        }
        val frame = Rect(glass.left - block * 3, glass.top - block * 3, glass.right + block * 3, glass.bottom + block * 2)
        wall(area.left, area.right, area.bottom, frame, block, tint)

        // Frame: white wood with a shadow edge and a cross of mullions
        val wood = shade(Color(0xFFF2EEE4), tint)
        val woodShadow = shade(Color(0xFFC9C1B0), tint)
        drawRect(wood, frame.topLeft, Size(frame.width, block * 3))
        drawRect(wood, Offset(frame.left, frame.top), Size(block * 3, frame.height))
        drawRect(wood, Offset(frame.right - block * 3, frame.top), Size(block * 3, frame.height))
        drawRect(wood, Offset(frame.left, glass.bottom), Size(frame.width, block * 2))
        drawRect(woodShadow, Offset(glass.left, glass.top), Size(glass.width, block))
        drawRect(woodShadow, Offset(glass.left, glass.top), Size(block, glass.height))
        val midX = snap(glass.center.x, block) - block
        val midY = snap(glass.top + glass.height * 0.45f, block)
        drawRect(wood, Offset(midX, glass.top), Size(block * 2, glass.height))
        drawRect(wood, Offset(glass.left, midY), Size(glass.width, block * 2))
        drawRect(woodShadow, Offset(midX + block * 2, glass.top), Size(block, glass.height))
        drawRect(woodShadow, Offset(glass.left, midY + block * 2), Size(glass.width, block))

        // Reflections on the glass, two short diagonal streaks per upper pane
        val shine = Color.White.copy(alpha = 0.18f * (1f - (tint / 0.4f).coerceIn(0f, 1f) * 0.7f))
        listOf(glass.left + block * 4, midX + block * 6).forEach { x0 ->
            for (i in 0 until 6) {
                drawRect(shine, Offset(x0 + i * block, glass.top + block * (8 - i)), Size(block, block))
                drawRect(shine, Offset(x0 + block * 3 + i * block, glass.top + block * (8 - i)), Size(block, block))
            }
        }

        // Curtains on both sides, gently moving
        val sway = snap(sin(time * 0.6f) * block, block)
        val curtain = shade(Color(0xFFB5524A), tint)
        val fold = shade(Color(0xFF8E3B36), tint)
        val rod = shade(Color(0xFF5A3A2A), tint)
        val curtainW = snap(glass.width * 0.1f, block)
        drawRect(rod, Offset(frame.left - block * 4, frame.top - block * 3), Size(frame.width + block * 8, block * 2))
        listOf(frame.left - block * 3 to 1f, frame.right + block * 3 - curtainW to -1f).forEach { (x, dir) ->
            var y = frame.top - block
            var row = 0
            while (y < frame.bottom + block * 2) {
                // Wider towards the bottom, like a tied curtain
                val extra = block * minOf(row / 12, 3) + if (row > 10) sway * dir else 0f
                val left = if (dir > 0) x else x - extra
                val w = curtainW + extra
                drawRect(curtain, Offset(left, y), Size(w, block))
                var fx = left + block * 2
                while (fx < left + w - block) {
                    drawRect(fold, Offset(fx, y), Size(block, block))
                    fx += block * 4
                }
                y += block
                row++
            }
        }
    }

    /** A sine ridge from the bottom of [area] up to [base] + amplitude, filled down to the bottom. */
    private fun DrawScope.ridge(area: Rect, block: Float, base: Float, amplitude: Float, frequency: Float, phase: Float, color: Color) {
        var x = area.left
        while (x < area.right) {
            val wave = sin(x * frequency / block * 4f + phase) * 0.6f + sin(x * frequency / block * 9f + phase * 2) * 0.4f
            val height = snap(base + wave * amplitude, block)
            drawRect(color, Offset(x, area.bottom - height), Size(block, height + 1))
            x += block
        }
    }

    private fun DrawScope.desert(area: Rect, block: Float, tint: Float) {
        // Mesas far away
        val mesa = shade(Color(0xFFC9774A), tint)
        val mesaTop = shade(Color(0xFFE09A62), tint)
        listOf(0.08f to 0.22f, 0.62f to 0.3f).forEach { (start, width) ->
            val left = snap(area.left + area.width * start, block)
            val w = snap(area.width * width, block)
            val h = snap(area.height * 0.42f, block)
            drawRect(mesa, Offset(left, area.bottom - h), Size(w, h))
            drawRect(mesaTop, Offset(left, area.bottom - h), Size(w, block))
            drawRect(mesa, Offset(left - block, area.bottom - h + block * 3), Size(w + 2 * block, h))
        }
        ridge(area, block, area.height * 0.22f, area.height * 0.08f, 0.012f, 0.5f, shade(Color(0xFFE8B66A), tint))
        ridge(area, block, area.height * 0.1f, area.height * 0.06f, 0.02f, 2f, shade(Color(0xFFD99A4E), tint))
        // Little cacti on the dunes
        val cactus = shade(Color(0xFF4F7A3A), tint)
        listOf(0.18f, 0.83f).forEach { at ->
            val x = snap(area.left + area.width * at, block)
            val y = area.bottom - area.height * 0.12f
            drawRect(cactus, Offset(x, y - block * 7), Size(block * 2, block * 7))
            drawRect(cactus, Offset(x - block * 2, y - block * 5), Size(block * 2, block))
            drawRect(cactus, Offset(x - block * 2, y - block * 7), Size(block, block * 2))
            drawRect(cactus, Offset(x + block * 2, y - block * 4), Size(block * 2, block))
            drawRect(cactus, Offset(x + block * 3, y - block * 6), Size(block, block * 2))
        }
    }

    private fun DrawScope.beach(area: Rect, block: Float, tint: Float, time: Float, wind: Float) {
        val horizon = snap(area.bottom - area.height * 0.32f, block)
        val shore = snap(area.bottom - area.height * 0.06f, block)
        drawRect(shade(Color(0xFF2E86C1), tint), Offset(area.left, horizon), Size(area.width, shore - horizon))
        drawRect(shade(Color(0xFF5DADE2), tint), Offset(area.left, horizon), Size(area.width, block))
        // Waves: moving light dashes, faster closer to the shore
        val random = Random(2)
        var y = horizon + block * 2
        var row = 0
        while (y < shore - block) {
            val speed = 6f + row * 4f
            repeat(6) {
                val length = block * (2 + random.nextInt(4))
                val x = ((random.nextFloat() * area.width + time * speed * block / 4f) % (area.width + length)) - length
                drawRect(shade(Color(0xFFD6EEF8), tint), Offset(area.left + snap(x, block), y), Size(length, block))
            }
            y += block * 3
            row++
        }
        // Foam line and sand
        val foam = sin(time * 1.5f) * block
        drawRect(shade(Color(0xFFF4F7FF), tint), Offset(area.left, shore - block + snap(foam, block)), Size(area.width, block))
        drawRect(shade(Color(0xFFF2D49B), tint), Offset(area.left, shore), Size(area.width, area.bottom - shore + 1))
        // Island
        val island = shade(Color(0xFF3E6B5A), tint)
        val ix = snap(area.left + area.width * 0.7f, block)
        drawRect(island, Offset(ix, horizon - block * 2), Size(block * 14, block * 2))
        drawRect(island, Offset(ix + block * 3, horizon - block * 4), Size(block * 7, block * 2))
        // Palm tree, its fronds move in the wind
        val trunk = shade(Color(0xFF8D6744), tint)
        val frond = shade(Color(0xFF3F9A4A), tint)
        val px = snap(area.left + area.width * 0.12f, block)
        val trunkTop = area.bottom - area.height * 0.62f
        var ty = area.bottom - block
        var tx = px
        while (ty > trunkTop) {
            drawRect(trunk, Offset(tx, ty), Size(block * 3, block))
            if (((area.bottom - ty) / block).toInt() % 3 == 0) drawRect(shade(Color(0xFF6B4A2E), tint), Offset(tx, ty), Size(block * 3, block))
            ty -= block
            if (((area.bottom - ty) / block).toInt() % 9 == 0) tx += block
        }
        tx += block / 2
        val swing = sin(time * 1.4f) * (1f + wind * 2f)
        listOf(-1, 1).forEach { side ->
            for (layer in 0..1) {
                for (i in 0 until 11) {
                    val droop = (i * i) / (14f + layer * 10f) + swing * i / 8f * side - layer * 2f
                    val fx = tx + side * i * block + (if (side < 0) 0f else block)
                    val fy = snap(trunkTop + droop * block, block)
                    drawRect(frond, Offset(fx, fy), Size(block, block * 2))
                    if (i in 2..8 && i % 2 == 0) drawRect(frond, Offset(fx, fy + block * 2), Size(block, block))
                }
            }
        }
    }

    private fun DrawScope.pine(x: Float, bottom: Float, height: Int, block: Float, color: Color, sway: Float) {
        for (row in 0 until height) {
            val fromTop = height - row
            val half = max(1, (fromTop / 2 + if (fromTop % 3 == 0) 1 else 0))
            val shift = snap(sway * row / height, block)
            drawRect(color, Offset(x - half * block + shift, bottom - (row + 1) * block), Size(half * 2 * block + block, block))
        }
    }

    private fun DrawScope.forest(area: Rect, block: Float, tint: Float, time: Float, wind: Float) {
        val layers = listOf(
            Triple(Color(0xFF4F7F6C), 0.5f, 10),
            Triple(Color(0xFF2E5A45), 0.3f, 13),
            Triple(Color(0xFF1F4532), 0.1f, 17),
        )
        layers.forEachIndexed { index, (color, lift, height) ->
            val random = Random(10 + index)
            val c = shade(color, tint)
            val bottom = snap(area.bottom - area.height * lift * 0.6f, block)
            drawRect(c, Offset(area.left, bottom), Size(area.width, area.bottom - bottom + 1))
            var x = area.left - block * 4
            while (x < area.right + block * 4) {
                val h = height + random.nextInt(-3, 4)
                val sway = if (index == 2) sin(time * 1.2f + x) * block * (0.5f + wind * 2f) else 0f
                pine(snap(x, block), bottom, h, block, c, sway)
                x += block * (5 + random.nextInt(4))
            }
        }
    }

    private fun DrawScope.city(area: Rect, block: Float, tint: Float, time: Float) {
        val night = (tint / 0.4f).coerceIn(0f, 1f)
        listOf(Color(0xFF6A6F9E) to 0.55f, Color(0xFF3C4070) to 0.42f).forEachIndexed { layer, (color, maxHeight) ->
            val random = Random(20 + layer)
            val c = shade(color, tint)
            var x = area.left
            var tallest = Offset.Zero
            while (x < area.right) {
                val w = snap(block * (5 + random.nextInt(6)), block)
                val h = snap(area.height * (0.15f + random.nextFloat() * maxHeight), block)
                drawRect(c, Offset(x, area.bottom - h), Size(w, h))
                if (layer == 1) {
                    // Windows: reflect the sky by day, light up randomly at night
                    var wy = area.bottom - h + block * 2
                    var wRow = 0
                    while (wy < area.bottom - block * 2) {
                        var wx = x + block
                        var wCol = 0
                        while (wx < x + w - block) {
                            val hash = (wRow * 31 + wCol * 17 + (x / block).toInt() * 7)
                            val flicker = ((time / 7f).toInt() + hash) % 11 == 0
                            val lit = night > 0.3f && (hash % 3 != 0) != flicker
                            val windowColor = when {
                                lit -> Color(0xFFFFE08A)
                                night > 0.3f -> shade(Color(0xFF2A2D50), tint)
                                else -> Color(0xFF9FC3E6)
                            }
                            drawRect(windowColor, Offset(wx, wy), Size(block, block))
                            wx += block * 2
                            wCol++
                        }
                        wy += block * 2
                        wRow++
                    }
                }
                if (h > tallest.y) tallest = Offset(x + w / 2, h)
                x += w + block
            }
            if (layer == 1) {
                val ax = snap(tallest.x, block)
                drawRect(c, Offset(ax, area.bottom - tallest.y - block * 4), Size(block, block * 4))
                if (night > 0.3f && (time * 1.5f).toInt() % 2 == 0) {
                    drawRect(Color(0xFFFF4A4A), Offset(ax, area.bottom - tallest.y - block * 5), Size(block, block))
                }
            }
        }
    }

    private fun DrawScope.zen(area: Rect, block: Float, tint: Float, time: Float, wind: Float) {
        ridge(area, block, area.height * 0.3f, area.height * 0.08f, 0.01f, 1.3f, shade(Color(0xFF7FA38C), tint))
        ridge(area, block, area.height * 0.14f, area.height * 0.05f, 0.02f, 0.2f, shade(Color(0xFF5E8A6F), tint))
        // Torii gate
        val red = shade(Color(0xFFC8423A), tint)
        val dark = shade(Color(0xFF2B1D24), tint)
        val gx = snap(area.left + area.width * 0.1f, block)
        val gw = block * 16
        val gh = snap(area.height * 0.42f, block)
        val top = area.bottom - gh
        drawRect(dark, Offset(gx - block, top), Size(gw + block * 2, block))
        drawRect(red, Offset(gx, top + block), Size(gw, block))
        drawRect(red, Offset(gx + block, top + block * 4), Size(gw - block * 2, block))
        drawRect(red, Offset(gx + block * 2, top + block), Size(block * 2, gh - block))
        drawRect(red, Offset(gx + gw - block * 4, top + block), Size(block * 2, gh - block))
        // Cherry tree on the right
        val trunk = shade(Color(0xFF5A3A2A), tint)
        val tx = snap(area.left + area.width * 0.78f, block)
        val treeTop = area.bottom - area.height * 0.55f
        var y = area.bottom - block
        var x = tx
        while (y > treeTop) {
            drawRect(trunk, Offset(x, y), Size(block * 3, block))
            y -= block
            if (((area.bottom - y) / block).toInt() % 11 == 0) x -= block
        }
        // Two branches
        for (i in 1..6) {
            drawRect(trunk, Offset(x - i * block, treeTop + block * 6 - i * block), Size(block * 2, block))
            drawRect(trunk, Offset(x + block + i * block, treeTop + block * 8 - i * block), Size(block * 2, block))
        }
        val pink = shade(Color(0xFFF4A6C6), tint)
        val pinkDark = shade(Color(0xFFE07FA8), tint)
        val random = Random(30)
        val breeze = snap(sin(time * 1.1f) * block * (0.5f + wind), block)
        repeat(9) {
            val cx = x + (random.nextInt(-11, 12)) * block + breeze
            val cy = treeTop + random.nextInt(-6, 3) * block
            val rx = 4 + random.nextInt(4)
            val ry = 3 + random.nextInt(2)
            for (dx in -rx..rx) for (dy in -ry..ry) {
                if ((dx * dx).toFloat() / (rx * rx) + (dy * dy).toFloat() / (ry * ry) > 1f) continue
                drawRect(if ((dx + dy) % 3 == 0) pinkDark else pink, Offset(cx + dx * block, cy + dy * block), Size(block, block))
            }
        }
    }

    // region Grounds

    /** Draws [ground] into [area] (from the ground line to the bottom of the screen). */
    fun drawGround(scope: DrawScope, ground: Ground, area: Rect, block: Float, tint: Float, time: Float, sprites: SceneSprites?) = with(scope) {
        when (ground) {
            Ground.GRASS -> if (sprites != null) drawPainter(sprites.ground, area) else {
                drawRect(shade(Color(0xFF3F282F), tint), area.topLeft, area.size)
                drawRect(shade(Color(0xFF40922E), tint), area.topLeft, Size(area.width, block * 2))
            }
            Ground.SAND -> {
                drawRect(shade(Color(0xFFE6C27A), tint), area.topLeft, area.size)
                drawRect(shade(Color(0xFFF2D49B), tint), area.topLeft, Size(area.width, block * 2))
                speckle(area, block, shade(Color(0xFFC9A05A), tint), 0.08f, 41)
                speckle(area, block, shade(Color(0xFFFFF0C8), tint), 0.03f, 42)
            }
            Ground.STONE -> {
                drawRect(shade(Color(0xFF4A4A55), tint), area.topLeft, area.size)
                val random = Random(7)
                var y = area.top
                var row = 0
                while (y < area.bottom) {
                    var x = area.left - (if (row % 2 == 0) 0f else block * 3)
                    while (x < area.right) {
                        val w = block * (5 + random.nextInt(3))
                        val color = if (random.nextBoolean()) Color(0xFF8E8E9A) else Color(0xFF7A7A88)
                        drawRect(shade(color, tint), Offset(x + block, y + block), Size(w - block, block * 3))
                        drawRect(shade(Color(0xFFB0B0BC), tint), Offset(x + block, y + block), Size(w - block * 2, block))
                        x += w
                    }
                    y += block * 4
                    row++
                }
            }
            Ground.WOOD -> {
                var y = area.top
                var row = 0
                while (y < area.bottom) {
                    val color = if (row % 2 == 0) Color(0xFF8D6744) else Color(0xFF7A5838)
                    drawRect(shade(color, tint), Offset(area.left, y), Size(area.width, block * 5))
                    drawRect(shade(Color(0xFF4E3524), tint), Offset(area.left, y + block * 5), Size(area.width, block))
                    var x = area.left + (row % 3) * block * 7
                    while (x < area.right) {
                        drawRect(shade(Color(0xFF4E3524), tint), Offset(x, y), Size(block, block * 5))
                        drawRect(shade(Color(0xFFC0A060), tint), Offset(x + block * 2, y + block * 2), Size(block, block))
                        x += block * 22
                    }
                    y += block * 6
                    row++
                }
                drawRect(shade(Color(0xFFB08458), tint), area.topLeft, Size(area.width, block))
            }
            Ground.SILL -> {
                // Wall with a radiator below the sill
                drawRect(shade(Color(0xFFD9C6A8), tint), area.topLeft, area.size)
                val radTop = area.top + block * 12
                val radLeft = snap(area.left + area.width * 0.18f, block)
                val radRight = snap(area.left + area.width * 0.82f, block)
                if (radTop < area.bottom) {
                    drawRect(shade(Color(0xFFE8E8E4), tint), Offset(radLeft, radTop), Size(radRight - radLeft, area.bottom - radTop))
                    var fx = radLeft + block
                    while (fx < radRight - block) {
                        drawRect(shade(Color(0xFFC4C4BE), tint), Offset(fx, radTop + block), Size(block, area.bottom - radTop - block))
                        fx += block * 3
                    }
                }
                // Shadow under the board
                drawRect(shade(Color(0xFFB8A27F), tint), Offset(area.left, area.top + block * 9), Size(area.width, block * 2))
                // The board: top surface, grain and the front edge
                drawRect(shade(Color(0xFFC9A274), tint), area.topLeft, Size(area.width, block * 7))
                drawRect(shade(Color(0xFFDDBB8C), tint), area.topLeft, Size(area.width, block))
                val random = Random(61)
                repeat((area.width / (block * 9)).toInt()) {
                    val x = snap(area.left + random.nextFloat() * area.width, block)
                    val y = area.top + block * (2 + random.nextInt(4))
                    drawRect(shade(Color(0xFFB08A5E), tint), Offset(x, y), Size(block * (3 + random.nextInt(5)), block))
                }
                drawRect(shade(Color(0xFF8D6744), tint), Offset(area.left, area.top + block * 7), Size(area.width, block * 2))
            }
            Ground.SNOW -> {
                drawRect(shade(Color(0xFFF4F7FF), tint), area.topLeft, area.size)
                speckle(area, block, shade(Color(0xFFD6E4F5), tint), 0.1f, 51)
                val random = Random(52)
                repeat(14) {
                    val x = snap(area.left + random.nextFloat() * area.width, block)
                    val y = snap(area.top + random.nextFloat() * area.height, block)
                    val twinkle = 0.5f + 0.5f * sin(time * 3f + it)
                    drawRect(Color.White, Offset(x, y), Size(block, block), alpha = twinkle)
                }
            }
        }
    }

    private fun DrawScope.speckle(area: Rect, block: Float, color: Color, density: Float, seed: Int) {
        val random = Random(seed)
        val count = (area.width / block * area.height / block * density).toInt()
        repeat(count) {
            val x = snap(area.left + random.nextFloat() * area.width, block)
            val y = snap(area.top + block * 2 + random.nextFloat() * (area.height - block * 2), block)
            drawRect(color, Offset(x, y), Size(block, block))
        }
    }

    // endregion

}
