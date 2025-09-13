package com.example.alertciclista

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlin.math.abs

class FallDetector(
    context: Context,
    private val onFallDetected: () -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastFallTime: Long = 0
    private var FALL_THRESHOLD = 20.0f
    private val LOW_THRESHOLD = 2.0f
    private val MIN_INTERVAL_MS = 5000L
    private val GRAVITY = 9.81f
    private var recentValues = mutableListOf<Float>()
    private val BUFFER_SIZE = 10

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        val netMagnitude = abs(magnitude - GRAVITY)

        recentValues.add(netMagnitude)
        if (recentValues.size > BUFFER_SIZE) {
            recentValues.removeAt(0)
        }

        val currentTime = System.currentTimeMillis()

        if (recentValues.size >= 5) {
            val hasHighPeak = recentValues.any { it > FALL_THRESHOLD }
            val recentLowValues = recentValues.takeLast(3)
            val hasLowPeriod = recentLowValues.any { it < LOW_THRESHOLD }
            val timeIntervalOk = currentTime - lastFallTime > MIN_INTERVAL_MS

            if (hasHighPeak && hasLowPeriod && timeIntervalOk) {
                lastFallTime = currentTime
                recentValues.clear()
                onFallDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción
    }

    fun adjustSensitivity(sensitivity: Float) {
        FALL_THRESHOLD = when {
            sensitivity < 0.5f -> 15.0f
            sensitivity > 2.0f -> 30.0f
            else -> 20.0f * sensitivity
        }
    }
}
