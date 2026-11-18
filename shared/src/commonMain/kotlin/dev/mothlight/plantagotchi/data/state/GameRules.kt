package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure game mechanics, independent of UI and platform.
 *
 * Base decay per second (scaled per [Species] and temperature):
 * - LUX: empty after one day without light
 * - LOVE: after one week without petting
 * - FERTILIZER: after two weeks
 * - WATER: after two days
 */
object GameRules {
    const val MAX = 100.0
    const val LUX_DECAY = MAX / 86_400.0
    const val LOVE_DECAY = MAX / (86_400.0 * 7)
    const val FERTILIZER_DECAY = MAX / (86_400.0 * 14)
    const val WATER_DECAY = MAX / (86_400.0 * 2)

    /** Ambient light (lux) above which plants get enough sun. */
    const val LUX_THRESHOLD = 1_000f
    /** Light a grow lamp provides. */
    const val LAMP_LUX = 5_000f
    const val LUX_GAIN = 0.1
    const val LOVE_GAIN = 1.0

    /**
     * Coins from petting per day, across all plants. Without the cap, discarding a plant (or buying
     * one) and petting the fresh seedling to full love would farm coins without limit.
     */
    const val PET_COINS_PER_DAY = 30
    const val MIN_PET_DRAG_PX = 25f

    const val HEAT_TEMPERATURE = 30.0
    const val FROST_TEMPERATURE = 0.0

    /** Plants grow while their health is at least this. */
    const val GROW_HEALTH = 60.0
    const val STAGE_2_GROWTH = 6 * 3600.0
    const val STAGE_3_GROWTH = 48 * 3600.0

    /** Below this health a plant is critical; after [DEAD_AFTER_MILLIS] critical it withers. */
    const val CRITICAL_HEALTH = 10.0
    const val DEAD_AFTER_MILLIS = 24 * 3600 * 1000L

    fun waterGain(weather: WeatherState): Double = when (weather) {
        WeatherState.LIGHT_RAIN -> 0.1
        WeatherState.RAIN -> 0.2
        WeatherState.HEAVY_RAIN -> 0.4
        WeatherState.THUNDERSTORM -> 0.8
        else -> 0.0
    }

    /** Light the plants actually receive, taking an active grow lamp into account. */
    fun effectiveLux(state: PlayerState, ambientLux: Float, nowMillis: Long): Float =
        if (state.isLampActive(nowMillis)) maxOf(ambientLux, LAMP_LUX) else ambientLux

    /** One game loop step of [seconds] (normally 1) for all plants. */
    fun tick(state: PlayerState, env: Environment, seconds: Double = 1.0): PlayerState {
        var coins = state.coins
        val plants = state.plants.map { plant ->
            if (plant.dead) return@map plant
            if (env.ambientLux > LUX_THRESHOLD && plant.lux < MAX) coins++
            tickPlant(plant, env, seconds)
        }
        return state.copy(plants = plants, coins = coins)
    }

    internal fun tickPlant(plant: PlantState, env: Environment, seconds: Double): PlantState {
        if (plant.dead) return plant
        val s = plant.species
        val frost = if (env.temperature < FROST_TEMPERATURE) s.frostSensitivity else 1.0
        val heat = if (env.temperature > HEAT_TEMPERATURE) s.heatSensitivity else 1.0

        val lux = if (env.ambientLux > LUX_THRESHOLD) plant.lux + LUX_GAIN * seconds
        else plant.lux - LUX_DECAY * s.luxDecay * frost * seconds

        val rain = waterGain(env.weather)
        val water = if (rain > 0) plant.water + rain * seconds
        else plant.water - WATER_DECAY * s.waterDecay * heat * frost * seconds

        val next = plant.copy(
            lux = lux.clampNeed(),
            water = water.clampNeed(),
            love = (plant.love - LOVE_DECAY * s.loveDecay * frost * seconds).clampNeed(),
            fertilizer = (plant.fertilizer - FERTILIZER_DECAY * s.fertilizerDecay * frost * seconds).clampNeed(),
        )
        val growth = if (next.health >= GROW_HEALTH) {
            next.growth + seconds * (0.5 + next.fertilizer / MAX)
        } else {
            next.growth
        }
        return withCondition(next.copy(growth = growth), env.nowMillis)
    }

    private fun withCondition(plant: PlantState, nowMillis: Long): PlantState = when {
        plant.health >= CRITICAL_HEALTH -> plant.copy(criticalSince = 0L)
        plant.criticalSince == 0L -> plant.copy(criticalSince = nowMillis)
        nowMillis - plant.criticalSince >= DEAD_AFTER_MILLIS -> plant.copy(dead = true)
        else -> plant
    }

    /** Petting the active plant with a vertical swipe: more love, and a coin while today's cap allows. */
    fun pet(state: PlayerState, dragY: Float, today: Long): PlayerState {
        val plant = state.activePlant
        if (abs(dragY) <= MIN_PET_DRAG_PX || plant.dead || plant.love >= MAX) return state
        val earned = if (state.petCoinsDay == today) state.petCoinsToday else 0
        val loved = state.updateActive { it.copy(love = (it.love + LOVE_GAIN).clampNeed()) }
        if (earned >= PET_COINS_PER_DAY) return loved.copy(petCoinsDay = today, petCoinsToday = earned)
        return loved.copy(coins = state.coins + 1, petCoinsDay = today, petCoinsToday = earned + 1)
    }

    /**
     * What happened while the app was closed: plants decay without light or rain and may
     * grow or wither. Simulated in steps of at most one hour.
     */
    fun applyOfflineDecay(state: PlayerState, nowMillis: Long): PlayerState {
        if (state.updatedAt <= 0L || nowMillis <= state.updatedAt) return state
        var time = state.updatedAt
        var plants = state.plants
        val end = min(nowMillis, state.updatedAt + MAX_OFFLINE_MILLIS)
        while (time < end) {
            val step = min(OFFLINE_STEP_MILLIS, end - time)
            time += step
            val env = Environment.offline(time)
            plants = plants.map { tickPlant(it, env, step / 1000.0) }
        }
        return state.copy(plants = plants, updatedAt = nowMillis)
    }

    /** Seconds until [need] of [plant] decays to [threshold] under default conditions, or null. */
    fun secondsUntil(plant: PlantState, need: (PlantState) -> Double, decayPerSecond: Double, threshold: Double): Double? {
        val value = need(plant)
        if (plant.dead || value <= threshold || decayPerSecond <= 0) return null
        return (value - threshold) / decayPerSecond
    }

    private const val OFFLINE_STEP_MILLIS = 3600 * 1000L
    private const val MAX_OFFLINE_MILLIS = 60L * 24 * 3600 * 1000

    private fun Double.clampNeed() = coerceIn(0.0, MAX)
}
