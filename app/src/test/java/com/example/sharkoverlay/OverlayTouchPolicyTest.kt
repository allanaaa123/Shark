package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test

class OverlayTouchPolicyTest {
    @Test fun modernWindowStaysBelowTheSystemObscuringLimit() {
        for (limit in listOf(0.1f, 0.5f, 0.8f, 1f)) {
            val alpha = OverlayTouchPolicy.windowAlpha(31, limit)
            assertTrue(alpha >= 0f && alpha < limit)
            assertTrue(alpha <= 0.8f)
        }
        assertEquals(0.79f, OverlayTouchPolicy.windowAlpha(35, 0.8f), 0.0001f)
    }

    @Test fun strictLimitAndInvalidValuesNeverProduceOpaqueModernWindows() {
        assertEquals(0f, OverlayTouchPolicy.windowAlpha(35, 0f), 0f)
        assertEquals(0f, OverlayTouchPolicy.windowAlpha(35, -1f), 0f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0.79f, OverlayTouchPolicy.windowAlpha(35, invalid), 0.0001f)
        }
    }

    @Test fun legacyAndroidKeepsOriginalOpacity() {
        assertEquals(1f, OverlayTouchPolicy.windowAlpha(26), 0f)
        assertEquals(1f, OverlayTouchPolicy.windowAlpha(30), 0f)
    }
}
