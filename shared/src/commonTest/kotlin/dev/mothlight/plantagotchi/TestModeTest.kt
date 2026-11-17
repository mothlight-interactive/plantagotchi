package dev.mothlight.plantagotchi

import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.MapSettings
import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.dev.AppEnv
import dev.mothlight.plantagotchi.dev.DevOverrides
import dev.mothlight.plantagotchi.dev.DevStore
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.model.ReminderPlanner
import dev.mothlight.plantagotchi.platform.LightSensor
import dev.mothlight.plantagotchi.platform.LocationProvider
import dev.mothlight.plantagotchi.platform.PlatformServices
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TestModeTest {

    @Test
    fun envParsing() {
        assertEquals(AppEnv.DEV, AppEnv.parse(null))
        assertEquals(AppEnv.DEV, AppEnv.parse("staging"))
        assertEquals(AppEnv.TEST, AppEnv.parse("test"))
        assertEquals(AppEnv.PROD, AppEnv.parse(" PROD "))
        assertTrue(AppEnv.DEV.testModeAvailable)
        assertTrue(AppEnv.TEST.testModeAvailable)
        assertFalse(AppEnv.PROD.testModeAvailable)
    }

    @Test
    fun overridesChangeWeatherAndClock() {
        val night = DevOverrides(weather = WeatherState.SNOW, temperature = -8.0, windSpeed = 50.0, minutesOfDay = 23 * 60)
            .applyTo(CurrentWeather.Default)
        assertEquals(WeatherState.SNOW, night.facts.state)
        assertEquals(-8.0, night.facts.temperature)
        assertEquals(LocalTime(23, 0), night.localTime)
        assertFalse(night.isDay)
        assertTrue(DevOverrides(minutesOfDay = 12 * 60).applyTo(CurrentWeather.Default).isDay)
        assertEquals(CurrentWeather.Default, DevOverrides().applyTo(CurrentWeather.Default))
    }

    @Test
    fun overridesSurviveRestarts() {
        val store = DevStore(MapSettings())
        store.save(DevOverrides(timeSpeed = 60f, season = Season.WINTER, locationId = "sydney"))
        assertEquals(DevOverrides(timeSpeed = 60f, season = Season.WINTER, locationId = "sydney"), store.load())
    }

    // region View model

    private class FixedClock(var millis: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private class Repo(var state: PlayerState?) : GameStateRepository {
        override suspend fun load() = state
        override suspend fun save(state: PlayerState) { this.state = state }
    }

    private val platform = PlatformServices(
        lightSensor = object : LightSensor {
            override val lux: StateFlow<Float?> = MutableStateFlow(0f)
            override fun start() = Unit
            override fun stop() = Unit
        },
        locationProvider = object : LocationProvider {
            override suspend fun currentPosition(): GeoPosition? = null
        },
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val plant = PlantState(water = 50.0, lux = 50.0, love = 50.0, fertilizer = 50.0)

    private fun viewModel(env: AppEnv, store: DevStore? = null) = PlantagotchiViewModel(
        platform = platform,
        repository = Repo(PlayerState(plants = listOf(plant), activePlantId = plant.id, updatedAt = 1_000L)),
        weatherClient = OpenMeteoClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })),
        reminders = ReminderPlanner { "" to "" },
        clock = FixedClock(1_000L),
        timeZone = TimeZone.UTC,
        env = env,
        devStore = store,
        autoStart = false,
    )

    @Test
    fun prodIgnoresTheTestMode() = runTest {
        val store = DevStore(MapSettings()).apply { save(DevOverrides(timeSpeed = 3600f)) }
        val vm = viewModel(AppEnv.PROD, store)
        vm.start(loops = false)
        assertTrue(vm.state.value.dev.isEmpty, "stored overrides are not loaded in prod")
        vm.devUpdate { it.copy(freezeNeeds = true) }
        vm.devUpdatePlayer { it.copy(coins = 9_999) }
        assertTrue(vm.state.value.dev.isEmpty)
        assertEquals(0, vm.state.value.player.coins)
        vm.viewModelScope.cancel()
    }

    @Test
    fun timeSpeedAndFreeze() = runTest {
        val vm = viewModel(AppEnv.TEST)
        vm.start(loops = false)
        vm.devUpdate { it.copy(timeSpeed = 3600f, ambientLux = 0f, weather = WeatherState.CLEAR_SKY) }
        vm.tick()
        // One tick at 3600x is one hour: water drops by 100 / 48 h per hour.
        assertEquals(50.0 - 100.0 / 48, vm.state.value.player.activePlant.water, 1e-6)

        vm.devUpdate { it.copy(freezeNeeds = true) }
        val frozen = vm.state.value.player.activePlant
        repeat(5) { vm.tick() }
        assertEquals(frozen.water, vm.state.value.player.activePlant.water)
        vm.viewModelScope.cancel()
    }

    @Test
    fun weatherSeasonAndPlayerEdits() = runTest {
        val vm = viewModel(AppEnv.DEV)
        vm.start(loops = false)
        vm.devUpdate { it.copy(weather = WeatherState.THUNDERSTORM, season = Season.AUTUMN, minutesOfDay = 2 * 60) }
        vm.tick()
        val s = vm.state.value
        assertEquals(WeatherState.THUNDERSTORM, s.weather.facts.state)
        assertEquals(Season.AUTUMN, s.season)
        assertFalse(s.weather.isDay)

        vm.devUpdatePlayer { it.copy(coins = 500).updateActive { p -> p.copy(growth = 1e9) } }
        assertEquals(500, vm.state.value.player.coins)
        assertEquals(2, vm.state.value.player.activePlant.stage)

        vm.devUpdate { DevOverrides() }
        assertEquals(CurrentWeather.Default, vm.state.value.weather, "back to live")

        vm.devResetGame()
        assertEquals(0, vm.state.value.player.coins)
        assertEquals(1, vm.state.value.player.plants.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun skipAheadWithersNeglectedPlants() = runTest {
        val vm = viewModel(AppEnv.TEST)
        vm.start(loops = false)
        vm.devUpdatePlayer { it.updateActive { p -> p.copy(water = 0.0, lux = 0.0, love = 0.0, fertilizer = 1.0) } }
        vm.devSkip(3 * 86_400.0)
        assertTrue(vm.state.value.player.activePlant.dead)
        vm.viewModelScope.cancel()
    }

    // endregion
}
