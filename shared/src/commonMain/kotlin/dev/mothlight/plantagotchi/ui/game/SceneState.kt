package dev.mothlight.plantagotchi.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.mothlight.plantagotchi.data.shop.Need
import dev.mothlight.plantagotchi.data.shop.ShopItem
import dev.mothlight.plantagotchi.data.state.Season
import dev.mothlight.plantagotchi.data.weather.WeatherState
import dev.mothlight.plantagotchi.model.GameEvent
import dev.mothlight.plantagotchi.model.GameUiState
import dev.mothlight.plantagotchi.ui.particlesystem.ParticleSystem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal enum class FxKind { HEART, TEXT, WATER, DIRT, SPARKLE, LEAF, COIN }

/** A short-lived particle: hearts, floating numbers, water drops, ... */
internal class Fx(
    val kind: FxKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val size: Float,
    val text: String = "",
    val color: Color = Color.White,
    val phase: Float = Random.nextFloat() * 6.28f,
    /** For COIN: where it flies to. */
    val target: Offset = Offset.Zero,
    val start: Offset = Offset(x, y),
) {
    var age = 0f
    val progress get() = (age / life).coerceIn(0f, 1f)
    val alive get() = age < life
}

/** A shop item flying in, being used on the plant and flying out again. */
internal class ToolAnimation(val sprite: PixelSprite, val kind: FxKind, val start: Float) {
    val duration = 2.4f
}

internal class ThunderPath(var id: Long = -1, var points: List<Offset> = emptyList())

/**
 * Everything the canvas animates between frames. Not Compose state: the canvas redraws every
 * frame anyway, so mutating this does not need to trigger recomposition.
 */
internal class SceneState {
    /** Seconds since the scene started. */
    var time = 0f
        internal set // tests drive the clock
    private var lastNanos = 0L

    /** 60 Hz step counter, independent of the display refresh rate. */
    val step: Long get() = (time * 60).toLong()

    var layout: SceneLayout? = null

    // Smoothed values so changes animate instead of jumping.
    val shownNeeds = FloatArray(4) { -1f }
    var shownMinutes = -1f

    // Weather crossfade
    var weather: WeatherState? = null
        private set
    var previousWeather: WeatherState? = null
        private set
    private var weatherChangedAt = -10f
    val weatherFade get() = ((time - weatherChangedAt) / 1.2f).coerceIn(0f, 1f)

    val particleSystems = HashMap<String, ParticleSystem>()
    val thunder = ThunderPath()

    // Animation triggers (time stamps)
    var petBounceAt = -10f
    var potBounceAt = -10f
    var coinPopAt = -10f
    var growAt = -10f
    var chestOpenAt = -10f
    var switchAt = -10f
    var switchDirection = 1
    private var lastCoins = -1
    private var pendingCoinGain = 0
    private var lastCoinText = -10f
    private var lastLeafAt = 0f
    private var lastAmbientAt = 0f
    private var lastActiveIndex = -1

    // Cozy room: the desk lamp can be switched, the cat flicks its tail when tapped.
    var deskLampOn = true
    var catFlickAt = -10f

    /** Plant being moved with a long press and drag, where the drag started and the finger is. */
    var dragIndex: Int? = null
    var dragStart = Offset.Zero
    var dragPosition = Offset.Zero
    val dragOffset: Offset get() = dragPosition - dragStart

    // Grow lamps hang above every lit plant and stay still until tapped. A single lamp swings out
    // with damping. With two or more, the end lamps form a Newton's cradle: the tapped end swings
    // out and back, "hits" the row, and the lamp at the other end flies out, back and forth.
    private var swingStart = -100f
    private var soloLamp: String? = null
    private var cradleLeft: String? = null
    private var cradleRight: String? = null
    private var cradleFromLeft = true
    private var lastHalfSwing = 0

    private val swingAmplitude: Float get() = LAMP_AMPLITUDE * kotlin.math.exp(-LAMP_DAMPING * (time - swingStart))

    /** True while any lamp still visibly moves. */
    val lampSwinging: Boolean get() = swingAmplitude > LAMP_REST

