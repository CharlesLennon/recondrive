package com.recondrive.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.recondrive.app.ReconApplication
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.ui.screens.TacticalHudScreen
import com.recondrive.app.ui.theme.ReconDriveTheme
import timber.log.Timber

/**
 * Mobile Handset Main Activity
 * Hosts the tactical HUD and manages GPS telemetry ingestion into the Fog of War engine.
 */
class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentSpeedKmh by mutableFloatStateOf(0f)
    private var currentHeading by mutableFloatStateOf(0f)
    private var currentLat by mutableDoubleStateOf(51.5074)
    private var currentLon by mutableDoubleStateOf(-0.1278)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission required for Tactical Radar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    onNewLocation(location)
                }
            }
        }

        checkAndRequestPermissions()

        // Background query to GitHub Releases for newest APK updates
        ReconApplication.instance.updateManager.checkForUpdates(isManual = false)

        setContent {
            ReconDriveTheme {
                val stats by ReconApplication.instance.fogGridManager.stats.collectAsState()

                TacticalHudScreen(
                    stats = stats,
                    skinManager = ReconApplication.instance.skinManager,
                    updateManager = ReconApplication.instance.updateManager,
                    currentSpeedKmh = currentSpeedKmh,
                    currentHeading = currentHeading,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    onSearchClick = {
                        Toast.makeText(this, "Target Search: Enter destination in Android Auto or Handset", Toast.LENGTH_SHORT).show()
                    },
                    onOfflinePacksClick = {
                        val packs = ReconApplication.instance.offlineTileRepository.getDownloadedRegions()
                        Toast.makeText(this, "Local Offline Packs: ${packs.size} regions loaded", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
        Timber.i("Started high-accuracy tactical GPS telemetry ingestion")
    }

    private fun onNewLocation(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        currentSpeedKmh = location.speed * 3.6f
        if (location.hasBearing()) {
            currentHeading = location.bearing
        }

        // Push to Fog of War exploration engine
        ReconApplication.instance.fogGridManager.processLocation(location)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
