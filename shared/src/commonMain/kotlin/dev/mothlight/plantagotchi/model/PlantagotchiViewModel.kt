package dev.mothlight.plantagotchi.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mothlight.plantagotchi.data.AppSettings
import dev.mothlight.plantagotchi.data.AppSettingsState
import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.DailyReward
import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.platform.PlatformServices
import dev.mothlight.plantagotchi.platform.Sfx
import dev.mothlight.plantagotchi.platform.play
import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.audio.AudioDirector
import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.state.Arrangement
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.platform.WidgetSnapshot
import dev.mothlight.plantagotchi.platform.Reminder
import dev.mothlight.plantagotchi.dev.AppEnv
import dev.mothlight.plantagotchi.dev.DevOverrides
import dev.mothlight.plantagotchi.dev.DevStore
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
    val cameraLightSupported: Boolean = false,
    /** Build environment; the test mode exists in dev and test. */
    val env: AppEnv = AppEnv.PROD,
    /** Test mode overrides, always empty in prod. */
    val dev: DevOverrides = DevOverrides(),
    /** A plant was tapped: it is shown larger and the need bubbles are visible. */
    val plantSelected: Boolean = false
) {
    /** Whether the active plant's grow lamp is on. */
    val lampActive: Boolean get() = player.activePlant.lampOn(now)

    /** Indices of the plants whose grow lamp is on. */
    val litPlants: List<Int> get() = player.plants.indices.filter { player.plants[it].lampOn(now) }
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
    data object PlantSwitched : GameEvent
    data object Replanted : GameEvent
    /** Test mode: make the active plant do an idle action, by `PlantAction` name. */
    data class DevPlantAction(val action: String) : GameEvent
}

