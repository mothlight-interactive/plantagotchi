package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import dev.mothlight.plantagotchi.data.shop.Shop.need
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.btn_close
import dev.mothlight.plantagotchi.resources.btn_full
import dev.mothlight.plantagotchi.resources.btn_in_use
import dev.mothlight.plantagotchi.resources.btn_use
import dev.mothlight.plantagotchi.resources.err_coins
import dev.mothlight.plantagotchi.resources.err_not_needed
import dev.mothlight.plantagotchi.resources.err_owned
import dev.mothlight.plantagotchi.resources.err_slots
import dev.mothlight.plantagotchi.resources.err_withered
import dev.mothlight.plantagotchi.resources.item_fertilizer
import dev.mothlight.plantagotchi.resources.item_fertilizer_desc
import dev.mothlight.plantagotchi.resources.item_grow_lamp
import dev.mothlight.plantagotchi.resources.item_grow_lamp_active
import dev.mothlight.plantagotchi.resources.item_grow_lamp_desc
import dev.mothlight.plantagotchi.resources.item_revive
import dev.mothlight.plantagotchi.resources.item_revive_desc
import dev.mothlight.plantagotchi.resources.item_seed
import dev.mothlight.plantagotchi.resources.item_watering_can
import dev.mothlight.plantagotchi.resources.item_watering_can_desc
import dev.mothlight.plantagotchi.resources.shop_plants
import dev.mothlight.plantagotchi.resources.shop_ground
import dev.mothlight.plantagotchi.resources.shop_pots
import dev.mothlight.plantagotchi.resources.shop_scenery
import dev.mothlight.plantagotchi.resources.shop_supplies
import dev.mothlight.plantagotchi.resources.shop_title
import dev.mothlight.plantagotchi.ui.game.CodeSprites
import dev.mothlight.plantagotchi.ui.game.PixelSprite
import dev.mothlight.plantagotchi.ui.game.PlantPreview
import dev.mothlight.plantagotchi.ui.game.ScenePreview
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.ui.game.aspectRatioOrDefault
import dev.mothlight.plantagotchi.ui.game.rememberPotPainters
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val tonic = PixelSprite(
    listOf(
        "..oo..",
        "..ww..",
        ".owwo.",
        "oppppo",
        "opPppo",
        "opppPo",
        "oppppo",
        ".oooo.",
    ),
    mapOf('o' to Color(0xFF222034), 'w' to Color(0xFFB8AFBF), 'p' to Color(0xFF9FD3C7), 'P' to Color(0xFFE8FFF8)),
)

