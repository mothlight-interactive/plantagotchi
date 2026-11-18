package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.Arrangement
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.RoomSpot
import kotlin.test.Test
import kotlin.test.assertEquals

class ArrangementTest {
    private fun player(vararg spots: RoomSpot?) =
        PlayerState(plants = spots.mapIndexed { i, s -> PlantState(id = "p$i", spot = s) }, activePlantId = "p0")

    @Test
    fun newPlantsFillTheFirstFreeSpots() {
        assertEquals(
            listOf(RoomSpot.SILL_LEFT, RoomSpot.DESK, RoomSpot.SILL_RIGHT),
            RoomSpot.assign(player(null, RoomSpot.DESK, null).plants),
        )
        // A duplicate spot (e.g. from an old save) falls back to a free one
        assertEquals(
            listOf(RoomSpot.SHELF, RoomSpot.SILL_LEFT),
            RoomSpot.assign(player(RoomSpot.SHELF, RoomSpot.SHELF).plants),
        )
    }

    @Test
    fun movingOntoAnOccupiedSpotSwaps() {
        val state = player(null, null, null) // sill left, sill right, floor left
        val moved = Arrangement.move(state, 0, RoomSpot.FLOOR_LEFT)
        assertEquals(listOf(RoomSpot.FLOOR_LEFT, RoomSpot.SILL_RIGHT, RoomSpot.SILL_LEFT), moved.plants.map { it.spot })
        val free = Arrangement.move(moved, 1, RoomSpot.HANGING)
        assertEquals(RoomSpot.HANGING, RoomSpot.assign(free.plants)[1])
        assertEquals(moved.activePlantId, free.activePlantId)
    }

    @Test
    fun swappingChangesTheOrderOutdoors() {
        val state = player(null, null, null)
        assertEquals(listOf("p2", "p1", "p0"), Arrangement.swap(state, 0, 2).plants.map { it.id })
        assertEquals(state, Arrangement.swap(state, 1, 1))
    }
}
