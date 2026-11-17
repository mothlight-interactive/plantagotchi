package dev.mothlight.plantagotchi.data.shop

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState

sealed interface PurchaseResult {
    data class Success(val state: PlayerState, val item: ShopItem) : PurchaseResult
    data object NotEnoughCoins : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    /** The need is already full (or the plant is fine), buying would waste coins. */
    data object NotNeeded : PurchaseResult
    data object PlantIsWithered : PurchaseResult
    data object NoFreeSlot : PurchaseResult
}

object Shop {

    fun purchase(state: PlayerState, item: ShopItem, nowMillis: Long): PurchaseResult {
        val plant = state.activePlant
        when (item) {
            is ShopItem.Pot -> if (item.id in state.ownedPots) return PurchaseResult.AlreadyOwned
            is ShopItem.Refill -> {
                if (plant.dead) return PurchaseResult.PlantIsWithered
                if (plant.need(item.need) >= GameRules.MAX) return PurchaseResult.NotNeeded
            }
            is ShopItem.Revive -> if (!plant.dead) return PurchaseResult.NotNeeded
            is ShopItem.Seed -> if (state.plants.size >= PlayerState.MAX_PLANTS) return PurchaseResult.NoFreeSlot
            is ShopItem.GrowLamp -> Unit
        }
        if (state.coins < item.price) return PurchaseResult.NotEnoughCoins

        val paid = state.copy(coins = state.coins - item.price)
        val result = when (item) {
            is ShopItem.Refill -> paid.updateActive { it.refill(item.need, item.amount) }
            is ShopItem.GrowLamp -> paid.copy(lampUntil = maxOf(nowMillis, state.lampUntil) + item.durationMillis)
            is ShopItem.Pot -> paid.copy(ownedPots = state.ownedPots + item.id).updateActive { it.copy(pot = item.id) }
            is ShopItem.Revive -> paid.updateActive {
                it.copy(dead = false, criticalSince = 0L, lux = 50.0, water = 50.0, love = 50.0, fertilizer = 50.0)
            }
            is ShopItem.Seed -> {
                val seedling = PlantState(species = item.species, plantedAt = nowMillis, love = 20.0)
                paid.copy(plants = state.plants + seedling, activePlantId = seedling.id)
            }
        }
        return PurchaseResult.Success(result, item)
    }

    /** Puts an owned pot on the active plant. */
    fun selectPot(state: PlayerState, potId: String): PlayerState =
        if (potId in state.ownedPots) state.updateActive { it.copy(pot = potId) } else state

    /** Replaces the withered active plant with a free sprout seedling in the same pot. */
    fun replant(state: PlayerState, nowMillis: Long): PlayerState {
        if (!state.activePlant.dead) return state
        return state.updateActive { PlantState(id = it.id, pot = it.pot, plantedAt = nowMillis, love = 20.0) }
    }

    /** Removes the withered active plant, if it is not the only one. */
    fun compost(state: PlayerState): PlayerState {
        val plant = state.activePlant
        if (!plant.dead || state.plants.size <= 1) return state
        val remaining = state.plants - plant
        return state.copy(plants = remaining, activePlantId = remaining.first().id)
    }

    internal fun PlantState.need(need: Need): Double = when (need) {
        Need.WATER -> water
        Need.FERTILIZER -> fertilizer
        Need.LIGHT -> lux
        Need.LOVE -> love
    }

    private fun PlantState.refill(need: Need, amount: Double): PlantState {
        fun Double.add() = (this + amount).coerceIn(0.0, GameRules.MAX)
        return when (need) {
            Need.WATER -> copy(water = water.add())
            Need.FERTILIZER -> copy(fertilizer = fertilizer.add())
            Need.LIGHT -> copy(lux = lux.add())
            Need.LOVE -> copy(love = love.add())
        }
    }
}
