package dev.mothlight.plantagotchi.data.repository

import dev.mothlight.plantagotchi.data.state.Species
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

@Serializable
data class FriendInfo(
    @SerialName("friend_code") val code: String,
    val species: Species = Species.SPROUT,
    val stage: Int = 0,
    val dead: Boolean = false,
    val health: Double = 0.0,
    val water: Double = 0.0,
)

enum class SendWaterResult { SENT, ALREADY_SENT_TODAY, UNKNOWN_CODE, OWN_CODE, OFFLINE }

/** Friends can look at each other's active plant and send water once a day. */
interface FriendsService {
    suspend fun friends(codes: List<String>): List<FriendInfo>
    suspend fun sendWater(code: String): SendWaterResult
    /** Collects water sent by friends and returns the total amount. */
    suspend fun claimGifts(): Double
}

class SupabaseFriendsService(private val client: SupabaseClient) : FriendsService {

    override suspend fun friends(codes: List<String>): List<FriendInfo> {
        if (codes.isEmpty()) return emptyList()
        client.ensureUserId()
        return client.postgrest.rpc(
            "friend_info",
            buildJsonObject { putJsonArray("codes") { codes.forEach { add(it) } } },
        ).decodeList<FriendInfo>()
    }

    override suspend fun sendWater(code: String): SendWaterResult {
        client.ensureUserId()
        val result = client.postgrest.rpc("send_water", buildJsonObject { put("code", code) })
            .data.trim('"')
        return when (result) {
            "sent" -> SendWaterResult.SENT
            "already_sent_today" -> SendWaterResult.ALREADY_SENT_TODAY
            "own_code" -> SendWaterResult.OWN_CODE
            else -> SendWaterResult.UNKNOWN_CODE
        }
    }

    override suspend fun claimGifts(): Double {
        val uid = client.ensureUserId()
        val gifts = client.from("gifts").select { filter { eq("to_player", uid) } }.decodeList<GiftRow>()
        if (gifts.isEmpty()) return 0.0
        client.from("gifts").delete { filter { isIn("id", gifts.map { it.id }) } }
        return gifts.sumOf { it.amount }
    }

    @Serializable
    private data class GiftRow(val id: Long, val amount: Double)
}
