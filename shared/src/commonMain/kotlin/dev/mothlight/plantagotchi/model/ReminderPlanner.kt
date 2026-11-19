package dev.mothlight.plantagotchi.model

import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.platform.Reminder
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.app_name
import dev.mothlight.plantagotchi.resources.notif_fertilizer
import dev.mothlight.plantagotchi.resources.notif_light
import dev.mothlight.plantagotchi.resources.notif_love
import dev.mothlight.plantagotchi.resources.notif_title
import dev.mothlight.plantagotchi.resources.notif_water
import dev.mothlight.plantagotchi.resources.species_cactus
import dev.mothlight.plantagotchi.resources.species_bonsai
import dev.mothlight.plantagotchi.resources.species_fern
import dev.mothlight.plantagotchi.resources.species_flytrap
import dev.mothlight.plantagotchi.resources.species_pothos
import dev.mothlight.plantagotchi.resources.species_hearts
import dev.mothlight.plantagotchi.resources.species_monstera
import dev.mothlight.plantagotchi.resources.species_sunflower
import dev.mothlight.plantagotchi.resources.species_tulip
import dev.mothlight.plantagotchi.resources.species_sprout
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString

/** A reminder before its texts are resolved. */
data class PlannedReminder(val need: Need?, val species: Species?, val delaySeconds: Long)

/** Predicts when needs drop low while the app is closed and turns that into notifications. */
class ReminderPlanner(
    private val texts: suspend (PlannedReminder) -> Pair<String, String> = ::defaultTexts,
) {
    suspend fun plan(state: PlayerState, today: Long, nowMillis: Long, timeZone: TimeZone): List<Reminder> =
        planDelays(state, today, nowMillis, timeZone).mapIndexed { i, planned ->
            val (title, body) = texts(planned)
            Reminder(id = (planned.need?.ordinal ?: 9) + 1, delaySeconds = planned.delaySeconds, title = title, body = body)
        }

    /** Pure part: earliest time per need across all living plants. The daily chest is a nudge ([NudgePlanner]). */
    fun planDelays(state: PlayerState, today: Long, nowMillis: Long, timeZone: TimeZone): List<PlannedReminder> {
        val result = mutableListOf<PlannedReminder>()
        Need.entries.forEach { need ->
            state.plants.filterNot { it.dead }.mapNotNull { plant ->
                val (value, decay) = when (need) {
                    Need.WATER -> plant.water to GameRules.WATER_DECAY * plant.species.waterDecay
                    Need.LIGHT -> plant.lux to GameRules.LUX_DECAY * plant.species.luxDecay
                    Need.LOVE -> plant.love to GameRules.LOVE_DECAY * plant.species.loveDecay
                    Need.FERTILIZER -> plant.fertilizer to GameRules.FERTILIZER_DECAY * plant.species.fertilizerDecay
                }
                val seconds = if (value <= LOW) MIN_DELAY_SECONDS else ((value - LOW) / decay).toLong()
                plant.species to seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
            }.minByOrNull { it.second }?.let { (species, seconds) ->
                result += PlannedReminder(need, species, seconds)
            }
        }
        return result
    }

    companion object {
        const val LOW = 20.0
        const val MIN_DELAY_SECONDS = 30 * 60L
        const val MAX_DELAY_SECONDS = 7 * 24 * 3600L

        suspend fun speciesName(species: Species): String = getString(
            when (species) {
                Species.SPROUT -> Res.string.species_sprout
                Species.CACTUS -> Res.string.species_cactus
                Species.FERN -> Res.string.species_fern
                Species.SUNFLOWER -> Res.string.species_sunflower
                Species.TULIP -> Res.string.species_tulip
                Species.MONSTERA -> Res.string.species_monstera
                Species.BONSAI -> Res.string.species_bonsai
                Species.FLYTRAP -> Res.string.species_flytrap
                Species.POTHOS -> Res.string.species_pothos
                Species.HEARTS -> Res.string.species_hearts
            }
        )

        private suspend fun defaultTexts(r: PlannedReminder): Pair<String, String> {
            if (r.need == null || r.species == null) return getString(Res.string.app_name) to ""
            val name = speciesName(r.species)
            val body = getString(
                when (r.need) {
                    Need.WATER -> Res.string.notif_water
                    Need.LIGHT -> Res.string.notif_light
                    Need.LOVE -> Res.string.notif_love
                    Need.FERTILIZER -> Res.string.notif_fertilizer
                },
                name,
            )
            return getString(Res.string.notif_title, name) to body
        }
    }
}
