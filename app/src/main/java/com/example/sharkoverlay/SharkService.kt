
package com.example.sharkoverlay

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.input.InputManager
import android.util.Log
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

    private val sharks = mutableListOf<Shark>()
    private var running = false
    private var lastTime = 0L
    private var overlayView: SharkView? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                handler.removeCallbacks(loop)
                lastTime = 0L
            } else if (intent?.action == Intent.ACTION_SCREEN_ON && running) {
                handler.removeCallbacks(loop)
                lastTime = 0L
                handler.post(loop)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running || !getSystemService(PowerManager::class.java).isInteractive) return

            val now = SystemClock.uptimeMillis()
            val dt = if (lastTime == 0L) {
                0.04f
            } else {
                ((now - lastTime) / 1000f)
                    .coerceIn(0f, 0.08f)
            }
            lastTime = now

            val metrics = resources.displayMetrics
            val view = overlayView ?: return
            val screenW = view.width.takeIf { it > 0 }?.toFloat() ?: metrics.widthPixels.toFloat()
            val screenH = view.height.takeIf { it > 0 }?.toFloat() ?: metrics.heightPixels.toFloat()

            sharks.forEach { shark ->
                val nav = shark.navigator
                val config = shark.config

                val speed =
                    65f * metrics.density *
                    config.speed / 100f

                val radius = 64.8f * shark.scale

                nav.update(
                    dtSeconds = dt,
                    width = screenW,
                    height = screenH,
                    speed = speed,
                    radius = radius,
                    species = config.species
                )

                shark.renderer.update(dt, config, nav)
            }
            // One transparent surface; no per-pet WindowManager/input-region moves.
            view.invalidate()

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
        val notificationManager =
            getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "桌面海洋寵物",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(
                this,
                SharkService::class.java
            ).setAction("STOP"),
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

        val notification =
            Notification.Builder(this, channelId)
                .setContentTitle("海洋寵物游泳中")
                .setContentText("點擊調整設定")
                .setSmallIcon(
                    android.R.drawable.ic_menu_compass
                )
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

        wm = getSystemService(
            WINDOW_SERVICE
        ) as WindowManager

        clearSharks()

        val configs = mutableListOf<SharkConfig>()
        val prefs = getSharedPreferences(
            "shark",
            MODE_PRIVATE
        )

        try {
            val array = JSONArray(
                prefs.getString("sharks", "[]") ?: "[]"
            )

            for (i in 0 until min(10, array.length())) {
                val obj = array.getJSONObject(i)

                configs.add(
                    SharkConfig.fromSaved(
                        size = obj.optInt("size", 100),
                        speed = obj.optInt("speed", 100),
                        speciesId = obj.optString("species", "")
                    )
                )
            }
        } catch (_: Exception) {
            // Invalid saved settings.
        }

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        configs.forEach { config ->
            val scale = config.size / 100f * metrics.density
            val radius = 64.8f * scale
            val minX = radius.coerceAtMost(screenW * 0.45f)
            val minY = radius.coerceAtMost(screenH * 0.45f)
            sharks.add(Shark(
                config = config,
                scale = scale,
                navigator = SharkNavigator(
                    minX + random.nextFloat() * max(0f, screenW - 2f * minX),
                    minY + random.nextFloat() * max(0f, screenH - 2f * minY),
                    random.nextFloat() * 360f
                )
            ))
        }

        if (sharks.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val maximumOpacity = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch }
                .getOrDefault(0.8f)
        } else 1f
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = OverlayTouchPolicy.windowAlpha(Build.VERSION.SDK_INT, maximumOpacity)
        }
        val view = SharkView(this, sharks)
        try {
            wm.addView(view, params)
            overlayView = view
        } catch (error: Exception) {
            Log.e("SharkService", "Cannot attach pet overlay", error)
            stopSelf()
            return START_NOT_STICKY
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

        overlayView?.let { view ->
            if (::wm.isInitialized) {
                try { wm.removeView(view) } catch (_: IllegalArgumentException) { }
            }
        }
        overlayView = null
        lastTime = 0L

        sharks.clear()
    }

    override fun onDestroy() {
        clearSharks()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}

private data class Shark(
    val config: SharkConfig,
    val scale: Float,
    val navigator: SharkNavigator,
    val renderer: SharkRenderer = SharkRenderer()
)

private class SharkView(context: Context, private val sharks: List<Shark>) : View(context) {
    private val artReady: () -> Unit = { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SharkArt.observe(context, artReady)
    }

    override fun onDetachedFromWindow() {
        SharkArt.removeObserver(artReady)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Explicitly erase previous positions on this window's own surface only.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (shark in sharks) {
            canvas.save()
            canvas.translate(shark.navigator.x, shark.navigator.y)
            canvas.rotate(shark.navigator.angle)
            shark.renderer.draw(canvas, 110f * shark.scale, shark.navigator.phase)
            canvas.restore()
        }
    }
}
