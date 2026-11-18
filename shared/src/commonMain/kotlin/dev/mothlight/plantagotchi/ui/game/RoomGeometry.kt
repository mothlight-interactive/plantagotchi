package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.mothlight.plantagotchi.data.state.RoomSpot
import kotlin.math.floor

/**
 * Where things are in the cozy room, as fractions of the canvas so the shop preview gets the
 * same room in small. [floorTop] is where the wall meets the floor (the ground line of the scene).
 */
internal class RoomGeometry(val size: Size, val floorTop: Float, val block: Float) {
    private val w = size.width
    private val h = size.height
    private fun snap(v: Float) = floor(v / block) * block

    val glass = Rect(snap(w * 0.45f), snap(h * 0.16f), snap(w * 0.94f), snap(h * 0.42f))
    val frame = Rect(glass.left - block * 3, glass.top - block * 3, glass.right + block * 3, glass.bottom + block * 2)
    /** Top surface and front edge of the windowsill board. */
    val sill = Rect(glass.left - block * 5, frame.bottom, glass.right + block * 5, frame.bottom + block * 4)
    val radiator = Rect(
        snap(glass.left + glass.width * 0.1f), sill.bottom + block * 3,
        snap(glass.right - glass.width * 0.1f), minOf(floorTop - block * 6, snap(sill.bottom + block * 3 + h * 0.1f)),
    )
    val shelf = Rect(snap(w * 0.03f), snap(h * 0.47f), snap(w * 0.38f), snap(h * 0.47f) + block * 2)
    val picture = Rect(snap(w * 0.05f), snap(h * 0.25f), snap(w * 0.17f), snap(h * 0.34f))
    /** The hook in the ceiling and the bottom of the hanging planter. */
    val hangX = snap(w * 0.25f)
    val hangBottom = snap(h * 0.31f)
    val desk = Rect(snap(w * 0.03f), snap(floorTop - h * 0.15f), snap(w * 0.42f), snap(floorTop - h * 0.15f) + block * 3)
    /** Desk lamp: tap area around the shade and stem. */
    val deskLamp = Rect(desk.left + block, desk.top - block * 17, desk.left + block * 13, desk.top)
    val cup = Offset(snap(w * 0.33f), desk.top)
    /** The cat sits at the right end of the windowsill. */
    val cat = Rect(sill.right - block * 12, sill.top - block * 12, sill.right - block * 3, sill.top)

    /** Bottom center of a pot standing on [spot] and the pot width. */
    fun anchor(spot: RoomSpot): Pair<Offset, Float> = when (spot) {
        RoomSpot.SILL_LEFT -> Offset(glass.left + glass.width * 0.24f, sill.top) to w * 0.15f
        RoomSpot.SILL_RIGHT -> Offset(glass.left + glass.width * 0.6f, sill.top) to w * 0.15f
        RoomSpot.SHELF -> Offset(w * 0.27f, shelf.top) to w * 0.13f
        RoomSpot.DESK -> Offset(w * 0.25f, desk.top) to w * 0.14f
        RoomSpot.HANGING -> Offset(hangX, hangBottom) to w * 0.14f
        RoomSpot.FLOOR_LEFT -> Offset(w * 0.6f, floorTop + w * 0.02f) to w * 0.24f
        RoomSpot.FLOOR_RIGHT -> Offset(w * 0.87f, floorTop + w * 0.02f) to w * 0.2f
    }
}
