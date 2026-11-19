package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameRulesTest {

    private fun env(lux: Float = 0f, weather: WeatherState = WeatherState.CLEAR_SKY, temp: Double = 15.0, now: Long = 1_000L) =
        Environment(lux, weather, temp, now)

    private fun player(vararg plants: PlantState) = PlayerState(plants = plants.toList(), activePlantId = plants.first().id, scenery = dev.mothlight.plantagotchi.data.state.Scenery.MOUNTAINS)

    @Test
    fun brightLightIncreasesLuxAndEarnsCoins() {
        val s = GameRules.tick(player(PlantState(lux = 50.0)), env(lux = 5_000f))
        assertEquals(50.1, s.activePlant.lux, 1e-9)
        assertEquals(1, s.coins)
    }

    @Test
    fun valuesStayWithinBounds() {
        val full = GameRules.tick(player(PlantState(lux = 100.0, water = 100.0)), env(lux = 5_000f, weather = WeatherState.THUNDERSTORM))
        assertEquals(100.0, full.activePlant.lux)
        assertEquals(100.0, full.activePlant.water)
        assertEquals(0, full.coins, "no coins when lux is already full")

        val empty = GameRules.tick(player(PlantState(lux = 0.0, water = 0.0, love = 0.0, fertilizer = 0.0)), env())
        val p = empty.activePlant
        assertEquals(0.0, p.lux)
        assertEquals(0.0, p.water)
        assertEquals(0.0, p.love)
        assertEquals(0.0, p.fertilizer)
    }

    @Test
    fun rainWatersAllPlants() {
        val s = GameRules.tick(player(PlantState(water = 10.0), PlantState(water = 20.0)), env(weather = WeatherState.HEAVY_RAIN))
        assertEquals(10.4, s.plants[0].water, 1e-9)
        assertEquals(20.4, s.plants[1].water, 1e-9)
    }

    @Test
    fun speciesDecayDifferently() {
        val day = 86_400.0
        val s = GameRules.tick(
            player(PlantState(species = Species.SPROUT), PlantState(species = Species.CACTUS), PlantState(species = Species.FERN)),
            env(),
            seconds = day / 2,
        )
        val (sprout, cactus, fern) = s.plants
        assertTrue(cactus.water > sprout.water, "cacti need less water")
        assertTrue(fern.water < sprout.water, "ferns drink more")
        assertTrue(fern.lux > sprout.lux, "ferns like shade")
    }

    @Test
    fun heatAndFrostSpeedUpDecay() {
        val mild = GameRules.tick(player(PlantState()), env(temp = 20.0), seconds = 3600.0).activePlant
        val hot = GameRules.tick(player(PlantState()), env(temp = 35.0), seconds = 3600.0).activePlant
        val frost = GameRules.tick(player(PlantState()), env(temp = -5.0), seconds = 3600.0).activePlant
        assertTrue(hot.water < mild.water)
        assertTrue(frost.lux < mild.lux)
        assertTrue(frost.love <= mild.love)
    }

    @Test
    fun healthyPlantsGrowThroughStages() {
        var p = player(PlantState(love = 100.0))
        assertEquals(0, p.activePlant.stage)
        p = GameRules.tick(p, env(lux = 5_000f, weather = WeatherState.RAIN), seconds = GameRules.STAGE_2_GROWTH)
        assertEquals(1, p.activePlant.stage)
        p = GameRules.tick(p, env(lux = 5_000f, weather = WeatherState.RAIN), seconds = GameRules.STAGE_3_GROWTH)
        assertEquals(2, p.activePlant.stage)
    }

    @Test
    fun neglectedPlantsWitherAfterADayInCriticalCondition() {
        val dying = PlantState(lux = 0.0, water = 0.0, love = 0.0, fertilizer = 5.0)
        var p = GameRules.tick(player(dying), env(now = 1_000L))
        assertTrue(p.activePlant.isCritical)
        assertFalse(p.activePlant.dead)
        p = GameRules.tick(p, env(now = 1_000L + GameRules.DEAD_AFTER_MILLIS))
        assertTrue(p.activePlant.dead)

        val frozen = GameRules.tick(p, env(lux = 5_000f, weather = WeatherState.RAIN, now = 1_000L + GameRules.DEAD_AFTER_MILLIS + 10))
        assertEquals(p.activePlant, frozen.activePlant, "withered plants no longer change")
        assertEquals(p.coins, frozen.coins)
    }

    @Test
    fun recoveringPlantsLeaveCriticalState() {
        val p = GameRules.tick(player(PlantState(lux = 0.0, water = 0.0, love = 0.0, fertilizer = 0.0, criticalSince = 5L)), env(lux = 5_000f, weather = WeatherState.THUNDERSTORM), seconds = 200.0)
        assertEquals(0L, p.activePlant.criticalSince)
    }

    @Test
    fun pettingRequiresARealSwipeAndALivingPlant() {
        val start = player(PlantState(love = 10.0))
        assertEquals(start, GameRules.pet(start, 5f, 1))
        val petted = GameRules.pet(start, -40f, 1)
        assertEquals(11.0, petted.activePlant.love)
        assertEquals(1, petted.coins)

        val dead = player(PlantState(dead = true))
        assertEquals(dead, GameRules.pet(dead, 100f, 1))
    }

    @Test
    fun offlineDecayAppliesElapsedTime() {
        val oneDay = 86_400_000L
        val s = GameRules.applyOfflineDecay(
            player(PlantState(lux = 100.0, water = 100.0)).copy(updatedAt = 1_000L),
            1_000L + oneDay,
        )
        assertEquals(0.0, s.activePlant.lux, 1e-6)
        assertEquals(50.0, s.activePlant.water, 1e-6)
        assertEquals(1_000L + oneDay, s.updatedAt)
    }

    @Test
    fun longAbsenceWithersPlants() {
        val tenDays = 10 * 86_400_000L
        val s = GameRules.applyOfflineDecay(player(PlantState()).copy(updatedAt = 1L), 1L + tenDays)
        assertTrue(s.activePlant.dead)
    }

    @Test
    fun wmoCodesMapToWeatherStates() {
        assertEquals(WeatherState.CLEAR_SKY, WeatherState.fromWmoCode(0))
        assertEquals(WeatherState.FOG, WeatherState.fromWmoCode(45))
        assertEquals(WeatherState.HEAVY_RAIN, WeatherState.fromWmoCode(65))
        assertEquals(WeatherState.SNOW, WeatherState.fromWmoCode(73))
        assertEquals(WeatherState.THUNDERSTORM, WeatherState.fromWmoCode(95))
    }

    @Test
    fun seasonsFlipOnTheSouthernHemisphere() {
        assertEquals(Season.WINTER, Season.of(1, 47.0))
        assertEquals(Season.SUMMER, Season.of(1, -33.0))
        assertEquals(Season.AUTUMN, Season.of(10, 47.0))
        assertEquals(Season.SPRING, Season.of(10, -33.0))
    }

    @Test
    fun pettingCoinsAreCappedPerDayAcrossPlants() {
        var p = player(PlantState(love = 0.0))
        repeat(GameRules.PET_COINS_PER_DAY + 20) { p = GameRules.pet(p, 40f, 7) }
        assertEquals(GameRules.PET_COINS_PER_DAY, p.coins)
        assertEquals(GameRules.PET_COINS_PER_DAY + 20.0, p.activePlant.love, "love still grows after the cap")
        // A fresh plant does not reset the cap (no farming by discarding and replanting)
        val fresh = dev.mothlight.plantagotchi.data.shop.Shop.discard(p, 0)
        assertEquals(p.coins, GameRules.pet(fresh, 40f, 7).coins)
        // The next day pays again
        assertEquals(p.coins + 1, GameRules.pet(fresh, 40f, 8).coins)
    }
}
