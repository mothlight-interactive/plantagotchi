package dev.mothlight.plantagotchi.data.weather

import kotlinx.datetime.LocalTime

data class WeatherFacts(
    val temperature: Double,
    val apparentTemperature: Double,
    val precipitation: Double,
    val humidity: Double,
    val windSpeed: Double,
    val cloudCover: Double,
    val pressure: Double,
    val uvIndex: Double,
    val state: WeatherState,
)

data class CurrentWeather(
    val facts: WeatherFacts,
    /** Local time at the plant's location. */
    val localTime: LocalTime,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val isDay: Boolean,
    val minTemperature: Double,
    val maxTemperature: Double,
) {
    /**
     * Rough outdoor illuminance estimate. Used on platforms without an ambient light sensor (iOS).
     */
    fun estimatedLux(): Float {
        if (!isDay) return 0f
        val cloudFactor = 1.0 - (facts.cloudCover / 100.0).coerceIn(0.0, 1.0) * 0.75
        val weatherFactor = when (facts.state) {
            WeatherState.FOG -> 0.3
            WeatherState.LIGHT_RAIN, WeatherState.SNOW -> 0.5
            WeatherState.RAIN -> 0.35
            WeatherState.HEAVY_RAIN, WeatherState.THUNDERSTORM -> 0.2
            else -> 1.0
        }
        return (10_000.0 * cloudFactor * weatherFactor).toFloat()
    }

    companion object {
        val Default = CurrentWeather(
            facts = WeatherFacts(
                temperature = 2.0,
                apparentTemperature = 2.0,
                precipitation = 0.0,
                humidity = 49.0,
                windSpeed = 17.0,
                cloudCover = 0.0,
                pressure = 1013.0,
                uvIndex = 1.0,
                state = WeatherState.CLEAR_SKY,
            ),
            localTime = LocalTime(12, 0),
            sunrise = LocalTime(6, 46),
            sunset = LocalTime(18, 53),
            isDay = true,
            minTemperature = -1.0,
            maxTemperature = 9.0,
        )
    }
}
