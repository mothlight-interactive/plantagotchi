package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor

/**
 * Reusable ARGB cell buffer with origin at the bottom center (x right, y up). Shapes are
 * rasterized into it first, then [flush] draws merged horizontal runs of equal color, so a
 * detailed sprite costs a few hundred rects instead of thousands. 0 means empty.
 */
internal class PixelRaster(
    private val width: Int,
    private val height: Int,
    private val originX: Int,
    private val originY: Int = 0,
) {
    private val data = IntArray(width * height)
    private var minX = width
    private var maxX = -1
    private var minY = height
    private var maxY = -1

    fun set(x: Int, y: Int, color: Int) {
        val bx = x + originX
        val by = y + originY
        if (bx < 0 || bx >= width || by < 0 || by >= height) return
        data[by * width + bx] = color
        if (bx < minX) minX = bx
        if (bx > maxX) maxX = bx
        if (by < minY) minY = by
        if (by > maxY) maxY = by
    }

    fun get(x: Int, y: Int): Int {
        val bx = x + originX
        val by = y + originY
        if (bx < 0 || bx >= width || by < 0 || by >= height) return 0
        return data[by * width + bx]
    }

    /** Draws all cells with their bottom-left cell edge (0, 0) at [base] and clears the buffer. */
    fun flush(scope: DrawScope, base: Offset, pixel: Float) {
        if (maxX < 0) return
        for (y in minY..maxY) {
            val row = y * width
            val top = snap(base.y - (y - originY + 1) * pixel)
            val bottom = snap(base.y - (y - originY) * pixel)
            var x = minX
            while (x <= maxX) {
                val c = data[row + x]
                if (c == 0) {
                    x++
                    continue
                }
                var e = x + 1
                while (e <= maxX && data[row + e] == c) e++
                val left = snap(base.x + (x - originX) * pixel)
                val right = snap(base.x + (e - originX) * pixel)
                scope.drawRect(Color(c), Offset(left, top), Size(right - left, bottom - top))
                for (i in x until e) data[row + i] = 0
                x = e
            }
        }
        minX = width; maxX = -1; minY = height; maxY = -1
    }

    private fun snap(v: Float) = floor(v + 0.5f)
}

/** Cheap stable hash to 0..1, used instead of Random to stay allocation free per frame. */
internal fun hash01(a: Int, b: Int, c: Int = 0): Float {
    var h = a * -0x61c88647 + b * 0x27d4eb2d + c * 0x165667b1
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    h *= 0x297a2d39
    h = h xor (h ushr 15)
    return (h and 0xFFFFFF) / 16777216f
}
