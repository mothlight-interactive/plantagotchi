package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.mothlight.plantagotchi.data.state.RoomSpot

/** One plant in its pot. Plant and pot share the same art pixel size. */
internal class PlantSlot(val pot: Rect, val plant: Rect) {
    /** One pixel of the plant grid; plants may be wider than their pot. */
    val pixel: Float get() = plant.width / PlantRenderer.GRID
    val base: Offset get() = Offset(plant.center.x, plant.bottom)
    /** Area that counts as tapping this plant. */
    val touch: Rect get() = Rect(minOf(pot.left, plant.left + plant.width * 0.2f), plant.top + plant.height * 0.3f, maxOf(pot.right, plant.right - plant.width * 0.2f), pot.bottom)
}

/** Positions of all scene elements in px. Also used for hit testing. */
internal class SceneLayout(
    val size: Size,
    /** 1 dp in px. */
    val dp: Float,
    val ground: Rect,
    val slots: List<PlantSlot>,
    val activeIndex: Int,
    /** Set in indoor scenes. */
    val room: IndoorScene?,
    val coinHud: Rect,
    val weatherChip: Rect,
    val menuButton: Rect,
    val chest: Rect,
    val replantButton: Rect,
    val compostButton: Rect,
    val needsLabelBaseline: Float,
    /** Love, fertilizer, water, light. */
    val bubbles: List<Rect>,
) {
    private val active get() = slots[activeIndex]
    val pot: Rect get() = active.pot
    val plant: Rect get() = active.plant
    val plantPixel: Float get() = active.pixel

    /** Bottom center of the active stem. */
    val plantPivot: Offset get() = Offset(plant.center.x, plant.bottom)

    /** Roughly where the active plant's leaves are, for effects. */
    val leaves: Rect get() = Rect(plant.left + plant.width * 0.25f, plant.top + plant.height * 0.35f, plant.right - plant.width * 0.25f, plant.bottom - plant.height * 0.1f)

    companion object {
        /**
         * @param weights relative width per plant (the active one is animated towards a larger weight)
         * @param potAspects pot height / width per plant
         */
        fun compute(
            size: Size,
            dp: Float,
            topInset: Float,
            bottomInset: Float,
            weights: List<Float>,
            potAspects: List<Float>,
            activeIndex: Int,
            /** Spots per plant in an indoor scene; null for the outdoor row. */
            roomSpots: List<RoomSpot>? = null,
            /** The indoor scene, built by [indoorScene]; required with [roomSpots]. */
            indoor: IndoorScene? = null,
        ): SceneLayout {
            val w = size.width
            val h = size.height
            val ground = Rect(0f, floorTop(size, dp), w, h)

            val available = w * 0.96f
            val total = weights.sum().coerceAtLeast(0.01f)
            var x = (w - available) / 2
            val room = if (roomSpots != null) indoor else null
            val slots = if (room != null && roomSpots != null) roomSpots.mapIndexed { i, spot ->
                val (anchor, baseWidth) = room.anchor(spot)
                // The selected plant grows towards a larger size (weight 1 = normal).
                val zoom = 1f + (weights[i] - 1f) / (ACTIVE_WEIGHT - 1f) * (SELECTED_ZOOM - 1f)
                val potWidth = baseWidth * zoom
                val potBottom = anchor.y + potWidth * 0.03f
                val pot = Rect(anchor.x - potWidth / 2, potBottom - potWidth * potAspects[i], anchor.x + potWidth / 2, potBottom)
                val plantBottom = pot.top + potWidth * 0.006f
                val plantSize = potWidth * PLANT_TO_POT
                PlantSlot(pot, Rect(anchor.x - plantSize / 2, plantBottom - plantSize, anchor.x + plantSize / 2, plantBottom))
            } else weights.mapIndexed { i, weight ->
                val slotWidth = available * weight / total
                val potWidth = minOf(slotWidth * 0.9f, w * 0.62f * weight / ACTIVE_WEIGHT)
                val potBottom = ground.top + potWidth * 0.14f
                val center = x + slotWidth / 2
                x += slotWidth
                val pot = Rect(center - potWidth / 2, potBottom - potWidth * potAspects[i], center + potWidth / 2, potBottom)
                val plantBottom = pot.top + potWidth * 0.006f
                val plantSize = potWidth * PLANT_TO_POT
                PlantSlot(pot, Rect(center - plantSize / 2, plantBottom - plantSize, center + plantSize / 2, plantBottom))
            }

            val top = topInset + 12 * dp
            val coinHud = Rect(12 * dp, top, 132 * dp, top + 38 * dp)
            val weatherChip = Rect(16 * dp, coinHud.bottom + 8 * dp, 112 * dp, coinHud.bottom + 40 * dp)
            val menuButton = Rect(w - 52 * dp, top - 2 * dp, w - 12 * dp, top + 38 * dp)
            val chest = Rect(menuButton.left - 50 * dp, top, menuButton.left - 10 * dp, top + 38 * dp)

            val activePot = slots[activeIndex].pot
            val buttonW = 150 * dp
            val buttonH = 40 * dp
            val buttonsY = activePot.top - activePot.width * 0.55f - buttonH
            val buttonX = activePot.center.x.coerceIn(buttonW / 2 + 8 * dp, w - buttonW / 2 - 8 * dp)
            val replant = Rect(buttonX - buttonW / 2, buttonsY, buttonX + buttonW / 2, buttonsY + buttonH)
            val compost = Rect(replant.left, replant.bottom + 10 * dp, replant.right, replant.bottom + 10 * dp + buttonH)

            val bubble = 80 * dp
            val spacing = (w - 4 * bubble) / 5
            val bubbleTop = h - bottomInset - 10 * dp - bubble
            val bubbles = (0 until 4).map { i ->
                val left = spacing + i * (bubble + spacing)
                Rect(left, bubbleTop, left + bubble, bubbleTop + bubble)
            }

            return SceneLayout(
                size, dp, ground, slots, activeIndex, room, coinHud, weatherChip, menuButton, chest,
                replantButton = replant, compostButton = compost,
                needsLabelBaseline = bubbleTop - 4 * dp, bubbles = bubbles,
            )
        }

        const val ACTIVE_WEIGHT = 2.2f

        /** How much larger the selected plant is drawn in an indoor scene. */
        const val SELECTED_ZOOM = 1.3f

        /** Plant rect width relative to the pot width: same art pixel for plant and pot. */
        const val PLANT_TO_POT = PlantRenderer.GRID.toFloat() / PotRenderer.GRID

        /** Where the floor of the scene starts. */
        fun floorTop(size: Size, dp: Float) = size.height - 128 * dp

        /** The indoor scene for [scenery] on a canvas of [size], or null outdoors. */
        fun indoorScene(scenery: dev.mothlight.plantagotchi.data.state.Scenery, size: Size, dp: Float): IndoorScene? =
            IndoorScenes.forScenery(scenery, size, floorTop(size, dp), PIXEL_DP * dp)
    }
}
