package dev.mothlight.plantagotchi.dev

import com.russhwolf.settings.Settings
import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.WeatherState
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Build environment, fixed at build time: Gradle property `appEnv` on Android, build setting
 * `PG_APP_ENV` on iOS. Local builds are [DEV], TestFlight / Play internal [TEST], store releases
 * [PROD]. The test mode exists in dev and test only.
 */
enum class AppEnv(val label: String) {
    DEV("DEV"),
    TEST("TEST"),
    PROD("PROD");

    val testModeAvailable: Boolean get() = this != PROD

    companion object {
        /** Unknown or empty values fall back to [DEV], the local default. */
        fun parse(value: String?): AppEnv =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: DEV
    }
}

/** A place to pretend to be, for weather, seasons and day length. */
data class DevLocation(val id: String, val name: String, val position: GeoPosition)

object DevLocations {
    val all = listOf(
        DevLocation("brugg", "Brugg", GeoPosition(47.481, 8.211, 352.0)),
        DevLocation("reykjavik", "Reykjavík", GeoPosition(64.147, -21.942, 20.0)),
        DevLocation("dubai", "Dubai", GeoPosition(25.205, 55.271, 5.0)),
        DevLocation("sydney", "Sydney", GeoPosition(-33.869, 151.209, 30.0)),
        DevLocation("tokyo", "Tokyo", GeoPosition(35.676, 139.650, 40.0)),
        DevLocation("anchorage", "Anchorage", GeoPosition(61.218, -149.900, 30.0)),
        DevLocation("singapore", "Singapore", GeoPosition(1.352, 103.820, 15.0)),
    )

    fun byId(id: String?): DevLocation? = all.firstOrNull { it.id == id }
}

/**
 * Everything the test mode can force. `null` means "live" (real sensor, GPS, weather or clock).
 * Ignored entirely in [AppEnv.PROD].
 */
@Serializable
data class DevOverrides(
    val weather: WeatherState? = null,
    /** °C */
    val temperature: Double? = null,
    /** km/h */
    val windSpeed: Double? = null,
    /** 0 until 24 * 60 */
    val minutesOfDay: Int? = null,
    val season: Season? = null,
    /** See [DevLocations]. */
    val locationId: String? = null,
    /** Light the plants get, instead of the sensor or the weather estimate. */
    val ambientLux: Float? = null,
    /** Game seconds per real second (decay, growth, withering). */
    val timeSpeed: Float = 1f,
    /** Needs, growth and withering stand still. */
    val freezeNeeds: Boolean = false,
) {
    val isEmpty: Boolean get() = this == DevOverrides()

    val location: DevLocation? get() = DevLocations.byId(locationId)

    /** Applies the weather and clock overrides to the real (or default) weather. */
    fun applyTo(weather: CurrentWeather): CurrentWeather {
        var facts = weather.facts
        this.weather?.let { facts = facts.copy(state = it) }
        temperature?.let { facts = facts.copy(temperature = it) }
        windSpeed?.let { facts = facts.copy(windSpeed = it) }
        var result = weather.copy(facts = facts)
        minutesOfDay?.let { minutes ->
            val time = LocalTime((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
            result = result.copy(localTime = time, isDay = time >= weather.sunrise && time < weather.sunset)
        }
        return result
    }

    companion object {
        val speeds = listOf(1f, 10f, 60f, 600f, 3600f)
    }
}

/** Keeps the overrides across app starts (test devices only). */
class DevStore(private val settings: Settings = Settings()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): DevOverrides = settings.getStringOrNull(KEY)
        ?.let { runCatching { json.decodeFromString(DevOverrides.serializer(), it) }.getOrNull() }
        ?: DevOverrides()

    fun save(overrides: DevOverrides) = settings.putString(KEY, json.encodeToString(DevOverrides.serializer(), overrides))

    private companion object {
        const val KEY = "dev.overrides"
    }
}
