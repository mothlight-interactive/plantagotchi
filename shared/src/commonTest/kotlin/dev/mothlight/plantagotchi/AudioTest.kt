package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.audio.AudioDirector
import dev.mothlight.plantagotchi.audio.AudioMix
import dev.mothlight.plantagotchi.data.AppSettingsState
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.platform.SoundPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {

    private fun state(
        weather: WeatherState = WeatherState.CLEAR_SKY,
        day: Boolean = true,
        scenery: Scenery = Scenery.MOUNTAINS,
        settings: AppSettingsState = AppSettingsState(),
        plant: PlantState = PlantState(),
    ) = GameUiState(
        loading = false,
        player = PlayerState(plants = listOf(plant), activePlantId = plant.id, scenery = scenery),
        weather = CurrentWeather.Default.copy(facts = CurrentWeather.Default.facts.copy(state = weather, windSpeed = 5.0), isDay = day),
        season = Season.SUMMER,
        settings = settings,
    )

    @Test
    fun mixFollowsTimeWeatherAndScenery() {
        val day = AudioMix.targets(state())
        assertTrue(day.getValue("music/day") > 0f)
        assertEquals(0f, day.getValue("music/night"))
        assertTrue(day.getValue("ambient/birds") > 0f)
        assertEquals(0f, day.getValue("ambient/rain"))

        val stormyNight = AudioMix.targets(state(WeatherState.THUNDERSTORM, day = false))
        assertTrue(stormyNight.getValue("music/night") > 0f)
        assertTrue(stormyNight.getValue("ambient/rain") > 0.5f)
        assertEquals(0f, stormyNight.getValue("ambient/crickets"), "no crickets in the rain")

        assertTrue(AudioMix.targets(state(scenery = Scenery.BEACH)).getValue("ambient/waves") > 0f)
        assertEquals(0f, AudioMix.targets(state(scenery = Scenery.CITY)).getValue("ambient/birds"))
    }

    @Test
    fun settingsMuteMusicAndAmbience() {
        val silent = AudioMix.targets(state(WeatherState.RAIN, settings = AppSettingsState(music = false, ambience = false)))
        assertTrue(silent.values.all { it == 0f })
    }

    private class FakePlayer : SoundPlayer {
        val loops = HashMap<String, Float>()
        val played = mutableListOf<String>()
        override suspend fun load(sounds: Map<String, ByteArray>) = Unit
        override fun play(asset: String, volume: Float) { played += asset }
        override fun setLoop(asset: String, volume: Float) { loops[asset] = volume }
    }

    @Test
    fun directorFadesInsteadOfJumping() {
        val player = FakePlayer()
        val director = AudioDirector(player, Random(1))
        director.update(state())
        director.step(0.1f)
        val first = player.loops.getValue("music/day")
        assertTrue(first > 0f && first < AudioMix.MUSIC_VOLUME, "starts fading in")
        repeat(30) { director.step(0.1f) }
        assertEquals(AudioMix.MUSIC_VOLUME, player.loops.getValue("music/day"), 1e-4f)

        director.update(state(day = false))
        director.step(0.1f)
        assertTrue(player.loops.getValue("music/day") < AudioMix.MUSIC_VOLUME, "day fades out")
        assertTrue(player.loops.getValue("music/night") > 0f, "night fades in")
    }

    @Test
    fun stormsThunderAndSadPlantsComplain() {
        val player = FakePlayer()
        val director = AudioDirector(player, Random(1))
        val thirsty = PlantState(species = Species.CACTUS, water = 5.0)
        repeat(60) { director.update(state(WeatherState.THUNDERSTORM, plant = thirsty)) }
        assertTrue(player.played.count { it == "sfx/thunder" } >= 3)
        assertTrue(AudioAssets.voice(Species.CACTUS, happy = false) in player.played)
    }

    @Test
    fun everySpeciesHasVoices() {
        assertEquals(Species.entries.size * 2, AudioAssets.voices.size)
    }
}
