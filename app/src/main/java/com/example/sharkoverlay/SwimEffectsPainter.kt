package com.example.sharkoverlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** Draw before the fish, in viewport coordinates; never attach effects to its rotation. */
class SwimEffectsPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()

    fun draw(canvas: Canvas, effects: SwimEffects) {
        paint.color = Color.rgb(91, 205, 235)
        val trails = effects.trails
        for (i in 0 until trails.size - 1) {
            val a = trails[(effects.nextTrail + i) % trails.size]
            val b = trails[(effects.nextTrail + i + 1) % trails.size]
            if (a.age >= SwimEffects.TRAIL_LIFE || b.age >= SwimEffects.TRAIL_LIFE) continue
            // Do not bridge a resize, configuration change, or an unrelated old sample.
            if (hypot(b.x - a.x, b.y - a.y) > 90f * max(a.scale, b.scale)) continue
            val fade = (1f - a.age / SwimEffects.TRAIL_LIFE).coerceIn(0f, 1f)
            paint.alpha = (88f * fade * fade).toInt()
            paint.strokeWidth = 1.15f * a.scale
            for (side in -1..1 step 2) {
                val da = side * (4f + a.age * 7f) * a.scale
                val db = side * (4f + b.age * 7f) * b.scale
                canvas.drawLine(a.x + a.nx * da, a.y + a.ny * da,
                    b.x + b.nx * db, b.y + b.ny * db, paint)
            }
        }
        paint.color = Color.rgb(155, 220, 239)
        for (p in effects.bubbles) {
            if (p.age >= SwimEffects.BUBBLE_LIFE) continue
            val fade = 1f - p.age / SwimEffects.BUBBLE_LIFE
            paint.alpha = (125f * fade).toInt()
            paint.strokeWidth = 0.75f * p.scale
            canvas.drawCircle(p.x, p.y, p.radius + p.age * 0.3f * p.scale, paint)
        }
        paint.color = Color.rgb(135, 219, 239)
        for (p in effects.ripples) {
            if (p.age >= SwimEffects.RIPPLE_LIFE) continue
            val fade = 1f - p.age / SwimEffects.RIPPLE_LIFE
            val radius = (12f + p.age * 22f) * p.scale
            paint.alpha = (72f * fade * fade).toInt()
            paint.strokeWidth = 1f * p.scale
            bounds.set(p.x - radius, p.y - radius, p.x + radius, p.y + radius)
            canvas.drawArc(bounds, p.angle, 80f, false, paint)
        }
        paint.alpha = 255
    }
}
