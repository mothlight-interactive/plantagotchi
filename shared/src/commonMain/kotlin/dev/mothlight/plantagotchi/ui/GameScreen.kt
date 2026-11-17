package dev.mothlight.plantagotchi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mothlight.plantagotchi.data.AppSettingsState
import dev.mothlight.plantagotchi.data.repository.SendWaterResult
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.compost
import dev.mothlight.plantagotchi.resources.hint_grew
import dev.mothlight.plantagotchi.resources.hint_light_estimated
import dev.mothlight.plantagotchi.resources.hint_light_sensor
import dev.mothlight.plantagotchi.resources.hint_love
import dev.mothlight.plantagotchi.resources.hint_reward
import dev.mothlight.plantagotchi.resources.hint_reward_tomorrow
import dev.mothlight.plantagotchi.resources.hint_water_received
import dev.mothlight.plantagotchi.resources.hint_withered
import dev.mothlight.plantagotchi.resources.needs_label
import dev.mothlight.plantagotchi.resources.plant_counter
import dev.mothlight.plantagotchi.resources.plant_withered
import dev.mothlight.plantagotchi.resources.replant
import dev.mothlight.plantagotchi.ui.game.GameCanvas
import dev.mothlight.plantagotchi.ui.game.SceneTarget
import dev.mothlight.plantagotchi.ui.game.SceneTexts
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Everything the player can do, so [GameContent] can be previewed without a view model. */
class GameActions(
    val pet: (Float) -> Unit = {},
    val buy: (ShopItem) -> PurchaseResult = { PurchaseResult.NotEnoughCoins },
    val selectPot: (String) -> Unit = {},
    val switchPlant: (Int) -> Unit = {},
    val selectPlant: (Int) -> Unit = {},
    val selectScenery: (Scenery) -> Unit = {},
    val selectGround: (Ground) -> Unit = {},
    val replant: () -> Unit = {},
    val compost: () -> Unit = {},
    val claimReward: () -> Unit = {},
    val addFriend: (String) -> Boolean = { false },
    val removeFriend: (String) -> Unit = {},
    val sendWater: suspend (String) -> SendWaterResult = { SendWaterResult.OFFLINE },
    val refreshFriends: suspend () -> Unit = {},
    val updateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit = {},
)

@Composable
fun GameScreen(viewModel: PlantagotchiViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        GameActions(
            pet = viewModel::pet,
            buy = viewModel::buy,
            selectPot = viewModel::selectPot,
            switchPlant = viewModel::switchPlant,
            selectPlant = viewModel::selectPlant,
            selectScenery = viewModel::selectScenery,
            selectGround = viewModel::selectGround,
            replant = viewModel::replant,
            compost = viewModel::compost,
            claimReward = viewModel::claimReward,
            addFriend = viewModel::addFriend,
            removeFriend = viewModel::removeFriend,
            sendWater = viewModel::sendWater,
            refreshFriends = viewModel::refreshFriends,
            updateSettings = viewModel::updateSettings,
        )
    }
    GameContent(state = state, events = viewModel.events, actions = actions)
}

enum class Sheet { NONE, SHOP, MENU, WEATHER, FRIENDS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameContent(
    state: GameUiState,
    events: Flow<GameEvent> = emptyFlow(),
    actions: GameActions = GameActions(),
    initialSheet: Sheet = Sheet.NONE,
    showSplash: Boolean = true,
    initialAbout: Boolean = false,
) {
    var sheet by remember { mutableStateOf(initialSheet) }
    var about by remember { mutableStateOf(initialAbout) }
    var hint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Splash: at least 1.8 s, longer while the game is still loading.
    val splash = remember { Animatable(if (showSplash) 0f else 1f) }
    LaunchedEffect(Unit) { splash.animateTo(0.9f, tween(1_600, easing = LinearEasing)) }
    LaunchedEffect(state.loading, splash.value >= 0.9f) {
        if (!state.loading && splash.value >= 0.9f) splash.animateTo(1f, tween(250))
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(3_000)
            hint = null
        }
    }
    LaunchedEffect(events) {
        events.collect { event ->
            val plantName = getString(state.player.activePlant.species.nameRes())
            hint = when (event) {
                is GameEvent.GrewUp -> getString(Res.string.hint_grew, plantName)
                is GameEvent.Withered -> getString(Res.string.hint_withered, plantName)
                is GameEvent.RewardClaimed -> getString(Res.string.hint_reward, event.coins, event.streak)
                GameEvent.RewardAlreadyClaimed -> getString(Res.string.hint_reward_tomorrow)
                is GameEvent.WaterReceived -> getString(Res.string.hint_water_received, event.amount)
                else -> hint
            }
        }
    }

