package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.dev.DevLocations
import dev.mothlight.plantagotchi.dev.DevOverrides
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.app_name
import dev.mothlight.plantagotchi.resources.btn_close
import dev.mothlight.plantagotchi.resources.dev_add_plant
import dev.mothlight.plantagotchi.resources.dev_all_full
import dev.mothlight.plantagotchi.resources.dev_all_low
import dev.mothlight.plantagotchi.resources.dev_almost_dead
import dev.mothlight.plantagotchi.resources.dev_back_to_live
import dev.mothlight.plantagotchi.resources.dev_coins
import dev.mothlight.plantagotchi.resources.dev_freeze
import dev.mothlight.plantagotchi.resources.dev_freeze_desc
import dev.mothlight.plantagotchi.resources.dev_gps
import dev.mothlight.plantagotchi.resources.dev_growth
import dev.mothlight.plantagotchi.resources.dev_light
import dev.mothlight.plantagotchi.resources.dev_live
import dev.mothlight.plantagotchi.resources.dev_remove_plant
import dev.mothlight.plantagotchi.resources.dev_reset_game
import dev.mothlight.plantagotchi.resources.dev_reset_game_confirm
import dev.mothlight.plantagotchi.resources.dev_reward_again
import dev.mothlight.plantagotchi.resources.dev_section_animations
import dev.mothlight.plantagotchi.resources.dev_section_location
import dev.mothlight.plantagotchi.resources.dev_section_plant
import dev.mothlight.plantagotchi.resources.dev_section_player
import dev.mothlight.plantagotchi.resources.dev_section_sounds
import dev.mothlight.plantagotchi.resources.dev_section_time
import dev.mothlight.plantagotchi.resources.dev_section_weather
import dev.mothlight.plantagotchi.resources.dev_skip
import dev.mothlight.plantagotchi.resources.dev_speed
import dev.mothlight.plantagotchi.resources.dev_streak
import dev.mothlight.plantagotchi.resources.dev_temperature
import dev.mothlight.plantagotchi.resources.dev_test_notification
import dev.mothlight.plantagotchi.resources.dev_test_notification_hint
import dev.mothlight.plantagotchi.resources.dev_time_of_day
import dev.mothlight.plantagotchi.resources.dev_title
import dev.mothlight.plantagotchi.resources.dev_unlock_all
import dev.mothlight.plantagotchi.resources.dev_wind
import dev.mothlight.plantagotchi.resources.item_grow_lamp
import dev.mothlight.plantagotchi.resources.need_fertilizer
import dev.mothlight.plantagotchi.resources.need_light
import dev.mothlight.plantagotchi.resources.need_love
import dev.mothlight.plantagotchi.resources.need_water
import dev.mothlight.plantagotchi.resources.notif_water
import dev.mothlight.plantagotchi.resources.plant_withered
import dev.mothlight.plantagotchi.resources.shop_ground
import dev.mothlight.plantagotchi.resources.shop_scenery
import dev.mothlight.plantagotchi.resources.weather_season
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** What the test mode can do; wired to the view model in [GameScreen]. */
class DevActions(
    val update: ((DevOverrides) -> DevOverrides) -> Unit = {},
    val updatePlayer: ((PlayerState) -> PlayerState) -> Unit = {},
    val emit: (GameEvent) -> Unit = {},
    val play: (String) -> Unit = {},
    val skip: (Double) -> Unit = {},
    val testNotification: (String, String) -> Unit = { _, _ -> },
    val resetGame: () -> Unit = {},
)

/** The plant idle actions, by name (see ui/game/PlantBehavior.kt). */
private val plantActions = listOf("WIGGLE", "STRETCH", "LOOK", "SHAKE", "DANCE", "NOD", "SNAP")

