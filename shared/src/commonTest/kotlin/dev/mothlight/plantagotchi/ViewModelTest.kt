package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.model.ReminderPlanner
import dev.mothlight.plantagotchi.platform.LightSensor
import dev.mothlight.plantagotchi.platform.LocationProvider
import dev.mothlight.plantagotchi.platform.PlatformServices
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class ViewModelTest {

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

    private fun viewModel(state: PlayerState, clock: Clock) = PlantagotchiViewModel(
        platform = platform,
        repository = Repo(state),
        weatherClient = OpenMeteoClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })),
        reminders = ReminderPlanner { "" to "" },
        clock = clock,
        timeZone = TimeZone.UTC,
        autoStart = false,
    )

    @Test
    fun rewardSwitchingAndGrowthEmitEvents() = runTest {
        val clock = FixedClock(86_400_000L * 10)
        val almostGrown = PlantState(growth = 6 * 3600.0 - 0.1, love = 100.0)
        val other = PlantState()
        val vm = viewModel(
            PlayerState(plants = listOf(almostGrown, other), activePlantId = almostGrown.id, updatedAt = clock.millis),
            clock,
        )
        val events = mutableListOf<GameEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        vm.start(loops = false)

        assertTrue(vm.state.value.rewardAvailable)
        vm.claimReward()
        assertEquals(20, vm.state.value.player.coins)
        vm.claimReward()

        vm.switchPlant(1)
        assertEquals(other.id, vm.state.value.player.activePlant.id)
        vm.switchPlant(1)
        assertEquals(almostGrown.id, vm.state.value.player.activePlant.id)

        clock.millis += 1_000
        vm.tick()

        assertTrue(events.any { it is GameEvent.RewardClaimed })
        assertTrue(GameEvent.RewardAlreadyClaimed in events)
        assertTrue(events.any { it is GameEvent.GrewUp && it.plantId == almostGrown.id })
        collector.cancel()
        vm.viewModelScope.cancel()
    }

    @Test
    fun selectingShowsTheNeedsAndTappingNextToThePlantsHidesThem() = runTest {
        val clock = FixedClock(86_400_000L * 10)
        val a = PlantState(love = 10.0)
        val b = PlantState(love = 10.0)
        val vm = viewModel(PlayerState(plants = listOf(a, b), activePlantId = a.id, updatedAt = clock.millis), clock)
        vm.start(loops = false)
        assertFalse(vm.state.value.plantSelected, "no plant is selected at start")

        vm.selectPlant(1)
        assertTrue(vm.state.value.plantSelected)
        assertEquals(b.id, vm.state.value.player.activePlant.id)

        vm.deselect()
        assertFalse(vm.state.value.plantSelected)
        assertEquals(b.id, vm.state.value.player.activePlant.id, "the last plant stays the shop target")

        vm.selectPlant(1)
        vm.deselect()
        vm.pet(-60f)
        assertTrue(vm.state.value.plantSelected, "petting selects the plant")
        assertTrue(vm.state.value.player.activePlant.love > 10.0)
        vm.viewModelScope.cancel()
    }
}
