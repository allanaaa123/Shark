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

/** One process-wide immutable atlas set: approximately 32 MiB, even for ten sharks. */
object SharkArt {
    data class Clip(
        val bitmap: Bitmap?, val frames: Int, val durationMs: Int,
        val frameSize: Int, val columns: Int, val meshFrames: List<Bitmap> = emptyList(),
        val thumbnailBounds: Rect = Rect(0, 0, frameSize, frameSize)
    )
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
                            val frameCount = spec.getInt("frames")
                            val frameSize = spec.getInt("frameSize")
                            val columns = spec.getInt("columns")
                            // BitmapMesh takes a whole bitmap. Split once, share tiles across
                            // all pets, and release the source atlas to avoid doubling memory.
                            val tileCount = if (species == SharkSpecies.HAMMERHEAD) frameCount else 1
                            val tiles = List(tileCount) { i ->
                                Bitmap.createBitmap(bitmap, i % columns * frameSize, i / columns * frameSize, frameSize, frameSize)
                            }
                            speciesClips[motion] = Clip(null, tileCount,
                                spec.getInt("durationMs"), frameSize, columns, tiles, alphaBounds(tiles.first()))
                            if (tiles.none { it === bitmap }) bitmap.recycle()
                        }
                    }
                    loaded.toMap()
                } catch (error: Exception) {
                    Log.e("SharkArt", "Animation loading failed; using Canvas fallback", error)
                    loaded.values.forEach { speciesClips -> speciesClips.values.forEach { clip ->
                        clip.bitmap?.recycle()
                        clip.meshFrames.forEach { it.recycle() }
                    } }
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

    private fun alphaBounds(bitmap: Bitmap): Rect {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width; var top = bitmap.height; var right = 0; var bottom = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((pixels[y*bitmap.width+x] ushr 24) > 20) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x+1); bottom = maxOf(bottom, y+1)
            }
        }
        return if (right > left && bottom > top) Rect((left-3).coerceAtLeast(0), (top-3).coerceAtLeast(0),
            (right+3).coerceAtMost(bitmap.width), (bottom+3).coerceAtMost(bitmap.height))
        else Rect(0, 0, bitmap.width, bitmap.height)
    }
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
    private val mesh = FloatArray(HammerheadMotion.VERTEX_FLOATS)
    private val rayMesh = FloatArray(RayMotion.VERTEX_FLOATS)
    private val rayScratch = FloatArray((RayMotion.COLUMNS+1)*2)
    private var tailPhase = 0.0
    private var turnBend = 0f
    private val turnPose = TurnPose()
    private var initialized = false
    private var drawingLength = 110f
    private val tailPosition = FloatArray(2)

    fun tailOffsetX(scale: Float): Float {
        if(!currentSpecies.isRay) return -53f*scale
        updateRayTail(scale)
        return tailPosition[0]
    }

    private fun updateRayTail(scale: Float) {
        RayMotion.tailPosition(tailPosition,110f*scale,timing.swimTimeMs/currentSpecies.swimCycleMs*2.0*Math.PI,
            currentSpecies,turnPose.bodyBend,turnPose.tailBend)
    }

    fun update(dt: Float, config: SharkConfig, navigator: SharkNavigator) {
        if (!initialized) {
            currentSpecies = config.species; previousSpecies = config.species
            initialized = true
        }
        timing.advance(dt, config.speed, navigator.speedMultiplier)
        turnPose.update(dt, navigator.angularVelocity)
        tailPhase = timing.swimTimeMs / 1008.0 * 2.0 * Math.PI
        val targetBend = if (config.species == SharkSpecies.HAMMERHEAD)
            (navigator.angularVelocity / 220f).coerceIn(-1f, 1f) else 0f
        val maxBendChange = dt.coerceIn(0f, 0.08f) * 6f
        turnBend += (targetBend - turnBend).coerceIn(-maxBendChange, maxBendChange)
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

    fun tailOffsetY(scale: Float): Float {
        if(currentSpecies.isRay) { updateRayTail(scale); return tailPosition[1] }
        if (currentSpecies == SharkSpecies.HAMMERHEAD) return 0f
        val cycleMs = currentSpecies.swimCycleMs
        return SpeciesSwimMotion.tailY(110f * scale * 192f / 170f / 2f,
            timing.swimTimeMs / cycleMs * 2.0 * Math.PI, currentSpecies,
            turnPose.bodyBend, turnPose.tailBend)
    }

    fun draw(canvas: Canvas, bodyLength: Float, fallbackPhase: Float) {
        drawingLength = bodyLength
        val clip = SharkArt.clip(currentSpecies, currentMotion)
        if (clip == null) {
            if(currentSpecies != SharkSpecies.HAMMERHEAD) return
            canvas.save()
            canvas.scale(bodyLength / 110f, bodyLength / 110f)
            SharkPreviewView.drawHammerhead(canvas, paint, fallbackPhase)
            canvas.restore()
            return
        }
        val half = bodyLength * 192f / 170f / 2f
        target.set(-half, -half, half, half)
        if (currentSpecies.isRay || (blend < 1f && previousSpecies.isRay)) {
            target.set(-bodyLength*2f, -bodyLength*1.3f, bodyLength, bodyLength*1.3f)
        }
        if (blend < 1f) {
            val checkpoint = canvas.saveLayer(target, null)
            SharkArt.clip(previousSpecies, previousMotion)?.let { drawFrame(canvas, it, previousFrame, 1f - blend, previousSpecies) }
            paint.xfermode = add
            drawFrame(canvas, clip, currentFrame, blend, currentSpecies)
            paint.xfermode = null
            canvas.restoreToCount(checkpoint)
        } else {
            drawFrame(canvas, clip, currentFrame, 1f, currentSpecies)
        }
        paint.alpha = 255
    }

    private fun drawFrame(canvas: Canvas, clip: SharkArt.Clip, frame: Int, opacity: Float, species: SharkSpecies) {
        paint.alpha = (opacity * 255).toInt()
        if (clip.meshFrames.isNotEmpty()) {
            if (species.isRay) {
                // target is a generous layer bound for a ray, not its texture rectangle.
                RayMotion.fill(rayMesh, rayScratch, drawingLength,
                    timing.swimTimeMs/species.swimCycleMs*2.0*Math.PI, species,
                    turnPose.bodyBend, turnPose.tailBend)
                canvas.drawBitmapMesh(clip.meshFrames[frame], RayMotion.COLUMNS, RayMotion.ROWS,
                    rayMesh, 0, null, 0, paint)
                return
            } else if (species == SharkSpecies.HAMMERHEAD) {
                HammerheadMotion.fill(mesh, drawingLength*192f/170f/2f, tailPhase, turnBend)
            } else {
                val cycleMs = species.swimCycleMs
                SpeciesSwimMotion.fill(mesh, drawingLength*192f/170f/2f,
                    timing.swimTimeMs / cycleMs * 2.0 * Math.PI, species,
                    turnPose.bodyBend, turnPose.tailBend)
            }
            canvas.drawBitmapMesh(clip.meshFrames[frame], HammerheadMotion.COLUMNS,
                HammerheadMotion.ROWS, mesh, 0, null, 0, paint)
            return
        }
        val atlas = clip.bitmap ?: return
        val x = frame % clip.columns * clip.frameSize
        val y = frame / clip.columns * clip.frameSize
        source.set(x, y, x + clip.frameSize, y + clip.frameSize)
        paint.alpha = (opacity * 255).toInt()
        canvas.drawBitmap(atlas, source, target, paint)
    }
}




