package dev.mothlight.plantagotchi.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mothlight.plantagotchi.data.AppSettings
import dev.mothlight.plantagotchi.data.AppSettingsState
import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.repository.FriendInfo
import dev.mothlight.plantagotchi.data.repository.FriendsService
import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.repository.SendWaterResult
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.DailyReward
import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.FriendCode
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.platform.PlatformServices
import dev.mothlight.plantagotchi.platform.Sfx
import dev.mothlight.plantagotchi.platform.WidgetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class GameUiState(
    val loading: Boolean = true,
    val player: PlayerState = PlayerState(),
    val weather: CurrentWeather = CurrentWeather.Default,
    val position: GeoPosition = GeoPosition.Default,
    /** Light the plants currently receive, from the sensor or estimated from the weather. */
    val ambientLux: Float = 0f,
    val luxFromSensor: Boolean = false,
    /** Epoch millis of the last game tick. */
    val now: Long = 0L,
    /** Local epoch day. */
    val today: Long = 0L,
    val season: Season = Season.SUMMER,
    val settings: AppSettingsState = AppSettingsState(),
    val friendsAvailable: Boolean = false,
    val friends: List<FriendInfo> = emptyList(),
    val cameraLightSupported: Boolean = false,
) {
    val lampActive: Boolean get() = player.isLampActive(now)
    val rewardAvailable: Boolean get() = !loading && DailyReward.isAvailable(player, today)
}

/** One-off events the scene turns into animations and the UI into hints. */
sealed interface GameEvent {
    data class Purchased(val item: ShopItem) : GameEvent
    data object Petted : GameEvent
    data class GrewUp(val plantId: String, val stage: Int) : GameEvent
    data class Withered(val plantId: String) : GameEvent
    data class RewardClaimed(val coins: Int, val streak: Int) : GameEvent
    data object RewardAlreadyClaimed : GameEvent
    data class WaterReceived(val amount: Int) : GameEvent
    data object PlantSwitched : GameEvent
    data object Replanted : GameEvent
}

