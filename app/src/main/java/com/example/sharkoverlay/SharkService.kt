package com.example.sharkoverlay

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import kotlin.math.*
import kotlin.random.Random

class SharkService : Service() {
    private lateinit var wm: WindowManager
    private var pet: PetView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0
    private var direction = 1
    private val loop = object : Runnable {
        override fun run() {
            val p = params ?: return
            val v = pet ?: return
            val display = resources.displayMetrics
            val speed = getSharedPreferences("shark", MODE_PRIVATE).getInt("speed", 45)
            p.x += direction * (1 + speed / 18)
            if (p.x > display.widthPixels - p.width || p.x < 0) {
                direction *= -1; p.x = p.x.coerceIn(0, max(0, display.widthPixels - p.width))
            }
            tick++
            p.y = (display.heightPixels * (0.20 + 0.12 * sin(tick * 0.018))).toInt().coerceAtLeast(0)
            v.flip = direction < 0
            v.frame = tick
            try { wm.updateViewLayout(v, p) } catch (_: Exception) { return }
            v.invalidate()
            handler.postDelayed(this, 40)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val channelId = "pet"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channelId, "桌面鯊魚", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, SharkService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = Notification.Builder(this, channelId).setContentTitle("像素鯊魚游泳中").setContentText("點擊調整設定，或按停止").setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(open).addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        startForeground(1, notif)
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        pet?.let { wm.removeView(it) }
        handler.removeCallbacks(loop)
        val dp = resources.displayMetrics.density
        val size = getSharedPreferences("shark", MODE_PRIVATE).getInt("size", 90)
        val width = (size * dp * 2.1).toInt()
        val height = (size * dp * 1.2).toInt()
        params = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 220 }
        pet = PetView(this)
        wm.addView(pet, params)
        handler.post(loop)
        return START_STICKY
    }
    override fun onDestroy() { handler.removeCallbacks(loop); pet?.let { try { wm.removeView(it) } catch (_: Exception) {} }; pet = null; super.onDestroy() }
}

private class PetView(context: Context) : View(context) {
    var flip = false
    var frame = 0
    private val prefs = context.getSharedPreferences("shark", Context.MODE_PRIVATE)
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
    private val pixelCanvas = Canvas(bitmap)
    private fun polygon(vararg pts: Int) {
        val path = Path().apply { moveTo(pts[0].toFloat(), pts[1].toFloat()); for (i in 2 until pts.size step 2) lineTo(pts[i].toFloat(), pts[i+1].toFloat()); close() }
        pixelCanvas.drawPath(path, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap.eraseColor(Color.TRANSPARENT)
        val hammer = prefs.getInt("species", 0) == 1
        val top = prefs.getInt("perspective", 1) == 0
        pixelCanvas.save()
        if (flip) { pixelCanvas.scale(-1f, 1f, 32f, 18f) }
        val tailWag = (sin(frame * .22) * 3).toInt()
        paint.color = Color.BLACK
        if (top) {
            // Top-down shark swimming horizontally; wide pectoral fins and distinct hammer head.
            polygon(14,16, 22,12, 41,12, 49,16, 42,20, 22,20)
            polygon(30,13, 22,3, 36,12)
            polygon(30,19, 22,31, 37,20)
            polygon(45,15, 54,13+tailWag, 58,8+tailWag, 57,18+tailWag, 58,27+tailWag, 53,21+tailWag, 45,19)
            if (hammer) { polygon(10,9, 15,9, 17,12, 17,22, 15,25, 10,25, 10,21, 12,18, 12,15, 10,13) }
            else polygon(8,17, 16,13, 17,21)
            paint.color = Color.WHITE
            polygon(19,16, 36,14, 44,16, 35,17, 19,18)
        } else {
            polygon(9,18, 17,13, 34,12, 47,16, 51,18, 45,21, 20,23)
            polygon(29,13, 35,4, 38,14)
            polygon(26,21, 30,28, 34,22)
            polygon(42,20, 45,26, 48,20)
            polygon(49,17, 56,12+tailWag, 57,18+tailWag, 56,25+tailWag, 48,20)
            if (hammer) { polygon(8,11, 12,11, 14,16, 14,22, 9,24, 7,20, 10,18) }
            else polygon(5,18, 12,14, 13,22)
            paint.color = Color.WHITE
            polygon(12,20, 27,21, 43,19, 38,22, 20,24)
            paint.color = Color.BLACK
            pixelCanvas.drawRect(13f, 16f, 14f, 17f, paint)
        }
        // Monochrome square particles behind the tail.
        paint.color = Color.BLACK
        for (i in 0 until 15) {
            val x = 58 + (i * 7 + frame * 2) % 14
            val y = 9 + ((i * 11 + frame / 4) % 18)
            if (x < 64 && (i + frame / 3) % 4 != 0) pixelCanvas.drawRect(x.toFloat(), y.toFloat(), (x+1).toFloat(), (y+1).toFloat(), paint)
        }
        pixelCanvas.restore()
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        // White rim helps visibility over dark app backgrounds.
        // Draw black-and-white pixel sprite with nearest-neighbor scaling.
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
    }
}
