package com.example.sharkoverlay

import kotlin.math.*

/** Continuous deformation shared by all non-hammerhead species. */
object SpeciesSwimMotion {
    const val COLUMNS = HammerheadMotion.COLUMNS
    const val ROWS = HammerheadMotion.ROWS
    private val xs = FloatArray(COLUMNS + 1) { it * 2f / COLUMNS - 1f }
    private val ys = FloatArray(ROWS + 1) { it * 2f / ROWS - 1f }
    private val rear = FloatArray(COLUMNS + 1) { ((0.45f - xs[it]) / 1.45f).coerceIn(0f, 1f) }
    private val rear2 = FloatArray(COLUMNS + 1) { rear[it] * rear[it] }
    private val rear3 = FloatArray(COLUMNS + 1) { rear2[it] * rear[it] }
    private val waveCos = FloatArray(COLUMNS + 1) { cos(rear[it] * 1.4).toFloat() }
    private val waveSin = FloatArray(COLUMNS + 1) { sin(rear[it] * 1.4).toFloat() }

    fun tailY(half: Float, phase: Double, species: SharkSpecies, bodyBend: Float, tailBend: Float): Float {
        val r = 1.30f / 1.45f
        val wave = if (species != SharkSpecies.ORCA)
            species.tailAmplitude * r * r * sin(phase - r * 1.4).toFloat() else 0f
        return half * (wave - 0.11f * bodyBend * r * r - 0.16f * tailBend * r * r * r)
    }

    fun fill(vertices: FloatArray, half: Float, phase: Double, species: SharkSpecies,
             bodyBend: Float = 0f, tailBend: Float = 0f) {
        require(vertices.size == HammerheadMotion.VERTEX_FLOATS)
        require(species != SharkSpecies.HAMMERHEAD)
        // Two trig evaluations per fish, instead of repeating sin for every vertex.
        val waveS = sin(phase).toFloat()
        val waveC = cos(phase).toFloat()
        val body = bodyBend.coerceIn(-1f, 1f)
        val tail = tailBend.coerceIn(-1f, 1f)
        var index = 0
        for (row in 0..ROWS) {
            for (column in 0..COLUMNS) {
                val weight = rear2[column]
                val wave = waveS * waveCos[column] - waveC * waveSin[column]
                val localY = if (species != SharkSpecies.ORCA) {
                    ys[row] + species.tailAmplitude * weight * wave
                } else {
                    ys[row] * (1f + species.tailAmplitude * weight * wave)
                }
                val curve = -0.11f * body * weight - 0.16f * tail * rear3[column]
                vertices[index++] = half * xs[column]
                vertices[index++] = half * (localY + curve)
            }
        }
    }
}
