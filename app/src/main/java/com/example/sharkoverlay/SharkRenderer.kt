package com.example.sharkoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import org.json.JSONObject

/** One process-wide immutable atlas set: approximately 26 MiB, even for ten sharks. */
object SharkArt {
    data class Clip(val bitmap: Bitmap, val frames: Int, val durationMs: Int, val frameSize: Int, val columns: Int)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val observers = linkedSetOf<() -> Unit>()
    private var loading = false
    private var clips: Map<SharkSpecies, Map<SharkMotion, Clip>>? = null
    private val releaseIdle = Runnable { if (observers.isEmpty()) clips = null }

    fun observe(context: Context, observer: () -> Unit) {
        main.removeCallbacks(releaseIdle)
        observers.add(observer)
        if (clips != null) {
            observer()
            return
        }
        if (loading) return
        loading = true
        val assets = context.applicationContext.assets
        executor.execute {
            val result = run {
                val loaded = mutableMapOf<SharkSpecies, MutableMap<SharkMotion, Clip>>()
                try {
                    for (species in SharkSpecies.entries) {
                        val speciesClips = mutableMapOf<SharkMotion, Clip>()
                        loaded[species] = speciesClips
                        val root = "shark/${species.assetFolder}/"
                        val manifest = assets.open(root + "animations.json").bufferedReader().use { JSONObject(it.readText()) }
                        for ((motion, name) in listOf(SharkMotion.SWIM to "swim", SharkMotion.TURN_LEFT to "turn_left", SharkMotion.TURN_RIGHT to "turn_right")) {
                            if (!manifest.has(name)) continue
                            val spec = manifest.getJSONObject(name)
                            val bitmap = assets.open(root + name + "_atlas.webp").use {
                                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                            } ?: error("Cannot decode ${species.id}/$name")
                            speciesClips[motion] = Clip(bitmap, spec.getInt("frames"), spec.getInt("durationMs"), spec.getInt("frameSize"), spec.getInt("columns"))
                        }
                    }
                    loaded.toMap()
                } catch (error: Exception) {
                    Log.e("SharkArt", "Animation loading failed; using Canvas fallback", error)
                    loaded.values.forEach { speciesClips -> speciesClips.values.forEach { it.bitmap.recycle() } }
                    null
                }
            }
            main.post {
                clips = result
                loading = false
                observers.toList().forEach { it() }
                if (observers.isEmpty()) main.postDelayed(releaseIdle, 30_000)
            }
        }
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
        if (observers.isEmpty()) main.postDelayed(releaseIdle, 30_000)
    }
    fun clip(species: SharkSpecies, motion: SharkMotion): Clip? =
        clips?.get(species)?.get(motion) ?: clips?.get(species)?.get(SharkMotion.SWIM)
}

/** Each shark owns timing, but all sharks share decoded pixels. No per-frame allocations. */
class SharkRenderer {
    private val timing = SharkAnimationState()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var currentSpecies = SharkSpecies.HAMMERHEAD
    private var previousSpecies = SharkSpecies.HAMMERHEAD
    private var previousMotion = SharkMotion.SWIM
    private var previousFrame = 0
    private var currentMotion = SharkMotion.SWIM
    private var currentFrame = 0
    private var blend = 1f
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)

    fun update(dt: Float, config: SharkConfig, navigator: SharkNavigator) {
        timing.advance(dt, config.speed, navigator.speedMultiplier)
        // The two approved concept clips are swim cycles, including during path turns.
        val motion = if (config.species == SharkSpecies.HAMMERHEAD) navigator.motion else SharkMotion.SWIM
        if (motion != currentMotion || config.species != currentSpecies) {
            previousSpecies = currentSpecies
            currentSpecies = config.species
            previousMotion = currentMotion
            previousFrame = currentFrame
            currentMotion = motion
            blend = 0f
        }
        blend = (blend + dt / 0.12f).coerceAtMost(1f)
        val clip = SharkArt.clip(currentSpecies, motion) ?: return
        currentFrame = timing.frame(motion, navigator.turnProgress, clip.frames, clip.durationMs)
    }

    fun draw(canvas: Canvas, bodyLength: Float, fallbackPhase: Float) {
        val clip = SharkArt.clip(currentSpecies, currentMotion)
        if (clip == null) {
            canvas.save()
            canvas.scale(bodyLength / 110f, bodyLength / 110f)
            SharkPreviewView.drawHammerhead(canvas, paint, fallbackPhase)
            canvas.restore()
            return
        }
        val half = bodyLength * 192f / 170f / 2f
        target.set(-half, -half, half, half)
        if (blend < 1f) {
            val checkpoint = canvas.saveLayer(target, null)
            SharkArt.clip(previousSpecies, previousMotion)?.let { drawFrame(canvas, it, previousFrame, 1f - blend) }
            paint.xfermode = add
            drawFrame(canvas, clip, currentFrame, blend)
            paint.xfermode = null
            canvas.restoreToCount(checkpoint)
        } else {
            drawFrame(canvas, clip, currentFrame, 1f)
        }
        paint.alpha = 255
    }

    private fun drawFrame(canvas: Canvas, clip: SharkArt.Clip, frame: Int, opacity: Float) {
        val x = frame % clip.columns * clip.frameSize
        val y = frame / clip.columns * clip.frameSize
        source.set(x, y, x + clip.frameSize, y + clip.frameSize)
        paint.alpha = (opacity * 255).toInt()
        canvas.drawBitmap(clip.bitmap, source, target, paint)
    }
}
