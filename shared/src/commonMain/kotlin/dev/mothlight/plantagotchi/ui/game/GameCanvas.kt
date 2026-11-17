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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.translate
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
import dev.mothlight.plantagotchi.data.state.GameRules
import dev.mothlight.plantagotchi.data.state.PlantState
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Elements of the scene that react to taps. */
enum class SceneTarget {
    SHOP, MENU, CHEST, WEATHER, NEED_FERTILIZER, NEED_WATER, NEED_LIGHT, NEED_LOVE,
    PLANT, PREVIOUS_PLANT, NEXT_PLANT, REPLANT, COMPOST,
}

/** Localized texts the canvas draws. */
data class SceneTexts(
    val needsLabel: String,
    val plantLabel: String,
    val replant: String,
    val compost: String,
)

/**
 * The whole game scene drawn in a single canvas and animated per frame:
 * sky, weather, plant, pot, HUD and effects.
 */
@Composable
fun GameCanvas(
    state: GameUiState,
    texts: SceneTexts,
    onPet: (Float) -> Unit,
    onTap: (SceneTarget) -> Unit,
    modifier: Modifier = Modifier,
    events: Flow<GameEvent> = emptyFlow(),
) {
    val sprites = rememberSceneSprites()
    val scene = remember { SceneState() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val textStyle = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val topInset = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()
    val haptics = LocalHapticFeedback.current

    val currentOnPet by rememberUpdatedState(onPet)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentState by rememberUpdatedState(state)

    val frameNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frameNanos.longValue = it }
    }
    LaunchedEffect(events) {
        events.collect {
            scene.post(it)
            when (it) {
                GameEvent.Petted -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                is GameEvent.GrewUp, is GameEvent.RewardClaimed, is GameEvent.Purchased ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                else -> Unit
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> scene.hitTest(offset, currentState)?.let { currentOnTap(it) } }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentOnPet(drag.y)
                }
            }
    ) {
        val plant = state.player.activePlant
        val layout = SceneLayout.compute(
            size = size,
            dp = density.density,
            topInset = topInset,
            bottomInset = bottomInset,
            potAspect = sprites.pot(plant.pot).aspectRatioOrDefault(190f / 296f),
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
        drawButterflies(scene, state, layout)
        drawFireflies(scene, state, layout)
        drawPainter(sprites.ground, layout.ground)
        drawGroundSeason(state.season, layout)
        if (state.lampActive) drawLampLight(scene, layout)
        drawPlantAndPot(scene, state, plant, sprites, layout)
        if (state.lampActive) drawLamp(scene, layout)
        drawTools(scene, layout)
        drawTemperatureOverlay(scene, state, layout)
        drawEffects(scene, sprites, layout, textMeasurer, textStyle)
        drawHud(scene, sprites, state, layout, textMeasurer, textStyle)
        drawPlantControls(scene, state, plant, texts, layout, textMeasurer, textStyle)
        drawNeeds(scene, sprites, state, plant, texts, layout, textMeasurer, textStyle)
    }
}

private fun SceneState.hitTest(offset: Offset, state: GameUiState): SceneTarget? {
    val l = layout ?: return null
    val plant = state.player.activePlant
    val multiple = state.player.plants.size > 1
    fun Rect.hit(slop: Float = 8f) = inflate(slop * l.dp).contains(offset)
    return when {
        l.menuButton.hit() -> SceneTarget.MENU
        l.chest.hit() -> SceneTarget.CHEST
        l.shopButton.hit() || l.coinHud.hit(0f) -> SceneTarget.SHOP
        l.weatherChip.hit(4f) -> SceneTarget.WEATHER
        plant.dead && l.replantButton.hit(4f) -> SceneTarget.REPLANT
        plant.dead && multiple && l.compostButton.hit(4f) -> SceneTarget.COMPOST
        multiple && l.arrowLeft.hit() -> SceneTarget.PREVIOUS_PLANT
        multiple && l.arrowRight.hit() -> SceneTarget.NEXT_PLANT
        l.bubbles[0].contains(offset) -> SceneTarget.NEED_LOVE
        l.bubbles[1].contains(offset) -> SceneTarget.NEED_FERTILIZER
        l.bubbles[2].contains(offset) -> SceneTarget.NEED_WATER
        l.bubbles[3].contains(offset) -> SceneTarget.NEED_LIGHT
        l.leaves.contains(offset) || l.pot.contains(offset) -> SceneTarget.PLANT.also { petBounceAt = time }
        else -> null
    }
}

