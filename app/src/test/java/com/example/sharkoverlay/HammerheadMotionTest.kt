package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class HammerheadMotionTest {
    private fun offset(column: Int, row: Int) = (row * (HammerheadMotion.COLUMNS + 1) + column) * 2
    private fun pose(phase: Double, turn: Float) = FloatArray(HammerheadMotion.VERTEX_FLOATS).also {
        HammerheadMotion.fill(it, 96f, phase, turn)
    }

    @Test fun tailSweepsStronglyWhileHammerHeadRemainsStable() {
        val samples = (0 until 24).map { pose(it * PI / 12, 0f) }
        val tail = offset(1, 6)
        val head = offset(23, 6)
        // The travelling wave has phase lag at the tail. Measure its full cycle,
        // rather than assuming the tail extrema occur at whole-body quarter phases.
        val sweep = samples.maxOf { it[tail + 1] } - samples.minOf { it[tail + 1] }
        assertTrue("rear sweep should be visible", sweep > 30f)
        for (sample in samples) {
            assertEquals(samples[0][head], sample[head], 0.0001f)
            assertEquals(samples[0][head + 1], sample[head + 1], 0.0001f)
        }
        assertArrayEquals(samples[0], pose(2 * PI, 0f), 0.0001f)
    }

    @Test fun dorsalTipLeansWithTurnRelativeToItsBodyBase() {
        val neutral = pose(0.0, 0f)
        val left = pose(0.0, -1f)
        val right = pose(0.0, 1f)
        val tip = offset(13, 3) + 1
        val base = offset(13, 6) + 1
        val leftLean = (left[tip] - neutral[tip]) - (left[base] - neutral[base])
        val rightLean = (right[tip] - neutral[tip]) - (right[base] - neutral[base])
        assertTrue(leftLean < -3f)
        assertTrue(rightLean > 3f)
        assertEquals(-leftLean, rightLean, 0.001f)
    }

    @Test fun meshDoesNotFoldAtMaximumBendOrTailSweep() {
        for (turn in listOf(-1f, 0f, 1f)) for (step in 0 until 24) {
            val mesh = pose(step * PI / 12, turn)
            assertTrue(mesh.all { it.isFinite() })
            for (r in 0 until HammerheadMotion.ROWS) for (c in 0 until HammerheadMotion.COLUMNS) {
                val p = offset(c, r)
                val right = offset(c + 1, r)
                val down = offset(c, r + 1)
                val cross = (mesh[right] - mesh[p]) * (mesh[down + 1] - mesh[p + 1]) -
                    (mesh[right + 1] - mesh[p + 1]) * (mesh[down] - mesh[p])
                assertTrue("no inverted mesh triangles", cross > 0f)
            }
        }
    }

    private fun steadyRandom() = object : Random() {
        override fun nextBits(bitCount: Int): Int = ((1L shl bitCount) * 3 / 4).toInt()
    }

    @Test fun hammerheadTurnsQuicklyWithoutTheOtherSpeciesSlowdown() {
        val hammer = SharkNavigator(1100f, 800f, 0f, steadyRandom())
        val whale = SharkNavigator(1100f, 800f, 0f, steadyRandom())
        val initial = hammer.speedMultiplier
        hammer.update(0.04f, 1000f, 1000f, 150f, 50f, SharkSpecies.HAMMERHEAD)
        whale.update(0.04f, 1000f, 1000f, 150f, 50f, SharkSpecies.WHALE_SHARK)
        assertTrue(abs(hammer.angularVelocity) > 200f)
        assertEquals(100f, abs(whale.angularVelocity), 0.01f)
        assertTrue(hammer.speedMultiplier > initial)
        assertTrue(whale.speedMultiplier < initial)
    }
}
