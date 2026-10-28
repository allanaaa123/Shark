package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class SpeciesSwimMotionTest {
    private fun mesh(phase: Double, species: SharkSpecies) =
        FloatArray(HammerheadMotion.VERTEX_FLOATS).also {
            SpeciesSwimMotion.fill(it, 96f, phase, species)
        }

    @Test fun loopPositionAndVelocityAreContinuous() {
        for (species in SharkSpecies.entries.filter { it != SharkSpecies.HAMMERHEAD }) {
            val start = mesh(0.0, species)
            val end = mesh(2 * PI, species)
            val before = mesh(2 * PI - 0.001, species)
            val after = mesh(0.001, species)
            for (i in start.indices) {
                assertEquals(start[i], end[i], 0.0001f)
                assertEquals(start[i] - before[i], after[i] - start[i], 0.0001f)
            }
        }
    }

    @Test fun headStaysFixedAndMeshNeverFolds() {
        val stride = (SpeciesSwimMotion.COLUMNS + 1) * 2
        for (species in SharkSpecies.entries.filter { it != SharkSpecies.HAMMERHEAD }) {
            for (tick in 0..120) {
                val vertices = mesh(tick * 2 * PI / 120, species)
                assertTrue(vertices.all { it.isFinite() })
                for (row in 0..SpeciesSwimMotion.ROWS) {
                    val head = row * stride + SpeciesSwimMotion.COLUMNS * 2
                    assertEquals(96f, vertices[head], 0.0001f)
                    assertEquals(96f * (row * 2f / SpeciesSwimMotion.ROWS - 1f), vertices[head + 1], 0.0001f)
                    for (column in 0 until SpeciesSwimMotion.COLUMNS) {
                        val i = row * stride + column * 2
                        assertTrue(vertices[i + 2] > vertices[i])
                    }
                }
                for (row in 0 until SpeciesSwimMotion.ROWS) {
                    for (column in 0..SpeciesSwimMotion.COLUMNS) {
                        val i = row * stride + column * 2 + 1
                        assertTrue(vertices[i + stride] > vertices[i])
                    }
                }
            }
        }
    }
}

