package dev.mothlight.plantagotchi.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.chest
import dev.mothlight.plantagotchi.resources.coin
import dev.mothlight.plantagotchi.resources.coint_counter
import dev.mothlight.plantagotchi.resources.day
import dev.mothlight.plantagotchi.resources.fertilizer
import dev.mothlight.plantagotchi.resources.heart
import dev.mothlight.plantagotchi.resources.ic_ground
import dev.mothlight.plantagotchi.resources.moon
import dev.mothlight.plantagotchi.resources.night
import dev.mothlight.plantagotchi.resources.pixel_landscape
import dev.mothlight.plantagotchi.resources.sun
import dev.mothlight.plantagotchi.resources.sunrise
import dev.mothlight.plantagotchi.resources.sunset
import dev.mothlight.plantagotchi.resources.sunshine
import dev.mothlight.plantagotchi.resources.water
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource

/** All bitmaps and vectors the game canvas draws. */
@Immutable
class SceneSprites(
    val skyDay: ImageBitmap,
    val skyNight: ImageBitmap,
    val skySunrise: ImageBitmap,
    val skySunset: ImageBitmap,
    val sun: ImageBitmap,
    val moon: ImageBitmap,
    val mountains: ImageBitmap,
    val chest: ImageBitmap,
    val coin: ImageBitmap,
    val coinCounter: ImageBitmap,
    val heart: ImageBitmap,
    val fertilizer: ImageBitmap,
    val water: ImageBitmap,
    val sunshine: ImageBitmap,
    val ground: Painter,
)

@Composable
fun rememberSceneSprites(): SceneSprites = SceneSprites(
    skyDay = imageResource(Res.drawable.day),
    skyNight = imageResource(Res.drawable.night),
    skySunrise = imageResource(Res.drawable.sunrise),
    skySunset = imageResource(Res.drawable.sunset),
    sun = imageResource(Res.drawable.sun),
    moon = imageResource(Res.drawable.moon),
    mountains = imageResource(Res.drawable.pixel_landscape),
    chest = imageResource(Res.drawable.chest),
    coin = imageResource(Res.drawable.coin),
    coinCounter = imageResource(Res.drawable.coint_counter),
    heart = imageResource(Res.drawable.heart),
    fertilizer = imageResource(Res.drawable.fertilizer),
    water = imageResource(Res.drawable.water),
    sunshine = imageResource(Res.drawable.sunshine),
    ground = painterResource(Res.drawable.ic_ground),
)