    /** Current angle in degrees of the lamp above [plantId]. */
    fun lampSwing(plantId: String): Float {
        if (!lampSwinging) return 0f
        val t = time - swingStart
        val amplitude = swingAmplitude
        if (soloLamp != null) {
            return if (plantId == soloLamp) amplitude * sin(t * 2 * PI.toFloat() / LAMP_PERIOD) else 0f
        }
        val half = LAMP_PERIOD / 2
        val k = (t / half).toInt()
        val phase = sin((t - k * half) / half * PI.toFloat())
        // Even half swings belong to the end that was pulled, odd ones to the other end.
        val leftMoves = (k % 2 == 0) == cradleFromLeft
        return when (plantId) {
            cradleLeft -> if (leftMoves) -amplitude * phase else 0f
            cradleRight -> if (!leftMoves) amplitude * phase else 0f
            else -> 0f
        }
    }

    /**
     * Pushes the lamp above [plantId]; [lit] are the plants with a lamp, left to right. Taps while
     * lamps swing add no energy. Returns true if pushed.
     */
    fun pushLamp(plantId: String, lit: List<String>): Boolean {
        if (lampSwinging) return false
        swingStart = time
        lastHalfSwing = 0
        if (lit.size >= 2 && (plantId == lit.first() || plantId == lit.last())) {
            soloLamp = null
            cradleLeft = lit.first()
            cradleRight = lit.last()
            cradleFromLeft = plantId == lit.first()
        } else {
            soloLamp = plantId
        }
        return true
    }

    /** True once per collision of the cradle (the moment the energy passes through the row). */
    fun lampClicked(): Boolean {
        if (soloLamp != null || !lampSwinging) return false
        val k = ((time - swingStart) / (LAMP_PERIOD / 2)).toInt()
        if (k <= lastHalfSwing) return false
        lastHalfSwing = k
        return true
    }

    private val animators = HashMap<String, PlantAnimator>()

    /** Each plant moves on its own. */
    fun animator(plantId: String): PlantAnimator = animators.getOrPut(plantId) { PlantAnimator() }

    /** The active plant's animator, for event reactions. */
    var activeAnimator: PlantAnimator = PlantAnimator()
        private set

    private val slotWeights = HashMap<String, Float>()

    /** Animated layout weights: the active plant grows, the others shrink. */
    /** 0 = need bubbles hidden below the screen, 1 = shown; follows the plant selection. */
    var needsShown = 0f
        private set

    fun weights(state: GameUiState): List<Float> {
        val activeId = state.player.activePlant.id
        val dt = 1f / 60f
        activeAnimator = animator(activeId)
        needsShown += ((if (state.plantSelected) 1f else 0f) - needsShown) * min(1f, dt * 9f)
        if (kotlin.math.abs(needsShown - (if (state.plantSelected) 1f else 0f)) < 0.002f) needsShown = if (state.plantSelected) 1f else 0f
        return state.player.plants.map { plant ->
            // The selected plant is shown larger.
            val target = if (plant.id == activeId && state.plantSelected) SceneLayout.ACTIVE_WEIGHT else 1f
            val current = slotWeights[plant.id] ?: target
            val next = current + (target - current) * min(1f, dt * 8f)
            slotWeights[plant.id] = next
            next
        }
    }

    val effects = mutableListOf<Fx>()
    val tools = mutableListOf<ToolAnimation>()
    private val pendingEvents = ArrayDeque<GameEvent>()

    fun post(event: GameEvent) = pendingEvents.addLast(event)

