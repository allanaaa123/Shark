package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random

/** Pixel-based navigation, including excursions beyond the visible viewport. */
class SharkNavigator(
    var x: Float,
    var y: Float,
    var angle: Float,
    private val random: Random = Random.Default
) {
    private enum class Mode { CRUISE, TURN }
    private var mode = Mode.CRUISE
    private var turnRemaining = 0f
    private var turnSign = 1f
    private var turnTotal = 0f
    private var progress = 0f
    private var cruiseSeconds = 2f + random.nextFloat() * 3f
    private var returning = false
    private var targetX = 0f
    private var targetY = 0f
    private var speedTarget = 1f
    private var speedSeconds = 0f

    private var startleRemaining = 0f
    private var startleTurnRate = 0f
    private var startleBoost = 1f
    val isStartled: Boolean get() = startleRemaining > 0f || startleBoost > 1.001f

    fun startle() {
        startleRemaining = 2.2f
        startleTurnRate = (45f + random.nextFloat()*40f) * if(random.nextBoolean()) 1f else -1f
        // A new burst scatters headings gradually; do not teleport or override a return.
        if (!returning) { mode = Mode.CRUISE; cruiseSeconds = 3f }
    }

    fun clearStartle(immediate: Boolean = false) {
        startleRemaining = 0f
        startleTurnRate = 0f
        if (immediate) { startleBoost = 1f; speedMultiplier = baseSpeedMultiplier }
    }

    /** The user's per-shark speed remains the baseline; this adds smooth variation. */
    private var baseSpeedMultiplier = 0.85f + random.nextFloat() * 0.3f
    var speedMultiplier = baseSpeedMultiplier
        private set
    var angularVelocity = 0f
        private set
    val isReturning: Boolean get() = returning
    val motion: SharkMotion
        get() = if (mode == Mode.CRUISE) SharkMotion.SWIM
        else if (turnSign < 0f) SharkMotion.TURN_LEFT else SharkMotion.TURN_RIGHT
    val turnProgress: Float get() = progress
    var phase = random.nextFloat() * (2f * PI.toFloat())
        private set

    fun update(dtSeconds: Float, width: Float, height: Float, speed: Float, radius: Float, species: SharkSpecies = SharkSpecies.HAMMERHEAD,
               socialTurnRate: Float = 0f, socialSpeedFactor: Float = 1f) {
        if (width <= 0f || height <= 0f) return
        val dt = dtSeconds.coerceIn(0f, 0.08f)
        angularVelocity = 0f
        startleRemaining = max(0f,startleRemaining-dt)
        val desiredBoost = 1f + 0.75f*(startleRemaining/1.3f).coerceIn(0f,1f)
        val boostChange = (if(desiredBoost > startleBoost) 3.5f else 0.9f)*dt
        startleBoost += (desiredBoost-startleBoost).coerceIn(-boostChange,boostChange)
        val agile = species == SharkSpecies.HAMMERHEAD
        val r = radius.coerceAtLeast(1f)
        // The center may leave the screen; turning never clamps/teleports position.
        val margin = max(r * 1.2f, min(width, height) * 0.08f)
            .coerceAtMost(min(width, height) * 0.35f)
        val outside = x < -margin || x > width + margin || y < -margin || y > height + margin
        if (outside && !returning) {
            returning = true
            targetX = width * (0.35f + random.nextFloat() * 0.3f)
            targetY = height * (0.35f + random.nextFloat() * 0.3f)
            beginTurn(signedAngle(bearing(targetX, targetY) - angle))
        }
        val insetX = min(r * 0.5f, width * 0.2f)
        val insetY = min(r * 0.5f, height * 0.2f)
        if (returning && x in insetX..(width - insetX) && y in insetY..(height - insetY)) {
            returning = false
            mode = Mode.CRUISE
            cruiseSeconds = 2f + random.nextFloat() * 3f
        }

        speedSeconds -= dt
        if (speedSeconds <= 0f) {
            // Long gentle glides interspersed with faster swimming, independent per fish.
            speedTarget = 0.65f + random.nextFloat() * 0.8f
            speedSeconds = 2f + random.nextFloat() * 4f
        }
        val desiredSpeed = speedTarget * (if (mode == Mode.TURN && !agile) 0.85f else 1f) *
            (if (returning || isStartled) 1f else socialSpeedFactor.coerceIn(0.85f, 1.15f))
        val maxChange = 0.22f * dt
        baseSpeedMultiplier += (desiredSpeed-baseSpeedMultiplier).coerceIn(-maxChange,maxChange)
        speedMultiplier = baseSpeedMultiplier*startleBoost
        val v = (speed.coerceAtLeast(1f) * speedMultiplier).coerceAtMost(min(width, height) * 1.5f)

        if (returning) {
            // Re-evaluate bearing every tick, so wide arcs cannot keep a fish offscreen.
            // Recompute the target after a viewport resize as well.
            targetX = targetX.coerceIn(width * 0.25f, width * 0.75f)
            targetY = targetY.coerceIn(height * 0.25f, height * 0.75f)
            val difference = signedAngle(bearing(targetX, targetY) - angle)
            val rate = if (agile) 220f else 100f // Preserve momentum, tighten hammerhead return arcs.
            val step = difference.coerceIn(-rate * dt, rate * dt)
            angularVelocity = if (dt > 0f) step / dt else 0f
            if (abs(difference) > 3f) {
                mode = Mode.TURN
                turnSign = if (difference < 0f) -1f else 1f
                progress = max(progress, 1f - abs(difference) / turnTotal.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                mode = Mode.CRUISE
            }
            angle = (angle + step + 360f) % 360f
        } else {
            if (mode == Mode.CRUISE) {
                cruiseSeconds -= dt
                if (cruiseSeconds <= 0f) {
                    val delta = (15f + random.nextFloat() * 30f) * if (random.nextBoolean()) 1f else -1f
                    beginTurn(delta)
                }
            }
            if (mode == Mode.TURN) {
                val turnRadius = if (agile) max(r * 0.65f, v * 0.26f) else max(r * 1.4f, v * 0.9f)
                val rate = Math.toDegrees((v / turnRadius).toDouble()).toFloat().let {
                    if (agile) it.coerceIn(90f, 240f) else it.coerceIn(25f, 110f)
                }
                val step = min(turnRemaining, rate * dt)
                angularVelocity = if (dt > 0f) turnSign * step / dt else 0f
                angle = (angle + turnSign * step + 360f) % 360f
                turnRemaining -= step
                progress = (1f - turnRemaining / turnTotal).coerceIn(0f, 1f)
                if (turnRemaining <= 0.01f) {
                    mode = Mode.CRUISE
                    cruiseSeconds = 2f + random.nextFloat() * 3f
                }
            }
        }
        if (!returning && startleRemaining > 0f) {
            val rate = startleTurnRate*(startleRemaining/2.2f).pow(2)
            angle = (angle+rate*dt+360f)%360f
            angularVelocity += rate
        }
        if (!returning && !isStartled && socialTurnRate != 0f) {
            val rate = socialTurnRate.coerceIn(-35f, 35f)
            angle = (angle + rate * dt + 360f) % 360f
            angularVelocity += rate
        }
        val heading = Math.toRadians(angle.toDouble())
        x += cos(heading).toFloat() * v * dt
        y += sin(heading).toFloat() * v * dt
        phase += dt * (4f + v / 25f)
    }

    private fun beginTurn(degrees: Float) {
        turnSign = if (degrees < 0f) -1f else 1f
        turnRemaining = abs(degrees).coerceAtLeast(0.01f)
        turnTotal = turnRemaining
        progress = 0f
        mode = Mode.TURN
    }
    private fun bearing(tx: Float, ty: Float): Float = Math.toDegrees(atan2((ty - y).toDouble(), (tx - x).toDouble())).toFloat()
    private fun signedAngle(degrees: Float): Float = ((degrees % 360f) + 540f) % 360f - 180f
}


