package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.map.OsmRoadSegment
import com.recondrive.app.map.OsmRoadType
import com.recondrive.app.map.UkBoundingBox
import com.recondrive.app.map.UkOsmDataEngine
import com.recondrive.app.scanner.DetectedRfTarget
import com.recondrive.app.scanner.RfCategory
import com.recondrive.app.scanner.TacticalRfScannerManager
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Tactical SIGINT RF Spectrum & Live Wardrive Scanner Screen.
 * Features an expansive interactive live radar map showing real OpenStreetMap road networks,
 * illuminated street corridors, 360° sweeping phosphor radar beam, and detected connected
 * vehicles (Tesla, Ford, Dashcams) and surveillance optics (Hikvision, Ring, ANPR) plotted on the streets.
 */
@Composable
fun TacticalScannerScreen(
    scannerManager: TacticalRfScannerManager = com.recondrive.app.ReconApplication.instance.rfScannerManager,
    currentLat: Double = 50.8555,
    currentLon: Double = -0.2449,
    currentHeading: Float = 0f,
    deviceHeading: Float = currentHeading,
    currentSpeedKmh: Float = 0f,
    onNavigateToLocation: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
) {
    val detectedTargets by scannerManager.detectedTargets.collectAsState()
    val isScanning by scannerManager.isScanning.collectAsState()
    val isSimulation by scannerManager.isSimulationEnabled.collectAsState()

    var selectedFilter by remember { mutableStateOf<RfCategory?>(null) } // null = ALL
    var selectedTargetId by remember { mutableStateOf<String?>(null) }

    // Map viewport state
    var cameraLat by remember { mutableDoubleStateOf(currentLat) }
    var cameraLon by remember { mutableDoubleStateOf(currentLon) }
    var isFollowingVehicle by remember { mutableStateOf(true) }

    // Range selection: 50m, 100m, 250m, 500m
    var selectedRangeMeters by remember { mutableFloatStateOf(100f) }

    // Derive zoom level from range in meters
    val zoom = remember(selectedRangeMeters) {
        when (selectedRangeMeters) {
            50f -> 18.2f
            100f -> 17.2f
            250f -> 16.0f
            500f -> 14.8f
            else -> 17.2f
        }
    }

    // Sync current location with scanner manager and viewport
    LaunchedEffect(currentLat, currentLon) {
        scannerManager.updateLastKnownLocation(currentLat, currentLon)
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

    val vehicleCount = detectedTargets.count { it.category == RfCategory.VEHICLE }
    val cctvCount = detectedTargets.count { it.category == RfCategory.CCTV_SURVEILLANCE }
    val droneCount = detectedTargets.count { it.category == RfCategory.DRONE_UAV }
    val wifiCount = detectedTargets.count { it.category == RfCategory.ACCESS_POINT }

    val filteredTargets = remember(detectedTargets, selectedFilter) {
        if (selectedFilter == null) {
            detectedTargets
        } else {
            detectedTargets.filter { it.category == selectedFilter }
        }
    }

    // Speed in MPH
    val speedMph = remember(currentSpeedKmh) { (currentSpeedKmh * 0.621371f).roundToInt() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // =====================================================================
        // 1. TOP STATUS ROW (SIGINT Scanner Title & Live Sweep LED)
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
                    text = "SWEEPING 2.4GHz • 5.0GHz • BLUETOOTH LE",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Live / Demo Mode Toggle Pill
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSimulation) ReconAmber else ReconGreen),
                modifier = Modifier.clickable { scannerManager.toggleSimulation() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSimulation) ReconAmber else ReconGreen)
                    )
                    Text(
                        text = if (isSimulation) "DEMO" else "LIVE RF",
                        color = if (isSimulation) ReconAmber else ReconGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Rescan Sweep Button
            IconButton(
                onClick = { scannerManager.triggerImmediateSweep(currentLat, currentLon) },
                modifier = Modifier
                    .size(30.dp)
                    .background(TacticalSurface, CircleShape)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sweep Spectrum",
                    tint = ReconCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =====================================================================
        // 2. QUICK THREAT TELEMETRY PILLS (Cars, CCTV, Drone, Total)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TelemetryThreatBadge(
                label = "CARS",
                count = vehicleCount,
                color = ReconCyan,
                icon = Icons.Default.DirectionsCar,
                selected = selectedFilter == RfCategory.VEHICLE,
                onClick = {
                    selectedFilter = if (selectedFilter == RfCategory.VEHICLE) null else RfCategory.VEHICLE
                },
                modifier = Modifier.weight(1f)
            )

            TelemetryThreatBadge(
                label = "CCTV",
                count = cctvCount,
                color = Color(0xFFFF5252),
                icon = Icons.Default.Videocam,
                selected = selectedFilter == RfCategory.CCTV_SURVEILLANCE,
                onClick = {
                    selectedFilter = if (selectedFilter == RfCategory.CCTV_SURVEILLANCE) null else RfCategory.CCTV_SURVEILLANCE
                },
                modifier = Modifier.weight(1f)
            )

            if (droneCount > 0) {
                TelemetryThreatBadge(
                    label = "DRONE",
                    count = droneCount,
                    color = Color(0xFFBA68C8),
                    icon = Icons.Default.Flight,
                    selected = selectedFilter == RfCategory.DRONE_UAV,
                    onClick = {
                        selectedFilter = if (selectedFilter == RfCategory.DRONE_UAV) null else RfCategory.DRONE_UAV
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            TelemetryThreatBadge(
                label = "TOTAL",
                count = detectedTargets.size,
                color = ReconGreen,
                icon = Icons.Default.Sensors,
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 3. HERO LIVE SCANNER MAP WITH REAL ROADS & 360° RADAR SWEEP
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
                zoom = zoom,
                currentLat = currentLat,
                currentLon = currentLon,
                currentHeading = currentHeading,
                deviceHeading = deviceHeading,
                speedMph = speedMph,
                rangeMeters = selectedRangeMeters,
                targets = filteredTargets,
                selectedTargetId = selectedTargetId,
                onSelectTarget = { target ->
                    selectedTargetId = target.id
                    cameraLat = target.detectedLat ?: currentLat
                    cameraLon = target.detectedLon ?: currentLon
                    isFollowingVehicle = false
                },
                onPanCamera = { deltaLat, deltaLon ->
                    cameraLat += deltaLat
                    cameraLon += deltaLon
                    isFollowingVehicle = false
                }
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
                                .background(ReconGreen)
                        )
                        Text(
                            text = "RNG: ${selectedRangeMeters.toInt()}m • Z${"%.1f".format(zoom)}",
                            color = ReconCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "${"%.4f".format(cameraLat)}°N, ${"%.4f".format(abs(cameraLon))}°W",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Top-Right Quick Range Selector & Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Range Chips
                listOf(50f, 100f, 250f, 500f).forEach { r ->
                    val isSelected = selectedRangeMeters == r
                    Surface(
                        color = if (isSelected) ReconCyan.copy(alpha = 0.25f) else TacticalObsidian.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ReconCyan else TacticalBorder),
                        modifier = Modifier.clickable { selectedRangeMeters = r }
                    ) {
                        Text(
                            text = "${r.toInt()}m",
                            color = if (isSelected) ReconCyan else TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
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
        // 4. CATEGORY FILTER CHIPS
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChipItem(
                label = "ALL (${detectedTargets.size})",
                selected = selectedFilter == null,
                activeColor = ReconCyan,
                onClick = { selectedFilter = null }
            )
            FilterChipItem(
                label = "🚗 CARS ($vehicleCount)",
                selected = selectedFilter == RfCategory.VEHICLE,
                activeColor = ReconCyan,
                onClick = { selectedFilter = RfCategory.VEHICLE }
            )
            FilterChipItem(
                label = "📷 CCTV ($cctvCount)",
                selected = selectedFilter == RfCategory.CCTV_SURVEILLANCE,
                activeColor = Color(0xFFFF5252),
                onClick = { selectedFilter = RfCategory.CCTV_SURVEILLANCE }
            )
            if (droneCount > 0) {
                FilterChipItem(
                    label = "🛸 DRONE ($droneCount)",
                    selected = selectedFilter == RfCategory.DRONE_UAV,
                    activeColor = Color(0xFFBA68C8),
                    onClick = { selectedFilter = RfCategory.DRONE_UAV }
                )
            }
            FilterChipItem(
                label = "📡 WIFI ($wifiCount)",
                selected = selectedFilter == RfCategory.ACCESS_POINT,
                activeColor = TextSecondary,
                onClick = { selectedFilter = RfCategory.ACCESS_POINT }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 5. HORIZONTAL TARGET CAROUSEL (Vehicles, CCTV, Drones)
        // =====================================================================
        if (filteredTargets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
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
                    .height(105.dp),
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
                            val pinLat = target.detectedLat ?: (currentLat + 0.00015)
                            val pinLon = target.detectedLon ?: (currentLon + 0.00015)
                            cameraLat = pinLat
                            cameraLon = pinLon
                            isFollowingVehicle = false
                        },
                        onPinToMap = {
                            val pinLat = target.detectedLat ?: (currentLat + 0.00015)
                            val pinLon = target.detectedLon ?: (currentLon + 0.00015)
                            onNavigateToLocation(target.name, pinLat, pinLon)
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
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
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
 * Combines real OpenStreetMap road vectors, animated 360° sweeping phosphor radar beam,
 * vehicle heading reticle, and plotted vehicles / CCTV cameras directly on the streets.
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
    selectedTargetId: String?,
    onSelectTarget: (DetectedRfTarget) -> Unit,
    onPanCamera: (deltaLat: Double, deltaLon: Double) -> Unit
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

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val scale = 256.0 * 2.0.pow(zoom.toDouble())
                    val deltaLon = -(dragAmount.x / scale) * 360.0
                    val deltaLat = (dragAmount.y / scale) * 180.0
                    onPanCamera(deltaLat, deltaLon)
                }
            }
            .pointerInput(targets, cameraLat, cameraLon, zoom) {
                detectTapGestures { tapOffset ->
                    val scale = 256.0 * 2.0.pow(zoom.toDouble())
                    val cx = (cameraLon + 180.0) / 360.0 * scale
                    val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / Math.PI) / 2.0 * scale

                    for (target in targets) {
                        val tLat = target.detectedLat ?: run {
                            val angleRad = (abs(target.id.hashCode() % 360)) * (Math.PI / 180.0)
                            val dist = target.estimatedDistanceMeters.coerceIn(5.0, 450.0)
                            currentLat + (dist * cos(angleRad)) / 111320.0
                        }
                        val tLon = target.detectedLon ?: run {
                            val angleRad = (abs(target.id.hashCode() % 360)) * (Math.PI / 180.0)
                            val dist = target.estimatedDistanceMeters.coerceIn(5.0, 450.0)
                            currentLon + (dist * sin(angleRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
                        }

                        val tx = (tLon + 180.0) / 360.0 * scale
                        val ty = (1.0 - ln(tan(Math.toRadians(tLat)) + 1.0 / cos(Math.toRadians(tLat))) / Math.PI) / 2.0 * scale

                        val screenX = (tx - cx + size.width / 2.0).toFloat()
                        val screenY = (ty - cy + size.height / 2.0).toFloat()

                        val distToTap = hypot(tapOffset.x - screenX, tapOffset.y - screenY)
                        if (distToTap < 45f) { // Tap radius
                            onSelectTarget(target)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        // Mercator projection scale & camera center
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

        // Viewport bounding box
        val latSpan = (height / scale) * 180.0
        val lonSpan = (width / scale) * 360.0
        val viewBounds = UkBoundingBox(
            minLat = cameraLat - latSpan / 2.0,
            maxLat = cameraLat + latSpan / 2.0,
            minLon = cameraLon - lonSpan / 2.0,
            maxLon = cameraLon + lonSpan / 2.0
        )

        // ---------------------------------------------------------------------
        // A. BACKGROUND TACTICAL GRID
        // ---------------------------------------------------------------------
        drawRect(color = TacticalObsidian)

        // Subtle tactical square grid overlay
        val gridSpacing = 40.dp.toPx()
        var gx = 0f
        while (gx < width) {
            drawLine(
                color = Color(0xFF13222E),
                start = Offset(gx, 0f),
                end = Offset(gx, height),
                strokeWidth = 1f
            )
            gx += gridSpacing
        }
        var gy = 0f
        while (gy < height) {
            drawLine(
                color = Color(0xFF13222E),
                start = Offset(0f, gy),
                end = Offset(width, gy),
                strokeWidth = 1f
            )
            gy += gridSpacing
        }

        // ---------------------------------------------------------------------
        // B. REAL OPENSTREETMAP ROAD NETWORK (Local Streets & Arterials)
        // ---------------------------------------------------------------------
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
                // Secondary road casing
                drawPath(
                    path = path,
                    color = Color(0xFF07121A),
                    style = Stroke(width = 5.0f, cap = StrokeCap.Round)
                )
                // Secondary road core line
                drawPath(
                    path = path,
                    color = Color(0xFF2C566C),
                    style = Stroke(width = 2.8f, cap = StrokeCap.Round)
                )
            } else {
                // Local residential street casing
                drawPath(
                    path = path,
                    color = Color(0xFF050E14),
                    style = Stroke(width = 3.6f, cap = StrokeCap.Round)
                )
                // Local residential street core line
                drawPath(
                    path = path,
                    color = Color(0xFF1B3648),
                    style = Stroke(width = 1.8f, cap = StrokeCap.Round)
                )
            }
        }

        // Major Arterials & Motorways intersecting view
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

            // Motorway / Primary dual-carriageway casing
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 8.0f, cap = StrokeCap.Round)
            )
            // Cyber glow
            drawPath(
                path = path,
                color = ReconCyan.copy(alpha = 0.35f),
                style = Stroke(width = 5.5f, cap = StrokeCap.Round)
            )
            // Corridor core line
            drawPath(
                path = path,
                color = ReconCyan.copy(alpha = 0.85f),
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
        }

        // ---------------------------------------------------------------------
        // C. TACTICAL RADAR SCOPE OVERLAY (Range Rings, Reticles, Cardinals)
        // ---------------------------------------------------------------------
        val vehiclePos = project(currentLat, currentLon)
        val maxScopeRadius = min(width, height) * 0.44f

        // Concentric distance rings
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

        // Crosshairs through vehicle position
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

        // Cardinal Direction labels along radar ring
        val cardinalPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(190, 0, 229, 255)
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", vehiclePos.x, vehiclePos.y - maxScopeRadius - 6f, cardinalPaint)
            drawText("S", vehiclePos.x, vehiclePos.y + maxScopeRadius + 22f, cardinalPaint)
            drawText("E", vehiclePos.x + maxScopeRadius + 14f, vehiclePos.y + 8f, cardinalPaint)
            drawText("W", vehiclePos.x - maxScopeRadius - 14f, vehiclePos.y + 8f, cardinalPaint)
        }

        // ---------------------------------------------------------------------
        // D. ANIMATED 360° SWEEPING RADAR BEAM & PHOSPHOR ILLUMINATION
        // ---------------------------------------------------------------------
        val sweepRad = sweepAngle * (Math.PI.toFloat() / 180f)
        val beamEndX = vehiclePos.x + maxScopeRadius * cos(sweepRad)
        val beamEndY = vehiclePos.y + maxScopeRadius * sin(sweepRad)

        // Trailing Phosphor Glow Wedge behind radar beam
        val trailPath = Path().apply {
            moveTo(vehiclePos.x, vehiclePos.y)
            val trailSpan = 35f // 35 degrees trailing arc
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
                colors = listOf(ReconGreen.copy(alpha = 0.22f), ReconGreen.copy(alpha = 0.03f), Color.Transparent),
                center = vehiclePos,
                radius = maxScopeRadius
            )
        )

        // Sharp Leading Beam Line
        drawLine(
            color = ReconGreen,
            start = vehiclePos,
            end = Offset(beamEndX, beamEndY),
            strokeWidth = 2.0f
        )
        // Leading beam tip dot
        drawCircle(
            color = ReconGreen,
            radius = 3.5f,
            center = Offset(beamEndX, beamEndY)
        )

        // ---------------------------------------------------------------------
        // E. DETECTED TARGET BLIPS PLOTTED DIRECTLY ON THE ROADS
        // ---------------------------------------------------------------------
        for (target in targets) {
            // Target coordinates: use detected coordinates or project near vehicle based on distance and hash angle
            val tLat = target.detectedLat ?: run {
                val angleRad = (abs(target.id.hashCode() % 360)) * (Math.PI / 180.0)
                val dist = target.estimatedDistanceMeters.coerceIn(5.0, 450.0)
                currentLat + (dist * cos(angleRad)) / 111320.0
            }
            val tLon = target.detectedLon ?: run {
                val angleRad = (abs(target.id.hashCode() % 360)) * (Math.PI / 180.0)
                val dist = target.estimatedDistanceMeters.coerceIn(5.0, 450.0)
                currentLon + (dist * sin(angleRad)) / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.1))
            }

            val blipPos = project(tLat, tLon)

            // Check if within visible bounds
            if (blipPos.x < -40 || blipPos.x > width + 40 || blipPos.y < -40 || blipPos.y > height + 40) {
                continue
            }

            val categoryColor = when (target.category) {
                RfCategory.VEHICLE -> ReconCyan
                RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFF5252)
                RfCategory.DRONE_UAV -> Color(0xFFBA68C8)
                RfCategory.ACCESS_POINT -> Color(0xFF64B5F6)
            }

            // Radar illumination detection (angle difference between sweep beam and target polar angle)
            val dx = blipPos.x - vehiclePos.x
            val dy = blipPos.y - vehiclePos.y
            var targetAngle = atan2(dy, dx) * (180f / Math.PI.toFloat())
            if (targetAngle < 0f) targetAngle += 360f

            val angleDiff = abs((sweepAngle - targetAngle + 360f) % 360f)
            val isSwept = angleDiff < 32f
            val isSelected = target.id == selectedTargetId

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
            drawCircle(
                color = categoryColor,
                radius = if (isSelected) 5.5f else 4.0f,
                center = blipPos
            )

            // Selected crosshairs bracket
            if (isSelected) {
                val bracketSize = 12f
                drawLine(
                    color = ReconAmber,
                    start = Offset(blipPos.x - bracketSize, blipPos.y),
                    end = Offset(blipPos.x + bracketSize, blipPos.y),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = ReconAmber,
                    start = Offset(blipPos.x, blipPos.y - bracketSize),
                    end = Offset(blipPos.x, blipPos.y + bracketSize),
                    strokeWidth = 1.5f
                )
            }

            // Tactical Callout Tag (visible when swept, selected, or zoomed in)
            if (isSwept || isSelected || zoom >= 17.5f) {
                val tagPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 20f
                    typeface = android.graphics.Typeface.MONOSPACE
                    isFakeBoldText = true
                }
                val subPaint = android.graphics.Paint().apply {
                    color = when (target.category) {
                        RfCategory.VEHICLE -> android.graphics.Color.parseColor("#00E5FF")
                        RfCategory.CCTV_SURVEILLANCE -> android.graphics.Color.parseColor("#FF5252")
                        RfCategory.DRONE_UAV -> android.graphics.Color.parseColor("#BA68C8")
                        RfCategory.ACCESS_POINT -> android.graphics.Color.parseColor("#64B5F6")
                    }
                    textSize = 17f
                    typeface = android.graphics.Typeface.MONOSPACE
                }

                val tagTitle = when (target.category) {
                    RfCategory.VEHICLE -> "🚗 ${target.name}"
                    RfCategory.CCTV_SURVEILLANCE -> "📷 ${target.name}"
                    RfCategory.DRONE_UAV -> "🛸 ${target.name}"
                    RfCategory.ACCESS_POINT -> "📡 ${target.name}"
                }
                val tagSub = "${target.rssi}dBm • ~${target.estimatedDistanceMeters.toInt()}m • ${target.vendor}"

                drawContext.canvas.nativeCanvas.apply {
                    val boxX = blipPos.x + 12f
                    val boxY = blipPos.y - 18f
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(220, 10, 16, 22)
                    }
                    val borderPaint = android.graphics.Paint().apply {
                        color = subPaint.color
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2f
                    }

                    val titleWidth = tagPaint.measureText(tagTitle)
                    val subWidth = subPaint.measureText(tagSub)
                    val boxWidth = max(titleWidth, subWidth) + 16f
                    val boxHeight = 44f

                    // Draw background box
                    val rect = android.graphics.RectF(boxX, boxY - 20f, boxX + boxWidth, boxY + boxHeight - 20f)
                    drawRoundRect(rect, 6f, 6f, bgPaint)
                    drawRoundRect(rect, 6f, 6f, borderPaint)

                    // Text lines
                    drawText(tagTitle, boxX + 8f, boxY, tagPaint)
                    drawText(tagSub, boxX + 8f, boxY + 20f, subPaint)
                }
            }
        }

        // ---------------------------------------------------------------------
        // F. VEHICLE POSITION RETICLE & DUAL HEADING INDICATORS
        // ---------------------------------------------------------------------
        // Pulsing Sonar Ring
        drawCircle(
            color = ReconCyan.copy(alpha = (1.6f - pulseScale).coerceIn(0f, 0.6f)),
            radius = 16f * pulseScale,
            center = vehiclePos,
            style = Stroke(width = 1.5f)
        )

        // Device Orientation Heading Line (Amber)
        val devRad = Math.toRadians((deviceHeading - 90.0)).toFloat()
        val devEndX = vehiclePos.x + 38f * cos(devRad)
        val devEndY = vehiclePos.y + 38f * sin(devRad)
        drawLine(
            color = ReconAmber.copy(alpha = 0.85f),
            start = vehiclePos,
            end = Offset(devEndX, devEndY),
            strokeWidth = 2.0f
        )

        // GPS Heading Direction Line (Cyan)
        val gpsRad = Math.toRadians((currentHeading - 90.0)).toFloat()
        val gpsEndX = vehiclePos.x + 48f * cos(gpsRad)
        val gpsEndY = vehiclePos.y + 48f * sin(gpsRad)
        drawLine(
            color = ReconCyan,
            start = vehiclePos,
            end = Offset(gpsEndX, gpsEndY),
            strokeWidth = 2.5f
        )
        // Arrowhead on GPS direction
        val arrowP1X = gpsEndX - 8f * cos(gpsRad - 0.4f)
        val arrowP1Y = gpsEndY - 8f * sin(gpsRad - 0.4f)
        val arrowP2X = gpsEndX - 8f * cos(gpsRad + 0.4f)
        val arrowP2Y = gpsEndY - 8f * sin(gpsRad + 0.4f)
        val arrowPath = Path().apply {
            moveTo(gpsEndX, gpsEndY)
            lineTo(arrowP1X, arrowP1Y)
            lineTo(arrowP2X, arrowP2Y)
            close()
        }
        drawPath(path = arrowPath, color = ReconCyan)

        // Vehicle Center Core Dot
        drawCircle(color = ReconCyan, radius = 5f, center = vehiclePos)
        drawCircle(color = TacticalObsidian, radius = 2.5f, center = vehiclePos)

        // Speed tag below vehicle if moving
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
 * Horizontal Card for Detected Target Carousel (Vehicles, CCTV, IoT)
 */
@Composable
private fun RfTargetHorizontalCard(
    target: DetectedRfTarget,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPinToMap: () -> Unit
) {
    val categoryColor = when (target.category) {
        RfCategory.VEHICLE -> ReconCyan
        RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFF5252)
        RfCategory.DRONE_UAV -> Color(0xFFBA68C8)
        RfCategory.ACCESS_POINT -> TextSecondary
    }

    val iconVector = when (target.category) {
        RfCategory.VEHICLE -> Icons.Default.DirectionsCar
        RfCategory.CCTV_SURVEILLANCE -> Icons.Default.Videocam
        RfCategory.DRONE_UAV -> Icons.Default.Flight
        RfCategory.ACCESS_POINT -> Icons.Default.Wifi
    }

    Card(
        modifier = Modifier
            .width(260.dp)
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
            // Top Row: Icon, Name, and RSSI badge
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
                    Text(
                        text = target.name,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        text = "${target.vendor} • ${target.protocol}",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Signal indicator
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

            // Bottom Row: MAC address, distance, and Pin button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "~${"%.0f".format(target.estimatedDistanceMeters)}m AWAY • ${target.macAddress}",
                        color = categoryColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onPinToMap() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = categoryColor, modifier = Modifier.size(10.dp))
                        Text(text = "PIN", color = categoryColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
