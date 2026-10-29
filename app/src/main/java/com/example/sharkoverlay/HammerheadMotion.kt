package com.example.sharkoverlay

import kotlin.math.*

/** Local body deformation; independent from world heading and position. */
object HammerheadMotion {
    const val COLUMNS = 24
    const val ROWS = 12
    const val VERTEX_FLOATS = (COLUMNS + 1) * (ROWS + 1) * 2

    private val xs = FloatArray(COLUMNS + 1) { it * 2f / COLUMNS - 1f }
    private val ys = FloatArray(ROWS + 1) { it * 2f / ROWS - 1f }
    private val rear = FloatArray(COLUMNS + 1) { ((0.35f - xs[it]) / 1.35f).coerceIn(0f, 1f) }
    private val tailWeight = FloatArray(COLUMNS + 1) { 0.23f * rear[it].pow(2.2f) }
    private val curve = FloatArray(COLUMNS + 1) { 0.14f * rear[it].pow(1.5f) }
    private val waveCos = FloatArray(COLUMNS + 1) { cos(rear[it] * 0.8f) }
    private val waveSin = FloatArray(COLUMNS + 1) { sin(rear[it] * 0.8f) }
    private val finX = FloatArray(COLUMNS + 1) { (1f - abs(xs[it] - 0.10f) / 0.45f).coerceIn(0f, 1f) }
    private val finTip = FloatArray(ROWS + 1) { ((-ys[it] - 0.08f) / 0.65f).coerceIn(0f, 1f) }
    private val finY = FloatArray(ROWS + 1) { finTip[it].pow(1.3f) }

    fun fill(vertices: FloatArray, half: Float, phase: Double, turnBend: Float) {
        require(vertices.size == VERTEX_FLOATS)
        val bend = turnBend.coerceIn(-1f, 1f)
        val waveS = sin(phase).toFloat()
        val waveC = cos(phase).toFloat()
        var index = 0
        for (row in 0..ROWS) {
            for (column in 0..COLUMNS) {
                // Head steady, increasingly strong motion toward the tail.
                val tail = (waveS * waveCos[column] - waveC * waveSin[column]) * tailWeight[column]
                val backbone = -bend * curve[column]
                // Dorsal tip leans relative to its root, with continuous body curvature.
                val finLean = bend * 0.14f * finX[column] * finY[row]
                vertices[index++] = half * (xs[column] + bend * 0.06f * finX[column] * finTip[row])
                vertices[index++] = half * (ys[row] + tail + backbone + finLean)
            }
        }
    }
}
