package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.Arrangement
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.RoomSpot
import kotlin.test.Test
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val free = Arrangement.move(moved, 1, RoomSpot.SHELF)
        assertEquals(RoomSpot.SHELF, RoomSpot.assign(free.plants)[1])
        assertEquals(moved.activePlantId, free.activePlantId)
    }

    @Test
    fun swappingChangesTheOrderOutdoors() {
        val state = player(null, null, null)
        assertEquals(listOf("p2", "p1", "p0"), Arrangement.swap(state, 0, 2).plants.map { it.id })
        assertEquals(state, Arrangement.swap(state, 1, 1))
    }

    @Test
    fun thePlanterIsForHangingPlantsOnly() {
        val bonsai = PlantState(id = "b", species = Species.BONSAI)
        val pothos = PlantState(id = "p", species = Species.POTHOS)
        val state = PlayerState(plants = listOf(bonsai, pothos), activePlantId = "b")
        assertEquals(listOf(RoomSpot.SILL_LEFT, RoomSpot.HANGING), RoomSpot.assign(state.plants))

        // A standing plant cannot be moved into the planter
        assertEquals(state, Arrangement.move(state, 0, RoomSpot.HANGING))
        // A saved planter spot of a standing plant (old saves) is not used
        assertEquals(RoomSpot.SILL_LEFT, RoomSpot.assign(listOf(bonsai.copy(spot = RoomSpot.HANGING)))[0])

        // The pothos may stand anywhere; swapping with a standing plant never puts that one in the planter
        val swapped = Arrangement.move(state, 1, RoomSpot.SILL_LEFT)
        val spots = RoomSpot.assign(swapped.plants)
        assertEquals(RoomSpot.SILL_LEFT, spots[1])
        assertTrue(spots[0] != RoomSpot.HANGING && spots[0] != RoomSpot.SILL_LEFT)
    }
}
