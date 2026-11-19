package dev.mothlight.plantagotchi.data

import com.russhwolf.settings.Settings

/** Device-local preferences (not synced). */
data class AppSettingsState(
    val sound: Boolean = true,
    val music: Boolean = true,
    val ambience: Boolean = true,
    val notifications: Boolean = true,
    /** Friendly hints to come back: daily chest, grown plants, weather, a hello from the plants. */
    val nudges: Boolean = true,
    val cameraLight: Boolean = false,
    /** Falling leaves and petals of the season. */
    val seasonParticles: Boolean = true,
)

class AppSettings(private val settings: Settings = Settings()) {
    fun load() = AppSettingsState(
        sound = settings.getBoolean(SOUND, true),
        music = settings.getBoolean(MUSIC, true),
        ambience = settings.getBoolean(AMBIENCE, true),
        notifications = settings.getBoolean(NOTIFICATIONS, true),
        nudges = settings.getBoolean(NUDGES, true),
        cameraLight = settings.getBoolean(CAMERA_LIGHT, false),
        seasonParticles = settings.getBoolean(SEASON_PARTICLES, true),
    )

    fun save(state: AppSettingsState) {
        settings.putBoolean(SOUND, state.sound)
        settings.putBoolean(MUSIC, state.music)
        settings.putBoolean(AMBIENCE, state.ambience)
        settings.putBoolean(NOTIFICATIONS, state.notifications)
        settings.putBoolean(NUDGES, state.nudges)
        settings.putBoolean(CAMERA_LIGHT, state.cameraLight)
        settings.putBoolean(SEASON_PARTICLES, state.seasonParticles)
    }

    private companion object {
        const val SOUND = "settings_sound"
        const val MUSIC = "settings_music"
        const val AMBIENCE = "settings_ambience"
        const val NOTIFICATIONS = "settings_notifications"
        const val NUDGES = "settings_nudges"
        const val CAMERA_LIGHT = "settings_camera_light"
        const val SEASON_PARTICLES = "settings_season_particles"
    }
}
