
package com.example.sharkoverlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class SharkPreviewView(context: Context) : View(context) {
    private data class Swimmer(
        val id: Int,
        var x: Float,
        var y: Float,
        var angle: Float,
        var targetAngle: Float,
        var phase: Float
    )

    private var configs = listOf<SharkConfig>()
    private val swimmers = mutableListOf<Swimmer>()
    private val random = Random.Default
    private var lastTime = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSharks(items: List<SharkConfig>) {
        configs = items.map { it.copy() }

        while (swimmers.size < configs.size) {
            val i = swimmers.size
            swimmers.add(
                Swimmer(
                    i,
                    random.nextFloat(),
                    random.nextFloat(),
                    random.nextFloat() * 360f,
                    random.nextFloat() * 360f,
                    random.nextFloat() * 6.28f
                )
            )
        }

        while (swimmers.size > configs.size) {
            swimmers.removeAt(swimmers.lastIndex)
        }

        invalidate()
    }

    private fun shape(
        canvas: Canvas,
        color: Int,
        vararg points: Float
    ) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) {
            path.lineTo(points[i], points[i + 1])
        }
        path.close()
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    companion object {
        fun drawHammerhead(
            canvas: Canvas,
            paint: Paint,
            phase: Float
        ) {
            // Local coordinates: nose to the right.
            // The tail oscillates independently.
            val wag = sin(phase) * 9f

            fun polygon(color: Int, vararg xy: Float) {
                val p = Path()
                p.moveTo(xy[0], xy[1])
                for (i in 2 until xy.size step 2) {
                    p.lineTo(xy[i], xy[i + 1])
                }
                p.close()
                paint.color = color
                paint.style = Paint.Style.FILL
                canvas.drawPath(p, paint)
            }

            val gray = Color.rgb(164, 164, 164)
            val light = Color.rgb(225, 225, 225)
            val dark = Color.rgb(75, 75, 75)

            // Tail stem and crescent-shaped caudal fin.
            polygon(
                gray,
                -35f, -4f,
                -52f, wag - 3f,
                -65f, wag - 17f,
                -61f, wag,
                -65f, wag + 17f,
                -52f, wag + 3f,
                -35f, 4f
            )

            // Left and right pectoral fins.
            polygon(
                light,
                -8f, -7f,
                -24f, -28f,
                8f, -9f
            )
            polygon(
                gray,
                -8f, 7f,
                -24f, 28f,
                8f, 9f
            )

            // Main body, gently tapering to the tail.
            polygon(
                gray,
                -43f, 0f,
                -32f, -6f,
                -6f, -11f,
                24f, -7f,
                37f, 0f,
                24f, 7f,
                -6f, 11f,
                -32f, 6f
            )

            // Hammer-shaped head.
            polygon(
                light,
                27f, -7f,
                34f, -20f,
                43f, -20f,
                45f, -14f,
                39f, -12f,
                40f, 12f,
                45f, 14f,
                43f, 20f,
                34f, 20f,
                27f, 7f
            )

            // Back highlight.
            polygon(
                light,
                -24f, -3f,
                8f, -7f,
                29f, -3f,
                8f, 0f
            )

            paint.color = dark
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            canvas.drawLine(17f, -8f, 17f, 8f, paint)
            canvas.drawLine(12f, -9f, 12f, 9f, paint)
            paint.style = Paint.Style.FILL

            // Eyes at both ends of the hammer.
            paint.color = Color.BLACK
            canvas.drawCircle(40f, -16f, 1.8f, paint)
            canvas.drawCircle(40f, 16f, 1.8f, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(18, 18, 18))

        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (lastTime == 0L) 0.04f
                 else ((now - lastTime) / 1000f).coerceIn(0f, 0.08f)
        lastTime = now

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        configs.forEachIndexed { i, config ->
            val s = swimmers.getOrNull(i) ?: return@forEachIndexed

            val scale = config.size / 100f
            val radius = 65f * scale
            val marginX = (radius / w).coerceAtMost(0.40f)
            val marginY = (radius / h).coerceAtMost(0.40f)

            // Turn gradually before reaching the edges.
            val cx = s.x * w
            val cy = s.y * h
            val headingX = cos(Math.toRadians(s.angle.toDouble())).toFloat()
            val headingY = sin(Math.toRadians(s.angle.toDouble())).toFloat()

            if (cx < radius && headingX < 0f ||
                cx > w - radius && headingX > 0f ||
                cy < radius && headingY < 0f ||
                cy > h - radius && headingY > 0f
            ) {
                s.targetAngle = Math.toDegrees(
                    atan2(
                        (h / 2f - cy).toDouble(),
                        (w / 2f - cx).toDouble()
                    )
                ).toFloat()
            } else if (random.nextFloat() < dt * 0.25f) {
                s.targetAngle += random.nextFloat() * 90f - 45f
            }

            val delta = ((s.targetAngle - s.angle + 540f) % 360f) - 180f
            val turn = 85f * dt
            s.angle += delta.coerceIn(-turn, turn)

            val rad = Math.toRadians(s.angle.toDouble())
            val speed = config.speed / 100f * 35f

            s.x += cos(rad).toFloat() * speed * dt / w
            s.y += sin(rad).toFloat() * speed * dt / h

            s.x = s.x.coerceIn(marginX, 1f - marginX)
            s.y = s.y.coerceIn(marginY, 1f - marginY)
            s.phase += dt * (5f + config.speed / 30f)

            canvas.save()
            canvas.translate(s.x * w, s.y * h)
            canvas.rotate(s.angle)
            canvas.scale(scale, scale)
            drawHammerhead(canvas, paint, s.phase)
            canvas.restore()
        }

        postInvalidateDelayed(40L)
    }

    override fun onDetachedFromWindow() {
        lastTime = 0L
        super.onDetachedFromWindow()
    }
}
