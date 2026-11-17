package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShopTest {

    @Test
    fun refillCostsCoinsAndIsCapped() {
        val result = Shop.purchase(PlayerState(water = 80.0, coins = 20), ShopCatalog.wateringCan, nowMillis = 0)
        val state = assertIs<PurchaseResult.Success>(result).state
        assertEquals(100.0, state.water)
        assertEquals(5, state.coins)
    }

    @Test
    fun cannotBuyWhatIsNotNeededOrAffordable() {
        assertEquals(PurchaseResult.NotNeeded, Shop.purchase(PlayerState(water = 100.0, coins = 99), ShopCatalog.wateringCan, 0))
        assertEquals(PurchaseResult.NotEnoughCoins, Shop.purchase(PlayerState(fertilizer = 10.0, coins = 1), ShopCatalog.fertilizerBag, 0))
    }

    @Test
    fun growLampStacksAndProvidesLight() {
        val lamp = ShopCatalog.growLamp
        val first = assertIs<PurchaseResult.Success>(Shop.purchase(PlayerState(coins = 200), lamp, nowMillis = 1_000)).state
        assertEquals(1_000 + lamp.durationMillis, first.lampUntil)
        val second = assertIs<PurchaseResult.Success>(Shop.purchase(first, lamp, nowMillis = 2_000)).state
        assertEquals(1_000 + 2 * lamp.durationMillis, second.lampUntil)

        assertEquals(GameRules.LAMP_LUX, GameRules.effectiveLux(second, 0f, nowMillis = 5_000))
        assertEquals(0f, GameRules.effectiveLux(second, 0f, nowMillis = second.lampUntil))
    }

    @Test
    fun potsAreBoughtOnceAndSelected() {
        val pot = ShopCatalog.pots.first { it.id == "pot33" }
        val state = assertIs<PurchaseResult.Success>(Shop.purchase(PlayerState(coins = 500), pot, 0)).state
        assertTrue("pot33" in state.ownedPots)
        assertEquals("pot33", state.selectedPot)
        assertEquals(PurchaseResult.AlreadyOwned, Shop.purchase(state, pot, 0))

        assertEquals(PotId.DEFAULT, Shop.selectPot(state, PotId.DEFAULT).selectedPot)
        assertEquals("pot33", Shop.selectPot(state, "pot21").selectedPot, "cannot select a pot that is not owned")
    }

    @Test
    fun catalogContainsAllPots() {
        assertEquals(12, ShopCatalog.pots.size)
        assertEquals(0, ShopCatalog.pots.first { it.id == PotId.DEFAULT }.price)
    }
}
