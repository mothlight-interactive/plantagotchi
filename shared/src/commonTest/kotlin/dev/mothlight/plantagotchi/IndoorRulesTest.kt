package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndoorRulesTest {
    private fun player(scenery: Scenery, lights: Boolean = true) = PlayerState(
        plants = listOf(PlantState(id = "a", water = 50.0, lux = 50.0)), activePlantId = "a",
        scenery = scenery, stringLights = lights,
    )

    private fun rainyNight() = Environment(0f, WeatherState.HEAVY_RAIN, 15.0, 1_000L)

    @Test
    fun rainWatersOnlyOutdoors() {
        val outside = GameRules.tick(player(Scenery.MOUNTAINS), rainyNight(), 60.0)
        val inside = GameRules.tick(player(Scenery.ROOM), rainyNight(), 60.0)
        assertTrue(outside.plants[0].water > 50.0, "rain waters plants outdoors")
        assertTrue(inside.plants[0].water < 50.0, "indoors the plant dries and has to be watered")
    }

    @Test
    fun stringLightsAreAWeakLampInThePlantShop() {
        val seconds = 100.0
        val on = GameRules.tick(player(Scenery.PLANT_SHOP), rainyNight(), seconds).plants[0].lux
        val off = GameRules.tick(player(Scenery.PLANT_SHOP, lights = false), rainyNight(), seconds).plants[0].lux
        val elsewhere = GameRules.tick(player(Scenery.ROOM), rainyNight(), seconds).plants[0].lux
        assertEquals(50.0 + GameRules.LUX_GAIN * seconds / 5, on, 1e-6)
        assertTrue(off < 50.0)
        assertTrue(elsewhere < 50.0, "the lights exist only in the plant shop")
        // No coins from the string lights
        assertEquals(0, GameRules.tick(player(Scenery.PLANT_SHOP), rainyNight(), seconds).coins)
    }
}
