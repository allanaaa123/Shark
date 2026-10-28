
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
        val navigator: SharkNavigator
    )

    private val sharks = mutableListOf<Shark>()
    private var running = false
    private var lastTime = 0L
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
            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()

            sharks.forEach { shark ->
                val nav = shark.navigator
                val config = shark.config

                val speed =
                    65f * metrics.density *
                    config.speed / 100f

                // 導航使用鯊魚中心座標。
                val radius =
                    min(
                        shark.params.width,
                        shark.params.height
                    ) * 0.36f

                nav.update(
                    dtSeconds = dt,
                    width = screenW,
                    height = screenH,
                    speed = speed,
                    radius = radius
                )

                shark.params.x = (
                    nav.x - shark.params.width / 2f
                ).toInt()

                shark.params.y = (
                    nav.y - shark.params.height / 2f
                ).toInt()

                shark.view.angle = nav.angle
                shark.view.phase = nav.phase
                shark.view.updateAnimation(dt, config, nav)

                try {
                    wm.updateViewLayout(
                        shark.view,
                        shark.params
                    )
                    shark.view.invalidate()
                } catch (_: Exception) {
                    // Overlay may have been removed.
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
            val scale = config.size / 100f

            val viewSize = (
                180f * scale * metrics.density
            ).toInt().coerceAtLeast(1)

            val params = WindowManager.LayoutParams(
                viewSize,
                viewSize,
                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams
                        .FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams
                        .FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            val radius = viewSize * 0.36f

            val minX = radius.coerceAtMost(
                screenW * 0.45f
            )
            val minY = radius.coerceAtMost(
                screenH * 0.45f
            )

            val x = minX +
                random.nextFloat() *
                max(0f, screenW - 2f * minX)

            val y = minY +
                random.nextFloat() *
                max(0f, screenH - 2f * minY)

            val navigator = SharkNavigator(
                x = x,
                y = y,
                angle = random.nextFloat() * 360f
            )

            params.x = (
                x - viewSize / 2f
            ).toInt()

            params.y = (
                y - viewSize / 2f
            ).toInt()

            val view = SharkView(this).apply {
                angle = navigator.angle
                phase = navigator.phase
            }

            try {
                wm.addView(view, params)

                sharks.add(
                    Shark(
                        config = config,
                        view = view,
                        params = params,
                        navigator = navigator
                    )
                )
            } catch (_: Exception) {
                // Skip if overlay cannot be added.
            }
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

        if (::wm.isInitialized) {
            sharks.forEach { shark ->
                try {
                    wm.removeView(shark.view)
                } catch (_: Exception) {}
            }
        }

        sharks.clear()
    }

    override fun onDestroy() {
        clearSharks()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}

private class SharkView(
    context: Context
) : View(context) {

    var angle = 0f
    var phase = 0f

    private val renderer = SharkRenderer()
    private val artReady: () -> Unit = { invalidate() }

    fun updateAnimation(dt: Float, config: SharkConfig, navigator: SharkNavigator) {
        renderer.update(dt, config, navigator)
    }

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

        canvas.save()

        canvas.translate(
            width / 2f,
            height / 2f
        )

        canvas.rotate(angle)

        val scale = min(
            width,
            height
        ) / 180f

        canvas.scale(scale, scale)

        renderer.draw(canvas, 110f, phase)

        canvas.restore()
    }
}

