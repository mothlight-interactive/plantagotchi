package dev.mothlight.plantagotchi.data.weather

enum class WeatherState {
    CLEAR_SKY,
    FEW_CLOUDS,
    SCATTERED_CLOUDS,
    MOSTLY_CLOUDY,
    HEAVY_RAIN,
    LIGHT_RAIN,
    THUNDERSTORM,
    RAIN,
    SNOW,
    FOG;

    companion object {
        /** Maps WMO weather interpretation codes (used by Open-Meteo). */
        fun fromWmoCode(code: Int): WeatherState = when (code) {
            0 -> CLEAR_SKY
            1 -> FEW_CLOUDS
            2 -> SCATTERED_CLOUDS
            3 -> MOSTLY_CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57, 61, 80 -> LIGHT_RAIN
            63, 66, 81 -> RAIN
            65, 67, 82 -> HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> CLEAR_SKY
        }
    }
}
