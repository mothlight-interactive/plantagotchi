package dev.mothlight.plantagotchi.ui.game

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameUiState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Little things the plant does by itself from time to time. */
enum class PlantAction(val duration: Float) {
    WIGGLE(1.2f),
    STRETCH(2.0f),
    LOOK(3.0f),
    SHAKE(1.0f),
    DANCE(2.4f),
    NOD(2.6f),

    /** Flytrap: jaws shut, then slowly open again. */
    SNAP(2.4f),
}

/**
 * Picks idle actions, reacts to events and turns the plant's condition into a [PlantPose].
 * Also animates growth: the shown maturity follows the real one, so growth that happened while
 * the app was closed plays back visibly.
 */
internal class PlantAnimator(private val random: Random = Random.Default) {
    private var action: PlantAction? = null
    private var actionAt = 0f
    private var nextActionAt = 3f
    private val shownMaturity = HashMap<String, Float>()
    private var lastTime = 0f
    private var daylight = -1f

    /** Lets the plant do [next] in [delay] seconds (or now). */
    fun trigger(next: PlantAction, time: Float, delay: Float = 0f) {
        action = next
        actionAt = time + delay
        nextActionAt = actionAt + next.duration + 4f + random.nextFloat() * 4f
    }

    fun maturity(plant: PlantState, time: Float): Float {
        val target = maturityOf(plant)
        val dt = (time - lastTime).coerceIn(0f, 0.1f)
        val shown = shownMaturity[plant.id]?.let { it + (target - it) * min(1f, dt * 0.9f) } ?: target
        shownMaturity[plant.id] = shown
        return shown
    }

    fun pose(state: GameUiState, plant: PlantState, time: Float, sunIsRight: Boolean, petBounce: Float, growBounce: Float): PlantPose {
        if (!plant.dead && time >= nextActionAt) trigger(pick(state, plant), time)
        val dt = (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        val targetDaylight = if (state.weather.isDay) 1f else 0f
        daylight = if (daylight < 0) targetDaylight else daylight + (targetDaylight - daylight) * min(1f, dt * 0.8f)
        var snap = 0f

        val wind = (state.weather.facts.windSpeed.toFloat() / 40f).coerceIn(0f, 1f)
        val droop = if (plant.dead) 1f else ((WILT_START - plant.health.toFloat()) / WILT_START).coerceIn(0f, 1f)
        val alive = 1f - droop * 0.7f

        var sway = (sin(time * 1.7f) * (0.6f + wind * 2.4f) + sin(time * 0.6f) * wind * 1.2f) * alive
        var lean = 0f
        // Thirsty plants let their leaves hang.
        var flap = ((plant.water.toFloat() - 50f) / 50f).coerceIn(-1f, 1f) * 0.8f
        var stretch = 0f
        var squash = petBounce * 0.15f + growBounce * 0.2f
        var flutter = (0.25f + wind * 0.6f) * alive

        val current = action
        val p = if (current == null) -1f else (time - actionAt) / current.duration
        if (current != null && p in 0f..1f && !plant.dead) {
            val pi = PI.toFloat()
            val envelope = sin(p * pi)
            when (current) {
                PlantAction.WIGGLE -> flap += sin(p * pi * 8) * 1.6f * (1 - p)
                PlantAction.STRETCH -> {
                    stretch += envelope * 2.5f
                    flap += envelope * 1.2f
                }
                PlantAction.LOOK -> lean += envelope * 3f * (if (sunIsRight) 1 else -1)
                PlantAction.SHAKE -> {
                    lean += sin(p * pi * 10) * 1.6f * (1 - p)
                    flutter += 1f * (1 - p)
                }
                PlantAction.DANCE -> {
                    lean += sin(p * pi * 4) * 2.2f
                    squash += abs(sin(p * pi * 4)) * 0.08f
                    flap += sin(p * pi * 8) * 0.8f
                }
                PlantAction.NOD -> {
                    flap -= envelope * 1.5f
                    lean += envelope * 1f
                }
                PlantAction.SNAP -> snap = when {
                    p < 0.04f -> p / 0.04f
                    p < 0.55f -> 1f
                    else -> 1f - (p - 0.55f) / 0.45f
                }
            }
        }
        if (plant.dead) {
            sway *= 0.2f
            flutter = 0f
        }
        val snapping = current == PlantAction.SNAP && p in 0f..0.9f
        return PlantPose(
            sway = sway, lean = lean, flap = flap, stretch = stretch,
            squash = squash, droop = droop, flutter = flutter, time = time,
            sunDirection = if (sunIsRight) 1f else -1f,
            daylight = daylight,
            snap = snap,
            fly = plant.species == dev.mothlight.plantagotchi.data.state.Species.FLYTRAP && !plant.dead && !snapping && daylight > 0.5f,
        )
    }

    private fun pick(state: GameUiState, plant: PlantState): PlantAction {
        val raining = GameRules.waterGain(state.weather.facts.state) > 0 || state.weather.facts.state == WeatherState.SNOW
        val options = buildList {
            add(PlantAction.WIGGLE)
            add(PlantAction.STRETCH)
            if (state.weather.isDay) add(PlantAction.LOOK) else repeat(2) { add(PlantAction.NOD) }
            if (raining) repeat(2) { add(PlantAction.SHAKE) }
            if (plant.love > 60) add(PlantAction.DANCE)
            when (plant.species) {
                dev.mothlight.plantagotchi.data.state.Species.SUNFLOWER -> if (state.weather.isDay) repeat(2) { add(PlantAction.LOOK) }
                dev.mothlight.plantagotchi.data.state.Species.FLYTRAP -> repeat(3) { add(PlantAction.SNAP) }
                dev.mothlight.plantagotchi.data.state.Species.BONSAI -> remove(PlantAction.WIGGLE)
                else -> Unit
            }
        }
        return options[random.nextInt(options.size)]
    }

    companion object {
        private const val WILT_START = 35f

        fun maturityOf(plant: PlantState): Float =
            (plant.growth / GameRules.STAGE_3_GROWTH).toFloat().coerceIn(0f, 1f)
    }
}
