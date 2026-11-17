package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.shop.PotId
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
    /** Pots the player owns, see [PotId]. */
    val ownedPots: Set<String> = setOf(PotId.DEFAULT),
    val selectedPot: String = PotId.DEFAULT,
    /** Epoch millis until which the grow lamp booster is active. */
    val lampUntil: Long = 0L,
    /** Epoch millis of the last game tick that was persisted. */
    val updatedAt: Long = 0L,
) {
    /** Average of all needs, 0..100. */
    val health: Double get() = (lux + water + love + fertilizer) / 4.0

    fun isLampActive(nowMillis: Long) = nowMillis < lampUntil
}
