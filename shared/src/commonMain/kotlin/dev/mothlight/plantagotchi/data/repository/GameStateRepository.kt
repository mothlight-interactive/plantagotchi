package dev.mothlight.plantagotchi.data.repository

import dev.mothlight.plantagotchi.data.state.PlayerState

interface GameStateRepository {
    /** Returns the stored state, or null if this is a new player. */
    suspend fun load(): PlayerState?

    suspend fun save(state: PlayerState)
}
