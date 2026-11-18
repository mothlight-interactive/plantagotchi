package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.platform.WidgetSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetSnapshotTest {
    private val player = PlayerState(
        plants = listOf(
            PlantState(id = "a", species = Species.TULIP),
            PlantState(id = "b", species = Species.CACTUS),
            PlantState(id = "c", species = Species.TULIP),
        ),
        activePlantId = "b",
        coins = 42,
    )
    private val snapshot = WidgetSnapshot.of(player, now = 1L) { it.name.lowercase() }

    @Test
    fun listsAllPlantsAndNumbersDuplicates() {
        assertEquals(listOf("tulip 1", "cactus", "tulip 2"), snapshot.plants.map { it.name })
        assertEquals("b", snapshot.active?.id)
        assertEquals(42, snapshot.coins)
    }

    @Test
    fun stepsWrapAround() {
        assertEquals("c", snapshot.step("b", 1)?.id)
        assertEquals("a", snapshot.step("c", 1)?.id)
        assertEquals("c", snapshot.step("a", -1)?.id)
        // Unknown id starts from the active plant
        assertEquals("c", snapshot.step(null, 1)?.id)
    }

    @Test
    fun jsonRoundTrip() {
        assertEquals(snapshot, WidgetSnapshot.fromJson(snapshot.toJson()))
        assertNull(WidgetSnapshot.fromJson("garbage"))
    }
}
