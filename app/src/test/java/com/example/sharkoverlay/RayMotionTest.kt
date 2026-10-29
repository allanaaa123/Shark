package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class RayMotionTest {
    private fun mesh(species: SharkSpecies, phase: Double, bend: Float): FloatArray {
        val out=FloatArray(RayMotion.VERTEX_FLOATS)
        RayMotion.fill(out,FloatArray((RayMotion.COLUMNS+1)*2),110f,phase,species,bend,bend)
        return out
    }

    @Test fun allFiveApprovedRaysHaveProfilesAndContinuousCycles() {
        val rays=SharkSpecies.entries.filter { it.isRay }
        assertEquals(5,rays.size)
        for(ray in rays) {
            assertTrue(RayMotion.profile(ray).span>100f)
            assertArrayEquals(mesh(ray,0.0,0f),mesh(ray,2*PI,0f),0.0001f)
            assertTrue(mesh(ray,PI/2,0f).zip(mesh(ray,PI*1.5,0f)).any { (a,b) -> kotlin.math.abs(a-b)>.5f })
        }
    }

    @Test fun leftAndRightBankWithoutMeshFolding() {
        val stride=(RayMotion.COLUMNS+1)*2
        for(ray in SharkSpecies.entries.filter { it.isRay }) for(bend in listOf(-1f,0f,1f)) {
            for(step in 0 until 12) {
                val out=mesh(ray,step*PI/6,bend)
                assertTrue(out.all { it.isFinite() })
                for(row in 0 until RayMotion.ROWS) for(col in 0 until RayMotion.COLUMNS) {
                    val i=row*stride+col*2; val right=i+2; val down=i+stride
                    val cross=(out[right]-out[i])*(out[down+1]-out[i+1])-
                        (out[right+1]-out[i+1])*(out[down]-out[i])
                    assertTrue("no folding: $ray $bend $step $row $col ($cross)",cross>0f)
                }
            }
        }
    }
}
