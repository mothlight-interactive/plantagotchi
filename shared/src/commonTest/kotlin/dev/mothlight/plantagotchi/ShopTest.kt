package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.shop.PotId
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.Environment
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Species
import dev.mothlight.plantagotchi.data.weather.WeatherState
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
        assertEquals(1_000 + lamp.durationMillis, first.activePlant.lampUntil)
        val second = assertIs<PurchaseResult.Success>(Shop.purchase(first, lamp, nowMillis = 2_000)).state
        assertEquals(1_000 + 2 * lamp.durationMillis, second.activePlant.lampUntil)

        assertEquals(GameRules.LAMP_LUX, GameRules.effectiveLux(second.activePlant, 0f, nowMillis = 5_000))
        assertEquals(0f, GameRules.effectiveLux(second.activePlant, 0f, nowMillis = second.activePlant.lampUntil))
    }

    @Test
    fun growLampLightsOnlyItsPlant() {
        val lit = PlantState(id = "lit", lux = 50.0, lampUntil = 10_000)
        val dark = PlantState(id = "dark", lux = 50.0)
        val state = PlayerState(plants = listOf(lit, dark), activePlantId = "lit")
        val after = GameRules.tick(state, Environment(0f, WeatherState.CLEAR_SKY, 15.0, 5_000), seconds = 10.0)
        assertTrue(after.plants[0].lux > 50.0, "the lamp lights its plant")
        assertTrue(after.plants[1].lux < 50.0, "the neighbour stays in the dark")
        val bought = assertIs<PurchaseResult.Success>(Shop.purchase(state.copy(activePlantId = "dark", coins = 200), ShopCatalog.growLamp, 0)).state
        assertTrue(bought.plants[1].lampOn(1), "a lamp is bought for the active plant")
        assertEquals(PurchaseResult.PlantIsWithered, Shop.purchase(state.copy(plants = listOf(dark.copy(dead = true)), coins = 200), ShopCatalog.growLamp, 0))
    }

    @Test
    fun oldSavesGetTheirLampOnEveryPlant() {
        val old = PlayerState(plants = listOf(PlantState(id = "a"), PlantState(id = "b")), lampUntil = 9_000)
        val migrated = old.withPerPlantLamps()
        assertEquals(listOf(9_000L, 9_000L), migrated.plants.map { it.lampUntil })
        assertEquals(0L, migrated.lampUntil)
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
