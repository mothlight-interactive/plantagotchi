package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.PlayerStateCodec
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorAndSpeciesTest {

    private fun tickHalfDay(species: Species): PlantState {
        val plant = PlantState(species = species, love = 100.0, fertilizer = 100.0)
        val player = PlayerState(plants = listOf(plant), activePlantId = plant.id)
        return GameRules.tick(player, Environment(0f, WeatherState.CLEAR_SKY, 15.0, 0L), seconds = 43_200.0).activePlant
    }

    @Test
    fun newSpeciesHaveTheirOwnNeeds() {
        val sprout = tickHalfDay(Species.SPROUT)
        assertTrue(tickHalfDay(Species.BONSAI).love < sprout.love, "bonsai wants more love")
        assertTrue(tickHalfDay(Species.FLYTRAP).fertilizer > sprout.fertilizer, "flytrap feeds itself")
        assertTrue(tickHalfDay(Species.FLYTRAP).water < sprout.water, "flytrap is thirsty")
        assertTrue(tickHalfDay(Species.SUNFLOWER).lux < sprout.lux, "sunflower needs more light")
        assertTrue(tickHalfDay(Species.MONSTERA).lux > sprout.lux, "monstera likes shade")
        assertEquals(8, ShopCatalog.seeds.size)
    }

    @Test
    fun oldSavesGetDefaultScenery() {
        val state = PlayerStateCodec.decode("""{"coins":3,"plants":[{"species":"BONSAI"}]}""")
        assertEquals(Scenery.ROOM, state.scenery)
        assertEquals(Species.BONSAI, state.activePlant.species)
    }
}
