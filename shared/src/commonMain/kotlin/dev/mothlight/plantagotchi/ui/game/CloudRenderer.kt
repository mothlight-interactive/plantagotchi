package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

/** A pixel cloud: filled cells of a small grid, built from a few round puffs on a flat base. */
internal class PixelCloud(val columns: Int, val rows: Int, private val cells: Array<BooleanArray>) {
    operator fun get(x: Int, y: Int): Boolean = x in 0 until columns && y in 0 until rows && cells[y][x]

    companion object {
        fun generate(seed: Int): PixelCloud {
            val random = Random(seed)
            val columns = random.nextInt(24, 35)
            val rows = random.nextInt(10, 14)
            fun range(from: Float, to: Float) = from + random.nextFloat() * (to - from)
            // Puffs sit on the bottom line: one big one, smaller ones along the width.
            val puffs = buildList {
                val big = rows * 0.5f
                add(Triple(range(big + 1, columns - big - 1), rows - big - 0.3f, big))
                var x = range(2.5f, 4f)
                while (x < columns - 2.5f) {
                    val radius = range(2f, rows * 0.36f)
                    val cx = x.coerceIn(radius + 0.5f, columns - radius - 0.5f)
                    add(Triple(cx, rows - radius - 0.3f, radius))
                    x += range(2.5f, 4.5f)
                }
            }
            val left = puffs.minOf { it.first }
            val right = puffs.maxOf { it.first }
            val cells = Array(rows) { y ->
                BooleanArray(columns) { x ->
                    val cx = x + 0.5f
                    val cy = y + 0.5f
                    val inPuff = puffs.any { (px, py, r) -> (cx - px) * (cx - px) + (cy - py) * (cy - py) <= r * r }
                    val inBase = y >= rows - 3 && cx in left..right
                    inPuff || inBase
                }
            }
            return PixelCloud(columns, rows, cells)
        }
    }
}

private val cloudShapes: List<PixelCloud> by lazy { List(8) { PixelCloud.generate(17 + it * 31) } }

private val Highlight = Color(0xFFFFFFFF)
private val Body = Color(0xFFE6EEF6)
private val Shadow = Color(0xFFBFCBDA)
private val StormBody = Color(0xFF8E97A6)
private val StormShadow = Color(0xFF6A7282)
private val Night = Color(0xFF1C2238)

/**
 * Draws a cloud into [dst]. [storm] (0..1) greys it, [night] (0..1) darkens it towards the night sky.
 */
internal fun DrawScope.drawPixelCloud(cloud: PixelCloud, dst: Rect, alpha: Float = 1f, storm: Float = 0f, night: Float = 0f) {
    val cw = dst.width / cloud.columns
    val ch = dst.height / cloud.rows
    fun shade(c: Color) = lerp(c, Night, night * 0.6f)
    val highlight = shade(lerp(Highlight, StormBody, storm * 0.7f))
    val body = shade(lerp(Body, StormBody, storm))
    val shadow = shade(lerp(Shadow, StormShadow, storm))
    // Whole pixels, and one rectangle per run of equal cells: no seams, no overlaps.
    fun px(v: Float) = kotlin.math.round(v)
    for (y in 0 until cloud.rows) {
        val top = px(dst.top + y * ch)
        val bottom = px(dst.top + (y + 1) * ch)
        var x = 0
        while (x < cloud.columns) {
            if (!cloud[x, y]) { x++; continue }
            fun colorAt(cx: Int) = when {
                !cloud[cx, y - 1] || !cloud[cx, y - 2] && !cloud[cx - 1, y - 1] -> highlight
                !cloud[cx, y + 1] || !cloud[cx, y + 2] -> shadow
                else -> body
            }
            val color = colorAt(x)
            var end = x + 1
            while (end < cloud.columns && cloud[end, y] && colorAt(end) == color) end++
            val l = px(dst.left + x * cw)
            val r = px(dst.left + end * cw)
            drawRect(color, Offset(l, top), Size(r - l, bottom - top), alpha = alpha)
            x = end
        }
    }
}

/** [count] clouds drifting from right to left across the upper part of the sky. */
internal fun DrawScope.drawDriftingClouds(count: Int, time: Float, dp: Float, alpha: Float, storm: Float, night: Float) {
    repeat(count) { i ->
        val random = Random(1000 + i * 7)
        val cloud = cloudShapes[i % cloudShapes.size]
        val width = (69f + random.nextFloat() * 69f) * dp
        val height = width * cloud.rows / cloud.columns
        val speed = (12f + random.nextFloat() * 24f) * dp / 2.75f
        val y = random.nextFloat() * size.height * 0.3f
        val span = size.width + width
        val x = size.width - ((time * speed + random.nextFloat() * span) % span)
        // Snap to whole pixels of the cloud grid so it moves in crisp steps
        val cell = width / cloud.columns
        val snapped = (x / cell).toInt() * cell
        drawPixelCloud(cloud, Rect(Offset(snapped, y), Size(width, height)), alpha, storm, night)
    }
}

/** Small cloud for the weather chip. */
internal fun DrawScope.drawChipCloud(dst: Rect) = drawPixelCloud(cloudShapes[0], dst)