    /** Advances the clock and all effects. Returns the frame delta in seconds. */
    fun advance(frameNanos: Long, state: GameUiState): Float {
        val dt = if (lastNanos == 0L) 0f else ((frameNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = frameNanos
        time += dt

        val l = layout ?: return dt
        trackWeather(state.weather.facts.state)
        trackCoins(state.player.coins, l)
        trackActivePlant(state.player.activeIndex)
        while (pendingEvents.isNotEmpty()) handle(pendingEvents.removeFirst(), l)
        val plant = state.player.activePlant
        if (!plant.dead && plant.health < 35 && time - lastLeafAt > 2.5f) {
            lastLeafAt = time
            spawnLeaf(l, Color(0xFF7FA03A))
        }
        spawnSeasonal(state, l)
        updateTools(l)
        updateEffects(dt, l)
        return dt
    }

    private fun trackWeather(current: WeatherState) {
        if (weather == null) {
            weather = current
        } else if (current != weather) {
            previousWeather = weather
            weather = current
            weatherChangedAt = time
        }
    }

    private fun trackActivePlant(index: Int) {
        if (lastActiveIndex >= 0 && index != lastActiveIndex) {
            switchDirection = if (index > lastActiveIndex) 1 else -1
            switchAt = time
        }
        lastActiveIndex = index
    }

    private fun trackCoins(coins: Int, l: SceneLayout) {
        if (lastCoins < 0) {
            lastCoins = coins
            return
        }
        val diff = coins - lastCoins
        lastCoins = coins
        if (diff > 0) {
            pendingCoinGain += diff
            coinPopAt = time
        } else if (diff < 0) {
            floatText("$diff", l, Color(0xFFFF6B6B))
        }
        if (pendingCoinGain > 0 && time - lastCoinText > 0.7f) {
            floatText("+$pendingCoinGain", l, Color(0xFFFFEB57))
            pendingCoinGain = 0
            lastCoinText = time
        }
    }

    private fun floatText(text: String, l: SceneLayout, color: Color) {
        effects += Fx(
            FxKind.TEXT, l.coinHud.center.x + 10 * l.dp, l.coinHud.bottom,
            vx = 0f, vy = -30 * l.dp, life = 1.2f, size = 16f, text = text, color = color,
        )
    }

    private fun handle(event: GameEvent, l: SceneLayout) {
        when (event) {
            GameEvent.Petted -> {
                if (time - petBounceAt > 0.8f) activeAnimator.trigger(PlantAction.WIGGLE, time)
                petBounceAt = time
                repeat(2) {
                    val leaves = l.leaves
                    effects += Fx(
                        FxKind.HEART,
                        x = leaves.left + Random.nextFloat() * leaves.width,
                        y = leaves.top + Random.nextFloat() * leaves.height * 0.5f,
                        vx = (Random.nextFloat() - 0.5f) * 30 * l.dp,
                        vy = -(50 + Random.nextFloat() * 40) * l.dp,
                        life = 1.4f,
                        size = (16 + Random.nextFloat() * 8) * l.dp,
                    )
                }
            }

            is GameEvent.Purchased -> when (val item = event.item) {
                is ShopItem.Refill -> tools += ToolAnimation(
                    sprite = if (item.need == Need.WATER) CodeSprites.wateringCan else CodeSprites.fertilizerBag,
                    kind = if (item.need == Need.WATER) FxKind.WATER else FxKind.DIRT,
                    start = time,
                ).also { activeAnimator.trigger(PlantAction.STRETCH, time, delay = 1.9f) }
                is ShopItem.GrowLamp -> {
                    sparkleBurst(lampPosition(l), 14, l.dp)
                    activeAnimator.trigger(PlantAction.LOOK, time)
                }
                is ShopItem.Pot -> {
                    potBounceAt = time
                    sparkleBurst(l.pot.center, 18, l.dp, spread = l.pot.width / 2)
                }
                is ShopItem.Backdrop, is ShopItem.Floor -> repeat(4) { i ->
                    sparkleBurst(Offset(l.size.width * (0.15f + i * 0.23f), l.ground.top - 40 * l.dp), 8, l.dp, spread = 60 * l.dp)
                }
                is ShopItem.Revive, is ShopItem.Seed -> {
                    growAt = time
                    sparkleBurst(l.leaves.center, 24, l.dp, spread = l.leaves.width / 2)
                }
            }

            is GameEvent.GrewUp -> {
                growAt = time
                activeAnimator.trigger(PlantAction.DANCE, time, delay = 0.5f)
                sparkleBurst(l.leaves.center, 30, l.dp, spread = l.leaves.width / 2)
            }
            GameEvent.Replanted -> {
                growAt = time
                sparkleBurst(l.pot.center.copy(y = l.pot.top), 16, l.dp, spread = l.pot.width / 3)
            }
            is GameEvent.Withered -> repeat(8) { spawnLeaf(l, Color(0xFF8D6E3F)) }
            is GameEvent.RewardClaimed -> {
                chestOpenAt = time
                repeat(min(event.coins / 5, 20)) { i ->
                    effects += Fx(
                        FxKind.COIN,
                        x = l.chest.center.x, y = l.chest.center.y,
                        vx = 0f, vy = 0f,
                        life = 0.7f + i * 0.04f,
                        size = 14 * l.dp,
                        target = l.coinHud.center,
                    )
                }
            }
            GameEvent.RewardAlreadyClaimed -> chestOpenAt = time - 0.5f
            GameEvent.PlantSwitched -> Unit
            is GameEvent.DevPlantAction -> PlantAction.entries.firstOrNull { it.name == event.action }
                ?.let { activeAnimator.trigger(it, time) }
        }
    }

    fun sparkleBurst(center: Offset, count: Int, dp: Float, spread: Float = 40 * dp) {
        repeat(count) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = (30 + Random.nextFloat() * 60) * dp
            effects += Fx(
                FxKind.SPARKLE,
                x = center.x + cos(angle) * spread * Random.nextFloat(),
                y = center.y + sin(angle) * spread * Random.nextFloat() * 0.6f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 20 * dp,
                life = 0.9f + Random.nextFloat() * 0.6f,
                size = 2 * dp,
            )
        }
    }

    private fun spawnLeaf(l: SceneLayout, color: Color) {
        val leaves = l.leaves
        effects += Fx(
            FxKind.LEAF,
            x = leaves.left + Random.nextFloat() * leaves.width,
            y = leaves.center.y,
            vx = 0f, vy = 25 * l.dp,
            life = 3.5f, size = 6 * l.dp, color = color,
        )
    }

    /** Autumn leaves and spring petals drifting across the scene. */
    private fun spawnSeasonal(state: GameUiState, l: SceneLayout) {
        val (interval, colors) = when {
            state.player.scenery == dev.mothlight.plantagotchi.data.state.Scenery.ZEN ->
                0.9f to listOf(Color(0xFFFFC2DC), Color(0xFFF4A6C6), Color(0xFFFFFFFF))
            state.season == Season.AUTUMN -> 0.8f to listOf(Color(0xFFE07A2E), Color(0xFFC94A2B), Color(0xFFE8B83D))
            state.season == Season.SPRING -> 1.4f to listOf(Color(0xFFFFC2DC), Color(0xFFFFFFFF))
            else -> return
        }
        if (time - lastAmbientAt < interval) return
        lastAmbientAt = time
        effects += Fx(
            FxKind.LEAF,
            x = Random.nextFloat() * l.size.width,
            y = -10 * l.dp,
            vx = 0f, vy = (30 + Random.nextFloat() * 20) * l.dp,
            life = l.size.height / (30 * l.dp),
            size = (if (state.season == Season.AUTUMN && state.player.scenery != dev.mothlight.plantagotchi.data.state.Scenery.ZEN) 6 else 4) * l.dp,
            color = colors.random(),
        )
    }

    /** Tool position, rotation and whether it is currently pouring. */
    class ToolPose(val center: Offset, val rotation: Float, val alpha: Float, val pouring: Boolean, val pixel: Float)

    fun toolPose(tool: ToolAnimation, l: SceneLayout): ToolPose {
        val p = ((time - tool.start) / tool.duration).coerceIn(0f, 1f)
        // Hold the tool so its spout is right above the soil, a bit right of the stem.
        val spoutOffset = tool.sprite.width * 4 * l.dp * 0.55f
        val target = Offset(
            l.pot.center.x + l.pot.width * 0.15f + spoutOffset,
            maxOf(l.pot.top - l.pot.width * 0.75f, 110 * l.dp),
        )
        val offscreen = Offset(l.size.width + 80 * l.dp, target.y - 60 * l.dp)
        val tilt = if (tool.kind == FxKind.WATER) -35f else -110f
        val pixel = 4 * l.dp
        return when {
            p < 0.2f -> ToolPose(lerpOffset(offscreen, target, easeOut(p / 0.2f)), 0f, 1f, false, pixel)
            p < 0.3f -> ToolPose(target, tilt * easeOut((p - 0.2f) / 0.1f), 1f, false, pixel)
            p < 0.75f -> ToolPose(target, tilt + sin(time * 6f) * 3f, 1f, true, pixel)
            p < 0.85f -> ToolPose(target, tilt * (1 - (p - 0.75f) / 0.1f), 1f, false, pixel)
            else -> {
                val t = (p - 0.85f) / 0.15f
                ToolPose(lerpOffset(target, offscreen, t * t), 0f, 1f - t, false, pixel)
            }
        }
    }

    private fun updateTools(l: SceneLayout) {
        tools.removeAll { time - it.start > it.duration }
        tools.forEach { tool ->
            val pose = toolPose(tool, l)
            if (!pose.pouring) return@forEach
            // Spout / opening is on the left after tilting.
            val spout = Offset(pose.center.x - tool.sprite.width * pose.pixel * 0.55f, pose.center.y + 4 * l.dp)
            repeat(if (tool.kind == FxKind.WATER) 2 else 1) {
                effects += Fx(
                    tool.kind,
                    x = spout.x + (Random.nextFloat() - 0.5f) * 8 * l.dp,
                    y = spout.y,
                    vx = -(5 + Random.nextFloat() * 15) * l.dp,
                    vy = (20 + Random.nextFloat() * 30) * l.dp,
                    life = 1.1f,
                    size = (if (tool.kind == FxKind.WATER) 3f else 4f) * l.dp,
                    color = if (tool.kind == FxKind.WATER) Color(0xFF5FCDE4) else
                        listOf(Color(0xFF8F563B), Color(0xFF5D3A2A), Color(0xFFB86F50)).random(),
                )
            }
        }
    }

    private fun updateEffects(dt: Float, l: SceneLayout) {
        val dp = l.dp
        // Water and fertilizer soak into the soil of the active pot.
        val soil = l.pot.top + l.pot.height * 0.12f
        val potLeft = l.pot.left + l.pot.width * 0.1f
        val potRight = l.pot.right - l.pot.width * 0.1f
        val splashes = mutableListOf<Fx>()
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val fx = iterator.next()
            fx.age += dt
            if (!fx.alive) {
                iterator.remove()
                if (fx.kind == FxKind.COIN) coinPopAt = time
                continue
            }
            when (fx.kind) {
                FxKind.WATER, FxKind.DIRT -> fx.vy += 700 * dp * dt
                FxKind.LEAF -> fx.vx = sin(time * 2.5f + fx.phase) * 25 * dp
                FxKind.HEART -> fx.vx = sin(time * 4f + fx.phase) * 20 * dp
                FxKind.SPARKLE -> {
                    fx.vx *= max(0f, 1 - 2.5f * dt)
                    fx.vy *= max(0f, 1 - 2.5f * dt)
                }
                FxKind.TEXT -> Unit
                FxKind.COIN -> {
                    // Arc from the chest to the coin counter.
                    val t = easeOut(fx.progress)
                    val p = lerpOffset(fx.start, fx.target, t)
                    fx.x = p.x + sin(fx.phase) * 40 * dp * sin(t * PI.toFloat())
                    fx.y = p.y + 90 * dp * sin(t * PI.toFloat())
                    continue
                }
            }
            fx.x += fx.vx * dt
            fx.y += fx.vy * dt
            if ((fx.kind == FxKind.WATER || fx.kind == FxKind.DIRT) && fx.vy > 0) {
                val inPot = fx.y >= soil && fx.x in potLeft..potRight
                if (inPot && fx.kind == FxKind.WATER && fx.size > 2.5f * dp && Random.nextFloat() < 0.3f) {
                    repeat(2) { i ->
                        splashes += Fx(
                            FxKind.WATER, fx.x, soil - dp, vx = (if (i == 0) -1 else 1) * (15 + Random.nextFloat() * 20) * dp,
                            vy = -(40 + Random.nextFloat() * 30) * dp, life = 0.3f, size = 2f * dp, color = fx.color,
                        )
                    }
                }
                if (inPot || fx.y >= l.ground.top) iterator.remove()
            }
        }
        effects += splashes
        if (effects.size > MAX_EFFECTS) effects.subList(0, effects.size - MAX_EFFECTS).clear()
    }

    companion object {
        const val MAX_EFFECTS = 500
        const val LAMP_AMPLITUDE = 16f
        const val LAMP_PERIOD = 1.7f
        const val LAMP_DAMPING = 0.45f
        /** Below this amplitude (degrees) the lamp counts as standing still. */
        const val LAMP_REST = 0.4f

        /** Lamp above the active plant. */
        fun lampPosition(l: SceneLayout) = lampPosition(l, l.activeIndex)

        /** Lamp above plant [index]; outdoors all lamps hang at the same height, like a cradle. */
        fun lampPosition(l: SceneLayout, index: Int): Offset {
            val plant = l.slots[index].plant
            val top = if (l.room != null) plant else l.plant
            return Offset(plant.center.x, top.top + top.height * 0.12f)
        }
    }
}

internal fun easeOut(t: Float) = 1 - (1 - t) * (1 - t)

internal fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Damped oscillation after an event at [since] seconds ago, 0 when settled. */
internal fun bounce(since: Float, duration: Float = 0.7f): Float =
    if (since < 0 || since > duration) 0f
    else sin(since * PI.toFloat() * 4) * exp(-since * 6) * min(1f, since * 20)
