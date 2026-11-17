package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import dev.mothlight.plantagotchi.data.state.Species
import kotlin.math.floor
import kotlin.math.sin

/** A small, gently swaying plant for lists (shop, friends). */
@Composable
fun PlantPreview(species: Species, maturity: Float, seed: Int, modifier: Modifier = Modifier, withered: Boolean = false) {
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame.longValue = it } }
    Canvas(modifier) {
        val time = (frame.longValue % 1_000_000_000_000L) / 1e9f
        val pixel = floor(size.height / PlantRenderer.GRID_HEIGHT * 1.15f).coerceAtLeast(1f)
        PlantRenderer.draw(
            scope = this,
            species = species,
            maturity = maturity,
            seed = seed,
            pose = PlantPose(sway = sin(time * 1.7f) * 0.8f, flutter = 0.3f, droop = if (withered) 1f else 0f, time = time),
            base = Offset(size.width / 2 + pixel / 2, size.height),
            pixel = pixel,
        )
    }
}
