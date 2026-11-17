package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.shop.PotId
import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val plants: List<PlantState> = listOf(PlantState()),
    val activePlantId: String = "",
    val coins: Int = 0,
    val lastPosition: GeoPosition = GeoPosition.Default,
    /** Pots the player owns, see [PotId]. */
    val ownedPots: Set<String> = setOf(PotId.DEFAULT),
    val ownedScenery: Set<Scenery> = setOf(Scenery.MOUNTAINS),
    val scenery: Scenery = Scenery.MOUNTAINS,
    val ownedGrounds: Set<Ground> = setOf(Ground.GRASS),
    val ground: Ground = Ground.GRASS,
    /** Epoch millis until which the grow lamp booster is active (lights all plants). */
    val lampUntil: Long = 0L,
    /** Local epoch day of the last claimed daily reward, -1 if never. */
    val lastRewardDay: Long = -1L,
    val streak: Int = 0,
    /** Epoch millis of the last game tick that was persisted. */
    val updatedAt: Long = 0L,
) {
    val activePlant: PlantState
        get() = plants.firstOrNull { it.id == activePlantId } ?: plants.first()

    val activeIndex: Int get() = plants.indexOf(activePlant)

    fun isLampActive(nowMillis: Long) = nowMillis < lampUntil

    fun updateActive(transform: (PlantState) -> PlantState): PlayerState {
        val active = activePlant
        return copy(plants = plants.map { if (it.id == active.id) transform(it) else it })
    }

    companion object {
        const val MAX_PLANTS = 5
    }
}
