package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class TurnPoseTest {
    @Test fun bodyRespondsBeforeTailAndBothEaseBackToStraight() {
        val pose = TurnPose()
        repeat(6) { pose.update(1f / 60, 100f) }
        assertTrue(pose.bodyBend > pose.tailBend)
        assertTrue(pose.tailBend > 0f)
        val before = pose.tailBend
        pose.update(1f / 60, 0f)
        assertTrue(pose.tailBend > before) // tail is still catching up
        repeat(120) { pose.update(1f / 60, 0f) }
        assertEquals(0f, pose.bodyBend, 0.001f)
        assertEquals(0f, pose.tailBend, 0.001f)
    }

    @Test fun leftAndRightAreSymmetricAndRefreshRateIndependent() {
        val slow = TurnPose()
        val fast = TurnPose()
        val left = TurnPose()
        repeat(30) { slow.update(1f / 30, 75f) }
        repeat(120) { fast.update(1f / 120, 75f); left.update(1f / 120, -75f) }
        assertEquals(slow.bodyBend, fast.bodyBend, 0.0001f)
        assertEquals(slow.tailBend, fast.tailBend, 0.0001f)
        assertEquals(-fast.bodyBend, left.bodyBend, 0.0001f)
        assertEquals(-fast.tailBend, left.tailBend, 0.0001f)
    }

    @Test fun bentSwimmingMeshKeepsHeadSteadyAndDoesNotFold() {
        val mesh = FloatArray(HammerheadMotion.VERTEX_FLOATS)
        val stride = (SpeciesSwimMotion.COLUMNS + 1) * 2
        for (species in SharkSpecies.entries.filter { it != SharkSpecies.HAMMERHEAD && !it.isRay }) {
            for (bend in listOf(-1f, 0f, 1f)) {
                for (tick in 0..24) {
                    SpeciesSwimMotion.fill(mesh, 96f, tick * 2 * PI / 24, species, bend, bend)
                    assertTrue(mesh.all { it.isFinite() })
                    for (row in 0..SpeciesSwimMotion.ROWS) {
                        val head = row * stride + SpeciesSwimMotion.COLUMNS * 2
                        assertEquals(96f, mesh[head], 0.0001f)
                        assertEquals(96f * (row * 2f / SpeciesSwimMotion.ROWS - 1f), mesh[head + 1], 0.0001f)
                    }
                    for (row in 0 until SpeciesSwimMotion.ROWS) {
                        for (column in 0..SpeciesSwimMotion.COLUMNS) {
                            val i = row * stride + column * 2
                            assertTrue(mesh[i + stride + 1] > mesh[i + 1])
                        }
                    }
                }
            }
        }
    }
}


