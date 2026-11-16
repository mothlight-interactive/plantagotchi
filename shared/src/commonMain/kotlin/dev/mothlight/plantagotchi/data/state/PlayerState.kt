package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.serialization.Serializable

/** All needs are percentages in the range 0..100. */
@Serializable
data class PlayerState(
    val lux: Double = 100.0,
    val water: Double = 100.0,
    val love: Double = 0.0,
    val fertilizer: Double = 100.0,
    val coins: Int = 0,
    val lastPosition: GeoPosition = GeoPosition.Default,
    /** Epoch millis of the last game tick that was persisted. */
    val updatedAt: Long = 0L,
)
