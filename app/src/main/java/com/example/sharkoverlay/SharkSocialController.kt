package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random

/** Soft steering from a shared, pre-movement snapshot. No positions are ever pushed apart. */
class SharkSocialController(private val random: Random = Random.Default) {
    enum class Action { IDLE, AVOID, PARALLEL, FOLLOW, CHASE, DRAFT, SCHOOL }
    private val capacity = SharkConfig.MAX_PETS
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val angle = FloatArray(capacity)
    private val hx = FloatArray(capacity)
    private val hy = FloatArray(capacity)
    private val length = FloatArray(capacity)
    private val speed = FloatArray(capacity)
    private val returning = BooleanArray(capacity)
    private val partner = IntArray(capacity) { -1 }
    private val actions = Array(capacity) { Action.IDLE }
    private val role = IntArray(capacity)
    private val side = FloatArray(capacity) { 1f }
    private val remaining = FloatArray(capacity)
    private val elapsed = FloatArray(capacity)
    private val cooldown = FloatArray(capacity)
    private val targetTurn = FloatArray(capacity)
    private val targetSpeed = FloatArray(capacity) { 1f }
    private val turns = FloatArray(capacity)
    private val speeds = FloatArray(capacity) { 1f }
    private var count = 0
    private var decisionClock = 0f
    var mode = SocialMode.NATURAL
        private set

    fun applyMode(value: SocialMode) {
        if (value == mode) return
        mode = value
        reset()
    }

    fun reset() {
        partner.fill(-1); actions.fill(Action.IDLE)
        remaining.fill(0f); elapsed.fill(0f)
        targetTurn.fill(0f); turns.fill(0f)
        targetSpeed.fill(1f); speeds.fill(1f)
        for (i in cooldown.indices) cooldown[i] = 0.3f + random.nextFloat() * 0.9f
        decisionClock = 0f
    }

    fun beginFrame(petCount: Int) {
        val next = petCount.coerceIn(0, capacity)
        if (next != count) { count = next; reset() }
    }

    fun capture(index: Int, px: Float, py: Float, heading: Float, bodyLength: Float,
                actualSpeed: Float, isReturning: Boolean) {
        require(index in 0 until count)
        x[index] = px; y[index] = py; angle[index] = heading
        val a = Math.toRadians(heading.toDouble())
        hx[index] = cos(a).toFloat(); hy[index] = sin(a).toFloat()
        length[index] = bodyLength.coerceAtLeast(1f)
        speed[index] = actualSpeed.coerceAtLeast(0f)
        returning[index] = isReturning
    }

    fun turnRate(index: Int): Float = turns[index]
    fun speedFactor(index: Int): Float = speeds[index]
    fun action(index: Int): Action = actions[index]
    fun partnerIndex(index: Int): Int = partner[index]

