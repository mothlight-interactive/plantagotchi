package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.weather.WeatherState

/** Everything outside the plant that influences one game tick. */
data class Environment(
    val ambientLux: Float,
    val weather: WeatherState,
    /** °C */
    val temperature: Double,
    val nowMillis: Long,
) {
    companion object {
        /** Used while the app is closed: dark, dry, mild. */
        fun offline(nowMillis: Long) = Environment(0f, WeatherState.CLEAR_SKY, 15.0, nowMillis)
    }
}

enum class Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    companion object {
        fun of(month: Int, latitude: Double): Season {
            val northern = when (month) {
                3, 4, 5 -> SPRING
                6, 7, 8 -> SUMMER
                9, 10, 11 -> AUTUMN
                else -> WINTER
            }
            if (latitude >= 0) return northern
            return when (northern) {
                SPRING -> AUTUMN
                SUMMER -> WINTER
                AUTUMN -> SPRING
                WINTER -> SUMMER
            }
        }
    }
}