@Composable
fun ShopSheet(
    player: PlayerState,
    now: Long,
    onBuy: (ShopItem) -> PurchaseResult,
    onSelectPot: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSelectScenery: (Scenery) -> Unit = {},
    onSelectGround: (Ground) -> Unit = {},
    onClose: (() -> Unit)? = null,
) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }
    val scope = rememberCoroutineScope()
    val pots = rememberPotPainters()
    val plant = player.activePlant

    fun buy(item: ShopItem) {
        val result = onBuy(item)
        scope.launch {
            message = when (result) {
                is PurchaseResult.Success -> null
                PurchaseResult.NotEnoughCoins -> getString(Res.string.err_coins)
                PurchaseResult.NotNeeded -> getString(Res.string.err_not_needed)
                PurchaseResult.AlreadyOwned -> getString(Res.string.err_owned)
                PurchaseResult.PlantIsWithered -> getString(Res.string.err_withered)
                PurchaseResult.NoFreeSlot -> getString(Res.string.err_slots, PlayerState.MAX_PLANTS)
            }
        }
    }

    Column(modifier.fillMaxWidth().background(Panel)) {
        // Pinned header: title, coins, close and the purchase message stay put while the items scroll.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle(stringResource(Res.string.shop_title), Modifier.weight(1f))
                CoinAmount(player.coins)
                if (onClose != null) {
                    Spacer(Modifier.width(12.dp))
                    PixelButton(stringResource(Res.string.btn_close), color = PlantagotchiColors.OffWhite, onClick = onClose)
                }
            }
            Text(
                text = message ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9E9E),
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {

            SectionTitle(stringResource(Res.string.shop_supplies))
            if (plant.dead) {
                ItemRow(tonic, stringResource(Res.string.item_revive), stringResource(Res.string.item_revive_desc)) {
                    PixelButton("${ShopCatalog.revive.price}", enabled = player.coins >= ShopCatalog.revive.price, showCoin = true) {
                        buy(ShopCatalog.revive)
                    }
                }
            }
            ShopCatalog.supplies.forEach { item ->
                val full = item is ShopItem.Refill && plant.need(item.need) >= GameRules.MAX
                val (name, description) = when (item) {
                    ShopCatalog.wateringCan -> Res.string.item_watering_can to Res.string.item_watering_can_desc
                    ShopCatalog.fertilizerBag -> Res.string.item_fertilizer to Res.string.item_fertilizer_desc
                    else -> Res.string.item_grow_lamp to Res.string.item_grow_lamp_desc
                }
                val descriptionText = if (item is ShopItem.GrowLamp && player.isLampActive(now)) {
                    stringResource(Res.string.item_grow_lamp_active, ((player.lampUntil - now) / 60_000).coerceAtLeast(1))
                } else {
                    stringResource(description)
                }
                ItemRow(item.sprite(), stringResource(name), descriptionText) {
                    if (full) PixelButton(stringResource(Res.string.btn_full), enabled = false) {}
                    else PixelButton("${item.price}", enabled = player.coins >= item.price && !plant.dead, showCoin = true) { buy(item) }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("${stringResource(Res.string.shop_plants)} (${player.plants.size}/${PlayerState.MAX_PLANTS})")
            ShopCatalog.seeds.forEach { seed ->
                val name = stringResource(Res.string.item_seed, speciesName(seed.species))
                val description = stringResource(seed.species.descriptionRes())
                ItemRow(
                    preview = { PlantPreview(seed.species, maturity = 1f, seed = seed.id.hashCode(), modifier = Modifier.size(48.dp)) },
                    name = name,
                    description = description,
                ) {
                    PixelButton(
                        "${seed.price}",
                        enabled = player.coins >= seed.price && player.plants.size < PlayerState.MAX_PLANTS,
                        showCoin = true,
                    ) { buy(seed) }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(Res.string.shop_scenery))
            ShopCatalog.backdrops.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        DecorCard(
                            title = sceneryName(item.scenery),
                            preview = { ScenePreview(item.scenery, player.ground, Modifier.fillMaxWidth().height(64.dp)) },
                            owned = item.scenery in player.ownedScenery,
                            selected = item.scenery == player.scenery,
                            price = item.price,
                            affordable = player.coins >= item.price,
                            onBuy = { buy(item) },
                            onSelect = { onSelectScenery(item.scenery) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(Res.string.shop_ground))
            ShopCatalog.floors.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        DecorCard(
                            title = groundName(item.ground),
                            preview = { ScenePreview(player.scenery, item.ground, Modifier.fillMaxWidth().height(64.dp)) },
                            owned = item.ground in player.ownedGrounds,
                            selected = item.ground == player.ground,
                            price = item.price,
                            affordable = player.coins >= item.price,
                            onBuy = { buy(item) },
                            onSelect = { onSelectGround(item.ground) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(Res.string.shop_pots))
            ShopCatalog.pots.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pot ->
                        PotCard(
                            pot = pot,
                            painter = pots.getValue(pot.id),
                            owned = pot.id in player.ownedPots,
                            selected = pot.id == plant.pot,
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
}

@Composable
private fun ItemRow(sprite: PixelSprite, name: String, description: String, button: @Composable () -> Unit) =
    ItemRow({ SpritePreview(sprite, Modifier.size(48.dp)) }, name, description, button)

@Composable
private fun ItemRow(
    preview: @Composable () -> Unit,
    name: String,
    description: String,
    button: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).pixelPanel().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        preview()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = PlantagotchiColors.OffWhite)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PlantagotchiColors.AmethystSmoke)
        }
        button()
    }
}

@Composable
private fun DecorCard(
    title: String,
    preview: @Composable () -> Unit,
    owned: Boolean,
    selected: Boolean,
    price: Int,
    affordable: Boolean,
    onBuy: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.pixelPanel(selected).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        preview()
        Text(title, style = MaterialTheme.typography.labelSmall, color = PlantagotchiColors.OffWhite, modifier = Modifier.padding(vertical = 4.dp))
        when {
            selected -> PixelButton(stringResource(Res.string.btn_in_use), enabled = false) {}
            owned -> PixelButton(stringResource(Res.string.btn_use), onClick = onSelect)
            else -> PixelButton("$price", enabled = affordable, showCoin = true, onClick = onBuy)
        }
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
    Column(modifier.pixelPanel(selected).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val aspect = painter.aspectRatioOrDefault(190f / 296f)
            val w = minOf(size.width, size.height / aspect)
            val h = w * aspect
            translate((size.width - w) / 2, size.height - h) {
                with(painter) { draw(Size(w, h)) }
            }
        }
        Text(
            potName(pot.id),
            style = MaterialTheme.typography.labelSmall,
            color = PlantagotchiColors.OffWhite,
            textAlign = TextAlign.Center,
            minLines = 2,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        when {
            selected -> PixelButton(stringResource(Res.string.btn_in_use), enabled = false) {}
            owned -> PixelButton(stringResource(Res.string.btn_use), onClick = onSelect)
            else -> PixelButton("${pot.price}", enabled = affordable, showCoin = true, onClick = onBuy)
        }
    }
}

private fun ShopItem.sprite(): PixelSprite = when (this) {
    is ShopItem.Refill -> if (need == Need.WATER) CodeSprites.wateringCan else CodeSprites.fertilizerBag
    is ShopItem.GrowLamp -> CodeSprites.growLamp
    else -> CodeSprites.shopBag
}