    fun update(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.08f)
        if (mode == SocialMode.OFF || dt <= 0f) return
        for (i in 0 until count) {
            cooldown[i] = max(0f, cooldown[i] - dt)
            remaining[i] -= dt
            elapsed[i] += dt
            if (partner[i] >= 0 && (returning[i] || remaining[i] <= 0f)) release(i)
        }
        decisionClock -= dt
        if (decisionClock <= 0f) {
            decisionClock = 0.10f
            decide()
        }
        // Perception is 10 Hz; outputs interpolate continuously at the display rate.
        for (i in 0 until count) {
            val turnChange = 85f * dt
            turns[i] += (targetTurn[i] - turns[i]).coerceIn(-turnChange, turnChange)
            speeds[i] += (targetSpeed[i] - speeds[i]).coerceIn(-0.20f * dt, 0.20f * dt)
        }
    }

    private fun release(i: Int) {
        val j = partner[i]
        val avoiding = actions[i] == Action.AVOID
        partner[i] = -1; actions[i] = Action.IDLE
        targetTurn[i] = 0f; targetSpeed[i] = 1f
        cooldown[i] = if (avoiding) 3f + random.nextFloat()*2f else 1.2f + random.nextFloat()*1.6f
        if (j in 0 until count && partner[j] == i) {
            partner[j] = -1; actions[j] = Action.IDLE
            targetTurn[j] = 0f; targetSpeed[j] = 1f
            cooldown[j] = if (avoiding) 3f + random.nextFloat()*2f else 1.2f + random.nextFloat()*1.6f
        }
    }

    private fun pair(i: Int, j: Int, action: Action, duration: Float) {
        partner[i] = j; partner[j] = i
        actions[i] = action; actions[j] = action
        role[i] = 0; role[j] = 1
        elapsed[i] = 0f; elapsed[j] = 0f
        remaining[i] = duration; remaining[j] = duration
        val lateral = hx[j] * (y[i] - y[j]) - hy[j] * (x[i] - x[j])
        side[i] = if (lateral >= 0f) 1f else -1f
        side[j] = -side[i]
        if (action == Action.AVOID) {
            // Keep a chosen side for the entire encounter, including exact head-on cases.
            for (k in 0..1) {
                val a = if (k == 0) i else j
                val b = if (k == 0) j else i
                val cross = hx[a] * (y[b] - y[a]) - hy[a] * (x[b] - x[a])
                side[a] = if (abs(cross) < 1f) 1f else if (cross > 0f) -1f else 1f
            }
        }
    }

    private fun decide() {
        targetTurn.fill(0f); targetSpeed.fill(1f)
        for (i in 0 until count) {
            val j = partner[i]
            if (j >= 0 && (returning[j] || hypot(x[j]-x[i], y[j]-y[i]) > (length[i]+length[j])*4f)) release(i)
        }
        // Only genuine path conflicts take priority. A large approaching fish has
        // an earlier notice range and may interrupt a social pair when necessary.
        for (i in 0 until count) {
            if (returning[i] || actions[i] == Action.AVOID) continue
            if (partner[i] < 0 && cooldown[i] > 0f) continue
            var threat = -1
            var score = 0f
            for (j in 0 until count) {
                if (i == j || returning[j] || actions[j] == Action.AVOID) continue
                if (partner[j] < 0 && cooldown[j] > 0f) continue
                val risk = conflictScore(i,j)
                if (risk <= score) continue
                val ratio = max(length[i],length[j])/min(length[i],length[j])
                // Ordinary near passes should not continually cancel a characteristic encounter.
                if ((partner[i] >= 0 || partner[j] >= 0) && ratio < 1.5f && risk < 100f) continue
                threat = j; score = risk
            }
            if (threat >= 0) {
                val j = threat
                if (partner[i] >= 0) release(i)
                if (partner[j] >= 0) release(j)
                val ratio = max(length[i],length[j])/min(length[i],length[j])
                pair(i,j,Action.AVOID,if(ratio>=1.5f) 1.2f+random.nextFloat()*0.3f
                    else 0.8f+random.nextFloat()*0.35f)
            }
        }
        // Other encounters are occasional and have one reciprocal partner each.
        for (i in 0 until count) {
            if (returning[i] || partner[i] >= 0 || cooldown[i] > 0f) continue
            var nearest = -1
            var best = Float.MAX_VALUE
            for (j in i+1 until count) {
                if (returning[j] || partner[j] >= 0 || cooldown[j] > 0f) continue
                val dist = hypot(x[j]-x[i], y[j]-y[i])
                val span = (length[i]+length[j])*0.5f
                val aligned = hx[i]*hx[j]+hy[i]*hy[j] > 0.55f
                val ahead = hx[i]*(x[j]-x[i])+hy[i]*(y[j]-y[i]) > 0f ||
                    hx[j]*(x[i]-x[j])+hy[j]*(y[i]-y[j]) > 0f
                if (dist in span*0.75f..span*3.0f && dist < best &&
                    (if (mode == SocialMode.PLAYFUL) ahead else aligned)) { nearest=j; best=dist }
            }
            val j = nearest
            if (j < 0 || random.nextFloat() > 0.38f) continue
            when (mode) {
                SocialMode.NATURAL -> pair(i,j,Action.PARALLEL,3.5f+random.nextFloat()*2.5f)
                SocialMode.PLAYFUL -> {
                    val action = if(random.nextBoolean()) Action.FOLLOW else Action.CHASE
                    val duration = if(action == Action.CHASE) 2.8f+random.nextFloat()*1.5f
                        else 3.5f+random.nextFloat()*2f
                    if(hx[i]*(x[j]-x[i])+hy[i]*(y[j]-y[i]) > 0f) pair(i,j,action,duration)
                    else pair(j,i,action,duration)
                }
                SocialMode.OCEAN -> {
                    if (min(length[i],length[j]) < max(length[i],length[j])*0.88f) {
                        if (length[i] < length[j]) pair(i,j,Action.DRAFT,4.5f+random.nextFloat()*2.5f)
                        else pair(j,i,Action.DRAFT,4.5f+random.nextFloat()*2.5f)
                    } else pair(i,j,Action.SCHOOL,4.5f+random.nextFloat()*2.5f)
                }
                SocialMode.OFF -> Unit
            }
        }
        for (i in 0 until count) {
            val j = partner[i]
            if (j < 0 || returning[i]) continue
            when (actions[i]) {
                Action.AVOID -> {
                    val ratio = length[j]/length[i]
                    val forward = hx[i]*(x[j]-x[i])+hy[i]*(y[j]-y[i]) > 0f
                    val rate = when {
                        ratio >= 2f -> 35f
                        ratio >= 1.5f -> 32f
                        ratio > 1.1f -> 27f
                        ratio <= 0.5f -> 6f
                        ratio <= 2f/3f -> 10f
                        ratio < 0.9f -> 16f
                        else -> 22f
                    }
                    targetTurn[i] = side[i]*rate
                    targetSpeed[i] = when {
                        forward && ratio >= 2f -> 0.85f
                        forward && ratio >= 1.5f -> 0.88f
                        forward && ratio > 0.9f -> 0.92f
                        else -> 1f
                    }
                }
                Action.PARALLEL -> {
                    val separation = (length[i]+length[j])*0.55f
                    val lookAhead = max(length[i],length[j])*2f
                    steerTo(i, (x[i]+x[j])*0.5f+hx[j]*lookAhead-hy[j]*side[i]*separation,
                        (y[i]+y[j])*0.5f+hy[j]*lookAhead+hx[j]*side[i]*separation, 0.55f)
                    matchSpeed(i,j,0.96f,1.04f)
                }
                Action.FOLLOW, Action.CHASE, Action.DRAFT -> {
                    if (role[i] == 0) {
                        val back = length[j] * if(actions[i] == Action.DRAFT) 0.95f else 0.75f
                        val offset = length[j]*0.28f*side[i]
                        steerTo(i, x[j]-hx[j]*back-hy[j]*offset,
                            y[j]-hy[j]*back+hx[j]*offset, 0.65f)
                        targetSpeed[i] = if(actions[i] == Action.CHASE) 1.14f else
                            if(hypot(x[j]-x[i],y[j]-y[i]) > length[j]*1.4f) 1.08f else 0.97f
                    } else if(actions[i] == Action.CHASE) {
                        targetTurn[i] = side[i]*10f
                        targetSpeed[i] = 1.08f
                    }
                }
                Action.SCHOOL -> school(i,j)
                Action.IDLE -> Unit
            }
            targetTurn[i] = targetTurn[i].coerceIn(-35f,35f)
            targetSpeed[i] = targetSpeed[i].coerceIn(0.85f,1.15f)
        }
    }

    private fun conflictScore(i: Int, j: Int): Float {
        val dx=x[j]-x[i]; val dy=y[j]-y[i]
        val distance=hypot(dx,dy)
        val span=length[i]+length[j]
        val ratio=max(length[i],length[j])/min(length[i],length[j])
        val large=ratio>=1.5f
        val notice=span*(if(ratio>=2f) 0.85f else if(large) 0.65f else 0.42f)
        if(distance > notice) return 0f
        // A very close overlap gets a short nudge, but separation is never enforced.
        if(distance < span*0.15f) return 130f+(ratio-1f).coerceAtMost(3f)*25f
        val rvx=hx[j]*speed[j]-hx[i]*speed[i]
        val rvy=hy[j]*speed[j]-hy[i]*speed[i]
        val dot=dx*rvx+dy*rvy
        val relativeSpeed2=rvx*rvx+rvy*rvy
        if(dot>=0f || relativeSpeed2<0.01f) return 0f
        val horizon=if(ratio>=2f) 1.5f else if(large) 1.1f else 0.65f
        val closestTime=(-dot/relativeSpeed2).coerceIn(0f,horizon)
        val clearance=span*(if(large) 0.28f else 0.22f)
        if(hypot(dx+rvx*closestTime,dy+rvy*closestTime) >= clearance) return 0f
        val urgent=closestTime <= 0.45f || distance < span*0.22f
        return (if(urgent) 105f else 35f) + (ratio-1f).coerceAtMost(3f)*30f +
            (1f-distance/notice)*15f
    }

    private fun steerTo(i: Int, px: Float, py: Float, weight: Float) {
        val desired = Math.toDegrees(atan2((py-y[i]).toDouble(),(px-x[i]).toDouble())).toFloat()
        val difference = ((desired-angle[i]+540f)%360f)-180f
        targetTurn[i] = (difference*weight).coerceIn(-30f,30f)
    }

    private fun matchSpeed(i: Int, j: Int, low: Float, high: Float) {
        targetSpeed[i] = (speed[j]/speed[i].coerceAtLeast(1f)).coerceIn(low,high)
    }

    private fun school(i: Int, j: Int) {
        // Small local groups align loosely; they do not share one global heading.
        var ax = hx[i]+hx[j]; var ay = hy[i]+hy[j]
        var seen = 0
        for (k in 0 until count) {
            if(k == i || k == j || returning[k]) continue
            if(hypot(x[k]-x[i],y[k]-y[i]) < length[i]*2.5f && hx[i]*hx[k]+hy[i]*hy[k] > 0.3f) {
                ax += hx[k]*0.5f; ay += hy[k]*0.5f
                seen++
                if(seen == 3) break
            }
        }
        val norm = hypot(ax,ay).coerceAtLeast(0.01f)
        ax /= norm; ay /= norm
        val gap = (length[i]+length[j])*0.5f
        steerTo(i,(x[i]+x[j])*0.5f+ax*length[i]*2f-ay*side[i]*gap,
            (y[i]+y[j])*0.5f+ay*length[i]*2f+ax*side[i]*gap,0.40f)
        matchSpeed(i,j,0.97f,1.04f)
    }
}

