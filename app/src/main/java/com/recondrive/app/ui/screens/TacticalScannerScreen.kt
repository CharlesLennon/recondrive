package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.map.OsmRoadSegment
import com.recondrive.app.map.OsmRoadType
import com.recondrive.app.map.OsmTileManager
import com.recondrive.app.map.UkBoundingBox
import com.recondrive.app.map.UkOsmDataEngine
import com.recondrive.app.scanner.*
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Tactical SIGINT RF Spectrum & Live Wardrive Scanner Screen.
 * Features an expansive interactive live radar map showing real OpenStreetMap raster tiles,
 * road vectors, course-up map rotation relative to device heading, ultra close-up zooms (max 50m: 10m/20m/35m/50m),
 * CSI & Beamforming Human Presence Sensing, device location history (breadcrumbs),
 * saved targets with custom number plates, door numbers, group tags, and background scanning with pause controls.
 */
@Composable
fun TacticalScannerScreen(
    scannerManager: TacticalRfScannerManager = com.recondrive.app.ReconApplication.instance.rfScannerManager,
    csiSensingEngine: TacticalCsiSensingEngine = com.recondrive.app.ReconApplication.instance.csiSensingEngine,
    osmTileManager: OsmTileManager = com.recondrive.app.ReconApplication.instance.osmTileManager,
    currentLat: Double = 50.8555,
    currentLon: Double = -0.2449,
    currentHeading: Float = 0f,
    deviceHeading: Float = currentHeading,
    currentSpeedKmh: Float = 0f,
    onNavigateToLocation: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
) {
    val detectedTargets by scannerManager.detectedTargets.collectAsState()
    val isScanning by scannerManager.isScanning.collectAsState()
    val isScannerPaused by scannerManager.isScannerPaused.collectAsState()
    val isSimulation by scannerManager.isSimulationEnabled.collectAsState()
    val csiSnapshot by csiSensingEngine.snapshot.collectAsState()
    val isPersonDetectionEnabled by csiSensingEngine.isEnabled.collectAsState()
    val tileRevision by osmTileManager.revision.collectAsState()
    val baroTelemetry by scannerManager.barometerEngine.telemetry.collectAsState()
    val sirenTelemetry by scannerManager.sirenEngine.telemetry.collectAsState()
    val imuTelemetry by scannerManager.imuEngine.telemetry.collectAsState()
    val nfcTelemetry by scannerManager.nfcManager.telemetry.collectAsState()
    val gnssTelemetry by scannerManager.gnssEngine.telemetry.collectAsState()
    val wifiRttTelemetry by scannerManager.wifiRttEngine.telemetry.collectAsState()

    var selectedFilterCategory by remember { mutableStateOf<RfCategory?>(null) } // null = ALL
    var filterSavedOnly by remember { mutableStateOf(false) }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    var editingTarget by remember { mutableStateOf<DetectedRfTarget?>(null) }
    var viewingCctvCamera by remember { mutableStateOf<PublicCctvCamera?>(null) }

    // Map viewport state
    var cameraLat by remember { mutableDoubleStateOf(currentLat) }
    var cameraLon by remember { mutableDoubleStateOf(currentLon) }
    var isFollowingVehicle by remember { mutableStateOf(true) }

    // Range selection: Expansive Tactical Scanning (25m, 50m, 100m, 250m, 500m, 1000m / 1km max)
    var selectedRangeMeters by remember { mutableFloatStateOf(500f) }
    var dynamicZoom by remember { mutableFloatStateOf(14.8f) }

    val onZoomChange: (Float) -> Unit = { zoomFactor ->
        if (zoomFactor > 0f && !zoomFactor.isNaN() && !zoomFactor.isInfinite()) {
            val delta = log2(zoomFactor.toDouble()).toFloat()
            if (!delta.isNaN() && !delta.isInfinite()) {
                val newZoom = (dynamicZoom + delta).coerceIn(12.0f, 21.0f)
                dynamicZoom = newZoom
                selectedRangeMeters = when {
                    newZoom >= 19.0f -> 25f
                    newZoom >= 17.8f -> 50f
                    newZoom >= 16.8f -> 100f
                    newZoom >= 15.5f -> 250f
                    newZoom >= 14.2f -> 500f
                    else -> 1000f
                }
            }
        }
    }

    val onSelectRangePreset: (Float) -> Unit = { rangeM ->
        selectedRangeMeters = rangeM
        dynamicZoom = when (rangeM) {
            25f -> 19.5f
            50f -> 18.2f
            100f -> 17.2f
            250f -> 16.0f
            500f -> 14.8f
            1000f -> 13.5f
            else -> 14.8f
        }
    }

    // Sync user location with scanner manager and anchor targets relative to vehicle
    LaunchedEffect(currentLat, currentLon, currentHeading) {
        scannerManager.updateLastKnownLocation(currentLat, currentLon, currentHeading)
    }

    // Follow vehicle position when not manually panned
    LaunchedEffect(currentLat, currentLon, isFollowingVehicle) {
        if (isFollowingVehicle) {
            cameraLat = currentLat
            cameraLon = currentLon
        }
    }

    // Automatically ensure scanning is started when entering this tab
    LaunchedEffect(Unit) {
        scannerManager.startScanning()
    }

    // Strict range filtering: only targets within selectedRangeMeters
    val targetsInRange = remember(detectedTargets, selectedRangeMeters, currentLat, currentLon) {
        detectedTargets.filter { target ->
            val dist = if (target.detectedLat != null && target.detectedLon != null) {
                val dLat = (target.detectedLat - currentLat) * 111320.0
                val dLon = (target.detectedLon - currentLon) * 111320.0 * cos(Math.toRadians(currentLat))
                hypot(dLat, dLon)
            } else {
                target.estimatedDistanceMeters.toDouble()
            }
            dist <= selectedRangeMeters
        }
    }

    val vehicleCount = targetsInRange.count { it.category == RfCategory.VEHICLE }
    val keyCount = targetsInRange.count { it.category == RfCategory.CAR_KEY }
    val cctvCount = targetsInRange.count { it.category == RfCategory.CCTV_SURVEILLANCE }
    val droneCount = targetsInRange.count { it.category == RfCategory.DRONE_UAV }
    val cellCount = targetsInRange.count { it.category == RfCategory.CELL_TOWER }
    val wifiCount = targetsInRange.count { it.category == RfCategory.ACCESS_POINT }
    val savedCount = targetsInRange.count { it.savedMetadata != null }

    val filteredTargets = remember(targetsInRange, selectedFilterCategory, filterSavedOnly) {
        targetsInRange.filter { target ->
            val matchCategory = selectedFilterCategory == null || target.category == selectedFilterCategory
            val matchSaved = !filterSavedOnly || target.savedMetadata != null
            matchCategory && matchSaved
        }
    }

    // Speed in MPH
    val speedMph = remember(currentSpeedKmh) { (currentSpeedKmh * 0.621371f).roundToInt() }

    // Edit Intel Dialog
    editingTarget?.let { targetToEdit ->
        EditDeviceIntelDialog(
            target = targetToEdit,
            repository = scannerManager.savedDeviceRepository,
            soundManager = scannerManager.soundManager,
            onDismiss = { editingTarget = null }
        )
    }

    // Public CCTV Live Stream Viewer Dialog
    viewingCctvCamera?.let { cam ->
        PublicCctvLiveViewerDialog(
            camera = cam,
            onDismiss = { viewingCctvCamera = null },
            onNavigate = {
                onNavigateToLocation(cam.name, cam.latitude, cam.longitude)
                viewingCctvCamera = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // =====================================================================
        // 1. TOP STATUS ROW (SIGINT Scanner Title, Pause/Resume, Demo Toggle)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIGINT RF SCANNER // SPECTRUM AUDIT",
                    color = ReconCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isScannerPaused) "SCANNER PAUSED (BACKGROUND IDLE)" else "24/7 LIVE PROXIMITY ACTIVE • WI-FI 2.4/5GHz • BLE • BT • P2P • CELL • MAD",
                    color = if (isScannerPaused) ReconAmber else TextSecondary,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Pause / Resume Toggle Button
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isScannerPaused) ReconAmber else ReconGreen),
                modifier = Modifier.clickable { scannerManager.togglePause() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isScannerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isScannerPaused) "Resume" else "Pause",
                        tint = if (isScannerPaused) ReconAmber else ReconGreen,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = if (isScannerPaused) "PAUSED" else "ACTIVE",
                        color = if (isScannerPaused) ReconAmber else ReconGreen,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(5.dp))

            // Live / Demo Mode Toggle Pill
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSimulation) ReconAmber else ReconGreen),
                modifier = Modifier.clickable { scannerManager.toggleSimulation() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSimulation) ReconAmber else ReconGreen)
                    )
                    Text(
                        text = if (isSimulation) "DEMO" else "LIVE",
                        color = if (isSimulation) ReconAmber else ReconGreen,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(5.dp))

            // Rescan Sweep Button
            IconButton(
                onClick = { scannerManager.triggerImmediateSweep(currentLat, currentLon) },
                modifier = Modifier
                    .size(28.dp)
                    .background(TacticalSurface, CircleShape)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sweep Spectrum",
                    tint = ReconCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =====================================================================
        // 2. QUICK THREAT TELEMETRY PILLS (Cars, CCTV, Drone, Saved)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TelemetryThreatBadge(
                label = "KEYS",
                count = keyCount,
                color = Color(0xFFFFD600),
                icon = Icons.Default.Key,
                selected = selectedFilterCategory == RfCategory.CAR_KEY && !filterSavedOnly,
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = if (selectedFilterCategory == RfCategory.CAR_KEY) null else RfCategory.CAR_KEY
                },
                modifier = Modifier.weight(1f)
            )

            TelemetryThreatBadge(
                label = "CARS",
                count = vehicleCount,
                color = ReconCyan,
                icon = Icons.Default.DirectionsCar,
                selected = selectedFilterCategory == RfCategory.VEHICLE && !filterSavedOnly,
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = if (selectedFilterCategory == RfCategory.VEHICLE) null else RfCategory.VEHICLE
                },
                modifier = Modifier.weight(1f)
            )

            TelemetryThreatBadge(
                label = "CCTV",
                count = cctvCount,
                color = Color(0xFFFF5252),
                icon = Icons.Default.Videocam,
                selected = selectedFilterCategory == RfCategory.CCTV_SURVEILLANCE && !filterSavedOnly,
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = if (selectedFilterCategory == RfCategory.CCTV_SURVEILLANCE) null else RfCategory.CCTV_SURVEILLANCE
                },
                modifier = Modifier.weight(1f)
            )

            if (savedCount > 0) {
                TelemetryThreatBadge(
                    label = "SAVED",
                    count = savedCount,
                    color = ReconAmber,
                    icon = Icons.Default.Star,
                    selected = filterSavedOnly,
                    onClick = {
                        filterSavedOnly = !filterSavedOnly
                        selectedFilterCategory = null
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (cellCount > 0) {
                TelemetryThreatBadge(
                    label = "CELL",
                    count = cellCount,
                    color = Color(0xFF00E5FF),
                    icon = Icons.Default.CellTower,
                    selected = selectedFilterCategory == RfCategory.CELL_TOWER && !filterSavedOnly,
                    onClick = {
                        filterSavedOnly = false
                        selectedFilterCategory = if (selectedFilterCategory == RfCategory.CELL_TOWER) null else RfCategory.CELL_TOWER
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (droneCount > 0) {
                TelemetryThreatBadge(
                    label = "DRONE",
                    count = droneCount,
                    color = Color(0xFFBA68C8),
                    icon = Icons.Default.Flight,
                    selected = selectedFilterCategory == RfCategory.DRONE_UAV && !filterSavedOnly,
                    onClick = {
                        filterSavedOnly = false
                        selectedFilterCategory = if (selectedFilterCategory == RfCategory.DRONE_UAV) null else RfCategory.DRONE_UAV
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            TelemetryThreatBadge(
                label = "TOTAL",
                count = targetsInRange.size,
                color = ReconGreen,
                icon = Icons.Default.Sensors,
                selected = selectedFilterCategory == null && !filterSavedOnly,
                onClick = {
                    selectedFilterCategory = null
                    filterSavedOnly = false
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =====================================================================
        // 2B. CSI & BEAMFORMING BIOMETRIC SENSING (HUMAN PRESENCE DETECTION)
        // =====================================================================
        if (!isPersonDetectionEnabled) {
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "BIOMETRIC PERSON DETECTION: OFF",
                            color = TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        color = ReconCyan.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        modifier = Modifier.clickable { csiSensingEngine.toggleSensing() }
                    ) {
                        Text(
                            text = "TURN ON",
                            color = ReconCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        } else {
            val bioColor = when (csiSnapshot.presenceState) {
                HumanPresenceState.ACTIVE_HUMAN_TRANSIT -> ReconAmber
                HumanPresenceState.RESPIRATION_MICRO_MOTION -> ReconGreen
                HumanPresenceState.VACANT -> Color(0xFF1E3A4C)
            }

            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, bioColor.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(bioColor.copy(alpha = 0.18f))
                                    .border(1.dp, bioColor.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (csiSnapshot.presenceState) {
                                        HumanPresenceState.ACTIVE_HUMAN_TRANSIT -> Icons.Default.DirectionsWalk
                                        HumanPresenceState.RESPIRATION_MICRO_MOTION -> Icons.Default.Air
                                        HumanPresenceState.VACANT -> Icons.Default.PersonOutline
                                    },
                                    contentDescription = null,
                                    tint = if (csiSnapshot.presenceState == HumanPresenceState.VACANT) TextSecondary else bioColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "CSI & BFI BIOMETRIC SENSING",
                                        color = ReconCyan,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "• ${csiSnapshot.perturbationIndexPercent}% PERTURBATION",
                                        color = bioColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = csiSnapshot.statusSummary,
                                    color = if (csiSnapshot.presenceState == HumanPresenceState.VACANT) TextSecondary else bioColor,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Calibrate noise floor button
                            Surface(
                                color = TacticalObsidian,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                modifier = Modifier.clickable { csiSensingEngine.calibrateNoiseFloor() }
                            ) {
                                Text(
                                    text = "CALIBRATE",
                                    color = TextSecondary,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            // Turn Off button
                            Surface(
                                color = TacticalObsidian,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                modifier = Modifier.clickable { csiSensingEngine.toggleSensing() }
                            ) {
                                Text(
                                    text = "TURN OFF",
                                    color = Color(0xFFFF5252),
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Live Multi-Path Perturbation Waveform Canvas (Subcarrier ripple)
                    Spacer(modifier = Modifier.height(3.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TacticalObsidian)
                    ) {
                        val samples = csiSnapshot.waveformSamples
                        if (samples.size >= 2) {
                            val stepX = size.width / (samples.size - 1)
                            val midY = size.height / 2f
                            val path = Path()

                            for ((idx, sample) in samples.withIndex()) {
                                val x = idx * stepX
                                val y = midY - (sample * (midY * 0.85f))
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(path = path, color = bioColor, style = Stroke(width = 1.4f))
                        }
                    }
                }
            }
        }

        // =====================================================================
        // UWB & OPTICAL NIR INFRARED DETECTION STATUS BANNER
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD600))
                    )
                    Text(
                        text = "UWB DIGITAL KEY: CH5/9 ACTIVE",
                        color = Color(0xFFFFD600),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252))
                    )
                    Text(
                        text = "NIR 850/940nm OPTICS: SENSING",
                        color = Color(0xFFFF5252),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // =====================================================================
        // EMERGENCY SIREN DOPPLER DIRECTION FINDER (POLICE / EMS EARLY WARNING)
        // =====================================================================
        if (sirenTelemetry.isSirenDetected) {
            Surface(
                color = Color(0xFFD50000).copy(alpha = 0.28f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF1744)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = sirenTelemetry.alertMessage,
                            color = Color(0xFFFF5252),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "DOPPLER CONFIDENCE: ${sirenTelemetry.confidencePercent}% • ${if (sirenTelemetry.isApproaching) "APPROACHING (INCOMING) • YIELD RIGHT OF WAY" else "RECEDING (DEPARTING)"} • %.0f Hz".format(sirenTelemetry.peakFrequencyHz),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // =====================================================================
        // TACTICAL MULTI-SENSOR SUITE (BARO, IMU, GNSS, NFC, RTT)
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Barometer (Floor & Cabin)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (baroTelemetry.isCabinPressureSpike) "⚠️ CABIN SHOCK" else "FLOOR ${baroTelemetry.parkingFloor} (${"%.0f".format(baroTelemetry.currentHpa)}hPa)",
                        color = if (baroTelemetry.isCabinPressureSpike) Color(0xFFFF5252) else ReconCyan,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // IMU G-Force & Launch
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val gText = if (imuTelemetry.isDrifting) "DRIFT ${"%.0f".format(imuTelemetry.driftAngleDeg)}°" else "${"%.2f".format(imuTelemetry.lateralG)}G"
                    Text(
                        text = "$gText • ${imuTelemetry.launchTimerState.take(8)}",
                        color = if (imuTelemetry.isDrifting) Color(0xFFFFD600) else ReconGreen,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // GNSS Satellites & Anti-Spoof
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val satText = if (gnssTelemetry.isSpoofingSuspected) "⚠️ SPOOF" else if (gnssTelemetry.isJammingSuspected) "🚨 JAM" else "${gnssTelemetry.satellitesUsedInFix}/${gnssTelemetry.totalSatellitesInView} SATS"
                    Text(
                        text = satText,
                        color = if (gnssTelemetry.isSpoofingSuspected || gnssTelemetry.isJammingSuspected) Color(0xFFFF5252) else ReconCyan,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // NFC 13.56 MHz
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "NFC 13.56M",
                        color = if (nfcTelemetry.isNfcEnabled) Color(0xFFFFD600) else TextSecondary,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Wi-Fi RTT 802.11mc
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (wifiRttTelemetry.closestDistanceMeters != null) "RTT: ${"%.1f".format(wifiRttTelemetry.closestDistanceMeters)}m" else "RTT: 802.11mc",
                        color = ReconCyan,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =====================================================================
        // 3. HERO LIVE SCANNER MAP WITH REAL OSM TILES & COURSE-UP ROTATION
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(TacticalSurface)
                .border(1.dp, TacticalBorder, RoundedCornerShape(12.dp))
        ) {
            LiveScannerRoadMapCanvas(
                cameraLat = cameraLat,
                cameraLon = cameraLon,
                zoom = dynamicZoom,
                currentLat = currentLat,
                currentLon = currentLon,
                currentHeading = currentHeading,
                deviceHeading = deviceHeading,
                speedMph = speedMph,
                rangeMeters = selectedRangeMeters,
                targets = filteredTargets,
                csiSnapshot = csiSnapshot,
                isPersonDetectionEnabled = isPersonDetectionEnabled,
                osmTileManager = osmTileManager,
                tileRevision = tileRevision,
                selectedTargetId = selectedTargetId,
                onSelectTarget = { target ->
                    selectedTargetId = target.id
                    cameraLat = target.detectedLat ?: currentLat
                    cameraLon = target.detectedLon ?: currentLon
                    isFollowingVehicle = false
                    if (target.category == RfCategory.CCTV_SURVEILLANCE) {
                        val extractedUrl = if (target.details.contains("URL: http")) {
                            target.details.substringAfter("URL: ").trim()
                        } else null
                        viewingCctvCamera = scannerManager.publicCctvRepo.getCameraById(target.id)
                            ?: PublicCctvCamera(
                                id = target.id,
                                name = target.displayName,
                                locationDesc = target.details,
                                latitude = target.detectedLat ?: currentLat,
                                longitude = target.detectedLon ?: currentLon,
                                operator = target.vendor,
                                liveImageUrl = extractedUrl
                            )
                    }
                },
                onPanCamera = { deltaLat, deltaLon ->
                    cameraLat += deltaLat
                    cameraLon += deltaLon
                    isFollowingVehicle = false
                },
                onZoomChange = onZoomChange
            )

            // Top-Left Floating HUD Status Box
            Surface(
                color = TacticalObsidian.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isScannerPaused) ReconAmber else ReconGreen)
                        )
                        Text(
                            text = "RNG: ${if (selectedRangeMeters >= 1000f) "1km" else "${selectedRangeMeters.toInt()}m"} • Z${"%.1f".format(dynamicZoom)}",
                            color = ReconCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "HDG: ${deviceHeading.roundToInt()}° • ${"%.4f".format(cameraLat)}°N, ${"%.4f".format(abs(cameraLon))}°W",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Top-Right Quick Range Selector (Expansive: 25m, 50m, 100m, 250m, 500m, 1km)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(25f, 50f, 100f, 250f, 500f, 1000f).forEach { r ->
                    val isSelected = selectedRangeMeters == r
                    Surface(
                        color = if (isSelected) ReconCyan.copy(alpha = 0.25f) else TacticalObsidian.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ReconCyan else TacticalBorder),
                        modifier = Modifier.clickable { onSelectRangePreset(r) }
                    ) {
                        Text(
                            text = if (r >= 1000f) "1km" else "${r.toInt()}m",
                            color = if (isSelected) ReconCyan else TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Center-Right Floating Tactile Zoom Control Bar (+ and - Buttons)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Zoom In Button (+)
                Surface(
                    color = TacticalObsidian.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onZoomChange(1.35f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = ReconCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Zoom Out Button (-)
                Surface(
                    color = TacticalObsidian.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onZoomChange(0.74f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = ReconCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom-Right Re-center Vehicle FAB (visible when panned away)
            if (!isFollowingVehicle) {
                Surface(
                    color = TacticalObsidian.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clickable {
                            cameraLat = currentLat
                            cameraLon = currentLon
                            isFollowingVehicle = true
                            selectedTargetId = null
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Center", tint = ReconCyan, modifier = Modifier.size(12.dp))
                        Text(text = "RE-CENTER", color = ReconCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 4. CATEGORY & SAVED FILTER CHIPS
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChipItem(
                label = "ALL (${targetsInRange.size})",
                selected = selectedFilterCategory == null && !filterSavedOnly,
                activeColor = ReconCyan,
                onClick = {
                    selectedFilterCategory = null
                    filterSavedOnly = false
                }
            )
            FilterChipItem(
                label = "⭐ SAVED ($savedCount)",
                selected = filterSavedOnly,
                activeColor = ReconAmber,
                onClick = {
                    filterSavedOnly = !filterSavedOnly
                    selectedFilterCategory = null
                }
            )
            FilterChipItem(
                label = "🔑 KEYS ($keyCount)",
                selected = selectedFilterCategory == RfCategory.CAR_KEY && !filterSavedOnly,
                activeColor = Color(0xFFFFD600),
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = RfCategory.CAR_KEY
                }
            )
            FilterChipItem(
                label = "🚗 CARS ($vehicleCount)",
                selected = selectedFilterCategory == RfCategory.VEHICLE && !filterSavedOnly,
                activeColor = ReconCyan,
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = RfCategory.VEHICLE
                }
            )
            FilterChipItem(
                label = "📷 CCTV ($cctvCount)",
                selected = selectedFilterCategory == RfCategory.CCTV_SURVEILLANCE && !filterSavedOnly,
                activeColor = Color(0xFFFF5252),
                onClick = {
                    filterSavedOnly = false
                    selectedFilterCategory = RfCategory.CCTV_SURVEILLANCE
                }
            )
            if (cellCount > 0) {
                FilterChipItem(
                    label = "📡 CELL ($cellCount)",
                    selected = selectedFilterCategory == RfCategory.CELL_TOWER && !filterSavedOnly,
                    activeColor = Color(0xFF00E5FF),
                    onClick = {
                        filterSavedOnly = false
                        selectedFilterCategory = RfCategory.CELL_TOWER
                    }
                )
            }
            if (wifiCount > 0) {
                FilterChipItem(
                    label = "📶 WI-FI ($wifiCount)",
                    selected = selectedFilterCategory == RfCategory.ACCESS_POINT && !filterSavedOnly,
                    activeColor = ReconGreen,
                    onClick = {
                        filterSavedOnly = false
                        selectedFilterCategory = RfCategory.ACCESS_POINT
                    }
                )
            }
            if (droneCount > 0) {
                FilterChipItem(
                    label = "🛸 DRONE ($droneCount)",
                    selected = selectedFilterCategory == RfCategory.DRONE_UAV && !filterSavedOnly,
                    activeColor = Color(0xFFBA68C8),
                    onClick = {
                        filterSavedOnly = false
                        selectedFilterCategory = RfCategory.DRONE_UAV
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 5. HORIZONTAL TARGET CAROUSEL WITH INTEL & RELATIVE MOTION
        // =====================================================================
        if (filteredTargets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO RF EMITTERS MATCH CURRENT FILTER",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 2.dp)
            ) {
                items(filteredTargets, key = { it.id }) { target ->
                    val isSelected = target.id == selectedTargetId
                    RfTargetHorizontalCard(
                        target = target,
                        isSelected = isSelected,
                        onSelect = {
                            selectedTargetId = target.id
                            val pinLat = target.detectedLat ?: run {
                                val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                                currentLat + (target.estimatedDistanceMeters * cos(bRad)) / 111320.0
                            }
                            val pinLon = target.detectedLon ?: run {
                                val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                                currentLon + (target.estimatedDistanceMeters * sin(bRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
                            }
                            cameraLat = pinLat
                            cameraLon = pinLon
                            isFollowingVehicle = false
                        },
                        onEditIntel = {
                            editingTarget = target
                        },
                        onPinToMap = {
                            val pinLat = target.detectedLat ?: run {
                                val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                                currentLat + (target.estimatedDistanceMeters * cos(bRad)) / 111320.0
                            }
                            val pinLon = target.detectedLon ?: run {
                                val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                                currentLon + (target.estimatedDistanceMeters * sin(bRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
                            }
                            onNavigateToLocation(target.displayName, pinLat, pinLon)
                        },
                        onViewCctv = {
                            val extractedUrl = if (target.details.contains("URL: http")) {
                                target.details.substringAfter("URL: ").trim()
                            } else null
                            val cam = scannerManager.publicCctvRepo.getCameraById(target.id)
                                ?: PublicCctvCamera(
                                    id = target.id,
                                    name = target.displayName,
                                    locationDesc = target.details,
                                    latitude = target.detectedLat ?: currentLat,
                                    longitude = target.detectedLon ?: currentLon,
                                    operator = target.vendor,
                                    liveImageUrl = extractedUrl
                                )
                            viewingCctvCamera = cam
                        }
                    )
                }
            }
        }
    }
}

/**
 * Top Telemetry Threat Badge with counter and icon.
 */
@Composable
private fun TelemetryThreatBadge(
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) color.copy(alpha = 0.15f) else TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else TacticalBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Text(text = label, color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                text = "$count",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Live Scanner Road Map Canvas.
 * Combines real OpenStreetMap raster tiles, high-resolution street vectors,
 * course-up map rotation relative to device heading, animated 360° sweeping radar beam,
 * CSI biological human presence ripples, and detected target blips.
 */
@Composable
private fun LiveScannerRoadMapCanvas(
    cameraLat: Double,
    cameraLon: Double,
    zoom: Float,
    currentLat: Double,
    currentLon: Double,
    currentHeading: Float,
    deviceHeading: Float,
    speedMph: Int,
    rangeMeters: Float,
    targets: List<DetectedRfTarget>,
    csiSnapshot: CsiSensingSnapshot,
    isPersonDetectionEnabled: Boolean = false,
    osmTileManager: OsmTileManager,
    tileRevision: Int,
    selectedTargetId: String?,
    onSelectTarget: (DetectedRfTarget) -> Unit,
    onPanCamera: (deltaLat: Double, deltaLon: Double) -> Unit,
    onZoomChange: (zoomFactor: Float) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    // Real-time Tactical ColorFilter applied to OpenStreetMap tiles
    val tileColorFilter = remember {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -0.75f, 0f, 0f, 0f, 165f,
                    0f, -0.75f, 0f, 0f, 175f,
                    0f, 0f, -0.65f, 0f, 195f,
                    0f, 0f, 0f, 0.88f, 0f
                )
            )
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(zoom, deviceHeading) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    if (zoomChange > 0f && zoomChange != 1.0f && !zoomChange.isNaN() && !zoomChange.isInfinite()) {
                        onZoomChange(zoomChange)
                    }
                    if ((pan.x != 0f || pan.y != 0f) && !pan.x.isNaN() && !pan.y.isNaN()) {
                        val safeZoom = zoom.coerceIn(12f, 21f)
                        val scale = 256.0 * 2.0.pow(safeZoom.toDouble())
                        val rotRad = Math.toRadians(deviceHeading.toDouble())
                        // Rotate drag vector according to device heading
                        val unrotX = pan.x * cos(rotRad) - pan.y * sin(rotRad)
                        val unrotY = pan.x * sin(rotRad) + pan.y * cos(rotRad)

                        val deltaLon = -(unrotX / scale) * 360.0
                        val deltaLat = (unrotY / scale) * 180.0
                        if (!deltaLat.isNaN() && !deltaLon.isNaN()) {
                            onPanCamera(deltaLat, deltaLon)
                        }
                    }
                }
            }
            .pointerInput(targets, cameraLat, cameraLon, zoom, deviceHeading) {
                detectTapGestures { tapOffset ->
                    val scale = 256.0 * 2.0.pow(zoom.toDouble())
                    val cx = (cameraLon + 180.0) / 360.0 * scale
                    val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / Math.PI) / 2.0 * scale
                    val centerPt = Offset(size.width / 2f, size.height / 2f)

                    // Un-rotate tap around center to test against un-rotated target coordinates
                    val rotRad = Math.toRadians(deviceHeading.toDouble())
                    val dx = tapOffset.x - centerPt.x
                    val dy = tapOffset.y - centerPt.y
                    val unrotX = centerPt.x + (dx * cos(rotRad) - dy * sin(rotRad)).toFloat()
                    val unrotY = centerPt.y + (dx * sin(rotRad) + dy * cos(rotRad)).toFloat()

                    for (target in targets) {
                        val tLat = target.detectedLat ?: run {
                            val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                            currentLat + (target.estimatedDistanceMeters * cos(bRad)) / 111320.0
                        }
                        val tLon = target.detectedLon ?: run {
                            val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                            currentLon + (target.estimatedDistanceMeters * sin(bRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
                        }

                        val tx = (tLon + 180.0) / 360.0 * scale
                        val ty = (1.0 - ln(tan(Math.toRadians(tLat)) + 1.0 / cos(Math.toRadians(tLat))) / Math.PI) / 2.0 * scale

                        val screenX = (tx - cx + size.width / 2.0).toFloat()
                        val screenY = (ty - cy + size.height / 2.0).toFloat()

                        val distToTap = hypot(unrotX - screenX, unrotY - screenY)
                        if (distToTap < 45f) {
                            onSelectTarget(target)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val rev = tileRevision

        val width = size.width
        val height = size.height

        val scale = 256.0 * 2.0.pow(zoom.toDouble())
        val cx = (cameraLon + 180.0) / 360.0 * scale
        val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / Math.PI) / 2.0 * scale

        fun project(lat: Double, lon: Double): Offset {
            val x = (lon + 180.0) / 360.0 * scale
            val y = (1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / Math.PI) / 2.0 * scale
            return Offset(
                (x - cx + width / 2.0).toFloat(),
                (y - cy + height / 2.0).toFloat()
            )
        }

        // Expanded view bounds to account for canvas rotation
        val latSpan = (height / scale) * 180.0 * 1.5
        val lonSpan = (width / scale) * 360.0 * 1.5
        val viewBounds = UkBoundingBox(
            minLat = cameraLat - latSpan / 2.0,
            maxLat = cameraLat + latSpan / 2.0,
            minLon = cameraLon - lonSpan / 2.0,
            maxLon = cameraLon + lonSpan / 2.0
        )

        val vehiclePos = project(currentLat, currentLon)
        val maxScopeRadius = min(width, height) * 0.44f

        // ---------------------------------------------------------------------
        // A. BACKGROUND TACTICAL BASE
        // ---------------------------------------------------------------------
        drawRect(color = TacticalObsidian)

        // =====================================================================
        // B. ROTATE RELATIVE TO DEVICE HEADING (COURSE-UP / HEADS-UP MODE)
        // =====================================================================
        withTransform({
            rotate(degrees = -deviceHeading, pivot = vehiclePos)
        }) {
            // -----------------------------------------------------------------
            // 1. DRAW ACTUAL OPENSTREETMAP RASTER TILES
            // -----------------------------------------------------------------
            val tileZ = zoom.toInt().coerceIn(1, 18)
            val n = 1 shl tileZ
            val tileScale = 2.0.pow((zoom - tileZ).toDouble())
            val tileSizeOnScreen = (256.0 * tileScale).toFloat()

            val cameraLatRad = Math.toRadians(cameraLat.coerceIn(-85.0511, 85.0511))
            val cameraTileX = (cameraLon + 180.0) / 360.0 * n
            val cameraTileY = (1.0 - ln(tan(cameraLatRad) + 1.0 / cos(cameraLatRad)) / PI) / 2.0 * n

            val halfTilesW = (width / (2.0 * tileSizeOnScreen)) * 1.6
            val halfTilesH = (height / (2.0 * tileSizeOnScreen)) * 1.6

            val minTx = (floor(cameraTileX - halfTilesW).toInt()).coerceIn(0, n - 1)
            val maxTx = (ceil(cameraTileX + halfTilesW).toInt()).coerceIn(0, n - 1)
            val minTy = (floor(cameraTileY - halfTilesH).toInt()).coerceIn(0, n - 1)
            val maxTy = (ceil(cameraTileY + halfTilesH).toInt()).coerceIn(0, n - 1)

            for (tx in minTx..maxTx) {
                for (ty in minTy..maxTy) {
                    val tileBmp = osmTileManager.getTile(tileZ, tx, ty, "tactical_obsidian")
                    if (tileBmp != null) {
                        val sX = (width / 2f) + ((tx - cameraTileX) * tileSizeOnScreen).toFloat()
                        val sY = (height / 2f) + ((ty - cameraTileY) * tileSizeOnScreen).toFloat()

                        drawImage(
                            image = tileBmp,
                            dstOffset = androidx.compose.ui.unit.IntOffset(sX.toInt(), sY.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                ceil(tileSizeOnScreen).toInt() + 1,
                                ceil(tileSizeOnScreen).toInt() + 1
                            ),
                            colorFilter = tileColorFilter
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 2. DRAW HIGH-RESOLUTION STREET VECTORS OVER TILES
            // -----------------------------------------------------------------
            val localStreets = UkOsmDataEngine.getLocalStreetGridForBounds(viewBounds, zoom)
            for (road in localStreets) {
                val path = Path()
                var started = false
                for (pt in road.points) {
                    val sc = project(pt.first, pt.second)
                    if (!started) {
                        path.moveTo(sc.x, sc.y)
                        started = true
                    } else {
                        path.lineTo(sc.x, sc.y)
                    }
                }

                if (road.type == OsmRoadType.SECONDARY) {
                    drawPath(
                        path = path,
                        color = Color(0xFF07121A).copy(alpha = 0.75f),
                        style = Stroke(width = 6.0f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF2C566C).copy(alpha = 0.90f),
                        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                    )
                } else {
                    drawPath(
                        path = path,
                        color = Color(0xFF050E14).copy(alpha = 0.65f),
                        style = Stroke(width = 4.0f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF1B3648).copy(alpha = 0.85f),
                        style = Stroke(width = 2.0f, cap = StrokeCap.Round)
                    )
                }
            }

            // Major Arterials & Motorways
            for (road in UkOsmDataEngine.MAJOR_ROADS) {
                val path = Path()
                var started = false
                for (pt in road.points) {
                    val sc = project(pt.first, pt.second)
                    if (!started) {
                        path.moveTo(sc.x, sc.y)
                        started = true
                    } else {
                        path.lineTo(sc.x, sc.y)
                    }
                }

                drawPath(path = path, color = Color.Black, style = Stroke(width = 9.0f, cap = StrokeCap.Round))
                drawPath(path = path, color = ReconCyan.copy(alpha = 0.40f), style = Stroke(width = 6.5f, cap = StrokeCap.Round))
                drawPath(path = path, color = ReconCyan.copy(alpha = 0.90f), style = Stroke(width = 3.8f, cap = StrokeCap.Round))
            }

            // -----------------------------------------------------------------
            // 3. CSI BIOMETRIC FRESNEL WAVE RIPPLES (HUMAN PRESENCE FIELD)
            // -----------------------------------------------------------------
            if (isPersonDetectionEnabled && csiSnapshot.presenceState != HumanPresenceState.VACANT) {
                val fresnelColor = if (csiSnapshot.presenceState == HumanPresenceState.ACTIVE_HUMAN_TRANSIT) ReconAmber else ReconGreen
                val bioRadius = maxScopeRadius * 0.28f * pulseScale
                drawCircle(
                    color = fresnelColor.copy(alpha = 0.22f),
                    radius = bioRadius,
                    center = vehiclePos,
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                )
            }

            // -----------------------------------------------------------------
            // 4. ANIMATED 360° SWEEPING RADAR BEAM & PHOSPHOR ILLUMINATION
            // -----------------------------------------------------------------
            val sweepRad = sweepAngle * (Math.PI.toFloat() / 180f)
            val beamEndX = vehiclePos.x + maxScopeRadius * cos(sweepRad)
            val beamEndY = vehiclePos.y + maxScopeRadius * sin(sweepRad)

            val trailPath = Path().apply {
                moveTo(vehiclePos.x, vehiclePos.y)
                val trailSpan = 35f
                var a = sweepAngle - trailSpan
                while (a <= sweepAngle) {
                    val rRad = a * (Math.PI.toFloat() / 180f)
                    val tx = vehiclePos.x + maxScopeRadius * cos(rRad)
                    val ty = vehiclePos.y + maxScopeRadius * sin(rRad)
                    lineTo(tx, ty)
                    a += 3f
                }
                close()
            }
            drawPath(
                path = trailPath,
                brush = Brush.radialGradient(
                    colors = listOf(ReconGreen.copy(alpha = 0.20f), ReconGreen.copy(alpha = 0.02f), Color.Transparent),
                    center = vehiclePos,
                    radius = maxScopeRadius
                )
            )

            drawLine(color = ReconGreen, start = vehiclePos, end = Offset(beamEndX, beamEndY), strokeWidth = 2.0f)
            drawCircle(color = ReconGreen, radius = 3.5f, center = Offset(beamEndX, beamEndY))

            // -----------------------------------------------------------------
            // 5. DETECTED TARGET BLIPS & RELATIVE MOTION HISTORY (BREADCRUMBS)
            // -----------------------------------------------------------------
            for (target in targets) {
                val tLat = target.detectedLat ?: run {
                    val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                    currentLat + (target.estimatedDistanceMeters * cos(bRad)) / 111320.0
                }
                val tLon = target.detectedLon ?: run {
                    val bRad = Math.toRadians(target.relativeBearingDeg.toDouble())
                    currentLon + (target.estimatedDistanceMeters * sin(bRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
                }

                val blipPos = project(tLat, tLon)

                val isSaved = target.savedMetadata != null
                val categoryColor = when {
                    isSaved -> ReconAmber
                    target.category == RfCategory.CAR_KEY -> Color(0xFFFFD600)
                    target.category == RfCategory.VEHICLE -> ReconCyan
                    target.category == RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFF5252)
                    target.category == RfCategory.DRONE_UAV -> Color(0xFFBA68C8)
                    target.category == RfCategory.CELL_TOWER -> Color(0xFF00E5FF)
                    target.category == RfCategory.ACCESS_POINT -> ReconGreen
                    else -> Color(0xFF64B5F6)
                }

                val dx = blipPos.x - vehiclePos.x
                val dy = blipPos.y - vehiclePos.y
                var targetAngle = atan2(dy, dx) * (180f / Math.PI.toFloat())
                if (targetAngle < 0f) targetAngle += 360f

                val angleDiff = abs((sweepAngle - targetAngle + 360f) % 360f)
                val isSwept = angleDiff < 32f
                val isSelected = target.id == selectedTargetId

                // Relative Location History Breadcrumbs
                if (target.locationHistory.size >= 2 && (isSelected || isSwept)) {
                    val historyPath = Path()
                    var hStarted = false
                    for ((idx, breadcrumb) in target.locationHistory.withIndex()) {
                        val hPos = project(breadcrumb.targetLat, breadcrumb.targetLon)
                        if (!hStarted) {
                            historyPath.moveTo(hPos.x, hPos.y)
                            hStarted = true
                        } else {
                            historyPath.lineTo(hPos.x, hPos.y)
                        }

                        val alpha = (0.3f + 0.6f * (idx.toFloat() / target.locationHistory.size)).coerceIn(0.2f, 0.9f)
                        drawCircle(
                            color = categoryColor.copy(alpha = alpha),
                            radius = 2.5f,
                            center = hPos
                        )
                    }

                    drawPath(
                        path = historyPath,
                        color = categoryColor.copy(alpha = 0.5f),
                        style = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                    )
                }

                // Target Blip Outer Glow
                if (isSwept || isSelected) {
                    drawCircle(
                        color = categoryColor.copy(alpha = if (isSelected) 0.55f else 0.40f),
                        radius = if (isSelected) 14f else 11f,
                        center = blipPos
                    )
                    drawCircle(
                        color = categoryColor.copy(alpha = 0.85f),
                        radius = if (isSelected) 10f else 7.5f,
                        center = blipPos,
                        style = Stroke(width = 1.8f)
                    )
                }

                // Target Core Marker Dot
                drawCircle(color = categoryColor, radius = if (isSelected) 5.5f else 4.0f, center = blipPos)

                if (isSaved) {
                    drawCircle(color = ReconAmber, radius = 2.0f, center = blipPos)
                }

                // Selected crosshairs bracket
                if (isSelected) {
                    val bracketSize = 12f
                    drawLine(color = ReconAmber, start = Offset(blipPos.x - bracketSize, blipPos.y), end = Offset(blipPos.x + bracketSize, blipPos.y), strokeWidth = 1.5f)
                    drawLine(color = ReconAmber, start = Offset(blipPos.x, blipPos.y - bracketSize), end = Offset(blipPos.x, blipPos.y + bracketSize), strokeWidth = 1.5f)
                }

                // Tactical Callout Tag (Counter-rotated so text remains upright)
                if (isSwept || isSelected || zoom >= 18.2f) {
                    val tagPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 20f
                        typeface = android.graphics.Typeface.MONOSPACE
                        isFakeBoldText = true
                    }
                    val subPaint = android.graphics.Paint().apply {
                        color = when {
                            isSaved -> android.graphics.Color.parseColor("#FFD54F")
                            target.category == RfCategory.CAR_KEY -> android.graphics.Color.parseColor("#FFD600")
                            target.category == RfCategory.VEHICLE -> android.graphics.Color.parseColor("#00E5FF")
                            target.category == RfCategory.CCTV_SURVEILLANCE -> android.graphics.Color.parseColor("#FF5252")
                            target.category == RfCategory.DRONE_UAV -> android.graphics.Color.parseColor("#BA68C8")
                            target.category == RfCategory.CELL_TOWER -> android.graphics.Color.parseColor("#00E5FF")
                            target.category == RfCategory.ACCESS_POINT -> android.graphics.Color.parseColor("#66BB6A")
                            else -> android.graphics.Color.parseColor("#64B5F6")
                        }
                        textSize = 17f
                        typeface = android.graphics.Typeface.MONOSPACE
                    }

                    val motionStr = when (target.motionTrend) {
                        RelativeMotionTrend.APPROACHING -> "▲ CLOSING"
                        RelativeMotionTrend.RECEDING -> "▼ RECEDING"
                        RelativeMotionTrend.STATIONARY -> "• TRACKING"
                    }

                    val savedPlate = target.savedMetadata?.numberPlate
                    val titlePrefix = when {
                        !savedPlate.isNullOrBlank() -> "[$savedPlate] "
                        isSaved -> "⭐ "
                        target.category == RfCategory.CAR_KEY -> "🔑 "
                        target.category == RfCategory.VEHICLE -> "🚗 "
                        target.category == RfCategory.CCTV_SURVEILLANCE -> "📷 "
                        target.category == RfCategory.DRONE_UAV -> "🛸 "
                        target.category == RfCategory.CELL_TOWER -> "📡 "
                        target.category == RfCategory.ACCESS_POINT -> "📶 "
                        else -> "📡 "
                    }
                    val tagTitle = "$titlePrefix${target.displayName}"
                    val doorStr = if (!target.savedMetadata?.doorNumberOrAddress.isNullOrBlank()) " • ${target.savedMetadata?.doorNumberOrAddress}" else ""
                    val tagSub = "${target.rssi}dBm • ~${target.estimatedDistanceMeters.toInt()}m • $motionStr$doorStr"

                    drawContext.canvas.nativeCanvas.apply {
                        save()
                        // Counter-rotate text so driver reads it horizontally
                        rotate(deviceHeading, blipPos.x, blipPos.y)

                        val boxX = blipPos.x + 12f
                        val boxY = blipPos.y - 18f
                        val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.argb(225, 10, 16, 22) }
                        val borderPaint = android.graphics.Paint().apply {
                            color = subPaint.color
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2f
                        }

                        val titleWidth = tagPaint.measureText(tagTitle)
                        val subWidth = subPaint.measureText(tagSub)
                        val boxWidth = max(titleWidth, subWidth) + 16f
                        val boxHeight = 44f

                        val rect = android.graphics.RectF(boxX, boxY - 20f, boxX + boxWidth, boxY + boxHeight - 20f)
                        drawRoundRect(rect, 6f, 6f, bgPaint)
                        drawRoundRect(rect, 6f, 6f, borderPaint)

                        drawText(tagTitle, boxX + 8f, boxY, tagPaint)
                        drawText(tagSub, boxX + 8f, boxY + 20f, subPaint)
                        restore()
                    }
                }
            }
        } // End of withTransform (rotated map)

        // ---------------------------------------------------------------------
        // C. TACTICAL RADAR RETICLE (Fixed Heads-Up Reference Frame)
        // ---------------------------------------------------------------------
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        val ringRatios = listOf(0.25f, 0.50f, 0.75f, 1.0f)
        for (ratio in ringRatios) {
            val r = maxScopeRadius * ratio
            drawCircle(
                color = if (ratio == 1.0f) ReconCyan.copy(alpha = 0.45f) else Color(0xFF1E3A4C).copy(alpha = 0.6f),
                radius = r,
                center = vehiclePos,
                style = if (ratio == 1.0f) Stroke(width = 1.5f) else Stroke(width = 1.0f, pathEffect = dashEffect)
            )
        }

        // Fixed Crosshairs (Forward/Aft, Port/Starboard)
        drawLine(
            color = Color(0xFF1E3A4C).copy(alpha = 0.45f),
            start = Offset(vehiclePos.x - maxScopeRadius, vehiclePos.y),
            end = Offset(vehiclePos.x + maxScopeRadius, vehiclePos.y),
            strokeWidth = 1f,
            pathEffect = dashEffect
        )
        drawLine(
            color = Color(0xFF1E3A4C).copy(alpha = 0.45f),
            start = Offset(vehiclePos.x, vehiclePos.y - maxScopeRadius),
            end = Offset(vehiclePos.x, vehiclePos.y + maxScopeRadius),
            strokeWidth = 1f,
            pathEffect = dashEffect
        )

        // Rotating Cardinal Direction Labels (N rotates with device heading)
        val cardinalPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 0, 229, 255)
            textSize = 22f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }

        fun drawCardinal(label: String, angleOffsetDeg: Float) {
            val rad = Math.toRadians((angleOffsetDeg - deviceHeading - 90.0)).toFloat()
            val cx = vehiclePos.x + (maxScopeRadius + 14f) * cos(rad)
            val cy = vehiclePos.y + (maxScopeRadius + 14f) * sin(rad)
            drawContext.canvas.nativeCanvas.drawText(label, cx, cy + 7f, cardinalPaint)
        }

        drawCardinal("N", 0f)
        drawCardinal("E", 90f)
        drawCardinal("S", 180f)
        drawCardinal("W", 270f)

        // ---------------------------------------------------------------------
        // D. USER VEHICLE RETICLE (ALWAYS POINTING STRAIGHT UP!)
        // ---------------------------------------------------------------------
        // Outer Pulsing Sonar Ring
        drawCircle(
            color = ReconCyan.copy(alpha = (1.6f - pulseScale).coerceIn(0f, 0.6f)),
            radius = 16f * pulseScale,
            center = vehiclePos,
            style = Stroke(width = 1.5f)
        )

        // Device Direction Pointer (Points straight UP in course-up mode)
        val devArrowPath = Path().apply {
            moveTo(vehiclePos.x, vehiclePos.y - 42f)
            lineTo(vehiclePos.x - 7f, vehiclePos.y - 24f)
            lineTo(vehiclePos.x + 7f, vehiclePos.y - 24f)
            close()
        }
        drawPath(path = devArrowPath, color = ReconAmber)
        drawLine(
            color = ReconAmber,
            start = vehiclePos,
            end = Offset(vehiclePos.x, vehiclePos.y - 42f),
            strokeWidth = 2.0f
        )

        // GPS Heading Deviation Pointer (Cyan line showing delta between travel direction and phone orientation)
        val gpsDelta = currentHeading - deviceHeading
        val gpsDeltaRad = Math.toRadians((gpsDelta - 90.0)).toFloat()
        val gpsEndX = vehiclePos.x + 48f * cos(gpsDeltaRad)
        val gpsEndY = vehiclePos.y + 48f * sin(gpsDeltaRad)
        drawLine(
            color = ReconCyan,
            start = vehiclePos,
            end = Offset(gpsEndX, gpsEndY),
            strokeWidth = 2.5f
        )
        val gpsArrow = Path().apply {
            moveTo(gpsEndX, gpsEndY)
            lineTo(gpsEndX - 7f * cos(gpsDeltaRad - 0.4f), gpsEndY - 7f * sin(gpsDeltaRad - 0.4f))
            lineTo(gpsEndX - 7f * cos(gpsDeltaRad + 0.4f), gpsEndY - 7f * sin(gpsDeltaRad + 0.4f))
            close()
        }
        drawPath(path = gpsArrow, color = ReconCyan)

        // Vehicle Center Core Dot
        drawCircle(color = ReconCyan, radius = 5.5f, center = vehiclePos)
        drawCircle(color = TacticalObsidian, radius = 2.5f, center = vehiclePos)

        if (speedMph > 0) {
            val speedPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                textSize = 19f
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText("$speedMph MPH", vehiclePos.x, vehiclePos.y + 26f, speedPaint)
        }
    }
}

/**
 * Filter Chip Item
 */
@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) activeColor.copy(alpha = 0.15f) else TacticalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) activeColor else TacticalBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) activeColor else TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * Horizontal Card for Detected Target Carousel (Vehicles, CCTV, IoT, Saved Intel)
 */
@Composable
private fun RfTargetHorizontalCard(
    target: DetectedRfTarget,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEditIntel: () -> Unit,
    onPinToMap: () -> Unit,
    onViewCctv: () -> Unit = {}
) {
    val isSaved = target.savedMetadata != null
    val categoryColor = when {
        isSaved -> ReconAmber
        target.category == RfCategory.CAR_KEY -> Color(0xFFFFD600)
        target.category == RfCategory.VEHICLE -> ReconCyan
        target.category == RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFF5252)
        target.category == RfCategory.DRONE_UAV -> Color(0xFFBA68C8)
        target.category == RfCategory.CELL_TOWER -> Color(0xFF00E5FF)
        target.category == RfCategory.ACCESS_POINT -> ReconGreen
        else -> TextSecondary
    }

    val iconVector = when {
        isSaved -> Icons.Default.Star
        target.category == RfCategory.CAR_KEY -> Icons.Default.Key
        target.category == RfCategory.VEHICLE -> Icons.Default.DirectionsCar
        target.category == RfCategory.CCTV_SURVEILLANCE -> Icons.Default.Videocam
        target.category == RfCategory.DRONE_UAV -> Icons.Default.Flight
        target.category == RfCategory.CELL_TOWER -> Icons.Default.CellTower
        target.category == RfCategory.ACCESS_POINT -> Icons.Default.Wifi
        else -> Icons.Default.Wifi
    }

    val motionTrendStr = when (target.motionTrend) {
        RelativeMotionTrend.APPROACHING -> "▲ CLOSING"
        RelativeMotionTrend.RECEDING -> "▼ RECEDING"
        RelativeMotionTrend.STATIONARY -> "• STEADY"
    }

    Card(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) TacticalBorder.copy(alpha = 0.4f) else TacticalSurface),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) categoryColor else categoryColor.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Category Icon, Name / Number Plate, and RSSI badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .border(1.dp, categoryColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = iconVector, contentDescription = null, tint = categoryColor, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        target.savedMetadata?.numberPlate?.takeIf { it.isNotBlank() }?.let { plate ->
                            Surface(
                                color = ReconAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber)
                            ) {
                                Text(
                                    text = plate,
                                    color = ReconAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = target.displayName,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    val subDetails = target.savedMetadata?.doorNumberOrAddress?.takeIf { it.isNotBlank() }
                        ?: "${target.vendor} • ${target.protocol}"
                    Text(
                        text = subDetails,
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(horizontalAlignment = Alignment.End) {
                    SignalStrengthBars(bars = target.signalBars, color = categoryColor)
                    Text(
                        text = "${target.rssi}dBm",
                        color = TextPrimary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom Row: Distance, Relative Motion, INTEL button, and PIN button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "~${"%.0f".format(target.estimatedDistanceMeters)}m • $motionTrendStr",
                            color = categoryColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            color = when {
                                target.estimatedDistanceMeters < 5.0 -> ReconAmber.copy(alpha = 0.2f)
                                target.estimatedDistanceMeters < 15.0 -> ReconCyan.copy(alpha = 0.15f)
                                else -> TacticalSurface
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                when {
                                    target.estimatedDistanceMeters < 5.0 -> ReconAmber
                                    target.estimatedDistanceMeters < 15.0 -> ReconCyan
                                    else -> TacticalBorder
                                }
                            )
                        ) {
                            Text(
                                text = when {
                                    target.estimatedDistanceMeters < 5.0 -> "IMMEDIATE"
                                    target.estimatedDistanceMeters < 15.0 -> "NEAR"
                                    target.estimatedDistanceMeters < 40.0 -> "MEDIUM"
                                    else -> "PERIMETER"
                                },
                                color = when {
                                    target.estimatedDistanceMeters < 5.0 -> ReconAmber
                                    target.estimatedDistanceMeters < 15.0 -> ReconCyan
                                    else -> TextSecondary
                                },
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                    Text(
                        text = if (target.isRandomizedMac) "${target.macAddress} [RANDOM]" else target.macAddress,
                        color = if (target.isRandomizedMac) ReconAmber.copy(alpha = 0.7f) else TextSecondary.copy(alpha = 0.7f),
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Live CCTV Stream Viewer Button
                    if (target.category == RfCategory.CCTV_SURVEILLANCE) {
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            modifier = Modifier.clickable { onViewCctv() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = "Feed", tint = Color(0xFFFF5252), modifier = Modifier.size(10.dp))
                                Text(text = "FEED", color = Color(0xFFFF5252), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    // Edit Intel Button (Number plate, Door #, Group, Custom Sound)
                    Surface(
                        color = ReconCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable { onEditIntel() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.EditNote, contentDescription = "Intel", tint = ReconCyan, modifier = Modifier.size(10.dp))
                            Text(text = "INTEL", color = ReconCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Pin To Map Button
                    Surface(
                        color = categoryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable { onPinToMap() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = categoryColor, modifier = Modifier.size(10.dp))
                            Text(text = "PIN", color = categoryColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4-Bar Signal Indicator
 */
@Composable
private fun SignalStrengthBars(bars: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        modifier = Modifier.height(10.dp)
    ) {
        for (i in 1..4) {
            val barHeight = (i * 2.2).dp
            val active = i <= bars
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .background(if (active) color else TacticalBorder)
            )
        }
    }
}

/**
 * High-Tech Tactical Dialog displaying Public CCTV live stream snapshot / camera feed.
 */
@Composable
private fun PublicCctvLiveViewerDialog(
    camera: PublicCctvCamera,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit
) {
    var refreshNonce by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isImageLoading by remember { mutableStateOf(camera.liveImageUrl?.startsWith("http") == true) }
    var isImageSuccess by remember { mutableStateOf(false) }
    var opticMode by remember { mutableStateOf(if (camera.liveImageUrl?.startsWith("http") == true) "LIVE" else "FLIR") }

    val infiniteTransition = rememberInfiniteTransition(label = "cctv_live_pulse")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cctv_live_alpha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TacticalObsidian,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = liveAlpha))
                    )
                    Text(
                        text = "PUBLIC CCTV // ${if (isImageSuccess && opticMode == "LIVE") "LIVE STREAM" else "TACTICAL OPTIC"}",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    color = Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.Red)
                ) {
                    Text(
                        text = if (isImageSuccess && opticMode == "LIVE") "LIVE SNAPSHOT" else "TACTICAL FLIR",
                        color = Color(0xFFFF5252),
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera Header & Operator
                Column {
                    Text(
                        text = camera.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${camera.operator} • ${camera.locationDesc}",
                        color = ReconCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Live Camera Image Viewport (Always active with Tactical Viewfinder fallback)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Base Animated Tactical Viewfinder (Guarantees screen NEVER shows blank void)
                    TacticalCctvViewfinder(
                        camera = camera,
                        opticMode = opticMode
                    )

                    // 2. Real Web Image Layer (When live URL is available and LIVE mode is active)
                    if (camera.liveImageUrl != null && camera.liveImageUrl.startsWith("http") && opticMode == "LIVE") {
                        val imageUrl = if (camera.liveImageUrl.contains("?")) {
                            "${camera.liveImageUrl}&_t=$refreshNonce"
                        } else {
                            "${camera.liveImageUrl}?_t=$refreshNonce"
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .listener(
                                    onStart = { isImageLoading = true },
                                    onSuccess = { _, _ ->
                                        isImageLoading = false
                                        isImageSuccess = true
                                    },
                                    onError = { _, _ ->
                                        isImageLoading = false
                                        isImageSuccess = false
                                    }
                                )
                                .build(),
                            contentDescription = camera.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isImageLoading && opticMode == "LIVE") {
                        CircularProgressIndicator(
                            color = Color(0xFFFF5252),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Tactical HUD Crosshairs & OSD Telemetry
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CAM: ${camera.id}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = liveAlpha))
                                )
                                Text(
                                    text = "REC ● 25.0 FPS",
                                    color = Color.Red,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COORDS: ${"%.4f".format(camera.latitude)}°N, ${"%.4f".format(abs(camera.longitude))}°W",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            val dirText = camera.viewDirectionDeg?.let { "DIR: ${it.toInt()}°" } ?: "DIR: 180°"
                            Text(
                                text = "$dirText • RADAR: 34 MPH",
                                color = ReconCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Spatial / Geo Coordinates & Status Bar
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, TacticalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "LOCATION: ${camera.locationDesc}",
                            color = TextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isImageSuccess && opticMode == "LIVE") {
                                "STREAM ACTIVE: HIGHWAYS & MUNICIPAL OPEN SURVEILLANCE FEED"
                            } else {
                                "TACTICAL OPTIC ACTIVE: NIGHT-VISION FLIR & VEHICULAR RADAR"
                            },
                            color = if (isImageSuccess && opticMode == "LIVE") ReconGreen else ReconAmber,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Mode Toggle Button (LIVE vs FLIR)
                Button(
                    onClick = {
                        opticMode = if (opticMode == "LIVE") "FLIR" else "LIVE"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (opticMode == "FLIR") ReconAmber else ReconCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (opticMode == "LIVE") "VIEW FLIR" else "VIEW LIVE",
                        color = if (opticMode == "FLIR") ReconAmber else ReconCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Refresh Stream Button
                Button(
                    onClick = {
                        refreshNonce = System.currentTimeMillis()
                        isImageLoading = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ReconCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "REFRESH", color = ReconCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                // Navigate / Pin Button
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(containerColor = ReconCyan.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = "Navigate", tint = ReconCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "NAVIGATE", color = ReconCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CLOSE", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

/**
 * Animated Tactical CCTV Viewfinder Canvas.
 * Renders real-time phosphor CRT raster scanlines, roadway perspective geometry,
 * vehicle detection target box, and crosshair reticles.
 */
@Composable
private fun TacticalCctvViewfinder(
    camera: PublicCctvCamera,
    opticMode: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cctv_anim")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_y"
    )
    val carOffset by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "car_offset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Dark CRT Phosphor / FLIR Night-Vision Gradient
        val bgGrad = if (opticMode == "FLIR") {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF030D08),
                    Color(0xFF062013),
                    Color(0xFF020A05)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0D1117),
                    Color(0xFF161B22),
                    Color(0xFF090D12)
                )
            )
        }
        drawRect(brush = bgGrad)

        // 2. Roadway Perspective Geometry (Dual Carriageway / Junction)
        val horizonY = h * 0.42f
        val vpX = w * 0.50f

        val roadPath = Path().apply {
            moveTo(vpX - w * 0.08f, horizonY)
            lineTo(vpX + w * 0.08f, horizonY)
            lineTo(w * 0.90f, h)
            lineTo(w * 0.10f, h)
            close()
        }
        val roadColor = if (opticMode == "FLIR") Color(0xFF0B291A) else Color(0xFF1C2128)
        drawPath(path = roadPath, color = roadColor)

        // Road lane center dashed lines
        val linePaint = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        drawLine(
            color = if (opticMode == "FLIR") Color(0xFF1DE9B6).copy(alpha = 0.5f) else ReconCyan.copy(alpha = 0.5f),
            start = Offset(vpX, horizonY),
            end = Offset(w * 0.50f, h),
            strokeWidth = 2.0f,
            pathEffect = linePaint
        )

        // Road borders
        drawLine(
            color = if (opticMode == "FLIR") Color(0xFF00E676).copy(alpha = 0.6f) else ReconCyan.copy(alpha = 0.6f),
            start = Offset(vpX - w * 0.08f, horizonY),
            end = Offset(w * 0.10f, h),
            strokeWidth = 1.5f
        )
        drawLine(
            color = if (opticMode == "FLIR") Color(0xFF00E676).copy(alpha = 0.6f) else ReconCyan.copy(alpha = 0.6f),
            start = Offset(vpX + w * 0.08f, horizonY),
            end = Offset(w * 0.90f, h),
            strokeWidth = 1.5f
        )

        // 3. Passing Traffic Silhouette / Optical Target Box
        val carScale = (0.22f + carOffset * 0.55f).coerceIn(0.15f, 0.90f)
        val carY = horizonY + (h - horizonY) * carScale
        val carX = vpX + (w * 0.18f) * carScale
        val boxW = 44f * carScale + 14f
        val boxH = 26f * carScale + 10f

        // Target Tracking Bounding Box
        drawRect(
            color = Color(0xFFFF5252).copy(alpha = 0.85f),
            topLeft = Offset(carX - boxW / 2f, carY - boxH / 2f),
            size = androidx.compose.ui.geometry.Size(boxW, boxH),
            style = Stroke(width = 1.5f)
        )
        // Corner ticks
        val tick = 6f
        val bLeft = carX - boxW / 2f
        val bTop = carY - boxH / 2f
        val bRight = carX + boxW / 2f
        val bBottom = carY + boxH / 2f
        drawLine(Color.White, Offset(bLeft, bTop), Offset(bLeft + tick, bTop), strokeWidth = 2f)
        drawLine(Color.White, Offset(bLeft, bTop), Offset(bLeft, bTop + tick), strokeWidth = 2f)
        drawLine(Color.White, Offset(bRight, bBottom), Offset(bRight - tick, bBottom), strokeWidth = 2f)
        drawLine(Color.White, Offset(bRight, bBottom), Offset(bRight, bBottom - tick), strokeWidth = 2f)

        // 4. Optical Center Reticle Crosshairs
        val reticleColor = if (opticMode == "FLIR") Color(0xFF00E676).copy(alpha = 0.45f) else ReconCyan.copy(alpha = 0.45f)
        drawLine(
            color = reticleColor,
            start = Offset(w * 0.5f - 24f, h * 0.5f),
            end = Offset(w * 0.5f + 24f, h * 0.5f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = reticleColor,
            start = Offset(w * 0.5f, h * 0.5f - 24f),
            end = Offset(w * 0.5f, h * 0.5f + 24f),
            strokeWidth = 1.2f
        )
        drawCircle(
            color = reticleColor.copy(alpha = 0.35f),
            radius = 16f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 1f)
        )

        // 5. Rolling Cathode Scanline Bar
        val sy = h * scanlineY
        drawLine(
            color = if (opticMode == "FLIR") Color(0xFF00E676).copy(alpha = 0.25f) else ReconCyan.copy(alpha = 0.25f),
            start = Offset(0f, sy),
            end = Offset(w, sy),
            strokeWidth = 3f
        )

        // Subtle horizontal scanline texture
        var scanY = 0f
        while (scanY < h) {
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = Offset(0f, scanY),
                end = Offset(w, scanY),
                strokeWidth = 1f
            )
            scanY += 4f
        }
    }
}
