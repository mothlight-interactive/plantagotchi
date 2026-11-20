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
    /** String lights fill the light need at this fraction of a grow lamp's rate. */
    const val WEAK_LIGHT_FACTOR = 0.2
    /** Daylight in an indoor scene fills the light need at this fraction of the outdoor rate. A grow lamp is not affected. */
    const val INDOOR_LIGHT_FACTOR = 0.5
    /** Daylight assumed while the app is closed (no forecast): an average day. */
    const val OFFLINE_DAY_LUX = 5_000f
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

    /** Light [plant] actually receives, taking its grow lamp into account. */
    fun effectiveLux(plant: PlantState, ambientLux: Float, nowMillis: Long): Float =
        if (plant.lampOn(nowMillis)) maxOf(ambientLux, LAMP_LUX) else ambientLux

    /** One game loop step of [seconds] (normally 1) for all plants. */
    fun tick(state: PlayerState, env: Environment, seconds: Double = 1.0): PlayerState {
        var coins = state.coins
        // Indoors the rain stays outside: plants have to be watered by hand.
        val here = if (state.scenery.indoor) env.copy(weather = WeatherState.CLEAR_SKY, indoor = true) else env
        val weakLight = state.scenery == Scenery.PLANT_SHOP && state.stringLights
        val plants = state.plants.map { plant ->
            if (plant.dead) return@map plant
            val lit = plant.lampOn(env.nowMillis)
            val lux = effectiveLux(plant, env.ambientLux, env.nowMillis)
            if (lux > LUX_THRESHOLD && plant.lux < MAX) coins++
            tickPlant(plant, here.copy(ambientLux = lux), seconds, weakLight, lamp = lit)
        }
        return state.copy(plants = plants, coins = coins)
    }

    /** [weakLight]: a faint light source (string lights) that helps a little when it is dark. */
    /** [lamp]: the plant's grow lamp is on, so it fills at the full rate also indoors. */
    internal fun tickPlant(plant: PlantState, env: Environment, seconds: Double, weakLight: Boolean = false, lamp: Boolean = false): PlantState {
        if (plant.dead) return plant
        val s = plant.species
        val frost = if (env.temperature < FROST_TEMPERATURE) s.frostSensitivity else 1.0
        val heat = if (env.temperature > HEAT_TEMPERATURE) s.heatSensitivity else 1.0

        val lux = if (env.ambientLux > LUX_THRESHOLD) plant.lux + LUX_GAIN * (if (env.indoor && !lamp) INDOOR_LIGHT_FACTOR else 1.0) * seconds
        else if (weakLight) plant.lux + LUX_GAIN * WEAK_LIGHT_FACTOR * seconds
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

    /**
     * A plant is in danger when its health is very low, or when water, love and fertilizer are
     * nearly gone: light alone (which comes back every day) does not keep a plant alive.
     */
    fun isCritical(plant: PlantState): Boolean =
        plant.health < CRITICAL_HEALTH || plant.water + plant.love + plant.fertilizer < CRITICAL_HEALTH * 4

    private fun withCondition(plant: PlantState, nowMillis: Long): PlantState = when {
        !isCritical(plant) -> plant.copy(criticalSince = 0L)
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
     * What happened while the app was closed: plants get daylight by day (from the date and the
     * last known position, without a forecast) and their grow lamp until it runs out, dry out
     * without rain and may grow or wither. No coins are earned. Simulated in steps of at most
     * [OFFLINE_STEP_MILLIS].
     */
    fun applyOfflineDecay(state: PlayerState, nowMillis: Long): PlayerState {
        if (state.updatedAt <= 0L || nowMillis <= state.updatedAt) return state
        var time = state.updatedAt
        var plants = state.plants
        val end = min(nowMillis, state.updatedAt + MAX_OFFLINE_MILLIS)
        while (time < end) {
            val step = min(OFFLINE_STEP_MILLIS, end - time)
            val from = time
            time += step
            plants = plants.map { offlineStep(it, state, from, time) }
        }
        return state.copy(plants = plants, updatedAt = nowMillis)
    }

    /** One plant from [from] to [to] (ms) while the app is closed; splits the step where its lamp runs out. */
    internal fun offlineStep(plant: PlantState, state: PlayerState, from: Long, to: Long): PlantState {
        val indoor = state.scenery.indoor
        val weakLight = state.scenery == Scenery.PLANT_SHOP && state.stringLights
        val lampEnd = plant.lampUntil.coerceIn(from, to)
        var result = plant
        if (lampEnd > from) {
            val env = Environment(LAMP_LUX, WeatherState.CLEAR_SKY, OFFLINE_TEMPERATURE, lampEnd, indoor)
            result = tickPlant(result, env, (lampEnd - from) / 1000.0, weakLight, lamp = true)
        }
        if (to > lampEnd) {
            val day = Daylight.isDay((lampEnd + to) / 2, state.lastPosition)
            val env = Environment(if (day) OFFLINE_DAY_LUX else 0f, WeatherState.CLEAR_SKY, OFFLINE_TEMPERATURE, to, indoor)
            result = tickPlant(result, env, (to - lampEnd) / 1000.0, weakLight)
        }
        return result
    }

    /**
     * Seconds from [nowMillis] until the light of [plant] drops to [threshold] while the app is
     * closed, or null if that does not happen within [horizonSeconds].
     */
    fun secondsUntilDark(plant: PlantState, state: PlayerState, nowMillis: Long, threshold: Double, horizonSeconds: Long): Long? {
        if (plant.dead) return null
        var p = plant
        var time = nowMillis
        val end = nowMillis + horizonSeconds * 1000
        while (time < end) {
            if (p.lux <= threshold) return (time - nowMillis) / 1000
            val next = min(time + OFFLINE_STEP_MILLIS, end)
            p = offlineStep(p, state, time, next)
            time = next
        }
        return if (p.lux <= threshold) horizonSeconds else null
    }

    /** Seconds until [need] of [plant] decays to [threshold] under default conditions, or null. */
    fun secondsUntil(plant: PlantState, need: (PlantState) -> Double, decayPerSecond: Double, threshold: Double): Double? {
        val value = need(plant)
        if (plant.dead || value <= threshold || decayPerSecond <= 0) return null
        return (value - threshold) / decayPerSecond
    }

    private const val OFFLINE_STEP_MILLIS = 15 * 60 * 1000L
    private const val OFFLINE_TEMPERATURE = 15.0
    private const val MAX_OFFLINE_MILLIS = 60L * 24 * 3600 * 1000

    private fun Double.clampNeed() = coerceIn(0.0, MAX)
}
