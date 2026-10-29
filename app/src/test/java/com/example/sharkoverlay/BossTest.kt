package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BossTest {
    @Test fun sharedSlotReplacesBossAtCapacityWithoutChangingOtherPets() {
        val model=PetListModel(List(49) { SharkConfig() })
        val ordinary=model.items.map { it.copy() }
        assertTrue(model.chooseBoss(SharkSpecies.DUNKLEOSTEUS))
        val id=model.boss!!.id
        for(species in SharkSpecies.bosses) {
            assertTrue(model.chooseBoss(species))
            assertEquals(50,model.items.size)
            assertEquals(1,model.items.count { it.species.isBoss })
            assertEquals(id,model.boss!!.id)
            assertEquals(ordinary,model.items.take(49))
            assertEquals(2.8f,model.boss!!.renderScale,.001f)
            assertEquals(.4f,model.boss!!.motionSpeed,.001f)
        }
        model.chooseBoss(null); model.add(listOf(SharkConfig()))
        assertFalse(model.chooseBoss(SharkSpecies.GIANT_SQUID))
        assertNull(model.boss)
    }
    @Test fun duplicateSavedBossesAndUndoCannotProduceTwoBosses() {
        val first=SharkConfig(150,200,SharkSpecies.DUNKLEOSTEUS)
        val ordinary=SharkConfig(85,77,SharkSpecies.ORCA)
        val model=PetListModel(listOf(first,ordinary,SharkConfig(species=SharkSpecies.GIANT_GROUPER)))
        assertEquals(2,model.items.size)
        assertEquals(first.id,model.boss!!.id)
        assertEquals(100,model.boss!!.size); assertEquals(100,model.boss!!.speed)
        val removed=model.remove(setOf(first.id,ordinary.id))
        model.chooseBoss(SharkSpecies.GIANT_SQUID)
        val before=model.items.map { it.copy() }
        assertFalse(model.canRestore(removed))
        assertEquals(0,model.restore(removed)); assertEquals(before,model.items)
        model.chooseBoss(null)
        assertEquals(2,model.restore(removed))
        assertEquals(listOf(first.id,ordinary.id),model.items.map { it.id })
        model.update(model.items.map { it.id }.toSet(),size=180,speed=180)
        assertEquals(100,model.boss!!.size); assertEquals(100,model.boss!!.speed)
        assertEquals(180,model.items[1].size)
    }
    @Test fun randomAndSpecifiedBatchesNeverAddBosses() {
        repeat(50) { seed ->
            assertTrue(RandomPetBatch.generate(50,0,Random(seed)).none { it.species.isBoss })
        }
        for(species in SharkSpecies.bosses) {
            try { RandomPetBatch.generate(1,0,species=species); fail("boss batch") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun manyFishYieldWithoutSocialPairingOrTurningTheBoss() {
        val a=BossAvoidance(); a.beginFrame(4)
        repeat(60) {
            a.capture(0,200f,200f,0f,110f,80f,false,false)
            a.capture(1,220f,300f,0f,110f,80f,false,false)
            a.capture(2,500f,250f,0f,308f,25f,true,false)
            a.capture(3,100f,1200f,0f,110f,80f,false,false)
            a.update(1f/60)
        }
        assertTrue(a.isYielding(0)); assertTrue(a.isYielding(1))
        assertTrue(abs(a.turnRate(0))>10f); assertTrue(a.speedFactor(0)<1f)
        assertEquals(0f,a.turnRate(2),0f); assertEquals(1f,a.speedFactor(2),0f)
        assertFalse(a.isYielding(3))
        repeat(120) {
            for(i in 0..3) a.capture(i,i*1000f,1000f,0f,110f,50f,false,false)
            a.update(1f/60)
        }
        assertTrue((0..3).none { a.isYielding(it) })
    }
    @Test fun allBossMeshesLoopWithoutFoldingAndKeepLeadingTipSteady() {
        val stride=(BossMotion.COLUMNS+1)*2
        for(species in SharkSpecies.bosses) {
            val first=FloatArray(BossMotion.VERTEX_FLOATS); val mesh=FloatArray(first.size)
            BossMotion.fill(first,96f,0.0,species,0f,0f)
            BossMotion.fill(mesh,96f,2*PI,species,0f,0f)
            assertArrayEquals(first,mesh,.0001f)
            var varied=false
            for(bend in listOf(-1f,0f,1f)) for(step in 0..24) {
                BossMotion.fill(mesh,96f,step*PI/12,species,bend,bend)
                assertTrue(mesh.all { it.isFinite() })
                if(mesh.indices.any { abs(mesh[it]-first[it])>.5f }) varied=true
                for(row in 0..BossMotion.ROWS) {
                    val head=row*stride+BossMotion.COLUMNS*2
                    assertEquals(96f,mesh[head],.001f)
                    assertEquals(96f*(row*2f/BossMotion.ROWS-1f),mesh[head+1],.001f)
                }
                for(row in 0 until BossMotion.ROWS) for(col in 0 until BossMotion.COLUMNS) {
                    val i=row*stride+col*2
                    val cross=(mesh[i+2]-mesh[i])*(mesh[i+stride+1]-mesh[i+1])-
                        (mesh[i+3]-mesh[i+1])*(mesh[i+stride]-mesh[i])
                    assertTrue("$species $bend $step $row $col",cross>0f)
                }
            }
            assertTrue(varied)
        }
    }
}
