package com.example.alertciclista

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {

    private lateinit var map: MapView
    private lateinit var emergencyContactText: TextView
    private lateinit var addContactButton: FloatingActionButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPref: SharedPreferences
    private lateinit var fallDetector: FallDetector
    private lateinit var emergencySMS: EmergencySMS
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLocation: GeoPoint? = null

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val PICK_CONTACT_REQUEST = 101
        private val DEFAULT_LOCATION = GeoPoint(-12.0464, -77.0428)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )

        setContentView(R.layout.activity_main)

        initializeViews()
        initializeServices()
        setupMap()
        loadSavedContact()
        setupEventListeners()
        checkAndRequestPermissions()
    }

    private fun initializeViews() {
        map = findViewById(R.id.map)
        emergencyContactText = findViewById(R.id.emergencyContactText)
        addContactButton = findViewById(R.id.addContactButton)
    }

    private fun initializeServices() {
        sharedPref = getSharedPreferences("CiclistaPrefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        emergencySMS = EmergencySMS(this)
        fallDetector = FallDetector(this) { handleFallDetected() }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.controller.setZoom(15.0)
        map.controller.setCenter(DEFAULT_LOCATION)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        myLocationOverlay.setDrawAccuracyEnabled(true)
        map.overlays.add(myLocationOverlay)
    }

    private fun loadSavedContact() {
        val contactName = sharedPref.getString("contact_name", null)
        emergencyContactText.text = if (contactName != null) {
            "📞 Contacto: $contactName"
        } else {
            "⚠️ Sin contacto de emergencia"
        }
    }

    private fun setupEventListeners() {
        addContactButton.setOnClickListener { pickContactFromPhone() }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startLocationUpdates()
            startFallDetection()
        } else {
            val needExplanation = permissionsToRequest.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            if (needExplanation) {
                AlertDialog.Builder(this)
                    .setTitle("Permisos necesarios")
                    .setMessage(
                        "Para que AlertCiclista funcione correctamente, se requieren permisos de ubicación, SMS y contactos. " +
                                "Por favor acepta todos los permisos."
                    )
                    .setPositiveButton("Aceptar") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            permissionsToRequest.toTypedArray(),
                            PERMISSION_REQUEST_CODE
                        )
                    }
                    .setCancelable(false)
                    .show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).apply {
            setMinUpdateIntervalMillis(1000L)
            setMaxUpdateDelayMillis(5000L)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun startFallDetection() {
        fallDetector.start()
        Toast.makeText(this, "🛡️ Protección activa - DeteCaídas ON", Toast.LENGTH_SHORT).show()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            currentLocation = geoPoint

            if (map.zoomLevelDouble < 10.0) {
                map.controller.setZoom(18.0)
                map.controller.setCenter(geoPoint)
            }

            scope.launch {
                withContext(Dispatchers.IO) {
                    sharedPref.edit().apply {
                        putString("last_lat", location.latitude.toString())
                        putString("last_lon", location.longitude.toString())
                        putLong("last_location_time", System.currentTimeMillis())
                        apply()
                    }
                }
            }
        }
    }

    private fun handleFallDetected() {
        val contactNumber = sharedPref.getString("contact_number", null)
        val location = currentLocation ?: myLocationOverlay.myLocation

        if (contactNumber == null) {
            Toast.makeText(
                this,
                "⚠️ CAÍDA DETECTADA pero no hay contacto configurado!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!emergencySMS.hasSmsPermission()) {
            Toast.makeText(
                this,
                "⚠️ No se puede enviar SMS: permiso SEND_SMS denegado",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (location == null) {
            emergencySMS.sendFallAlert(contactNumber, 0.0, 0.0) // Envía 0,0 si la ubicación no está disponible
        } else {
            emergencySMS.sendFallAlert(contactNumber, location.latitude, location.longitude)
        }

        Toast.makeText(
            this,
            "🚨 CAÍDA DETECTADA!\n📱 Alerta enviada al contacto", // Mensaje más genérico, ya que el SMS se confirma por su propio Toast
            Toast.LENGTH_LONG
        ).show()
    }

    private fun pickContactFromPhone() {
        if (!hasContactsPermission()) {
            Toast.makeText(this, "Se necesita permiso para acceder a contactos", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_REQUEST)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { contactUri -> scope.launch { processContactSelection(contactUri) } }
        }
    }

    private suspend fun processContactSelection(contactUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                        if (nameIndex >= 0 && numberIndex >= 0) {
                            val name = cursor.getString(nameIndex) ?: "Desconocido"
                            val number = cursor.getString(numberIndex) ?: ""

                            sharedPref.edit().apply {
                                putString("contact_name", name)
                                putString("contact_number", number)
                                apply()
                            }

                            withContext(Dispatchers.Main) {
                                emergencyContactText.text = "📞 Contacto: $name"
                                Toast.makeText(this@MainActivity, "✅ Contacto configurado: $name", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al seleccionar contacto: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                startLocationUpdates()
                startFallDetection()
                Toast.makeText(this, "✅ Permisos concedidos - App lista", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "⚠️ Algunos permisos fueron denegados. La app no puede enviar SMS ni detectar ubicación correctamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (hasLocationPermission()) fallDetector.start() 
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        fallDetector.stop()
    }

    @Suppress("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        fallDetector.stop()
        if (hasLocationPermission()) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: SecurityException) {}
        }
        scope.cancel()
    }
}
