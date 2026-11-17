package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Elements of the scene that react to taps. */
enum class SceneTarget { SHOP, ABOUT, NEED_FERTILIZER, NEED_WATER, NEED_LIGHT, NEED_LOVE, PLANT }

/**
 * The whole game scene drawn in a single canvas and animated per frame:
 * sky, weather, plant, pot, HUD and effects.
 */
@Composable
fun GameCanvas(
    state: GameUiState,
    onPet: (Float) -> Unit,
    onTap: (SceneTarget) -> Unit,
    modifier: Modifier = Modifier,
    events: Flow<GameEvent> = emptyFlow(),
) {
    val sprites = rememberSceneSprites()
    val scene = remember { SceneState() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val textStyle = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val topInset = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()
    val haptics = LocalHapticFeedback.current

    val currentOnPet by rememberUpdatedState(onPet)
    val currentOnTap by rememberUpdatedState(onTap)

    val frameNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frameNanos.longValue = it }
    }
    LaunchedEffect(events) {
        events.collect {
            scene.post(it)
            if (it is GameEvent.Petted) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> scene.hitTest(offset)?.let { currentOnTap(it) } }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentOnPet(drag.y)
                }
            }
    ) {
        val layout = SceneLayout.compute(
            size = size,
            dp = density.density,
            topInset = topInset,
            bottomInset = bottomInset,
            potAspect = sprites.pot(state.player.selectedPot).aspectRatioOrDefault(190f / 296f),
        )
        scene.layout = layout
        scene.advance(frameNanos.longValue, state)

        val targetMinutes = state.weather.localTime.minutesOfDay()
        scene.shownMinutes = if (scene.shownMinutes < 0) targetMinutes
        else lerp(scene.shownMinutes, targetMinutes, 0.05f)
        val tint = nightTint(scene.shownMinutes, state.weather)

        drawSky(scene, sprites, state.weather, scene.shownMinutes)
        drawWeatherBack(scene)
        drawMountains(sprites, tint)
        drawWeatherFront(scene, sprites, tint)
        drawFireflies(scene, state, layout)
        drawPainter(sprites.ground, layout.ground)
        if (state.lampActive) drawLampLight(scene, layout)
        drawPlantAndPot(scene, sprites, state, layout)
        if (state.lampActive) drawLamp(scene, layout)
        drawTools(scene, layout)
        drawEffects(scene, sprites, layout, textMeasurer, textStyle)
        drawHud(scene, sprites, state, layout, textMeasurer, textStyle)
        drawNeeds(scene, sprites, state, layout, textMeasurer, textStyle)
    }
}

private fun SceneState.hitTest(offset: Offset): SceneTarget? {
    val l = layout ?: return null
    return when {
        l.shopButton.inflate(8 * l.dp).contains(offset) || l.coinHud.contains(offset) -> SceneTarget.SHOP
        l.chest.inflate(8 * l.dp).contains(offset) -> SceneTarget.ABOUT
        l.bubbles[0].contains(offset) -> SceneTarget.NEED_LOVE
        l.bubbles[1].contains(offset) -> SceneTarget.NEED_FERTILIZER
        l.bubbles[2].contains(offset) -> SceneTarget.NEED_WATER
        l.bubbles[3].contains(offset) -> SceneTarget.NEED_LIGHT
        l.leaves.contains(offset) || l.pot.contains(offset) -> SceneTarget.PLANT.also { petBounceAt = time }
        else -> null
    }
}

private fun DrawScope.drawPlantAndPot(scene: SceneState, sprites: SceneSprites, state: GameUiState, l: SceneLayout) {
    val t = scene.time
    val health = state.player.health.toFloat()
    // 0 = healthy, 1 = badly neglected
    val droop = ((WILT_START - health) / WILT_START).coerceIn(0f, 1f)
    val wind = min(state.weather.facts.windSpeed.toFloat(), 40f) / 40f
    val sway = sin(t * 1.7f) * (2f + wind * 5f) * (1 - droop * 0.6f) + sin(t * 0.6f) * wind * 2f
    val breathe = 1f + 0.012f * sin(t * 2.3f)
    val bounce = bounce(t - scene.petBounceAt)

    val saturation = ColorMatrix().apply { setToSaturation(1f - droop * 0.75f) }
    val plantFilter = if (droop > 0f) ColorFilter.colorMatrix(saturation) else null

    withTransform({
        rotate(sway + droop * 12f, l.plantPivot)
        scale(
            scaleX = (1f / breathe) - bounce * 0.08f,
            scaleY = breathe * (1f - droop * 0.12f) + bounce * 0.12f,
            pivot = l.plantPivot,
        )
    }) {
        drawPainter(sprites.plant, l.plant, colorFilter = plantFilter)
    }

    val potBounce = bounce(t - scene.potBounceAt)
    withTransform({
        scale(1f - potBounce * 0.06f, 1f + potBounce * 0.08f, Offset(l.pot.center.x, l.pot.bottom))
    }) {
        drawPainter(sprites.pot(state.player.selectedPot), l.pot)
    }
}

