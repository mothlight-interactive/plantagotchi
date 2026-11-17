package dev.mothlight.plantagotchi.data.shop

import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.data.state.Species

/** Pots are named after the bundled drawables `ic_pot<shape><color>`. */
object PotId {
    const val DEFAULT = "pot12"

    val all: List<String> = (1..3).flatMap { shape -> (1..4).map { color -> "pot$shape$color" } }

    fun shape(id: String): Int = id.getOrNull(3)?.digitToIntOrNull() ?: 1
    fun color(id: String): Int = id.getOrNull(4)?.digitToIntOrNull() ?: 2
}

enum class Need { WATER, FERTILIZER, LIGHT, LOVE }

sealed class ShopItem {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val price: Int

    /** Refills a need instantly. */
    data class Refill(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
        val need: Need,
        val amount: Double,
    ) : ShopItem()

    /** Grow lamp: the plant gets enough light for [durationMillis], regardless of the real light. */
    data class GrowLamp(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
        val durationMillis: Long,
    ) : ShopItem()

    /** Adds a new plant of [species]. */
    data class Seed(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
        val species: Species,
    ) : ShopItem()

    /** Brings a withered plant back to life. */
    data class Revive(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
    ) : ShopItem()

    data class Backdrop(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
        val scenery: Scenery,
    ) : ShopItem()

    data class Floor(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
        val ground: Ground,
    ) : ShopItem()

    data class Pot(
        override val id: String,
        override val name: String,
        override val description: String,
        override val price: Int,
    ) : ShopItem()
}

object ShopCatalog {
    val wateringCan = ShopItem.Refill(
        id = "watering_can",
        name = "Watering can",
        description = "+35 water",
        price = 15,
        need = Need.WATER,
        amount = 35.0,
    )

    val fertilizerBag = ShopItem.Refill(
        id = "fertilizer_bag",
        name = "Fertilizer",
        description = "+50 fertilizer",
        price = 25,
        need = Need.FERTILIZER,
        amount = 50.0,
    )

    val growLamp = ShopItem.GrowLamp(
        id = "grow_lamp",
        name = "Grow lamp",
        description = "Full light for 30 min",
        price = 60,
        durationMillis = 30 * 60 * 1000L,
    )

    val revive = ShopItem.Revive(
        id = "revive",
        name = "Revival tonic",
        description = "Brings a withered plant back",
        price = 120,
    )

    val seeds: List<ShopItem.Seed> = Species.entries.map { species ->
        ShopItem.Seed(
            id = "seed_${species.name.lowercase()}",
            name = species.name.lowercase().replaceFirstChar { it.uppercase() } + " seeds",
            description = "A new plant",
            price = species.seedPrice,
            species = species,
        )
    }

    val backdrops: List<ShopItem.Backdrop> = Scenery.entries.map {
        ShopItem.Backdrop("scenery_${it.name.lowercase()}", it.name.lowercase(), "Scenery", it.price, it)
    }

    val floors: List<ShopItem.Floor> = Ground.entries.map {
        ShopItem.Floor("ground_${it.name.lowercase()}", it.name.lowercase(), "Ground", it.price, it)
    }

    private val colorNames = mapOf(1 to "Terracotta", 2 to "Lavender", 3 to "Rose", 4 to "Mint")
    private val shapeNames = mapOf(1 to "Bowl", 2 to "Pot", 3 to "Vase")
    private val shapePrices = mapOf(1 to 80, 2 to 120, 3 to 200)

    val pots: List<ShopItem.Pot> = PotId.all.map { id ->
        val shape = PotId.shape(id)
        ShopItem.Pot(
            id = id,
            name = "${colorNames[PotId.color(id)]} ${shapeNames[shape]}",
            description = "A new home for your plant",
            price = if (id == PotId.DEFAULT) 0 else shapePrices.getValue(shape),
        )
    }

    val supplies: List<ShopItem> = listOf(wateringCan, fertilizerBag, growLamp)

    fun byId(id: String): ShopItem? = (supplies + seeds + revive + pots + backdrops + floors).firstOrNull { it.id == id }
}
