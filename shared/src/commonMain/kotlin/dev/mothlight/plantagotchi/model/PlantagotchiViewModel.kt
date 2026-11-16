package dev.mothlight.plantagotchi.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.platform.PlatformServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class GameUiState(
    val loading: Boolean = true,
    val player: PlayerState = PlayerState(),
    val weather: CurrentWeather = CurrentWeather.Default,
    val position: GeoPosition = GeoPosition.Default,
    /** Light the plant currently receives, from the sensor or estimated from the weather. */
    val ambientLux: Float = 0f,
    val luxFromSensor: Boolean = false,
)

class PlantagotchiViewModel(
    private val platform: PlatformServices,
    private val repository: GameStateRepository,
    private val weatherClient: OpenMeteoClient,
    private val clock: Clock = Clock.System,
    private val gameTick: Duration = 1.seconds,
    private val dataInterval: Duration = 1.minutes,
    private val saveInterval: Duration = 15.seconds,
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            val loaded = runCatching { repository.load() }.getOrNull()
            val player = loaded?.let { GameRules.applyOfflineDecay(it, now) } ?: PlayerState(updatedAt = now)
            _state.update { it.copy(loading = false, player = player, position = player.lastPosition) }

            launch { dataLoop() }
            launch { gameLoop() }
            launch { saveLoop() }
        }
    }

    fun onStart() = platform.lightSensor.start()

    fun onStop() {
        platform.lightSensor.stop()
        viewModelScope.launch { save() }
    }

    /** Swiping over the plant pets it. */
    fun pet(dragY: Float) {
        if (_state.value.loading) return
        _state.update { it.copy(player = GameRules.pet(it.player, dragY)) }
    }

    private suspend fun gameLoop() {
        while (viewModelScope.isActive) {
            delay(gameTick)
            _state.update { s ->
                val sensorLux = platform.lightSensor.lux.value
                val lux = sensorLux ?: s.weather.estimatedLux()
                val player = GameRules.tick(s.player, lux, s.weather.facts.state, gameTick.inWholeMilliseconds / 1000.0)
                s.copy(
                    player = player.copy(updatedAt = clock.now().toEpochMilliseconds()),
                    ambientLux = lux,
                    luxFromSensor = sensorLux != null,
                )
            }
        }
    }

    private suspend fun dataLoop() {
        while (viewModelScope.isActive) {
            refreshWeather()
            delay(dataInterval)
        }
    }

    private suspend fun refreshWeather() {
        val position = runCatchingCancellable { platform.locationProvider.currentPosition() }
            ?: _state.value.position
        _state.update { it.copy(position = position, player = it.player.copy(lastPosition = position)) }
        runCatchingCancellable { weatherClient.currentWeather(position) }?.let { weather ->
            _state.update { it.copy(weather = weather) }
        }
    }

    private suspend fun saveLoop() {
        while (viewModelScope.isActive) {
            delay(saveInterval)
            save()
        }
    }

    private suspend fun save() {
        if (_state.value.loading) return
        runCatchingCancellable { repository.save(_state.value.player) }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}
