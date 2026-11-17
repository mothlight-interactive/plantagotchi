package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.data.AppSettingsState
import dev.mothlight.plantagotchi.data.repository.FriendInfo
import dev.mothlight.plantagotchi.data.repository.SendWaterResult
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.CurrentWeather
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.friends_add
import dev.mothlight.plantagotchi.resources.friends_already
import dev.mothlight.plantagotchi.resources.friends_code_hint
import dev.mothlight.plantagotchi.resources.friends_empty
import dev.mothlight.plantagotchi.resources.friends_health
import dev.mothlight.plantagotchi.resources.friends_invalid
import dev.mothlight.plantagotchi.resources.friends_offline
import dev.mothlight.plantagotchi.resources.friends_own
import dev.mothlight.plantagotchi.resources.friends_remove
import dev.mothlight.plantagotchi.resources.friends_send_water
import dev.mothlight.plantagotchi.resources.friends_sent
import dev.mothlight.plantagotchi.resources.friends_title
import dev.mothlight.plantagotchi.resources.friends_unknown
import dev.mothlight.plantagotchi.resources.friends_unknown_plant
import dev.mothlight.plantagotchi.resources.friends_your_code
import dev.mothlight.plantagotchi.resources.menu_about
import dev.mothlight.plantagotchi.resources.menu_friends
import dev.mothlight.plantagotchi.resources.menu_settings
import dev.mothlight.plantagotchi.resources.plant_withered
import dev.mothlight.plantagotchi.resources.settings_camera
import dev.mothlight.plantagotchi.resources.settings_camera_desc
import dev.mothlight.plantagotchi.resources.settings_notifications
import dev.mothlight.plantagotchi.resources.settings_notifications_desc
import dev.mothlight.plantagotchi.resources.settings_sound
import dev.mothlight.plantagotchi.resources.settings_title
import dev.mothlight.plantagotchi.resources.weather_clouds
import dev.mothlight.plantagotchi.resources.weather_effect_frost
import dev.mothlight.plantagotchi.resources.weather_effect_heat
import dev.mothlight.plantagotchi.resources.weather_effect_none
import dev.mothlight.plantagotchi.resources.weather_effect_rain
import dev.mothlight.plantagotchi.resources.weather_feels_like
import dev.mothlight.plantagotchi.resources.weather_humidity
import dev.mothlight.plantagotchi.resources.weather_min_max
import dev.mothlight.plantagotchi.resources.weather_season
import dev.mothlight.plantagotchi.resources.weather_sunrise
import dev.mothlight.plantagotchi.resources.weather_sunset
import dev.mothlight.plantagotchi.resources.weather_title
import dev.mothlight.plantagotchi.resources.weather_uv
import dev.mothlight.plantagotchi.resources.weather_wind
import dev.mothlight.plantagotchi.ui.game.CodeSprites
import dev.mothlight.plantagotchi.ui.game.PlantSprites
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

enum class MenuEntry { FRIENDS, SETTINGS, ABOUT }

@Composable
fun MenuSheet(onSelect: (MenuEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(16.dp)) {
        listOf(
            MenuEntry.FRIENDS to stringResource(Res.string.menu_friends),
            MenuEntry.SETTINGS to stringResource(Res.string.menu_settings),
            MenuEntry.ABOUT to stringResource(Res.string.menu_about),
        ).forEach { (entry, label) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).pixelPanel().clickable { onSelect(entry) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.OffWhite, modifier = Modifier.weight(1f))
                SpritePreview(CodeSprites.arrowRight, Modifier.size(width = 12.dp, height = 18.dp))
            }
        }
    }
}

