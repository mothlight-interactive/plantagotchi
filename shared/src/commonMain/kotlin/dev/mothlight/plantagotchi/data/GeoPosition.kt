package dev.mothlight.plantagotchi.data

import kotlinx.serialization.Serializable

@Serializable
data class GeoPosition(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
) {
    companion object {
        /** FHNW Brugg/Windisch, used when no location is available. */
        val Default = GeoPosition(latitude = 47.4809967, longitude = 8.2115859, altitude = 522.0)
    }
}
