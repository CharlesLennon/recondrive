package com.recondrive.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.recondrive.app.ReconApplication
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.service.ExplorationTrackingService
import com.recondrive.app.ui.screens.FullScreenUpdateOverlay
import com.recondrive.app.ui.screens.InteractiveMapScreen
import com.recondrive.app.ui.screens.LocationTimelineSheet
import com.recondrive.app.ui.screens.OfflineRegionsDialog
import com.recondrive.app.ui.screens.SkinSelectorDialog
import com.recondrive.app.ui.screens.TacticalHudScreen
import com.recondrive.app.ui.screens.TacticalStartupUpdateDialog
import com.recondrive.app.ui.theme.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mobile Handset Main Activity
 * Hosts the tactical HUD and manages GPS telemetry ingestion into the Fog of War engine.
 * Coordinates with ExplorationTrackingService to provide 24/7 territory unmasking via persistent notification.
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
            checkBackgroundLocationAndStartService()
        } else {
            Toast.makeText(this, "Location permission required for Tactical Radar", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with service start regardless of background location prompt result
        startExplorationServiceIfEnabled()
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

        // Synchronize UI telemetry with the 24/7 background service location stream
        lifecycleScope.launch {
            ExplorationTrackingService.currentLocation.collect { loc ->
                loc?.let {
                    currentLat = it.latitude
                    currentLon = it.longitude
                    currentSpeedKmh = it.speed * 3.6f
                    if (it.hasBearing()) {
                        currentHeading = it.bearing
                    }
                }
            }
        }

        checkAndRequestPermissions()

        // Coordinated Startup Network & Updates Check (App Releases & UK OSM Data)
        if (ReconApplication.instance.osmMapUpdateManager.isNetworkConnected()) {
            Timber.i("Startup network online: Auditing App and UK OSM Map updates")
            ReconApplication.instance.updateManager.checkForUpdates(isManual = false)
            ReconApplication.instance.osmMapUpdateManager.checkForMapUpdates(isManual = false)
        } else {
            Timber.i("Startup: No network connection detected. Operating in full tactical offline mode.")
        }

        setContent {
            ReconDriveTheme {
                val stats by ReconApplication.instance.fogGridManager.stats.collectAsState()
                val appUpdateState by ReconApplication.instance.updateManager.updateState.collectAsState()
                val mapUpdateState by ReconApplication.instance.osmMapUpdateManager.mapUpdateState.collectAsState()
                val deviceHeading by ReconApplication.instance.deviceOrientationManager.deviceHeading.collectAsState()

                var selectedTab by remember { mutableIntStateOf(0) } // 0 = HUD, 1 = MAP
                var showSkinsDialog by remember { mutableStateOf(false) }
                var showOfflineDialog by remember { mutableStateOf(false) }
                var showTimelineSheet by remember { mutableStateOf(false) }
                var hasDismissedStartupSync by remember { mutableStateOf(false) }
                var targetWaypoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
                var targetWaypointName by remember { mutableStateOf<String?>(null) }

                // Full-Screen Tactical Update Overlay when downloading, ready to install, or installing
                val isFullScreenUpdateActive = appUpdateState is com.recondrive.app.update.UpdateState.Downloading ||
                    appUpdateState is com.recondrive.app.update.UpdateState.ReadyToInstall ||
                    appUpdateState is com.recondrive.app.update.UpdateState.Installing

                if (isFullScreenUpdateActive) {
                    FullScreenUpdateOverlay(
                        updateManager = ReconApplication.instance.updateManager,
                        updateState = appUpdateState,
                        onDismiss = { ReconApplication.instance.updateManager.resetState() }
                    )
                }

                // Check if either an app update or map update is available and not yet dismissed
                val isStartupSyncAvailable = !isFullScreenUpdateActive && !hasDismissedStartupSync && (
                    appUpdateState is com.recondrive.app.update.UpdateState.UpdateAvailable ||
                    mapUpdateState is com.recondrive.app.map.MapUpdateState.UpdateAvailable
                )

                if (isStartupSyncAvailable) {
                    TacticalStartupUpdateDialog(
                        appUpdateManager = ReconApplication.instance.updateManager,
                        osmMapUpdateManager = ReconApplication.instance.osmMapUpdateManager,
                        onDismiss = { hasDismissedStartupSync = true }
                    )
                }

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

                if (showTimelineSheet) {
                    LocationTimelineSheet(
                        timelineManager = ReconApplication.instance.locationTimelineManager,
                        currentSpeedKmh = currentSpeedKmh,
                        currentGpsBearing = currentHeading,
                        currentDeviceHeading = deviceHeading,
                        onJumpToLocation = { jLat, jLon ->
                            targetWaypoint = jLat to jLon
                            targetWaypointName = "Timeline Fix"
                            selectedTab = 1
                            showTimelineSheet = false
                        },
                        onDismiss = { showTimelineSheet = false }
                    )
                }

                Scaffold(
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalObsidian)
                        ) {
                            NavigationBar(
                                containerColor = TacticalObsidian,
                                contentColor = ReconCyan,
                                tonalElevation = 0.dp
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

                            // App Version Badge on Nav Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp, top = 0.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = TacticalSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                    modifier = Modifier.clickable {
                                        ReconApplication.instance.updateManager.checkForUpdates(isManual = true)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(ReconGreen, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "RECONDRIVE v${com.recondrive.app.BuildConfig.VERSION_NAME}",
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
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
                                    deviceHeading = deviceHeading,
                                    currentLat = currentLat,
                                    currentLon = currentLon,
                                    onSearchClick = {
                                        selectedTab = 1 // Jump directly to interactive map
                                    },
                                    onOfflinePacksClick = {
                                        showOfflineDialog = true
                                    },
                                    onToggleBackgroundRadar = {
                                        toggleBackgroundRadar()
                                    },
                                    onOpenTimeline = {
                                        showTimelineSheet = true
                                    },
                                    onNavigateToDestination = { name, destLat, destLon ->
                                        targetWaypoint = destLat to destLon
                                        targetWaypointName = name
                                        selectedTab = 1
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Target locked: $name. Opening Tactical Map...",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                                    initialWaypoint = targetWaypoint,
                                    initialWaypointName = targetWaypointName,
                                    onNavigateToWaypoint = { wpLat, wpLon ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Target locked: %.4f°N, %.4f°W. Calculating route...".format(wpLat, kotlin.math.abs(wpLon)),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onOpenSkins = { showSkinsDialog = true },
                                    onOpenOfflinePacks = { showOfflineDialog = true },
                                    currentSpeedKmh = currentSpeedKmh,
                                    deviceHeading = deviceHeading,
                                    onOpenTimeline = { showTimelineSheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            checkBackgroundLocationAndStartService()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkBackgroundLocationAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) {
                try {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } catch (e: Exception) {
                    Timber.w(e, "Background location permission request note")
                }
            }
        }
        startExplorationServiceIfEnabled()
        startLocationUpdates()
    }

    private fun startExplorationServiceIfEnabled() {
        val isEnabled = ReconApplication.instance.fogPreferences.isBackgroundTrackingEnabled.value
        if (isEnabled) {
            Timber.i("Starting 24/7 background exploration tracking service")
            ExplorationTrackingService.start(this)
        }
    }

    private fun toggleBackgroundRadar() {
        val currentlyRunning = ExplorationTrackingService.isRunning.value
        if (currentlyRunning) {
            ExplorationTrackingService.stop(this)
            ReconApplication.instance.fogPreferences.setBackgroundTrackingEnabled(false)
            Toast.makeText(
                this,
                "24/7 Radar paused. Background fog removal stopped.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            ReconApplication.instance.fogPreferences.setBackgroundTrackingEnabled(true)
            ExplorationTrackingService.start(this)
            Toast.makeText(
                this,
                "24/7 Radar ACTIVE! Territory will uncover in background with ongoing notification.",
                Toast.LENGTH_LONG
            ).show()
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

        // Ingest into Location Timeline engine with active handset compass heading
        ReconApplication.instance.locationTimelineManager.recordTelemetryPoint(
            location = location,
            deviceHeading = ReconApplication.instance.deviceOrientationManager.deviceHeading.value
        )

        // Feed fog grid directly if background service is not currently active
        if (!ExplorationTrackingService.isRunning.value) {
            ReconApplication.instance.fogGridManager.processLocation(location)
        }
    }

    override fun onResume() {
        super.onResume()
        ReconApplication.instance.deviceOrientationManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        ReconApplication.instance.deviceOrientationManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // Note: ExplorationTrackingService is intentionally NOT stopped on activity destroy
        // so that fog continues to be unmasked when the app is closed or in background!
    }
}
