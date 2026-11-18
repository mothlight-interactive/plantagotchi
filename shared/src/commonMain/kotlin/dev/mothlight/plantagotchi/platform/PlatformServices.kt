package dev.mothlight.plantagotchi.platform

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.coroutines.flow.StateFlow
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ambient light sensor. [lux] is null if no usable sensor is active. */
interface LightSensor {
    val lux: StateFlow<Float?>
    /** True if light can be measured with the camera (iOS, which has no public light sensor API). */
    val supportsCamera: Boolean get() = false
    fun setCameraEnabled(enabled: Boolean) = Unit
    fun start()
    fun stop()
}

interface LocationProvider {
    /** Returns the current position or null if unavailable / permission denied. */
    suspend fun currentPosition(): GeoPosition?
}

data class Reminder(val id: Int, val delaySeconds: Long, val title: String, val body: String)

/** Local notifications, e.g. "your plant is thirsty". */
interface NotificationScheduler {
    suspend fun requestPermission(): Boolean = true
    fun schedule(reminders: List<Reminder>)
    fun cancelAll()
}

/** One-shot sound effects, files in composeResources/files/sfx. */
enum class Sfx(val file: String) {
    COIN("coin"), PET("pet"), WATER("water"), BUY("buy"), LEVEL_UP("levelup"),
    REWARD("reward"), TAP("tap"), WITHER("wither"),
    FERTILIZER("fertilizer"), LAMP("lamp"), DECOR("decor"), SWITCH("switch"), SNAP("snap"),
    ERROR("error"), OPEN("open"), CLOSE("close"), THUNDER("thunder");

    val asset: String get() = "sfx/$file"
}

/**
 * Plays sounds by asset name, e.g. "sfx/coin", "voice/cactus_happy", "music/day".
 * One-shots via [play]; long sounds loop via [setLoop].
 */
interface SoundPlayer {
    /** Called once with the WAV bytes of every asset. */
    suspend fun load(sounds: Map<String, ByteArray>)
    fun play(asset: String, volume: Float = 1f)
    /** Loops [asset] at [volume]; 0 pauses it. */
    fun setLoop(asset: String, volume: Float) = Unit
    /** Pauses all loops (app in background). */
    fun pauseAll() = Unit
}

fun SoundPlayer.play(sfx: Sfx, volume: Float = 1f) = play(sfx.asset, volume)

/** One plant as home screen widgets show it. Needs are 0..100. */
@Serializable
data class WidgetPlant(
    val id: String,
    /** Localized species name, numbered when the species appears more than once ("Tulip 2"). */
    val name: String,
    val species: String,
    val stage: Int,
    val dead: Boolean,
    val lux: Int,
    val water: Int,
    val love: Int,
    val fertilizer: Int,
)

/** What home screen widgets show: every plant, the one active in the app and the coins. */
@Serializable
data class WidgetSnapshot(
    val plants: List<WidgetPlant>,
    val activeId: String,
    val coins: Int,
    val updatedAt: Long,
) {
    val active: WidgetPlant? get() = plants.firstOrNull { it.id == activeId } ?: plants.firstOrNull()

    fun plant(id: String?): WidgetPlant? = plants.firstOrNull { it.id == id }

    /** The plant [steps] positions after [id] (wraps around), for the widget arrows. */
    fun step(id: String?, steps: Int): WidgetPlant? {
        if (plants.isEmpty()) return null
        val from = plants.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: plants.indexOf(active)
        return plants[((from + steps) % plants.size + plants.size) % plants.size]
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String?): WidgetSnapshot? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        fun of(player: PlayerState, now: Long, speciesName: (Species) -> String): WidgetSnapshot {
            val counts = player.plants.groupingBy { it.species }.eachCount()
            val seen = mutableMapOf<Species, Int>()
            val plants = player.plants.map { plant ->
                val n = (seen[plant.species] ?: 0) + 1
                seen[plant.species] = n
                val base = speciesName(plant.species)
                WidgetPlant(
                    id = plant.id,
                    name = if ((counts[plant.species] ?: 0) > 1) "$base $n" else base,
                    species = plant.species.name,
                    stage = plant.stage,
                    dead = plant.dead,
                    lux = plant.lux.toInt(),
                    water = plant.water.toInt(),
                    love = plant.love.toInt(),
                    fertilizer = plant.fertilizer.toInt(),
                )
            }
            return WidgetSnapshot(plants, player.activePlant.id, player.coins, now)
        }
    }
}

interface WidgetBridge {
    fun update(snapshot: WidgetSnapshot)
}

class PlatformServices(
    val lightSensor: LightSensor,
    val locationProvider: LocationProvider,
    val notifications: NotificationScheduler = NoNotifications,
    val sound: SoundPlayer = NoSound,
    val widget: WidgetBridge = NoWidget,
)

object NoNotifications : NotificationScheduler {
    override fun schedule(reminders: List<Reminder>) = Unit
    override fun cancelAll() = Unit
}

object NoSound : SoundPlayer {
    override suspend fun load(sounds: Map<String, ByteArray>) = Unit
    override fun play(asset: String, volume: Float) = Unit
}

object NoWidget : WidgetBridge {
    override fun update(snapshot: WidgetSnapshot) = Unit
}
