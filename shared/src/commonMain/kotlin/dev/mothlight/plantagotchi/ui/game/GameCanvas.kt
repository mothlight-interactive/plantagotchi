package dev.mothlight.plantagotchi.ui.game

import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import dev.mothlight.plantagotchi.data.state.RoomSpot
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.mothlight.plantagotchi.data.state.Ground
import dev.mothlight.plantagotchi.data.state.Scenery
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

    /** Tap on nothing in particular: ends the plant selection. */
    EMPTY,
    PLANT, REPLANT, COMPOST,

    /** Long press on a plant: offer to discard it. */
    DISCARD,

    /** The grow lamp was pushed and starts swinging. */
    LAMP,

    /** Two lamps of the cradle "hit" each other. */
    LAMP_CLICK,

    /** Toys of the indoor scenes: the cat and desk lamp, the fire, the string lights. */
    CAT,
    DESK_LAMP,
    FIRE,
    STRING_LIGHTS,
}

/** Localized texts the canvas draws. */
data class SceneTexts(
    /** Species, growth stage and position, shown above the need bubbles. */
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
    onSelectPlant: (Int) -> Unit = {},
    events: Flow<GameEvent> = emptyFlow(),
    onMovePlant: (Int, RoomSpot) -> Unit = { _, _ -> },
    onSwapPlants: (Int, Int) -> Unit = { _, _ -> },
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
    val currentOnSelect by rememberUpdatedState(onSelectPlant)
    val currentOnMove by rememberUpdatedState(onMovePlant)
    val currentOnSwap by rememberUpdatedState(onSwapPlants)
    val currentState by rememberUpdatedState(state)

    val frameNanos = remember { mutableLongStateOf(0L) }
    // Last touch or game event, in frame time: drives the frame rate (battery).
    val lastActivity = remember { mutableLongStateOf(0L) }
    fun active() { lastActivity.longValue = frameNanos.longValue }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos.longValue = it }
            val idle = frameNanos.longValue - lastActivity.longValue > IDLE_AFTER_NANOS && !scene.lampSwinging
            delay(if (idle) IDLE_FRAME_DELAY_MS else ACTIVE_FRAME_DELAY_MS)
        }
    }
    LaunchedEffect(events) {
        events.collect {
            active()
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
                // One handler for everything: tap, swipe to pet, long press (discard dialog) and
                // long press + drag to move a plant.
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    active()
                    val start = down.position
                    val held = scene.plantIndexAt(start, currentState)
                    var mode = GestureMode.UNDECIDED
                    var lastUptime = down.uptimeMillis
                    while (true) {
                        val event = if (mode == GestureMode.UNDECIDED && held != null) {
                            val left = longPressMs - (lastUptime - down.uptimeMillis)
                            withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Long press on a plant: pick it up
                            mode = GestureMode.HOLD
                            scene.dragIndex = held
                            scene.dragStart = start
                            scene.dragPosition = start
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastUptime = change.uptimeMillis
                        active()
                        if (!change.pressed) {
                            when (mode) {
                                GestureMode.UNDECIDED -> handleTap(scene, change.position, currentState, currentOnTap, currentOnSelect)
                                GestureMode.HOLD -> {
                                    val index = scene.dragIndex
                                    val moved = scene.dragOffset.getDistance() > slop * 2
                                    scene.dragIndex = null
                                    if (index != null && !moved) {
                                        if (index != currentState.player.activeIndex) currentOnSelect(index)
                                        currentOnTap(SceneTarget.DISCARD)
                                    } else if (index != null) {
                                        drop(scene, index, change.position - start, currentState, currentOnMove, currentOnSwap)
                                    }
                                }
                                GestureMode.PET, GestureMode.IGNORE -> Unit
                            }
                            break
                        }
                        when (mode) {
                            GestureMode.UNDECIDED -> if ((change.position - start).getDistance() > slop) {
                                // Only a swipe that starts on a plant pets it (and selects it)
                                mode = if (held != null) GestureMode.PET else GestureMode.IGNORE
                                if (held != null && held != currentState.player.activeIndex) currentOnSelect(held)
                            }
                            GestureMode.PET -> {
                                currentOnPet(change.position.y - change.previousPosition.y)
                                change.consume()
                            }
                            GestureMode.HOLD -> {
                                scene.dragPosition = change.position
                                change.consume()
                            }
                            GestureMode.IGNORE -> Unit
                        }
                    }
                    if (mode == GestureMode.HOLD) scene.dragIndex = null
                }
            }
    ) {
        val plant = state.player.activePlant
        val indoor = SceneLayout.indoorScene(state.player.scenery, size, density.density)
        val roomSpots = if (indoor != null) RoomSpot.assign(state.player.plants) else null
        val layout = SceneLayout.compute(
            size = size,
            dp = density.density,
            topInset = topInset,
            bottomInset = bottomInset,
            weights = scene.weights(state),
            potAspects = state.player.plants.map { PotRenderer.aspect(PotRenderer.potFor(it.species, it.pot)) },
            activeIndex = state.player.activeIndex,
            roomSpots = roomSpots,
            indoor = indoor,
        )
        scene.layout = layout
        scene.advance(frameNanos.longValue, state)
        if (scene.lampClicked()) currentOnTap(SceneTarget.LAMP_CLICK)
        scene.toys.lightsOn = state.player.stringLights

        val targetMinutes = state.weather.localTime.minutesOfDay()
        scene.shownMinutes = if (scene.shownMinutes < 0) targetMinutes
        else lerp(scene.shownMinutes, targetMinutes, 0.05f)
        val tint = nightTint(scene.shownMinutes, state.weather)

        drawSky(scene, sprites, state.weather, scene.shownMinutes)
        drawWeatherBack(scene)
        val wind = (state.weather.facts.windSpeed.toFloat() / 40f).coerceIn(0f, 1f)
        val room = layout.room
        if (room != null) {
            // Weather only behind the glass, then the room in front of it
            room.windows.forEach { glass ->
                clipRect(glass.left, glass.top, glass.right, glass.bottom) { drawWeatherFront(scene, sprites, tint) }
            }
            room.draw(this, tint, scene.time, scene.toys)
            if (scene.dragIndex != null) drawDropSpots(scene, state, layout, room)
        } else {
            val sceneryArea = Rect(0f, size.height * 0.3f, size.width, layout.ground.top)
            val pixel = PIXEL_DP * layout.dp
            SceneryRenderer.drawLandscape(this, state.player.scenery, sceneryArea, pixel, tint, scene.time, wind, sprites.mountains)
            drawWeatherFront(scene, sprites, tint)
            drawButterflies(scene, state, layout)
            drawFireflies(scene, state, layout)
            val ground = state.player.scenery.ground ?: Ground.GRASS
            SceneryRenderer.drawSceneGround(this, ground, layout.ground, pixel, tint, scene.time, sprites.ground)
            if (ground != Ground.SNOW) drawGroundSeason(state.season, layout)
        }
        val lit = state.litPlants
        lit.forEach { drawLampLight(scene, layout, it, state.player.plants[it].id) }
        drawPlants(scene, state, sprites, layout)
        lit.forEach { drawLamp(scene, layout, it, state.player.plants[it].id) }
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
        l.coinHud.hit(4f) -> SceneTarget.SHOP
        l.weatherChip.hit(4f) -> SceneTarget.WEATHER
        plant.dead && l.replantButton.hit(4f) -> SceneTarget.REPLANT
        plant.dead && multiple && l.compostButton.hit(4f) -> SceneTarget.COMPOST
        needsShown > 0.9f && l.bubbles[0].contains(offset) -> SceneTarget.NEED_LOVE
        needsShown > 0.9f && l.bubbles[1].contains(offset) -> SceneTarget.NEED_FERTILIZER
        needsShown > 0.9f && l.bubbles[2].contains(offset) -> SceneTarget.NEED_WATER
        needsShown > 0.9f && l.bubbles[3].contains(offset) -> SceneTarget.NEED_LIGHT
        l.slots[l.activeIndex].touch.contains(offset) -> SceneTarget.PLANT.also {
            petBounceAt = time
            if (plant.species == dev.mothlight.plantagotchi.data.state.Species.FLYTRAP && !plant.dead) activeAnimator.trigger(PlantAction.SNAP, time)
        }
        else -> null
    }
}

