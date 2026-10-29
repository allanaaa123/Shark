package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class SharkSocialControllerTest {
    private class EncounterRandom(private val follow: Boolean = false) : Random() {
        override fun nextBits(bitCount: Int): Int = if (bitCount == 1 && follow) 1 else 0
    }

    private fun encounter(mode: SocialMode, secondLength: Float = 100f, follow: Boolean = false): SharkSocialController {
        val social = SharkSocialController(EncounterRandom(follow))
        social.applyMode(mode)
        social.beginFrame(2)
        repeat(45) {
            social.capture(0,200f,200f,0f,100f,60f,false)
            social.capture(1,380f,200f,0f,secondLength,60f,false)
            social.update(1f/60)
        }
        return social
    }

    private class QuarterRandom : Random() {
        override fun nextBits(bitCount: Int): Int = if(bitCount >= 2) 1 shl (bitCount-2) else 0
    }

    @Test fun characteristicEncountersAreMoreLikelyAndLastLonger() {
        val social=SharkSocialController(QuarterRandom())
        social.beginFrame(2)
        repeat(200) {
            social.capture(0,200f,200f,0f,100f,60f,false)
            social.capture(1,380f,200f,0f,100f,60f,false)
            social.update(1f/60)
        }
        // A .25 trigger draw was rejected by the old .12 probability.
        // This interaction also outlasts the old 2–3.5 second parallel window.
        assertEquals(SharkSocialController.Action.PARALLEL,social.action(0))
    }

    @Test fun socialCooldownIsShorterThanAvoidanceCooldown() {
        val social=SharkSocialController(EncounterRandom())
        social.beginFrame(2)
        var seen=false
        var endedAt=-1
        var restartedAt=-1
        repeat(480) { tick ->
            social.capture(0,200f,200f,0f,100f,60f,false)
            social.capture(1,380f,200f,0f,100f,60f,false)
            social.update(1f/60)
            if(social.action(0)==SharkSocialController.Action.PARALLEL) {
                if(endedAt>=0 && restartedAt<0) restartedAt=tick
                seen=true
            } else if(seen && endedAt<0) endedAt=tick
        }
        assertTrue(endedAt>0 && restartedAt>endedAt)
        assertTrue((restartedAt-endedAt)/60f < 1.8f)
    }

    @Test fun largeApproachingFishAreNoticedEarlierButSafePassesCanStillDraft() {
        fun run(secondLength: Float, secondY: Float, heading: Float): SharkSocialController {
            val social=SharkSocialController(EncounterRandom())
            social.applyMode(SocialMode.OCEAN); social.beginFrame(2)
            repeat(45) {
                social.capture(0,200f,200f,0f,100f,100f,false)
                social.capture(1,400f,secondY,heading,secondLength,100f,false)
                social.update(1f/60)
            }
            return social
        }
        assertEquals(SharkSocialController.Action.IDLE,run(100f,200f,180f).action(0))
        val big=run(200f,200f,180f)
        assertEquals(SharkSocialController.Action.AVOID,big.action(0))
        assertTrue(big.turnRate(0) > big.turnRate(1))
        assertTrue(big.speedFactor(0) < big.speedFactor(1))
        assertEquals(SharkSocialController.Action.DRAFT,run(200f,320f,0f).action(0))
    }

    @Test fun urgentBigFishCanInterruptAnExistingSocialPair() {
        val social=SharkSocialController(EncounterRandom())
        social.beginFrame(3)
        repeat(45) {
            social.capture(0,200f,200f,0f,100f,100f,false)
            social.capture(1,380f,200f,0f,100f,100f,false)
            social.capture(2,2000f,200f,180f,200f,100f,false)
            social.update(1f/60)
        }
        assertEquals(SharkSocialController.Action.PARALLEL,social.action(0))
        repeat(10) {
            social.capture(0,200f,200f,0f,100f,100f,false)
            social.capture(1,380f,200f,0f,100f,100f,false)
            social.capture(2,280f,200f,180f,200f,100f,false)
            social.update(1f/60)
        }
        assertEquals(SharkSocialController.Action.AVOID,social.action(0))
        assertEquals(2,social.partnerIndex(0))
        assertEquals(0,social.partnerIndex(2))
        assertEquals(-1,social.partnerIndex(1))
    }

    @Test fun modesHaveDifferentEncountersAndStableSavedIds() {
        for(mode in SocialMode.entries) assertEquals(mode,SocialMode.fromId(mode.id))
        assertEquals(SocialMode.NATURAL,SocialMode.fromId(null))
        assertEquals(SocialMode.NATURAL,SocialMode.fromId("future"))
        assertEquals(SharkSocialController.Action.PARALLEL,encounter(SocialMode.NATURAL).action(0))
        assertEquals(SharkSocialController.Action.FOLLOW,encounter(SocialMode.PLAYFUL,follow=true).action(0))
        assertEquals(SharkSocialController.Action.CHASE,encounter(SocialMode.PLAYFUL).action(0))
        assertEquals(SharkSocialController.Action.SCHOOL,encounter(SocialMode.OCEAN).action(0))
        val drafting = encounter(SocialMode.OCEAN,secondLength=150f)
        assertEquals(SharkSocialController.Action.DRAFT,drafting.action(0))
        assertEquals(1,drafting.partnerIndex(0))
    }

    @Test fun headOnAvoidanceKeepsItsSideAndSmallFishYieldMore() {
        val social = SharkSocialController(EncounterRandom())
        social.beginFrame(2)
        var previous = 0f
        repeat(55) {
            social.capture(0,200f,200f,0f,70f,70f,false)
            social.capture(1,280f,200f,180f,140f,70f,false)
            social.update(1f/60)
            val rate=social.turnRate(0)
            assertTrue(abs(rate-previous) <= 85f/60+0.0001f)
            assertTrue(rate >= 0f) // exact head-on: both keep the chosen right turn
            previous=rate
        }
        assertEquals(SharkSocialController.Action.AVOID,social.action(0))
        assertTrue(abs(social.turnRate(0)) > abs(social.turnRate(1)))
        assertTrue(social.speedFactor(0) < social.speedFactor(1))
    }

    @Test fun pairHasOneReciprocalPartnerAndARealCooldown() {
        val social = SharkSocialController(EncounterRandom())
        social.beginFrame(3)
        repeat(160) {
            for(i in 0..2) social.capture(i,200f+i,200f,0f,100f,60f,false)
            social.update(1f/60)
            for(i in 0..2) {
                val j=social.partnerIndex(i)
                if(j>=0) { assertNotEquals(i,j); assertEquals(i,social.partnerIndex(j)) }
            }
        }
        assertEquals(SharkSocialController.Action.IDLE,social.action(0))
        assertEquals(-1,social.partnerIndex(0))
    }

    @Test fun offAndModeChangesReleasePairsWithoutChangingPositions() {
        val social=encounter(SocialMode.PLAYFUL)
        assertTrue(social.partnerIndex(0)>=0)
        social.applyMode(SocialMode.OFF)
        repeat(60) {
            social.capture(0,200f,200f,0f,100f,60f,false)
            social.capture(1,201f,200f,180f,100f,60f,false)
            social.update(1f/60)
        }
        for(i in 0..1) {
            assertEquals(0f,social.turnRate(i),0f)
            assertEquals(1f,social.speedFactor(i),0f)
            assertEquals(-1,social.partnerIndex(i))
        }
        social.applyMode(SocialMode.OCEAN)
        assertEquals(0f,social.turnRate(0),0f)
        assertEquals(-1,social.partnerIndex(0))
    }

    @Test fun snapshotCaptureOrderDoesNotChangeDecisions() {
        val a=SharkSocialController(EncounterRandom())
        val b=SharkSocialController(EncounterRandom())
        a.beginFrame(2); b.beginFrame(2)
        repeat(60) {
            a.capture(0,200f,200f,0f,100f,60f,false)
            a.capture(1,280f,200f,180f,100f,60f,false)
            b.capture(1,280f,200f,180f,100f,60f,false)
            b.capture(0,200f,200f,0f,100f,60f,false)
            a.update(1f/60); b.update(1f/60)
            for(i in 0..1) assertEquals(a.turnRate(i),b.turnRate(i),0f)
        }
    }

    @Test fun defaultNavigationAndReturnRecoveryRemainUnchanged() {
        val normal=SharkNavigator(500f,500f,0f,Random(7))
        val off=SharkNavigator(500f,500f,0f,Random(7))
        repeat(120) {
            normal.update(1f/60,1080f,1920f,150f,50f)
            off.update(1f/60,1080f,1920f,150f,50f,socialTurnRate=0f,socialSpeedFactor=1f)
            assertEquals(normal.x,off.x,0f); assertEquals(normal.y,off.y,0f)
        }
        val returning=SharkNavigator(1200f,800f,0f,Random(7))
        val influenced=SharkNavigator(1200f,800f,0f,Random(7))
        returning.update(0.04f,1080f,1920f,150f,50f)
        influenced.update(0.04f,1080f,1920f,150f,50f,socialTurnRate=35f,socialSpeedFactor=1.15f)
        assertTrue(returning.isReturning)
        assertEquals(returning.x,influenced.x,0f)
        assertEquals(returning.y,influenced.y,0f)
        assertEquals(returning.angle,influenced.angle,0f)
    }

    @Test fun allModesWithFiftyMixedPetsStayBoundedAndNeverTeleport() {
        for(mode in SocialMode.entries) {
            val social=SharkSocialController(Random(7))
            social.applyMode(mode); social.beginFrame(50)
            val navs=List(50) { SharkNavigator(200f+(it%10)*65,300f+(it/10)*85,it*31f,Random(it)) }
            repeat(480) {
                for(i in navs.indices) {
                    val n=navs[i]
                    social.capture(i,n.x,n.y,n.angle,75f+(i%3)*25f,150f*n.speedMultiplier,n.isReturning)
                }
                social.update(1f/60)
                for(i in navs.indices) {
                    val n=navs[i]; val x=n.x; val y=n.y
                    assertTrue(abs(social.turnRate(i))<=35.001f)
                    assertTrue(social.speedFactor(i) in 0.849f..1.151f)
                    val j=social.partnerIndex(i)
                    if(j>=0) assertEquals(i,social.partnerIndex(j))
                    n.update(1f/60,1080f,1920f,150f,50f,SharkSpecies.entries[i%SharkSpecies.entries.size],
                        social.turnRate(i),social.speedFactor(i))
                    assertTrue(n.x.isFinite() && n.y.isFinite() && n.angle.isFinite())
                    assertTrue(hypot(n.x-x,n.y-y)<5.5f)
                }
            }
        }
    }
}

