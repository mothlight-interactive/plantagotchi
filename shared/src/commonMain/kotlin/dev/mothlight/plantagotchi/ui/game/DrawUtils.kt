package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Draws [image] stretched into [dst] with crisp pixels. */
internal fun DrawScope.drawBitmap(
    image: ImageBitmap,
    dst: Rect,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) = drawImage(
    image = image,
    srcOffset = IntOffset.Zero,
    srcSize = IntSize(image.width, image.height),
    dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
    dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
    alpha = alpha,
    colorFilter = colorFilter,
    filterQuality = FilterQuality.None,
)

/** Draws [image] like ContentScale.Crop into [dst]. */
internal fun DrawScope.drawBitmapCrop(image: ImageBitmap, dst: Rect, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val scale = maxOf(dst.width / image.width, dst.height / image.height)
    val srcW = (dst.width / scale).roundToInt().coerceIn(1, image.width)
    val srcH = (dst.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

internal fun DrawScope.drawPainter(
    painter: Painter,
    dst: Rect,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) = translate(dst.left, dst.top) {
    with(painter) { draw(dst.size, alpha, colorFilter) }
}

/** Height / width of a painter, falling back to [default] for painters without intrinsic size. */
fun Painter.aspectRatioOrDefault(default: Float = 1f): Float {
    val s: Size = intrinsicSize
    return if (s == Size.Unspecified || s.width <= 0f) default else s.height / s.width
}

internal fun Rect.scaled(factor: Float): Rect {
    val w = width * factor
    val h = height * factor
    return Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 16x16 pixel ring of the round buttons: per row, the inclusive x ranges of the outline. */
private val ringRows: List<List<IntRange>> = listOf(
    listOf(6..9),
    listOf(4..5, 10..11),
    listOf(3..3, 12..12),
    listOf(2..2, 13..13),
    listOf(1..1, 14..14),
    listOf(1..1, 14..14),
    listOf(0..0, 15..15),
    listOf(0..0, 15..15),
    listOf(0..0, 15..15),
    listOf(0..0, 15..15),
    listOf(1..1, 14..14),
    listOf(1..1, 14..14),
    listOf(2..2, 13..13),
    listOf(3..3, 12..12),
    listOf(4..5, 10..11),
    listOf(6..9),
)

/**
 * Round pixel button drawn in code (crisp on every platform): filled disc, light rim on the upper
 * left, dark outline. [fill] null draws only the outline.
 */
internal fun DrawScope.drawPixelButton(
    dst: Rect,
    fill: Color? = Color(0xFF3F282F),
    outline: Color = Color(0xFF222034),
    highlight: Color = Color(0x33FFFFFF),
) {
    val cw = dst.width / 16f
    val ch = dst.height / 16f
    // Slight overlap avoids hairline seams between cells.
    fun cells(row: Int, from: Int, to: Int, color: Color) = drawRect(
        color,
        Offset(dst.left + from * cw, dst.top + row * ch),
        Size((to - from + 1) * cw + 0.5f, ch + 0.5f),
    )
    ringRows.forEachIndexed { row, ranges ->
        val left = ranges.first().last + 1
        val right = ranges.last().first - 1
        if (fill != null && ranges.size == 2 && left <= right) {
            cells(row, left, right, fill)
            // Rim light: first inner cell on the upper half, and the top inner row.
            if (row < 8) cells(row, left, left, highlight)
            if (row == 1 || row == 2) cells(row, left, right, highlight)
        }
        ranges.forEach { cells(row, it.first, it.last, outline) }
    }
}
