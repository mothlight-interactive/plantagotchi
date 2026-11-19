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
    val ownedScenery: Set<Scenery> = setOf(Scenery.DEFAULT),
    val scenery: Scenery = Scenery.DEFAULT,
    /** Unused since grounds belong to the scene ([Scenery.ground]); kept so old saves load. */
    val ownedGrounds: Set<Ground> = setOf(Ground.GRASS),
    /** Kept in sync with [Scenery.ground] for older readers; draw [Scenery.ground] instead. */
    val ground: Ground = Ground.GRASS,
    /** Version of the scene defaults applied to this save, see [withDefaultScene]. */
    val sceneVersion: Int = SCENE_VERSION,
    /** The string lights of the plant shop: a weak light for all plants there while switched on. */
    val stringLights: Boolean = true,
    /** Before 2.2 the grow lamp lit all plants; now it is per plant ([PlantState.lampUntil]). */
    val lampUntil: Long = 0L,
    /** Local epoch day of the last claimed daily reward, -1 if never. */
    val lastRewardDay: Long = -1L,
    val streak: Int = 0,
    /** Local epoch days with a claimed daily reward, oldest first, at most [DailyReward.HISTORY_DAYS]. */
    val rewardDays: List<Long> = emptyList(),
    /** Local epoch day of [petCoinsToday]; petting coins are capped per day across all plants. */
    val petCoinsDay: Long = -1L,
    val petCoinsToday: Int = 0,
    /** Epoch millis of the last game tick that was persisted. */
    val updatedAt: Long = 0L,
) {
    val activePlant: PlantState
        get() = plants.firstOrNull { it.id == activePlantId } ?: plants.first()

    val activeIndex: Int get() = plants.indexOf(activePlant)

    /** Moves a lamp bought for all plants (older saves) onto every plant. */
    fun withPerPlantLamps(): PlayerState =
        if (lampUntil <= 0L) this
        else copy(plants = plants.map { it.copy(lampUntil = maxOf(it.lampUntil, lampUntil)) }, lampUntil = 0L)

    /**
     * Applies the scene defaults once per [SCENE_VERSION]: since 2.2 everyone starts in the cozy
     * room (free for all). Owned scenes stay owned. Saves without the field decode with version 0.
     */
    fun withDefaultScene(): PlayerState =
        if (sceneVersion >= SCENE_VERSION) this
        else copy(
            scenery = Scenery.DEFAULT,
            ownedScenery = ownedScenery + Scenery.DEFAULT,
            sceneVersion = SCENE_VERSION,
        )

    fun updateActive(transform: (PlantState) -> PlantState): PlayerState {
        val active = activePlant
        return copy(plants = plants.map { if (it.id == active.id) transform(it) else it })
    }

    companion object {
        const val MAX_PLANTS = 5

        /** Bump to switch every player once more to [Scenery.DEFAULT]. */
        const val SCENE_VERSION = 1
    }
}
