package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.ui.particlesystem.ParticleSystem
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationShape
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationSourceEdge
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationsParameters
import dev.mothlight.plantagotchi.ui.particlesystem.drawParticles
import dev.mothlight.plantagotchi.ui.particlesystem.rainParameters
import dev.mothlight.plantagotchi.ui.particlesystem.snowParameters
import kotlinx.datetime.LocalTime
import kotlin.math.sin
import kotlin.random.Random

/** Night tint for mountains and clouds, 0 (day) .. MOUNTAIN_TINT_ALPHA_MAX (night). */
internal fun nightTint(minutes: Float, weather: CurrentWeather): Float {
    val sunrise = weather.sunrise.minutesOfDay()
    val sunset = weather.sunset.minutesOfDay()
    val d = MOUNTAIN_TINT_TRANSITION
    val percent = when {
        minutes < sunrise - d -> 1f
        minutes < sunrise + d -> 1 - (minutes - sunrise + d) / (2 * d)
        minutes < sunset - d -> 0f
        minutes < sunset + d -> (minutes - sunset + d) / (2 * d)
        else -> 1f
    }
    return percent * MOUNTAIN_TINT_ALPHA_MAX
}

internal fun DrawScope.drawSky(scene: SceneState, sprites: SceneSprites, weather: CurrentWeather, minutes: Float) {
    val sunrise = weather.sunrise.minutesOfDay()
    val sunset = weather.sunset.minutesOfDay()
    val d = TRANSITION
    val full = Rect(Offset.Zero, size)

    fun layers(first: ImageBitmap, second: ImageBitmap?, secondAlpha: Float) {
        drawBitmapCrop(first, full)
        if (second != null) drawBitmapCrop(second, full, secondAlpha.coerceIn(0f, 1f))
    }
    when {
        minutes < sunrise - d -> layers(sprites.skyNight, null, 0f)
        minutes < sunrise -> layers(sprites.skyNight, sprites.skySunrise, 1 - (sunrise - minutes) / d)
        minutes < sunrise + d -> layers(sprites.skySunrise, sprites.skyDay, 1 - (sunrise + d - minutes) / d)
        minutes < sunset - d -> layers(sprites.skyDay, null, 0f)
        minutes < sunset -> layers(sprites.skyDay, sprites.skySunset, 1 - (sunset - minutes) / d)
        minutes < sunset + d -> layers(sprites.skySunset, sprites.skyNight, 1 - (sunset + d - minutes) / d)
        else -> layers(sprites.skyNight, null, 0f)
    }

    // Twinkling stars at night
    val night = nightTint(minutes, weather) / MOUNTAIN_TINT_ALPHA_MAX
    if (night > 0f) {
        val random = Random(42)
        repeat(40) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height * 0.45f
            val twinkle = 0.5f + 0.5f * sin(scene.time * (1f + random.nextFloat() * 2f) + it)
            drawRect(Color.White, Offset(x, y), Size(2f, 2f) * (scene.layout?.dp ?: 1f) * 0.8f, alpha = night * twinkle * 0.8f)
        }
    }

    // Sun and moon travel along a parabola across the sky.
    val dp = scene.layout?.dp ?: 1f
    val imageSize = CELESTIAL_SIZE * dp
    val width = size.width
    val height = size.height
    val sunProgress = (minutes - (sunrise - d)) / ((sunset + d) - (sunrise - d))
    val nightElapsed = when {
        minutes < sunrise -> minutes + MINUTES_PER_DAY - sunset
        minutes > sunset -> minutes - sunset
        else -> 0f
    }
    val moonProgress = nightElapsed / (MINUTES_PER_DAY - sunset + sunrise)
    val x2 = width - imageSize
    val a = -1 * (height + imageSize) / (((width / 2) - width - imageSize) * (width / 2))
    fun arc(progress: Float): Offset {
        val x = (width - imageSize) * (1 - progress.coerceIn(0f, 1f))
        val y = a * (x - x2) * x + height - imageSize - CELESTIAL_BOTTOM_MARGIN
        return Offset(x, y)
    }
    val sun = arc(sunProgress)
    val sunPulse = 1f + 0.04f * sin(scene.time * 1.5f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x55FFF2A8), Color.Transparent),
            center = sun + Offset(imageSize / 2, imageSize / 2),
            radius = imageSize * 1.1f * sunPulse,
        ),
        radius = imageSize * 1.1f * sunPulse,
        center = sun + Offset(imageSize / 2, imageSize / 2),
    )
    drawBitmap(sprites.sun, Rect(sun, Size(imageSize, imageSize)))
    drawBitmap(sprites.moon, Rect(arc(moonProgress), Size(imageSize, imageSize)))
}

