package dev.mothlight.plantagotchi.ui.particlesystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* Weather particles based on https://github.com/fidloo/flux */

enum class PrecipitationSourceEdge { TOP, RIGHT, BOTTOM, LEFT }

sealed class PrecipitationShape {
    data class Circle(val minRadius: Int, val maxRadius: Int, val color: Color) : PrecipitationShape()

    data class Line(
        val minStrokeWidth: Int,
        val maxStrokeWidth: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val color: Color,
    ) : PrecipitationShape()

    data class Image(
        val image: ImageBitmap,
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val colorFilter: ColorFilter,
    ) : PrecipitationShape()
}

data class PrecipitationsParameters(
    val particleCount: Int,
    val distancePerStep: Int,
    val minSpeed: Float,
    val maxSpeed: Float,
    val minAngle: Int,
    val maxAngle: Int,
    val shape: PrecipitationShape,
    val sourceEdge: PrecipitationSourceEdge,
)

val snowParameters = PrecipitationsParameters(
    particleCount = 200,
    distancePerStep = 5,
    minSpeed = 0.1f,
    maxSpeed = 1f,
    minAngle = 260,
    maxAngle = 280,
    shape = PrecipitationShape.Circle(minRadius = 1, maxRadius = 10, color = Color.White),
    sourceEdge = PrecipitationSourceEdge.TOP,
)

val rainParameters = PrecipitationsParameters(
    particleCount = 600,
    distancePerStep = 30,
    minSpeed = 0.7f,
    maxSpeed = 1f,
    minAngle = 265,
    maxAngle = 285,
    shape = PrecipitationShape.Line(
        minStrokeWidth = 1,
        maxStrokeWidth = 3,
        minHeight = 10,
        maxHeight = 15,
        color = Color.Gray,
    ),
    sourceEdge = PrecipitationSourceEdge.TOP,
)

internal fun Int.toRadians(): Double = this * PI / 180.0

/** Draws the particles of [system], advancing it to [frame] first. */
internal fun DrawScope.drawParticles(
    system: ParticleSystem,
    frame: Long,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    system.step(frame, size.width.toInt(), size.height.toInt())
    val parameters = system.parameters
    system.particles.forEach { particle ->
        when (val shape = parameters.shape) {
            is PrecipitationShape.Circle -> drawCircle(
                color = shape.color,
                radius = particle.width,
                center = Offset(particle.x, particle.y),
                alpha = alpha,
            )

            is PrecipitationShape.Line -> {
                val angle = particle.angle.toRadians()
                drawLine(
                    color = shape.color,
                    pathEffect = PathEffect.cornerPathEffect(20f),
                    start = Offset(particle.x, particle.y),
                    end = Offset(
                        particle.x - particle.height * cos(angle).toFloat(),
                        particle.y - particle.height * sin(angle).toFloat(),
                    ),
                    strokeWidth = particle.width,
                    alpha = alpha,
                )
            }

            is PrecipitationShape.Image -> drawImage(
                image = shape.image,
                dstOffset = IntOffset(particle.x.toInt(), particle.y.toInt()),
                dstSize = IntSize(particle.width.toInt(), particle.height.toInt()),
                alpha = alpha,
                colorFilter = colorFilter ?: shape.colorFilter,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

class Particle(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var speed: Float,
    var angle: Int,
)

internal class ParticleSystem(val parameters: PrecipitationsParameters) {
    private val _particles = mutableListOf<Particle>()
    val particles: List<Particle> get() = _particles

    private var frameWidth = 0
    private var frameHeight = 0
    private var lastFrame = Long.MIN_VALUE

    fun step(frame: Long, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width != frameWidth || height != frameHeight) {
            frameWidth = width
            frameHeight = height
            _particles.clear()
        }
        while (_particles.size < parameters.particleCount) _particles.add(createParticle())
        if (frame == lastFrame) return
        lastFrame = frame
        _particles.forEach { p ->
            if (isOutOfFrame(p)) {
                reset(p, fromEdge = true)
            } else {
                val angle = p.angle.toRadians()
                p.x -= (parameters.distancePerStep * p.speed) * cos(angle).toFloat()
                p.y -= (parameters.distancePerStep * p.speed) * sin(angle).toFloat()
            }
        }
    }

    private fun createParticle() = Particle(0f, 0f, 0f, 0f, 0f, 0).also { reset(it, fromEdge = false) }

    private fun reset(p: Particle, fromEdge: Boolean) {
        p.width = randomWidth()
        p.height = randomHeight(p.width)
        p.angle = if (parameters.minAngle == parameters.maxAngle) parameters.maxAngle
        else Random.nextInt(parameters.minAngle, parameters.maxAngle)
        p.speed = Random.nextFloat() * (parameters.maxSpeed - parameters.minSpeed) + parameters.minSpeed
        p.x = generateX(fromEdge)
        p.y = generateY(fromEdge)
    }

    private fun generateX(fromEdge: Boolean): Float {
        val randomX = Random.nextInt(frameWidth).toFloat()
        return when (parameters.sourceEdge) {
            PrecipitationSourceEdge.RIGHT -> if (fromEdge) frameWidth.toFloat() else randomX
            PrecipitationSourceEdge.LEFT -> if (fromEdge) 0f else randomX
            else -> randomX
        }
    }

    private fun generateY(fromEdge: Boolean): Float {
        val randomY = Random.nextInt(frameHeight).toFloat()
        return when (parameters.sourceEdge) {
            PrecipitationSourceEdge.TOP -> if (fromEdge) 0f else randomY
            PrecipitationSourceEdge.BOTTOM -> if (fromEdge) frameHeight.toFloat() else randomY
            else -> randomY
        }
    }

    private fun isOutOfFrame(p: Particle): Boolean = when (parameters.sourceEdge) {
        PrecipitationSourceEdge.TOP -> p.y > frameHeight || p.x < 0 || p.x > frameWidth
        PrecipitationSourceEdge.BOTTOM -> p.y < 0 || p.x < 0 || p.x > frameWidth
        PrecipitationSourceEdge.RIGHT ->
            p.y - p.height > frameHeight || p.y + p.height < 0 || p.x + p.width < 0
        PrecipitationSourceEdge.LEFT ->
            p.y - p.height > frameHeight || p.y + p.height < 0 || p.x - p.width > frameWidth
    }

    private fun randomWidth(): Float = when (val s = parameters.shape) {
        is PrecipitationShape.Circle -> Random.nextInt(s.minRadius, s.maxRadius)
        is PrecipitationShape.Line -> Random.nextInt(s.minStrokeWidth, s.maxStrokeWidth)
        is PrecipitationShape.Image -> Random.nextInt(s.minWidth, s.maxWidth)
    }.toFloat()

    private fun randomHeight(width: Float): Float = when (val s = parameters.shape) {
        is PrecipitationShape.Circle -> width
        is PrecipitationShape.Line -> Random.nextInt(s.minHeight, s.maxHeight).toFloat()
        is PrecipitationShape.Image -> Random.nextInt(s.minHeight, s.maxHeight).toFloat()
    }
}
