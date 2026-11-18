package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.graphics.drawscope.DrawScope

/** Old entry point for drawing the cozy room; draws [CozyRoom] through the [IndoorScene] API. */
internal object RoomRenderer {
    private val toys = IndoorToys()

    @Suppress("UNUSED_PARAMETER")
    fun draw(scope: DrawScope, g: RoomGeometry, block: Float, tint: Float, time: Float, deskLampOn: Boolean, catFlickAt: Float) {
        toys.deskLampOn = deskLampOn
        toys.catFlickAt = catFlickAt
        g.scene.draw(scope, tint, time, toys)
    }
}
