package dev.mothlight.plantagotchi.data.repository

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.state.PlayerState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stores the game state in the Supabase table `game_state` (see supabase/migrations).
 * Each device signs in anonymously; row level security restricts access to the own row.
 */
class SupabaseGameStateRepository(private val client: SupabaseClient) : GameStateRepository {

    private suspend fun userId(): String {
        client.auth.awaitInitialization()
        client.auth.currentUserOrNull()?.let { return it.id }
        client.auth.signInAnonymously()
        return requireNotNull(client.auth.currentUserOrNull()).id
    }

    override suspend fun load(): PlayerState? {
        val uid = userId()
        return client.from(TABLE)
            .select { filter { eq("player_id", uid) } }
            .decodeSingleOrNull<GameStateRow>()
            ?.toPlayerState()
    }

    override suspend fun save(state: PlayerState) {
        client.from(TABLE).upsert(GameStateRow.from(userId(), state))
    }

    private companion object {
        const val TABLE = "game_state"
    }
}

@Serializable
internal data class GameStateRow(
    @SerialName("player_id") val playerId: String,
    val lux: Double,
    val water: Double,
    val love: Double,
    val fertilizer: Double,
    val coins: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    @SerialName("updated_at_ms") val updatedAt: Long,
) {
    fun toPlayerState() = PlayerState(
        lux = lux,
        water = water,
        love = love,
        fertilizer = fertilizer,
        coins = coins,
        lastPosition = GeoPosition(latitude, longitude, altitude),
        updatedAt = updatedAt,
    )

    companion object {
        fun from(playerId: String, s: PlayerState) = GameStateRow(
            playerId = playerId,
            lux = s.lux,
            water = s.water,
            love = s.love,
            fertilizer = s.fertilizer,
            coins = s.coins,
            latitude = s.lastPosition.latitude,
            longitude = s.lastPosition.longitude,
            altitude = s.lastPosition.altitude,
            updatedAt = s.updatedAt,
        )
    }
}
