package dev.mothlight.plantagotchi.ui.weatherui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.day
import dev.mothlight.plantagotchi.resources.moon
import dev.mothlight.plantagotchi.resources.night
import dev.mothlight.plantagotchi.resources.pixel_landscape
import dev.mothlight.plantagotchi.resources.sun
import dev.mothlight.plantagotchi.resources.sunrise
import dev.mothlight.plantagotchi.resources.sunset
import dev.mothlight.plantagotchi.ui.particlesystem.Particles
import dev.mothlight.plantagotchi.ui.particlesystem.rainParameters
import dev.mothlight.plantagotchi.ui.particlesystem.snowParameters
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** Animated sky with sun/moon, day-night transition and weather effects. */
@Composable
fun WeatherLandscape(weather: CurrentWeather, modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frame.longValue++ }
    }

    val timeInMin by animateFloatAsState(
        targetValue = weather.localTime.minutesOfDay().toFloat(),
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "time",
    )
    val sunriseAt = weather.sunrise.minutesOfDay().toFloat()
    val sunsetAt = weather.sunset.minutesOfDay().toFloat()
    val sky = skyLayers(timeInMin, sunriseAt, sunsetAt)
    val nightTint = mountainTint(timeInMin, sunriseAt, sunsetAt) * MOUNTAIN_TINT_ALPHA_MAX
    val tintFilter = ColorFilter.tint(Color.Black.copy(alpha = nightTint), BlendMode.SrcAtop)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        sky.first?.let { SkyImage(it, 1f) }
        sky.second?.let { SkyImage(it, sky.secondAlpha) }

        // Sun and moon travel along a parabola across the sky.
        val imageSize = with(density) { CELESTIAL_SIZE.dp.toPx() }
        val sunProgress = (timeInMin - (sunriseAt - TRANSITION)) / ((sunsetAt + TRANSITION) - (sunriseAt - TRANSITION))
        val nightElapsed = when {
            timeInMin < sunriseAt -> timeInMin + MINUTES_PER_DAY - sunsetAt
            timeInMin > sunsetAt -> timeInMin - sunsetAt
            else -> 0f
        }
        val moonProgress = nightElapsed / (MINUTES_PER_DAY - sunsetAt + sunriseAt)
        val x2 = width - imageSize
        val a = -1 * (height + imageSize) / (((width / 2) - width - imageSize) * (width / 2))
        fun arcOffset(progress: Float): IntOffset {
            val x = (width - imageSize) * (1 - progress.coerceIn(0f, 1f))
            val y = a * (x - x2) * x + height - imageSize - CELESTIAL_BOTTOM_MARGIN
            return IntOffset(x.roundToInt(), y.roundToInt())
        }
        CelestialBody(Res.drawable.sun, "Sun", arcOffset(sunProgress))
        CelestialBody(Res.drawable.moon, "Moon", arcOffset(moonProgress))

        val state = weather.facts.state
        Crossfade(targetState = state, label = "fog") { s ->
            val fogAlpha = when (s) {
                WeatherState.MOSTLY_CLOUDY, WeatherState.HEAVY_RAIN, WeatherState.RAIN -> 0.1f
                WeatherState.THUNDERSTORM, WeatherState.SNOW -> 0.2f
                WeatherState.FOG -> 0.4f
                else -> 0f
            }
            if (fogAlpha > 0f) Box(Modifier.fillMaxSize().background(Color.DarkGray.copy(alpha = fogAlpha)))
        }

        if (state == WeatherState.THUNDERSTORM) Thunder(frame, Modifier.fillMaxSize())

        Image(
            painter = painterResource(Res.drawable.pixel_landscape),
            contentDescription = "Mountain",
            contentScale = ContentScale.FillWidth,
            colorFilter = tintFilter,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        )

        Crossfade(targetState = state, label = "precipitation") { s ->
            val precipitation = when (s) {
                WeatherState.LIGHT_RAIN -> rainParameters.copy(particleCount = 150)
                WeatherState.RAIN -> rainParameters
                WeatherState.HEAVY_RAIN -> rainParameters.copy(particleCount = 2000)
                WeatherState.THUNDERSTORM -> rainParameters.copy(particleCount = 2000, minAngle = 265, maxAngle = 295)
                WeatherState.SNOW -> snowParameters
                else -> null
            }
            val cloudCount = when (s) {
                WeatherState.CLEAR_SKY -> 0
                WeatherState.FEW_CLOUDS -> 2
                WeatherState.RAIN, WeatherState.LIGHT_RAIN, WeatherState.SNOW,
                WeatherState.SCATTERED_CLOUDS, WeatherState.FOG -> 3
                WeatherState.HEAVY_RAIN -> 5
                WeatherState.THUNDERSTORM -> 6
                WeatherState.MOSTLY_CLOUDY -> 8
            }
            Box(Modifier.fillMaxSize()) {
                if (cloudCount > 0) {
                    Clouds(frame, tintFilter, cloudCount, Modifier.fillMaxWidth().fillMaxHeight(0.5f))
                }
                if (precipitation != null) Particles(frame, precipitation, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SkyImage(image: DrawableResource, alpha: Float) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CelestialBody(image: DrawableResource, description: String, offset: IntOffset) {
    Image(
        painter = painterResource(image),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.offset { offset }.size(CELESTIAL_SIZE.dp),
    )
}

private class SkyLayers(val first: DrawableResource?, val second: DrawableResource?, val secondAlpha: Float)

private fun skyLayers(t: Float, sunrise: Float, sunset: Float): SkyLayers {
    val d = TRANSITION
    return when {
        t < sunrise - d -> SkyLayers(Res.drawable.night, null, 0f)
        t < sunrise -> SkyLayers(Res.drawable.night, Res.drawable.sunrise, 1 - (sunrise - t) / d)
        t < sunrise + d -> SkyLayers(Res.drawable.sunrise, Res.drawable.day, 1 - (sunrise + d - t) / d)
        t < sunset - d -> SkyLayers(Res.drawable.day, null, 0f)
        t < sunset -> SkyLayers(Res.drawable.day, Res.drawable.sunset, 1 - (sunset - t) / d)
        t < sunset + d -> SkyLayers(Res.drawable.sunset, Res.drawable.night, 1 - (sunset + d - t) / d)
        else -> SkyLayers(Res.drawable.night, null, 0f)
    }
}

private fun mountainTint(t: Float, sunrise: Float, sunset: Float): Float {
    val d = MOUNTAIN_TINT_TRANSITION
    return when {
        t < sunrise - d -> 1f
        t < sunrise + d -> 1 - (t - sunrise + d) / (2 * d)
        t < sunset - d -> 0f
        t < sunset + d -> (t - sunset + d) / (2 * d)
        else -> 1f
    }
}

private fun LocalTime.minutesOfDay() = hour * 60 + minute

private const val MINUTES_PER_DAY = 24 * 60
private const val TRANSITION = 45f
private const val MOUNTAIN_TINT_TRANSITION = 80f
private const val CELESTIAL_SIZE = 64
private const val CELESTIAL_BOTTOM_MARGIN = 60
private const val MOUNTAIN_TINT_ALPHA_MAX = 0.4f
