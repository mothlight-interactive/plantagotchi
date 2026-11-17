package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** Positions of all scene elements in px. Also used for hit testing. */
internal class SceneLayout(
    val size: Size,
    /** 1 dp in px. */
    val dp: Float,
    val ground: Rect,
    val pot: Rect,
    val plant: Rect,
    val coinHud: Rect,
    val shopButton: Rect,
    val chest: Rect,
    val needsLabelBaseline: Float,
    /** Love, fertilizer, water, light. */
    val bubbles: List<Rect>,
) {
    /** Bottom center of the stem, rotation pivot for swaying. */
    val plantPivot: Offset get() = Offset(plant.center.x, plant.bottom)

    /** Roughly where the leaves are, for effects. */
    val leaves: Rect get() = Rect(plant.left + plant.width * 0.25f, plant.top + plant.height * 0.35f, plant.right - plant.width * 0.25f, plant.bottom - plant.height * 0.1f)

    companion object {
        fun compute(size: Size, dp: Float, topInset: Float, bottomInset: Float, potAspect: Float): SceneLayout {
            val w = size.width
            val h = size.height
            val ground = Rect(0f, h - 128 * dp, w, h)

            val potWidth = w * 0.92f
            val potBottom = ground.top + 48 * dp
            val pot = Rect(
                (w - potWidth) / 2, potBottom - potWidth * potAspect,
                (w + potWidth) / 2, potBottom,
            )
            // Plant and pot vectors share the same 296 px wide viewport.
            val plant = Rect(pot.left, pot.top + 2 * dp - potWidth, pot.right, pot.top + 2 * dp)

            val coinHud = Rect(20 * dp, topInset + 41 * dp, 140 * dp, topInset + 79 * dp)
            val chest = Rect(w - 53 * dp, topInset + 35 * dp, w - 13 * dp, topInset + 75 * dp)
            val shopButton = Rect(chest.left - 56 * dp, topInset + 33 * dp, chest.left - 12 * dp, topInset + 77 * dp)

            val bubble = 80 * dp
            val spacing = (w - 4 * bubble) / 5
            val bubbleTop = h - bottomInset - 10 * dp - bubble
            val bubbles = (0 until 4).map { i ->
                val left = spacing + i * (bubble + spacing)
                Rect(left, bubbleTop, left + bubble, bubbleTop + bubble)
            }

            return SceneLayout(size, dp, ground, pot, plant, coinHud, shopButton, chest, bubbleTop - 4 * dp, bubbles)
        }
    }
}
