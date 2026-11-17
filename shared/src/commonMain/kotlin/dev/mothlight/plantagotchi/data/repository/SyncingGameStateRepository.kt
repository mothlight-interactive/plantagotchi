package dev.mothlight.plantagotchi.data.repository

import dev.mothlight.plantagotchi.data.state.PlayerState
import kotlinx.coroutines.CancellationException

/**
 * Local-first repository: always writes locally, mirrors to [remote] on a best-effort basis.
 * On load the most recently updated state wins.
 */
class SyncingGameStateRepository(
    private val local: GameStateRepository,
    private val remote: GameStateRepository?,
    private val onRemoteError: (Throwable) -> Unit = {},
) : GameStateRepository {

    override suspend fun load(): PlayerState? {
        val localState = local.load()
        val remoteState = remote?.let { safely { it.load() } }
        return listOfNotNull(localState, remoteState).maxByOrNull { it.updatedAt }
    }

    override suspend fun save(state: PlayerState) {
        local.save(state)
        remote?.let { safely { it.save(state) } }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onRemoteError(e)
        null
    }
}
