package com.example.sharkoverlay

import kotlin.math.*

/** Many fish can yield to one boss, independently of optional social pairs and modes. */
class BossAvoidance {
    private val capacity=SharkConfig.MAX_PETS
    private val x=FloatArray(capacity); private val y=FloatArray(capacity)
    private val heading=FloatArray(capacity); private val length=FloatArray(capacity)
    private val speed=FloatArray(capacity); private val boss=BooleanArray(capacity)
    private val blocked=BooleanArray(capacity); private val active=BooleanArray(capacity)
    private val sides=FloatArray(capacity)
    private val turns=FloatArray(capacity); private val speeds=FloatArray(capacity) { 1f }
    private var count=0
    fun reset() { active.fill(false); sides.fill(0f); turns.fill(0f); speeds.fill(1f) }
    fun beginFrame(size: Int) {
        val next=size.coerceIn(0,capacity)
        if(next!=count) { count=next; reset() }
    }
    fun capture(i: Int, px: Float, py: Float, angle: Float, bodyLength: Float,
                actualSpeed: Float, isBoss: Boolean, unavailable: Boolean) {
        require(i in 0 until count)
        x[i]=px; y[i]=py; heading[i]=angle; length[i]=bodyLength.coerceAtLeast(1f)
        speed[i]=actualSpeed.coerceAtLeast(0f); boss[i]=isBoss; blocked[i]=unavailable
    }
    fun isYielding(i: Int): Boolean = active[i] || abs(turns[i])>.1f || abs(speeds[i]-1f)>.001f
    fun turnRate(i: Int): Float = turns[i]
    fun speedFactor(i: Int): Float = speeds[i]
    fun update(seconds: Float) {
        val dt=seconds.coerceIn(0f,.08f)
        val leader=(0 until count).firstOrNull { boss[it] }
        for(i in 0 until count) {
            var targetTurn=0f; var targetSpeed=1f
            if(leader!=null && i!=leader && !boss[i] && !blocked[i]) {
                val b=leader
                val a=Math.toRadians(heading[i].toDouble())
                val ba=Math.toRadians(heading[b].toDouble())
                val dx=x[b]-x[i]; val dy=y[b]-y[i]
                val distance=hypot(dx,dy)
                val vx=cos(a).toFloat()*speed[i]-cos(ba).toFloat()*speed[b]
                val vy=sin(a).toFloat()*speed[i]-sin(ba).toFloat()*speed[b]
                val clearance=length[b]*.62f+length[i]*.45f
                val notice=clearance+max(length[b]*.45f,speed[i]*2f)
                val closing=dx*vx+dy*vy>0f
                val t=((dx*vx+dy*vy)/(vx*vx+vy*vy).coerceAtLeast(1f)).coerceIn(0f,2.4f)
                val predicted=hypot(dx-vx*t,dy-vy*t)
                val risk=distance<clearance || (closing && predicted<clearance && distance<notice)
                if(risk || (active[i] && distance<clearance*1.15f)) {
                    if(!active[i]) {
                        val cross=cos(a).toFloat()*dy-sin(a).toFloat()*dx
                        sides[i]=if(abs(cross)<1f) if(i%2==0) 1f else -1f else if(cross>0) -1f else 1f
                    }
                    active[i]=true
                    val d=distance.coerceAtLeast(1f)
                    val awayX=if(distance<1f) -sin(a).toFloat()*sides[i] else -dx/d
                    val awayY=if(distance<1f) cos(a).toFloat()*sides[i] else -dy/d
                    val tx=awayX-dy/d*sides[i]*.8f
                    val ty=awayY+dx/d*sides[i]*.8f
                    val desired=Math.toDegrees(atan2(ty,tx).toDouble()).toFloat()
                    var difference=((desired-heading[i])+540f)%360f-180f
                    if(abs(difference)>170f) difference=abs(difference)*sides[i]
                    targetTurn=(difference*1.4f).coerceIn(-35f,35f)
                    if(closing) targetSpeed=.85f
                } else active[i]=false
            } else active[i]=false
            val change=85f*dt
            turns[i]+=(targetTurn-turns[i]).coerceIn(-change,change)
            speeds[i]+=(targetSpeed-speeds[i]).coerceIn(-.3f*dt,.3f*dt)
        }
    }
}