    PlantagotchiTheme(darkTheme = !state.weather.isDay) {
        if (splash.value < 1f || state.loading) {
            SplashScreen(progress = splash.value)
            return@PlantagotchiTheme
        }

        val plant = state.player.activePlant
        val label = buildString {
            append(speciesName(plant.species))
            append(" · ")
            append(if (plant.dead) stringResource(Res.string.plant_withered) else stageName(plant.stage))
            if (state.player.plants.size > 1) {
                append("  ")
                append(stringResource(Res.string.plant_counter, state.player.activeIndex + 1, state.player.plants.size))
            }
        }
        val texts = SceneTexts(
            needsLabel = stringResource(Res.string.needs_label),
            plantLabel = label,
            replant = stringResource(Res.string.replant),
            compost = stringResource(Res.string.compost),
        )

        Box {
            GameCanvas(
                state = state,
                texts = texts,
                events = events,
                onPet = actions.pet,
                onSelectPlant = actions.selectPlant,
                onTap = { target ->
                    when (target) {
                        SceneTarget.SHOP, SceneTarget.NEED_FERTILIZER, SceneTarget.NEED_WATER -> sheet = Sheet.SHOP
                        SceneTarget.MENU -> sheet = Sheet.MENU
                        SceneTarget.WEATHER -> sheet = Sheet.WEATHER
                        SceneTarget.CHEST -> actions.claimReward()
                        SceneTarget.REPLANT -> actions.replant()
                        SceneTarget.COMPOST -> actions.compost()
                        SceneTarget.NEED_LIGHT -> scope.launch {
                            hint = getString(if (state.luxFromSensor) Res.string.hint_light_sensor else Res.string.hint_light_estimated)
                        }
                        SceneTarget.NEED_LOVE -> scope.launch { hint = getString(Res.string.hint_love) }
                        SceneTarget.PLANT -> Unit
                    }
                },
            )

            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 150.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    text = hint.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlantagotchiColors.OffWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color(0xE62B1D24)).border(2.dp, Outline).padding(12.dp),
                )
            }

            if (about) MothlightAboutScreen(onClose = { about = false })
        }

        if (sheet != Sheet.NONE) {
            ModalBottomSheet(
                onDismissRequest = { sheet = Sheet.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                containerColor = Panel,
            ) {
                when (sheet) {
                    Sheet.SHOP -> ShopSheet(
                        player = state.player,
                        now = state.now,
                        onBuy = { item -> actions.buy(item).also { if (it is PurchaseResult.Success) sheet = Sheet.NONE } },
                        onSelectPot = {
                            actions.selectPot(it)
                            sheet = Sheet.NONE
                        },
                        onSelectScenery = {
                            actions.selectScenery(it)
                            sheet = Sheet.NONE
                        },
                        onSelectGround = {
                            actions.selectGround(it)
                            sheet = Sheet.NONE
                        },
                    )
                    Sheet.MENU -> MenuSheet { entry ->
                        sheet = when (entry) {
                            MenuEntry.FRIENDS -> Sheet.FRIENDS
                            MenuEntry.SETTINGS -> Sheet.SETTINGS
                            MenuEntry.ABOUT -> {
                                about = true
                                Sheet.NONE
                            }
                        }
                    }
                    Sheet.WEATHER -> WeatherSheet(state.weather, state.season)
                    Sheet.FRIENDS -> FriendsSheet(
                        available = state.friendsAvailable,
                        ownCode = state.player.friendCode,
                        friends = state.player.friends,
                        infos = state.friends,
                        onAdd = actions.addFriend,
                        onRemove = actions.removeFriend,
                        onSendWater = actions.sendWater,
                        onRefresh = actions.refreshFriends,
                    )
                    Sheet.SETTINGS -> SettingsSheet(state.settings, state.cameraLightSupported, actions.updateSettings)
                    Sheet.NONE -> Unit
                }
            }
        }
    }
}