/** Whether [offset] touches the (swinging) lamp shade. */
/** Id of the plant whose (swinging) lamp is at [offset], if any. */
private fun SceneState.lampAt(offset: Offset, l: SceneLayout, state: GameUiState): String? =
    state.litPlants.firstOrNull { index ->
        val id = state.player.plants[index].id
        val lamp = SceneState.lampPosition(l, index)
        val angle = lampSwing(id) / 180f * kotlin.math.PI.toFloat()
        // Rotate the lamp position around the suspension point at the top of the screen.
        val x = lamp.x - sin(angle) * lamp.y
        val y = cos(angle) * lamp.y
        val dx = offset.x - x
        val dy = offset.y - y
        dx * dx + dy * dy < (36 * l.dp) * (36 * l.dp)
    }?.let { state.player.plants[it].id }

/** Index of a plant other than the active one at [offset], to select it. */
private fun SceneState.plantAt(offset: Offset, state: GameUiState): Int? {
    val l = layout ?: return null
    if (l.slots.size < 2) return null
    // Keep the buttons and HUD in front.
    if (state.player.activePlant.dead && (l.replantButton.contains(offset) || l.compostButton.contains(offset))) return null
    return l.slots.indices.firstOrNull { it != l.activeIndex && l.slots[it].touch.contains(offset) }
}