@Composable
fun WeatherSheet(weather: CurrentWeather, season: Season) {
    val f = weather.facts
    Column(Modifier.fillMaxWidth().background(Panel).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
        SheetTitle(stringResource(Res.string.weather_title))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${f.temperature.roundToInt()}°", style = MaterialTheme.typography.displayMedium, color = PlantagotchiColors.OffWhite)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(weatherName(f.state), style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.OffWhite)
                Text(
                    stringResource(Res.string.weather_feels_like, "${f.apparentTemperature.roundToInt()}°"),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlantagotchiColors.AmethystSmoke,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val effect = when {
            f.temperature < GameRules.FROST_TEMPERATURE -> Res.string.weather_effect_frost
            f.temperature > GameRules.HEAT_TEMPERATURE -> Res.string.weather_effect_heat
            GameRules.waterGain(f.state) > 0 -> Res.string.weather_effect_rain
            else -> Res.string.weather_effect_none
        }
        Text(
            stringResource(effect),
            style = MaterialTheme.typography.bodyMedium,
            color = PlantagotchiColors.Sunshine,
            modifier = Modifier.fillMaxWidth().pixelPanel().padding(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        val rows = listOf(
            stringResource(Res.string.weather_season) to seasonName(season),
            stringResource(Res.string.weather_min_max) to "${weather.minTemperature.roundToInt()}° / ${weather.maxTemperature.roundToInt()}°",
            stringResource(Res.string.weather_humidity) to "${f.humidity.roundToInt()} %",
            stringResource(Res.string.weather_wind) to "${f.windSpeed.roundToInt()} km/h",
            stringResource(Res.string.weather_clouds) to "${f.cloudCover.roundToInt()} %",
            stringResource(Res.string.weather_uv) to "${f.uvIndex.roundToInt()}",
            stringResource(Res.string.weather_sunrise) to weather.sunrise.toString().take(5),
            stringResource(Res.string.weather_sunset) to weather.sunset.toString().take(5),
        )
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(bottom = 8.dp).pixelPanel().padding(10.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = PlantagotchiColors.AmethystSmoke)
                        Text(value, style = MaterialTheme.typography.titleSmall, color = PlantagotchiColors.OffWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsSheet(
    available: Boolean,
    ownCode: String,
    friends: List<String>,
    infos: List<FriendInfo>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
    onSendWater: suspend (String) -> SendWaterResult,
    onRefresh: suspend () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }

    Column(Modifier.fillMaxWidth().background(Panel).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
        SheetTitle(stringResource(Res.string.friends_title))
        Spacer(Modifier.height(12.dp))
        if (!available) {
            Text(stringResource(Res.string.friends_offline), style = MaterialTheme.typography.bodyMedium, color = PlantagotchiColors.OffWhite)
            return@Column
        }
        Column(Modifier.fillMaxWidth().pixelPanel().padding(12.dp)) {
            Text(stringResource(Res.string.friends_your_code), style = MaterialTheme.typography.labelSmall, color = PlantagotchiColors.AmethystSmoke)
            Text(ownCode, style = MaterialTheme.typography.headlineSmall, color = PlantagotchiColors.Sunshine)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it.uppercase().take(6) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = PlantagotchiColors.OffWhite),
                cursorBrush = SolidColor(PlantagotchiColors.Sunshine),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(stringResource(Res.string.friends_code_hint), style = MaterialTheme.typography.titleMedium, color = Color(0xFF7A6E80))
                    }
                    inner()
                },
                modifier = Modifier.weight(1f).background(Color(0xFF1C1318)).border(2.dp, Outline).padding(12.dp),
            )
            Spacer(Modifier.width(8.dp))
            PixelButton(stringResource(Res.string.friends_add)) {
                scope.launch {
                    if (onAdd(input)) input = "" else message = getString(Res.string.friends_invalid)
                }
            }
        }
        Text(message ?: " ", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9E9E), modifier = Modifier.padding(vertical = 6.dp))

        if (friends.isEmpty()) {
            Text(stringResource(Res.string.friends_empty), style = MaterialTheme.typography.bodyMedium, color = PlantagotchiColors.AmethystSmoke)
        }
        friends.forEach { code ->
            val info = infos.firstOrNull { it.code == code }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).pixelPanel().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(40.dp)) {
                    val sprite = when (info?.species) {
                        dev.mothlight.plantagotchi.data.state.Species.CACTUS -> PlantSprites.cactus[info.stage.coerceIn(0, 2)]
                        dev.mothlight.plantagotchi.data.state.Species.FERN -> PlantSprites.fern[info.stage.coerceIn(0, 2)]
                        else -> PlantSprites.fern[0]
                    }
                    sprite.drawFit(this, size)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(code, style = MaterialTheme.typography.titleSmall, color = PlantagotchiColors.OffWhite)
                    val status = when {
                        info == null -> stringResource(Res.string.friends_unknown_plant)
                        info.dead -> stringResource(Res.string.plant_withered)
                        else -> "${speciesName(info.species)}, ${stringResource(Res.string.friends_health, info.health.roundToInt())}"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke)
                }
                PixelButton(stringResource(Res.string.friends_send_water), enabled = info != null && !info.dead) {
                    scope.launch {
                        message = getString(
                            when (onSendWater(code)) {
                                SendWaterResult.SENT -> Res.string.friends_sent
                                SendWaterResult.ALREADY_SENT_TODAY -> Res.string.friends_already
                                SendWaterResult.UNKNOWN_CODE -> Res.string.friends_unknown
                                SendWaterResult.OWN_CODE -> Res.string.friends_own
                                SendWaterResult.OFFLINE -> Res.string.friends_offline
                            }
                        )
                        onRefresh()
                    }
                }
                Spacer(Modifier.width(6.dp))
                PixelButton(stringResource(Res.string.friends_remove), color = PlantagotchiColors.Love) { onRemove(code) }
            }
        }
    }
}

@Composable
fun SettingsSheet(settings: AppSettingsState, cameraSupported: Boolean, onChange: ((AppSettingsState) -> AppSettingsState) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(16.dp)) {
        SheetTitle(stringResource(Res.string.settings_title))
        Spacer(Modifier.height(12.dp))
        SettingRow(stringResource(Res.string.settings_sound), null, settings.sound) { v -> onChange { it.copy(sound = v) } }
        SettingRow(
            stringResource(Res.string.settings_notifications),
            stringResource(Res.string.settings_notifications_desc),
            settings.notifications,
        ) { v -> onChange { it.copy(notifications = v) } }
        if (cameraSupported) {
            SettingRow(
                stringResource(Res.string.settings_camera),
                stringResource(Res.string.settings_camera_desc),
                settings.cameraLight,
            ) { v -> onChange { it.copy(cameraLight = v) } }
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).pixelPanel().clickable { onChecked(!checked) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PlantagotchiColors.OffWhite)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = PlantagotchiColors.Water, checkedThumbColor = Outline),
        )
    }
}
