package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameRulesTest {

    @Test
    fun brightLightIncreasesLuxAndEarnsCoins() {
        val s = GameRules.tick(PlayerState(lux = 50.0), ambientLux = 5_000f, weather = WeatherState.CLEAR_SKY)
        assertEquals(50.1, s.lux, 1e-9)
        assertEquals(1, s.coins)
    }

    @Test
    fun valuesStayWithinBounds() {
        val full = GameRules.tick(PlayerState(lux = 100.0, water = 100.0), 5_000f, WeatherState.THUNDERSTORM)
        assertEquals(100.0, full.lux)
        assertEquals(100.0, full.water)
        assertEquals(0, full.coins, "no coins when lux is already full")

        val empty = GameRules.tick(PlayerState(lux = 0.0, water = 0.0, love = 0.0, fertilizer = 0.0), 0f, WeatherState.CLEAR_SKY)
        assertEquals(0.0, empty.lux)
        assertEquals(0.0, empty.water)
        assertEquals(0.0, empty.love)
        assertEquals(0.0, empty.fertilizer)
    }

    @Test
    fun rainWatersThePlant() {
        val s = GameRules.tick(PlayerState(water = 10.0), 0f, WeatherState.HEAVY_RAIN)
        assertEquals(10.4, s.water, 1e-9)
    }

    @Test
    fun pettingRequiresARealSwipe() {
        val start = PlayerState(love = 10.0)
        assertEquals(start, GameRules.pet(start, 5f))
        val petted = GameRules.pet(start, -40f)
        assertEquals(11.0, petted.love)
        assertEquals(1, petted.coins)
    }

    @Test
    fun offlineDecayAppliesElapsedTime() {
        val oneDay = 86_400_000L
        val s = GameRules.applyOfflineDecay(PlayerState(lux = 100.0, water = 100.0, updatedAt = 1_000L), 1_000L + oneDay)
        assertEquals(0.0, s.lux, 1e-6)
        assertEquals(50.0, s.water, 1e-6)
        assertTrue(s.love == 0.0)
        assertEquals(1_000L + oneDay, s.updatedAt)
    }

    @Test
    fun wmoCodesMapToWeatherStates() {
        assertEquals(WeatherState.CLEAR_SKY, WeatherState.fromWmoCode(0))
        assertEquals(WeatherState.FOG, WeatherState.fromWmoCode(45))
        assertEquals(WeatherState.HEAVY_RAIN, WeatherState.fromWmoCode(65))
        assertEquals(WeatherState.SNOW, WeatherState.fromWmoCode(73))
        assertEquals(WeatherState.THUNDERSTORM, WeatherState.fromWmoCode(95))
    }
}
