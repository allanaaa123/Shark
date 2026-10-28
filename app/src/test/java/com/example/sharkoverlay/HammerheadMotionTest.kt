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
        val a = pose(PI / 2, 0f)
        val b = pose(3 * PI / 2, 0f)
        val tail = offset(1, 6)
        val head = offset(23, 6)
        assertTrue("rear sweep should be visible", abs(a[tail + 1] - b[tail + 1]) > 30f)
        assertEquals(a[head], b[head], 0.0001f)
        assertEquals(a[head + 1], b[head + 1], 0.0001f)
        val loop = pose(PI / 2 + 2 * PI, 0f)
        assertArrayEquals(a, loop, 0.0001f)
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