/**
 * Test mode: every value of the game can be changed here. Only reachable in dev and test builds;
 * the view model ignores all of it in prod.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevSheet(state: GameUiState, actions: DevActions, onClose: () -> Unit = {}) {
    val dev = state.dev
    val player = state.player
    val plant = player.activePlant
    val now = state.now
    fun plantUpdate(transform: (PlantState) -> PlantState) = actions.updatePlayer { it.updateActive(transform) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Panel)
            .border(2.dp, Outline)
            .statusBarsPadding(),
    ) {
        // Pinned header: title, env, close and "back to live" stay visible while the rest scrolls.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.dev_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = PlantagotchiColors.Sunshine,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                EnvChip(state.env.label)
                Spacer(Modifier.width(8.dp))
                PixelButton(stringResource(Res.string.btn_close), color = PlantagotchiColors.OffWhite, onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
            if (!dev.isEmpty) PixelButton(stringResource(Res.string.dev_back_to_live), color = PlantagotchiColors.Fertilizer) { actions.update { DevOverrides() } }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            // Time
            DevSection(stringResource(Res.string.dev_section_time))
            DevLabel(stringResource(Res.string.dev_speed))
            Chips {
                DevOverrides.speeds.forEach { speed ->
                    DevChip("${speed.toInt()}x", dev.timeSpeed == speed) { actions.update { it.copy(timeSpeed = speed) } }
                }
            }
            DevToggle(stringResource(Res.string.dev_freeze), stringResource(Res.string.dev_freeze_desc), dev.freezeNeeds) { v ->
                actions.update { it.copy(freezeNeeds = v) }
            }
            DevSlider(
                label = stringResource(Res.string.dev_time_of_day),
                value = dev.minutesOfDay?.toFloat(),
                range = 0f..(24 * 60 - 1).toFloat(),
                format = { "${(it / 60).toInt().toString().padStart(2, '0')}:${(it % 60).toInt().toString().padStart(2, '0')}" },
                onLive = { actions.update { it.copy(minutesOfDay = null) } },
                liveValue = state.weather.localTime.let { (it.hour * 60 + it.minute).toFloat() },
            ) { v -> actions.update { it.copy(minutesOfDay = v.roundToInt()) } }
            DevLabel(stringResource(Res.string.dev_skip))
            Chips {
                listOf("+1 h" to 3_600.0, "+6 h" to 21_600.0, "+1 d" to 86_400.0, "+3 d" to 259_200.0).forEach { (label, seconds) ->
                    DevChip(label, false) { actions.skip(seconds) }
                }
            }

            // Weather
            DevSection(stringResource(Res.string.dev_section_weather))
            Chips {
                DevChip(stringResource(Res.string.dev_live), dev.weather == null) { actions.update { it.copy(weather = null) } }
                WeatherState.entries.forEach { ws ->
                    DevChip(weatherName(ws), dev.weather == ws) { actions.update { it.copy(weather = ws) } }
                }
            }
            DevSlider(
                label = stringResource(Res.string.dev_temperature),
                value = dev.temperature?.toFloat(),
                range = -20f..45f,
                format = { "${it.roundToInt()} °C" },
                onLive = { actions.update { it.copy(temperature = null) } },
                liveValue = state.weather.facts.temperature.toFloat(),
            ) { v -> actions.update { it.copy(temperature = v.roundToInt().toDouble()) } }
            DevSlider(
                label = stringResource(Res.string.dev_wind),
                value = dev.windSpeed?.toFloat(),
                range = 0f..80f,
                format = { "${it.roundToInt()} km/h" },
                onLive = { actions.update { it.copy(windSpeed = null) } },
                liveValue = state.weather.facts.windSpeed.toFloat(),
            ) { v -> actions.update { it.copy(windSpeed = v.roundToInt().toDouble()) } }
            DevSlider(
                label = stringResource(Res.string.dev_light),
                value = dev.ambientLux,
                range = 0f..10_000f,
                format = { "${it.roundToInt()} lx" + if (it > GameRules.LUX_THRESHOLD) " ☀" else "" },
                onLive = { actions.update { it.copy(ambientLux = null) } },
                liveValue = state.ambientLux,
            ) { v -> actions.update { it.copy(ambientLux = (v / 100).roundToInt() * 100f) } }
            DevLabel(stringResource(Res.string.weather_season))
            Chips {
                DevChip(stringResource(Res.string.dev_live), dev.season == null) { actions.update { it.copy(season = null) } }
                Season.entries.forEach { season ->
                    DevChip(seasonName(season), dev.season == season) { actions.update { it.copy(season = season) } }
                }
            }

            // Location
            DevSection(stringResource(Res.string.dev_section_location))
            Chips {
                DevChip(stringResource(Res.string.dev_gps), dev.locationId == null) { actions.update { it.copy(locationId = null) } }
                DevLocations.all.forEach { location ->
                    DevChip(location.name, dev.locationId == location.id) { actions.update { it.copy(locationId = location.id) } }
                }
            }
            val p = state.position
            DevNote("${(p.latitude * 1000).roundToInt() / 1000.0}, ${(p.longitude * 1000).roundToInt() / 1000.0}")

            // Active plant
            DevSection("${stringResource(Res.string.dev_section_plant)}: ${speciesName(plant.species)} (${player.activeIndex + 1}/${player.plants.size})")
            Chips {
                Species.entries.forEach { species ->
                    DevChip(speciesName(species), plant.species == species) { plantUpdate { it.copy(species = species) } }
                }
            }
            val stage = stageName(plant.stage)
            DevSlider(
                label = stringResource(Res.string.dev_growth),
                value = (plant.growth / GameRules.STAGE_3_GROWTH * 100).toFloat().coerceIn(0f, 100f),
                range = 0f..100f,
                format = { "${it.roundToInt()} % · $stage" },
            ) { v -> plantUpdate { it.copy(growth = v / 100.0 * GameRules.STAGE_3_GROWTH) } }
            NeedSlider(stringResource(Res.string.need_water), plant.water) { v -> plantUpdate { it.copy(water = v) } }
            NeedSlider(stringResource(Res.string.need_light), plant.lux) { v -> plantUpdate { it.copy(lux = v) } }
            NeedSlider(stringResource(Res.string.need_love), plant.love) { v -> plantUpdate { it.copy(love = v) } }
            NeedSlider(stringResource(Res.string.need_fertilizer), plant.fertilizer) { v -> plantUpdate { it.copy(fertilizer = v) } }
            DevToggle(stringResource(Res.string.plant_withered), null, plant.dead) { v ->
                plantUpdate { it.copy(dead = v, criticalSince = 0L) }
            }
            Chips {
                DevChip(stringResource(Res.string.dev_all_full), false) {
                    plantUpdate { it.copy(water = 100.0, lux = 100.0, love = 100.0, fertilizer = 100.0, dead = false, criticalSince = 0L) }
                }
                DevChip(stringResource(Res.string.dev_all_low), false) {
                    plantUpdate { it.copy(water = 15.0, lux = 15.0, love = 15.0, fertilizer = 15.0) }
                }
                DevChip(stringResource(Res.string.dev_almost_dead), false) {
                    // Critical for 23 h 59 min: withers within the next game minute.
                    plantUpdate {
                        it.copy(water = 2.0, lux = 2.0, love = 2.0, fertilizer = 2.0, dead = false,
                            criticalSince = now - GameRules.DEAD_AFTER_MILLIS + 60_000)
                    }
                }
            }
            Chips {
                if (player.plants.size < PlayerState.MAX_PLANTS) {
                    DevChip(stringResource(Res.string.dev_add_plant), false) {
                        actions.updatePlayer {
                            val next = PlantState(species = Species.entries[it.plants.size % Species.entries.size], plantedAt = now)
                            it.copy(plants = it.plants + next, activePlantId = next.id)
                        }
                    }
                }
                if (player.plants.size > 1) {
                    DevChip(stringResource(Res.string.dev_remove_plant), false) {
                        actions.updatePlayer {
                            val rest = it.plants - it.activePlant
                            it.copy(plants = rest, activePlantId = rest.first().id)
                        }
                    }
                }
            }

            // Player
            DevSection(stringResource(Res.string.dev_section_player))
            DevLabel("${stringResource(Res.string.dev_coins)}: ${player.coins}")
            Chips {
                listOf(10, 100, 1_000, 10_000).forEach { amount ->
                    DevChip("+$amount", false) { actions.updatePlayer { it.copy(coins = it.coins + amount) } }
                }
                DevChip("0", false) { actions.updatePlayer { it.copy(coins = 0) } }
            }
            DevLabel("${stringResource(Res.string.dev_streak)}: ${player.streak}")
            Chips {
                DevChip(stringResource(Res.string.dev_reward_again), !state.rewardAvailable) {
                    // Yesterday: the next claim continues the streak.
                    actions.updatePlayer { it.copy(lastRewardDay = state.today - 1) }
                }
                DevChip("+1", false) { actions.updatePlayer { it.copy(streak = it.streak + 1) } }
                DevChip("0", false) { actions.updatePlayer { it.copy(streak = 0) } }
            }
            DevToggle(stringResource(Res.string.item_grow_lamp), null, state.lampActive) { on ->
                actions.updatePlayer { it.copy(lampUntil = if (on) now + 30 * 60_000L else 0L) }
            }
            PixelButton(stringResource(Res.string.dev_unlock_all), color = PlantagotchiColors.Sunshine) {
                actions.updatePlayer {
                    it.copy(ownedPots = PotId.all.toSet(), ownedScenery = Scenery.entries.toSet(), ownedGrounds = Ground.entries.toSet())
                }
            }
            DevLabel(stringResource(Res.string.shop_scenery))
            Chips {
                Scenery.entries.forEach { scenery ->
                    DevChip(sceneryName(scenery), player.scenery == scenery) {
                        actions.updatePlayer { it.copy(scenery = scenery, ownedScenery = it.ownedScenery + scenery) }
                    }
                }
            }
            DevLabel(stringResource(Res.string.shop_ground))
            Chips {
                Ground.entries.forEach { ground ->
                    DevChip(groundName(ground), player.ground == ground) {
                        actions.updatePlayer { it.copy(ground = ground, ownedGrounds = it.ownedGrounds + ground) }
                    }
                }
            }

            // Animations
            DevSection(stringResource(Res.string.dev_section_animations))
            Chips {
                DevChip("Water", false) { actions.emit(GameEvent.Purchased(ShopCatalog.wateringCan)) }
                DevChip("Fertilizer", false) { actions.emit(GameEvent.Purchased(ShopCatalog.fertilizerBag)) }
                DevChip("Lamp", false) { actions.emit(GameEvent.Purchased(ShopCatalog.growLamp)) }
                DevChip("Pot", false) { actions.emit(GameEvent.Purchased(ShopCatalog.pots.first())) }
                DevChip("Scenery", false) { actions.emit(GameEvent.Purchased(ShopCatalog.backdrops.first())) }
                DevChip("Pet", false) { actions.emit(GameEvent.Petted) }
                DevChip("Grow", false) { actions.emit(GameEvent.GrewUp(plant.id, plant.stage)) }
                DevChip("Wither", false) { actions.emit(GameEvent.Withered(plant.id)) }
                DevChip("Reward", false) { actions.emit(GameEvent.RewardClaimed(60, player.streak + 1)) }
                plantActions.forEach { action ->
                    DevChip(action.lowercase().replaceFirstChar { it.uppercase() }, false) { actions.emit(GameEvent.DevPlantAction(action)) }
                }
            }

            // Sounds
            DevSection(stringResource(Res.string.dev_section_sounds))
            Chips {
                AudioAssets.all.filterNot { it.startsWith("music/") || it.startsWith("ambient/") }.forEach { asset ->
                    DevChip(asset.substringAfter('/'), false) { actions.play(asset) }
                }
            }

            Spacer(Modifier.height(16.dp))
            val title = stringResource(Res.string.app_name)
            val body = stringResource(Res.string.notif_water, speciesName(plant.species))
            PixelButton(stringResource(Res.string.dev_test_notification)) { actions.testNotification(title, body) }
            DevNote(stringResource(Res.string.dev_test_notification_hint))
            Spacer(Modifier.height(12.dp))
            var confirmReset by remember { mutableStateOf(false) }
            PixelButton(
                stringResource(if (confirmReset) Res.string.dev_reset_game_confirm else Res.string.dev_reset_game),
                color = PlantagotchiColors.Love,
            ) {
                if (confirmReset) {
                    actions.resetGame()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Small label shown at the top of the screen in dev and test builds; tap opens the test mode. */
