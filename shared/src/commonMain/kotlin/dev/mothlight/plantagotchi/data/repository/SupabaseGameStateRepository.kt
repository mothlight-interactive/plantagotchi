package dev.mothlight.plantagotchi.data.repository

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.state.FriendCode
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Signs in anonymously once and returns the user id. */
internal suspend fun SupabaseClient.ensureUserId(): String {
    auth.awaitInitialization()
    auth.currentUserOrNull()?.let { return it.id }
    auth.signInAnonymously()
    return requireNotNull(auth.currentUserOrNull()).id
}

/**
 * Stores the game state in the Supabase table `game_state` (see supabase/migrations).
 * Each device signs in anonymously; row level security restricts access to the own row.
 */
class SupabaseGameStateRepository(private val client: SupabaseClient) : GameStateRepository {

    override suspend fun load(): PlayerState? {
        val uid = client.ensureUserId()
        return client.from(TABLE)
            .select { filter { eq("player_id", uid) } }
            .decodeSingleOrNull<GameStateRow>()
            ?.toPlayerState()
    }

    override suspend fun save(state: PlayerState) {
        client.from(TABLE).upsert(GameStateRow.from(client.ensureUserId(), state))
    }

    private companion object {
        const val TABLE = "game_state"
    }
}

@Serializable
internal data class GameStateRow(
    @SerialName("player_id") val playerId: String,
    // Active plant, kept as columns for friends and the v2.0 schema.
    val lux: Double,
    val water: Double,
    val love: Double,
    val fertilizer: Double,
    val coins: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    @SerialName("owned_pots") val ownedPots: List<String> = listOf(PotId.DEFAULT),
    @SerialName("selected_pot") val selectedPot: String = PotId.DEFAULT,
    @SerialName("lamp_until_ms") val lampUntil: Long = 0L,
    val plants: List<PlantState> = emptyList(),
    @SerialName("active_plant_id") val activePlantId: String = "",
    @SerialName("active_species") val activeSpecies: String = Species.SPROUT.name,
    @SerialName("active_stage") val activeStage: Int = 0,
    @SerialName("active_dead") val activeDead: Boolean = false,
    @SerialName("last_reward_day") val lastRewardDay: Long = -1L,
    val streak: Int = 0,
    @SerialName("friend_code") val friendCode: String? = null,
    val friends: List<String> = emptyList(),
    @SerialName("owned_scenery") val ownedScenery: List<String> = listOf(Scenery.MOUNTAINS.name),
    val scenery: String = Scenery.MOUNTAINS.name,
    @SerialName("owned_grounds") val ownedGrounds: List<String> = listOf(Ground.GRASS.name),
    val ground: String = Ground.GRASS.name,
    @SerialName("updated_at_ms") val updatedAt: Long,
) {
    fun toPlayerState(): PlayerState {
        val plantList = plants.ifEmpty {
            listOf(PlantState(lux = lux, water = water, love = love, fertilizer = fertilizer, pot = selectedPot))
        }
        return PlayerState(
            plants = plantList,
            activePlantId = activePlantId.ifEmpty { plantList.first().id },
            coins = coins,
            lastPosition = GeoPosition(latitude, longitude, altitude),
            ownedPots = ownedPots.toSet() + PotId.DEFAULT,
            lampUntil = lampUntil,
            lastRewardDay = lastRewardDay,
            streak = streak,
            friendCode = friendCode ?: FriendCode.generate(),
            friends = friends,
            ownedScenery = ownedScenery.mapNotNull { n -> Scenery.entries.firstOrNull { it.name == n } }.toSet() + Scenery.MOUNTAINS,
            scenery = Scenery.entries.firstOrNull { it.name == scenery } ?: Scenery.MOUNTAINS,
            ownedGrounds = ownedGrounds.mapNotNull { n -> Ground.entries.firstOrNull { it.name == n } }.toSet() + Ground.GRASS,
            ground = Ground.entries.firstOrNull { it.name == ground } ?: Ground.GRASS,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun from(playerId: String, s: PlayerState): GameStateRow {
            val active = s.activePlant
            return GameStateRow(
                playerId = playerId,
                lux = active.lux,
                water = active.water,
                love = active.love,
                fertilizer = active.fertilizer,
                coins = s.coins,
                latitude = s.lastPosition.latitude,
                longitude = s.lastPosition.longitude,
                altitude = s.lastPosition.altitude,
                ownedPots = s.ownedPots.sorted(),
                selectedPot = active.pot,
                lampUntil = s.lampUntil,
                plants = s.plants,
                activePlantId = active.id,
                activeSpecies = active.species.name,
                activeStage = active.stage,
                activeDead = active.dead,
                lastRewardDay = s.lastRewardDay,
                streak = s.streak,
                friendCode = s.friendCode,
                friends = s.friends,
                ownedScenery = s.ownedScenery.map { it.name }.sorted(),
                scenery = s.scenery.name,
                ownedGrounds = s.ownedGrounds.map { it.name }.sorted(),
                ground = s.ground.name,
                updatedAt = s.updatedAt,
            )
        }
    }
}
