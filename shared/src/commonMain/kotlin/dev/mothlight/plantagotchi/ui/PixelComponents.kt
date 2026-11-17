package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.coin
import dev.mothlight.plantagotchi.resources.pot_color_1
import dev.mothlight.plantagotchi.resources.pot_color_2
import dev.mothlight.plantagotchi.resources.pot_color_3
import dev.mothlight.plantagotchi.resources.pot_color_4
import dev.mothlight.plantagotchi.resources.pot_name
import dev.mothlight.plantagotchi.resources.pot_shape_1
import dev.mothlight.plantagotchi.resources.pot_shape_2
import dev.mothlight.plantagotchi.resources.pot_shape_3
import dev.mothlight.plantagotchi.resources.season_autumn
import dev.mothlight.plantagotchi.resources.season_spring
import dev.mothlight.plantagotchi.resources.season_summer
import dev.mothlight.plantagotchi.resources.season_winter
import dev.mothlight.plantagotchi.resources.species_cactus
import dev.mothlight.plantagotchi.resources.species_fern
import dev.mothlight.plantagotchi.resources.species_sprout
import dev.mothlight.plantagotchi.resources.stage_0
import dev.mothlight.plantagotchi.resources.stage_1
import dev.mothlight.plantagotchi.resources.stage_2
import dev.mothlight.plantagotchi.resources.ws_clear_sky
import dev.mothlight.plantagotchi.resources.ws_few_clouds
import dev.mothlight.plantagotchi.resources.ws_fog
import dev.mothlight.plantagotchi.resources.ws_heavy_rain
import dev.mothlight.plantagotchi.resources.ws_light_rain
import dev.mothlight.plantagotchi.resources.ws_mostly_cloudy
import dev.mothlight.plantagotchi.resources.ws_rain
import dev.mothlight.plantagotchi.resources.ws_scattered_clouds
import dev.mothlight.plantagotchi.resources.ws_snow
import dev.mothlight.plantagotchi.resources.ws_thunderstorm
import dev.mothlight.plantagotchi.ui.game.PixelSprite
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val Panel = Color(0xFF2B1D24)
internal val PanelLight = Color(0xFF3F282F)
internal val Outline = Color(0xFF222034)

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.OffWhite, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = PlantagotchiColors.Sunshine, modifier = modifier)
}

@Composable
internal fun CoinAmount(coins: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(Res.drawable.coin), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("$coins", style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.Sunshine)
    }
}

@Composable
internal fun PixelButton(
    text: String,
    enabled: Boolean = true,
    showCoin: Boolean = false,
    color: Color = PlantagotchiColors.Water,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .background(if (enabled) color else Color(0xFF5A5060))
            .border(2.dp, Outline)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCoin) {
            Image(painterResource(Res.drawable.coin), contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Outline else Color(0xFFB8AFBF))
    }
}

@Composable
internal fun SpritePreview(sprite: PixelSprite, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) { sprite.drawFit(this, size) }
    }
}

internal fun Modifier.pixelPanel(selected: Boolean = false) = this
    .background(if (selected) Color(0xFF4A3540) else PanelLight)
    .border(2.dp, if (selected) PlantagotchiColors.Sunshine else Outline)

@Composable
fun speciesName(species: Species): String = stringResource(species.nameRes())

fun Species.nameRes(): StringResource = when (this) {
    Species.SPROUT -> Res.string.species_sprout
    Species.CACTUS -> Res.string.species_cactus
    Species.FERN -> Res.string.species_fern
}

@Composable
fun stageName(stage: Int): String = stringResource(
    when (stage) {
        0 -> Res.string.stage_0
        1 -> Res.string.stage_1
        else -> Res.string.stage_2
    }
)

@Composable
fun seasonName(season: Season): String = stringResource(
    when (season) {
        Season.SPRING -> Res.string.season_spring
        Season.SUMMER -> Res.string.season_summer
        Season.AUTUMN -> Res.string.season_autumn
        Season.WINTER -> Res.string.season_winter
    }
)

@Composable
fun weatherName(state: WeatherState): String = stringResource(
    when (state) {
        WeatherState.CLEAR_SKY -> Res.string.ws_clear_sky
        WeatherState.FEW_CLOUDS -> Res.string.ws_few_clouds
        WeatherState.SCATTERED_CLOUDS -> Res.string.ws_scattered_clouds
        WeatherState.MOSTLY_CLOUDY -> Res.string.ws_mostly_cloudy
        WeatherState.HEAVY_RAIN -> Res.string.ws_heavy_rain
        WeatherState.LIGHT_RAIN -> Res.string.ws_light_rain
        WeatherState.THUNDERSTORM -> Res.string.ws_thunderstorm
        WeatherState.RAIN -> Res.string.ws_rain
        WeatherState.SNOW -> Res.string.ws_snow
        WeatherState.FOG -> Res.string.ws_fog
    }
)

@Composable
fun potName(id: String): String {
    val color = stringResource(
        when (PotId.color(id)) {
            1 -> Res.string.pot_color_1
            2 -> Res.string.pot_color_2
            3 -> Res.string.pot_color_3
            else -> Res.string.pot_color_4
        }
    )
    val shape = stringResource(
        when (PotId.shape(id)) {
            1 -> Res.string.pot_shape_1
            2 -> Res.string.pot_shape_2
            else -> Res.string.pot_shape_3
        }
    )
    return stringResource(Res.string.pot_name, color, shape)
}
