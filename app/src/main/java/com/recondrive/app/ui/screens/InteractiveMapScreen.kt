package com.recondrive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.fog.FogPreferences
import com.recondrive.app.map.*
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Google Maps-style Interactive OpenStreetMap Navigation Screen
 * Supports free panning, pinch-to-zoom, compass bearing, UK city jump presets,
 * target waypoint routing, and toggling real-world Fog of War exploration shroud.
 */
@Composable
fun InteractiveMapScreen(
    currentLat: Double,
    currentLon: Double,
    currentHeading: Float,
    skinManager: SkinManager,
    offlineTileRepository: OfflineTileRepository,
    fogPreferences: FogPreferences = com.recondrive.app.ReconApplication.instance.fogPreferences,
    onNavigateToWaypoint: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onOpenSkins: () -> Unit = {},
    onOpenOfflinePacks: () -> Unit = {}
) {
    val mapSkin by skinManager.currentMapSkin.collectAsState()
    val fogSkin by skinManager.currentFogSkin.collectAsState()
    val fogRange by fogPreferences.fogRangeMeters.collectAsState()
    val fogOpacity by fogPreferences.fogOpacity.collectAsState()

    // Interactive Viewport State (Default: Vehicle Current Position)
    var cameraLat by remember { mutableDoubleStateOf(currentLat) }
    var cameraLon by remember { mutableDoubleStateOf(currentLon) }
    var zoom by remember { mutableFloatStateOf(14.0f) } // 6.0f (whole UK) to 18.5f (street level)
    var isFollowingGps by remember { mutableStateOf(true) }
    var isFogLayerActive by remember { mutableStateOf(true) }

    // Selected Target Waypoint
    var selectedWaypoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var waypointName by remember { mutableStateOf("Custom Tactical Waypoint") }

    // Sync with GPS if in follow mode
    LaunchedEffect(currentLat, currentLon) {
        if (isFollowingGps) {
            cameraLat = currentLat
            cameraLon = currentLon
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(mapSkin.backgroundColor))
    ) {
        // =====================================================================
        // 1. INTERACTIVE FULL-SCREEN OPENSTREETMAP CANVAS
        // =====================================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        // Panning Math: Convert screen pixel delta to Lat/Lon
                        val scale = 256.0 * 2.0.pow(zoom.toDouble())
                        val dLon = -(pan.x / scale) * 360.0
                        val dLat = (pan.y / scale) * 180.0

                        cameraLon = (cameraLon + dLon).coerceIn(
                            UkOsmDataEngine.UK_FULL_BOUNDS.minLon,
                            UkOsmDataEngine.UK_FULL_BOUNDS.maxLon
                        )
                        cameraLat = (cameraLat + dLat).coerceIn(
                            UkOsmDataEngine.UK_FULL_BOUNDS.minLat,
                            UkOsmDataEngine.UK_FULL_BOUNDS.maxLat
                        )

                        // Pinch Zoom
                        if (zoomFactor != 1.0f) {
                            zoom = (zoom + (zoomFactor - 1.0f) * 2.0f).coerceIn(6.0f, 18.5f)
                        }

                        isFollowingGps = false // User panned away from vehicle GPS
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = (zoom + 1.0f).coerceAtMost(18.5f)
                        },
                        onTap = { tapOffset ->
                            // Convert tap offset to Lat/Lon
                            val scale = 256.0 * 2.0.pow(zoom.toDouble())
                            val dx = tapOffset.x - (size.width / 2f)
                            val dy = tapOffset.y - (size.height / 2f)

                            val tapLon = cameraLon + (dx / scale) * 360.0
                            val tapLat = cameraLat - (dy / scale) * 180.0

                            selectedWaypoint = tapLat to tapLon
                            val dist = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, tapLat, tapLon)
                            waypointName = "Waypoint (%.1f km away)".format(dist)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Helper to project WGS84 Lat/Lon to Canvas screen coordinates
            fun project(lat: Double, lon: Double): Offset {
                val scale = 256.0 * 2.0.pow(zoom.toDouble())
                val cx = (cameraLon + 180.0) / 360.0 * scale
                val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / PI) / 2.0 * scale

                val x = (lon + 180.0) / 360.0 * scale
                val y = (1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * scale

                return Offset(
                    (x - cx + width / 2.0).toFloat(),
                    (y - cy + height / 2.0).toFloat()
                )
            }

            // Current viewport bounding box
            val scale = 256.0 * 2.0.pow(zoom.toDouble())
            val latSpan = (height / scale) * 180.0
            val lonSpan = (width / scale) * 360.0
            val viewBounds = UkBoundingBox(
                minLat = cameraLat - latSpan / 2.0,
                maxLat = cameraLat + latSpan / 2.0,
                minLon = cameraLon - lonSpan / 2.0,
                maxLon = cameraLon + lonSpan / 2.0
            )

            // A. Draw Water Bodies & Estuaries (Thames, Severn, Forth, Clyde, Lochs)
            val waterColor = Color(mapSkin.waterColor)
            for (water in UkOsmDataEngine.MAJOR_WATERWAYS) {
                val path = Path()
                var started = false
                for (pt in water.points) {
                    val sc = project(pt.first, pt.second)
                    if (!started) {
                        path.moveTo(sc.x, sc.y)
                        started = true
                    } else {
                        path.lineTo(sc.x, sc.y)
                    }
                }
                drawPath(path = path, color = waterColor, style = Stroke(width = (zoom * 0.8f).coerceAtLeast(3f)))
            }

            // B. Draw Secondary & Local Street Grid (when zoomed in)
            if (zoom >= 11.5f) {
                val localGrid = UkOsmDataEngine.getLocalStreetGridForBounds(viewBounds, zoom)
                for (road in localGrid) {
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
                    val strokeW = if (road.type == OsmRoadType.PRIMARY) 2.5f else 1.5f
                    drawPath(
                        path = path,
                        color = Color(mapSkin.minorRoadColor),
                        style = Stroke(width = strokeW)
                    )
                }
            }

            // C. Draw Major UK Arterials & Motorways
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

                when (road.type) {
                    OsmRoadType.MOTORWAY -> {
                        // Casing / glow
                        drawPath(
                            path = path,
                            color = Color(mapSkin.highwayColor).copy(alpha = 0.3f),
                            style = Stroke(width = (zoom * 0.7f).coerceIn(4f, 10f))
                        )
                        // Core corridor
                        drawPath(
                            path = path,
                            color = Color(mapSkin.highwayColor),
                            style = Stroke(width = (zoom * 0.4f).coerceIn(2.5f, 6f))
                        )
                    }
                    OsmRoadType.TRUNK -> {
                        drawPath(
                            path = path,
                            color = Color(mapSkin.routeColor),
                            style = Stroke(width = (zoom * 0.35f).coerceIn(2f, 5f))
                        )
                    }
                    else -> {
                        drawPath(
                            path = path,
                            color = Color(mapSkin.minorRoadColor),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // D. Draw Fog of War Shroud & Explored Trails (If enabled)
            val vehicleScreenPos = project(currentLat, currentLon)
            if (isFogLayerActive) {
                // Explored track glow around vehicle trajectory
                val exploredTrail = Path().apply {
                    val p1 = project(currentLat - 0.008, currentLon - 0.005)
                    val p2 = project(currentLat - 0.004, currentLon - 0.002)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(vehicleScreenPos.x, vehicleScreenPos.y)
                }
                drawPath(
                    path = exploredTrail,
                    color = Color(fogSkin.exploredRoadGlowColor),
                    style = Stroke(width = 6f)
                )

                // Surveillance Beam Cleared Boundary
                val screenBeamRadius = (fogRange * 2.0.pow(zoom.toDouble() - 14.0)).toFloat().coerceIn(40f, 400f)
                drawCircle(
                    color = Color(fogSkin.perimeterGlowColor),
                    radius = screenBeamRadius,
                    center = vehicleScreenPos,
                    style = Stroke(width = fogSkin.borderStrokeWidth)
                )
            }

            // E. Draw Waypoint Route Corridors (If destination selected)
            selectedWaypoint?.let { wp ->
                val wpScreenPos = project(wp.first, wp.second)
                // Route line from vehicle to waypoint
                drawLine(
                    color = Color(mapSkin.routeColor),
                    start = vehicleScreenPos,
                    end = wpScreenPos,
                    strokeWidth = 4f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
                )
                // Target Beacon Marker
                drawCircle(color = Color(mapSkin.routeColor), radius = 10f, center = wpScreenPos)
                drawCircle(color = Color(mapSkin.routeColor).copy(alpha = 0.4f), radius = 22f, center = wpScreenPos, style = Stroke(2f))
            }

            // F. Draw UK City / Settlement Nodes
            for (place in UkOsmDataEngine.MAJOR_CITIES) {
                val sc = project(place.lat, place.lon)
                // Filter if offscreen
                if (sc.x in -50f..(width + 50f) && sc.y in -50f..(height + 50f)) {
                    val radius = if (place.isCity) 5f else 3f
                    val col = if (place.name == "London") Color(mapSkin.highwayColor) else Color(mapSkin.minorRoadColor)
                    drawCircle(color = col, radius = radius, center = sc)
                }
            }

            // G. Draw Vehicle GPS Cursor
            drawCircle(color = Color(mapSkin.vehicleCursorColor).copy(alpha = 0.25f), radius = 24f, center = vehicleScreenPos)
            val cursorPath = Path().apply {
                val headRad = Math.toRadians((currentHeading - 90.0).toDouble())
                val tipX = vehicleScreenPos.x + 16f * cos(headRad).toFloat()
                val tipY = vehicleScreenPos.y + 16f * sin(headRad).toFloat()
                val leftX = vehicleScreenPos.x + 10f * cos(headRad + 2.5).toFloat()
                val leftY = vehicleScreenPos.y + 10f * sin(headRad + 2.5).toFloat()
                val rightX = vehicleScreenPos.x + 10f * cos(headRad - 2.5).toFloat()
                val rightY = vehicleScreenPos.y + 10f * sin(headRad - 2.5).toFloat()

                moveTo(tipX, tipY)
                lineTo(leftX, leftY)
                lineTo(vehicleScreenPos.x, vehicleScreenPos.y)
                lineTo(rightX, rightY)
                close()
            }
            drawPath(path = cursorPath, color = Color(mapSkin.vehicleCursorColor))
        }

        // =====================================================================
        // 2. TOP FLOATING SEARCH & UK CITY JUMP BAR
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp)),
                color = TacticalSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = ReconCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "United Kingdom OSM • Tap map to drop waypoint",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    // Offline pack badge
                    Surface(
                        color = TacticalObsidian,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen)
                    ) {
                        Text(
                            text = "UK OSM",
                            color = ReconGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Jump UK City Chips (Google Maps-style horizontal scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cityPresets = listOf(
                    "🏛️ London" to (51.5074 to -0.1278),
                    "🐝 Manchester" to (53.4808 to -2.2426),
                    "🏭 Birmingham" to (52.4862 to -1.8904),
                    "🏰 Edinburgh" to (55.9533 to -3.1883),
                    "🐉 Cardiff" to (51.4816 to -3.1791),
                    "⚓ Belfast" to (54.5973 to -5.9301),
                    "🚢 Liverpool" to (53.4084 to -2.9916),
                    "🌉 Bristol" to (51.4545 to -2.5879),
                    "⛰️ Highlands" to (56.8198 to -5.1052),
                    "🏖️ Cornwall" to (50.1186 to -5.5372)
                )

                for ((label, coords) in cityPresets) {
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier.clickable {
                            cameraLat = coords.first
                            cameraLon = coords.second
                            zoom = 13.5f
                            isFollowingGps = false
                        }
                    ) {
                        Text(
                            text = label,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 3. RIGHT FLOATING TACTICAL CONTROLS (Google Maps-style)
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compass Bearing (Reset North)
            FloatingActionButton(
                onClick = { /* Compass reset */ },
                containerColor = TacticalSurface,
                contentColor = ReconCyan,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Navigation, contentDescription = "Compass", modifier = Modifier.size(18.dp))
            }

            // Fog of War Layer Toggle
            FloatingActionButton(
                onClick = { isFogLayerActive = !isFogLayerActive },
                containerColor = if (isFogLayerActive) ReconCyan else TacticalSurface,
                contentColor = if (isFogLayerActive) TacticalObsidian else TextSecondary,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (isFogLayerActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Fog Toggle",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Skin Selector Shortcut
            FloatingActionButton(
                onClick = onOpenSkins,
                containerColor = TacticalSurface,
                contentColor = Color(mapSkin.highwayColor),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Palette, contentDescription = "Skins", modifier = Modifier.size(18.dp))
            }

            // Zoom In (+)
            FloatingActionButton(
                onClick = { zoom = (zoom + 1.0f).coerceAtMost(18.5f) },
                containerColor = TacticalSurface,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            // Zoom Out (−)
            FloatingActionButton(
                onClick = { zoom = (zoom - 1.0f).coerceAtLeast(6.0f) },
                containerColor = TacticalSurface,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GPS Re-Center "My Location" Floating Action Button
            FloatingActionButton(
                onClick = {
                    cameraLat = currentLat
                    cameraLon = currentLon
                    zoom = 14.5f
                    isFollowingGps = true
                },
                containerColor = if (isFollowingGps) ReconCyan else TacticalSurface,
                contentColor = if (isFollowingGps) TacticalObsidian else ReconCyan,
                shape = CircleShape,
                modifier = Modifier
                    .size(50.dp)
                    .border(
                        1.dp,
                        if (isFollowingGps) ReconCyan else ReconCyan.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // =====================================================================
        // 4. BOTTOM TELEMETRY & SCALE OVERLAY
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
        ) {
            Surface(
                color = TacticalSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = "Z%.1f • %.4f°N, %.4f°W".format(zoom, cameraLat, abs(cameraLon)),
                        color = ReconCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFollowingGps) "LOCK: GPS VEHICLE" else "PAN: FREE ROAM",
                        color = if (isFollowingGps) ReconGreen else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // =====================================================================
        // 5. WAYPOINT TARGET SELECTION SHEET (Appears when map is tapped)
        // =====================================================================
        AnimatedVisibility(
            visible = selectedWaypoint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp)
        ) {
            selectedWaypoint?.let { wp ->
                val distKm = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, wp.first, wp.second)
                val etaMinutes = ((distKm / 60.0) * 60).toInt().coerceAtLeast(1)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color(mapSkin.routeColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "TACTICAL DESTINATION",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = waypointName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            IconButton(onClick = { selectedWaypoint = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Coords: %.4f°N, %.4f°W  •  Distance: %.1f km  •  ETA: ~%d min".format(wp.first, abs(wp.second), distKm, etaMinutes),
                            color = ReconCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onNavigateToWaypoint(wp.first, wp.second)
                                    selectedWaypoint = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NAVIGATE HERE",
                                    color = TacticalObsidian,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            OutlinedButton(
                                onClick = { selectedWaypoint = null },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(text = "CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