class PlantagotchiViewModel(
    private val platform: PlatformServices,
    private val repository: GameStateRepository,
    private val weatherClient: OpenMeteoClient,
    private val appSettings: AppSettings? = null,
    private val env: AppEnv = AppEnv.PROD,
    private val devStore: DevStore? = null,
    private val reminders: ReminderPlanner = ReminderPlanner(),
    private val nudges: NudgePlanner = NudgePlanner(),
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
            cameraLightSupported = platform.lightSensor.supportsCamera,
            env = env,
            dev = if (env.testModeAvailable) devStore?.load() ?: DevOverrides() else DevOverrides(),
        )
    )

    /** Weather as fetched (or the default), before test mode overrides. */
    private var liveWeather: CurrentWeather = CurrentWeather.Default
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var lastPetSound = 0L
    private var lastVoice = 0L
    private val audio = AudioDirector(platform.sound)

    init {
        platform.lightSensor.setCameraEnabled(_state.value.settings.cameraLight)
        if (autoStart) viewModelScope.launch { start() }
    }

    /** Loads the game; [loops] = false only for tests. */
    internal suspend fun start(loops: Boolean = true) {
        val now = clock.now().toEpochMilliseconds()
        val loaded = runCatching { repository.load() }.getOrNull()
        val player = (loaded?.withPerPlantLamps()?.let { GameRules.applyOfflineDecay(it, now) } ?: PlayerState(updatedAt = now))
            .let { if (it.activePlantId.isEmpty()) it.copy(activePlantId = it.plants.first().id) else it }
        _state.update {
            it.copy(
                loading = false, player = player, position = player.lastPosition, now = now, today = today(now),
                weather = it.dev.applyTo(liveWeather),
            )
        }
        audio.update(_state.value)
        if (!loops) return
        if (_state.value.settings.notifications || _state.value.settings.nudges) {
            viewModelScope.launch { runCatchingCancellable { platform.notifications.requestPermission() } }
        }
        viewModelScope.launch { dataLoop() }
        viewModelScope.launch { gameLoop() }
        viewModelScope.launch { saveLoop() }
    }

    fun onStart() {
        audio.start(viewModelScope)
        platform.lightSensor.start()
        platform.notifications.cancelAll()
    }

    fun onStop() {
        audio.stop()
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
        val after = GameRules.pet(before, dragY, _state.value.today)
        if (after != before) {
            _state.update { it.copy(player = after, plantSelected = true) }
            _events.tryEmit(GameEvent.Petted)
            val now = clock.now().toEpochMilliseconds()
            when {
                now - lastVoice > 1_500 -> {
                    lastVoice = now
                    lastPetSound = now
                    voice(after.activePlant.species, happy = true)
                }
                now - lastPetSound > 250 -> {
                    lastPetSound = now
                    sound(Sfx.PET)
                }
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
            when (item) {
                is ShopItem.Refill -> sound(if (item.need == Need.WATER) Sfx.WATER else Sfx.FERTILIZER)
                is ShopItem.GrowLamp -> sound(Sfx.LAMP)
                is ShopItem.Pot, is ShopItem.Backdrop, is ShopItem.Floor -> sound(Sfx.DECOR)
                is ShopItem.Seed -> voice(item.species, happy = true)
                is ShopItem.Revive -> voice(result.state.activePlant.species, happy = true)
            }
            viewModelScope.launch { save() }
        } else {
            sound(Sfx.ERROR)
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

    /** Moves plant [index] to [spot] in the cozy room; a plant there swaps with it. */
    fun movePlant(index: Int, spot: RoomSpot) {
        updatePlayer { Arrangement.move(it, index, spot) }
        sound(Sfx.DECOR)
    }

    /** Swaps two plants in the outdoor row. */
    fun swapPlants(from: Int, to: Int) {
        updatePlayer { Arrangement.swap(it, from, to) }
        sound(Sfx.DECOR)
    }

    /** Discards the active plant, also a living one (see [Shop.discard]). */
    fun discardPlant() {
        val single = _state.value.player.plants.size <= 1
        updatePlayer { Shop.discard(it, clock.now().toEpochMilliseconds()) }
        _events.tryEmit(if (single) GameEvent.Replanted else GameEvent.PlantSwitched)
        sound(Sfx.WITHER)
    }

    /** Shows the next ([delta] = 1) or previous (-1) plant. */
    fun switchPlant(delta: Int) {
        val player = _state.value.player
        if (player.plants.size < 2) return
        val index = (player.activeIndex + delta).mod(player.plants.size)
        updatePlayer { it.copy(activePlantId = it.plants[index].id) }
        _events.tryEmit(GameEvent.PlantSwitched)
        sound(Sfx.TAP)
    }

    fun selectPlant(index: Int) {
        val player = _state.value.player
        if (index !in player.plants.indices) return
        if (index == player.activeIndex) {
            _state.update { it.copy(plantSelected = true) }
            return
        }
        _state.update { it.copy(plantSelected = true) }
        updatePlayer { it.copy(activePlantId = it.plants[index].id) }
        _events.tryEmit(GameEvent.PlantSwitched)
        sound(Sfx.SWITCH)
        player.plants[index].takeUnless { it.dead }?.let { voice(it.species, happy = true, volume = 0.6f) }
    }

    /** Tapping the active plant: the flytrap snaps, the others say hello. */
    /** Tapping next to the plants: the selection ends and the need bubbles slide away. */
    fun deselect() = _state.update { it.copy(plantSelected = false) }

    /** Switches the string lights of the plant shop (a weak light for the plants there). */
    fun toggleStringLights() = updatePlayer { it.copy(stringLights = !it.stringLights) }

    fun tapPlant() {
        _state.update { it.copy(plantSelected = true) }
        val plant = _state.value.player.activePlant
        if (plant.dead) return
        if (plant.species == Species.FLYTRAP) {
            sound(Sfx.SNAP)
        } else {
            val now = clock.now().toEpochMilliseconds()
            if (now - lastVoice > 800) {
                lastVoice = now
                voice(plant.species, happy = true, volume = 0.7f)
            }
        }
    }

    /** UI sounds, e.g. opening and closing sheets. */
    fun playUi(sfx: Sfx) = sound(sfx, 0.6f)

    fun selectScenery(scenery: Scenery) {
        updatePlayer { Shop.selectScenery(it, scenery) }
        sound(Sfx.DECOR)
    }

    fun selectGround(ground: Ground) {
        updatePlayer { Shop.selectGround(it, ground) }
        sound(Sfx.DECOR)
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

    // region Settings

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        val next = transform(_state.value.settings)
        _state.update { it.copy(settings = next) }
        appSettings?.save(next)
        platform.lightSensor.setCameraEnabled(next.cameraLight)
        if (next.notifications || next.nudges) viewModelScope.launch { platform.notifications.requestPermission() }
    }

    // endregion

    private fun updatePlayer(transform: (PlayerState) -> PlayerState) {
        if (_state.value.loading) return
        _state.update { it.copy(player = transform(it.player)) }
    }

    private fun sound(sfx: Sfx, volume: Float = 1f) {
        if (_state.value.settings.sound) platform.sound.play(sfx, volume)
    }

    private fun voice(species: Species, happy: Boolean, volume: Float = 0.8f) {
        if (_state.value.settings.sound) platform.sound.play(AudioAssets.voice(species, happy), volume)
    }

    private fun today(now: Long): Long =
        kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date.toEpochDays()

    internal fun tick() {
        val now = clock.now().toEpochMilliseconds()
        val before = _state.value.player
        _state.update { s ->
            val dev = s.dev
            val sensorLux = platform.lightSensor.lux.value
            val baseLux = dev.ambientLux ?: sensorLux ?: s.weather.estimatedLux()
            // Lamps are per plant: the tick adds them; the UI shows the active plant's light.
            val lux = GameRules.effectiveLux(s.player.activePlant, baseLux, now)
            val environment = Environment(baseLux, s.weather.facts.state, s.weather.facts.temperature, now)
            val seconds = gameTick.inWholeMilliseconds / 1000.0 * dev.timeSpeed
            val player = if (dev.freezeNeeds) s.player else GameRules.tick(s.player, environment, seconds)
            val date = kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
            s.copy(
                now = now,
                today = date.toEpochDays(),
                season = dev.season ?: Season.of(date.month.ordinal + 1, s.position.latitude),
                player = player.copy(updatedAt = now),
                ambientLux = lux,
                luxFromSensor = dev.ambientLux == null && sensorLux != null,
            )
        }
        audio.update(_state.value)
        emitTransitions(before, _state.value.player)
    }

    private fun emitTransitions(before: PlayerState, after: PlayerState) {
        before.plants.forEach { old ->
            val new = after.plants.firstOrNull { it.id == old.id } ?: return@forEach
            if (new.stage > old.stage) {
                _events.tryEmit(GameEvent.GrewUp(new.id, new.stage))
                sound(Sfx.LEVEL_UP)
                voice(new.species, happy = true)
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
        while (viewModelScope.isActive) {
            refreshWeather()
            delay(dataInterval)
        }
    }

    private suspend fun refreshWeather() {
        val position = _state.value.dev.location?.position
            ?: runCatchingCancellable { platform.locationProvider.currentPosition() }
            ?: _state.value.position
        _state.update { it.copy(position = position, player = it.player.copy(lastPosition = position)) }
        runCatchingCancellable { weatherClient.currentWeather(position) }?.let { weather ->
            liveWeather = weather
            _state.update { it.copy(weather = it.dev.applyTo(weather)) }
        }
    }

    // region Test mode (no effect in prod)

    private val testMode: Boolean get() = env.testModeAvailable

    fun devUpdate(transform: (DevOverrides) -> DevOverrides) {
        if (!testMode) return
        val before = _state.value.dev
        val next = transform(before)
        _state.update { it.copy(dev = next, weather = next.applyTo(liveWeather)) }
        devStore?.save(next)
        if (next.locationId != before.locationId) viewModelScope.launch { refreshWeather() }
        if (next.season != before.season) tick()
    }

    /** Edits the save game directly: coins, plants, needs, unlocks. */
    fun devUpdatePlayer(transform: (PlayerState) -> PlayerState) {
        if (!testMode || _state.value.loading) return
        _state.update { it.copy(player = transform(it.player)) }
        viewModelScope.launch { save() }
    }

    /** Plays an animation or reaction without the game logic behind it. */
    fun devEmit(event: GameEvent) {
        if (testMode) _events.tryEmit(event)
    }

    fun devPlay(asset: String) {
        if (testMode) platform.sound.play(asset)
    }

    /** Schedules a reminder a few seconds from now; send the app to the background to see it. */
    fun devTestNotification(title: String, body: String) {
        if (!testMode) return
        viewModelScope.launch {
            platform.notifications.requestPermission()
            platform.notifications.schedule(listOf(Reminder(id = 99, delaySeconds = 5, title = title, body = body)))
        }
    }

    /** Starts over with a fresh save game (test mode overrides stay). */
    fun devResetGame() {
        if (!testMode) return
        val now = clock.now().toEpochMilliseconds()
        val fresh = PlayerState(updatedAt = now).let { it.copy(activePlantId = it.plants.first().id) }
        _state.update { it.copy(player = fresh) }
        viewModelScope.launch { save() }
    }

    /** Runs [seconds] of game time at once, e.g. to see what a night away does. */
    fun devSkip(seconds: Double) {
        if (!testMode || _state.value.loading) return
        val now = clock.now().toEpochMilliseconds()
        val s = _state.value
        val before = s.player
        var player = s.player
        var cursor = now
        var left = seconds
        while (left > 0) {
            val step = minOf(left, 600.0)
            cursor += (step * 1000).toLong()
            val environment = Environment(s.ambientLux, s.weather.facts.state, s.weather.facts.temperature, cursor)
            player = GameRules.tick(player, environment, step)
            left -= step
        }
        // The simulation ran ahead of the real clock: move "critical since" back by the skipped time.
        val skipped = cursor - now
        player = player.copy(plants = player.plants.map { if (it.criticalSince > 0) it.copy(criticalSince = it.criticalSince - skipped) else it })
        _state.update { it.copy(player = player) }
        emitTransitions(before, player)
    }

    // endregion

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
        runCatchingCancellable {
            val names = Species.entries.associateWith { ReminderPlanner.speciesName(it) }
            platform.widget.update(WidgetSnapshot.of(s.player, s.now) { names.getValue(it) })
        }
    }

    private suspend fun scheduleReminders() {
        val s = _state.value
        if (s.loading || (!s.settings.notifications && !s.settings.nudges)) return
        runCatchingCancellable {
            // Two independent groups: what a plant needs, and friendly hints to come back.
            val needs = if (s.settings.notifications) reminders.plan(s.player, s.today, s.now, timeZone) else emptyList()
            val hints = if (s.settings.nudges) {
                nudges.plan(s.player, s.today, s.now, timeZone, s.weather.minTemperature, s.weather.maxTemperature)
            } else emptyList()
            platform.notifications.schedule(needs + hints)
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
