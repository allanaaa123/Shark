package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SharkAnimationTest {
    @Test fun turnFramesClampAndDoNotLoop() {
        val state = SharkAnimationState(0.0)
        for (motion in listOf(SharkMotion.TURN_LEFT, SharkMotion.TURN_RIGHT)) {
            assertEquals(0, state.frame(motion, -1f, 48, 2016))
            assertEquals(23, state.frame(motion, 0.5f, 48, 2016))
            assertEquals(47, state.frame(motion, 1f, 48, 2016))
            assertEquals(47, state.frame(motion, 2f, 48, 2016))
        }
    }

    @Test fun cadenceRespectsIndependentSpeedAndPauseClamp() {
        val slow = SharkAnimationState(0.0)
        val fast = SharkAnimationState(0.0)
        repeat(20) { slow.advance(0.04f, 20); fast.advance(0.04f, 200) }
        assertTrue(fast.swimTimeMs > slow.swimTimeMs * 3)
        val before = slow.swimTimeMs
        slow.advance(100f, 100)
        assertEquals(80.0, slow.swimTimeMs - before, 0.01)
        repeat(10000) { fast.advance(0.04f, 200) }
        assertTrue(fast.frame(SharkMotion.SWIM, 0f, 72, 3024) in 0..71)
    }

    @Test fun boundariesExposeCorrectTurnDirectionAndReturnToCruise() {
        // At the right boundary, the screen center is above/below the swimmer.
        for ((y, expected) in listOf(800f to SharkMotion.TURN_LEFT, 200f to SharkMotion.TURN_RIGHT)) {
            val nav = SharkNavigator(900f, y, 0f, Random(7))
            nav.update(0.04f, 1000f, 1000f, 100f, 50f)
            assertEquals(expected, nav.motion)
            var progress = nav.turnProgress
            var finished = false
            for (i in 0 until 500) {
                nav.update(0.04f, 1000f, 1000f, 100f, 50f)
                assertTrue(nav.x in 50f..950f && nav.y in 50f..950f)
                if (nav.motion == SharkMotion.SWIM) { finished = true; break }
                assertTrue(nav.turnProgress >= progress)
                progress = nav.turnProgress
            }
            assertTrue("turn must finish", finished)
        }
    }

    @Test fun tinyViewportAndExtremeConfigurationRemainFinite() {
        val nav = SharkNavigator(1f, 1f, 359f, Random(4))
        repeat(1000) { nav.update(0.08f, 20f, 10f, 2000f, 400f) }
        assertTrue(nav.x.isFinite() && nav.y.isFinite() && nav.angle.isFinite())
        assertTrue(nav.x in 9f..11f && nav.y in 4.5f..5.5f)
    }
}
