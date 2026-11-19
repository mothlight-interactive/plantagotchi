package dev.mothlight.plantagotchi.ui.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Hanging grow lamps as damped pendulums that bump into each other. Every lamp can be pushed at
 * any time. Two shades that touch swap their speed (equal masses, elastic), so lamps hanging
 * close together in a row pass a push along like a Newton's cradle, and lamps hanging apart just
 * swing on their own.
 *
 * Angles are in degrees as the canvas rotates them: positive turns clockwise around the
 * suspension point, which moves a hanging lamp to the left.
 */
internal class LampPhysics {
    private class Lamp(val id: String) {
        var pivotX = 0f
        var length = 1f
        var angle = 0f
        /** Degrees per second. */
        var speed = 0f

        val rad: Float get() = angle * DEG
        val x: Float get() = pivotX - sin(rad) * length
        val y: Float get() = cos(rad) * length
        /** Sideways speed of the shade in px/s. */
        var linear: Float
            get() = -cos(rad) * length * speed * DEG
            set(v) { speed = -v / (cos(rad) * length * DEG) }
    }

    private val lamps = ArrayList<Lamp>()
    private var shade = 1f
    private var clicks = 0

    /** True while any lamp still visibly moves. */
    val swinging: Boolean get() = lamps.any { abs(it.angle) > REST || abs(it.speed) > REST_SPEED }

    /**
     * Sets which lamps hang where: id, x of the suspension point and cord length (px) per lamp,
     * and the width of a shade. Lamps that stay keep their motion.
     */
    fun sync(ids: List<String>, pivotX: (Int) -> Float, length: (Int) -> Float, shadeWidth: Float) {
        shade = shadeWidth
        if (ids.size != lamps.size || ids.indices.any { lamps[it].id != ids[it] }) {
            val old = lamps.associateBy { it.id }
            lamps.clear()
            ids.forEach { lamps += old[it] ?: Lamp(it) }
        }
        for (i in lamps.indices) {
            lamps[i].pivotX = pivotX(i)
            lamps[i].length = length(i).coerceAtLeast(1f)
        }
    }

    /** Current angle in degrees of the lamp [id], 0 if there is none. */
    fun angle(id: String): Float = lamps.firstOrNull { it.id == id }?.angle ?: 0f

    /**
     * Pushes the lamp [id]. [towardsRight] says where the tap wants it to go; null picks the free
     * side (away from a close neighbour), so an end lamp of a row swings out and comes back in.
     */
    fun push(id: String, towardsRight: Boolean? = null): Boolean {
        val lamp = lamps.firstOrNull { it.id == id } ?: return false
        val right = towardsRight ?: run {
            val leftNear = lamps.any { it !== lamp && it.pivotX < lamp.pivotX && near(it, lamp) }
            val rightNear = lamps.any { it !== lamp && it.pivotX > lamp.pivotX && near(it, lamp) }
            if (leftNear != rightNear) leftNear else lamp.speed > 0f
        }
        // Speed that reaches PUSH_AMPLITUDE from rest; positive angles go left.
        val kick = PUSH_AMPLITUDE * OMEGA
        lamp.speed = (lamp.speed + if (right) -kick else kick).coerceIn(-kick * 1.6f, kick * 1.6f)
        return true
    }

    private fun near(a: Lamp, b: Lamp) =
        abs(a.pivotX - b.pivotX) < shade * 2.2f && abs(a.length - b.length) < shade * 0.5f

    /** Advances the lamps by [dt] seconds. */
    fun step(dt: Float) {
        if (lamps.isEmpty() || !swinging) {
            lamps.forEach { it.angle = 0f; it.speed = 0f }
            return
        }
        val steps = (dt / 0.004f).toInt().coerceIn(1, 40)
        val h = dt / steps
        val damp = exp(-DAMPING * h)
        repeat(steps) {
            for (lamp in lamps) {
                lamp.speed = (lamp.speed - OMEGA * OMEGA * lamp.angle * h) * damp
                lamp.angle += lamp.speed * h
            }
            collide()
        }
        if (!swinging) lamps.forEach { it.angle = 0f; it.speed = 0f }
    }

    private fun collide() {
        // A few passes let a push run through a whole row within one step.
        repeat(min(lamps.size, 6)) {
            for (i in lamps.indices) for (j in i + 1 until lamps.size) {
                val a = if (lamps[i].pivotX <= lamps[j].pivotX) lamps[i] else lamps[j]
                val b = if (a === lamps[i]) lamps[j] else lamps[i]
                val rest = b.pivotX - a.pivotX
                if (abs(a.y - b.y) > shade * 0.5f) continue
                // Shades that overlap at rest (plants standing very close) touch at their rest distance.
                val contact = min(shade, rest)
                if (contact <= 0f) continue
                val gap = b.x - a.x
                if (gap >= contact) continue
                val va = a.linear
                val vb = b.linear
                if (va - vb <= 0f) continue // already moving apart
                a.linear = vb
                b.linear = va
                if (va - vb > CLICK_SPEED * shade) clicks++
            }
        }
    }

    /** True once per audible bump since the last call. */
    fun clicked(): Boolean {
        val c = clicks > 0
        clicks = 0
        return c
    }

    companion object {
        /** Seconds per full swing. */
        const val PERIOD = 1.7f
        const val DAMPING = 0.22f
        /** Degrees a pushed lamp swings out. */
        const val PUSH_AMPLITUDE = 16f
        const val REST = 0.3f
        const val REST_SPEED = 1.5f
        /** Bumps slower than this many shade widths per second make no sound. */
        const val CLICK_SPEED = 0.25f
        private const val DEG = PI.toFloat() / 180f
        private const val OMEGA = 2f * PI.toFloat() / PERIOD
    }
}
