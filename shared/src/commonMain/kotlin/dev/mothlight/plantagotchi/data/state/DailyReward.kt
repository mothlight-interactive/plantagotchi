package dev.mothlight.plantagotchi.data.state

import kotlin.math.min

/** The treasure chest gives coins once per day; consecutive days build a streak. */
object DailyReward {
    const val BASE = 20
    const val PER_STREAK_DAY = 10
    const val MAX_STREAK_BONUS_DAYS = 6
    const val HISTORY_DAYS = 31

    fun isAvailable(state: PlayerState, today: Long): Boolean = state.lastRewardDay != today

    fun nextStreak(state: PlayerState, today: Long): Int =
        if (state.lastRewardDay == today - 1) state.streak + 1 else 1

    fun amount(streak: Int): Int = BASE + PER_STREAK_DAY * min(streak - 1, MAX_STREAK_BONUS_DAYS)

    /** Returns the new state and the coins received, or null if already claimed today. */
    fun claim(state: PlayerState, today: Long): Pair<PlayerState, Int>? {
        if (!isAvailable(state, today)) return null
        val streak = nextStreak(state, today)
        val coins = amount(streak)
        val days = (state.rewardDays + today).distinct().sorted().takeLast(HISTORY_DAYS)
        return state.copy(coins = state.coins + coins, lastRewardDay = today, streak = streak, rewardDays = days) to coins
    }

    /** Streak that still counts today: it breaks once a whole day was missed. */
    fun currentStreak(state: PlayerState, today: Long): Int =
        if (state.lastRewardDay >= today - 1) state.streak else 0

    /** Claimed days; older saves only know the last streak, which is turned into days. */
    fun claimedDays(state: PlayerState): Set<Long> {
        val fromStreak = if (state.lastRewardDay >= 0 && state.streak > 0) {
            (state.lastRewardDay - state.streak + 1..state.lastRewardDay).toSet()
        } else {
            emptySet()
        }
        return state.rewardDays.toSet() + fromStreak
    }

    /** The last [count] days up to [today], oldest first. */
    fun recentDays(state: PlayerState, today: Long, count: Int = 7): List<RewardDay> {
        val claimed = claimedDays(state)
        val first = claimed.minOrNull()
        return (today - count + 1..today).map { day ->
            val mark = when {
                day in claimed -> DayMark.CLAIMED
                day == today -> DayMark.OPEN
                first == null || day < first -> DayMark.NONE
                else -> DayMark.MISSED
            }
            RewardDay(day, mark)
        }
    }
}

/** How a day looks in the chest dialog. */
enum class DayMark { CLAIMED, MISSED, OPEN, NONE }

data class RewardDay(val day: Long, val mark: DayMark)
