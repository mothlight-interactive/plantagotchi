package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.repository.GameStateRepository
import dev.mothlight.plantagotchi.data.repository.SyncingGameStateRepository
import dev.mothlight.plantagotchi.data.state.PlayerState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncingGameStateRepositoryTest {

    private class InMemory(var state: PlayerState? = null, val fail: Boolean = false) : GameStateRepository {
        override suspend fun load(): PlayerState? = if (fail) error("offline") else state
        override suspend fun save(state: PlayerState) {
            if (fail) error("offline")
            this.state = state
        }
    }

    @Test
    fun newestStateWins() = runTest {
        val local = InMemory(PlayerState(coins = 1, updatedAt = 100))
        val remote = InMemory(PlayerState(coins = 2, updatedAt = 200))
        assertEquals(2, SyncingGameStateRepository(local, remote).load()?.coins)
    }

    @Test
    fun remoteFailureFallsBackToLocal() = runTest {
        var errors = 0
        val local = InMemory(PlayerState(coins = 7))
        val repo = SyncingGameStateRepository(local, InMemory(fail = true)) { errors++ }

        assertEquals(7, repo.load()?.coins)
        repo.save(PlayerState(coins = 8))
        assertEquals(8, assertNotNull(local.state).coins)
        assertEquals(2, errors)
    }
}
