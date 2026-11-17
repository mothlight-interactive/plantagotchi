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
        private set
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
        updateEffects(dt, l.dp)
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
                )
                is ShopItem.GrowLamp -> sparkleBurst(lampPosition(l), 14, l.dp)
                is ShopItem.Pot -> {
                    potBounceAt = time
                    sparkleBurst(l.pot.center, 18, l.dp, spread = l.pot.width / 2)
                }
                is ShopItem.Revive, is ShopItem.Seed -> {
                    growAt = time
                    sparkleBurst(l.leaves.center, 24, l.dp, spread = l.leaves.width / 2)
                }
            }

            is GameEvent.GrewUp -> {
                growAt = time
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
            is GameEvent.WaterReceived -> repeat(40) {
                val leaves = l.leaves
                effects += Fx(
                    FxKind.WATER,
                    x = leaves.left + Random.nextFloat() * leaves.width,
                    y = leaves.top - 100 * l.dp - Random.nextFloat() * 120 * l.dp,
                    vx = 0f, vy = 80 * l.dp,
                    life = 1.4f, size = 3 * l.dp, color = Color(0xFF5FCDE4),
                )
            }
            GameEvent.PlantSwitched -> Unit
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
        val (interval, colors) = when (state.season) {
            Season.AUTUMN -> 0.8f to listOf(Color(0xFFE07A2E), Color(0xFFC94A2B), Color(0xFFE8B83D))
            Season.SPRING -> 1.4f to listOf(Color(0xFFFFC2DC), Color(0xFFFFFFFF))
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
            size = (if (state.season == Season.SPRING) 4 else 6) * l.dp,
            color = colors.random(),
        )
    }

    /** Tool position, rotation and whether it is currently pouring. */
    class ToolPose(val center: Offset, val rotation: Float, val alpha: Float, val pouring: Boolean, val pixel: Float)

    fun toolPose(tool: ToolAnimation, l: SceneLayout): ToolPose {
        val p = ((time - tool.start) / tool.duration).coerceIn(0f, 1f)
        val target = Offset(l.leaves.right + 20 * l.dp, l.leaves.top - 40 * l.dp)
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
                    vx = -(30 + Random.nextFloat() * 40) * l.dp,
                    vy = (20 + Random.nextFloat() * 40) * l.dp,
                    life = 1.1f,
                    size = (if (tool.kind == FxKind.WATER) 3f else 4f) * l.dp,
                    color = if (tool.kind == FxKind.WATER) Color(0xFF5FCDE4) else
                        listOf(Color(0xFF8F563B), Color(0xFF5D3A2A), Color(0xFFB86F50)).random(),
                )
            }
        }
    }

    private fun updateEffects(dt: Float, dp: Float) {
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
        }
        if (effects.size > MAX_EFFECTS) effects.subList(0, effects.size - MAX_EFFECTS).clear()
    }

    companion object {
        const val MAX_EFFECTS = 500

        fun lampPosition(l: SceneLayout) = Offset(l.plant.center.x, l.plant.top + l.plant.height * 0.12f)
    }
}

internal fun easeOut(t: Float) = 1 - (1 - t) * (1 - t)

internal fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Damped oscillation after an event at [since] seconds ago, 0 when settled. */
internal fun bounce(since: Float, duration: Float = 0.7f): Float =
    if (since < 0 || since > duration) 0f
    else sin(since * PI.toFloat() * 4) * exp(-since * 6) * min(1f, since * 20)
