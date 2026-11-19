package dev.mothlight.plantagotchi.data.state

/** Moving plants around: spots in the room, order side by side outdoors. */
object Arrangement {
    /** Whether plant [index] may be put on [spot] (see [RoomSpot.accepts]). */
    fun canMove(state: PlayerState, index: Int, spot: RoomSpot): Boolean =
        index in state.plants.indices && spot.accepts(state.plants[index].species)

    /**
     * Puts plant [index] on [spot] in the room; a plant already there takes its old spot, or the
     * first free one if it may not be there (a standing plant never ends up in the planter).
     */
    fun move(state: PlayerState, index: Int, spot: RoomSpot): PlayerState {
        if (!canMove(state, index, spot)) return state
        val current = RoomSpot.assign(state.plants)
        val other = current.indexOf(spot)
        val old = current[index]
        val otherSpot = when {
            other < 0 -> null
            old.accepts(state.plants[other].species) -> old
            else -> RoomSpot.entries.first { it != spot && it !in current && it.accepts(state.plants[other].species) }
        }
        val plants = state.plants.mapIndexed { i, plant ->
            when (i) {
                index -> plant.copy(spot = spot)
                other -> plant.copy(spot = otherSpot)
                else -> plant.copy(spot = current[i])
            }
        }
        return state.copy(plants = plants)
    }

    /** Swaps the places of plants [from] and [to] in the row (outdoor sceneries). */
    fun swap(state: PlayerState, from: Int, to: Int): PlayerState {
        if (from == to || from !in state.plants.indices || to !in state.plants.indices) return state
        val plants = state.plants.toMutableList()
        plants[from] = state.plants[to]
        plants[to] = state.plants[from]
        return state.copy(plants = plants)
    }
}
