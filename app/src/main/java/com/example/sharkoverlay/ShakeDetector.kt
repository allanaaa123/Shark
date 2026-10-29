package com.example.sharkoverlay

import kotlin.math.sqrt

/** Two separate acceleration peaks, a release between them, and a four-second cooldown. */
class ShakeDetector {
    private var pending = Long.MIN_VALUE
    private var triggered = Long.MIN_VALUE
    private var lastTime = Long.MIN_VALUE
    private var armed = true

    fun reset() { pending = Long.MIN_VALUE; armed = true }

    fun sample(timeNanos: Long, x: Float, y: Float, z: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) { reset(); return false }
        if (lastTime != Long.MIN_VALUE && timeNanos < lastTime) {
            reset(); triggered = Long.MIN_VALUE
        }
        lastTime = timeNanos
        val g = sqrt(x.toDouble()*x+y.toDouble()*y+z.toDouble()*z) / 9.80665
        if (g < 1.25) armed = true
        if (pending != Long.MIN_VALUE && timeNanos-pending > 450_000_000L) pending = Long.MIN_VALUE
        if (triggered != Long.MIN_VALUE && timeNanos-triggered < COOLDOWN_NANOS) {
            pending = Long.MIN_VALUE
            if (g >= 1.8) armed = false
            return false
        }
        if (!armed || g < 1.8) return false
        armed = false
        if (pending != Long.MIN_VALUE && timeNanos-pending in 80_000_000L..450_000_000L) {
            triggered = timeNanos; pending = Long.MIN_VALUE
            return true
        }
        if (pending == Long.MIN_VALUE || timeNanos-pending > 450_000_000L) pending = timeNanos
        return false
    }

    companion object { const val COOLDOWN_NANOS = 4_000_000_000L }
}
