
package com.example.sharkoverlay

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import org.json.JSONArray
import kotlin.math.*
import kotlin.random.Random

class SharkService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random.Default

    private data class Shark(
        val config: SharkConfig,
        val view: SharkView,
        val params: WindowManager.LayoutParams,
        var x: Float,
        var y: Float,
        var angle: Float,
        var targetAngle: Float,
        var phase: Float
    )

    private val sharks = mutableListOf<Shark>()
    private var lastTime = 0L
    private var running = false

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return

            val now = SystemClock.uptimeMillis()
            val dt = if (lastTime == 0L) 0.04f
                     else ((now - lastTime) / 1000f).coerceIn(0f, 0.08f)
            lastTime = now

            val display = resources.displayMetrics
            val screenW = display.widthPixels.toFloat()
            val screenH = display.heightPixels.toFloat()

            sharks.forEach { s ->
                val speed = s.config.speed / 100f *
                    65f * display.density

                val rad = Math.toRadians(s.angle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()

                val margin = max(s.params.width, s.params.height) * 0.7f
                val cx = s.x + s.params.width / 2f
                val cy = s.y + s.params.height / 2f

                val nearEdge =
                    (cx < margin && dx < 0f) ||
                    (cx > screenW - margin && dx > 0f) ||
                    (cy < margin && dy < 0f) ||
                    (cy > screenH - margin && dy > 0f)

                if (nearEdge) {
                    s.targetAngle = Math.toDegrees(
                        atan2(
                            (screenH / 2f - cy).toDouble(),
                            (screenW / 2f - cx).toDouble()
                        )
                    ).toFloat()
                } else if (random.nextFloat() < dt * 0.15f) {
                    s.targetAngle += random.nextFloat() * 90f - 45f
                }

                val difference =
                    ((s.targetAngle - s.angle + 540f) % 360f) - 180f

                val turnRate = 80f * dt
                val actualTurn = difference.coerceIn(-turnRate, turnRate)
                s.angle += actualTurn

                val newRad = Math.toRadians(s.angle.toDouble())
                s.x += cos(newRad).toFloat() * speed * dt
                s.y += sin(newRad).toFloat() * speed * dt

                s.x = s.x.coerceIn(
                    0f,
                    max(0f, screenW - s.params.width)
                )
                s.y = s.y.coerceIn(
                    0f,
                    max(0f, screenH - s.params.height)
                )

                s.phase += dt * (5f + s.config.speed / 30f)
                s.view.phase = s.phase
                s.view.angle = s.angle

                s.params.x = s.x.toInt()
                s.params.y = s.y.toInt()

                try {
                    wm.updateViewLayout(s.view, s.params)
                    s.view.invalidate()
                } catch (_: Exception) {
                    // Overlay may be removed during shutdown.
                }
            }

            handler.postDelayed(this, 40L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "pet"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                "桌面雙髻鯊",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SharkService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("雙髻鯊游泳中")
            .setContentText("點擊調整設定，或按停止")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "停止",
                    stopIntent
                ).build()
            )
            .build()

        startForeground(1, notification)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        clearSharks()

        val prefs = getSharedPreferences("shark", MODE_PRIVATE)
        val configs = mutableListOf<SharkConfig>()

        try {
            val array = JSONArray(
                prefs.getString("sharks", "[]") ?: "[]"
            )
            for (i in 0 until min(10, array.length())) {
                val obj = array.getJSONObject(i)
                configs.add(
                    SharkConfig(
                        obj.optInt("size", 100).coerceIn(30, 200),
                        obj.optInt("speed", 100).coerceIn(20, 200)
                    )
                )
            }
        } catch (_: Exception) {}

        val display = resources.displayMetrics
        val screenW = display.widthPixels
        val screenH = display.heightPixels

        configs.forEach { config ->
            val scale = config.size / 100f
            val width = (180f * scale * display.density)
                .toInt().coerceAtLeast(1)
            val height = (180f * scale * display.density)
                .toInt().coerceAtLeast(1)

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = random.nextInt(max(1, screenW - width + 1))
                y = random.nextInt(max(1, screenH - height + 1))
            }

            val view = SharkView(this)
            val angle = random.nextFloat() * 360f
            val shark = Shark(
                config,
                view,
                params,
                params.x.toFloat(),
                params.y.toFloat(),
                angle,
                angle,
                random.nextFloat() * 6.28f
            )

            try {
                wm.addView(view, params)
                sharks.add(shark)
            } catch (_: Exception) {}
        }

        lastTime = 0L
        running = true
        handler.removeCallbacks(loop)
        handler.post(loop)

        return START_STICKY
    }

    private fun clearSharks() {
        running = false
        handler.removeCallbacks(loop)
        sharks.forEach {
            try {
                wm.removeView(it.view)
            } catch (_: Exception) {}
        }
        sharks.clear()
    }

    override fun onDestroy() {
        clearSharks()
        super.onDestroy()
    }
}

private class SharkView(context: Context) : View(context) {
    var angle = 0f
    var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(angle)

        val scale = min(width, height) / 180f
        canvas.scale(scale, scale)

        SharkPreviewView.drawHammerhead(
            canvas,
            paint,
            phase
        )

        canvas.restore()
    }
}