private val witheredMatrix = ColorMatrix(
    floatArrayOf(
        0.35f, 0.45f, 0.10f, 0f, 20f,
        0.25f, 0.33f, 0.08f, 0f, 10f,
        0.12f, 0.18f, 0.05f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

private fun DrawScope.drawPlants(scene: SceneState, state: GameUiState, sprites: SceneSprites, l: SceneLayout) {
    val t = scene.time
    val noon = (state.weather.sunrise.minutesOfDay() + state.weather.sunset.minutesOfDay()) / 2
    val plants = state.player.plants
    // Others first, the active plant on top, a plant being moved above everything.
    val order = plants.indices.sortedBy { if (it == scene.dragIndex) 2 else if (it == l.activeIndex) 1 else 0 }
    order.forEach { i ->
        val dragged = i == scene.dragIndex
        val lift = if (dragged) scene.dragOffset + Offset(0f, -6 * l.dp) else Offset.Zero
        withTransform({ translate(lift.x, lift.y) }) { drawPlant(scene, state, sprites, l, i, t, noon) }
    }
}

private fun DrawScope.drawPlant(scene: SceneState, state: GameUiState, sprites: SceneSprites, l: SceneLayout, i: Int, t: Float, noon: Float) {
    run {
        val plants = state.player.plants
        val plant = plants[i]
        val slot = l.slots[i]
        val active = i == l.activeIndex
        val animator = scene.animator(plant.id)
        val pose = animator.pose(
            state = state,
            plant = plant,
            time = t,
            sunIsRight = scene.shownMinutes < noon,
            petBounce = if (active) bounce(t - scene.petBounceAt) else 0f,
            growBounce = if (active) bounce(t - scene.growAt, 1f) else 0f,
        )
        val maturity = animator.maturity(plant, t)
        val filter = when {
            plant.dead -> ColorFilter.colorMatrix(witheredMatrix)
            pose.droop > 0f -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - pose.droop * 0.75f) })
            else -> null
        }
        val seed = plant.id.hashCode()
        val draw: DrawScope.() -> Unit = {
            PlantRenderer.draw(this, plant.species, maturity, seed, pose, slot.base, slot.pixel)
        }
        // A plant in the planter hangs on cords from the ring under the ceiling hook.
        val ring = l.room?.hangRing?.takeIf { l.roomSpots?.getOrNull(i) == RoomSpot.HANGING && i != scene.dragIndex }
        if (ring != null) drawHangerCords(ring, slot.pot, PIXEL_DP * l.dp)
        if (filter == null) draw() else drawIntoLayer(filter) { draw() }

        val potBounce = if (active) bounce(t - scene.potBounceAt) else 0f
        withTransform({
            scale(1f - potBounce * 0.06f, 1f + potBounce * 0.08f, Offset(slot.pot.center.x, slot.pot.bottom))
        }) {
            PotRenderer.draw(this, PotRenderer.potFor(plant.species, plant.pot), slot.pot)
        }
        if (ring != null) drawHangerNet(slot.pot, PIXEL_DP * l.dp)
        // Parts of the plant in front of the pot (hanging vines)
        val front: DrawScope.() -> Unit = {
            PlantRenderer.drawFront(this, plant.species, maturity, seed, pose, slot.base, slot.pixel)
        }
        if (filter == null) front() else drawIntoLayer(filter) { front() }

        // Plants without visible bubbles call for attention when something is low.
        if ((!active || !state.plantSelected) && (plant.dead || listOf(plant.water, plant.lux, plant.love, plant.fertilizer).any { it < 20 })) {
            val bob = sin(t * 4f + i) * 2 * l.dp
            val center = Offset(slot.pot.right - 4 * l.dp, slot.pot.top - 14 * l.dp + bob)
            drawCircle(Color(0xFF222034), 9 * l.dp, center)
            drawCircle(if (plant.dead) Color(0xFF8D6E3F) else Color(0xFFFF5A5A), 7 * l.dp, center)
            drawRect(Color.White, center + Offset(-1.5f * l.dp, -5 * l.dp), Size(3 * l.dp, 6 * l.dp))
            drawRect(Color.White, center + Offset(-1.5f * l.dp, 2.5f * l.dp), Size(3 * l.dp, 2.5f * l.dp))
        }
    }
}