class PlantagotchiViewModel(
    private val platform: PlatformServices,
    private val repository: GameStateRepository,
    private val weatherClient: OpenMeteoClient,
    private val friendsService: FriendsService? = null,
    private val appSettings: AppSettings? = null,
    private val reminders: ReminderPlanner = ReminderPlanner(),
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val gameTick: Duration = 1.seconds,
    private val dataInterval: Duration = 1.minutes,
    private val saveInterval: Duration = 15.seconds,
    autoStart: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameUiState(
            settings = appSettings?.load() ?: AppSettingsState(),
            friendsAvailable = friendsService != null,
            cameraLightSupported = platform.lightSensor.supportsCamera,
        )
    )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var lastPetSound = 0L

    init {
        platform.lightSensor.setCameraEnabled(_state.value.settings.cameraLight)
        if (autoStart) viewModelScope.launch { start() }
    }

    /** Loads the game; [loops] = false only for tests. */
    internal suspend fun start(loops: Boolean = true) {
        val now = clock.now().toEpochMilliseconds()
        val loaded = runCatching { repository.load() }.getOrNull()
        val player = (loaded?.let { GameRules.applyOfflineDecay(it, now) } ?: PlayerState(updatedAt = now))
            .let { if (it.activePlantId.isEmpty()) it.copy(activePlantId = it.plants.first().id) else it }
        _state.update {
            it.copy(loading = false, player = player, position = player.lastPosition, now = now, today = today(now))
        }
        if (!loops) return
        if (_state.value.settings.notifications) {
            viewModelScope.launch { runCatchingCancellable { platform.notifications.requestPermission() } }
        }
        viewModelScope.launch { dataLoop() }
        viewModelScope.launch { gameLoop() }
        viewModelScope.launch { saveLoop() }
    }

    fun onStart() {
        platform.lightSensor.start()
        platform.notifications.cancelAll()
    }

    fun onStop() {
        platform.lightSensor.stop()
        viewModelScope.launch {
            save()
            scheduleReminders()
        }
    }

    // region Player actions

    /** Swiping over the plant pets it. */
    fun pet(dragY: Float) {
        if (_state.value.loading) return
        val before = _state.value.player
        val after = GameRules.pet(before, dragY)
        if (after != before) {
            _state.update { it.copy(player = after) }
            _events.tryEmit(GameEvent.Petted)
            val now = clock.now().toEpochMilliseconds()
            if (now - lastPetSound > 250) {
                lastPetSound = now
                sound(Sfx.PET)
            }
        }
    }

    fun buy(item: ShopItem): PurchaseResult {
        if (_state.value.loading) return PurchaseResult.NotEnoughCoins
        val result = Shop.purchase(_state.value.player, item, clock.now().toEpochMilliseconds())
        if (result is PurchaseResult.Success) {
            _state.update { it.copy(player = result.state) }
            _events.tryEmit(GameEvent.Purchased(item))
            sound(Sfx.BUY)
            viewModelScope.launch { save() }
        }
        return result
    }

    fun selectPot(potId: String) = updatePlayer { Shop.selectPot(it, potId) }

    fun replant() {
        updatePlayer { Shop.replant(it, clock.now().toEpochMilliseconds()) }
        _events.tryEmit(GameEvent.Replanted)
        sound(Sfx.LEVEL_UP)
    }

    fun compost() = updatePlayer { Shop.compost(it) }

    /** Shows the next ([delta] = 1) or previous (-1) plant. */
    fun switchPlant(delta: Int) {
        val player = _state.value.player
        if (player.plants.size < 2) return
        val index = (player.activeIndex + delta).mod(player.plants.size)
        updatePlayer { it.copy(activePlantId = it.plants[index].id) }
        _events.tryEmit(GameEvent.PlantSwitched)
        sound(Sfx.TAP)
    }

    fun claimReward() {
        val s = _state.value
        if (s.loading) return
        val claimed = DailyReward.claim(s.player, s.today)
        if (claimed == null) {
            _events.tryEmit(GameEvent.RewardAlreadyClaimed)
            return
        }
        val (player, coins) = claimed
        _state.update { it.copy(player = player) }
        _events.tryEmit(GameEvent.RewardClaimed(coins, player.streak))
        sound(Sfx.REWARD)
        viewModelScope.launch { save() }
    }

    // endregion

    // region Friends

    /** Returns false if [code] is not a valid friend code. */
    fun addFriend(code: String): Boolean {
        val normalized = FriendCode.normalize(code) ?: return false
        if (normalized == _state.value.player.friendCode) return false
        updatePlayer { if (normalized in it.friends) it else it.copy(friends = it.friends + normalized) }
        viewModelScope.launch { refreshFriends() }
        return true
    }

    fun removeFriend(code: String) = updatePlayer { it.copy(friends = it.friends - code) }

    suspend fun sendWater(code: String): SendWaterResult {
        val service = friendsService ?: return SendWaterResult.OFFLINE
        return runCatchingCancellable { service.sendWater(code) }
            ?.also { if (it == SendWaterResult.SENT) sound(Sfx.WATER) }
            ?: SendWaterResult.OFFLINE
    }

    suspend fun refreshFriends() {
        val service = friendsService ?: return
        val codes = _state.value.player.friends
        runCatchingCancellable { service.friends(codes) }?.let { friends ->
            _state.update { it.copy(friends = friends) }
        }
    }

    private suspend fun claimGifts() {
        val service = friendsService ?: return
        val amount = runCatchingCancellable { service.claimGifts() } ?: return
        if (amount <= 0) return
        updatePlayer { p -> p.copy(plants = p.plants.map { if (it.dead) it else it.copy(water = (it.water + amount).coerceAtMost(GameRules.MAX)) }) }
        _events.tryEmit(GameEvent.WaterReceived(amount.toInt()))
        sound(Sfx.WATER)
    }

    // endregion

    // region Settings

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        val next = transform(_state.value.settings)
        _state.update { it.copy(settings = next) }
        appSettings?.save(next)
        platform.lightSensor.setCameraEnabled(next.cameraLight)
        if (next.notifications) viewModelScope.launch { platform.notifications.requestPermission() }
    }

    // endregion

    private fun updatePlayer(transform: (PlayerState) -> PlayerState) {
        if (_state.value.loading) return
        _state.update { it.copy(player = transform(it.player)) }
    }

    private fun sound(sfx: Sfx) {
        if (_state.value.settings.sound) platform.sound.play(sfx)
    }

    private fun today(now: Long): Long =
        kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date.toEpochDays()

    internal fun tick() {
        val now = clock.now().toEpochMilliseconds()
        val before = _state.value.player
        _state.update { s ->
            val sensorLux = platform.lightSensor.lux.value
            val lux = GameRules.effectiveLux(s.player, sensorLux ?: s.weather.estimatedLux(), now)
            val env = Environment(lux, s.weather.facts.state, s.weather.facts.temperature, now)
            val player = GameRules.tick(s.player, env, gameTick.inWholeMilliseconds / 1000.0)
            val date = kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
            s.copy(
                now = now,
                today = date.toEpochDays(),
                season = Season.of(date.month.ordinal + 1, s.position.latitude),
                player = player.copy(updatedAt = now),
                ambientLux = lux,
                luxFromSensor = sensorLux != null,
            )
        }
        val after = _state.value.player
        before.plants.forEach { old ->
            val new = after.plants.firstOrNull { it.id == old.id } ?: return@forEach
            if (new.stage > old.stage) {
                _events.tryEmit(GameEvent.GrewUp(new.id, new.stage))
                sound(Sfx.LEVEL_UP)
            }
            if (new.dead && !old.dead) {
                _events.tryEmit(GameEvent.Withered(new.id))
                sound(Sfx.WITHER)
            }
        }
    }

    private suspend fun gameLoop() {
        while (viewModelScope.isActive) {
            delay(gameTick)
            tick()
        }
    }

    private suspend fun dataLoop() {
        var iteration = 0
        while (viewModelScope.isActive) {
            refreshWeather()
            claimGifts()
            if (iteration++ % 5 == 0) refreshFriends()
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
        val s = _state.value
        if (s.loading) return
        runCatchingCancellable { repository.save(s.player) }
        val plant = s.player.activePlant
        runCatchingCancellable {
            platform.widget.update(
                WidgetSnapshot(
                    species = plant.species.name,
                    stage = plant.stage,
                    dead = plant.dead,
                    lux = plant.lux.toInt(),
                    water = plant.water.toInt(),
                    love = plant.love.toInt(),
                    fertilizer = plant.fertilizer.toInt(),
                    coins = s.player.coins,
                    updatedAt = s.now,
                )
            )
        }
    }

    private suspend fun scheduleReminders() {
        val s = _state.value
        if (s.loading || !s.settings.notifications) return
        runCatchingCancellable {
            platform.notifications.schedule(reminders.plan(s.player, s.today, s.now, timeZone))
        }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}