private val witheredMatrix = ColorMatrix(
    floatArrayOf(
        0.35f, 0.45f, 0.10f, 0f, 20f,
        0.25f, 0.33f, 0.08f, 0f, 10f,
        0.12f, 0.18f, 0.05f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

private fun DrawScope.drawPlantAndPot(scene: SceneState, state: GameUiState, plant: PlantState, sprites: SceneSprites, l: SceneLayout) {
    val t = scene.time
    // Slide in after switching plants.
    val switchT = ((t - scene.switchAt) / 0.35f).coerceIn(0f, 1f)
    val slide = (1f - easeOut(switchT)) * scene.switchDirection * size.width * 0.6f

    translate(left = slide) {
        val noon = (state.weather.sunrise.minutesOfDay() + state.weather.sunset.minutesOfDay()) / 2
        val pose = scene.plantAnimator.pose(
            state = state,
            plant = plant,
            time = t,
            sunIsRight = scene.shownMinutes < noon,
            petBounce = bounce(t - scene.petBounceAt),
            growBounce = bounce(t - scene.growAt, 1f),
        )
        val maturity = scene.plantAnimator.maturity(plant, t)
        val filter = when {
            plant.dead -> ColorFilter.colorMatrix(witheredMatrix)
            pose.droop > 0f -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - pose.droop * 0.75f) })
            else -> null
        }
        val draw: DrawScope.() -> Unit = {
            PlantRenderer.draw(
                scope = this,
                species = plant.species,
                maturity = maturity,
                seed = plant.id.hashCode(),
                pose = pose,
                base = Offset(l.plant.center.x + l.plantPixel / 2, l.plant.bottom),
                pixel = l.plantPixel,
            )
        }
        if (filter == null) draw() else drawIntoLayer(filter) { draw() }

        val potBounce = bounce(t - scene.potBounceAt)
        withTransform({
            scale(1f - potBounce * 0.06f, 1f + potBounce * 0.08f, Offset(l.pot.center.x, l.pot.bottom))
        }) {
            drawPainter(sprites.pot(plant.pot), l.pot)
        }
    }
}

private inline fun DrawScope.drawIntoLayer(filter: ColorFilter, crossinline block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(
        Rect(Offset.Zero, size),
        androidx.compose.ui.graphics.Paint().apply { colorFilter = filter },
    )
    block()
    drawContext.canvas.restore()
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
        Brush.verticalGradient(listOf(Color(0x88FFF6B0), Color(0x00FFF6B0)), startY = lamp.y, endY = bottom),
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
        val glowCenter = lamp + Offset(0f, 14 * l.dp)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x99FFEB57), Color.Transparent), center = glowCenter, radius = 40 * l.dp),
            radius = 40 * l.dp,
            center = glowCenter,
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
    if (state.weather.isDay || !calm || state.season == Season.WINTER) return
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

/** Butterflies on calm summer and spring days. */
private fun DrawScope.drawButterflies(scene: SceneState, state: GameUiState, l: SceneLayout) {
    if (!state.weather.isDay || state.season !in setOf(Season.SUMMER, Season.SPRING)) return
    if (state.weather.facts.state !in setOf(WeatherState.CLEAR_SKY, WeatherState.FEW_CLOUDS, WeatherState.SCATTERED_CLOUDS)) return
    repeat(2) { i ->
        val t = scene.time * (0.25f + i * 0.08f) + i * 3f
        val x = size.width * (0.5f + 0.42f * sin(t))
        val y = l.pot.top - 60 * l.dp + 50 * l.dp * sin(t * 2.3f) - i * 90 * l.dp
        val sprite = if (((scene.time * 8).toInt() + i) % 2 == 0) CodeSprites.butterflyOpen else CodeSprites.butterflyClosed
        sprite.draw(this, Offset(x, y), 3 * l.dp)
    }
}

/** Snow caps in winter, flowers in spring. */
private fun DrawScope.drawGroundSeason(season: Season, l: SceneLayout) {
    val random = Random(11)
    when (season) {
        Season.WINTER -> {
            val px = 3 * l.dp
            drawRect(Color(0xFFF4F7FF), Offset(0f, l.ground.top - px), Size(size.width, px * 2))
            var x = 0f
            while (x < size.width) {
                val bump = random.nextInt(3)
                if (bump > 0) drawRect(Color(0xFFF4F7FF), Offset(x, l.ground.top - px * (1 + bump)), Size(px * (2 + random.nextInt(3)), px * bump))
                drawRect(Color(0xFFD6E4F5), Offset(x, l.ground.top + px), Size(px, px))
                x += px * (3 + random.nextInt(3))
            }
        }
        Season.SPRING -> repeat(9) {
            val x = random.nextFloat() * size.width
            CodeSprites.flower.draw(this, Offset(x, l.ground.top - 4 * 3 * l.dp + 2 * l.dp), 3 * l.dp)
        }
        else -> Unit
    }
}

