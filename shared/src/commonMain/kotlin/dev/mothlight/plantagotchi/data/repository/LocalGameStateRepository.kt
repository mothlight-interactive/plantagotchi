package dev.mothlight.plantagotchi.data.repository

import com.russhwolf.settings.Settings
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.PlayerStateCodec

/** Persists the game state on device (SharedPreferences / NSUserDefaults). */
class LocalGameStateRepository(
    private val settings: Settings = Settings(),
) : GameStateRepository {

    override suspend fun load(): PlayerState? =
        settings.getStringOrNull(KEY)?.let { runCatching { PlayerStateCodec.decode(it) }.getOrNull() }

    override suspend fun save(state: PlayerState) {
        settings.putString(KEY, PlayerStateCodec.encode(state))
    }

    private companion object {
        const val KEY = "player_state"
    }
}
