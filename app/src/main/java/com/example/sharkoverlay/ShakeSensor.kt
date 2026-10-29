package com.example.sharkoverlay

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Lifecycle-owned, low-rate sensor subscription. No runtime permission is required. */
class ShakeSensor(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = ShakeDetector()
    private var listening = false
    val isAvailable: Boolean get() = sensor != null

    fun start() {
        if (listening) return
        val accelerometer = sensor ?: return
        detector.reset()
        listening = manager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) == true
    }

    fun stop() {
        if (listening) manager?.unregisterListener(this)
        listening = false
        detector.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (listening && event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3 &&
            detector.sample(event.timestamp,event.values[0],event.values[1],event.values[2])) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object { const val PREFERENCE = "shake_enabled" }
}
