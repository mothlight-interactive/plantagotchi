package dev.mothlight.plantagotchi.model

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.platform.Reminder
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.app_name
import dev.mothlight.plantagotchi.resources.notif_frost
import dev.mothlight.plantagotchi.resources.notif_grown
import dev.mothlight.plantagotchi.resources.notif_heat
import dev.mothlight.plantagotchi.resources.notif_reward
import dev.mothlight.plantagotchi.resources.notif_reward_streak
import dev.mothlight.plantagotchi.resources.notif_visit_1
import dev.mothlight.plantagotchi.resources.notif_visit_2
import dev.mothlight.plantagotchi.resources.notif_visit_3
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

enum class NudgeKind { CHEST, VISIT, GROWN, FROST, HEAT }

/** A nudge before its text is resolved. [day] counts from today (0). */
data class PlannedNudge(val kind: NudgeKind, val day: Int, val delaySeconds: Long, val species: Species? = null, val streak: Int = 0)

/**
 * Friendly reasons to come back while the app is closed: the daily chest, a plant that has grown,
 * frost or heat, or just a hello from the plants. At most one per day, never at night, less often
 * the longer the player stays away and nothing at all after two weeks.
 */
class NudgePlanner(
    private val texts: suspend (PlannedNudge) -> String = ::defaultText,
    private val title: suspend () -> String = { getString(Res.string.app_name) },
) {
    suspend fun plan(
        state: PlayerState, today: Long, nowMillis: Long, timeZone: TimeZone,
        minTemperature: Double? = null, maxTemperature: Double? = null,
    ): List<Reminder> = planNudges(state, today, nowMillis, timeZone, minTemperature, maxTemperature).map { nudge ->
        Reminder(id = FIRST_ID + nudge.day, delaySeconds = nudge.delaySeconds, title = title(), body = texts(nudge))
    }

    /** Pure part: which nudge on which day, as delays from [nowMillis]. */
    fun planNudges(
        state: PlayerState, today: Long, nowMillis: Long, timeZone: TimeZone,
        minTemperature: Double? = null, maxTemperature: Double? = null,
    ): List<PlannedNudge> {
        fun delay(day: Int, time: LocalTime): Long =
            (LocalDate.fromEpochDays(today + day).atTime(time).toInstant(timeZone).toEpochMilliseconds() - nowMillis) / 1000
        val byDay = HashMap<Int, PlannedNudge>()
        fun put(kind: NudgeKind, day: Int, time: LocalTime, species: Species? = null) {
            val seconds = delay(day, time)
            if (seconds >= MIN_DELAY_SECONDS) byDay[day] = PlannedNudge(kind, day, seconds, species, state.streak)
        }

        // The base rhythm: chest and visits take turns and get rarer.
        if (state.lastRewardDay != today) put(NudgeKind.CHEST, 0, EVENING)
        CHEST_DAYS.forEach { put(NudgeKind.CHEST, it, MORNING) }
        VISIT_DAYS.forEach { put(NudgeKind.VISIT, it, AFTERNOON) }

        // Frost or heat (as far as the last forecast knows): tomorrow morning instead of the chest.
        val living = state.plants.filterNot { it.dead }
        if (living.isNotEmpty()) {
            if (minTemperature != null && minTemperature < GameRules.FROST_TEMPERATURE) put(NudgeKind.FROST, 1, EARLY)
            else if (maxTemperature != null && maxTemperature > GameRules.HEAT_TEMPERATURE) put(NudgeKind.HEAT, 1, EARLY)
        }

        // A healthy plant about to reach its next stage: on that day, at that time (within the day hours).
        living.filter { it.health >= GameRules.GROW_HEALTH }.mapNotNull { plant ->
            val target = when {
                plant.growth < GameRules.STAGE_2_GROWTH -> GameRules.STAGE_2_GROWTH
                plant.growth < GameRules.STAGE_3_GROWTH -> GameRules.STAGE_3_GROWTH
                else -> return@mapNotNull null
            }
            val seconds = ((target - plant.growth) / (0.5 + plant.fertilizer / GameRules.MAX)).toLong()
            if (seconds > GROWTH_HORIZON_SECONDS) null else plant.species to seconds
        }.minByOrNull { it.second }?.let { (species, seconds) ->
            val at = Instant.fromEpochMilliseconds(nowMillis + seconds * 1000).toLocalDateTime(timeZone)
            val day = (at.date.toEpochDays() - today).toInt()
            val time = when {
                at.time < EARLY -> EARLY
                at.time > LATEST -> LATEST
                else -> at.time
            }
            // Too late in the evening would mean tomorrow morning
            val shifted = if (at.time > LATEST) day + 1 else day
            put(NudgeKind.GROWN, shifted, if (at.time > LATEST) EARLY else time, species)
        }
        return byDay.values.sortedBy { it.delaySeconds }
    }

    companion object {
        /** Notification ids of nudges start here (needs use 1..4). */
        const val FIRST_ID = 20
        const val MIN_DELAY_SECONDS = 60 * 60L
        /** Growth is only predicted this far: later the plant may no longer be healthy. */
        const val GROWTH_HORIZON_SECONDS = 2 * 24 * 3600L
        val CHEST_DAYS = listOf(1, 3, 7)
        val VISIT_DAYS = listOf(2, 5, 10, 14)
        val EARLY = LocalTime(9, 0)
        val MORNING = LocalTime(10, 0)
        val AFTERNOON = LocalTime(17, 30)
        val EVENING = LocalTime(18, 30)
        val LATEST = LocalTime(20, 0)

        private suspend fun defaultText(n: PlannedNudge): String = when (n.kind) {
            NudgeKind.CHEST ->
                if (n.streak >= 2 && n.day <= 1) getString(Res.string.notif_reward_streak, n.streak)
                else getString(Res.string.notif_reward)
            NudgeKind.VISIT -> getString(
                when (n.day % 3) {
                    0 -> Res.string.notif_visit_1
                    1 -> Res.string.notif_visit_2
                    else -> Res.string.notif_visit_3
                }
            )
            NudgeKind.GROWN -> getString(Res.string.notif_grown, ReminderPlanner.speciesName(n.species ?: Species.SPROUT))
            NudgeKind.FROST -> getString(Res.string.notif_frost)
            NudgeKind.HEAT -> getString(Res.string.notif_heat)
        }
    }
}
