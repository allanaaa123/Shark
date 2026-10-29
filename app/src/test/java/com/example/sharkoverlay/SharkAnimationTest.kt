package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
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

    @Test fun cadenceRespectsActualSpeedAndPauseClamp() {
        val slow = SharkAnimationState(0.0)
        val fast = SharkAnimationState(0.0)
        repeat(20) { slow.advance(0.04f, 100, 0.7f); fast.advance(0.04f, 100, 1.4f) }
        assertEquals(slow.swimTimeMs * 2, fast.swimTimeMs, 0.01)
        val before = slow.swimTimeMs
        slow.advance(100f, 100)
        assertEquals(80.0, slow.swimTimeMs - before, 0.01)
        repeat(10000) { fast.advance(0.04f, 200) }
        assertTrue(fast.frame(SharkMotion.SWIM, 0f, 72, 3024) in 0..71)
    }

    @Test fun fishCanLeaveViewportAndReturnWithoutTeleporting() {
        val nav = SharkNavigator(990f, 700f, 0f, Random(7))
        var left = false
        var returned = false
        repeat(2000) {
            val beforeX = nav.x
            val beforeY = nav.y
            nav.update(0.04f, 1000f, 1000f, 150f, 50f)
            val distance = hypot(nav.x - beforeX, nav.y - beforeY)
            assertTrue("no position clamp or teleport", distance < 10f)
            if (nav.x > 1000f || nav.x < 0f || nav.y < 0f || nav.y > 1000f) left = true
            if (left && nav.x in 25f..975f && nav.y in 25f..975f) returned = true
        }
        assertTrue("must allow an excursion", left)
        assertTrue("must return to visible water", returned)
    }

    @Test fun returnTurnsHaveCorrectSignAndRecoverFromEverySide() {
        for ((x, y, heading, expected) in listOf(
            listOf(1100f, 800f, 0f, -1f),
            listOf(1100f, 200f, 0f, 1f),
            listOf(-100f, 500f, 180f, 0f),
            listOf(500f, -100f, 270f, 0f),
            listOf(500f, 1100f, 90f, 0f)
        )) {
            val nav = SharkNavigator(x, y, heading, Random(7))
            nav.update(0.04f, 1000f, 1000f, 150f, 50f)
            assertTrue(nav.isReturning)
            if (expected != 0f) assertEquals(if (expected < 0) SharkMotion.TURN_LEFT else SharkMotion.TURN_RIGHT, nav.motion)
            var recovered = false
            repeat(1500) {
                nav.update(0.04f, 1000f, 1000f, 150f, 50f)
                if (!nav.isReturning && nav.x in 25f..975f && nav.y in 25f..975f) recovered = true
            }
            assertTrue("return from $x,$y", recovered)
        }
    }

    @Test fun speedVariesSmoothlyAndIndependently() {
        val a = SharkNavigator(5000f, 5000f, 0f, Random(4))
        val b = SharkNavigator(5000f, 5000f, 0f, Random(9))
        var minimum = Float.MAX_VALUE
        var maximum = 0f
        var different = false
        repeat(3000) {
            val before = a.speedMultiplier
            a.update(0.04f, 10000f, 10000f, 100f, 50f)
            b.update(0.04f, 10000f, 10000f, 100f, 50f)
            assertTrue(abs(a.speedMultiplier - before) <= 0.00881f)
            minimum = min(minimum, a.speedMultiplier)
            maximum = max(maximum, a.speedMultiplier)
            if (abs(a.speedMultiplier - b.speedMultiplier) > 0.1f) different = true
        }
        assertTrue(maximum - minimum > 0.3f)
        assertTrue(different)
    }

    @Test fun tinyViewportAndExtremeSpeedCanStillReturn() {
        val nav = SharkNavigator(-30f, -30f, 359f, Random(4))
        var recovered = false
        repeat(3000) {
            nav.update(0.08f, 20f, 10f, 2000f, 400f)
            assertTrue(nav.x.isFinite() && nav.y.isFinite() && nav.angle.isFinite())
            if (nav.x in 0f..20f && nav.y in 0f..10f) recovered = true
        }
        assertTrue(recovered)
    }
}