private fun DrawScope.drawLampLight(scene: SceneState, l: SceneLayout) {
    val lamp = SceneState.lampPosition(l)
    val pulse = 0.85f + 0.15f * sin(scene.time * 3f)
    val bottom = l.leaves.bottom
    val cone = Path().apply {
        moveTo(lamp.x - 20 * l.dp, lamp.y + 16 * l.dp)
        lineTo(lamp.x + 20 * l.dp, lamp.y + 16 * l.dp)
        lineTo(l.leaves.right + 20 * l.dp, bottom)
        lineTo(l.leaves.left - 20 * l.dp, bottom)
        close()
    }
    drawPath(
        cone,
        Brush.verticalGradient(
            listOf(Color(0x88FFF6B0), Color(0x00FFF6B0)),
            startY = lamp.y,
            endY = bottom,
        ),
        alpha = pulse,
    )
}

private fun DrawScope.drawLamp(scene: SceneState, l: SceneLayout) {
    val lamp = SceneState.lampPosition(l)
    val swing = sin(scene.time * 1.3f) * 3f
    drawLine(Color(0xFF222034), Offset(lamp.x, 0f), lamp, strokeWidth = 2 * l.dp)
    rotate(swing, Offset(lamp.x, 0f)) {
        val sprite = CodeSprites.growLamp
        val pixel = 4 * l.dp
        val topLeft = Offset(lamp.x - sprite.width * pixel / 2, lamp.y - sprite.height * pixel / 2)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x99FFEB57), Color.Transparent), center = lamp + Offset(0f, 14 * l.dp), radius = 40 * l.dp),
            radius = 40 * l.dp,
            center = lamp + Offset(0f, 14 * l.dp),
        )
        sprite.draw(this, topLeft, pixel)
    }
}

private fun DrawScope.drawTools(scene: SceneState, l: SceneLayout) {
    scene.tools.forEach { tool ->
        val pose = scene.toolPose(tool, l)
        val w = tool.sprite.width * pose.pixel
        val h = tool.sprite.height * pose.pixel
        withTransform({
            rotate(pose.rotation, pose.center)
            // Mirror so the spout faces the plant.
            scale(-1f, 1f, pose.center)
        }) {
            tool.sprite.draw(this, Offset(pose.center.x - w / 2, pose.center.y - h / 2), pose.pixel, pose.alpha)
        }
    }
}

private fun DrawScope.drawFireflies(scene: SceneState, state: GameUiState, l: SceneLayout) {
    val calm = state.weather.facts.state in setOf(WeatherState.CLEAR_SKY, WeatherState.FEW_CLOUDS, WeatherState.SCATTERED_CLOUDS)
    if (state.weather.isDay || !calm) return
    val random = Random(7)
    repeat(10) { i ->
        val baseX = random.nextFloat() * size.width
        val baseY = l.ground.top - random.nextFloat() * size.height * 0.35f
        val speed = 0.3f + random.nextFloat() * 0.5f
        val t = scene.time * speed + i
        val pos = Offset(baseX + sin(t) * 30 * l.dp, baseY + cos(t * 1.3f) * 20 * l.dp)
        val glow = (0.5f + 0.5f * sin(scene.time * (2f + random.nextFloat() * 2f) + i * 1.7f)).coerceIn(0f, 1f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xAAE8FF7A), Color.Transparent), center = pos, radius = 8 * l.dp),
            radius = 8 * l.dp,
            center = pos,
            alpha = glow,
        )
        drawRect(Color(0xFFF4FFB0), pos - Offset(l.dp, l.dp), Size(2 * l.dp, 2 * l.dp), alpha = glow)
    }
}

private fun DrawScope.drawEffects(
    scene: SceneState,
    sprites: SceneSprites,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    scene.effects.forEach { fx ->
        val fade = 1f - fx.progress * fx.progress
        when (fx.kind) {
            FxKind.HEART -> {
                val s = fx.size * (0.6f + 0.4f * easeOut(min(1f, fx.progress * 4)))
                drawBitmap(sprites.heart, Rect(Offset(fx.x - s / 2, fx.y - s / 2), Size(s, s)), alpha = fade)
            }
            FxKind.TEXT -> {
                val layout = textMeasurer.measure(fx.text, style.copy(color = fx.color, fontSize = fx.size.sp))
                val topLeft = Offset(fx.x - layout.size.width / 2, fx.y)
                drawText(layout, color = Color(0xFF222034), topLeft = topLeft + Offset(l.dp, l.dp), alpha = fade)
                drawText(layout, topLeft = topLeft, alpha = fade)
            }
            FxKind.WATER -> drawRect(fx.color, Offset(fx.x, fx.y), Size(fx.size, fx.size * 1.6f), alpha = fade)
            FxKind.DIRT -> drawRect(fx.color, Offset(fx.x, fx.y), Size(fx.size, fx.size), alpha = fade)
            FxKind.SPARKLE -> {
                val twinkle = 0.5f + 0.5f * sin(scene.time * 20f + fx.phase)
                CodeSprites.sparkle.draw(this, Offset(fx.x, fx.y), fx.size, alpha = fade * twinkle)
            }
            FxKind.LEAF -> rotate(sin(scene.time * 3f + fx.phase) * 40f, Offset(fx.x, fx.y)) {
                drawRect(Color(0xFF7FA03A), Offset(fx.x, fx.y), Size(fx.size, fx.size * 0.6f), alpha = fade)
            }
        }
    }
}

