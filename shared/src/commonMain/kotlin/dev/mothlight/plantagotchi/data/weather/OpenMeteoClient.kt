package dev.mothlight.plantagotchi.data.weather

import dev.mothlight.plantagotchi.data.GeoPosition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Weather from https://open-meteo.com (free, no API key). */
class OpenMeteoClient(private val http: HttpClient) {

    suspend fun currentWeather(position: GeoPosition): CurrentWeather {
        val response: ForecastResponse = http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", position.latitude)
            parameter("longitude", position.longitude)
            parameter("current", CURRENT_FIELDS)
            parameter("daily", "sunrise,sunset,temperature_2m_max,temperature_2m_min,uv_index_max")
            parameter("timezone", "auto")
            parameter("forecast_days", 1)
        }.body()
        return response.toCurrentWeather()
    }

    private companion object {
        const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code," +
                "cloud_cover,pressure_msl,wind_speed_10m,is_day"
    }
}

@Serializable
internal data class ForecastResponse(
    val current: Current,
    val daily: Daily,
) {
    @Serializable
    data class Current(
        val time: String,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("apparent_temperature") val apparentTemperature: Double,
        @SerialName("relative_humidity_2m") val humidity: Double,
        val precipitation: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("cloud_cover") val cloudCover: Double,
        @SerialName("pressure_msl") val pressure: Double,
        @SerialName("wind_speed_10m") val windSpeed: Double,
        @SerialName("is_day") val isDay: Int,
    )

    @Serializable
    data class Daily(
        val sunrise: List<String>,
        val sunset: List<String>,
        @SerialName("temperature_2m_max") val maxTemperature: List<Double>,
        @SerialName("temperature_2m_min") val minTemperature: List<Double>,
        @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    )

    fun toCurrentWeather() = CurrentWeather(
        facts = WeatherFacts(
            temperature = current.temperature,
            apparentTemperature = current.apparentTemperature,
            precipitation = current.precipitation,
            humidity = current.humidity,
            windSpeed = current.windSpeed,
            cloudCover = current.cloudCover,
            pressure = current.pressure,
            uvIndex = daily.uvIndexMax.firstOrNull() ?: 0.0,
            state = WeatherState.fromWmoCode(current.weatherCode),
        ),
        localTime = current.time.localTime(),
        sunrise = daily.sunrise.first().localTime(),
        sunset = daily.sunset.first().localTime(),
        isDay = current.isDay == 1,
        minTemperature = daily.minTemperature.first(),
        maxTemperature = daily.maxTemperature.first(),
    )

    /** Open-Meteo returns local ISO times without seconds, e.g. 2026-11-16T07:23 */
    private fun String.localTime(): LocalTime = LocalDateTime.parse(this).time
}