private val HangerRope = Color(0xFFE6D8B8)
private val HangerRopeDark = Color(0xFFA8956E)

/** A pixel line of whole art pixels. */
private fun DrawScope.pixelLine(color: Color, from: Offset, to: Offset, p: Float) {
    val n = maxOf(1, (maxOf(abs(to.x - from.x), abs(to.y - from.y)) / p).toInt())
    for (k in 0..n) {
        val x = from.x + (to.x - from.x) * k / n
        val y = from.y + (to.y - from.y) * k / n
        drawRect(color, Offset(kotlin.math.floor(x / p) * p, kotlin.math.floor(y / p) * p), Size(p, p))
    }
}

/** Macrame cords from the [ring] down to the rim of a hanging [pot], drawn behind the plant. */
private fun DrawScope.drawHangerCords(ring: Offset, pot: Rect, p: Float) {
    val rim = pot.top + pot.height * 0.1f
    for (dx in floatArrayOf(-0.42f, 0f, 0.42f)) {
        val end = Offset(pot.center.x + pot.width * dx, rim)
        pixelLine(HangerRope, ring, end, p)
        // A knot halfway
        val knot = Offset(ring.x + (end.x - ring.x) * 0.55f, ring.y + (end.y - ring.y) * 0.55f)
        drawRect(HangerRopeDark, Offset(kotlin.math.floor(knot.x / p) * p - p, kotlin.math.floor(knot.y / p) * p), Size(3 * p, 2 * p))
    }
}

/** The net of the hanger around the [pot], drawn over it: crossing cords gathered below. */
private fun DrawScope.drawHangerNet(pot: Rect, p: Float) {
    val top = pot.top + pot.height * 0.1f
    val mid = pot.top + pot.height * 0.55f
    val left = pot.left + pot.width * 0.1f
    val right = pot.right - pot.width * 0.1f
    val cx = pot.center.x
    val knot = Offset(cx, pot.bottom + 2 * p)
    pixelLine(HangerRope, Offset(left, top), Offset(cx, mid), p)
    pixelLine(HangerRope, Offset(right, top), Offset(cx, mid), p)
    pixelLine(HangerRope, Offset(cx, top), Offset(left + pot.width * 0.08f, mid), p)
    pixelLine(HangerRope, Offset(cx, top), Offset(right - pot.width * 0.08f, mid), p)
    pixelLine(HangerRope, Offset(left + pot.width * 0.08f, mid), knot, p)
    pixelLine(HangerRope, Offset(right - pot.width * 0.08f, mid), knot, p)
    pixelLine(HangerRope, Offset(cx, mid), knot, p)
    drawRect(HangerRopeDark, Offset(kotlin.math.floor(knot.x / p) * p - p, kotlin.math.floor(knot.y / p) * p), Size(3 * p, 2 * p))
    // Tassel
    drawRect(HangerRope, Offset(kotlin.math.floor(knot.x / p) * p, kotlin.math.floor(knot.y / p) * p + 2 * p), Size(p, 4 * p))
}

private inline fun DrawScope.drawIntoLayer(filter: ColorFilter, crossinline block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(
        Rect(Offset.Zero, size),
        androidx.compose.ui.graphics.Paint().apply { colorFilter = filter },
    )
    block()
    drawContext.canvas.restore()
}

