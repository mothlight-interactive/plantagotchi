package dev.mothlight.plantagotchi.platform

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

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

enum class Sfx(val file: String) {
    COIN("coin"), PET("pet"), WATER("water"), BUY("buy"), LEVEL_UP("levelup"),
    REWARD("reward"), TAP("tap"), WITHER("wither"),
}

interface SoundPlayer {
    /** Called once with the decoded WAV bytes of every effect. */
    suspend fun load(sounds: Map<Sfx, ByteArray>)
    fun play(sfx: Sfx)
}

/** What home screen widgets show. */
@Serializable
data class WidgetSnapshot(
    val species: String,
    val stage: Int,
    val dead: Boolean,
    val lux: Int,
    val water: Int,
    val love: Int,
    val fertilizer: Int,
    val coins: Int,
    val updatedAt: Long,
)

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
    override suspend fun load(sounds: Map<Sfx, ByteArray>) = Unit
    override fun play(sfx: Sfx) = Unit
}

object NoWidget : WidgetBridge {
    override fun update(snapshot: WidgetSnapshot) = Unit
}
