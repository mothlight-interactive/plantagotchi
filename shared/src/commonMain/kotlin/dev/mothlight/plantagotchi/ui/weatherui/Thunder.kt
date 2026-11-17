package dev.mothlight.plantagotchi.ui.weatherui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private class ThunderPath(var id: Long = -1, var points: List<Offset> = emptyList())

@Composable
fun Thunder(frame: State<Long>, modifier: Modifier = Modifier) {
    val thunder = remember { ThunderPath() }
    Canvas(modifier) {
        val current = frame.value
        val width = size.width
        val height = size.height
        val id = current / THUNDER_PHASE
        if (thunder.id != id && width > 0 && height > 0) {
            thunder.id = id
            val pointCount = Random.nextInt(THUNDER_POINT_COUNT_MIN, THUNDER_POINT_COUNT_MAX)
            val points = mutableListOf(
                Offset(Random.nextInt(width.toInt()).toFloat(), Random.nextInt((height / 4).toInt().coerceAtLeast(1)).toFloat())
            )
            while (points.size < pointCount - 2) {
                val last = points.last()
                points += Offset(
                    last.x + Random.nextInt(THUNDER_PROGRESS_X_MIN, THUNDER_PROGRESS_X_MAX),
                    last.y + Random.nextInt(THUNDER_PROGRESS_Y_MIN, THUNDER_PROGRESS_Y_MAX),
                )
            }
            points += Offset(width / 2f, height)
            thunder.points = points
        }
        if (current % THUNDER_PHASE < THUNDER_DURATION && thunder.points.isNotEmpty()) {
            val path = Path().apply {
                thunder.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private const val THUNDER_POINT_COUNT_MIN = 4
private const val THUNDER_POINT_COUNT_MAX = 8
private const val THUNDER_PROGRESS_X_MIN = -200
private const val THUNDER_PROGRESS_X_MAX = 200
private const val THUNDER_PROGRESS_Y_MIN = 50
private const val THUNDER_PROGRESS_Y_MAX = 200

/** In frames (~60 fps): one flash every ~5 s, visible for ~150 ms. */
private const val THUNDER_PHASE = 300L
private const val THUNDER_DURATION = 9L
