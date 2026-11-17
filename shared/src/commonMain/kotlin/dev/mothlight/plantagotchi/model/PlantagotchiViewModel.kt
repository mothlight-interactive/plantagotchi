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
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Epoch millis of the last game tick. */
    val now: Long = 0L,
) {
    val lampActive: Boolean get() = player.isLampActive(now)
}

/** One-off events the scene turns into animations. */
sealed interface GameEvent {
    data class Purchased(val item: ShopItem) : GameEvent
    data object Petted : GameEvent
}

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

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            val loaded = runCatching { repository.load() }.getOrNull()
            val player = loaded?.let { GameRules.applyOfflineDecay(it, now) } ?: PlayerState(updatedAt = now)
            _state.update { it.copy(loading = false, player = player, position = player.lastPosition, now = now) }

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
        val before = _state.value.player
        val after = GameRules.pet(before, dragY)
        if (after != before) {
            _state.update { it.copy(player = GameRules.pet(it.player, dragY)) }
            _events.tryEmit(GameEvent.Petted)
        }
    }

    fun buy(item: ShopItem): PurchaseResult {
        if (_state.value.loading) return PurchaseResult.NotEnoughCoins
        val result = Shop.purchase(_state.value.player, item, clock.now().toEpochMilliseconds())
        if (result is PurchaseResult.Success) {
            _state.update { it.copy(player = result.state) }
            _events.tryEmit(GameEvent.Purchased(item))
            viewModelScope.launch { save() }
        }
        return result
    }

    fun selectPot(potId: String) {
        _state.update { it.copy(player = Shop.selectPot(it.player, potId)) }
    }

    private suspend fun gameLoop() {
        while (viewModelScope.isActive) {
            delay(gameTick)
            _state.update { s ->
                val now = clock.now().toEpochMilliseconds()
                val sensorLux = platform.lightSensor.lux.value
                val lux = GameRules.effectiveLux(s.player, sensorLux ?: s.weather.estimatedLux(), now)
                val player = GameRules.tick(s.player, lux, s.weather.facts.state, gameTick.inWholeMilliseconds / 1000.0)
                s.copy(
                    now = now,
                    player = player.copy(updatedAt = now),
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
