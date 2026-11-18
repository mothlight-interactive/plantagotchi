package dev.mothlight.plantagotchi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.dev.AppEnv
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.about_presents
import dev.mothlight.plantagotchi.resources.shop_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Smoke tests for the whole game UI, rendered offscreen with Skia on the JVM (:shared:desktopTest).
 * The scene animates every frame, so the test clock is advanced by hand instead of waiting for idle.
 */
@OptIn(ExperimentalTestApi::class)
class GameContentUiTest {

    /** Loaded game with today's chest already claimed, so no sheet opens on its own. */
    private fun loaded(env: AppEnv = AppEnv.PROD) = GameUiState(
        loading = false,
        player = PlayerState(lastRewardDay = TODAY),
        now = NOW,
        today = TODAY,
        env = env,
    )

    private fun text(res: StringResource) = runBlocking { getString(res) }

    @Test
    fun splashGoesAwayAndSceneRenders() = runComposeUiTest {
        mainClock.autoAdvance = false
        var state by mutableStateOf(loaded().copy(loading = true))
        setContent { GameContent(state = state) }
        val presents = text(Res.string.about_presents)

        mainClock.advanceTimeBy(500)
        onNodeWithText(presents).assertExists()

        // Still loading: the splash stays even after its minimum time.
        mainClock.advanceTimeBy(3_000)
        onNodeWithText(presents).assertExists()

        runOnIdle { state = state.copy(loading = false) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText(presents).assertDoesNotExist()

        // The scene is one canvas without semantics: check that it actually drew something.
        val pixels = onRoot().captureToImage().toPixelMap()
        val colors = buildSet {
            for (x in 0 until pixels.width step 8) for (y in 0 until pixels.height step 8) add(pixels[x, y])
        }
        assertTrue(colors.size > 20, "scene looks empty, only ${colors.size} colors")
    }

    @Test
    fun shopSheetShowsItsTitle() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { GameContent(state = loaded(), initialSheet = Sheet.SHOP, showSplash = false) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText(text(Res.string.shop_title)).assertExists()
    }

    @Test
    fun testModeBadgeOnlyOutsideProd() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { GameContent(state = loaded(AppEnv.DEV), showSplash = false) }
            mainClock.advanceTimeBy(500)
            onNodeWithText(AppEnv.DEV.label, substring = true).assertExists()
        }
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { GameContent(state = loaded(AppEnv.PROD), showSplash = false) }
            mainClock.advanceTimeBy(500)
            onAllNodesWithText(AppEnv.PROD.label, substring = true).assertCountEquals(0)
        }
    }

    private companion object {
        /** 2026-06-21 12:00 UTC, a fixed time so the scene does not depend on the clock. */
        const val NOW = 1_782_043_200_000L
        const val TODAY = NOW / 86_400_000L
    }
}
