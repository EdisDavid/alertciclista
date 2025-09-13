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
    private val NORMAL_SENSITIVITY_THRESHOLD = 25.0f
    private var fallThreshold = NORMAL_SENSITIVITY_THRESHOLD
    private val lowThreshold = 2.0f
    private val minIntervalMs = 5000L
    private val gravity = 9.81f
    private var recentValues = mutableListOf<Float>()
    private val bufferSize = 10
    private var lastFallTime: Long = 0

    init {
        adjustSensitivity(1.0f)
    }

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
        val netMagnitude = abs(magnitude - gravity)

        recentValues.add(netMagnitude)
        if (recentValues.size > bufferSize) {
            recentValues.removeAt(0)
        }

        val currentTime = System.currentTimeMillis()

        if (recentValues.size >= 5) {
            val hasHighPeak = recentValues.any { it > fallThreshold }

            val recentLowValues = recentValues.takeLast(3)
            val hasLowPeriod = recentLowValues.size == 3 && recentLowValues.all { it < lowThreshold }

            val timeIntervalOk = currentTime - lastFallTime > minIntervalMs

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
        fallThreshold = when {
            sensitivity < 0.5f -> NORMAL_SENSITIVITY_THRESHOLD - 5.0f
            sensitivity > 1.5f -> NORMAL_SENSITIVITY_THRESHOLD + 10.0f
            else -> NORMAL_SENSITIVITY_THRESHOLD * sensitivity
        }
        if (fallThreshold < 10.0f) fallThreshold = 10.0f
    }
}
