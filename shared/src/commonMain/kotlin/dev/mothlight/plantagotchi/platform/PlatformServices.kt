package dev.mothlight.plantagotchi.platform

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.coroutines.flow.StateFlow

/** Ambient light sensor. [lux] is null if the platform has no usable sensor (e.g. iOS). */
interface LightSensor {
    val lux: StateFlow<Float?>
    fun start()
    fun stop()
}

interface LocationProvider {
    /** Returns the current position or null if unavailable / permission denied. */
    suspend fun currentPosition(): GeoPosition?
}

class PlatformServices(
    val lightSensor: LightSensor,
    val locationProvider: LocationProvider,
)