/** Fog layer and lightning behind the mountains. */
internal fun DrawScope.drawWeatherBack(scene: SceneState) {
    forEachWeather(scene) { state, alpha ->
        val fog = when (state) {
            WeatherState.MOSTLY_CLOUDY, WeatherState.HEAVY_RAIN, WeatherState.RAIN -> 0.1f
            WeatherState.THUNDERSTORM, WeatherState.SNOW -> 0.2f
            WeatherState.FOG -> 0.4f
            else -> 0f
        }
        if (fog > 0f) drawRect(Color.DarkGray, alpha = fog * alpha)
        if (state == WeatherState.THUNDERSTORM) drawThunder(scene, alpha)
    }
}

/** Clouds and precipitation in front of the mountains. */
internal fun DrawScope.drawWeatherFront(scene: SceneState, sprites: SceneSprites, tint: Float) {
    val dp = scene.layout?.dp ?: return
    val night = (tint / MOUNTAIN_TINT_ALPHA_MAX).coerceIn(0f, 1f)
    forEachWeather(scene) { state, alpha ->
        val clouds = cloudCount(state)
        if (clouds > 0) {
            val storm = when (state) {
                WeatherState.THUNDERSTORM, WeatherState.HEAVY_RAIN -> 1f
                WeatherState.RAIN, WeatherState.MOSTLY_CLOUDY -> 0.6f
                WeatherState.LIGHT_RAIN, WeatherState.SNOW, WeatherState.FOG -> 0.3f
                else -> 0f
            }
            clipRect(bottom = size.height * 0.5f) {
                drawDriftingClouds(clouds, scene.time, dp, alpha, storm, night)
            }
        }
        precipitation(state)?.let { params ->
            val system = scene.particleSystems.getOrPut("rain-$state") { ParticleSystem(params) }
            drawParticles(system, scene.step, alpha)
        }
    }
}

private inline fun forEachWeather(scene: SceneState, block: (WeatherState, Float) -> Unit) {
    val fade = scene.weatherFade
    scene.previousWeather?.let { if (fade < 1f) block(it, 1f - fade) }
    scene.weather?.let { block(it, fade) }
}

private fun cloudCount(state: WeatherState) = when (state) {
    WeatherState.CLEAR_SKY -> 0
    WeatherState.FEW_CLOUDS -> 2
    WeatherState.RAIN, WeatherState.LIGHT_RAIN, WeatherState.SNOW,
    WeatherState.SCATTERED_CLOUDS, WeatherState.FOG -> 3
    WeatherState.HEAVY_RAIN -> 5
    WeatherState.THUNDERSTORM -> 6
    WeatherState.MOSTLY_CLOUDY -> 8
}

private fun precipitation(state: WeatherState) = when (state) {
    WeatherState.LIGHT_RAIN -> rainParameters.copy(particleCount = 150)
    WeatherState.RAIN -> rainParameters
    WeatherState.HEAVY_RAIN -> rainParameters.copy(particleCount = 900)
    WeatherState.THUNDERSTORM -> rainParameters.copy(particleCount = 900, minAngle = 265, maxAngle = 295)
    WeatherState.SNOW -> snowParameters
    else -> null
}

private fun DrawScope.drawThunder(scene: SceneState, alpha: Float) {
    val step = scene.step
    val thunder = scene.thunder
    val id = step / THUNDER_PHASE
    if (thunder.id != id) {
        thunder.id = id
        val pointCount = Random.nextInt(4, 8)
        val points = mutableListOf(
            Offset(Random.nextFloat() * size.width, Random.nextFloat() * size.height / 4)
        )
        while (points.size < pointCount - 2) {
            val last = points.last()
            points += Offset(last.x + Random.nextInt(-200, 200), last.y + Random.nextInt(50, 200))
        }
        points += Offset(size.width / 2f, size.height)
        thunder.points = points
    }
    if (step % THUNDER_PHASE < THUNDER_DURATION) {
        drawRect(Color.White, alpha = 0.15f * alpha)
        val path = Path().apply {
            thunder.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(path, Color.White, alpha = alpha, style = Stroke(width = 2 * (scene.layout?.dp ?: 1f)))
    }
}

internal fun LocalTime.minutesOfDay(): Float = (hour * 60 + minute).toFloat()

private const val MINUTES_PER_DAY = 24 * 60
private const val TRANSITION = 45f
private const val MOUNTAIN_TINT_TRANSITION = 80f
private const val MOUNTAIN_TINT_ALPHA_MAX = 0.4f
private const val CELESTIAL_SIZE = 64
private const val CELESTIAL_BOTTOM_MARGIN = 60
private const val THUNDER_PHASE = 300L
private const val THUNDER_DURATION = 9L
