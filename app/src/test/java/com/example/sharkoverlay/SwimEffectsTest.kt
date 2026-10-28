package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SwimEffectsTest {
    private fun simulate(turnRate: Float, fps: Int, seconds: Int): SwimEffects {
        val effects = SwimEffects(Random(42))
        for (i in 0 until fps * seconds) {
            effects.update(1f / fps, i * 60f / fps, 200f,
                0f, turnRate, 1f)
        }
        return effects
    }

    @Test fun turnsEmitMuchMoreBubblesThanStraightSwimming() {
        val straight = simulate(0f, 60, 4)
        val turning = simulate(90f, 60, 4)
        assertTrue(straight.emittedBubbles in 6L..8L)
        assertTrue(turning.emittedBubbles > straight.emittedBubbles * 5)
        assertTrue(turning.ripples.any { it.age < SwimEffects.RIPPLE_LIFE })
        assertFalse(straight.ripples.any { it.age < SwimEffects.RIPPLE_LIFE })
    }

    @Test fun emissionDoesNotDependOnScreenRefreshRate() {
        for (turn in listOf(0f, 90f)) {
            val slow = simulate(turn, 30, 4)
            val fast = simulate(turn, 120, 4)
            assertEquals(slow.emittedBubbles.toDouble(), fast.emittedBubbles.toDouble(), 1.0)
            val slowTrail = slow.trails.count { it.age < SwimEffects.TRAIL_LIFE }
            val fastTrail = fast.trails.count { it.age < SwimEffects.TRAIL_LIFE }
            assertEquals(slowTrail.toDouble(), fastTrail.toDouble(), 1.0)
        }
    }

    @Test fun emittedEffectsStayInWorldSpaceAndEventuallyDisappear() {
        val effects = simulate(90f, 60, 1)
        val trail = effects.trails.first { it.age < SwimEffects.TRAIL_LIFE }
        val originalX = trail.x
        val originalY = trail.y
        effects.update(0.05f, 900f, 800f, 180f, 0f, 1f, emitting = false)
        assertEquals(originalX, trail.x, 0f)
        assertEquals(originalY, trail.y, 0f)
        repeat(120) { effects.update(1f / 60, 900f, 800f, 180f, 0f, 1f, emitting = false) }
        assertFalse(effects.trails.any { it.age < SwimEffects.TRAIL_LIFE })
        assertFalse(effects.bubbles.any { it.age < SwimEffects.BUBBLE_LIFE })
        assertFalse(effects.ripples.any { it.age < SwimEffects.RIPPLE_LIFE })
    }

    @Test fun poolsStayBoundedAndClearRemovesAllEffects() {
        val effects = simulate(180f, 120, 30)
        assertEquals(40, effects.trails.size)
        assertEquals(32, effects.bubbles.size)
        assertEquals(8, effects.ripples.size)
        assertTrue(effects.bubbles.all { it.x.isFinite() && it.y.isFinite() })
        effects.clear()
        assertFalse(effects.trails.any { it.age < SwimEffects.TRAIL_LIFE })
        assertFalse(effects.bubbles.any { it.age < SwimEffects.BUBBLE_LIFE })
        assertFalse(effects.ripples.any { it.age < SwimEffects.RIPPLE_LIFE })
    }
}
