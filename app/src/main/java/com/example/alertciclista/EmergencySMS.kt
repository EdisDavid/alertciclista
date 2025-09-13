package com.example.alertciclista

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

// Clase que maneja el envío de SMS de emergencia
class EmergencySMS(private val context: Context) {

    // Obtiene SmsManager según la versión de Android
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    companion object {
        // Acción usada para identificar el resultado del envío
        const val ACTION_SMS_SENT = "com.example.alertciclista.SMS_SENT"
    }

    // Envía un SMS con ubicación y alerta de caída
    fun sendFallAlert(phoneNumber: String, latitude: Double, longitude: Double) {
        // Verifica permisos SMS
        if (!hasSmsPermission()) {
            showError("⚠️ Sin permisos para enviar SMS. Activa SEND_SMS en ajustes.")
            return
        }

        // Verifica si hay número configurado
        if (phoneNumber.isBlank()) {
            showError("No hay contacto de emergencia configurado")
            return
        }

        // Limpia y corrige el número (ej: añade +51 si es Perú)
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        var finalNumber = cleanNumber
        if (!finalNumber.startsWith("+") && finalNumber.length == 9 && finalNumber.startsWith("9")) {
            finalNumber = "+51$finalNumber"
        }

        // Valida número
        if (finalNumber.length < 7) {
            showError("Número de teléfono inválido: $finalNumber")
            return
        }

        // Fecha/hora actual
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        // URL de Google Maps con ubicación
        val googleMapsUrl = "https://maps.google.com/maps?q=$latitude,$longitude"

        // Mensaje de alerta
        val message = """
            🚨 ALERTA DE EMERGENCIA - CICLISTA 🚨

            ⚠️ CAÍDA DETECTADA ⚠️

            📍 UBICACIÓN EXACTA:
            Lat: $latitude
            Lon: $longitude

            🗺️ Ver en Google Maps: $googleMapsUrl

            Hora: $timestamp

            ℹ️ Alerta automática enviada por AlertCiclista
        """.trimIndent()

        // Flags necesarios para PendingIntent
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        try {
            // Divide mensaje si es largo
            val parts = smsManager.divideMessage(message)
            val numParts = parts.size

            // Crea lista de PendingIntents para cada parte del SMS
            val sentPIs = ArrayList<PendingIntent>()
            for (i in 0 until numParts) {
                val sentIntent = Intent(ACTION_SMS_SENT)
                sentPIs.add(
                    PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt() + i,
                        sentIntent,
                        pendingIntentFlags
                    )
                )
            }

            // Enviar SMS simple o multipart
            if (numParts > 1) {
                smsManager.sendMultipartTextMessage(finalNumber, null, parts, sentPIs, null)
            } else {
                smsManager.sendTextMessage(finalNumber, null, parts[0], sentPIs[0], null)
            }

        } catch (e: Exception) {
            // Si algo falla al enviar
            showError("❌ Error al iniciar envío de alerta: ${e.localizedMessage ?: "desconocido"}")
        }
    }

    // Verifica permiso de SMS
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Muestra error en pantalla (Toast)
    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

// Receptor que escucha si el SMS fue enviado o falló
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val resultCode = resultCode

        if (EmergencySMS.ACTION_SMS_SENT == action) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Toast.makeText(context, "✅ Alerta SMS enviada.", Toast.LENGTH_LONG).show()
                }
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                    Toast.makeText(context, "❌ Fallo genérico al enviar SMS.", Toast.LENGTH_LONG).show()
                }
                SmsManager.RESULT_ERROR_NO_SERVICE -> {
                    Toast.makeText(context, "❌ Sin servicio para enviar SMS.", Toast.LENGTH_LONG).show()
                }
                SmsManager.RESULT_ERROR_NULL_PDU -> {
                    Toast.makeText(context, "❌ Error PDU nulo al enviar SMS.", Toast.LENGTH_LONG).show()
                }
                SmsManager.RESULT_ERROR_RADIO_OFF -> {
                    Toast.makeText(context, "❌ Radio apagada, no se puede enviar SMS.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(
                        context,
                        "❌ Error desconocido al enviar SMS. Código: $resultCode",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
