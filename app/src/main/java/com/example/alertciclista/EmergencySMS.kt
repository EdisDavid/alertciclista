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

class EmergencySMS(private val context: Context) {

    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.example.alertciclista.SMS_SENT"
    }

    fun sendFallAlert(phoneNumber: String, latitude: Double, longitude: Double) {
        if (!hasSmsPermission()) {
            showError("⚠️ Sin permisos para enviar SMS. Activa SEND_SMS en ajustes.")
            return
        }

        if (phoneNumber.isBlank()) {
            showError("No hay contacto de emergencia configurado")
            return
        }

        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        var finalNumber = cleanNumber
        if (!finalNumber.startsWith("+") && finalNumber.length == 9 && finalNumber.startsWith("9")) {
            finalNumber = "+51$finalNumber"
        }

        if (finalNumber.length < 7) {
            showError("Número de teléfono inválido: $finalNumber")
            return
        }

        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val googleMapsUrl = "https://maps.google.com/maps?q=$latitude,$longitude"

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

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        try {
            val parts = smsManager.divideMessage(message)
            val numParts = parts.size

            val sentPIs = ArrayList<PendingIntent>()
            for (i in 0 until numParts) {
                val sentIntent = Intent(ACTION_SMS_SENT)
                sentPIs.add(PendingIntent.getBroadcast(context, System.currentTimeMillis().toInt() + i, sentIntent, pendingIntentFlags))
            }

            if (numParts > 1) {
                smsManager.sendMultipartTextMessage(finalNumber, null, parts, sentPIs, null /* deliveredPIs */)
            } else {
                smsManager.sendTextMessage(finalNumber, null, parts[0], sentPIs[0], null /* deliveredPI */)
            }

        } catch (e: Exception) {
            showError("❌ Error al iniciar envío de alerta: ${e.localizedMessage ?: "desconocido"}")
        }
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

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
                    Toast.makeText(context, "❌ Error desconocido al enviar SMS. Código: $resultCode", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
