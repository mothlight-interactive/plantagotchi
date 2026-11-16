package dev.mothlight.plantagotchi.ui.weatherui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.cloud1
import dev.mothlight.plantagotchi.ui.particlesystem.Particles
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationShape
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationSourceEdge
import dev.mothlight.plantagotchi.ui.particlesystem.PrecipitationsParameters
import org.jetbrains.compose.resources.imageResource

@Composable
fun Clouds(
    frame: State<Long>,
    tint: ColorFilter,
    cloudCount: Int,
    modifier: Modifier = Modifier,
) {
    val image = imageResource(Res.drawable.cloud1)
    val px = with(LocalDensity.current) { { dp: Int -> dp.dp.roundToPx() } }
    val parameters = PrecipitationsParameters(
        particleCount = cloudCount,
        distancePerStep = 1,
        minSpeed = 0.2f,
        maxSpeed = 0.6f,
        minAngle = 0,
        maxAngle = 0,
        shape = PrecipitationShape.Image(
            image = image,
            minWidth = px(69),
            maxWidth = px(138),
            minHeight = px(31),
            maxHeight = px(62),
            colorFilter = tint,
        ),
        sourceEdge = PrecipitationSourceEdge.RIGHT,
    )
    Particles(frame = frame, parameters = parameters, modifier = modifier)
}
