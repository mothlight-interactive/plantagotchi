package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pixel version of the Mothlight wordmark: "mothlight" whose dotless i carries a glowing lamp,
 * with a small moth circling it. Rasterized from Bricolage Grotesque Bold and cleaned up by hand.
 */
object MothlightLogo {
    val ink = Color(0xFFEDE6D6)
    val lamp = Color(0xFFF2B84B)
    val background = Color(0xFF14121F)

    val wordmark = PixelSprite(
        listOf(
            ".................................###........###................###...............",
            "..........................###....###........###................###.........###...",
            "..........................###....###........###................###.........###...",
            "##.####.####....######...######..###.####...###.###..#########.###.####...######.",
            "#############..########..######..#########..###.###.##########.#########..######.",
            "###..###..###.###....###..###....####...###.###.###.###...###..####...###..###...",
            "###..###..###.###....###..###....###....###.###.###.###...###..###....###..###...",
            "###..###..###.###....###..###....###....###.###.###..#######...###....###..###...",
            "###..###..###.###....###..###....###....###.###.###..######....###....###..###...",
            "###..###..###.###....###..###....###....###.###.###.###........###....###..###...",
            "###..###..###.###....###..###....###....###.###.###.#########..###....###..###...",
            "###..###..###..########...#####..###....###.###.###.##########.###....###..#####.",
            "###..###..###...######.....####..###....###.###.###.###....###.###....###...####.",
            "....................................................###....###...................",
            "....................................................##########...................",
            ".....................................................########....................",
        ),
        mapOf('#' to ink),
    )

    /** Column of the dotless i, the lamp sits above it. */
    const val LAMP_COLUMN = 48

    private val mothUp = PixelSprite(
        listOf(
            "#.......#",
            "##..#..##",
            "###.#.###",
            ".#######.",
            "..##.##..",
            "....#....",
        ),
        mapOf('#' to ink),
    )

    private val mothDown = PixelSprite(
        listOf(
            "....#....",
            "....#....",
            ".###.###.",
            "#########",
            ".##.#.##.",
            "....#....",
        ),
        mapOf('#' to ink),
    )

    val width get() = wordmark.width
    val height get() = wordmark.height

    /**
     * Draws the animated logo with its top-left at [topLeft]. [time] in seconds drives the lamp glow
     * and the moth; pass a constant for a still image.
     */
    fun draw(scope: DrawScope, topLeft: Offset, pixel: Float, time: Float, alpha: Float = 1f) = with(scope) {
        wordmark.draw(this, topLeft, pixel, alpha)

        val lampCenter = Offset(topLeft.x + (LAMP_COLUMN + 1.5f) * pixel, topLeft.y + 1.2f * pixel)
        val glow = 0.75f + 0.25f * sin(time * 2.4f)
        drawCircle(
            Brush.radialGradient(listOf(lamp.copy(alpha = 0.45f * alpha), Color.Transparent), center = lampCenter, radius = pixel * 7 * glow),
            radius = pixel * 7 * glow,
            center = lampCenter,
        )
        drawRect(lamp, Offset(lampCenter.x - 1.5f * pixel, lampCenter.y - pixel), Size(3 * pixel, 2 * pixel), alpha)
        drawRect(Color(0xFFFFE6A8), Offset(lampCenter.x - 0.5f * pixel, lampCenter.y - pixel), Size(pixel, pixel), alpha)

        // The moth circles the lamp on a flat ellipse, flapping its wings.
        val angle = time * 2.2f
        val mothPixel = pixel * 0.7f
        val moth = if ((time * 9).toInt() % 2 == 0) mothUp else mothDown
        val topLeftMoth = Offset(
            lampCenter.x + cos(angle) * pixel * 7f - moth.width * mothPixel / 2,
            lampCenter.y + sin(angle) * pixel * 3.5f - pixel * 2.5f - moth.height * mothPixel / 2,
        )
        moth.draw(this, topLeftMoth, mothPixel, alpha)
    }
}
