package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EffectsStrengthTest {
    private fun runEffects(strength: EffectsStrength): SwimEffects {
        val fx = SwimEffects(Random(7))
        fx.applyStrength(strength)
        repeat(240) { fx.update(1f / 60, it.toFloat(), 200f, 0f, 90f, 1f) }
        return fx
    }

    @Test fun oldOrUnknownSavedSettingsDefaultToGentle() {
        assertEquals(EffectsStrength.GENTLE, EffectsStrength.fromId(null))
        assertEquals(EffectsStrength.GENTLE, EffectsStrength.fromId("unknown"))
        for (value in EffectsStrength.entries) assertEquals(value, EffectsStrength.fromId(value.id))
    }

    @Test fun offClearsExistingEffectsAndDoesNotEmitMore() {
        val fx = runEffects(EffectsStrength.GENTLE)
        val count = fx.emittedBubbles
        fx.applyStrength(EffectsStrength.OFF)
        repeat(120) { fx.update(1f / 60, 900f, 800f, 180f, 90f, 1f) }
        assertEquals(count, fx.emittedBubbles)
        assertFalse(fx.trails.any { it.age < SwimEffects.TRAIL_LIFE })
        assertFalse(fx.bubbles.any { it.age < SwimEffects.BUBBLE_LIFE })
        assertFalse(fx.ripples.any { it.age < SwimEffects.RIPPLE_LIFE })
        fx.applyStrength(EffectsStrength.GENTLE)
        repeat(60) { fx.update(1f / 60, 900f, 800f, 180f, 90f, 1f) }
        assertTrue(fx.emittedBubbles > count)
    }

    @Test fun prominentIncreasesDensityAndOpacityWithoutExpandingPools() {
        val gentle = runEffects(EffectsStrength.GENTLE)
        val prominent = runEffects(EffectsStrength.PROMINENT)
        assertTrue(prominent.emittedBubbles > gentle.emittedBubbles * 1.25)
        assertTrue(prominent.strength.opacity > gentle.strength.opacity)
        assertEquals(gentle.trails.size, prominent.trails.size)
        assertEquals(gentle.bubbles.size, prominent.bubbles.size)
        assertEquals(gentle.ripples.size, prominent.ripples.size)
    }

    @Test fun tailEmissionFollowsTheBentBody() {
        val fx = SwimEffects(Random(7))
        fx.update(0.04f, 100f, 200f, 90f, 90f, 1f, tailOffsetY = 12f)
        val point = fx.trails.first { it.age < SwimEffects.TRAIL_LIFE }
        assertEquals(88f, point.x, 0.001f)
        assertEquals(147f, point.y, 0.001f)
    }
}
