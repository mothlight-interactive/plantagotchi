package dev.mothlight.plantagotchi

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.mothlight.plantagotchi.data.shop.PurchaseResult
import dev.mothlight.plantagotchi.data.shop.Shop
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.PlayerStateCodec
import dev.mothlight.plantagotchi.data.state.RoomSpot
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.ui.game.IndoorScenes
import dev.mothlight.plantagotchi.ui.game.IndoorToy
import dev.mothlight.plantagotchi.ui.game.IndoorToys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenesTest {

    @Test
    fun newPlayersStartInTheFreeRoom() {
        val state = PlayerState()
        assertEquals(Scenery.ROOM, state.scenery)
        assertTrue(Scenery.ROOM in state.ownedScenery)
        assertEquals(0, Scenery.ROOM.price)
        assertEquals(state, state.withDefaultScene(), "nothing to migrate")
    }

    @Test
    fun existingSavesAreSwitchedToTheRoomOnce() {
        val old = PlayerState(
            scenery = Scenery.BEACH,
            ownedScenery = setOf(Scenery.MOUNTAINS, Scenery.BEACH),
            ground = Ground.SNOW,
            ownedGrounds = setOf(Ground.GRASS, Ground.SNOW),
            sceneVersion = 0,
        )
        val migrated = old.withDefaultScene()
        assertEquals(Scenery.ROOM, migrated.scenery)
        assertEquals(setOf(Scenery.MOUNTAINS, Scenery.BEACH, Scenery.ROOM), migrated.ownedScenery, "owned scenes stay owned")
        assertEquals(PlayerState.SCENE_VERSION, migrated.sceneVersion)

        // Once migrated, the player's choice sticks
        val chosen = Shop.selectScenery(migrated, Scenery.BEACH)
        assertEquals(Scenery.BEACH, chosen.withDefaultScene().scenery)
        assertEquals(Scenery.BEACH, PlayerStateCodec.decode(PlayerStateCodec.encode(chosen)).scenery)
    }

    @Test
    fun savesWithoutTheVersionAreMigratedWhenDecoded() {
        val json = """{"coins":3,"plants":[{"species":"BONSAI"}],"scenery":"CITY","ownedScenery":["MOUNTAINS","CITY"],""" +
            """"ground":"WOOD","ownedGrounds":["GRASS","WOOD"],"someFutureField":1}"""
        val state = PlayerStateCodec.decode(json)
        assertEquals(Scenery.ROOM, state.scenery)
        assertEquals(setOf(Scenery.MOUNTAINS, Scenery.CITY, Scenery.ROOM), state.ownedScenery)
        assertEquals(PlayerState.SCENE_VERSION, state.sceneVersion)
        assertEquals(Ground.WOOD, state.ground, "old fields still load")
    }

    @Test
    fun catalogHasOneSectionOfScenes() {
        val scenes = ShopCatalog.scenes
        assertEquals(Scenery.entries.toSet(), scenes.map { it.scenery }.toSet())
        assertEquals(scenes.size, scenes.map { it.id }.toSet().size)
        assertEquals(Scenery.ROOM, scenes.first().scenery)
        assertTrue(scenes.none { ShopCatalog.byId(it.id) == null })
        assertTrue(listOf("ground_grass", "ground_wood", "ground_snow").all { ShopCatalog.byId(it) == null }, "grounds are not sold")
        assertTrue(Scenery.FIREPLACE.price in 200..400 && Scenery.PLANT_SHOP.price in 200..400)
        // Outdoor scenes bring their ground, indoor scenes their floor
        Scenery.entries.forEach { assertEquals(it.ground == null, it.indoor, it.name) }
        assertEquals(setOf(Scenery.ROOM, Scenery.FIREPLACE, Scenery.PLANT_SHOP), Scenery.entries.filter { it.indoor }.toSet())
        assertEquals(Ground.SAND, Scenery.DESERT.ground)
        assertEquals(Ground.STONE, Scenery.CITY.ground)
    }

    @Test
    fun scenesCanBeBoughtAndSelected() {
        var state = PlayerState(coins = 1_000)
        val room = ShopCatalog.scenes.first { it.scenery == Scenery.ROOM }
        assertEquals(PurchaseResult.AlreadyOwned, Shop.purchase(state, room, 0))

        val fireplace = ShopCatalog.scenes.first { it.scenery == Scenery.FIREPLACE }
        state = assertIs<PurchaseResult.Success>(Shop.purchase(state, fireplace, 0)).state
        assertEquals(Scenery.FIREPLACE, state.scenery)
        assertEquals(1_000 - fireplace.price, state.coins)
        assertEquals(PurchaseResult.AlreadyOwned, Shop.purchase(state, fireplace, 0))

        val beach = ShopCatalog.scenes.first { it.scenery == Scenery.BEACH }
        state = assertIs<PurchaseResult.Success>(Shop.purchase(state, beach, 0)).state
        assertEquals(Scenery.BEACH, state.scenery)
        assertEquals(Ground.SAND, state.ground, "the legacy ground follows the scene")

        assertEquals(Scenery.ROOM, Shop.selectScenery(state, Scenery.ROOM).scenery)
        assertEquals(Scenery.FIREPLACE, Shop.selectScenery(state, Scenery.FIREPLACE).scenery)
        assertEquals(Scenery.BEACH, Shop.selectScenery(state, Scenery.PLANT_SHOP).scenery, "not owned")
        assertEquals(PurchaseResult.NotEnoughCoins, Shop.purchase(PlayerState(coins = 10), ShopCatalog.scenes.first { it.scenery == Scenery.PLANT_SHOP }, 0))

        // Grounds can no longer be bought or chosen on their own
        assertEquals(PurchaseResult.AlreadyOwned, Shop.purchase(state, ShopItem.Floor("ground_snow", "snow", "", 0, Ground.SNOW), 0))
        assertEquals(state, Shop.selectGround(state, Ground.SNOW))
        assertEquals(state, PlayerStateCodec.decode(PlayerStateCodec.encode(state)))
    }

    @Test
    fun indoorSpotsFitTheCanvasAndDoNotOverlap() {
        val size = Size(400f, 870f)
        val floorTop = 870f - 128f
        Scenery.entries.forEach { scenery ->
            val scene = IndoorScenes.forScenery(scenery, size, floorTop, pixel = 1.5f)
            if (!scenery.indoor) {
                assertNull(scene)
                return@forEach
            }
            assertNotNull(scene)
            assertEquals(scenery, scene.scenery)
            val pots = RoomSpot.entries.associateWith { spot ->
                val (anchor, width) = scene.anchor(spot)
                assertTrue(width > size.width * 0.08f && width < size.width * 0.3f, "$scenery $spot width $width")
                // A pot is about 0.7 times as tall as wide
                Rect(anchor.x - width / 2, anchor.y - width * 0.7f, anchor.x + width / 2, anchor.y)
            }
            pots.forEach { (spot, pot) ->
                assertTrue(pot.left >= 0f && pot.right <= size.width && pot.top >= 0f && pot.bottom <= size.height, "$scenery $spot $pot")
            }
            val list = pots.entries.toList()
            for (i in list.indices) for (j in i + 1 until list.size) {
                assertFalse(list[i].value.overlaps(list[j].value), "$scenery ${list[i].key} overlaps ${list[j].key}")
            }
            scene.windows.forEach { glass ->
                assertTrue(glass.width > 0f && glass.height > 0f && glass.left >= 0f && glass.right <= size.width && glass.bottom < floorTop, "$scenery $glass")
            }
        }
    }

    @Test
    fun toysReactToTaps() {
        val size = Size(400f, 870f)
        val room = IndoorScenes.forScenery(Scenery.ROOM, size, 742f, 1.5f)!!
        val fireplace = IndoorScenes.forScenery(Scenery.FIREPLACE, size, 742f, 1.5f)!!
        val shop = IndoorScenes.forScenery(Scenery.PLANT_SHOP, size, 742f, 1.5f)!!
        fun find(scene: dev.mothlight.plantagotchi.ui.game.IndoorScene, toy: String): Offset? {
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    if (scene.toyAt(Offset(x, y)) == toy) return Offset(x, y)
                    x += 4f
                }
                y += 4f
            }
            return null
        }
        assertNotNull(find(room, IndoorToy.DESK_LAMP))
        assertNotNull(find(room, IndoorToy.CAT))
        assertNotNull(find(fireplace, IndoorToy.FIRE))
        assertNotNull(find(shop, IndoorToy.STRING_LIGHTS))
        assertNull(room.toyAt(Offset(size.width / 2, size.height - 4f)))

        val toys = IndoorToys()
        toys.tap(IndoorToy.DESK_LAMP, 1f)
        assertFalse(toys.deskLampOn)
        toys.tap(IndoorToy.STRING_LIGHTS, 2f)
        assertTrue(toys.lightsOn, "the game state owns the switch")
        assertEquals(2f, toys.lightsSwitchedAt)
        toys.tap(IndoorToy.FIRE, 3f)
        assertEquals(3f, toys.sparksAt)
        toys.tap(IndoorToy.CAT, 4f)
        assertEquals(4f, toys.catFlickAt)
    }
}
