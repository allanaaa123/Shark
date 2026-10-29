package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random

/** World-space effect state. Fixed pools bound memory even with ten pets. */
class SwimEffects(private val random: Random = Random.Default) {
    class Trail {
        var x = 0f; var y = 0f
        var nx = 0f; var ny = 1f
        var scale = 1f
        var age = 2f
    }
    class Bubble {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var radius = 1f; var scale = 1f
        var age = 2f
    }
    class Ripple {
        var x = 0f; var y = 0f
        var angle = 0f; var scale = 1f
        var age = 2f
    }
    var strength = EffectsStrength.GENTLE
        private set

    fun applyStrength(value: EffectsStrength) {
        if (strength == value) return
        strength = value
        if (value == EffectsStrength.OFF) clear()
    }

    val trails = Array(40) { Trail() }
    val bubbles = Array(32) { Bubble() }
    val ripples = Array(8) { Ripple() }
    var nextTrail = 0
        private set
    private var nextBubble = 0
    private var nextRipple = 0
    private var trailClock = 0f
    private var bubbleClock = 0f
    private var rippleClock = 0f
    private var initialized = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastAngle = 0f
    private var lastTailOffset = 0f
    private var lastTailOffsetX = 0f
    var emittedBubbles = 0L
        private set

    fun clear() {
        for (p in trails) p.age = 2f
        for (p in bubbles) p.age = 2f
        for (p in ripples) p.age = 2f
        trailClock = 0f; bubbleClock = 0f; rippleClock = 0f
        initialized = false
    }

    fun update(dtSeconds: Float, x: Float, y: Float, angle: Float,
               angularVelocity: Float, bodyScale: Float, emitting: Boolean = true, tailOffsetY: Float = 0f, detailFactor: Float = 1f,
               tailOffsetX: Float = -53f*bodyScale) {
        val dt = dtSeconds.coerceIn(0f, 0.08f)
        if (dt <= 0f || strength == EffectsStrength.OFF) return
        val scale = bodyScale.coerceAtLeast(0.01f)
        val detail = detailFactor.coerceIn(0.4f, 1f)
        val trailStep = TRAIL_STEP / detail
        for (p in trails) p.age = (p.age + dt).coerceAtMost(2f)
        for (p in bubbles) {
            p.age = (p.age + dt).coerceAtMost(2f)
            if (p.age < BUBBLE_LIFE) {
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }
        for (p in ripples) p.age = (p.age + dt).coerceAtMost(2f)
        if (!initialized) {
            lastX = x; lastY = y; lastAngle = angle
            lastTailOffset = tailOffsetY
            lastTailOffsetX = tailOffsetX
            initialized = true
        }
        val radians = Math.toRadians(angle.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val tailX = x + tailOffsetX*c - s * tailOffsetY
        val tailY = y + tailOffsetX*s + c * tailOffsetY
        if (emitting) {
            // Sample the moving tail at a fixed rate, independently of display FPS.
            trailClock += dt
            while (trailClock >= trailStep) {
                trailClock -= trailStep
                val f = (1f - trailClock / dt).coerceIn(0f, 1f)
                val deltaAngle = ((angle - lastAngle + 540f) % 360f) - 180f
                val a = Math.toRadians((lastAngle + deltaAngle * f).toDouble())
                val tc = cos(a).toFloat(); val ts = sin(a).toFloat()
                val p = trails[nextTrail]
                val offset = lastTailOffset + (tailOffsetY - lastTailOffset) * f
                val offsetX = lastTailOffsetX + (tailOffsetX-lastTailOffsetX)*f
                p.x = lastX + (x - lastX) * f + offsetX * tc - ts * offset
                p.y = lastY + (y - lastY) * f + offsetX * ts + tc * offset
                p.nx = -ts; p.ny = tc; p.scale = scale; p.age = trailClock
                nextTrail = (nextTrail + 1) % trails.size
            }
            val turning = abs(angularVelocity) >= 12f
            // Turn bursts are deliberately much richer than the gentle straight swim.
            val bubbleRate = if (turning) 12f + 4f * (abs(angularVelocity) / 180f).coerceAtMost(1f) else 1.8f
            bubbleClock += dt * bubbleRate * strength.density * detail
            while (bubbleClock >= 1f) {
                bubbleClock -= 1f
                val p = bubbles[nextBubble]
                val spread = (random.nextFloat() - 0.5f) * 12f * scale
                p.x = tailX - s * spread; p.y = tailY + c * spread
                p.vx = (-c * 5f + (random.nextFloat() - 0.5f) * 6f) * scale
                p.vy = (-s * 5f - 9f - random.nextFloat() * 5f) * scale
                p.radius = (1.1f + random.nextFloat() * 1.3f) * scale
                p.scale = scale; p.age = 0f
                nextBubble = (nextBubble + 1) % bubbles.size
                emittedBubbles++
            }
            if (turning) {
                rippleClock += dt * strength.density * detail
                if (rippleClock >= 0.24f) {
                    rippleClock %= 0.24f
                    val p = ripples[nextRipple]
                    // Place the arc on the outside of the turn, near the rear body.
                    val side = if (angularVelocity > 0f) -1f else 1f
                    p.x = x - c * 22f * scale - s * side * 13f * scale
                    p.y = y - s * 22f * scale + c * side * 13f * scale
                    p.angle = angle + if (side > 0f) 40f else 220f
                    p.scale = scale; p.age = 0f
                    nextRipple = (nextRipple + 1) % ripples.size
                }
            } else rippleClock = 0f
        }
        lastX = x; lastY = y; lastAngle = angle
        lastTailOffset = tailOffsetY
        lastTailOffsetX = tailOffsetX
    }

    companion object {
        fun detailFor(petCount: Int): Float = sqrt(10f / petCount.coerceAtLeast(1)).coerceIn(0.4f, 1f)

        const val TRAIL_LIFE = 1.05f
        const val BUBBLE_LIFE = 1.35f
        const val RIPPLE_LIFE = 0.85f
        private const val TRAIL_STEP = 1f / 30f
    }
}



