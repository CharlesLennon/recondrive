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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.recondrive.app.ReconApplication
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.ui.screens.InteractiveMapScreen
import com.recondrive.app.ui.screens.OfflineRegionsDialog
import com.recondrive.app.ui.screens.SkinSelectorDialog
import com.recondrive.app.ui.screens.TacticalHudScreen
import com.recondrive.app.ui.theme.ReconCyan
import com.recondrive.app.ui.theme.ReconDriveTheme
import com.recondrive.app.ui.theme.TacticalObsidian
import com.recondrive.app.ui.theme.TextSecondary
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
                var selectedTab by remember { mutableIntStateOf(0) } // 0 = HUD, 1 = MAP
                var showSkinsDialog by remember { mutableStateOf(false) }
                var showOfflineDialog by remember { mutableStateOf(false) }

                if (showSkinsDialog) {
                    SkinSelectorDialog(
                        skinManager = ReconApplication.instance.skinManager,
                        fogPreferences = ReconApplication.instance.fogPreferences,
                        onDismiss = { showSkinsDialog = false }
                    )
                }

                if (showOfflineDialog) {
                    OfflineRegionsDialog(
                        repository = ReconApplication.instance.offlineTileRepository,
                        onDismiss = { showOfflineDialog = false }
                    )
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = TacticalObsidian,
                            contentColor = ReconCyan
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "HUD",
                                        tint = if (selectedTab == 0) ReconCyan else TextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        text = "HUD",
                                        color = if (selectedTab == 0) ReconCyan else TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "MAP",
                                        tint = if (selectedTab == 1) ReconCyan else TextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        text = "MAP",
                                        color = if (selectedTab == 1) ReconCyan else TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = { showSkinsDialog = true },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "SKINS",
                                        tint = TextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        text = "SKINS",
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = { showOfflineDialog = true },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "OFFLINE",
                                        tint = TextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        text = "OFFLINE",
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTab) {
                            0 -> {
                                TacticalHudScreen(
                                    stats = stats,
                                    skinManager = ReconApplication.instance.skinManager,
                                    updateManager = ReconApplication.instance.updateManager,
                                    currentSpeedKmh = currentSpeedKmh,
                                    currentHeading = currentHeading,
                                    currentLat = currentLat,
                                    currentLon = currentLon,
                                    onSearchClick = {
                                        selectedTab = 1 // Jump directly to interactive map
                                    },
                                    onOfflinePacksClick = {
                                        showOfflineDialog = true
                                    }
                                )
                            }
                            1 -> {
                                InteractiveMapScreen(
                                    currentLat = currentLat,
                                    currentLon = currentLon,
                                    currentHeading = currentHeading,
                                    skinManager = ReconApplication.instance.skinManager,
                                    offlineTileRepository = ReconApplication.instance.offlineTileRepository,
                                    fogPreferences = ReconApplication.instance.fogPreferences,
                                    onNavigateToWaypoint = { wpLat, wpLon ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Target locked: %.4f°N, %.4f°W. Calculating route...".format(wpLat, kotlin.math.abs(wpLon)),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onOpenSkins = { showSkinsDialog = true },
                                    onOpenOfflinePacks = { showOfflineDialog = true }
                                )
                            }
                        }
                    }
                }
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
