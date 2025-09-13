package com.example.alertciclista

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlin.math.abs

// Clase que detecta caídas usando el acelerómetro
class FallDetector(
    context: Context,
    private val onFallDetected: () -> Unit // callback cuando se detecta caída
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Umbrales de sensibilidad
    private val NORMAL_SENSITIVITY_THRESHOLD = 25.0f
    private var fallThreshold = NORMAL_SENSITIVITY_THRESHOLD
    private val lowThreshold = 2.0f // valores bajos (inmovilidad tras impacto)
    private val minIntervalMs = 5000L // evita múltiples alertas en pocos segundos
    private val gravity = 9.81f // gravedad terrestre

    // Almacena últimos valores para análisis
    private var recentValues = mutableListOf<Float>()
    private val bufferSize = 10
    private var lastFallTime: Long = 0

    init {
        // Ajusta sensibilidad inicial
        adjustSensitivity(1.0f)
    }

    // Inicia la escucha del sensor
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // Detiene la escucha del sensor
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    // Se ejecuta cuando cambian los valores del acelerómetro
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Lecturas en los 3 ejes
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Magnitud total del movimiento
        val magnitude = sqrt(x * x + y * y + z * z)
        // Magnitud neta (quitando gravedad)
        val netMagnitude = abs(magnitude - gravity)

        // Guarda valores recientes
        recentValues.add(netMagnitude)
        if (recentValues.size > bufferSize) {
            recentValues.removeAt(0)
        }

        val currentTime = System.currentTimeMillis()

        // Se analiza solo si hay suficientes datos
        if (recentValues.size >= 5) {
            // Pico alto = posible impacto fuerte
            val hasHighPeak = recentValues.any { it > fallThreshold }

            // Valores bajos tras el impacto (posible quietud después de caída)
            val recentLowValues = recentValues.takeLast(3)
            val hasLowPeriod = recentLowValues.size == 3 && recentLowValues.all { it < lowThreshold }

            // Evita falsas alarmas si ya se envió recientemente
            val timeIntervalOk = currentTime - lastFallTime > minIntervalMs

            // Condición de caída: golpe fuerte + periodo de baja actividad + intervalo correcto
            if (hasHighPeak && hasLowPeriod && timeIntervalOk) {
                lastFallTime = currentTime
                recentValues.clear()
                onFallDetected() // dispara alerta de caída
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se usa
    }

    // Ajusta la sensibilidad del detector
    fun adjustSensitivity(sensitivity: Float) {
        fallThreshold = when {
            sensitivity < 0.5f -> NORMAL_SENSITIVITY_THRESHOLD - 5.0f
            sensitivity > 1.5f -> NORMAL_SENSITIVITY_THRESHOLD + 10.0f
            else -> NORMAL_SENSITIVITY_THRESHOLD * sensitivity
        }
        // Límite mínimo para evitar falsos positivos
        if (fallThreshold < 10.0f) fallThreshold = 10.0f
    }
}
