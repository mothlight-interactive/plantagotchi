package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShopTest {

    private fun player(plant: PlantState = PlantState(), coins: Int = 0) =
        PlayerState(plants = listOf(plant), activePlantId = plant.id, coins = coins)

    @Test
    fun refillCostsCoinsAndIsCapped() {
        val result = Shop.purchase(player(PlantState(water = 80.0), coins = 20), ShopCatalog.wateringCan, nowMillis = 0)
        val state = assertIs<PurchaseResult.Success>(result).state
        assertEquals(100.0, state.activePlant.water)
        assertEquals(5, state.coins)
    }

    @Test
    fun cannotBuyWhatIsNotNeededOrAffordable() {
        assertEquals(PurchaseResult.NotNeeded, Shop.purchase(player(PlantState(water = 100.0), 99), ShopCatalog.wateringCan, 0))
        assertEquals(PurchaseResult.NotEnoughCoins, Shop.purchase(player(PlantState(fertilizer = 10.0), 1), ShopCatalog.fertilizerBag, 0))
        assertEquals(PurchaseResult.PlantIsWithered, Shop.purchase(player(PlantState(dead = true, water = 0.0), 99), ShopCatalog.wateringCan, 0))
        assertEquals(PurchaseResult.NotNeeded, Shop.purchase(player(coins = 999), ShopCatalog.revive, 0))
    }

    @Test
    fun growLampStacksAndProvidesLight() {
        val lamp = ShopCatalog.growLamp
        val first = assertIs<PurchaseResult.Success>(Shop.purchase(player(coins = 200), lamp, nowMillis = 1_000)).state
        assertEquals(1_000 + lamp.durationMillis, first.lampUntil)
        val second = assertIs<PurchaseResult.Success>(Shop.purchase(first, lamp, nowMillis = 2_000)).state
        assertEquals(1_000 + 2 * lamp.durationMillis, second.lampUntil)

        assertEquals(GameRules.LAMP_LUX, GameRules.effectiveLux(second, 0f, nowMillis = 5_000))
        assertEquals(0f, GameRules.effectiveLux(second, 0f, nowMillis = second.lampUntil))
    }

    @Test
    fun potsAreBoughtOnceAndPutOnTheActivePlant() {
        val pot = ShopCatalog.pots.first { it.id == "pot33" }
        val state = assertIs<PurchaseResult.Success>(Shop.purchase(player(coins = 500), pot, 0)).state
        assertTrue("pot33" in state.ownedPots)
        assertEquals("pot33", state.activePlant.pot)
        assertEquals(PurchaseResult.AlreadyOwned, Shop.purchase(state, pot, 0))

        assertEquals(PotId.DEFAULT, Shop.selectPot(state, PotId.DEFAULT).activePlant.pot)
        assertEquals("pot33", Shop.selectPot(state, "pot21").activePlant.pot, "cannot use a pot that is not owned")
    }

    @Test
    fun seedsAddPlantsUpToTheLimit() {
        val seed = ShopCatalog.seeds.first { it.species == Species.CACTUS }
        var state = player(coins = 10_000)
        repeat(PlayerState.MAX_PLANTS - 1) {
            state = assertIs<PurchaseResult.Success>(Shop.purchase(state, seed, 0)).state
        }
        assertEquals(PlayerState.MAX_PLANTS, state.plants.size)
        assertEquals(Species.CACTUS, state.activePlant.species, "the new plant becomes active")
        assertEquals(PurchaseResult.NoFreeSlot, Shop.purchase(state, seed, 0))
    }

    @Test
    fun withered_plants_can_be_revived_replanted_or_composted() {
        val dead = PlantState(dead = true, growth = 200_000.0, pot = "pot21")
        val revived = assertIs<PurchaseResult.Success>(Shop.purchase(player(dead, 500), ShopCatalog.revive, 0)).state.activePlant
        assertFalse(revived.dead)
        assertEquals(2, revived.stage, "revival keeps the growth")

        val replanted = Shop.replant(player(dead), nowMillis = 7).activePlant
        assertFalse(replanted.dead)
        assertEquals(0, replanted.stage)
        assertEquals("pot21", replanted.pot)

        assertEquals(1, Shop.compost(player(dead)).plants.size, "the last plant cannot be composted")
        val two = PlayerState(plants = listOf(dead, PlantState()), activePlantId = dead.id)
        assertEquals(1, Shop.compost(two).plants.size)
    }

    @Test
    fun catalogContainsAllPots() {
        assertEquals(12, ShopCatalog.pots.size)
        assertEquals(0, ShopCatalog.pots.first { it.id == PotId.DEFAULT }.price)
    }
}
