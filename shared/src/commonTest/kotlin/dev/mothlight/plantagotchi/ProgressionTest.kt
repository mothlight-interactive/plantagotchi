package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.state.DailyReward
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.PlayerStateCodec
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.model.ReminderPlanner
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionTest {

    @Test
    fun dailyRewardBuildsAStreak() {
        val (day1, coins1) = assertNotNull(DailyReward.claim(PlayerState(), today = 100))
        assertEquals(DailyReward.BASE, coins1)
        assertNull(DailyReward.claim(day1, today = 100), "only once per day")

        val (day2, coins2) = assertNotNull(DailyReward.claim(day1, today = 101))
        assertEquals(2, day2.streak)
        assertEquals(DailyReward.BASE + DailyReward.PER_STREAK_DAY, coins2)

        val (afterBreak, _) = assertNotNull(DailyReward.claim(day2, today = 105))
        assertEquals(1, afterBreak.streak)
        assertEquals(DailyReward.BASE + DailyReward.PER_STREAK_DAY * DailyReward.MAX_STREAK_BONUS_DAYS, DailyReward.amount(30))
    }

    @Test
    fun singlePlantSavesAreMigrated() {
        val legacy = """{"lux":12.5,"water":34.0,"love":56.0,"fertilizer":78.0,"coins":42,"selectedPot":"pot33",
            "ownedPots":["pot12","pot33"],"lastPosition":{"latitude":1.0,"longitude":2.0,"altitude":3.0},"updatedAt":99}"""
        val state = PlayerStateCodec.decode(legacy)
        assertEquals(1, state.plants.size)
        assertEquals(12.5, state.activePlant.lux)
        assertEquals(78.0, state.activePlant.fertilizer)
        assertEquals("pot33", state.activePlant.pot)
        assertEquals(42, state.coins)
        assertEquals(99, state.updatedAt)
    }

    @Test
    fun codecRoundTrip() {
        val state = PlayerState(
            plants = listOf(PlantState(species = Species.FERN, growth = 123.0), PlantState(species = Species.CACTUS, dead = true)),
            coins = 7,
            streak = 3,
        )
        assertEquals(state, PlayerStateCodec.decode(PlayerStateCodec.encode(state)))
    }

    @Test
    fun remindersFireWhenNeedsGetLow() {
        val plant = PlantState(water = 20.0 + 100.0 / (86_400.0 * 2) * 3600, lux = 100.0, love = 10.0, fertilizer = 100.0)
        val state = PlayerState(plants = listOf(plant, PlantState(dead = true, water = 0.0)), activePlantId = plant.id)
        val planned = ReminderPlanner { "" to "" }.planDelays(state, today = 1, nowMillis = 0, timeZone = TimeZone.UTC)
        val water = planned.first { it.need == Need.WATER }
        assertTrue(water.delaySeconds in 3599L..3600L, "one hour until 20 % water")
        assertEquals(ReminderPlanner.MIN_DELAY_SECONDS, planned.first { it.need == Need.LOVE }.delaySeconds, "already low: soon")
        assertTrue(planned.none { it.need == null }, "no chest reminder before claiming today")

        val claimed = state.copy(lastRewardDay = 1)
        val chest = ReminderPlanner { "" to "" }.planDelays(claimed, today = 1, nowMillis = 0, timeZone = TimeZone.UTC).first { it.need == null }
        assertEquals((86_400L * 2) + 10 * 3600L, chest.delaySeconds, "next morning 10:00")
    }
}
