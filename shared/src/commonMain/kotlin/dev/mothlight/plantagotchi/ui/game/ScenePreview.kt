package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery

/** Small animated preview of a scenery and ground combination for the shop. */
@Composable
fun ScenePreview(scenery: Scenery, ground: Ground, modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame.longValue = it }
            delay(40) // ~20 fps is plenty for the shop previews
        }
    }
    val block = 2f * LocalDensity.current.density
    Canvas(modifier.clipToBounds()) {
        val time = (frame.longValue % 1_000_000_000_000L) / 1e9f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF5DA9E9), Color(0xFFBFE3FF))))
        val groundTop = size.height * 0.72f
        SceneryRenderer.drawScenery(this, scenery, Rect(0f, 0f, size.width, groundTop), block, 0f, time, 0.4f, null)
        if (scenery != Scenery.ROOM) SceneryRenderer.drawGround(this, ground, Rect(0f, groundTop, size.width, size.height), block, 0f, time, null)
    }
}
