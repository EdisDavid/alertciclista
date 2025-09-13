package com.example.alertciclista

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class EmergencySMS(private val context: Context) {

    private val smsManager = SmsManager.getDefault()
    private val TAG = "ALERTCICLISTA_SMS"

    /**
     * Envía un SMS de emergencia con ubicación al contacto proporcionado.
     */
    fun sendFallAlert(phoneNumber: String, latitude: Double, longitude: Double) {
        // 1️⃣ Verificar permiso de SMS
        if (!hasSmsPermission()) {
            showError("⚠️ Sin permisos para enviar SMS. Activa SEND_SMS en ajustes.")
            Log.e(TAG, "Permiso SEND_SMS no concedido")
            return
        }

        // 2️⃣ Validar número
        if (phoneNumber.isBlank()) {
            showError("No hay contacto de emergencia configurado")
            Log.e(TAG, "Número vacío")
            return
        }

        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        if (cleanNumber.length < 7) {
            showError("Número de teléfono inválido: $cleanNumber")
            Log.e(TAG, "Número inválido: $cleanNumber")
            return
        }

        // 3️⃣ Construir mensaje
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

        // 4️⃣ Enviar SMS
        try {
            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
                Log.d(TAG, "SMS multipart enviado a $cleanNumber")
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                Log.d(TAG, "SMS simple enviado a $cleanNumber")
            }

            Toast.makeText(
                context,
                "🚨 ALERTA ENVIADA\nContacto: ${cleanNumber.takeLast(4).padStart(7, '*')}\nUbicación compartida",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            showError("❌ Error al enviar alerta: ${e.localizedMessage ?: "desconocido"}")
            Log.e(TAG, "Error enviando SMS: ${e.localizedMessage}", e)
        }
    }

    /**
     * Verifica si la app tiene permiso SEND_SMS
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Muestra error por pantalla
     */
    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, message)
    }
}
