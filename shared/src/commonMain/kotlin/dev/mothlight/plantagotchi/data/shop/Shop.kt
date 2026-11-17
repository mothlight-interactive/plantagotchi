package dev.mothlight.plantagotchi.data.shop

import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlayerState

sealed interface PurchaseResult {
    data class Success(val state: PlayerState, val item: ShopItem) : PurchaseResult
    data object NotEnoughCoins : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    /** The need is already full, buying would waste coins. */
    data object NotNeeded : PurchaseResult
}

object Shop {

    fun purchase(state: PlayerState, item: ShopItem, nowMillis: Long): PurchaseResult {
        if (item is ShopItem.Pot && item.id in state.ownedPots) return PurchaseResult.AlreadyOwned
        if (item is ShopItem.Refill && state.need(item.need) >= GameRules.MAX) return PurchaseResult.NotNeeded
        if (state.coins < item.price) return PurchaseResult.NotEnoughCoins

        val paid = state.copy(coins = state.coins - item.price)
        val result = when (item) {
            is ShopItem.Refill -> paid.refill(item.need, item.amount)
            is ShopItem.GrowLamp -> paid.copy(lampUntil = maxOf(nowMillis, state.lampUntil) + item.durationMillis)
            is ShopItem.Pot -> paid.copy(ownedPots = state.ownedPots + item.id, selectedPot = item.id)
        }
        return PurchaseResult.Success(result, item)
    }

    fun selectPot(state: PlayerState, potId: String): PlayerState =
        if (potId in state.ownedPots) state.copy(selectedPot = potId) else state

    private fun PlayerState.need(need: Need): Double = when (need) {
        Need.WATER -> water
        Need.FERTILIZER -> fertilizer
        Need.LIGHT -> lux
        Need.LOVE -> love
    }

    private fun PlayerState.refill(need: Need, amount: Double): PlayerState {
        fun Double.add() = (this + amount).coerceIn(0.0, GameRules.MAX)
        return when (need) {
            Need.WATER -> copy(water = water.add())
            Need.FERTILIZER -> copy(fertilizer = fertilizer.add())
            Need.LIGHT -> copy(lux = lux.add())
            Need.LOVE -> copy(love = love.add())
        }
    }
}
