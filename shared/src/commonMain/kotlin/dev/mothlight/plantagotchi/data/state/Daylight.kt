package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.tan

/**
 * Whether the sun is up at a place and time, from the date and position alone (no forecast).
 * Used for the time the app was closed. Good to a few minutes, which is plenty for the game.
 */
object Daylight {
    fun isDay(epochMillis: Long, position: GeoPosition): Boolean {
        val days = epochMillis / 86_400_000.0
        // Day of the year, counted from 1 January 1970 (leap years average out)
        val dayOfYear = (days % 365.2425)
        val declination = -23.44 * RAD * cos(2 * PI * (dayOfYear + 10) / 365.2425)
        val latitude = position.latitude.coerceIn(-89.0, 89.0) * RAD
        val cosHour = -tan(latitude) * tan(declination)
        if (cosHour <= -1) return true // midnight sun
        if (cosHour >= 1) return false // polar night
        val halfDayHours = acos(cosHour) / RAD / 15.0
        // Local solar time: UTC shifted by the longitude
        val solarHour = (((days - days.toLong()) * 24 + position.longitude / 15.0) % 24 + 24) % 24
        return kotlin.math.abs(solarHour - 12.0) < halfDayHours
    }

    private const val RAD = PI / 180.0
}
