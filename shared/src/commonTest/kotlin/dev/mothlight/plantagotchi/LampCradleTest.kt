package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.ui.game.LampPhysics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LampCradleTest {
    private val ids = listOf("a", "b", "c")

    /** Three lamps on 400 px cords, [spacing] px apart, with 48 px shades. */
    private fun lamps(spacing: Float) = LampPhysics().apply {
        sync(ids, { 100f + it * spacing }, { 400f }, 48f)
    }

    private fun LampPhysics.run(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds / 0.016f).toInt()) { step(0.016f); each() }
    }

    @Test
    fun aRowPassesThePushToTheOtherEnd() {
        val l = lamps(spacing = 50f)
        assertTrue(l.push("a"))
        var maxA = 0f; var maxB = 0f; var minC = 0f; var clicks = 0
        l.run(2.5f) {
            maxA = maxOf(maxA, l.angle("a")); maxB = maxOf(maxB, abs(l.angle("b"))); minC = minOf(minC, l.angle("c"))
            if (l.clicked()) clicks++
        }
        assertTrue(maxA > 10f, "the pushed left end swings out to the left (positive angle)")
        assertTrue(minC < -8f, "the right end flies out to the right, was $minC")
        assertTrue(maxB < 3f, "the middle hardly moves, was $maxB")
        assertTrue(clicks >= 1, "bumps click")
    }

    @Test
    fun lampsNeverPassThroughEachOther() {
        val l = lamps(spacing = 50f)
        l.push("a")
        l.run(6f) {
            // Shade x = pivot - sin(angle) * length; neighbours keep their order.
            val xs = ids.indices.map { 100f + it * 50f - kotlin.math.sin(l.angle(ids[it]) * 0.017453f) * 400f }
            assertTrue(xs[0] < xs[1] + 12f && xs[1] < xs[2] + 12f, "order kept: $xs")
        }
    }

    @Test
    fun lampsFarApartSwingAlone() {
        val l = lamps(spacing = 300f)
        l.push("a")
        var clicks = 0
        l.run(3f) { if (l.clicked()) clicks++ }
        assertEquals(0f, l.angle("b"))
        assertEquals(0f, l.angle("c"))
        assertEquals(0, clicks)
    }

    @Test
    fun everyLampCanBePushedWhileOthersSwing() {
        val l = lamps(spacing = 300f)
        l.push("a")
        l.run(0.3f)
        assertTrue(l.push("c"), "a second lamp can be pushed any time")
        l.run(0.3f)
        assertTrue(l.angle("a") != 0f)
        assertTrue(l.angle("c") != 0f)
    }

    @Test
    fun theSwingDiesDown() {
        val l = lamps(spacing = 50f)
        l.push("b")
        assertTrue(l.swinging)
        l.run(60f)
        assertFalse(l.swinging)
        assertEquals(0f, l.angle("b"))
    }
}
