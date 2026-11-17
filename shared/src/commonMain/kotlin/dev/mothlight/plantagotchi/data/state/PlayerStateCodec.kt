package dev.mothlight.plantagotchi.data.state

import dev.mothlight.plantagotchi.data.shop.PotId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** JSON encoding of [PlayerState], including migration of the single-plant format (v2.0). */
object PlayerStateCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: PlayerState): String = json.encodeToString(PlayerState.serializer(), state)

    fun decode(text: String): PlayerState {
        val obj = json.parseToJsonElement(text).jsonObject
        val migrated = if ("plants" in obj) obj else migrateSinglePlant(obj)
        return json.decodeFromJsonElement(PlayerState.serializer(), migrated)
    }

    private fun migrateSinglePlant(old: JsonObject): JsonObject {
        fun num(key: String, default: Double) = old[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: default
        val pot = old["selectedPot"]?.jsonPrimitive?.contentOrNull ?: PotId.DEFAULT
        val plant = PlantState(
            lux = num("lux", 100.0),
            water = num("water", 100.0),
            love = num("love", 0.0),
            fertilizer = num("fertilizer", 100.0),
            pot = pot,
        )
        return buildJsonObject {
            old.forEach { (k, v) ->
                if (k !in setOf("lux", "water", "love", "fertilizer", "selectedPot")) put(k, v)
            }
            put("plants", buildJsonArray { add(json.encodeToJsonElement(PlantState.serializer(), plant)) })
            put("activePlantId", plant.id)
        }
    }
}
