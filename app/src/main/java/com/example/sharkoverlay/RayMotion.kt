package com.example.sharkoverlay

import kotlin.math.*

/** Forward mesh counterpart of the five approved GIF rigs. Pixel coordinates are 512px poses. */
object RayMotion {
    const val COLUMNS = 40
    const val ROWS = 28
    const val VERTEX_FLOATS = (COLUMNS + 1) * (ROWS + 1) * 2

    data class Profile(val pivotX: Float, val pivotY: Float, val span: Float,
                       val amplitude: Float, val lag: Float, val tail: Float, val bank: Float, val tipX: Float, val tipY: Float)

    private val profiles = mapOf(
        SharkSpecies.BLUE_SPOTTED_RAY to Profile(373.316798f, 256.124014f, 110.124014f, 0.105000f, 0.900000f, 0.040541f, 0.055000f, 43.000000f, 217.500000f),
        SharkSpecies.GOLDEN_RAY to Profile(374.800000f, 255.555556f, 122.222222f, 0.100000f, 0.850000f, 0.070000f, 0.055000f, 41.000000f, 203.000000f),
        SharkSpecies.EAGLE_RAY to Profile(388.000000f, 255.981258f, 158.447122f, 0.140000f, 0.300000f, 0.025000f, 0.070000f, 41.000000f, 211.000000f),
        SharkSpecies.SILVER_RAY to Profile(374.800000f, 255.957393f, 120.751880f, 0.075000f, 1.050000f, 0.050000f, 0.050000f, 41.000000f, 214.000000f),
        SharkSpecies.MANTA_RAY to Profile(339.600000f, 256.044444f, 174.696296f, 0.135000f, 0.250000f, 0.015000f, 0.075000f, 42.000000f, 253.000000f)
    )

    private class Rig(val profile: Profile) {
        val x = FloatArray(COLUMNS + 1) { it * 512f / COLUMNS - profile.pivotX }
        val y = FloatArray(ROWS + 1) { it * 512f / ROWS - profile.pivotY }
        val fin = FloatArray(COLUMNS + 1)
        val tailWeight = FloatArray(COLUMNS + 1)
        val lagCos = FloatArray(COLUMNS + 1)
        val lagSin = FloatArray(COLUMNS + 1)
        val tailCos = FloatArray(COLUMNS + 1)
        val tailSin = FloatArray(COLUMNS + 1)
        val edge = FloatArray(ROWS + 1) {
            ((abs(y[it]) / profile.span - 0.22f) / 0.78f).coerceIn(0f, 1f).pow(1.3f)
        }
        init {
            val root = profile.pivotX - profile.span * 0.65f
            for (i in 0..COLUMNS) {
                val px = x[i] + profile.pivotX
                val head = ((x[i] / profile.span - .1f) / .45f).coerceIn(0f,1f)
                fin[i] = exp(-((x[i]/profile.span+.15f).pow(4))) * (1f-head*head*(3f-2f*head))
                val rear = ((root-px)/root.coerceAtLeast(1f)).coerceIn(0f,1f)
                tailWeight[i] = rear*rear
                val lag = (.6f-x[i]/profile.span)*profile.lag
                lagCos[i] = cos(lag); lagSin[i] = sin(lag)
                tailCos[i] = cos(rear*1.7f); tailSin[i] = sin(rear*1.7f)
            }
        }
    }
    private val rigs by lazy { profiles.mapValues { Rig(it.value) } }

    fun profile(species: SharkSpecies): Profile = profiles.getValue(species)

    /** scratch has one sin/cos pair per column, owned by each renderer. */
    fun fill(vertices: FloatArray, scratch: FloatArray, bodyLength: Float, phase: Double,
             species: SharkSpecies, bodyBend: Float, tailBend: Float) {
        require(vertices.size == VERTEX_FLOATS)
        require(scratch.size == (COLUMNS+1)*2)
        val rig = rigs.getValue(species)
        val p = rig.profile
        val scale = bodyLength*1.35f/440f
        val bend = (bodyBend*.55f+tailBend*.45f).coerceIn(-1f,1f)
        val sign = if (bend < 0f) -1f else 1f
        val radius = p.span*2.5f/abs(bend).coerceAtLeast(.0001f)
        val bank = bend*.8f
        val waveS = sin(phase).toFloat(); val waveC = cos(phase).toFloat()
        val shiftS = sin(bank*.3f); val shiftC = cos(bank*.3f)
        for (column in 0..COLUMNS) {
            val arc = if(rig.x[column] < 0f && abs(bend)>.0001f) -rig.x[column]/radius else 0f
            scratch[column*2] = sin(arc)
            scratch[column*2+1] = cos(arc)
        }
        var offset = 0
        for (row in 0..ROWS) {
            val side = if(rig.y[row] >= 0f) 1f else -1f
            val ws = waveS*shiftC+waveC*shiftS*side
            val wc = waveC*shiftC-waveS*shiftS*side
            for (column in 0..COLUMNS) {
                val wave = ws*rig.lagCos[column]-wc*rig.lagSin[column]
                val contraction = (p.amplitude*(1f+wave)+bank*side*p.bank).coerceIn(0f,.30f)*rig.fin[column]
                var py = rig.y[row]*(1f-contraction*rig.edge[row])
                py += p.tail*p.span*rig.tailWeight[column]*(waveS*rig.tailCos[column]-waveC*rig.tailSin[column])
                var px = rig.x[column]
                if(px < 0f && abs(bend)>.0001f) {
                    val s = scratch[column*2]; val c = scratch[column*2+1]
                    px = -radius*s+sign*py*s
                    py = sign*radius*(1f-c)+py*c
                }
                vertices[offset++] = px*scale
                vertices[offset++] = py*scale
            }
        }
    }

    fun tailPosition(out: FloatArray, bodyLength: Float, phase: Double, species: SharkSpecies,
                     bodyBend: Float, tailBend: Float) {
        val p=profile(species)
        val root=p.pivotX-p.span*.65f
        val rear=((root-p.tipX)/root.coerceAtLeast(1f)).coerceIn(0f,1f)
        var px=p.tipX-p.pivotX
        var py=p.tipY-p.pivotY+p.tail*p.span*rear*rear*sin(phase-rear*1.7).toFloat()
        val bend=(bodyBend*.55f+tailBend*.45f).coerceIn(-1f,1f)
        if(abs(bend)>.0001f && px<0f) {
            val sign=if(bend<0f) -1f else 1f
            val radius=p.span*2.5f/abs(bend)
            val arc=-px/radius
            px=-radius*sin(arc)+sign*py*sin(arc)
            py=sign*radius*(1f-cos(arc))+py*cos(arc)
        }
        val scale=bodyLength*1.35f/440f
        out[0]=px*scale; out[1]=py*scale
    }
}
