package dev.mothlight.plantagotchi

import androidx.compose.ui.geometry.Size
import dev.mothlight.plantagotchi.data.shop.ShopCatalog
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.data.state.Scenery
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.ui.game.SceneLayout
import dev.mothlight.plantagotchi.ui.game.SceneState
import dev.mothlight.plantagotchi.ui.game.ToolAnimation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bubble of a refill fills while the watering can pours, not at the purchase. */
class PourFillTest {
    private val water = SceneState.NEED_WATER
    private var nanos = 1L

    private fun state(waterValue: Double, id: String = "a") = GameUiState(
        loading = false,
        player = PlayerState(plants = listOf(PlantState(id = id, water = waterValue)), activePlantId = id, scenery = Scenery.MOUNTAINS),
    )

    private fun scene() = SceneState().apply {
        layout = SceneLayout.compute(Size(400f, 800f), 1f, 0f, 0f, listOf(1f), listOf(0.6f), 0)
    }

    /** Advances the scene by [seconds] in 50 ms frames. */
    private fun SceneState.run(seconds: Float, s: GameUiState) {
        repeat((seconds / 0.05f).toInt()) { nanos += 50_000_000L; advance(nanos, s) }
    }

    private fun SceneState.shown(s: GameUiState) = displayedNeed(water, s.player.activePlant.water.toFloat(), s.player.activePlant.id)

    private val pourStart = ToolAnimation.POUR_START * 2.4f
    private val pourEnd = ToolAnimation.POUR_END * 2.4f

    @Test
    fun theBubbleFillsWhileTheCanPours() {
        val scene = scene()
        scene.run(0.2f, state(40.0))
        // Purchase: the save game has the new value at once, the event follows
        val after = state(75.0)
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        scene.run(0.1f, after)
        assertEquals(75.0, after.player.activePlant.water, "the save game is filled at once")
        assertEquals(40f, scene.shown(after), 0.01f, "the bubble waits while the can flies in")

        scene.run(pourStart - 0.15f, after)
        assertEquals(40f, scene.shown(after), 0.5f, "still the old value right before pouring")

        scene.run((pourEnd - pourStart) / 2, after)
        assertTrue(scene.shown(after) in 50f..66f, "half way while pouring, was ${scene.shown(after)}")

        scene.run((pourEnd - pourStart) / 2 + 0.3f, after)
        assertEquals(75f, scene.shown(after), 0.01f, "full when the can is empty")
        scene.run(2f, after)
        assertEquals(75f, scene.shown(after), 0.01f)
    }

    @Test
    fun itAlsoWorksWhenTheEventArrivesBeforeTheNewValue() {
        val scene = scene()
        scene.run(0.2f, state(40.0))
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        scene.run(0.05f, state(40.0))
        val after = state(75.0)
        scene.run(0.1f, after)
        assertEquals(40f, scene.shown(after), 0.01f)
        scene.run(3f, after)
        assertEquals(75f, scene.shown(after), 0.01f)
    }

    @Test
    fun twoRefillsInARowNeverShowMoreThanTheRealValue() {
        val scene = scene()
        scene.run(0.2f, state(10.0))
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        scene.run(0.5f, state(45.0))
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        val after = state(80.0)
        var previous = 0f
        repeat(80) {
            scene.run(0.05f, after)
            val shown = scene.shown(after)
            assertTrue(shown <= 80f && shown >= previous - 0.01f, "rises steadily up to the real value, was $shown after $previous")
            previous = shown
        }
        assertEquals(80f, previous, 0.01f)
    }

    @Test
    fun anAlmostFullBubbleOnlyGainsWhatFits() {
        val scene = scene()
        scene.run(0.2f, state(90.0))
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        val after = state(100.0)
        scene.run(0.1f, after)
        assertEquals(90f, scene.shown(after), 0.01f)
        scene.run(3f, after)
        assertEquals(100f, scene.shown(after), 0.01f)
    }

    @Test
    fun aFreshSceneOrAnotherPlantShowsTheRealValue() {
        val scene = scene()
        scene.run(0.2f, state(40.0))
        scene.post(GameEvent.Purchased(ShopCatalog.wateringCan))
        val after = state(75.0)
        scene.run(0.3f, after)
        assertEquals(40f, scene.shown(after), 0.01f)

        // The scene is rebuilt in the middle of the animation (app restart, rotation)
        assertEquals(75f, scene().shown(after), 0.01f)

        // The player switches to another plant: its bubble is not held back
        val other = state(60.0, id = "b")
        scene.run(0.05f, other)
        assertEquals(60f, scene.shown(other), 0.01f)
    }

    @Test
    fun aRiseWithoutAToolIsShownAfterAMoment() {
        val scene = scene()
        scene.run(0.2f, state(40.0))
        val after = state(75.0) // e.g. changed in the test mode panel
        scene.run(SceneState.UNCLAIMED_SECONDS + 0.2f, after)
        assertEquals(75f, scene.shown(after), 0.01f)
    }
}