/** Frost tint and ice sparkles below 0 °C, warm haze above 30 °C. */
private fun DrawScope.drawTemperatureOverlay(scene: SceneState, state: GameUiState, l: SceneLayout) {
    val temperature = state.weather.facts.temperature
    when {
        temperature < GameRules.FROST_TEMPERATURE -> {
            drawRect(Color(0xFFB8E0FF), alpha = 0.12f)
            val random = Random(5)
            repeat(12) {
                val x = l.pot.left + random.nextFloat() * l.pot.width
                val y = l.pot.top + random.nextFloat() * 10 * l.dp
                val twinkle = 0.5f + 0.5f * sin(scene.time * 3f + it)
                CodeSprites.sparkle.draw(this, Offset(x, y), 1.5f * l.dp, twinkle)
            }
        }
        temperature > GameRules.HEAT_TEMPERATURE -> drawRect(
            Brush.verticalGradient(listOf(Color(0x33FF8A3D), Color.Transparent), endY = size.height * 0.6f),
        )
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
                drawRect(fx.color, Offset(fx.x, fx.y), Size(fx.size, fx.size * 0.6f), alpha = min(1f, (1 - fx.progress) * 4))
            }
            FxKind.COIN -> {
                // Spin: squash horizontally.
                val w = fx.size * abs(cos(scene.time * 10f + fx.phase)).coerceAtLeast(0.2f)
                drawBitmap(sprites.coin, Rect(Offset(fx.x - w / 2, fx.y - fx.size / 2), Size(w, fx.size)))
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
    val pop = 1f + abs(bounce(t - scene.coinPopAt, 0.5f)) * 0.25f
    val hud = l.coinHud.scaled(pop)
    drawBitmap(sprites.coinCounter, hud)
    val coins = textMeasurer.measure("${state.player.coins}", style.copy(color = Color.Black, fontSize = 16.sp))
    drawText(coins, topLeft = Offset(hud.center.x + 5 * l.dp * pop - coins.size.width / 2, hud.center.y - coins.size.height / 2))

    // Weather chip
    val chip = l.weatherChip
    drawRoundRect(Color(0xAA222034), chip.topLeft, chip.size, CornerRadius(6 * l.dp))
    val icon = Rect(chip.left + 6 * l.dp, chip.top + 4 * l.dp, chip.left + 30 * l.dp, chip.bottom - 4 * l.dp)
    val ws = state.weather.facts.state
    when {
        ws == WeatherState.CLEAR_SKY || ws == WeatherState.FEW_CLOUDS ->
            drawBitmap(if (state.weather.isDay) sprites.sun else sprites.moon, icon)
        else -> {
            drawBitmap(sprites.cloud, Rect(icon.left - 2 * l.dp, icon.top + 4 * l.dp, icon.right + 2 * l.dp, icon.bottom - 4 * l.dp))
            if (ws in setOf(WeatherState.RAIN, WeatherState.LIGHT_RAIN, WeatherState.HEAVY_RAIN, WeatherState.THUNDERSTORM, WeatherState.SNOW)) {
                val color = if (ws == WeatherState.SNOW) Color.White else Color(0xFF5FCDE4)
                repeat(3) { i ->
                    val y = icon.bottom - 4 * l.dp + ((t * 20 + i * 5) % 6) * l.dp
                    drawRect(color, Offset(icon.left + (4 + i * 7) * l.dp, y), Size(2 * l.dp, 3 * l.dp))
                }
            }
        }
    }
    val temperature = textMeasurer.measure("${state.weather.facts.temperature.roundToInt()}°", style.copy(color = PlantagotchiColors.OffWhite, fontSize = 15.sp))
    drawText(temperature, topLeft = Offset(icon.right + 8 * l.dp, chip.center.y - temperature.size.height / 2))

    // Shop button: gentle bob.
    val bob = sin(t * 2.2f) * 2 * l.dp
    val shop = l.shopButton.translate(0f, bob)
    drawPainter(sprites.buttonFrame, shop)
    CodeSprites.shopBag.drawFit(this, Size(shop.width * 0.62f, shop.height * 0.62f), Offset(shop.left + shop.width * 0.19f, shop.top + shop.height * 0.17f))

    // Daily chest: glows and hops while a reward is waiting.
    val available = state.rewardAvailable
    val hop = if (available) abs(sin(t * 3f)) * 4 * l.dp * (if ((t % 3f) < 1f) 1f else 0f) else 0f
    val open = bounce(t - scene.chestOpenAt, 0.8f)
    val chest = l.chest.translate(0f, -hop).scaled(1f + abs(open) * 0.2f)
    if (available) {
        val glow = 0.6f + 0.4f * sin(t * 4f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xDDFFEB57), Color(0x55FFEB57), Color.Transparent), center = chest.center, radius = chest.width),
            radius = chest.width,
            center = chest.center,
            alpha = glow,
        )
        if (Random.nextFloat() < 0.04f) scene.sparkleBurst(chest.center, 1, l.dp, spread = chest.width / 2)
    }
    drawBitmap(sprites.chest, chest)

    // Menu
    drawPainter(sprites.buttonFrame, l.menuButton)
    CodeSprites.menu.drawFit(this, Size(l.menuButton.width * 0.45f, l.menuButton.height * 0.45f), Offset(l.menuButton.left + l.menuButton.width * 0.275f, l.menuButton.top + l.menuButton.height * 0.275f))
}

