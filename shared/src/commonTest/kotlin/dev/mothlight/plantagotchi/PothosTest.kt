package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PothosTest {
    @Test
    fun pothosIsSoldAndHasAVoice() {
        assertTrue(ShopCatalog.seeds.any { it.species == Species.POTHOS })
        assertTrue(AudioAssets.voice(Species.POTHOS, true) in AudioAssets.all)
    }

    @Test
    fun pothosPrefersTheHangingPlanter() {
        val spots = RoomSpot.assign(listOf(PlantState(id = "a"), PlantState(id = "b", species = Species.POTHOS)))
        assertEquals(listOf(RoomSpot.SILL_LEFT, RoomSpot.HANGING), spots)
        // Not when another hanging plant is already there
        val taken = RoomSpot.assign(listOf(PlantState(id = "a", species = Species.POTHOS, spot = RoomSpot.HANGING), PlantState(id = "b", species = Species.POTHOS)))
        assertEquals(RoomSpot.SILL_LEFT, taken[1])
    }
}
