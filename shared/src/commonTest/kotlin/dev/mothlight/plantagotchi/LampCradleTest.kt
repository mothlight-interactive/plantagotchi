package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.ui.game.SceneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LampCradleTest {
    private val lit = listOf("a", "b", "c")
    private val half = SceneState.LAMP_PERIOD / 2

    @Test
    fun theEndsTakeTurnsAndTheMiddleStaysStill() {
        val s = SceneState()
        s.time = 10f
        assertTrue(s.pushLamp("a", lit))
        assertFalse(s.pushLamp("c", lit), "taps while swinging add no energy")

        s.time = 10f + half / 2
        // Positive degrees rotate clockwise: the hanging lamp moves to the left.
        assertTrue(s.lampSwing("a") > 0f, "the pulled left lamp swings out to the left")
        assertEquals(0f, s.lampSwing("b"))
        assertEquals(0f, s.lampSwing("c"))

        s.time = 10f + half * 1.5f
        assertEquals(0f, s.lampSwing("a"))
        assertTrue(s.lampSwing("c") < 0f, "then the right end flies out to the right")
        assertEquals(0f, s.lampSwing("b"))
    }

    @Test
    fun clicksOncePerCollisionAndComesToRest() {
        val s = SceneState()
        s.time = 0f
        s.pushLamp("c", lit)
        var clicks = 0
        var t = 0f
        while (t < half * 4) {
            s.time = t
            if (s.lampClicked()) clicks++
            t += half / 8
        }
        assertEquals(3, clicks)
        s.time = 30f
        assertFalse(s.lampSwinging)
    }

    @Test
    fun aMiddleOrSingleLampSwingsAlone() {
        val s = SceneState()
        s.pushLamp("b", lit)
        s.time = half / 2
        assertTrue(s.lampSwing("b") != 0f)
        assertEquals(0f, s.lampSwing("a"))
        assertFalse(s.lampClicked())
    }

    @Test
    fun indoorLampsSwingAlone() {
        val s = SceneState()
        s.pushLamp("a", lit, cradle = false)
        s.time = half * 1.5f
        assertTrue(s.lampSwing("a") != 0f)
        assertEquals(0f, s.lampSwing("c"), "no cradle indoors")
        assertFalse(s.lampClicked())
    }
}
