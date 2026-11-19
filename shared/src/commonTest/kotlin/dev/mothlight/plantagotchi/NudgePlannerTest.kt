package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.model.NudgeKind
import dev.mothlight.plantagotchi.model.NudgePlanner
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgePlannerTest {
    private val planner = NudgePlanner(texts = { it.kind.name }, title = { "t" })
    private val day = 86_400L
    private val today = 100L
    /** 14:00 UTC on [today]. */
    private val now = (today * day + 14 * 3600) * 1000

    /** A grown, healthy plant: no growth event. */
    private fun player(claimed: Boolean = true, plant: PlantState = PlantState(id = "a", growth = GameRules.STAGE_3_GROWTH, love = 100.0)) =
        PlayerState(plants = listOf(plant), activePlantId = plant.id, lastRewardDay = if (claimed) today else today - 1, streak = 4)

    private fun plan(state: PlayerState, min: Double? = null, max: Double? = null) =
        planner.planNudges(state, today, now, TimeZone.UTC, min, max)

    @Test
    fun atMostOnePerDayNeverAtNightAndSilentAfterTwoWeeks() {
        val nudges = plan(player(claimed = false), min = -5.0)
        assertEquals(nudges.size, nudges.map { it.day }.distinct().size, "one per day")
        nudges.forEach { n ->
            val hour = ((now / 1000 + n.delaySeconds) % day) / 3600
            assertTrue(hour in 9..20, "day hours only, was $hour for ${n.kind}")
            assertTrue(n.day <= 14)
        }
        assertTrue(nudges.size <= 9)
    }

    @Test
    fun chestAndVisitsTakeTurnsAndGetRarer() {
        val nudges = plan(player())
        assertEquals(listOf(1, 2, 3, 5, 7, 10, 14), nudges.map { it.day })
        assertEquals(NudgeKind.CHEST, nudges[0].kind)
        assertEquals(20 * 3600L, nudges[0].delaySeconds, "tomorrow 10:00")
        assertEquals(NudgeKind.VISIT, nudges[1].kind)
        assertNull(nudges.firstOrNull { it.day == 0 }, "nothing today once the chest is collected")
    }

    @Test
    fun anUnclaimedChestIsMentionedThisEvening() {
        val first = plan(player(claimed = false)).first()
        assertEquals(NudgeKind.CHEST, first.kind)
        assertEquals(0, first.day)
        assertEquals(4 * 3600L + 1800, first.delaySeconds, "today 18:30")
    }

    @Test
    fun frostOrHeatReplacesTomorrowsChest() {
        assertEquals(NudgeKind.FROST, plan(player(), min = -2.0, max = 4.0).first { it.day == 1 }.kind)
        assertEquals(NudgeKind.HEAT, plan(player(), min = 18.0, max = 34.0).first { it.day == 1 }.kind)
        assertEquals(NudgeKind.CHEST, plan(player(), min = 5.0, max = 20.0).first { it.day == 1 }.kind)
    }

    @Test
    fun aPlantAboutToGrowIsAnnouncedOnThatDay() {
        // Healthy, 3 hours of growth at full fertilizer (rate 1.5/s) before stage 2
        val plant = PlantState(id = "a", growth = GameRules.STAGE_2_GROWTH - 3 * 3600 * 1.5, love = 100.0)
        val grown = plan(player(plant = plant)).first { it.kind == NudgeKind.GROWN }
        assertEquals(0, grown.day)
        assertEquals(3 * 3600L, grown.delaySeconds)

        // An unhealthy plant does not grow: no announcement
        val weak = plant.copy(water = 0.0, lux = 0.0, love = 0.0)
        assertTrue(plan(player(plant = weak)).none { it.kind == NudgeKind.GROWN })
    }
}
