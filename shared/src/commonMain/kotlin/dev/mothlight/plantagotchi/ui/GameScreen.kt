package dev.mothlight.plantagotchi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.ui.game.GameCanvas
import dev.mothlight.plantagotchi.ui.game.SceneTarget
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun GameScreen(viewModel: PlantagotchiViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameContent(
        state = state,
        events = viewModel.events,
        onPet = viewModel::pet,
        onBuy = viewModel::buy,
        onSelectPot = viewModel::selectPot,
    )
}

private enum class Sheet { NONE, SHOP, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameContent(
    state: GameUiState,
    events: Flow<GameEvent> = emptyFlow(),
    onPet: (Float) -> Unit = {},
    onBuy: (ShopItem) -> PurchaseResult = { PurchaseResult.NotEnoughCoins },
    onSelectPot: (String) -> Unit = {},
    initialShopOpen: Boolean = false,
) {
    var sheet by remember { mutableStateOf(if (initialShopOpen) Sheet.SHOP else Sheet.NONE) }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(2_500)
            hint = null
        }
    }

    PlantagotchiTheme(darkTheme = !state.weather.isDay) {
        if (state.loading) {
            LoadingScreen()
            return@PlantagotchiTheme
        }

        Box {
            GameCanvas(
                state = state,
                events = events,
                onPet = onPet,
                onTap = { target ->
                    when (target) {
                        SceneTarget.SHOP, SceneTarget.NEED_FERTILIZER, SceneTarget.NEED_WATER -> sheet = Sheet.SHOP
                        SceneTarget.NEED_LIGHT -> {
                            hint = if (state.luxFromSensor) "Bring your phone into bright light, or buy a grow lamp."
                            else "Your plant gets light during the day. Buy a grow lamp for more."
                            sheet = Sheet.NONE
                        }
                        SceneTarget.NEED_LOVE -> hint = "Swipe over your plant to pet it."
                        SceneTarget.ABOUT -> sheet = Sheet.ABOUT
                        SceneTarget.PLANT -> Unit
                    }
                },
            )

            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    text = hint.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlantagotchiColors.OffWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xE62B1D24))
                        .border(2.dp, Color(0xFF222034))
                        .padding(12.dp),
                )
            }
        }

        if (sheet != Sheet.NONE) {
            ModalBottomSheet(
                onDismissRequest = { sheet = Sheet.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                containerColor = Color(0xFF2B1D24),
            ) {
                when (sheet) {
                    Sheet.SHOP -> ShopSheet(
                        player = state.player,
                        now = state.now,
                        onBuy = { item ->
                            onBuy(item).also { if (it is PurchaseResult.Success) sheet = Sheet.NONE }
                        },
                        onSelectPot = {
                            onSelectPot(it)
                            sheet = Sheet.NONE
                        },
                    )
                    Sheet.ABOUT -> AboutScreen(Modifier.fillMaxHeight(0.95f))
                    Sheet.NONE -> Unit
                }
            }
        }
    }
}
