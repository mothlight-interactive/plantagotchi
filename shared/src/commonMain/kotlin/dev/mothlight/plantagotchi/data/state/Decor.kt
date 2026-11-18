package dev.mothlight.plantagotchi.data.state

import kotlinx.serialization.Serializable

/**
 * A scene: the landscape behind the plants together with what they stand on. Sold as one unit in
 * the shop. Outdoor scenes have their natural [ground]; indoor scenes ([indoor]) draw their own
 * floor and place the plants on [RoomSpot]s. The name stays `Scenery` for save compatibility.
 */
@Serializable
enum class Scenery(val price: Int, val ground: Ground?) {
    MOUNTAINS(100, Ground.GRASS),
    DESERT(150, Ground.SAND),
    BEACH(200, Ground.SAND),
    FOREST(150, Ground.GRASS),
    CITY(250, Ground.STONE),
    ZEN(300, Ground.WOOD),

    /**
     * The default scene, free for everyone: window with the city outside (weather shows through
     * the glass), windowsill, shelf, desk, hanging planter and a cat.
     */
    ROOM(0, null),

    /** Stone fireplace with a crackling fire, two rainy windows, armchairs and a reading lamp. */
    FIREPLACE(300, null),

    /** A little plant shop: string lights, counter, shelves with goods and a chalkboard. */
    PLANT_SHOP(350, null);

    /** Drawn as a room with its own floor; plants stand on [RoomSpot]s. */
    val indoor: Boolean get() = ground == null

    companion object {
        /** Everyone starts here, see [PlayerState.withDefaultScene]. */
        val DEFAULT = ROOM

        /** Order in the shop: the indoor scenes first. */
        val shopOrder: List<Scenery> = listOf(ROOM, FIREPLACE, PLANT_SHOP, MOUNTAINS, FOREST, DESERT, BEACH, CITY, ZEN)
    }
}

/**
 * Places for plants in an indoor scene, in the order new plants fill them. The names come from the
 * cozy room; other indoor scenes map them to their own places (see `IndoorScene.anchor`).
 */
@Serializable
enum class RoomSpot {
    SILL_LEFT, SILL_RIGHT, FLOOR_LEFT, DESK, SHELF, HANGING, FLOOR_RIGHT;

    companion object {
        /** Each plant's spot: its saved one if free and valid, otherwise the first free spot. */
        fun assign(plants: List<PlantState>): List<RoomSpot> {
            val taken = mutableSetOf<RoomSpot>()
            val result = arrayOfNulls<RoomSpot>(plants.size)
            plants.forEachIndexed { i, plant ->
                val wanted = plant.spot
                if (wanted != null && taken.add(wanted)) result[i] = wanted
            }
            plants.indices.forEach { i ->
                if (result[i] == null) result[i] = entries.first { it !in taken }.also { taken += it }
            }
            return result.map { it!! }
        }
    }
}

/** What the pots stand on outdoors; part of a [Scenery], no longer sold on its own. */
@Serializable
enum class Ground { GRASS, SAND, STONE, WOOD, SNOW }
