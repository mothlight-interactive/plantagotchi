package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Scenery

/**
 * Old entry point for the cozy room, kept until GameCanvas and SceneLayout use [IndoorScene]
 * ([IndoorScenes.forScenery]). [block] is the old 4 dp art pixel; the room is drawn in fine
 * pixels ([PIXEL_DP]) at the same on-screen size.
 */
internal class RoomGeometry(val size: Size, val floorTop: Float, val block: Float) {
    val scene = IndoorScenes.forScenery(Scenery.ROOM, size, floorTop, block * PIXEL_DP / 4f) as CozyRoom

    val glass: Rect get() = scene.glass
    val deskLamp: Rect get() = scene.deskLamp
    val cat: Rect get() = scene.cat

    /** Bottom center of a pot standing on [spot] and the pot width. */
    fun anchor(spot: RoomSpot): Pair<Offset, Float> = scene.anchor(spot)
}
