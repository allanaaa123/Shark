package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class SharkStartleTest {
    @Test fun burstAcceleratesSmoothlyWithoutTeleportingThenRecovers() {
        val nav=SharkNavigator(5000f,5000f,0f,Random(7))
        repeat(30) { nav.update(1f/60,10000f,10000f,150f,50f) }
        val beforeSpeed=nav.speedMultiplier
        val x=nav.x; val y=nav.y; val angle=nav.angle
        nav.startle()
        assertTrue(nav.isStartled)
        assertEquals(x,nav.x,0f); assertEquals(y,nav.y,0f); assertEquals(angle,nav.angle,0f)
        var peak=beforeSpeed
        repeat(210) {
            val previousX=nav.x; val previousY=nav.y; val previousSpeed=nav.speedMultiplier
            nav.update(1f/60,10000f,10000f,150f,50f)
            peak=max(peak,nav.speedMultiplier)
            assertTrue(hypot(nav.x-previousX,nav.y-previousY)<8f)
            assertTrue(abs(nav.speedMultiplier-previousSpeed)<0.15f)
        }
        assertTrue(peak>beforeSpeed*1.3f)
        assertFalse(nav.isStartled)
        assertTrue(nav.speedMultiplier < peak)
        assertTrue(nav.speedMultiplier <= 1.451f)
    }

    @Test fun socialSteeringDoesNotSuppressTheBurst() {
        val a=SharkNavigator(5000f,5000f,0f,Random(7))
        val b=SharkNavigator(5000f,5000f,0f,Random(7))
        a.startle(); b.startle()
        repeat(120) {
            a.update(1f/60,10000f,10000f,150f,50f)
            b.update(1f/60,10000f,10000f,150f,50f,socialTurnRate=35f,socialSpeedFactor=0.85f)
            assertEquals(a.x,b.x,0f); assertEquals(a.y,b.y,0f)
            assertEquals(a.speedMultiplier,b.speedMultiplier,0f)
        }
    }

    @Test fun turningOffCanEaseOutOrClearImmediatelyWithoutMovingFish() {
        val nav=SharkNavigator(5000f,5000f,0f,Random(7))
        nav.startle()
        repeat(30) { nav.update(1f/60,10000f,10000f,150f,50f) }
        nav.clearStartle()
        repeat(90) { nav.update(1f/60,10000f,10000f,150f,50f) }
        assertFalse(nav.isStartled)
        nav.startle()
        nav.update(1f/60,10000f,10000f,150f,50f)
        val x=nav.x; val y=nav.y
        nav.clearStartle(immediate=true)
        assertFalse(nav.isStartled)
        assertEquals(x,nav.x,0f); assertEquals(y,nav.y,0f)
    }

    @Test fun fiftyStartledPetsStayFiniteAndCanReturnFromOffscreen() {
        val navs=List(50) { SharkNavigator(1200f,800f,it*7f,Random(it)) }
        val recovered=BooleanArray(50)
        navs.forEach { it.startle() }
        repeat(1500) {
            navs.forEachIndexed { i,nav ->
                nav.update(1f/60,1080f,1920f,150f,50f,SharkSpecies.entries[i%SharkSpecies.entries.size])
                assertTrue(nav.x.isFinite() && nav.y.isFinite() && nav.angle.isFinite())
                if(nav.x in 25f..1055f && nav.y in 25f..1895f) recovered[i]=true
            }
        }
        assertTrue(recovered.all { it })
        assertTrue(navs.all { !it.isStartled })
    }
}
