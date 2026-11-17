package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pixel art defined in code. Each char of [rows] is one pixel, '.' is transparent.
 * Being plain data, sprites can be recolored, flipped or animated per pixel.
 */
class PixelSprite(rows: List<String>, private val palette: Map<Char, Color>) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size

    private val pixels: List<Triple<Int, Int, Color>> = rows.flatMapIndexed { y, row ->
        row.mapIndexedNotNull { x, c -> palette[c]?.let { Triple(x, y, it) } }
    }

    /** Draws the sprite with its top-left corner at [topLeft], each pixel [pixel] px wide. */
    fun draw(scope: DrawScope, topLeft: Offset, pixel: Float, alpha: Float = 1f) {
        val size = Size(pixel + 0.5f, pixel + 0.5f) // overlap avoids hairline gaps
        pixels.forEach { (x, y, color) ->
            scope.drawRect(color, Offset(topLeft.x + x * pixel, topLeft.y + y * pixel), size, alpha)
        }
    }

    /** Draws the sprite scaled to fit [box] (keeping pixels square), centered. */
    fun drawFit(scope: DrawScope, box: Size, topLeft: Offset = Offset.Zero, alpha: Float = 1f) {
        val pixel = minOf(box.width / width, box.height / height)
        val offset = Offset(
            topLeft.x + (box.width - width * pixel) / 2,
            topLeft.y + (box.height - height * pixel) / 2,
        )
        draw(scope, offset, pixel, alpha)
    }
}

/** Sprites that are not part of the original asset pack. */
object CodeSprites {
    private val outline = Color(0xFF222034)

    val wateringCan = PixelSprite(
        listOf(
            "......oooo........",
            ".....o....o.......",
            ".....o....o.......",
            "..oooooooooooo...o",
            ".oGGGGGGGGGGGGo.oG",
            ".oGlGGGGGGGGGGooGo",
            ".oGlGGGGGGGGGGGGo.",
            ".oGlGGGGGGGGGGGo..",
            ".oGlGGGGGGGGGGo...",
            ".oGGGGGGGGGGGGo...",
            ".odddddddddddddo..",
            "..oooooooooooo....",
        ),
        mapOf('o' to outline, 'G' to Color(0xFF5FCDE4), 'l' to Color(0xFFCBF4FF), 'd' to Color(0xFF3B8EA5)),
    )

    val fertilizerBag = PixelSprite(
        listOf(
            "...oooooo...",
            "..oBBBBBBo..",
            "...oBBBBo...",
            "..oBBBBBBo..",
            ".oBBBBBBBBo.",
            ".oBByyyyBBo.",
            ".oByykkyyBo.",
            ".oByyKkyyBo.",
            ".oByykkyyBo.",
            ".oBByyyyBBo.",
            ".oBBBBBBBBo.",
            ".obBBBBBBbo.",
            "..obbbbbbo..",
            "...oooooo...",
        ),
        mapOf(
            'o' to outline,
            'B' to Color(0xFFB86F50),
            'b' to Color(0xFF8F563B),
            'y' to Color(0xFFEEC39A),
            'k' to Color(0xFF54C30A),
            'K' to Color(0xFF009705),
        ),
    )

    val growLamp = PixelSprite(
        listOf(
            ".....oo.....",
            ".....oo.....",
            "...oooooo...",
            "..oGGGGGGo..",
            ".oGGlGGGGGo.",
            "oGGGGGGGGGGo",
            "oooooooooooo",
            "...yYYYYy...",
            "....yYYy....",
            ".....yy.....",
        ),
        mapOf(
            'o' to outline,
            'G' to Color(0xFF6D6EBF),
            'l' to Color(0xFFB3B4F5),
            'y' to Color(0xFFFFEB57),
            'Y' to Color(0xFFFFFBD6),
        ),
    )

    val shopBag = PixelSprite(
        listOf(
            "....oooo....",
            "...o....o...",
            "..o......o..",
            ".oRRRRRRRRo.",
            ".oRRRRRRRRo.",
            ".oRRyyyyRRo.",
            ".oRyYYYYyRo.",
            ".oRyYyyYyRo.",
            ".oRyYYYYyRo.",
            ".oRRyyyyRRo.",
            ".orRRRRRRro.",
            "..oooooooo..",
        ),
        mapOf(
            'o' to outline,
            'R' to Color(0xFFD77BBA),
            'r' to Color(0xFFA9568E),
            'y' to Color(0xFFF5BD1C),
            'Y' to Color(0xFFFFEB57),
        ),
    )

    val sparkle = PixelSprite(
        listOf(
            "..w..",
            "..w..",
            "wwYww",
            "..w..",
            "..w..",
        ),
        mapOf('w' to Color(0xFFFFFBD6), 'Y' to Color(0xFFFFEB57)),
    )
}
