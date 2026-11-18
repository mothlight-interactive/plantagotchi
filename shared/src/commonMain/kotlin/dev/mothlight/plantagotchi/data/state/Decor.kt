package dev.mothlight.plantagotchi.data.state

import kotlinx.serialization.Serializable

/** Landscape behind the plants. */
@Serializable
enum class Scenery(val price: Int) {
    MOUNTAINS(0),
    DESERT(150),
    BEACH(200),
    FOREST(150),
    CITY(250),
    ZEN(300),

    /**
     * A cozy room: window with the city outside (weather shows through the glass), windowsill,
     * shelf, desk, hanging planter. Plants stand on [RoomSpot]s and can be moved between them.
     */
    ROOM(250),
}

/** Places for plants in the [Scenery.ROOM], in the order new plants fill them. */
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

/** What the pots stand on. */
@Serializable
enum class Ground(val price: Int) {
    GRASS(0),
    SAND(80),
    STONE(100),
    WOOD(120),
    SNOW(100),
}
