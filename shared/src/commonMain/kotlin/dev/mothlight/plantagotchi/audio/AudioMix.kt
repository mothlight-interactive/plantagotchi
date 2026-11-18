package dev.mothlight.plantagotchi.audio

import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.platform.Sfx

/** All audio assets, so they can be loaded up front. */
object AudioAssets {
    val music = listOf("music/day", "music/night")
    val ambient = listOf("ambient/rain", "ambient/wind", "ambient/waves", "ambient/birds", "ambient/crickets", "ambient/city")
    val voices = Species.entries.flatMap { listOf(voice(it, true), voice(it, false)) }
    val all: List<String> = Sfx.entries.map { it.asset } + voices + ambient + music

    /** Each species has a happy and a sad voice. */
    fun voice(species: Species, happy: Boolean) = "voice/${species.name.lowercase()}_${if (happy) "happy" else "sad"}"
}

/** Which loops should play how loud, derived from the game state. Pure, so it can be tested. */
object AudioMix {
    const val MUSIC_VOLUME = 0.35f

    fun targets(state: GameUiState): Map<String, Float> {
        val settings = state.settings
        val weather = state.weather.facts
        val day = state.weather.isDay
        val scenery = state.player.scenery
        val result = HashMap<String, Float>()
        AudioAssets.music.forEach { result[it] = 0f }
        AudioAssets.ambient.forEach { result[it] = 0f }
        if (state.loading) return result

        if (settings.music) result[if (day) "music/day" else "music/night"] = MUSIC_VOLUME

        if (settings.ambience) {
            val rain = when (weather.state) {
                WeatherState.LIGHT_RAIN -> 0.3f
                WeatherState.RAIN -> 0.5f
                WeatherState.HEAVY_RAIN, WeatherState.THUNDERSTORM -> 0.75f
                else -> 0f
            }
            val wet = rain > 0f || weather.state == WeatherState.SNOW
            result["ambient/rain"] = rain
            result["ambient/wind"] = ((weather.windSpeed.toFloat() - 15f) / 35f).coerceIn(0f, 0.5f) +
                (if (weather.state == WeatherState.SNOW) 0.15f else 0f)
            result["ambient/waves"] = if (scenery == Scenery.BEACH) 0.45f else 0f
            result["ambient/city"] = if (scenery == Scenery.CITY) 0.35f else 0f
            val calm = !wet && weather.state != WeatherState.FOG
            result["ambient/birds"] = if (day && calm && scenery != Scenery.CITY && state.season != Season.WINTER) 0.3f else 0f
            result["ambient/crickets"] = if (!day && calm && state.season != Season.WINTER) 0.3f else 0f
            if (scenery == Scenery.ROOM) {
                // Indoors: the outside comes muffled through the window, a bit of city at night
                AudioAssets.ambient.forEach { key -> result[key]?.let { result[key] = it * 0.4f } }
                result["ambient/city"] = if (day) 0.06f else 0.12f
            }
        }
        return result
    }

    /** True when the active plant should complain now and then. */
    fun plantIsUnhappy(plant: PlantState): Boolean =
        !plant.dead && listOf(plant.water, plant.lux, plant.love, plant.fertilizer).any { it < 20.0 }
}
