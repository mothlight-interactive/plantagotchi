package dev.mothlight.plantagotchi.data.state

import kotlin.math.min

/** The treasure chest gives coins once per day; consecutive days build a streak. */
object DailyReward {
    const val BASE = 20
    const val PER_STREAK_DAY = 10
    const val MAX_STREAK_BONUS_DAYS = 6

    fun isAvailable(state: PlayerState, today: Long): Boolean = state.lastRewardDay != today

    fun nextStreak(state: PlayerState, today: Long): Int =
        if (state.lastRewardDay == today - 1) state.streak + 1 else 1

    fun amount(streak: Int): Int = BASE + PER_STREAK_DAY * min(streak - 1, MAX_STREAK_BONUS_DAYS)

    /** Returns the new state and the coins received, or null if already claimed today. */
    fun claim(state: PlayerState, today: Long): Pair<PlayerState, Int>? {
        if (!isAvailable(state, today)) return null
        val streak = nextStreak(state, today)
        val coins = amount(streak)
        return state.copy(coins = state.coins + coins, lastRewardDay = today, streak = streak) to coins
    }
}
