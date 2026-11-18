package dev.mothlight.plantagotchi.data.state

/** Moving plants around: spots in the room, order side by side outdoors. */
object Arrangement {
    /** Puts plant [index] on [spot] in the room; a plant already there takes its old spot. */
    fun move(state: PlayerState, index: Int, spot: RoomSpot): PlayerState {
        if (index !in state.plants.indices) return state
        val current = RoomSpot.assign(state.plants)
        val other = current.indexOf(spot)
        val plants = state.plants.mapIndexed { i, plant ->
            when (i) {
                index -> plant.copy(spot = spot)
                other -> plant.copy(spot = current[index])
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
