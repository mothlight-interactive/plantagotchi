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
    val weatherChip: Rect,
    val menuButton: Rect,
    val chest: Rect,
    val shopButton: Rect,
    val arrowLeft: Rect,
    val arrowRight: Rect,
    val plantLabelY: Float,
    val replantButton: Rect,
    val compostButton: Rect,
    val needsLabelBaseline: Float,
    /** Love, fertilizer, water, light. */
    val bubbles: List<Rect>,
) {
    /** Bottom center of the stem, rotation pivot for swaying. */
    val plantPivot: Offset get() = Offset(plant.center.x, plant.bottom)

    /** One pixel of the 25 px plant grid. */
    val plantPixel: Float get() = plant.width / 25.06f

    /** Roughly where the leaves are, for effects. */
    val leaves: Rect get() = Rect(plant.left + plant.width * 0.25f, plant.top + plant.height * 0.35f, plant.right - plant.width * 0.25f, plant.bottom - plant.height * 0.1f)

    companion object {
        fun compute(size: Size, dp: Float, topInset: Float, bottomInset: Float, potAspect: Float): SceneLayout {
            val w = size.width
            val h = size.height
            val ground = Rect(0f, h - 128 * dp, w, h)

            val potWidth = w * 0.86f
            val potBottom = ground.top + 48 * dp
            val pot = Rect((w - potWidth) / 2, potBottom - potWidth * potAspect, (w + potWidth) / 2, potBottom)
            // Plant and pot vectors share the same 296 px wide viewport.
            val plant = Rect(pot.left, pot.top + 2 * dp - potWidth, pot.right, pot.top + 2 * dp)

            val top = topInset + 12 * dp
            val coinHud = Rect(12 * dp, top, 132 * dp, top + 38 * dp)
            val weatherChip = Rect(16 * dp, coinHud.bottom + 8 * dp, 112 * dp, coinHud.bottom + 40 * dp)
            val menuButton = Rect(w - 52 * dp, top - 2 * dp, w - 12 * dp, top + 38 * dp)
            val chest = Rect(menuButton.left - 50 * dp, top, menuButton.left - 10 * dp, top + 38 * dp)
            val shopButton = Rect(chest.left - 52 * dp, top - 3 * dp, chest.left - 8 * dp, top + 41 * dp)

            val arrowY = pot.top - 8 * dp
            val arrowLeft = Rect(8 * dp, arrowY - 24 * dp, 44 * dp, arrowY + 24 * dp)
            val arrowRight = Rect(w - 44 * dp, arrowY - 24 * dp, w - 8 * dp, arrowY + 24 * dp)

            val buttonW = 150 * dp
            val buttonH = 40 * dp
            val buttonsY = pot.top - 120 * dp
            val replant = Rect(w / 2 - buttonW / 2, buttonsY, w / 2 + buttonW / 2, buttonsY + buttonH)
            val compost = Rect(replant.left, replant.bottom + 10 * dp, replant.right, replant.bottom + 10 * dp + buttonH)

            val bubble = 80 * dp
            val spacing = (w - 4 * bubble) / 5
            val bubbleTop = h - bottomInset - 10 * dp - bubble
            val bubbles = (0 until 4).map { i ->
                val left = spacing + i * (bubble + spacing)
                Rect(left, bubbleTop, left + bubble, bubbleTop + bubble)
            }

            return SceneLayout(
                size, dp, ground, pot, plant, coinHud, weatherChip, menuButton, chest, shopButton,
                arrowLeft, arrowRight, plantLabelY = weatherChip.bottom + 14 * dp,
                replantButton = replant, compostButton = compost,
                needsLabelBaseline = bubbleTop - 4 * dp, bubbles = bubbles,
            )
        }
    }
}
