package com.recondrive.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*
import com.recondrive.app.update.AppUpdateManager
import com.recondrive.app.update.UpdateState

@Composable
fun TacticalHudScreen(
    stats: ExplorationStats,
    skinManager: SkinManager,
    updateManager: AppUpdateManager = com.recondrive.app.ReconApplication.instance.updateManager,
    osmMapUpdateManager: com.recondrive.app.map.OsmMapUpdateManager = com.recondrive.app.ReconApplication.instance.osmMapUpdateManager,
    fogPreferences: com.recondrive.app.fog.FogPreferences = com.recondrive.app.ReconApplication.instance.fogPreferences,
    currentSpeedKmh: Float = 48f,
    currentHeading: Float = 45f,
    currentLat: Double = 51.5074,
    currentLon: Double = -0.1278,
    onSearchClick: () -> Unit = {},
    onOfflinePacksClick: () -> Unit = {},
    onToggleBackgroundRadar: () -> Unit = {}
) {
    var showSkinDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }

    val mapSkin by skinManager.currentMapSkin.collectAsState()
    val fogSkin by skinManager.currentFogSkin.collectAsState()
    val currentRange by fogPreferences.fogRangeMeters.collectAsState()
    val currentOpacity by fogPreferences.fogOpacity.collectAsState()
    val updateState by updateManager.updateState.collectAsState()
    val mapUpdateState by osmMapUpdateManager.mapUpdateState.collectAsState()
    val isServiceRunning by com.recondrive.app.service.ExplorationTrackingService.isRunning.collectAsState()

    if (showSyncDialog) {
        TacticalStartupUpdateDialog(
            appUpdateManager = updateManager,
            osmMapUpdateManager = osmMapUpdateManager,
            onDismiss = { showSyncDialog = false }
        )
    }

    if (showSkinDialog) {
        SkinSelectorDialog(
            skinManager = skinManager,
            fogPreferences = fogPreferences,
            onDismiss = { showSkinDialog = false }
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            updateManager = updateManager,
            onDismiss = { showUpdateDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(16.dp)
    ) {
        // 1. Top Status Banner with Skins Toggle Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECON // TACTICAL RADAR",
                    color = Color(mapSkin.highwayColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "GRID: %.4f°N, %.4f°W".format(currentLat, kotlin.math.abs(currentLon)),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // In-App GitHub Releases Update Button (ONLY visible when a new version is available)
                val isUpdateAvailable = updateState is UpdateState.UpdateAvailable
                val isDownloading = updateState is UpdateState.Downloading
                if (isUpdateAvailable || isDownloading) {
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber),
                        modifier = Modifier.clickable { showSyncDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Updates",
                                tint = ReconAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (val s = updateState) {
                                    is UpdateState.UpdateAvailable -> "NEW: ${s.release.tagName}"
                                    is UpdateState.Downloading -> "DL: ${s.progressPercent}%"
                                    else -> "UPDATE"
                                },
                                color = ReconAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // UK OpenStreetMap Data Update Button (ONLY visible when new OSM data is available)
                val isMapUpdateAvailable = mapUpdateState is com.recondrive.app.map.MapUpdateState.UpdateAvailable
                val isMapDownloading = mapUpdateState is com.recondrive.app.map.MapUpdateState.Downloading
                if (isMapUpdateAvailable || isMapDownloading) {
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen),
                        modifier = Modifier.clickable { showSyncDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map Updates",
                                tint = ReconGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (val s = mapUpdateState) {
                                    is com.recondrive.app.map.MapUpdateState.UpdateAvailable -> "MAP UPDATE"
                                    is com.recondrive.app.map.MapUpdateState.Downloading -> "MAP: ${s.progressPercent}%"
                                    else -> "OSM DATA"
                                },
                                color = ReconGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Skins Selector Button
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(fogSkin.perimeterGlowColor)),
                    modifier = Modifier.clickable { showSkinDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Skins",
                            tint = Color(fogSkin.perimeterGlowColor),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SKINS",
                            color = Color(fogSkin.perimeterGlowColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 24/7 Background Radar Status & Toggle Pill
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceRunning) ReconGreen else ReconAmber
                    ),
                    modifier = Modifier.clickable { onToggleBackgroundRadar() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "24/7 Background Radar",
                            tint = if (isServiceRunning) ReconGreen else ReconAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isServiceRunning) "RADAR 24/7" else "RADAR PAUSED",
                            color = if (isServiceRunning) ReconGreen else ReconAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Android Auto Status Badge
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Car",
                            tint = ReconGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AUTO LINKED",
                            color = ReconGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tactical Update Alert Banner (Appears when GitHub has a newer APK build)
        val currentUpdate = updateState
        if (currentUpdate is UpdateState.UpdateAvailable) {
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUpdateDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ReconAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "TACTICAL UPDATE AVAILABLE: ${currentUpdate.release.tagName}",
                                color = ReconAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Build ${currentUpdate.release.releaseTitle} (${currentUpdate.release.formattedSize})",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = ReconCyan,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "REVIEW",
                            color = TacticalObsidian,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. Primary Telemetry Readout (Speedometer & Compass Heading)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalSurface)
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speedometer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(currentSpeedKmh),
                    color = Color(mapSkin.highwayColor),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "KM/H  (%.0f MPH)".format(currentSpeedKmh * 0.621371f),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(
                modifier = Modifier
                    .height(50.dp)
                    .width(1.dp),
                color = TacticalBorder
            )

            // Compass Heading
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f°".format(currentHeading),
                    color = Color(mapSkin.routeColor),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "HEADING // NE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Tactical Radar Map & Fog Display (Live-driven by both independent skins!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(mapSkin.backgroundColor))
                .border(1.dp, Color(fogSkin.perimeterGlowColor).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Range rings
                for (r in listOf(60f, 130f, 210f, 290f)) {
                    drawCircle(
                        color = Color(fogSkin.perimeterGlowColor).copy(alpha = 0.15f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Radar Crosshair
                drawLine(
                    color = Color(fogSkin.perimeterGlowColor).copy(alpha = 0.15f),
                    start = Offset(cx - 20f, cy),
                    end = Offset(cx + 20f, cy),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(fogSkin.perimeterGlowColor).copy(alpha = 0.15f),
                    start = Offset(cx, cy - 20f),
                    end = Offset(cx, cy + 20f),
                    strokeWidth = 1.5f
                )

                // Background Map Roads (Map Skin colors)
                val bgRoad = Path().apply {
                    moveTo(0f, cy + 80f)
                    lineTo(size.width, cy - 40f)
                }
                drawPath(
                    path = bgRoad,
                    color = Color(mapSkin.minorRoadColor),
                    style = Stroke(width = 3f)
                )

                // Explored Road Track (Driven by Fog Skin Explored Road Glow)
                val road = Path().apply {
                    moveTo(cx - 140f, cy + 120f)
                    lineTo(cx - 60f, cy + 60f)
                    lineTo(cx, cy)
                    lineTo(cx + 80f, cy - 70f)
                    lineTo(cx + 180f, cy - 110f)
                }
                drawPath(
                    path = road,
                    color = Color(fogSkin.exploredRoadGlowColor),
                    style = Stroke(width = 6f)
                )

                // Active Route (Driven by Map Skin Route Color)
                val route = Path().apply {
                    moveTo(cx, cy)
                    lineTo(cx + 80f, cy - 70f)
                    lineTo(cx + 110f, cy - 160f)
                }
                drawPath(
                    path = route,
                    color = Color(mapSkin.routeColor),
                    style = Stroke(width = 8f)
                )

                // Fog Cleared Boundary (Scaled dynamically by user Fog Range)
                val canvasBeamRadius = (120f * (currentRange / 180f)).coerceIn(50f, 250f)
                drawCircle(
                    color = Color(fogSkin.perimeterGlowColor),
                    radius = canvasBeamRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = fogSkin.borderStrokeWidth)
                )

                // Vehicle Cursor Arrow (Map Skin Vehicle Color)
                val cursor = Path().apply {
                    moveTo(cx, cy - 14f)
                    lineTo(cx + 10f, cy + 10f)
                    lineTo(cx, cy + 6f)
                    lineTo(cx - 10f, cy + 10f)
                    close()
                }
                drawPath(path = cursor, color = Color(mapSkin.vehicleCursorColor))
            }

            // Top Overlay HUD Badge showing active skin pairing and optics settings
            Text(
                text = "MAP: ${mapSkin.displayName} | FOG: ${fogSkin.displayName} | ${currentRange}m @ ${(currentOpacity * 100).toInt()}%",
                color = Color(fogSkin.perimeterGlowColor),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Exploration Career & Gamification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXPLORATION INTEL",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stats.explorationRank.title.uppercase(),
                        color = Color(android.graphics.Color.parseColor(stats.explorationRank.badgeHex)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "%.2f km²".format(stats.totalAreaKm2),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Territory Uncovered",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.2f km".format(stats.currentUnchartedStreakKm),
                            color = Color(fogSkin.exploredRoadGlowColor),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Uncharted Streak",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = TacticalBorder)
                Spacer(modifier = Modifier.height(8.dp))

                // Ongoing Notification & 24/7 Background Status Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isServiceRunning) ReconCyan else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isServiceRunning) "24/7 Fog Unmasking (Notification Active)" else "Background Fog Removal Paused",
                            color = if (isServiceRunning) ReconCyan else TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (isServiceRunning) "ACTIVE" else "OFF",
                        color = if (isServiceRunning) ReconGreen else ReconAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Tactical Navigation Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSearchClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(mapSkin.highwayColor)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = TacticalObsidian,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ENGAGE ROUTE",
                    color = TacticalObsidian,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            OutlinedButton(
                onClick = onOfflinePacksClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(mapSkin.highwayColor)),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(mapSkin.highwayColor),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OFFLINE MAPS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
