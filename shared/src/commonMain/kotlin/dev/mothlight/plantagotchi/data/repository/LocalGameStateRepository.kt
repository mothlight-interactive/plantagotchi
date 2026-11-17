package dev.mothlight.plantagotchi.data.repository

import com.russhwolf.settings.Settings
import dev.mothlight.plantagotchi.data.state.PlayerState
import kotlinx.serialization.json.Json

/** Persists the game state on device (SharedPreferences / NSUserDefaults). */
class LocalGameStateRepository(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GameStateRepository {

    override suspend fun load(): PlayerState? =
        settings.getStringOrNull(KEY)?.let { runCatching { json.decodeFromString<PlayerState>(it) }.getOrNull() }

    override suspend fun save(state: PlayerState) {
        settings.putString(KEY, json.encodeToString(state))
    }

    private companion object {
        const val KEY = "player_state"
    }
}
