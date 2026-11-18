package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Outdoor landscapes and grounds in fine pixel art. [pixel] is one art pixel in px (normally
 * `PIXEL_DP * density`); object sizes are given in units `u` of 4 dp so they keep their size on
 * screen whatever the pixel size. [tint] darkens colors at night (0 = day, ~0.4 = night).
 */
internal object SceneryRenderer {
    private val nightColor = Color(0xFF0B0B1E)

    internal fun shade(c: Color, tint: Float) = lerp(c, nightColor, (tint * 1.5f).coerceIn(0f, 0.7f))

    /** Snaps to the pixel grid. */
    internal fun snap(v: Float, block: Float) = floor(v / block) * block

    /** Size unit of 4 dp for a [pixel] of [PIXEL_DP] dp. */
    private fun unit(pixel: Float) = pixel * 4f / PIXEL_DP

    /** Cheap deterministic noise in 0..1, no allocations (instead of `Random` per frame). */
    internal fun noise(i: Int, seed: Int): Float {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535f
    }

    // region Legacy entry points (block = old 4 dp pixel), kept until GameCanvas uses the new API

    /** Old API: [block] was the 4 dp art pixel. Draws the new fine art at the same on-screen size. */
    fun drawScenery(scope: DrawScope, scenery: Scenery, area: Rect, block: Float, tint: Float, time: Float, wind: Float, sprites: SceneSprites?) {
        val pixel = block * PIXEL_DP / 4f
        if (scenery.indoor) {
            IndoorScenes.forScenery(scenery, scope.size, area.bottom, pixel)?.draw(scope, tint, time, legacyToys)
        } else {
            drawLandscape(scope, scenery, area, pixel, tint, time, wind, sprites?.mountains)
        }
    }

    /** Old API, see [drawScenery]. */
    fun drawGround(scope: DrawScope, ground: Ground, area: Rect, block: Float, tint: Float, time: Float, sprites: SceneSprites?) =
        drawSceneGround(scope, ground, area, block * PIXEL_DP / 4f, tint, time, sprites?.ground)

    private val legacyToys = IndoorToys()

    // endregion

