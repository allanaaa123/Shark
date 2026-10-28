package com.example.sharkoverlay

enum class SharkMotion { SWIM, TURN_LEFT, TURN_RIGHT }

/** Pure timing logic, shared by the overlay and preview. */
class SharkAnimationState(initialTimeMs: Double = kotlin.random.Random.nextDouble(3_024.0)) {
    var swimTimeMs = initialTimeMs
        private set

    fun advance(dtSeconds: Float, speedPercent: Int, speedMultiplier: Float = 1f) {
        val cadence = (speedPercent / 100.0 * speedMultiplier).coerceIn(0.35, 2.4)
        // Keep one monotonic Double clock; each clip applies its own duration modulo.
        // No shared wrap can interrupt a species with a different loop duration.
        swimTimeMs += dtSeconds.coerceIn(0f, 0.08f) * 1000 * cadence
    }

    fun frame(motion: SharkMotion, progress: Float, frameCount: Int, durationMs: Int): Int {
        require(frameCount > 0 && durationMs > 0)
        return if (motion == SharkMotion.SWIM) {
            ((swimTimeMs % durationMs) / durationMs * frameCount).toInt().coerceIn(0, frameCount - 1)
        } else {
            (progress.coerceIn(0f, 1f) * (frameCount - 1)).toInt()
        }
    }
}
