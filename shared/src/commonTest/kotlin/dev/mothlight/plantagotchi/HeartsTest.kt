package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartsTest {
    @Test
    fun stringOfHeartsIsSoldAndHasAVoice() {
        assertTrue(ShopCatalog.seeds.any { it.species == Species.HEARTS })
        assertTrue(AudioAssets.voice(Species.HEARTS, true) in AudioAssets.all)
        assertTrue(AudioAssets.voice(Species.HEARTS, false) in AudioAssets.all)
    }

    @Test
    fun itHangsButOnlyOnePlantFitsThePlanter() {
        assertTrue(Species.HEARTS.hanging)
        val spots = RoomSpot.assign(
            listOf(PlantState(id = "a", species = Species.POTHOS), PlantState(id = "b", species = Species.HEARTS)),
        )
        assertEquals(listOf(RoomSpot.HANGING, RoomSpot.SILL_LEFT), spots)
    }
}
