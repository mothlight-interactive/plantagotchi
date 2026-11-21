package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.shop.PotId
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Plant species with different needs. Multipliers scale the base decay rates in [GameRules].
 */
@Serializable
enum class Species(
    val waterDecay: Double,
    val luxDecay: Double,
    /** Extra decay factor below 0 °C. */
    val frostSensitivity: Double,
    /** Extra water decay factor above 30 °C. */
    val heatSensitivity: Double,
    val seedPrice: Int,
    val loveDecay: Double = 1.0,
    val fertilizerDecay: Double = 1.0,
    /** Trailing plants: the only ones that may hang in the planter ([RoomSpot.HANGING]). */
    val hanging: Boolean = false,
    /**
     * Days until fully grown with good care (fertilizer kept full). The order follows the real
     * plants, from a sunflower that is done in a season to a bonsai that takes many years,
     * squeezed into one to seven days so that every plant visibly changes each day.
     */
    val growDays: Double = 1.0,
) {

    SPROUT(waterDecay = 1.0, luxDecay = 1.0, frostSensitivity = 1.5, heatSensitivity = 2.0, seedPrice = 100, growDays = 1.0),
    CACTUS(waterDecay = 0.3, luxDecay = 1.2, frostSensitivity = 2.5, heatSensitivity = 1.0, seedPrice = 150, growDays = 6.0),
    FERN(waterDecay = 1.4, luxDecay = 0.5, frostSensitivity = 1.2, heatSensitivity = 2.5, seedPrice = 150, growDays = 3.5),

    /** Hungry for sun, turns its head to follow it. */
    SUNFLOWER(waterDecay = 1.2, luxDecay = 1.5, frostSensitivity = 2.0, heatSensitivity = 1.0, seedPrice = 180, growDays = 1.5),

    /** Hardy in the cold, opens its bloom during the day. */
    TULIP(waterDecay = 1.0, luxDecay = 1.0, frostSensitivity = 0.8, heatSensitivity = 2.0, seedPrice = 120, growDays = 2.0),

    /** Big leaves, happy in the shade, hates the cold. */
    MONSTERA(waterDecay = 0.8, luxDecay = 0.6, frostSensitivity = 2.5, heatSensitivity = 1.2, seedPrice = 220, growDays = 4.0),

    /** Slow and wise, needs a lot of attention. */
    BONSAI(waterDecay = 1.0, luxDecay = 0.8, frostSensitivity = 1.2, heatSensitivity = 1.5, seedPrice = 300, loveDecay = 2.0, fertilizerDecay = 0.6, growDays = 7.0),

    /** Bog plant: thirsty, but catches its own food. */
    FLYTRAP(waterDecay = 1.6, luxDecay = 1.1, frostSensitivity = 1.5, heatSensitivity = 1.0, seedPrice = 250, fertilizerDecay = 0.3, growDays = 5.0),

    /** Trailing vines, happiest up high in a hanging planter. */
    POTHOS(waterDecay = 1.1, luxDecay = 0.7, frostSensitivity = 1.5, heatSensitivity = 1.5, seedPrice = 200, hanging = true, growDays = 2.5),

    /** String of hearts: thin vines with small heart leaves. A succulent, drinks little, likes it bright. */
    HEARTS(waterDecay = 0.5, luxDecay = 1.1, frostSensitivity = 2.0, heatSensitivity = 1.0, seedPrice = 240, hanging = true, growDays = 3.0);

    /** Factor on the growth per second, see [GameRules.growthPerSecond]. */
    val growthFactor: Double get() = GameRules.FASTEST_GROW_DAYS / growDays
}

/** All needs are percentages in the range 0..100. */
@Serializable
data class PlantState(
    val id: String = newId(),
    val species: Species = Species.SPROUT,
    val lux: Double = 100.0,
    val water: Double = 100.0,
    val love: Double = 0.0,
    val fertilizer: Double = 100.0,
    /** Seconds of good care, drives the growth stage. */
    val growth: Double = 0.0,
    val pot: String = PotId.DEFAULT,
    /** Epoch millis since when the plant has been in critical condition, 0 if it is fine. */
    val criticalSince: Long = 0L,
    val dead: Boolean = false,
    val plantedAt: Long = 0L,
    /** Epoch millis until which this plant's grow lamp is on. */
    val lampUntil: Long = 0L,
    /** Where the plant stands in the [Scenery.ROOM]; null = first free spot. */
    val spot: RoomSpot? = null,
) {
    fun lampOn(nowMillis: Long): Boolean = nowMillis < lampUntil

    /** Average of all needs, 0..100. */
    val health: Double get() = (lux + water + love + fertilizer) / 4.0

    /** 0 = seedling, 1 = young, 2 = grown. */
    val stage: Int get() = when {
        growth >= GameRules.STAGE_3_GROWTH -> 2
        growth >= GameRules.STAGE_2_GROWTH -> 1
        else -> 0
    }

    val isCritical: Boolean get() = !dead && criticalSince > 0L

    companion object {
        fun newId(): String = "p" + Random.nextLong(0, Long.MAX_VALUE).toString(36)
    }
}
