package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.coin
import dev.mothlight.plantagotchi.ui.game.CodeSprites
import dev.mothlight.plantagotchi.ui.game.PixelSprite
import dev.mothlight.plantagotchi.ui.game.aspectRatioOrDefault
import dev.mothlight.plantagotchi.ui.game.rememberPotPainters
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private val Panel = Color(0xFF2B1D24)
private val PanelLight = Color(0xFF3F282F)
private val Outline = Color(0xFF222034)

@Composable
fun ShopSheet(
    player: PlayerState,
    now: Long,
    onBuy: (ShopItem) -> PurchaseResult,
    onSelectPot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_000)
            message = null
        }
    }
    val pots = rememberPotPainters()

    fun buy(item: ShopItem) {
        message = when (onBuy(item)) {
            is PurchaseResult.Success -> null
            PurchaseResult.NotEnoughCoins -> "Not enough coins. Pet your plant and give it light!"
            PurchaseResult.NotNeeded -> "Your plant doesn't need that right now."
            PurchaseResult.AlreadyOwned -> "You already own that."
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Panel)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shop", style = MaterialTheme.typography.headlineMedium, color = PlantagotchiColors.Sunshine, modifier = Modifier.weight(1f))
            CoinAmount(player.coins)
        }
        Text(
            text = message ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFF9E9E),
            modifier = Modifier.padding(vertical = 6.dp),
        )

        SectionTitle("Supplies")
        ShopCatalog.supplies.forEach { item ->
            val (enabled, label) = when {
                item is ShopItem.Refill && player.needValue(item.need) >= GameRules.MAX -> false to "Full"
                else -> (player.coins >= item.price) to "${item.price}"
            }
            val description = if (item is ShopItem.GrowLamp && player.isLampActive(now)) {
                "Active, ${((player.lampUntil - now) / 60_000).coerceAtLeast(1)} min left. Buy to extend."
            } else {
                item.description
            }
            SupplyRow(item, description, enabled, label) { buy(item) }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Pots")
        ShopCatalog.pots.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { pot ->
                    PotCard(
                        pot = pot,
                        painter = pots.getValue(pot.id),
                        owned = pot.id in player.ownedPots,
                        selected = pot.id == player.selectedPot,
                        affordable = player.coins >= pot.price,
                        onBuy = { buy(pot) },
                        onSelect = { onSelectPot(pot.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = PlantagotchiColors.OffWhite,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun CoinAmount(coins: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(Res.drawable.coin),
            contentDescription = "Coins",
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("$coins", style = MaterialTheme.typography.titleMedium, color = PlantagotchiColors.Sunshine)
    }
}

@Composable
private fun SupplyRow(item: ShopItem, description: String, enabled: Boolean, buttonLabel: String, onBuy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(PanelLight)
            .border(2.dp, Outline)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpritePreview(item.sprite(), Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = PlantagotchiColors.OffWhite)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke)
        }
        PixelButton(buttonLabel, enabled, showCoin = buttonLabel != "Full", onClick = onBuy)
    }
}

@Composable
private fun PotCard(
    pot: ShopItem.Pot,
    painter: Painter,
    owned: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onBuy: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(if (selected) Color(0xFF4A3540) else PanelLight)
            .border(2.dp, if (selected) PlantagotchiColors.Sunshine else Outline)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val aspect = painter.aspectRatioOrDefault(190f / 296f)
            val w = minOf(size.width, size.height / aspect)
            val h = w * aspect
            translate((size.width - w) / 2, size.height - h) {
                with(painter) { draw(Size(w, h)) }
            }
        }
        Text(
            pot.name,
            style = MaterialTheme.typography.labelSmall,
            color = PlantagotchiColors.OffWhite,
            textAlign = TextAlign.Center,
            minLines = 2,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        when {
            selected -> PixelButton("In use", enabled = false, showCoin = false) {}
            owned -> PixelButton("Use", enabled = true, showCoin = false, onClick = onSelect)
            else -> PixelButton("${pot.price}", enabled = affordable, showCoin = true, onClick = onBuy)
        }
    }
}

@Composable
private fun PixelButton(text: String, enabled: Boolean, showCoin: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (enabled) PlantagotchiColors.Water else Color(0xFF5A5060))
            .border(2.dp, Outline)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCoin) {
            Image(
                painter = painterResource(Res.drawable.coin),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Outline else Color(0xFFB8AFBF))
    }
}

@Composable
private fun SpritePreview(sprite: PixelSprite, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) { sprite.drawFit(this, size) }
    }
}

private fun ShopItem.sprite(): PixelSprite = when (this) {
    is ShopItem.Refill -> if (need == Need.WATER) CodeSprites.wateringCan else CodeSprites.fertilizerBag
    is ShopItem.GrowLamp -> CodeSprites.growLamp
    is ShopItem.Pot -> CodeSprites.shopBag
}

private fun PlayerState.needValue(need: Need): Double = when (need) {
    Need.WATER -> water
    Need.FERTILIZER -> fertilizer
    Need.LIGHT -> lux
    Need.LOVE -> love
}
