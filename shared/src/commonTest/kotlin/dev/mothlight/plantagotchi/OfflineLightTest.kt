package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.state.Daylight
import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.ReminderPlanner
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineLightTest {
    private val hour = 3_600_000L
    /** 20 June 2026 00:00 UTC; Brugg is at 8.2° E, so UTC is close to solar time. */
    private val midnight = 20_624L * 86_400_000L
    private val brugg = GeoPosition(47.48, 8.21)

    private fun player(plant: PlantState, scenery: Scenery = Scenery.MOUNTAINS, at: Long) =
        PlayerState(plants = listOf(plant), activePlantId = plant.id, scenery = scenery, lastPosition = brugg, updatedAt = at)

    @Test
    fun theSunIsUpByDayAndDownAtNight() {
        assertTrue(Daylight.isDay(midnight + 11 * hour, brugg))
        assertFalse(Daylight.isDay(midnight + 1 * hour, brugg))
        // Long summer days, short winter days
        assertTrue(Daylight.isDay(midnight + 18 * hour, brugg))
        assertFalse(Daylight.isDay(midnight + 183L * 86_400_000L + 18 * hour, brugg))
    }

    @Test
    fun daylightFillsTheLightWhileTheAppIsClosed() {
        val plant = PlantState(id = "a", lux = 30.0)
        val closedAt = midnight + 10 * hour
        val after = GameRules.applyOfflineDecay(player(plant, at = closedAt), closedAt + 2 * hour)
        assertEquals(100.0, after.plants[0].lux, "two hours of daylight fill it")
        assertEquals(0, after.coins, "no coins while closed")

        val night = midnight - 2 * hour
        val dark = GameRules.applyOfflineDecay(player(plant, at = night), night + 2 * hour)
        assertTrue(dark.plants[0].lux < 30.0, "at night it sinks")
    }

    @Test
    fun theLampWorksWhileTheAppIsClosedUntilItRunsOut() {
        val night = midnight - 3 * hour
        val lamp = PlantState(id = "a", lux = 10.0, lampUntil = night + 10 * 60_000L)
        val after = GameRules.applyOfflineDecay(player(lamp, at = night), night + hour)
        // 10 minutes of lamp: +60, then 50 minutes of night
        assertTrue(after.plants[0].lux in 65.0..70.0, "was ${after.plants[0].lux}")
    }

    @Test
    fun indoorsDaylightIsHalfAsEffectiveButTheLampIsNot() {
        fun gain(scenery: Scenery, lampUntil: Long = 0L): Double {
            val state = player(PlantState(id = "a", lux = 10.0, lampUntil = lampUntil), scenery, at = 0L)
            val env = Environment(5_000f, WeatherState.CLEAR_SKY, 15.0, 1_000L)
            return GameRules.tick(state, env, 100.0).plants[0].lux - 10.0
        }
        assertEquals(10.0, gain(Scenery.MOUNTAINS), 1e-6)
        assertEquals(5.0, gain(Scenery.ROOM), 1e-6)
        assertEquals(10.0, gain(Scenery.ROOM, lampUntil = 1_000_000L), 1e-6)
    }

    @Test
    fun aLitPlantFillsEverySecondOnline() {
        // The reported case: outdoors, lamp bought, light must rise by 0.1 per second also at night
        var state = player(PlantState(id = "a", lux = 20.0, lampUntil = 30 * 60_000L), at = 0L)
        repeat(300) { i -> state = GameRules.tick(state, Environment(0f, WeatherState.CLEAR_SKY, 15.0, i * 1000L), 1.0) }
        assertEquals(50.0, state.plants[0].lux, 1e-6)
    }

    @Test
    fun noLightReminderWhenTheDayRefillsIt() {
        val closedAt = midnight + 20 * hour
        val planner = ReminderPlanner { "" to "" }
        val fine = player(PlantState(id = "a", lux = 100.0), at = closedAt)
        assertTrue(planner.planDelays(fine, 0, closedAt, TimeZone.UTC).none { it.need == Need.LIGHT }, "a short night never empties it")

        val low = player(PlantState(id = "a", lux = 25.0), at = closedAt)
        val light = planner.planDelays(low, 0, closedAt, TimeZone.UTC).first { it.need == Need.LIGHT }
        assertTrue(light.delaySeconds in 3600L..6 * 3600L, "sinks to 20 during the night, was ${light.delaySeconds}")
    }
}
