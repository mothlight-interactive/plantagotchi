package dev.mothlight.plantagotchi.audio

import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.platform.Sfx
import dev.mothlight.plantagotchi.platform.SoundPlayer
import dev.mothlight.plantagotchi.platform.play
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * Keeps music and ambience in sync with the game: fades loops towards [AudioMix.targets],
 * rolls thunder during storms and lets unhappy plants complain from time to time.
 */
class AudioDirector(
    private val player: SoundPlayer,
    private val random: Random = Random.Default,
) {
    private val current = HashMap<String, Float>()
    private var targets: Map<String, Float> = emptyMap()
    private var job: Job? = null
    private var secondsToThunder = 6
    private var secondsToComplaint = 20

    /** Starts fading; call when the app comes to the foreground. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                step(FADE_STEP_SECONDS)
                delay((FADE_STEP_SECONDS * 1000).toLong())
            }
        }
    }

    /** Silences everything immediately; call when the app goes to the background. */
    fun stop() {
        job?.cancel()
        job = null
        current.keys.toList().forEach { current[it] = 0f }
        player.pauseAll()
    }

    /** Called once per game tick (about every second). */
    fun update(state: GameUiState) {
        targets = AudioMix.targets(state)
        if (state.loading) return

        if (state.settings.ambience && state.weather.facts.state == WeatherState.THUNDERSTORM && --secondsToThunder <= 0) {
            player.play(Sfx.THUNDER, 0.6f + random.nextFloat() * 0.4f)
            secondsToThunder = 6 + random.nextInt(10)
        }
        val plant = state.player.activePlant
        if (state.settings.sound && AudioMix.plantIsUnhappy(plant) && --secondsToComplaint <= 0) {
            player.play(AudioAssets.voice(plant.species, happy = false), 0.7f)
            secondsToComplaint = 25 + random.nextInt(20)
        }
    }

    internal fun step(seconds: Float) {
        val keys = (targets.keys + current.keys).toSet()
        keys.forEach { asset ->
            val target = targets[asset] ?: 0f
            val now = current[asset] ?: 0f
            if (abs(now - target) < 0.001f) return@forEach
            val delta = FADE_PER_SECOND * seconds
            val next = if (now < target) minOf(target, now + delta) else maxOf(target, now - delta)
            current[asset] = next
            player.setLoop(asset, next)
        }
    }

    internal fun volume(asset: String) = current[asset] ?: 0f

    private companion object {
        const val FADE_STEP_SECONDS = 0.1f
        /** Volume change per second: full fade in about 2.5 s. */
        const val FADE_PER_SECOND = 0.3f
    }
}
