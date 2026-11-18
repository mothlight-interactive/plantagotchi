package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.DailyReward
import dev.mothlight.plantagotchi.data.state.DayMark
import dev.mothlight.plantagotchi.data.state.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals

class RewardHistoryTest {
    @Test
    fun claimRecordsTheDay() {
        val (state, _) = DailyReward.claim(PlayerState(), 100)!!
        assertEquals(listOf(100L), state.rewardDays)
        val (next, _) = DailyReward.claim(state, 101)!!
        assertEquals(listOf(100L, 101L), next.rewardDays)
        assertEquals(2, next.streak)
    }

    @Test
    fun weekShowsClaimedMissedAndToday() {
        val state = PlayerState(rewardDays = listOf(95, 96, 98), lastRewardDay = 98, streak = 1)
        val marks = DailyReward.recentDays(state, today = 100).map { it.mark }
        // Days 94..100: before the first claim, claimed, claimed, missed, claimed, missed, open today
        assertEquals(
            listOf(DayMark.NONE, DayMark.CLAIMED, DayMark.CLAIMED, DayMark.MISSED, DayMark.CLAIMED, DayMark.MISSED, DayMark.OPEN),
            marks,
        )
        assertEquals(0, DailyReward.currentStreak(state, 100))
    }

    @Test
    fun olderSavesDeriveDaysFromTheStreak() {
        val state = PlayerState(lastRewardDay = 99, streak = 3)
        assertEquals(setOf(97L, 98L, 99L), DailyReward.claimedDays(state))
        assertEquals(3, DailyReward.currentStreak(state, 100))
    }

    @Test
    fun historyIsCapped() {
        var state = PlayerState()
        for (day in 1L..40L) state = DailyReward.claim(state, day)!!.first
        assertEquals(DailyReward.HISTORY_DAYS, state.rewardDays.size)
        assertEquals(40L, state.rewardDays.last())
    }
}

class DiscardTest {
    private fun plants() = listOf(
        dev.mothlight.plantagotchi.data.state.PlantState(id = "a"),
        dev.mothlight.plantagotchi.data.state.PlantState(id = "b"),
        dev.mothlight.plantagotchi.data.state.PlantState(id = "c"),
    )

    @Test
    fun discardsALivingPlantAndSelectsTheNeighbour() {
        val state = PlayerState(plants = plants(), activePlantId = "b")
        val after = dev.mothlight.plantagotchi.data.shop.Shop.discard(state, 0)
        assertEquals(listOf("a", "c"), after.plants.map { it.id })
        assertEquals("a", after.activePlant.id)
        val first = dev.mothlight.plantagotchi.data.shop.Shop.discard(state.copy(activePlantId = "a"), 0)
        assertEquals("b", first.activePlant.id)
    }

    @Test
    fun theOnlyPlantBecomesAFreshSprout() {
        val old = dev.mothlight.plantagotchi.data.state.PlantState(id = "x", species = dev.mothlight.plantagotchi.data.state.Species.BONSAI, growth = 1e6, pot = "pot_red")
        val after = dev.mothlight.plantagotchi.data.shop.Shop.discard(PlayerState(plants = listOf(old), activePlantId = "x"), 5)
        val plant = after.plants.single()
        assertEquals(dev.mothlight.plantagotchi.data.state.Species.SPROUT, plant.species)
        assertEquals("pot_red", plant.pot)
        assertEquals(0.0, plant.growth)
        assertEquals(plant.id, after.activePlantId)
        kotlin.test.assertNotEquals("x", plant.id)
    }
}
