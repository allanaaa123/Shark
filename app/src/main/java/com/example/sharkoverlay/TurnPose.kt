package com.example.sharkoverlay

import kotlin.math.exp

/** Body curvature responds first; the rear body catches up and eases back after a turn. */
class TurnPose {
    var bodyBend = 0f
        private set
    var tailBend = 0f
        private set

    fun update(dtSeconds: Float, angularVelocity: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.08f)
        if (dt <= 0f) return
        val target = (angularVelocity / 100f).coerceIn(-1f, 1f)
        // Exact solution for two cascaded first-order responses during this tick.
        // Unlike Euler integration, this is independent of refresh rate.
        val bodyDecay = exp(-dt / BODY_TIME)
        val tailDecay = exp(-dt / TAIL_TIME)
        val previousBody = bodyBend
        tailBend = target + (tailBend - target) * tailDecay +
            (previousBody - target) * BODY_TIME / (BODY_TIME - TAIL_TIME) * (bodyDecay - tailDecay)
        bodyBend = target + (previousBody - target) * bodyDecay
    }

    companion object {
        private const val BODY_TIME = 0.10f
        private const val TAIL_TIME = 0.18f
    }
}