private fun DrawScope.drawLampLight(scene: SceneState, l: SceneLayout, index: Int, plantId: String) {
    val lamp = SceneState.lampPosition(l, index)
    val pulse = 0.85f + 0.15f * sin(scene.time * 3f + index)
    val plant = l.slots[index].plant
    val leaves = Rect(plant.left + plant.width * 0.25f, plant.top + plant.height * 0.35f, plant.right - plant.width * 0.25f, plant.bottom - plant.height * 0.1f)
    val bottom = leaves.bottom
    rotate(scene.lampSwing(plantId), Offset(lamp.x, 0f)) {
        // The shade (12 x 10 sprite pixels of 4 dp, centered on the lamp) ends 4 dp below its center:
        // the light starts right at its rim.
        val rim = lamp.y + 4 * l.dp
        val cone = Path().apply {
            moveTo(lamp.x - 22 * l.dp, rim)
            lineTo(lamp.x + 22 * l.dp, rim)
            lineTo(leaves.right + 20 * l.dp, bottom)
            lineTo(leaves.left - 20 * l.dp, bottom)
            close()
        }
        drawPath(
            cone,
            Brush.verticalGradient(listOf(Color(0x99FFF6B0), Color(0x00FFF6B0)), startY = rim, endY = bottom),
            alpha = pulse,
        )
    }
}

private fun DrawScope.drawLamp(scene: SceneState, l: SceneLayout, index: Int, plantId: String) {
    val lamp = SceneState.lampPosition(l, index)
    rotate(scene.lampSwing(plantId), Offset(lamp.x, 0f)) {
        // Cord, glow and lamp all swing together.
        drawLine(Color(0xFF222034), Offset(lamp.x, 0f), lamp, strokeWidth = 2 * l.dp)
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
            drawChipCloud(Rect(icon.left - 2 * l.dp, icon.top + 4 * l.dp, icon.right + 2 * l.dp, icon.bottom - 4 * l.dp))
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
    drawPixelButton(l.menuButton)
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
    // The bubbles and the plant label slide in from below while a plant is selected.
    val shown = easeOut(scene.needsShown)
    if (shown <= 0.001f) return
    val bandTop = l.needsLabelBaseline - 40 * l.dp
    translate(0f, (1f - shown) * (size.height - bandTop)) { drawNeedsBand(scene, sprites, state, plant, texts, l, textMeasurer, style, bandTop) }
}

private fun DrawScope.drawNeedsBand(
    scene: SceneState,
    sprites: SceneSprites,
    state: GameUiState,
    plant: PlantState,
    texts: SceneTexts,
    l: SceneLayout,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    bandTop: Float,
) {
    // Darken the bottom so the label and bubbles stay readable on bright scenes.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x88000000)), startY = bandTop, endY = l.needsLabelBaseline),
        topLeft = Offset(0f, bandTop),
        size = Size(size.width, size.height - bandTop),
    )
    drawRect(Color(0x88000000), Offset(0f, l.needsLabelBaseline), Size(size.width, size.height - l.needsLabelBaseline))
    val label = textMeasurer.measure(texts.plantLabel, style.copy(color = Color.White))
    val labelPos = Offset((size.width - label.size.width) / 2, l.needsLabelBaseline - label.size.height)
    drawText(label, color = Color(0xFF222034), topLeft = labelPos + Offset(l.dp, l.dp))
    drawText(label, topLeft = labelPos)

    val needs = listOf(
        Triple(plant.love, PlantagotchiColors.Love, sprites.heart),
        Triple(plant.fertilizer, PlantagotchiColors.Fertilizer, sprites.fertilizer),
        Triple(plant.water, PlantagotchiColors.Water, sprites.water),
        Triple(plant.lux, PlantagotchiColors.Sunshine, sprites.sunshine),
    )
    needs.forEachIndexed { i, (value, color, icon) ->
        // A refill fills its bubble while the tool pours, not at the moment of the purchase
        val target = scene.displayedNeed(i, value.toFloat(), plant.id)
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
            drawPixelButton(Rect(center - Offset(box.width / 2, box.height / 2), box.size), fill = null)
        }
        if (i == 3 && state.lampActive && Random.nextFloat() < 0.05f) {
            scene.sparkleBurst(center, 1, l.dp, spread = radius)
        }
    }
}


private const val LOW_NEED = 20f

// Frame pacing: pixel art needs no 60/120 fps. ~30 fps while the player interacts, ~15 fps when idle.
// iOS keeps CADisableMinimumFrameDurationOnPhone in Info.plist: Compose aborts at launch on ProMotion
// devices without it; this pacing is what keeps the frame rate (and battery use) low.
private const val ACTIVE_FRAME_DELAY_MS = 22L
private const val IDLE_FRAME_DELAY_MS = 55L
private const val IDLE_AFTER_NANOS = 20_000_000_000L

