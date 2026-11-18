package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
import kotlinx.coroutines.delay

/** Aspect (width / height) of the part of the scene a preview shows; use it for the preview size. */
const val SCENE_PREVIEW_ASPECT = ScenePreviewFrame.WIDTH / (ScenePreviewFrame.BOTTOM - ScenePreviewFrame.TOP)

/** The phone sized scene a preview draws, in dp, and the part of it that is shown. */
internal object ScenePreviewFrame {
    const val WIDTH = 400f
    const val HEIGHT = 870f

    /** Ground line, as in the game (128 dp above the bottom). */
    const val FLOOR = HEIGHT - 128f
    const val TOP = 110f
    const val BOTTOM = FLOOR + 60f
}

/** Small animated preview of a whole scene (landscape and ground, or the room) for the shop. */
@Composable
fun ScenePreview(scenery: Scenery, modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    val toys = remember { IndoorToys() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame.longValue = it }
            delay(66) // ~15 fps is plenty for the shop previews
        }
    }
    Canvas(modifier.clipToBounds()) {
        val time = (frame.longValue % 1_000_000_000_000L) / 1e9f
        drawScenePreview(scenery, time, tint = 0f, toys = toys)
    }
}

/**
 * Draws [scenery] as on a phone ([ScenePreviewFrame]) scaled down to fill this canvas (cropping
 * the sky first, the floor last). Art pixels stay [PIXEL_DP] of the virtual phone, so the preview
 * looks like the game in small.
 */
internal fun DrawScope.drawScenePreview(scenery: Scenery, time: Float, tint: Float, toys: IndoorToys) {
    val dp = density
    val virtual = Size(ScenePreviewFrame.WIDTH * dp, ScenePreviewFrame.HEIGHT * dp)
    val shownTop = ScenePreviewFrame.TOP * dp
    val shownHeight = (ScenePreviewFrame.BOTTOM - ScenePreviewFrame.TOP) * dp
    val s = maxOf(size.width / virtual.width, size.height / shownHeight)
    val dx = (size.width - virtual.width * s) / 2f
    // Crop the sky rather than the floor: align the bottom of the shown part
    val dy = size.height - (shownTop + shownHeight) * s
    translate(dx, dy) {
        scale(s, s, pivot = Offset.Zero) {
            drawScene(scenery, virtual, ScenePreviewFrame.FLOOR * dp, PIXEL_DP * dp, tint, time, toys)
        }
    }
}

/**
 * Draws a complete scene into a canvas of [canvas] size (not necessarily this DrawScope's size):
 * a simple sky, then the indoor scene or the landscape with its ground. Used by previews; the game
 * draws its own sky and weather between the layers.
 */
internal fun DrawScope.drawScene(scenery: Scenery, canvas: Size, floorTop: Float, pixel: Float, tint: Float, time: Float, toys: IndoorToys) {
    val night = (tint / 0.4f).coerceIn(0f, 1f)
    val skyTop = androidx.compose.ui.graphics.lerp(Color(0xFF5DA9E9), Color(0xFF141838), night)
    val skyBottom = androidx.compose.ui.graphics.lerp(Color(0xFFBFE3FF), Color(0xFF3A3560), night)
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom), 0f, canvas.height), Offset.Zero, canvas)
    val indoor = IndoorScenes.forScenery(scenery, canvas, floorTop, pixel)
    if (indoor != null) {
        indoor.draw(this, tint, time, toys)
    } else {
        clipRect(0f, 0f, canvas.width, canvas.height) {
            SceneryRenderer.drawLandscape(this, scenery, Rect(0f, canvas.height * 0.3f, canvas.width, floorTop), pixel, tint, time, 0.3f, screenBottom = canvas.height)
            SceneryRenderer.drawSceneGround(this, scenery.ground ?: Ground.GRASS, Rect(0f, floorTop, canvas.width, canvas.height), pixel, tint, time)
        }
    }
}