@Composable
fun DevBadge(state: GameUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dev = state.dev
    val parts = buildList {
        add(state.env.label)
        if (dev.timeSpeed > 1f) add("${dev.timeSpeed.toInt()}x")
        if (dev.freezeNeeds) add("II")
        val others = dev.copy(timeSpeed = 1f, freezeNeeds = false)
        if (!others.isEmpty) add("*")
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = Outline,
        modifier = modifier
            .clickable(onClick = onClick)
            .background(PlantagotchiColors.Fertilizer)
            .border(2.dp, Outline)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EnvChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = Outline,
        modifier = Modifier.background(PlantagotchiColors.Fertilizer).border(2.dp, Outline).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun DevSection(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.Sunshine)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DevLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = PlantagotchiColors.OffWhite, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun DevNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) { content() }
}

@Composable
private fun DevChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Outline else PlantagotchiColors.OffWhite,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) PlantagotchiColors.Water else PanelLight)
            .border(2.dp, Outline)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun DevToggle(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = PlantagotchiColors.OffWhite)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = PlantagotchiColors.Water, checkedThumbColor = Outline),
        )
    }
}

/**
 * A slider that is either "live" ([value] null, shows [liveValue] greyed out) or overridden.
 * Without [onLive] it always edits the value directly.
 */
@Composable
private fun DevSlider(
    label: String,
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onLive: (() -> Unit)? = null,
    liveValue: Float = range.start,
    onChange: (Float) -> Unit,
) {
    val shown = (value ?: liveValue).coerceIn(range)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = PlantagotchiColors.OffWhite, modifier = Modifier.weight(1f))
        Text(
            format(shown),
            style = MaterialTheme.typography.labelLarge,
            color = if (value == null && onLive != null) PlantagotchiColors.AmethystSmoke else PlantagotchiColors.Sunshine,
        )
        if (onLive != null) {
            Spacer(Modifier.width(8.dp))
            DevChip(stringResource(Res.string.dev_live), value == null, onLive)
        }
    }
    Slider(
        value = shown,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = PlantagotchiColors.Sunshine,
            activeTrackColor = if (value == null && onLive != null) PlantagotchiColors.AmethystSmoke else PlantagotchiColors.Water,
            inactiveTrackColor = Color(0xFF5A5060),
        ),
    )
}

@Composable
private fun NeedSlider(label: String, value: Double, onChange: (Double) -> Unit) =
    DevSlider(label, value.toFloat(), 0f..100f, { "${it.roundToInt()} %" }) { onChange(it.toDouble()) }