private fun DrawScope.drawPlantControls(
    scene: SceneState,
    state: GameUiState,
    plant: PlantState,
    texts: SceneTexts,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val label = textMeasurer.measure(texts.plantLabel, style.copy(color = PlantagotchiColors.OffWhite, fontSize = 14.sp))
    val labelPos = Offset((size.width - label.size.width) / 2, l.plantLabelY)
    drawText(label, color = Color(0xFF222034), topLeft = labelPos + Offset(l.dp, l.dp))
    drawText(label, topLeft = labelPos)

    if (state.player.plants.size > 1) {
        val nudge = sin(scene.time * 3f) * 2 * l.dp
        CodeSprites.arrowRight.drawFit(this, l.arrowRight.size, l.arrowRight.topLeft + Offset(nudge, 0f))
        withTransform({ scale(-1f, 1f, l.arrowLeft.center) }) {
            CodeSprites.arrowRight.drawFit(this, l.arrowLeft.size, l.arrowLeft.topLeft + Offset(nudge, 0f))
        }
    }

    if (plant.dead) {
        pixelButton(l.replantButton, texts.replant, PlantagotchiColors.Water, l, textMeasurer, style)
        if (state.player.plants.size > 1) {
            pixelButton(l.compostButton, texts.compost, PlantagotchiColors.Fertilizer, l, textMeasurer, style)
        }
    }
}

private fun DrawScope.pixelButton(rect: Rect, text: String, color: Color, l: SceneLayout, textMeasurer: TextMeasurer, style: TextStyle) {
    drawRect(Color(0xFF222034), rect.topLeft, rect.size)
    drawRect(color, rect.topLeft + Offset(2 * l.dp, 2 * l.dp), Size(rect.width - 4 * l.dp, rect.height - 4 * l.dp))
    drawRect(Color.White.copy(alpha = 0.35f), rect.topLeft + Offset(2 * l.dp, 2 * l.dp), Size(rect.width - 4 * l.dp, 3 * l.dp))
    val measured = textMeasurer.measure(text, style.copy(color = Color(0xFF222034), fontSize = 13.sp))
    drawText(measured, topLeft = Offset(rect.center.x - measured.size.width / 2, rect.center.y - measured.size.height / 2))
}

private fun DrawScope.drawNeeds(
    scene: SceneState,
    sprites: SceneSprites,
    state: GameUiState,
    plant: PlantState,
    texts: SceneTexts,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val label = textMeasurer.measure(texts.needsLabel, style.copy(color = Color.White))
    drawText(label, topLeft = Offset((size.width - label.size.width) / 2, l.needsLabelBaseline - label.size.height))

    val needs = listOf(
        Triple(plant.love, PlantagotchiColors.Love, sprites.heart),
        Triple(plant.fertilizer, PlantagotchiColors.Fertilizer, sprites.fertilizer),
        Triple(plant.water, PlantagotchiColors.Water, sprites.water),
        Triple(plant.lux, PlantagotchiColors.Sunshine, sprites.sunshine),
    )
    needs.forEachIndexed { i, (value, color, icon) ->
        val target = value.toFloat()
        val shown = if (scene.shownNeeds[i] < 0) target else lerp(scene.shownNeeds[i], target, 0.08f)
        scene.shownNeeds[i] = shown

        val box = l.bubbles[i]
        val low = shown < LOW_NEED && !plant.dead
        val pulse = if (low) 1f + 0.06f * sin(scene.time * 8f) else 1f
        val shake = if (low) sin(scene.time * 30f) * 1.5f * l.dp * ((LOW_NEED - shown) / LOW_NEED) else 0f
        val center = box.center + Offset(shake, 0f)
        val radius = 25 * l.dp

        scale(pulse, center) {
            drawCircle(if (plant.dead) Color(0xFF6B5F5A) else color, radius, center)
            val missing = (1f - shown / 100f).coerceIn(0f, 1f)
            val circle = Path().apply { addOval(Rect(center, radius)) }
            clipPath(circle) {
                drawRect(PlantagotchiColors.BubbleEmpty, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2 * missing))
                val surface = center.y - radius + radius * 2 * missing
                if (missing in 0.02f..0.98f && !plant.dead) {
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


private const val LOW_NEED = 20f
