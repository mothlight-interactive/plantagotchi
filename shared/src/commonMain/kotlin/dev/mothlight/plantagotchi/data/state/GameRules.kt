package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.math.abs

/**
 * Pure game mechanics, independent of UI and platform.
 *
 * Decay per second:
 * - LUX: plant starts to suffer after one day without light
 * - LOVE: after one week without petting
 * - FERTILIZER: after two weeks without fertilizer
 * - WATER: after two days without water
 */
object GameRules {
    const val MAX = 100.0
    const val LUX_DECAY = MAX / 86_400.0
    const val LOVE_DECAY = MAX / (86_400.0 * 7)
    const val FERTILIZER_DECAY = MAX / (86_400.0 * 14)
    const val WATER_DECAY = MAX / (86_400.0 * 2)

    /** Ambient light (lux) above which the plant is considered to get enough sun. */
    const val LUX_THRESHOLD = 1_000f
    /** Light a grow lamp provides. */
    const val LAMP_LUX = 5_000f
    const val LUX_GAIN = 0.1
    const val LOVE_GAIN = 1.0
    const val MIN_PET_DRAG_PX = 25f

    fun waterGain(weather: WeatherState): Double = when (weather) {
        WeatherState.LIGHT_RAIN -> 0.1
        WeatherState.RAIN -> 0.2
        WeatherState.HEAVY_RAIN -> 0.4
        WeatherState.THUNDERSTORM -> 0.8
        else -> 0.0
    }

    /** Light the plant actually receives, taking an active grow lamp into account. */
    fun effectiveLux(state: PlayerState, ambientLux: Float, nowMillis: Long): Float =
        if (state.isLampActive(nowMillis)) maxOf(ambientLux, LAMP_LUX) else ambientLux

    /** One game loop step of [seconds] (normally 1). */
    fun tick(state: PlayerState, ambientLux: Float, weather: WeatherState, seconds: Double = 1.0): PlayerState {
        var coins = state.coins

        val lux = if (ambientLux > LUX_THRESHOLD) {
            if (state.lux < MAX) coins++
            state.lux + LUX_GAIN * seconds
        } else {
            state.lux - LUX_DECAY * seconds
        }

        val gain = waterGain(weather)
        val water = if (gain > 0) state.water + gain * seconds else state.water - WATER_DECAY * seconds

        return state.copy(
            lux = lux.clampNeed(),
            water = water.clampNeed(),
            love = (state.love - LOVE_DECAY * seconds).clampNeed(),
            fertilizer = (state.fertilizer - FERTILIZER_DECAY * seconds).clampNeed(),
            coins = coins,
        )
    }

    /** Petting the plant with a vertical swipe. */
    fun pet(state: PlayerState, dragY: Float): PlayerState {
        if (abs(dragY) <= MIN_PET_DRAG_PX || state.love >= MAX) return state
        return state.copy(love = (state.love + LOVE_GAIN).clampNeed(), coins = state.coins + 1)
    }

    /** Decay that happened while the app was closed. No gains are applied. */
    fun applyOfflineDecay(state: PlayerState, nowMillis: Long): PlayerState {
        if (state.updatedAt <= 0L || nowMillis <= state.updatedAt) return state
        val seconds = (nowMillis - state.updatedAt) / 1000.0
        return state.copy(
            lux = (state.lux - LUX_DECAY * seconds).clampNeed(),
            water = (state.water - WATER_DECAY * seconds).clampNeed(),
            love = (state.love - LOVE_DECAY * seconds).clampNeed(),
            fertilizer = (state.fertilizer - FERTILIZER_DECAY * seconds).clampNeed(),
            updatedAt = nowMillis,
        )
    }

    private fun Double.clampNeed() = coerceIn(0.0, MAX)
}
