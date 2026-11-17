package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
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
