package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrowthPaceTest {
    private val day = 86_400.0

    /** Days until [species] is fully grown at a constant fertilizer level, kept healthy. */
    private fun daysToGrow(species: Species, fertilizer: Double): Double =
        GameRules.STAGE_3_GROWTH / GameRules.growthPerSecond(PlantState(species = species, fertilizer = fertilizer)) / day

    @Test
    fun everySpeciesTakesItsOwnDaysWithFullFertilizer() {
        Species.entries.forEach { assertEquals(it.growDays, daysToGrow(it, 100.0), 1e-9, it.name) }
    }

    @Test
    fun theOrderFollowsTheRealPlants() {
        val order = listOf(
            Species.SPROUT, Species.SUNFLOWER, Species.TULIP, Species.POTHOS, Species.HEARTS,
            Species.FERN, Species.MONSTERA, Species.FLYTRAP, Species.CACTUS, Species.BONSAI,
        )
        assertEquals(order, Species.entries.sortedBy { it.growDays })
        assertTrue(Species.entries.all { it.growDays in 1.0..7.0 }, "between a day and a week")
    }

    @Test
    fun withoutFertilizerItTakesThreeTimesAsLong() {
        assertEquals(3 * Species.TULIP.growDays, daysToGrow(Species.TULIP, 0.0), 1e-9)
    }

    @Test
    fun theTickUsesThePace() {
        val env = Environment(5_000f, WeatherState.CLEAR_SKY, 15.0, 1_000L)
        fun grown(species: Species) =
            GameRules.tickPlant(PlantState(species = species, love = 100.0), env, 3600.0).growth
        assertEquals(grown(Species.SPROUT) / 7.0, grown(Species.BONSAI), grown(Species.SPROUT) * 0.01)
    }
}
