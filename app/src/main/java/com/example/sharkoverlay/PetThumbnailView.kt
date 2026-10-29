package com.example.sharkoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Static thumbnails use the same shared pixels as swimming pets. */
class PetThumbnailView(context: Context, var species: SharkSpecies) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private val artReady: () -> Unit = { invalidate() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); SharkArt.observe(context, artReady) }
    override fun onDetachedFromWindow() { SharkArt.removeObserver(artReady); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val clip = SharkArt.clip(species, SharkMotion.SWIM)
        val bitmap = clip?.meshFrames?.firstOrNull() ?: clip?.bitmap
        if (clip == null || bitmap == null) {
            paint.color = Color.rgb(120, 146, 162); paint.textSize = 12f*resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("載入中", width/2f, height/2f, paint)
            return
        }
        val pad = 8f*resources.displayMetrics.density
        val bounds = clip.thumbnailBounds
        val scale = min((width-2*pad)/bounds.width(), (height-2*pad)/bounds.height()).coerceAtLeast(0f)
        val w = bounds.width()*scale; val h = bounds.height()*scale
        target.set((width-w)/2f, (height-h)/2f, (width+w)/2f, (height+h)/2f)
        paint.color = Color.WHITE
        canvas.drawBitmap(bitmap, bounds, target, paint)
    }
}