private fun DrawScope.drawHud(
    scene: SceneState,
    sprites: SceneSprites,
    state: GameUiState,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val t = scene.time
    val pop = 1f + bounce(t - scene.coinPopAt, 0.5f).let { kotlin.math.abs(it) } * 0.25f
    val hud = l.coinHud.scaled(pop)
    drawBitmap(sprites.coinCounter, hud)
    val text = textMeasurer.measure("${state.player.coins}", style.copy(color = Color.Black, fontSize = 16.sp))
    drawText(
        text,
        topLeft = Offset(hud.center.x + 5 * l.dp * pop - text.size.width / 2, hud.center.y - text.size.height / 2),
    )

    // Shop button: gentle bob, sparkles when the player can afford something.
    val bob = sin(t * 2.2f) * 2 * l.dp
    val shop = l.shopButton.translate(0f, bob)
    drawPainter(sprites.buttonFrame, shop)
    CodeSprites.shopBag.drawFit(this, Size(shop.width * 0.62f, shop.height * 0.62f), Offset(shop.left + shop.width * 0.19f, shop.top + shop.height * 0.17f))

    // Chest wiggles every few seconds.
    val wiggle = ((t % 5f) - 4.4f).let { if (it in 0f..0.6f) sin(it * 30f) * 8f * (1 - it / 0.6f) else 0f }
    rotate(wiggle, Offset(l.chest.center.x, l.chest.bottom)) {
        drawBitmap(sprites.chest, l.chest)
    }
}

private fun DrawScope.drawNeeds(
    scene: SceneState,
    sprites: SceneSprites,
    state: GameUiState,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val label = textMeasurer.measure("What does your plant need?", style.copy(color = Color.White))
    drawText(label, topLeft = Offset((size.width - label.size.width) / 2, l.needsLabelBaseline - label.size.height))

    val p = state.player
    val needs = listOf(
        Triple(p.love, PlantagotchiColors.Love, sprites.heart),
        Triple(p.fertilizer, PlantagotchiColors.Fertilizer, sprites.fertilizer),
        Triple(p.water, PlantagotchiColors.Water, sprites.water),
        Triple(p.lux, PlantagotchiColors.Sunshine, sprites.sunshine),
    )
    needs.forEachIndexed { i, (value, color, icon) ->
        val target = value.toFloat()
        val shown = if (scene.shownNeeds[i] < 0) target else lerp(scene.shownNeeds[i], target, 0.08f)
        scene.shownNeeds[i] = shown

        val box = l.bubbles[i]
        val low = shown < LOW_NEED
        val pulse = if (low) 1f + 0.06f * sin(scene.time * 8f) else 1f
        val shake = if (low) sin(scene.time * 30f) * 1.5f * l.dp * ((LOW_NEED - shown) / LOW_NEED) else 0f
        val center = box.center + Offset(shake, 0f)
        val radius = 25 * l.dp

        scale(pulse, center) {
            drawCircle(color, radius, center)
            val missing = (1f - shown / 100f).coerceIn(0f, 1f)
            val circle = Path().apply { addOval(Rect(center, radius)) }
            clipPath(circle) {
                drawRect(PlantagotchiColors.BubbleEmpty, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2 * missing))
                // Little wave on the fill surface
                val surface = center.y - radius + radius * 2 * missing
                if (missing in 0.02f..0.98f) {
                    for (x in 0 until 10) {
                        val wx = center.x - radius + x * radius / 5
                        val wy = surface + sin(scene.time * 4f + x) * 1.5f * l.dp
                        drawRect(color, Offset(wx, wy - 2 * l.dp), Size(radius / 5 + 1, 2 * l.dp))
                    }
                }
            }
            val iconSize = 25 * l.dp
            val iconBob = sin(scene.time * 2f + i) * 1.2f * l.dp
            drawBitmap(icon, Rect(Offset(center.x - iconSize / 2, center.y - iconSize / 2 + iconBob), Size(iconSize, iconSize)))
            drawPainter(sprites.buttonFrame, Rect(center - Offset(box.width / 2, box.height / 2), box.size))
        }
        if (i == 3 && state.lampActive && Random.nextFloat() < 0.05f) {
            scene.sparkleBurst(center, 1, l.dp, spread = radius)
        }
    }
}

private const val WILT_START = 35f
private const val LOW_NEED = 20f

