package dev.mothlight.plantagotchi.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.chest
import dev.mothlight.plantagotchi.resources.cloud1
import dev.mothlight.plantagotchi.resources.coin
import dev.mothlight.plantagotchi.resources.coint_counter
import dev.mothlight.plantagotchi.resources.day
import dev.mothlight.plantagotchi.resources.fertilizer
import dev.mothlight.plantagotchi.resources.heart
import dev.mothlight.plantagotchi.resources.ic_button_bg
import dev.mothlight.plantagotchi.resources.ic_ground
import dev.mothlight.plantagotchi.resources.ic_plant1
import dev.mothlight.plantagotchi.resources.ic_plant2
import dev.mothlight.plantagotchi.resources.ic_plant3
import dev.mothlight.plantagotchi.resources.ic_pot11
import dev.mothlight.plantagotchi.resources.ic_pot12
import dev.mothlight.plantagotchi.resources.ic_pot13
import dev.mothlight.plantagotchi.resources.ic_pot14
import dev.mothlight.plantagotchi.resources.ic_pot21
import dev.mothlight.plantagotchi.resources.ic_pot22
import dev.mothlight.plantagotchi.resources.ic_pot23
import dev.mothlight.plantagotchi.resources.ic_pot24
import dev.mothlight.plantagotchi.resources.ic_pot31
import dev.mothlight.plantagotchi.resources.ic_pot32
import dev.mothlight.plantagotchi.resources.ic_pot33
import dev.mothlight.plantagotchi.resources.ic_pot34
import dev.mothlight.plantagotchi.resources.moon
import dev.mothlight.plantagotchi.resources.night
import dev.mothlight.plantagotchi.resources.pixel_landscape
import dev.mothlight.plantagotchi.resources.sun
import dev.mothlight.plantagotchi.resources.sunrise
import dev.mothlight.plantagotchi.resources.sunset
import dev.mothlight.plantagotchi.resources.sunshine
import dev.mothlight.plantagotchi.resources.water
import org.jetbrains.compose.resources.DrawableResource
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
    val cloud: ImageBitmap,
    val mountains: ImageBitmap,
    val chest: ImageBitmap,
    val coin: ImageBitmap,
    val coinCounter: ImageBitmap,
    val heart: ImageBitmap,
    val fertilizer: ImageBitmap,
    val water: ImageBitmap,
    val sunshine: ImageBitmap,
    val ground: Painter,
    /** Sprout growth stages. */
    val sproutStages: List<Painter>,
    val buttonFrame: Painter,
    val pots: Map<String, Painter>,
) {
    fun pot(id: String): Painter = pots[id] ?: pots.getValue(PotId.DEFAULT)
}

private val potResources: Map<String, DrawableResource> = mapOf(
    "pot11" to Res.drawable.ic_pot11, "pot12" to Res.drawable.ic_pot12,
    "pot13" to Res.drawable.ic_pot13, "pot14" to Res.drawable.ic_pot14,
    "pot21" to Res.drawable.ic_pot21, "pot22" to Res.drawable.ic_pot22,
    "pot23" to Res.drawable.ic_pot23, "pot24" to Res.drawable.ic_pot24,
    "pot31" to Res.drawable.ic_pot31, "pot32" to Res.drawable.ic_pot32,
    "pot33" to Res.drawable.ic_pot33, "pot34" to Res.drawable.ic_pot34,
)

@Composable
fun rememberPotPainters(): Map<String, Painter> =
    potResources.mapValues { (_, res) -> painterResource(res) }

@Composable
fun rememberSceneSprites(): SceneSprites = SceneSprites(
    skyDay = imageResource(Res.drawable.day),
    skyNight = imageResource(Res.drawable.night),
    skySunrise = imageResource(Res.drawable.sunrise),
    skySunset = imageResource(Res.drawable.sunset),
    sun = imageResource(Res.drawable.sun),
    moon = imageResource(Res.drawable.moon),
    cloud = imageResource(Res.drawable.cloud1),
    mountains = imageResource(Res.drawable.pixel_landscape),
    chest = imageResource(Res.drawable.chest),
    coin = imageResource(Res.drawable.coin),
    coinCounter = imageResource(Res.drawable.coint_counter),
    heart = imageResource(Res.drawable.heart),
    fertilizer = imageResource(Res.drawable.fertilizer),
    water = imageResource(Res.drawable.water),
    sunshine = imageResource(Res.drawable.sunshine),
    ground = painterResource(Res.drawable.ic_ground),
    sproutStages = listOf(
        painterResource(Res.drawable.ic_plant1),
        painterResource(Res.drawable.ic_plant2),
        painterResource(Res.drawable.ic_plant3),
    ),
    buttonFrame = painterResource(Res.drawable.ic_button_bg),
    pots = rememberPotPainters(),
)
