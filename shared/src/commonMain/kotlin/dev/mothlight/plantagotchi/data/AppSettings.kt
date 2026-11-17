package dev.mothlight.plantagotchi.data

import com.russhwolf.settings.Settings

/** Device-local preferences (not synced). */
data class AppSettingsState(
    val sound: Boolean = true,
    val notifications: Boolean = true,
    val cameraLight: Boolean = false,
)

class AppSettings(private val settings: Settings = Settings()) {
    fun load() = AppSettingsState(
        sound = settings.getBoolean(SOUND, true),
        notifications = settings.getBoolean(NOTIFICATIONS, true),
        cameraLight = settings.getBoolean(CAMERA_LIGHT, false),
    )

    fun save(state: AppSettingsState) {
        settings.putBoolean(SOUND, state.sound)
        settings.putBoolean(NOTIFICATIONS, state.notifications)
        settings.putBoolean(CAMERA_LIGHT, state.cameraLight)
    }

    private companion object {
        const val SOUND = "settings_sound"
        const val NOTIFICATIONS = "settings_notifications"
        const val CAMERA_LIGHT = "settings_camera_light"
    }
}
