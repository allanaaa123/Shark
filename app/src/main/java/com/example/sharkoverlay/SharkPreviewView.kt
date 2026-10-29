
package com.example.sharkoverlay

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class SharkPreviewView(context: Context) : View(context) {

    private val configs = mutableListOf<SharkConfig>()
    private val swimmers = mutableListOf<SharkNavigator>()
    private val renderers = mutableListOf<SharkRenderer>()
    private val effects = mutableListOf<SwimEffects>()
    private val effectsPainter = SwimEffectsPainter()
    private val social = SharkSocialController()

    fun applySocialMode(value: SocialMode) {
        social.applyMode(value)
        invalidate()
    }
    fun startle() {
        social.reset()
        swimmers.forEach { it.startle() }
        invalidate()
    }

    fun stopStartle() { swimmers.forEach { it.clearStartle() } }

    private val artReady: () -> Unit = { invalidate() }
    private val random = Random.Default
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var effectsStrength = EffectsStrength.GENTLE

    fun applyEffectsStrength(value: EffectsStrength) {
        effectsStrength = value
        effects.forEach { it.applyStrength(value) }
        invalidate()
    }

    private var lastTime = 0L
    private var needsInitialPlacement = false

    fun setSharks(items: List<SharkConfig>) {
        val existing = configs.indices.associate { i ->
            configs[i].id to PreviewState(configs[i], swimmers[i], renderers[i], effects[i])
        }
        configs.clear(); swimmers.clear(); renderers.clear(); effects.clear()
        for (item in items.take(SharkConfig.MAX_PETS)) {
            val previous = existing[item.id]?.takeIf { it.config.species == item.species }
            if(previous == null && (width <= 0 || height <= 0)) needsInitialPlacement = true
            configs.add(item.copy())
            swimmers.add(previous?.nav ?: SharkNavigator(
                random.nextFloat()*width.coerceAtLeast(1),
                random.nextFloat()*height.coerceAtLeast(1), random.nextFloat()*360f))
            renderers.add(previous?.renderer ?: SharkRenderer())
            val fx = previous?.effects ?: SwimEffects()
            if (previous != null && previous.config.size != item.size) fx.clear()
            fx.applyStrength(effectsStrength)
            effects.add(fx)
        }
        social.reset()
        invalidate()
    }

    fun resumePreview() { lastTime = 0L; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(needsInitialPlacement && w>0 && h>0) {
            swimmers.forEach { nav ->
                nav.x=w*(.15f+random.nextFloat()*.7f)
                nav.y=h*(.15f+random.nextFloat()*.7f)
            }
            needsInitialPlacement=false
        }
    }

    private data class PreviewState(val config: SharkConfig, val nav: SharkNavigator,
        val renderer: SharkRenderer, val effects: SwimEffects)

    companion object {

        fun drawHammerhead(
            canvas: Canvas,
            paint: Paint,
            phase: Float
        ) {
            val wag = sin(phase) * 9f

            fun polygon(color: Int, vararg xy: Float) {
                val path = Path()
                path.moveTo(xy[0], xy[1])

                for (i in 2 until xy.size step 2) {
                    path.lineTo(xy[i], xy[i + 1])
                }

                path.close()
                paint.color = color
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }

            val gray = Color.rgb(164, 164, 164)
            val light = Color.rgb(225, 225, 225)
            val dark = Color.rgb(75, 75, 75)

            // 尾柄與左右擺動的尾鰭
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

            // 胸鰭
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

            // 身體
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

            // 雙髻鯊特有的錘形頭部
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

            // 背部亮面
            polygon(
                light,
                -24f, -3f,
                8f, -7f,
                29f, -3f,
                8f, 0f
            )

            // 鰓裂
            paint.color = dark
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f

            canvas.drawLine(
                17f, -8f,
                17f, 8f,
                paint
            )

            canvas.drawLine(
                12f, -9f,
                12f, 9f,
                paint
            )

            paint.style = Paint.Style.FILL

            // 眼睛
            paint.color = Color.BLACK
            canvas.drawCircle(40f, -16f, 1.8f, paint)
            canvas.drawCircle(40f, 16f, 1.8f, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(5, 37, 55))

        if (!isShown || windowVisibility != VISIBLE) return

        val now = SystemClock.uptimeMillis()

        val dt = if (lastTime == 0L) {
            0.04f
        } else {
            ((now - lastTime) / 1000f)
                .coerceIn(0f, 0.08f)
        }

        lastTime = now

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0f || h <= 0f) return

        val detailFactor = SwimEffects.detailFor(configs.size)
        social.beginFrame(configs.size)
        configs.forEachIndexed { index, config ->
            val nav = swimmers[index]
            social.capture(index, nav.x, nav.y, nav.angle, 110f * config.size / 100f,
                35f * config.speed / 100f * nav.speedMultiplier, nav.isReturning || nav.isStartled)
        }
        social.update(dt)
        configs.forEachIndexed { index, config ->
            val nav = swimmers.getOrNull(index)
                ?: return@forEachIndexed

            val scale = config.size / 100f
            val radius = 65f * scale
            val speed = 35f * config.speed / 100f

            nav.update(
                dtSeconds = dt,
                width = w,
                height = h,
                speed = speed,
                radius = radius,
                species = config.species,
                socialTurnRate = social.turnRate(index),
                socialSpeedFactor = social.speedFactor(index)
            )

            renderers[index].update(dt, config, nav)
            effects[index].update(dt, nav.x, nav.y, nav.angle, nav.angularVelocity, scale,
                tailOffsetY = renderers[index].tailOffsetY(scale), detailFactor = detailFactor,
                tailOffsetX = renderers[index].tailOffsetX(scale))
        }

        for (effect in effects) effectsPainter.draw(canvas, effect)
        configs.forEachIndexed { index, config ->
            val nav = swimmers[index]
            val scale = config.size / 100f
            canvas.save()

            canvas.translate(
                nav.x,
                nav.y
            )

            canvas.rotate(nav.angle)
            canvas.scale(scale, scale)

            renderers[index].draw(canvas, 110f, nav.phase)

            canvas.restore()
        }

        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SharkArt.observe(context, artReady)
        lastTime = 0L
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) swimmers.forEach { it.clearStartle(immediate = true) }
        lastTime = 0L
        if (visibility == VISIBLE) invalidate()
    }

    override fun onDetachedFromWindow() {
        SharkArt.removeObserver(artReady)
        lastTime = 0L
        super.onDetachedFromWindow()
    }
}








