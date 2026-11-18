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

    /** Indoors: a wall with a window; the sky and weather show through the glass. */
    WINDOW(250),
}

/** What the pots stand on. */
@Serializable
enum class Ground(val price: Int) {
    GRASS(0),
    SAND(80),
    STONE(100),
    WOOD(120),
    SNOW(100),

    /** Wooden windowsill with a radiator below. */
    SILL(100),
}
