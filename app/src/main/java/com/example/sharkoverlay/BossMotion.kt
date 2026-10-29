package com.example.sharkoverlay

import kotlin.math.*

/** Armored head stability, gentle reef fins, and delayed motion in the squid's trailing arms. */
object BossMotion {
    const val COLUMNS=40
    const val ROWS=20
    const val VERTEX_FLOATS=(COLUMNS+1)*(ROWS+1)*2
    private fun vertex(out: FloatArray, index: Int, x: Float, y: Float, half: Float,
                       phase: Double, species: SharkSpecies, body: Float, tail: Float) {
        val squid=species==SharkSpecies.GIANT_SQUID
        val root=if(squid) .08f else .35f
        val rear=((root-x)/(root+1f)).coerceIn(0f,1f)
        val wave=sin(phase-rear*(if(squid) 2.6 else 1.5)).toFloat()
        val edge=(abs(y)/.65f).coerceIn(0f,1f)
        var py=y+species.tailAmplitude*rear*rear*wave
        if(squid) {
            py+=.045f*rear*edge*sin(phase-rear*3.0+(if(y<0f) .6 else -.6)).toFloat()
        } else {
            val fin=(1f-abs(x-.15f)/.4f).coerceIn(0f,1f)
            py*=1f+.035f*fin*edge*sin(phase+.4).toFloat()
        }
        py-=.10f*body.coerceIn(-1f,1f)*rear*rear+.15f*tail.coerceIn(-1f,1f)*rear*rear*rear
        out[index]=half*x
        out[index+1]=half*py
    }
    fun fill(out: FloatArray, half: Float, phase: Double, species: SharkSpecies,
             bodyBend: Float, tailBend: Float) {
        require(species.isBoss && out.size==VERTEX_FLOATS)
        var index=0
        for(row in 0..ROWS) for(column in 0..COLUMNS) {
            vertex(out,index,column*2f/COLUMNS-1f,row*2f/ROWS-1f,half,phase,species,bodyBend,tailBend)
            index+=2
        }
    }
    fun emission(out: FloatArray, half: Float, phase: Double, species: SharkSpecies,
                 bodyBend: Float, tailBend: Float) {
        vertex(out,0,if(species==SharkSpecies.GIANT_SQUID) .08f else -.85f,
            0f,half,phase,species,bodyBend,tailBend)
    }
}