private enum class GestureMode { UNDECIDED, PET, HOLD, IGNORE }

/** Index of the plant (active or not) under [offset], the active one first. */
private fun SceneState.plantIndexAt(offset: Offset, state: GameUiState): Int? {
    val l = layout ?: return null
    if (state.player.activePlant.dead && (l.replantButton.contains(offset) || l.compostButton.contains(offset))) return null
    if (l.slots[l.activeIndex].touch.contains(offset)) return l.activeIndex
    return l.slots.indices.firstOrNull { it != l.activeIndex && l.slots[it].touch.contains(offset) }
}

private fun handleTap(
    scene: SceneState,
    offset: Offset,
    state: GameUiState,
    onTap: (SceneTarget) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val l = scene.layout ?: return
    // The HUD is in front of everything in the scene (lamps, string lights).
    val hud = when {
        l.menuButton.inflate(8 * l.dp).contains(offset) -> SceneTarget.MENU
        l.chest.inflate(8 * l.dp).contains(offset) -> SceneTarget.CHEST
        l.coinHud.inflate(4 * l.dp).contains(offset) -> SceneTarget.SHOP
        l.weatherChip.inflate(4 * l.dp).contains(offset) -> SceneTarget.WEATHER
        else -> null
    }
    if (hud != null) {
        onTap(hud)
        return
    }
    val lamp = scene.lampAt(offset, l, state)
    if (lamp != null) {
        if (scene.pushLamp(lamp)) onTap(SceneTarget.LAMP)
        return
    }
    val toy = l.room?.toyAt(offset)
    if (toy != null) {
        scene.toys.tap(toy, scene.time)
        onTap(
            when (toy) {
                IndoorToy.CAT -> SceneTarget.CAT
                IndoorToy.FIRE -> SceneTarget.FIRE
                IndoorToy.STRING_LIGHTS -> SceneTarget.STRING_LIGHTS
                else -> SceneTarget.DESK_LAMP
            }
        )
        return
    }
    val selected = scene.plantAt(offset, state)
    if (selected != null) onSelect(selected)
    else onTap(scene.hitTest(offset, state) ?: SceneTarget.EMPTY)
}

/** Where a dragged pot would land: the spot (room) or plant slot (outdoors) nearest to it. */
private fun dropTarget(scene: SceneState, index: Int, delta: Offset, state: GameUiState): Any? {
    val l = scene.layout ?: return null
    val pot = l.slots[index].pot
    val dropped = Offset(pot.center.x, pot.bottom) + delta
    val room = l.room
    return if (room != null) {
        RoomSpot.entries
            .filter { it.accepts(state.player.plants[index].species) }
            .map { it to (room.anchor(it).first - dropped).getDistance() }
            .filter { it.second < l.size.width * 0.22f }
            .minByOrNull { it.second }?.first
    } else {
        l.slots.indices.minByOrNull { kotlin.math.abs(l.slots[it].pot.center.x - dropped.x) }?.takeIf { it != index }
    }
}

private fun drop(
    scene: SceneState,
    index: Int,
    delta: Offset,
    state: GameUiState,
    onMove: (Int, RoomSpot) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    when (val target = dropTarget(scene, index, delta, state)) {
        is RoomSpot -> onMove(index, target)
        is Int -> onSwap(index, target)
    }
}

/** While a plant is carried in the room: the spots it can go to, the nearest one highlighted. */
private fun DrawScope.drawDropSpots(scene: SceneState, state: GameUiState, l: SceneLayout, room: IndoorScene) {
    val index = scene.dragIndex ?: return
    val target = dropTarget(scene, index, scene.dragOffset, state)
    val species = state.player.plants.getOrNull(index)?.species ?: return
    RoomSpot.entries.filter { it.accepts(species) }.forEach { spot ->
        val (anchor, width) = room.anchor(spot)
        val highlight = spot == target
        val color = if (highlight) Color(0xCCFFEB57) else Color(0x66FFFFFF)
        val r = Rect(anchor.x - width / 2, anchor.y - 4 * l.dp, anchor.x + width / 2, anchor.y + 4 * l.dp)
        drawOval(color, r.topLeft, r.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2 * l.dp))
        if (highlight) drawOval(Color(0x33FFEB57), r.topLeft, r.size)
    }
}
