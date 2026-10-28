package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test

class ShakeDetectorTest {
    private fun sample(detector: ShakeDetector, ms: Long, g: Float): Boolean =
        detector.sample(ms*1_000_000L,0f,0f,g*9.80665f)

    @Test fun normalMovementAndOneContinuousPeakDoNotTrigger() {
        val d=ShakeDetector()
        repeat(100) { assertFalse(sample(d,it*60L,if(it%2==0) 1f else 1.4f)) }
        repeat(20) { assertFalse(sample(d,6000L+it*60,2.3f)) }
    }

    @Test fun twoDistinctPeaksTriggerOnceAndRespectFourSecondCooldown() {
        val d=ShakeDetector()
        assertFalse(sample(d,0,2.2f))
        assertFalse(sample(d,60,1f))
        assertTrue(sample(d,150,2.2f))
        assertFalse(sample(d,200,1f))
        assertFalse(sample(d,400,2.2f))
        assertFalse(sample(d,460,1f))
        assertFalse(sample(d,600,2.2f))
        assertFalse(sample(d,4150,1f))
        assertFalse(sample(d,4200,2.2f))
        assertFalse(sample(d,4260,1f))
        assertTrue(sample(d,4350,2.2f))
    }

    @Test fun stalePeaksResetAndStopStartDoesNotBypassCooldown() {
        val d=ShakeDetector()
        assertFalse(sample(d,0,2.2f))
        assertFalse(sample(d,60,1f))
        assertFalse(sample(d,700,2.2f)) // too late to combine with the first
        d.reset()
        assertFalse(sample(d,800,2.2f))
        assertFalse(sample(d,860,1f))
        assertTrue(sample(d,950,2.2f))
        d.reset()
        assertFalse(sample(d,1000,2.2f))
        assertFalse(sample(d,1060,1f))
        assertFalse(sample(d,1150,2.2f))
    }

    @Test fun invalidSamplesCannotCompleteAGesture() {
        val d=ShakeDetector()
        assertFalse(sample(d,0,2.2f))
        assertFalse(d.sample(60_000_000L,Float.NaN,0f,0f))
        assertFalse(sample(d,150,2.2f))
    }
}
