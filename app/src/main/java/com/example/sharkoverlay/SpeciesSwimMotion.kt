package com.example.sharkoverlay

import kotlin.math.*

/** Continuous swim deformation of one clean pose; no optical-flow frames or clip seam. */
object SpeciesSwimMotion {
    fun tailY(half: Float, phase: Double, species: SharkSpecies, bodyBend: Float, tailBend: Float): Float {
        val r = 1.30f / 1.45f // normalized tail anchor x = -0.85
        val wave = if (species == SharkSpecies.WHALE_SHARK)
            0.14f * r * r * sin(phase - r * 1.4).toFloat() else 0f
        return half * (wave - 0.11f * bodyBend * r * r - 0.16f * tailBend * r * r * r)
    }

    // Match the renderer's shared mesh buffer and Android drawBitmapMesh topology.
    const val COLUMNS = HammerheadMotion.COLUMNS
    const val ROWS = HammerheadMotion.ROWS
    private val rear = FloatArray(COLUMNS + 1) { column ->
        val x = column * 2f / COLUMNS - 1f
        ((0.45f - x) / 1.45f).coerceIn(0f, 1f)
    }

    fun fill(vertices: FloatArray, half: Float, phase: Double, species: SharkSpecies,
             bodyBend: Float = 0f, tailBend: Float = 0f) {
        require(vertices.size == HammerheadMotion.VERTEX_FLOATS)
        require(species == SharkSpecies.WHALE_SHARK || species == SharkSpecies.ORCA)
        var index = 0
        for (row in 0..ROWS) {
            val y = row * 2f / ROWS - 1f
            for (column in 0..COLUMNS) {
                val x = column * 2f / COLUMNS - 1f
                val r = rear[column]
                val weight = r * r
                val wave = sin(phase - r * 1.4).toFloat()
                // Sharks sweep the tail laterally. Orcas beat flukes vertically;
                // from above this reads as gentle foreshortening, with a steady head.
                val localY = if (species == SharkSpecies.WHALE_SHARK) {
                    y + 0.14f * weight * wave
                } else {
                    y * (1f + 0.16f * weight * wave)
                }
                vertices[index++] = half * x
                // A broad torso bend leads a more delayed rear-body response.
                val curve = -0.11f * bodyBend.coerceIn(-1f, 1f) * r * r -
                    0.16f * tailBend.coerceIn(-1f, 1f) * r * r * r
                vertices[index++] = half * (localY + curve)
            }
        }
    }
}

