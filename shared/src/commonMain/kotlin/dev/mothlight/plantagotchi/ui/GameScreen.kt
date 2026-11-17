package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.chest
import dev.mothlight.plantagotchi.resources.coint_counter
import dev.mothlight.plantagotchi.resources.fertilizer
import dev.mothlight.plantagotchi.resources.heart
import dev.mothlight.plantagotchi.resources.ic_button_bg
import dev.mothlight.plantagotchi.resources.ic_ground
import dev.mothlight.plantagotchi.resources.ic_plant1
import dev.mothlight.plantagotchi.resources.ic_pot12
import dev.mothlight.plantagotchi.resources.sunshine
import dev.mothlight.plantagotchi.resources.water
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiTheme
import dev.mothlight.plantagotchi.ui.weatherui.WeatherLandscape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun GameScreen(viewModel: PlantagotchiViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameContent(state = state, onPet = viewModel::pet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameContent(state: GameUiState, onPet: (Float) -> Unit) {
    var showAbout by remember { mutableStateOf(false) }

    PlantagotchiTheme(darkTheme = !state.weather.isDay) {
        if (state.loading) {
            LoadingScreen()
            return@PlantagotchiTheme
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPet(dragAmount.y)
                    }
                }
        ) {
            WeatherLandscape(state.weather)

            Image(
                painter = painterResource(Res.drawable.ic_ground),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxWidth().height(GROUND_HEIGHT).align(Alignment.BottomCenter),
            )

            Plant(Modifier.align(Alignment.BottomCenter).padding(bottom = GROUND_HEIGHT - 48.dp))

            CoinCounter(
                coins = state.player.coins,
                modifier = Modifier.statusBarsPadding().align(Alignment.TopStart),
            )

            Image(
                painter = painterResource(Res.drawable.chest),
                contentDescription = "About",
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(top = 35.dp, end = 13.dp)
                    .size(40.dp)
                    .clickable { showAbout = true },
            )

            NeedsBar(
                player = state.player,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        if (showAbout) {
            ModalBottomSheet(
                onDismissRequest = { showAbout = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
            ) {
                AboutScreen(Modifier.fillMaxHeight(0.95f))
            }
        }
    }
}

@Composable
private fun Plant(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(Res.drawable.ic_plant1),
            contentDescription = "Your plant",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().offset(y = 2.dp),
        )
        Image(
            painter = painterResource(Res.drawable.ic_pot12),
            contentDescription = "Pot",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CoinCounter(coins: Int, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 20.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(Res.drawable.coint_counter),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = "$coins",
            color = Color.Black,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun NeedsBar(player: PlayerState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = "What does your plant need?",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            NeedBubble(player.love, PlantagotchiColors.Love, Res.drawable.heart, "Love")
            NeedBubble(player.fertilizer, PlantagotchiColors.Fertilizer, Res.drawable.fertilizer, "Fertilizer")
            NeedBubble(player.water, PlantagotchiColors.Water, Res.drawable.water, "Water")
            NeedBubble(player.lux, PlantagotchiColors.Sunshine, Res.drawable.sunshine, "Sunshine")
        }
    }
}

/** Round indicator that empties from the top as the need decreases. */
@Composable
private fun NeedBubble(percent: Double, color: Color, icon: DrawableResource, label: String) {
    val missing = (1f - (percent / 100.0).toFloat()).coerceIn(0f, 1f)
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(BUBBLE_SIZE).clip(CircleShape).background(color)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BUBBLE_SIZE * missing)
                    .background(PlantagotchiColors.BubbleEmpty)
            )
        }
        Image(
            painter = painterResource(icon),
            contentDescription = "$label ${percent.toInt()}%",
            modifier = Modifier.size(25.dp),
        )
        Image(
            painter = painterResource(Res.drawable.ic_button_bg),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
    }
}

private val GROUND_HEIGHT = 128.dp
private val BUBBLE_SIZE = 50.dp
