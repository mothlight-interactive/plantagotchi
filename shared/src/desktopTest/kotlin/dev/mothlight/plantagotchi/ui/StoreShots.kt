package dev.mothlight.plantagotchi.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.mothlight.plantagotchi.data.state.*
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.dev.AppEnv
import dev.mothlight.plantagotchi.model.GameUiState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.test.Test

/** Raw frames for the store screenshots; runs only with STORE_SHOTS set (see docs/store-listing.md). */
class StoreShots {
    private val now = 1_782_043_200_000L
    private val full = GameRules.STAGE_3_GROWTH

    private fun p(id: String, sp: Species, pot: String, water: Double = 80.0, love: Double = 70.0, lux: Double = 75.0, fert: Double = 65.0, g: Double = full) =
        PlantState(id = id, species = sp, growth = g, pot = pot, water = water, love = love, lux = lux, fertilizer = fert)

    private fun weather(state: WeatherState, temp: Double, hour: Int = 12) = CurrentWeather.Default.copy(
        facts = CurrentWeather.Default.facts.copy(state = state, temperature = temp, apparentTemperature = temp,
            cloudCover = if (state == WeatherState.CLEAR_SKY) 0.0 else 70.0, windSpeed = 8.0),
        localTime = kotlinx.datetime.LocalTime(hour, 0),
        isDay = hour in 7..18,
    )

    @Test
    fun shots() {
        val out = System.getenv("STORE_SHOTS") ?: return
        val w = 1206; val h = 2622; val d = 3f
        val scenes = listOf(
            Triple("01", Sheet.NONE, GameUiState(
                loading = false, plantSelected = true, now = now, today = now / 86_400_000L, env = AppEnv.PROD,
                weather = weather(WeatherState.FEW_CLOUDS, 21.0),
                player = PlayerState(scenery = Scenery.ROOM, coins = 340, lastRewardDay = now / 86_400_000L, activePlantId = "a", plants = listOf(
                    p("a", Species.MONSTERA, "pot21", water = 45.0), p("b", Species.POTHOS, "pot32"), p("c", Species.TULIP, "pot13"),
                    p("d", Species.BONSAI, "pot14"), p("e", Species.CACTUS, "pot11"))))),
            Triple("02", Sheet.NONE, GameUiState(
                loading = false, plantSelected = true, now = now, today = now / 86_400_000L, env = AppEnv.PROD, season = Season.WINTER,
                weather = weather(WeatherState.SNOW, -3.0, 17),
                player = PlayerState(scenery = Scenery.FIREPLACE, coins = 520, lastRewardDay = now / 86_400_000L, activePlantId = "t", plants = listOf(
                    p("t", Species.TULIP, "pot23", love = 30.0), p("f", Species.FERN, "pot21"), p("b2", Species.BONSAI, "pot11"), p("h", Species.HEARTS, "pot12"))))),
            Triple("03", Sheet.NONE, GameUiState(
                loading = false, plantSelected = true, now = now, today = now / 86_400_000L, env = AppEnv.PROD,
                weather = weather(WeatherState.LIGHT_RAIN, 14.0),
                player = PlayerState(scenery = Scenery.PLANT_SHOP, coins = 760, lastRewardDay = now / 86_400_000L, activePlantId = "s", plants = listOf(
                    p("s", Species.BONSAI, "pot24", fert = 30.0), p("x", Species.FLYTRAP, "pot31"), p("y", Species.MONSTERA, "pot11"),
                    p("z", Species.POTHOS, "pot21"))))),
            Triple("04", Sheet.NONE, GameUiState(
                loading = false, plantSelected = true, now = now, today = now / 86_400_000L, env = AppEnv.PROD,
                weather = weather(WeatherState.CLEAR_SKY, 27.0),
                player = PlayerState(scenery = Scenery.BEACH, coins = 410, lastRewardDay = now / 86_400_000L, activePlantId = "u", plants = listOf(
                    p("u", Species.SUNFLOWER, "pot21", lux = 95.0), p("v", Species.CACTUS, "pot13"), p("k", Species.TULIP, "pot22"))))),
            Triple("05", Sheet.SHOP, GameUiState(
                loading = false, plantSelected = true, now = now, today = now / 86_400_000L, env = AppEnv.PROD,
                weather = weather(WeatherState.FEW_CLOUDS, 21.0),
                player = PlayerState(scenery = Scenery.ROOM, coins = 340, lastRewardDay = now / 86_400_000L, activePlantId = "a",
                    ownedPots = setOf("pot11", "pot21", "pot32"), plants = listOf(p("a", Species.MONSTERA, "pot21"), p("b", Species.POTHOS, "pot32"))))),
        )
        for (lang in (System.getenv("LANGS") ?: "en").split(",")) {
            Locale.setDefault(Locale.forLanguageTag(lang))
            val dir = File(out, lang).apply { mkdirs() }
            for ((name, sheet, state) in scenes) {
                val scene = ImageComposeScene(w, h, Density(d)) { GameContent(state = state, showSplash = false, initialSheet = sheet) }
                var img: org.jetbrains.skia.Image? = null
                var t = 0L
                repeat(150) { img?.close(); img = scene.render(t); t += 16_666_667L }
                File(dir, "$name.png").writeBytes(img!!.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                img!!.close(); scene.close()
            }
        }
    }
}