    /**
     * Draws the landscape of an outdoor [scenery] into [area] (the part above the ground line).
     * [mountains] is the bitmap of the original mountain art (null: drawn in code, e.g. previews);
     * it is aligned to [screenBottom].
     */
    fun drawLandscape(
        scope: DrawScope,
        scenery: Scenery,
        area: Rect,
        pixel: Float,
        tint: Float,
        time: Float,
        wind: Float,
        mountains: ImageBitmap? = null,
        screenBottom: Float = scope.size.height,
    ) = with(scope) {
        val p = IndoorScenes.wholePixel(pixel)
        val u = unit(pixel)
        when (scenery) {
            Scenery.MOUNTAINS -> if (mountains != null) {
                val h = area.width * mountains.height / mountains.width
                drawBitmap(
                    mountains,
                    Rect(area.left, screenBottom - h, area.right, screenBottom),
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = tint), BlendMode.SrcAtop),
                )
            } else mountainRange(area, p, tint)
            Scenery.DESERT -> desert(area, p, u, tint)
            Scenery.BEACH -> beach(area, p, u, tint, time, wind)
            Scenery.FOREST -> forest(area, p, u, tint, time, wind)
            Scenery.CITY -> city(area, p, u, tint, time)
            Scenery.ZEN -> zen(area, p, u, tint, time, wind)
            Scenery.ROOM, Scenery.FIREPLACE, Scenery.PLANT_SHOP -> Unit // indoor, see IndoorScene
        }
    }

    /** A wavy ridge from the bottom of [area] up to [base] + amplitude, one column per pixel. */
    internal fun DrawScope.ridge(area: Rect, p: Float, u: Float, base: Float, amplitude: Float, frequency: Float, phase: Float, color: Color, top: Color? = null) {
        var x = area.left
        var prev = -1f
        var runStart = x
        // Columns with equal height are merged into one rect.
        while (x <= area.right) {
            val i = x / u
            val wave = sin(i * frequency * 4f + phase) * 0.6f + sin(i * frequency * 9f + phase * 2) * 0.4f
            val height = if (x >= area.right) -2f else snap(base + wave * amplitude, p)
            if (height != prev) {
                if (prev >= 0f && x > runStart) {
                    // A little overlap hides seams when the scene is drawn scaled (shop previews)
                    drawRect(color, Offset(runStart, area.bottom - prev), Size(x - runStart + p * 0.5f, prev + 1))
                    if (top != null) drawRect(top, Offset(runStart, area.bottom - prev), Size(x - runStart + p * 0.5f, p))
                }
                runStart = x
                prev = height
            }
            x += p
        }
    }

    /** Code version of the mountain art (previews): layered peaks with lit flanks. */
    private fun DrawScope.mountainRange(area: Rect, p: Float, tint: Float) {
        val back = shade(Color(0xFF2B2D4E), tint)
        val mid = shade(Color(0xFF1E5A3E), tint)
        val front = shade(Color(0xFF1B3B40), tint)
        val lit = shade(Color(0xFF2E6A4C), tint)
        peaks(area, p, area.height * 0.62f, 0.42f, 0.1f, back, null)
        peaks(area, p, area.height * 0.5f, 0.55f, 0.55f, mid, lit)
        peaks(area, p, area.height * 0.34f, 0.48f, 0.3f, front, lit)
    }

    /** Triangular peaks every [spacing] (fraction of the width); [lit] colors the left flanks. */
    private fun DrawScope.peaks(area: Rect, p: Float, height: Float, spacing: Float, offset: Float, color: Color, lit: Color?) {
        val step = area.width * spacing
        var x = area.left
        while (x <= area.right) {
            val t = ((x - area.left) / step + offset) % 1f
            val peak = 1f - abs(t - 0.5f) * 2f
            val bump = noise(((x - area.left) / p).toInt() / 9, 3) * 0.06f
            val colH = snap(height * (0.35f + 0.65f * peak * peak * (3f - 2f * peak) + bump), p)
            drawRect(color, Offset(x, area.bottom - colH), Size(p * 1.5f, colH + 1))
            if (lit != null && t < 0.5f && peak > 0.55f) {
                val litH = snap(colH * (peak - 0.55f) * 0.9f, p)
                drawRect(lit, Offset(x, area.bottom - colH), Size(p, litH))
            }
            x += p
        }
    }

    private fun DrawScope.desert(area: Rect, p: Float, u: Float, tint: Float) {
        // Mesas far away, in layered bands with a shaded side
        val bands = arrayOf(Color(0xFFE09A62), Color(0xFFC9774A), Color(0xFFD48A55), Color(0xFFB8673F))
        for (m in 0..1) {
            val left = snap(area.left + area.width * (if (m == 0) 0.06f else 0.6f), p)
            val width = snap(area.width * (if (m == 0) 0.24f else 0.32f), p)
            val height = snap(area.height * (if (m == 0) 0.38f else 0.44f), p)
            val top = area.bottom - height
            // Body widens towards the bottom in steps
            var y = top
            var row = 0
            while (y < area.bottom) {
                val grow = snap((y - top) / height * u * 3f, p)
                val band = bands[(row / 3) % bands.size]
                drawRect(shade(band, tint), Offset(left - grow, y), Size(width + grow * 2, p * 3))
                // Shadow side on the right
                drawRect(shade(Color(0xFF9A5536), tint), Offset(left + width + grow - snap(width * 0.18f, p), y), Size(snap(width * 0.18f, p), p * 3))
                y += p * 3
                row++
            }
            drawRect(shade(Color(0xFFF0B27A), tint), Offset(left, top), Size(width, p))
        }
        ridge(area, p, u, area.height * 0.22f, area.height * 0.07f, 0.012f, 0.5f, shade(Color(0xFFE8B66A), tint), shade(Color(0xFFF6CF8A), tint))
        ridge(area, p, u, area.height * 0.1f, area.height * 0.05f, 0.02f, 2f, shade(Color(0xFFD99A4E), tint), shade(Color(0xFFEAB066), tint))
        // Saguaros with a lit side and spines
        for (k in 0..2) {
            val at = floatArrayOfCacti[k]
            val x = snap(area.left + area.width * at, p)
            val y = snap(area.bottom - area.height * (0.09f + 0.03f * k), p)
            val s = 1f - k * 0.25f
            cactus(x, y, p, u * s, tint)
        }
        // Pebbles
        for (i in 0 until 10) {
            val x = snap(area.left + noise(i, 61) * area.width, p)
            val y = snap(area.bottom - noise(i, 62) * area.height * 0.08f, p)
            drawRect(shade(Color(0xFFB98348), tint), Offset(x, y - p), Size(p * 2, p))
        }
    }

    private val floatArrayOfCacti = floatArrayOf(0.18f, 0.84f, 0.5f)

    private fun DrawScope.cactus(x: Float, base: Float, p: Float, u: Float, tint: Float) {
        val green = shade(Color(0xFF4F7A3A), tint)
        val light = shade(Color(0xFF6FA052), tint)
        val dark = shade(Color(0xFF365A28), tint)
        val trunkW = snap(u * 2.2f, p).coerceAtLeast(p * 3)
        val trunkH = snap(u * 8f, p)
        fun column(cx: Float, top: Float, bottom: Float, width: Float) {
            drawRect(green, Offset(cx, top + p), Size(width, bottom - top - p))
            drawRect(green, Offset(cx + p, top), Size(width - p * 2, p))
            drawRect(light, Offset(cx + p, top + p), Size(p, bottom - top - p * 2))
            drawRect(dark, Offset(cx + width - p, top + p * 2), Size(p, bottom - top - p * 2))
        }
        column(x, base - trunkH, base, trunkW)
        val armW = snap(trunkW * 0.75f, p).coerceAtLeast(p * 2)
        // Left arm: out then up
        val ly = snap(base - trunkH * 0.55f, p)
        drawRect(green, Offset(x - armW * 1.5f, ly), Size(armW * 1.5f, armW))
        column(x - armW * 1.5f - p, ly - snap(u * 3f, p), ly + armW, armW)
        // Right arm, higher
        val ry = snap(base - trunkH * 0.72f, p)
        drawRect(green, Offset(x + trunkW, ry), Size(armW * 1.2f, armW))
        column(x + trunkW + snap(armW * 1.2f, p) - p, ry - snap(u * 2.2f, p), ry + armW, armW)
        // Spines
        var sy = base - trunkH + p * 3
        while (sy < base - p * 2) {
            drawRect(shade(Color(0xFFE8E2B0), tint), Offset(x - p, sy), Size(p, p))
            drawRect(shade(Color(0xFFE8E2B0), tint), Offset(x + trunkW, sy + p * 2), Size(p, p))
            sy += p * 5
        }
    }

    private fun DrawScope.beach(area: Rect, p: Float, u: Float, tint: Float, time: Float, wind: Float) {
        val horizon = snap(area.bottom - area.height * 0.32f, p)
        val shore = snap(area.bottom - area.height * 0.06f, p)
        // Sea in bands, lighter towards the shore
        val sea = arrayOf(Color(0xFF256FA6), Color(0xFF2B7DB6), Color(0xFF3289C1), Color(0xFF3C97CB))
        val bandH = snap((shore - horizon) / sea.size, p)
        for (i in sea.indices) {
            val top = horizon + bandH * i
            val bottom = if (i == sea.lastIndex) shore else top + bandH
            drawRect(shade(sea[i], tint), Offset(area.left, top), Size(area.width, bottom - top))
        }
        drawRect(shade(Color(0xFF6CB8E6), tint), Offset(area.left, horizon), Size(area.width, p))
        // Island with a tiny palm
        val island = shade(Color(0xFF3E6B5A), tint)
        val ix = snap(area.left + area.width * 0.68f, p)
        val iw = snap(u * 14f, p)
        drawRect(island, Offset(ix, horizon - p * 3), Size(iw, p * 3))
        drawRect(island, Offset(ix + p * 4, horizon - p * 6), Size(iw - p * 10, p * 3))
        drawRect(island, Offset(ix + p * 9, horizon - p * 8), Size(iw - p * 20, p * 2))
        drawRect(shade(Color(0xFF55806A), tint), Offset(ix + p * 4, horizon - p * 6), Size(iw - p * 14, p))
        // Waves: thin moving dashes, faster near the shore, plus sun glitter
        var y = horizon + p * 3
        var row = 0
        while (y < shore - p * 2) {
            val speed = (6f + row * 2.5f) * u / 4f
            for (k in 0 until 7) {
                val length = snap(u * (1.5f + noise(k + row * 7, 4) * 3f), p)
                val start = noise(k + row * 7, 5) * area.width
                val x = ((start + time * speed) % (area.width + length)) - length
                drawRect(shade(Color(0xFFD6EEF8), tint), Offset(area.left + snap(x, p), y), Size(length, p))
            }
            y += p * (3 + row / 3)
            row++
        }
        val glitter = shade(Color(0xFFFFF6D0), tint)
        for (k in 0 until 6) {
            val on = sin(time * 3f + k * 1.7f) > 0.4f
            if (on) drawRect(glitter, Offset(snap(area.left + area.width * (0.35f + k * 0.05f), p), horizon + p * (4 + k * 3)), Size(p * 2, p))
        }
        // Foam line, wet sand and dry sand
        val foam = snap(sin(time * 1.5f) * u, p)
        drawRect(shade(Color(0xFFE9C68E), tint), Offset(area.left, shore), Size(area.width, area.bottom - shore + 1))
        drawRect(shade(Color(0xFFD9B57C), tint), Offset(area.left, shore), Size(area.width, p * 2))
        drawRect(shade(Color(0xFFF4F7FF), tint), Offset(area.left, shore - p + foam), Size(area.width, p))
        for (k in 0 until 12) {
            val fx = snap(area.left + noise(k, 8) * area.width, p)
            drawRect(shade(Color(0xFFF4F7FF), tint), Offset(fx, shore + foam), Size(p * (2 + k % 3), p))
        }
        palm(area, p, u, tint, time, wind)
    }

    private fun DrawScope.palm(area: Rect, p: Float, u: Float, tint: Float, time: Float, wind: Float) {
        val trunk = shade(Color(0xFF8D6744), tint)
        val ring = shade(Color(0xFF6B4A2E), tint)
        val frond = shade(Color(0xFF3F9A4A), tint)
        val frondDark = shade(Color(0xFF2C7638), tint)
        val px = snap(area.left + area.width * 0.12f, p)
        val trunkTop = snap(area.bottom - area.height * 0.62f, p)
        val tw = snap(u * 2.5f, p)
        var ty = area.bottom - p
        var tx = px
        var i = 0
        while (ty > trunkTop) {
            drawRect(if (i % 4 == 0) ring else trunk, Offset(tx, ty), Size(tw, p))
            if (i % 4 != 0) drawRect(ring, Offset(tx + tw - p, ty), Size(p, p))
            ty -= p
            i++
            if (i % 22 == 0) tx += p
        }
        // Coconuts
        drawRect(shade(Color(0xFF5A3A22), tint), Offset(tx - p, trunkTop + p), Size(p * 3, p * 3))
        drawRect(shade(Color(0xFF5A3A22), tint), Offset(tx + tw - p * 2, trunkTop + p * 2), Size(p * 3, p * 3))
        val cx = tx + tw / 2
        val swing = sin(time * 1.4f) * (1f + wind * 2f)
        for (f in 0 until 5) {
            val dir = frondDirs[f]
            val len = (if (f == 2) 12f else 18f) * u
            val steps = (len / p).toInt()
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                val droop = t * t * len * 0.55f + swing * t * u * 0.8f * (if (dir < 0) -1f else 1f)
                val fx = snap(cx + dir * t * len, p)
                val fy = snap(trunkTop - (if (f == 2) len * 0.6f * t else len * 0.18f * t) + droop, p)
                drawRect(frond, Offset(fx, fy), Size(p, p * 2))
                // Leaflets hang below the rib
                if (s % 2 == 0 && t > 0.1f) drawRect(frondDark, Offset(fx, fy + p * 2), Size(p, snap(u * (1.4f - t), p).coerceAtLeast(p)))
            }
        }
    }

    private val frondDirs = floatArrayOf(-1f, -0.55f, 0.1f, 0.55f, 1f)

    private fun DrawScope.pine(x: Float, bottom: Float, height: Float, p: Float, color: Color, light: Color?, sway: Float) {
        val rows = (height / p).toInt()
        var row = 0
        while (row < rows) {
            val fromTop = rows - row
            // Tiers: the outline widens and steps back in every tier
            val tier = fromTop % 9
            val half = max(1f, fromTop * 0.32f + tier * 0.35f - 1f)
            val shift = snap(sway * row / rows, p)
            val hw = snap(half * p, p)
            drawRect(color, Offset(x - hw + shift, bottom - (row + 1) * p), Size(hw * 2 + p, p))
            if (light != null && tier in 2..6) drawRect(light, Offset(x - hw + shift + p, bottom - (row + 1) * p), Size(snap(hw * 0.6f, p), p))
            row++
        }
    }

    private fun DrawScope.forest(area: Rect, p: Float, u: Float, tint: Float, time: Float, wind: Float) {
        for (index in 0..2) {
            val color = shade(forestColors[index], tint)
            val light = if (index == 2) shade(Color(0xFF2F6447), tint) else if (index == 1) shade(Color(0xFF3B6E55), tint) else null
            val lift = forestLift[index]
            val height = forestHeight[index] * u
            val bottom = snap(area.bottom - area.height * lift * 0.6f, p)
            drawRect(color, Offset(area.left, bottom), Size(area.width, area.bottom - bottom + 1))
            var x = area.left - u * 4
            var k = 0
            while (x < area.right + u * 4) {
                val h = height * (0.8f + noise(k, 10 + index) * 0.45f)
                val sway = if (index == 2) sin(time * 1.2f + x) * u * (0.5f + wind * 2f) else 0f
                pine(snap(x, p), bottom, h, p, color, light, sway)
                if (index == 2) drawRect(shade(Color(0xFF3A2A20), tint), Offset(snap(x, p), bottom), Size(p * 2, p * 2))
                x += u * (4.5f + noise(k, 20 + index) * 4f)
                k++
            }
            // Mist between the layers
            if (index < 2) drawRect(shade(Color(0x33E8F0F0), tint), Offset(area.left, bottom - p * 4), Size(area.width, p * 4))
        }
    }

    private val forestColors = arrayOf(Color(0xFF4F7F6C), Color(0xFF2E5A45), Color(0xFF1F4532))
    private val forestLift = floatArrayOf(0.5f, 0.3f, 0.1f)
    private val forestHeight = floatArrayOf(10f, 13f, 17f)

    /** Skyline with lit windows at night. Also drawn behind the cozy room's window. */
    internal fun DrawScope.city(area: Rect, p: Float, u: Float, tint: Float, time: Float) {
        val night = (tint / 0.4f).coerceIn(0f, 1f)
        for (layer in 0..1) {
            val c = shade(if (layer == 0) Color(0xFF6A6F9E) else Color(0xFF3C4070), tint)
            val edge = shade(if (layer == 0) Color(0xFF7D82B0) else Color(0xFF4C5185), tint)
            val maxHeight = if (layer == 0) 0.55f else 0.42f
            var x = area.left
            var k = 0
            var tallestX = 0f
            var tallestH = 0f
            while (x < area.right) {
                val bw = snap(u * (5f + noise(k, 20 + layer) * 6f), p)
                val bh = snap(area.height * (0.15f + noise(k, 30 + layer) * maxHeight), p)
                val top = area.bottom - bh
                drawRect(c, Offset(x, top), Size(bw, bh))
                drawRect(edge, Offset(x, top), Size(p, bh))
                // Roof details: ledge, and now and then a water tank or antenna
                drawRect(edge, Offset(x - p, top), Size(bw + p * 2, p))
                if (layer == 1 && k % 3 == 1) {
                    drawRect(c, Offset(x + p * 3, top - p * 4), Size(p * 4, p * 4))
                    drawRect(c, Offset(x + p * 3, top - p * 5), Size(p * 4, p))
                }
                if (layer == 1) {
                    // Windows: 2x2 px, reflect the sky by day, some lit at night
                    var wy = top + p * 4
                    var wRow = 0
                    while (wy < area.bottom - p * 3) {
                        var wx = x + p * 2
                        var wCol = 0
                        while (wx < x + bw - p * 3) {
                            val hash = wRow * 31 + wCol * 17 + k * 7
                            val flicker = ((time / 7f).toInt() + hash) % 11 == 0
                            val lit = night > 0.3f && (hash % 3 != 0) != flicker
                            val windowColor = when {
                                lit -> if (hash % 5 == 0) Color(0xFFFFC56A) else Color(0xFFFFE08A)
                                night > 0.3f -> shade(Color(0xFF2A2D50), tint)
                                else -> Color(0xFF9FC3E6)
                            }
                            drawRect(windowColor, Offset(wx, wy), Size(p * 2, p * 2))
                            wx += p * 4
                            wCol++
                        }
                        wy += p * 5
                        wRow++
                    }
                }
                if (bh > tallestH) {
                    tallestH = bh
                    tallestX = x + bw / 2
                }
                x += bw + p * 2
                k++
            }
            if (layer == 1) {
                val ax = snap(tallestX, p)
                drawRect(c, Offset(ax, area.bottom - tallestH - u * 4), Size(p, u * 4))
                if (night > 0.3f && (time * 1.5f).toInt() % 2 == 0) {
                    drawRect(Color(0xFFFF4A4A), Offset(ax - p, area.bottom - tallestH - u * 4 - p * 2), Size(p * 3, p * 2))
                }
            }
        }
    }

    private fun DrawScope.zen(area: Rect, p: Float, u: Float, tint: Float, time: Float, wind: Float) {
        ridge(area, p, u, area.height * 0.3f, area.height * 0.08f, 0.01f, 1.3f, shade(Color(0xFF7FA38C), tint), shade(Color(0xFF94B89F), tint))
        ridge(area, p, u, area.height * 0.14f, area.height * 0.05f, 0.02f, 0.2f, shade(Color(0xFF5E8A6F), tint), shade(Color(0xFF6E9C7E), tint))
        // Torii gate
        val red = shade(Color(0xFFC8423A), tint)
        val redDark = shade(Color(0xFF9E2F2A), tint)
        val dark = shade(Color(0xFF2B1D24), tint)
        val gx = snap(area.left + area.width * 0.1f, p)
        val gw = snap(u * 16f, p)
        val gh = snap(area.height * 0.42f, p)
        val top = area.bottom - gh
        drawRect(dark, Offset(gx - p * 3, top), Size(gw + p * 6, p * 2))
        drawRect(dark, Offset(gx - p * 4, top - p), Size(p * 3, p * 2))
        drawRect(dark, Offset(gx + gw + p, top - p), Size(p * 3, p * 2))
        drawRect(red, Offset(gx - p, top + p * 2), Size(gw + p * 2, p * 3))
        drawRect(red, Offset(gx + p * 2, top + u * 4), Size(gw - p * 4, p * 3))
        drawRect(redDark, Offset(gx + p * 2, top + u * 4 + p * 2), Size(gw - p * 4, p))
        val post = snap(u * 2f, p)
        for (side in 0..1) {
            val px = if (side == 0) gx + p * 4 else gx + gw - p * 4 - post
            drawRect(red, Offset(px, top + p * 5), Size(post, gh - p * 5))
            drawRect(redDark, Offset(px + post - p, top + p * 5), Size(p, gh - p * 5))
            drawRect(dark, Offset(px - p, area.bottom - p * 3), Size(post + p * 2, p * 3))
        }
        // Stone lantern
        val stone = shade(Color(0xFF9A9A92), tint)
        val lx = snap(area.left + area.width * 0.5f, p)
        val lb = area.bottom - snap(area.height * 0.06f, p)
        drawRect(stone, Offset(lx, lb - p * 6), Size(p * 4, p * 6))
        drawRect(stone, Offset(lx - p * 2, lb - p * 11), Size(p * 8, p * 5))
        drawRect(shade(Color(0xFFFFD27A), (tint * 0.3f)), Offset(lx, lb - p * 10), Size(p * 4, p * 3))
        drawRect(stone, Offset(lx - p * 4, lb - p * 13), Size(p * 12, p * 2))
        drawRect(stone, Offset(lx - p, lb - p * 15), Size(p * 6, p * 2))
        // Cherry tree on the right
        val trunk = shade(Color(0xFF5A3A2A), tint)
        val tx = snap(area.left + area.width * 0.78f, p)
        val treeTop = snap(area.bottom - area.height * 0.55f, p)
        var y = area.bottom - p
        var x = tx
        var i = 0
        val tw = snap(u * 2.5f, p)
        while (y > treeTop) {
            drawRect(trunk, Offset(x, y), Size(tw, p))
            y -= p
            i++
            if (i % 24 == 0) x -= p
        }
        for (b in 1..24) {
            drawRect(trunk, Offset(x - b * p, treeTop + p * 24 - b * p), Size(p * 3, p * 2))
            drawRect(trunk, Offset(x + tw + b * p - p, treeTop + p * 28 - b * p), Size(p * 3, p * 2))
        }
        val pink = shade(Color(0xFFF4A6C6), tint)
        val pinkDark = shade(Color(0xFFE07FA8), tint)
        val pinkLight = shade(Color(0xFFFFCFE0), tint)
        val breeze = snap(sin(time * 1.1f) * u * (0.5f + wind), p)
        for (k in 0 until 9) {
            val cx = snap(x + (noise(k, 30) * 28f - 14f) * u + breeze, p)
            val cy = snap(treeTop + (noise(k, 31) * 12f - 8f) * u, p)
            val rx = (6f + noise(k, 32) * 4f) * u
            val ry = (4f + noise(k, 33) * 2f) * u
            blob(cx, cy, rx, ry, p, pink, pinkDark, pinkLight)
        }
        // Falling petals
        for (k in 0 until 6) {
            val t = (time * 0.08f + noise(k, 34)) % 1f
            val px = snap(x + (noise(k, 35) * 30f - 18f) * u + sin(time + k) * u * 2f, p)
            val py = snap(treeTop + t * (area.bottom - treeTop), p)
            drawRect(pink, Offset(px, py), Size(p, p))
        }
    }

    /** A filled ellipse in pixel rows, darker underneath and lighter on top. */
    private fun DrawScope.blob(cx: Float, cy: Float, rx: Float, ry: Float, p: Float, color: Color, dark: Color, light: Color) {
        val rows = (ry / p).toInt()
        for (r in -rows..rows) {
            val f = r / rows.toFloat()
            val half = snap(rx * kotlin.math.sqrt((1f - f * f).coerceAtLeast(0f)), p)
            if (half <= 0f) continue
            val yy = cy + r * p
            drawRect(if (r > rows * 0.45f) dark else color, Offset(cx - half, yy), Size(half * 2, p))
            if (r < -rows * 0.4f) drawRect(light, Offset(cx - half * 0.5f, yy), Size(snap(half * 0.6f, p), p))
        }
    }

    // region Grounds

    /**
     * Draws [ground] into [area] (from the ground line to the bottom of the screen). [grass] is the
     * painter of the original grass art (null: drawn in code).
     */
    fun drawSceneGround(scope: DrawScope, ground: Ground, area: Rect, pixel: Float, tint: Float, time: Float, grass: Painter? = null) = with(scope) {
        val p = IndoorScenes.wholePixel(pixel)
        val u = unit(pixel)
        when (ground) {
            Ground.GRASS -> if (grass != null) drawPainter(grass, area) else {
                drawRect(shade(Color(0xFF3F282F), tint), area.topLeft, area.size)
                drawRect(shade(Color(0xFF4A3036), tint), Offset(area.left, area.top + u * 4), Size(area.width, p * 2))
                drawRect(shade(Color(0xFF40922E), tint), area.topLeft, Size(area.width, u * 2))
                drawRect(shade(Color(0xFF58AE3E), tint), area.topLeft, Size(area.width, p))
                var x = area.left
                var k = 0
                while (x < area.right) {
                    val tuft = (noise(k, 70) * 3).toInt()
                    if (tuft > 0) drawRect(shade(Color(0xFF40922E), tint), Offset(x, area.top + u * 2), Size(p, p * tuft))
                    x += p * 2
                    k++
                }
            }
            Ground.SAND -> {
                drawRect(shade(Color(0xFFE6C27A), tint), area.topLeft, area.size)
                drawRect(shade(Color(0xFFF2D49B), tint), area.topLeft, Size(area.width, u * 2))
                drawRect(shade(Color(0xFFFFE6B5), tint), area.topLeft, Size(area.width, p))
                // Ripples
                var y = area.top + u * 4
                var row = 0
                while (y < area.bottom) {
                    var x = area.left + noise(row, 43) * u * 8 - u * 8
                    while (x < area.right) {
                        drawRect(shade(Color(0xFFD7AE66), tint), Offset(snap(x, p), y), Size(snap(u * 4f, p), p))
                        x += u * 12
                    }
                    y += u * 3
                    row++
                }
                speckle(area, p, shade(Color(0xFFC9A05A), tint), 120, 41)
                speckle(area, p, shade(Color(0xFFFFF0C8), tint), 60, 42)
            }
            Ground.STONE -> {
                drawRect(shade(Color(0xFF4A4A55), tint), area.topLeft, area.size)
                var y = area.top
                var row = 0
                val rowH = snap(u * 4f, p)
                while (y < area.bottom) {
                    var x = area.left - (if (row % 2 == 0) 0f else u * 3)
                    var k = 0
                    while (x < area.right) {
                        val sw = snap(u * (5f + noise(k + row * 13, 7) * 3f), p)
                        val color = if (noise(k + row * 13, 8) > 0.5f) Color(0xFF8E8E9A) else Color(0xFF7A7A88)
                        drawRect(shade(color, tint), Offset(x + p, y + p), Size(sw - p, rowH - p))
                        drawRect(shade(Color(0xFFB0B0BC), tint), Offset(x + p, y + p), Size(sw - p * 2, p))
                        drawRect(shade(Color(0xFF65656F), tint), Offset(x + p, y + rowH - p), Size(sw - p, p))
                        if (noise(k + row * 13, 9) > 0.7f) drawRect(shade(Color(0xFF6E6E7A), tint), Offset(x + sw / 2, y + p * 3), Size(p * 2, p))
                        x += sw
                        k++
                    }
                    y += rowH
                    row++
                }
            }
            Ground.WOOD -> {
                var y = area.top
                var row = 0
                val plank = snap(u * 5f, p)
                while (y < area.bottom) {
                    val color = if (row % 2 == 0) Color(0xFF8D6744) else Color(0xFF7A5838)
                    drawRect(shade(color, tint), Offset(area.left, y), Size(area.width, plank))
                    drawRect(shade(Color(0xFF9E7850), tint), Offset(area.left, y), Size(area.width, p))
                    drawRect(shade(Color(0xFF4E3524), tint), Offset(area.left, y + plank), Size(area.width, p))
                    var x = area.left + (row % 3) * u * 7
                    var k = 0
                    while (x < area.right) {
                        drawRect(shade(Color(0xFF4E3524), tint), Offset(x, y), Size(p, plank))
                        // Nails and grain
                        drawRect(shade(Color(0xFFC0A060), tint), Offset(x + p * 3, y + p * 2), Size(p, p))
                        drawRect(shade(Color(0xFF6E4E32), tint), Offset(x + u * (6f + noise(k, row) * 8f), y + plank / 2), Size(snap(u * 3f, p), p))
                        x += u * 22
                        k++
                    }
                    y += plank + p
                    row++
                }
                drawRect(shade(Color(0xFFB08458), tint), area.topLeft, Size(area.width, p * 2))
            }
            Ground.SNOW -> {
                drawRect(shade(Color(0xFFF4F7FF), tint), area.topLeft, area.size)
                drawRect(shade(Color(0xFFE2EAF6), tint), Offset(area.left, area.top + u * 5), Size(area.width, p))
                speckle(area, p, shade(Color(0xFFD6E4F5), tint), 160, 51)
                for (k in 0 until 18) {
                    val x = snap(area.left + noise(k, 52) * area.width, p)
                    val y = snap(area.top + noise(k, 53) * area.height, p)
                    val twinkle = 0.5f + 0.5f * sin(time * 3f + k)
                    drawRect(Color.White, Offset(x, y), Size(p, p), alpha = twinkle)
                }
            }
        }
    }

    private fun DrawScope.speckle(area: Rect, p: Float, color: Color, count: Int, seed: Int) {
        for (i in 0 until count) {
            val x = snap(area.left + noise(i, seed) * area.width, p)
            val y = snap(area.top + p * 3 + noise(i, seed + 100) * (area.height - p * 3), p)
            drawRect(color, Offset(x, y), Size(p, p))
        }
    }

    // endregion
}
