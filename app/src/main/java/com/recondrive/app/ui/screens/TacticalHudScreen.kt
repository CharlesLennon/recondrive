package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.recondrive.app.destinations.SavedDestination
import com.recondrive.app.destinations.SavedDestinationRepository
import com.recondrive.app.fog.ExplorationStats
import com.recondrive.app.map.UkOsmDataEngine
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
    savedDestinationRepository: SavedDestinationRepository = com.recondrive.app.ReconApplication.instance.savedDestinationRepository,
    currentSpeedKmh: Float = 48f,
    currentHeading: Float = 45f,
    currentLat: Double = 51.5074,
    currentLon: Double = -0.1278,
    onSearchClick: () -> Unit = {},
    onOfflinePacksClick: () -> Unit = {},
    onToggleBackgroundRadar: () -> Unit = {},
    onNavigateToDestination: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
) {
    var showSkinDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogPersonName by remember { mutableStateOf<String?>(null) }
    var isListeningVoice by remember { mutableStateOf(false) }

    // Expanded contacts state for stacked cards (e.g. "Annabell")
    val expandedContacts = remember { mutableStateMapOf<String, Boolean>("Annabell" to true) }

    val mapSkin by skinManager.currentMapSkin.collectAsState()
    val fogSkin by skinManager.currentFogSkin.collectAsState()
    val currentRange by fogPreferences.fogRangeMeters.collectAsState()
    val currentOpacity by fogPreferences.fogOpacity.collectAsState()
    val updateState by updateManager.updateState.collectAsState()
    val mapUpdateState by osmMapUpdateManager.mapUpdateState.collectAsState()
    val isServiceRunning by com.recondrive.app.service.ExplorationTrackingService.isRunning.collectAsState()
    val destinations by savedDestinationRepository.destinations.collectAsState()

    // Dialogs
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

    if (showAddDialog) {
        val existingPersons = destinations.mapNotNull { it.personName }.distinct()
        AddDestinationDialog(
            initialPersonName = addDialogPersonName,
            currentLat = currentLat,
            currentLon = currentLon,
            existingContacts = existingPersons,
            onSave = { newDest ->
                savedDestinationRepository.addDestination(newDest)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // =====================================================================
        // 1. TOP STATUS BAR (J.A.R.V.I.S. Core Intelligence Status)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "J.A.R.V.I.S. // TACTICAL NAVIGATION CORE",
                    color = Color(mapSkin.highwayColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "GRID: %.4f°N, %.4f°W • ALL SYSTEMS NOMINAL".format(currentLat, kotlin.math.abs(currentLon)),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Update indicator if new APK build ready
                val isUpdateAvailable = updateState is UpdateState.UpdateAvailable
                if (isUpdateAvailable) {
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber),
                        modifier = Modifier.clickable { showSyncDialog = true }
                    ) {
                        Text(
                            text = "UPDATE",
                            color = ReconAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Skins Selector Button
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(fogSkin.perimeterGlowColor)),
                    modifier = Modifier.clickable { showSkinDialog = true }
                ) {
                    Text(
                        text = "SKINS",
                        color = Color(fogSkin.perimeterGlowColor),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // 24/7 Radar Pill
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceRunning) ReconGreen else ReconAmber
                    ),
                    modifier = Modifier.clickable { onToggleBackgroundRadar() }
                ) {
                    Text(
                        text = if (isServiceRunning) "RADAR 24/7" else "PAUSED",
                        color = if (isServiceRunning) ReconGreen else ReconAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 2. J.A.R.V.I.S. AI GREETING & HOLOGRAPHIC PROMPT ("Where to, sir?")
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(mapSkin.highwayColor).copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Holographic Arc Reactor Icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(mapSkin.highwayColor).copy(alpha = 0.15f))
                            .border(1.5f, Color(mapSkin.highwayColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            drawCircle(color = Color(mapSkin.highwayColor), radius = size.minDimension / 4f)
                            drawCircle(color = Color(mapSkin.highwayColor).copy(alpha = 0.35f), radius = size.minDimension / 2f, style = Stroke(width = 2f))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Where to, sir?",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ONLINE",
                                color = ReconGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (isListeningVoice) "Listening for tactical command..." else "Stark Navigation AI online. Sussex grid unmasked.",
                            color = if (isListeningVoice) ReconAmber else TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Voice Command Action Button
                    IconButton(
                        onClick = { isListeningVoice = !isListeningVoice },
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isListeningVoice) ReconAmber else Color(mapSkin.highwayColor).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = if (isListeningVoice) TacticalObsidian else Color(mapSkin.highwayColor),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick AI Action Buttons
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            addDialogPersonName = null
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(mapSkin.highwayColor)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddLocationAlt, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "ADD DESTINATION", color = TacticalObsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = onSearchClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "OPEN MAP", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =====================================================================
        // 3. PRIMARY TELEMETRY COCKPIT (Speed & Heading)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalSurface)
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(currentSpeedKmh),
                    color = Color(mapSkin.highwayColor),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "KM/H  (%.0f MPH)".format(currentSpeedKmh * 0.621371f),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = TacticalBorder
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f°".format(currentHeading),
                    color = Color(mapSkin.routeColor),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "HEADING // NE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = TacticalBorder
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentRange}m",
                    color = Color(fogSkin.perimeterGlowColor),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "FOG @ ${(currentOpacity * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =====================================================================
        // 4. SAVED DESTINATIONS & STACKED CARDS (Sub-lists, e.g. Annabell)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVED TARGETS // QUICK ENGAGE (${destinations.size})",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "+ ADD",
                color = Color(mapSkin.highwayColor),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    addDialogPersonName = null
                    showAddDialog = true
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Group destinations:
        // Top-level standalone destinations (personName == null)
        val standaloneDestinations = destinations.filter { it.personName.isNullOrBlank() }
        // Grouped by Person (personName != null, e.g. "Annabell")
        val personGroups = destinations.filterNot { it.personName.isNullOrBlank() }.groupBy { it.personName!! }

        // A. Render Top-Level Standalone Cards (Home, Work, etc.)
        for (dest in standaloneDestinations) {
            DestinationCard(
                destination = dest,
                currentLat = currentLat,
                currentLon = currentLon,
                mapSkinHighwayColor = mapSkin.highwayColor,
                onNavigate = {
                    onNavigateToDestination(dest.name, dest.lat, dest.lon)
                },
                onDelete = {
                    savedDestinationRepository.removeDestination(dest.id)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // B. Render Stacked Cards (Grouped by Person, e.g. Annabell)
        for ((person, places) in personGroups) {
            val isExpanded = expandedContacts[person] ?: false
            StackedPersonCard(
                personName = person,
                destinations = places,
                isExpanded = isExpanded,
                currentLat = currentLat,
                currentLon = currentLon,
                mapSkinHighwayColor = mapSkin.highwayColor,
                onToggleExpand = {
                    expandedContacts[person] = !isExpanded
                },
                onAddDestinationToPerson = {
                    addDialogPersonName = person
                    showAddDialog = true
                },
                onNavigate = { name, lat, lon ->
                    onNavigateToDestination(name, lat, lon)
                },
                onDeleteDestination = { destId ->
                    savedDestinationRepository.removeDestination(destId)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 5. MINI TACTICAL RADAR HUD & SENSORS
        // =====================================================================
        Text(
            text = "LIVE TACTICAL RADAR CANVAS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(mapSkin.backgroundColor))
                .border(1.dp, Color(fogSkin.perimeterGlowColor).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Background minor road network with contrast casing
                val bgRoad = Path().apply {
                    moveTo(0f, cy + 80f)
                    lineTo(size.width, cy - 40f)
                }
                drawPath(
                    path = bgRoad,
                    color = Color.Black.copy(alpha = 0.5f),
                    style = Stroke(width = 5.5f)
                )
                drawPath(
                    path = bgRoad,
                    color = Color(mapSkin.minorRoadColor),
                    style = Stroke(width = 3.5f)
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

                // Fog Shroud Mask & Cleared Surveillance Boundary (Scaled by user Fog Range & Opacity)
                val canvasBeamRadius = (60f + (currentRange - 50) * (180f / 450f)).coerceIn(40f, 260f)

                val hudShroud = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                }
                val hudHole = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(cx, cy), radius = canvasBeamRadius))
                }
                val maskedHudShroud = Path().apply {
                    op(hudShroud, hudHole, androidx.compose.ui.graphics.PathOperation.Difference)
                }
                val effectiveOpacity = (fogSkin.shroudOpacity * currentOpacity).coerceIn(0.10f, 1.0f)
                drawPath(
                    path = maskedHudShroud,
                    color = Color(fogSkin.shroudColor).copy(alpha = effectiveOpacity)
                )

                // Glowing reticle boundary
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
                    .padding(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =====================================================================
        // 6. EXPLORATION CAREER INTEL
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "%.2f km²".format(stats.totalAreaKm2),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(text = "Territory Uncovered", color = TextSecondary, fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.2f km".format(stats.currentUnchartedStreakKm),
                            color = Color(fogSkin.exploredRoadGlowColor),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(text = "Uncharted Streak", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Tactical Actions
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
                Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "ENGAGE RADAR MAP", color = TacticalObsidian, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onOfflinePacksClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(mapSkin.highwayColor)),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(mapSkin.highwayColor), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "OFFLINE MAPS", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Standard Standalone Saved Destination Card
 * Only shows photo thumbnail if user added a photo!
 */
@Composable
private fun DestinationCard(
    destination: SavedDestination,
    currentLat: Double,
    currentLon: Double,
    mapSkinHighwayColor: Int,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    val distanceKm = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, destination.lat, destination.lon)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo thumbnail (ONLY if user added a photo!) OR tactical icon fallback
            if (!destination.photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = destination.photoUri,
                    contentDescription = destination.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(mapSkinHighwayColor), RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TacticalObsidian)
                        .border(1.dp, Color(mapSkinHighwayColor).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val iconVector = when (destination.iconType) {
                        "HOME" -> Icons.Default.Home
                        "WORK" -> Icons.Default.Work
                        "STAR" -> Icons.Default.Star
                        "GYM" -> Icons.Default.FitnessCenter
                        "COFFEE" -> Icons.Default.LocalCafe
                        else -> Icons.Default.Place
                    }
                    Icon(imageVector = iconVector, contentDescription = null, tint = Color(mapSkinHighwayColor), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = destination.addressOrSubtext,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Text(
                    text = "%.1f km away • Tap to Engage".format(distanceKm),
                    color = Color(mapSkinHighwayColor),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(mapSkinHighwayColor)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.NearMe, contentDescription = "Engage", tint = TacticalObsidian, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "GO", color = TacticalObsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Stacked Cards for People / Sub-lists (e.g. "Annabell")
 * Displays visual multi-card stacked layer behind the card.
 * Expands on tap to reveal all places belonging to that person (Home, Work, etc.)
 */
@Composable
private fun StackedPersonCard(
    personName: String,
    destinations: List<SavedDestination>,
    isExpanded: Boolean,
    currentLat: Double,
    currentLon: Double,
    mapSkinHighwayColor: Int,
    onToggleExpand: () -> Unit,
    onAddDestinationToPerson: () -> Unit,
    onNavigate: (name: String, lat: Double, lon: Double) -> Unit,
    onDeleteDestination: (id: String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Background Stacked Card Layers (Physical stacked cards effect!)
        Box(
            modifier = Modifier
                .padding(start = 10.dp, top = 8.dp, end = 2.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalBorder.copy(alpha = 0.4f))
        )
        Box(
            modifier = Modifier
                .padding(start = 5.dp, top = 4.dp, end = 4.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalSurface.copy(alpha = 0.8f))
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
        )

        // Main Foreground Stacked Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isExpanded) ReconCyan else ReconAmber)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contact Avatar Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ReconAmber.copy(alpha = 0.15f))
                            .border(1.dp, ReconAmber, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = personName.take(1).uppercase(),
                            color = ReconAmber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "👤 $personName",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = ReconAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "${destinations.size} PLACES",
                                    color = ReconAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isExpanded) "Tap to collapse stacked deck" else "Stacked card • Tap to open sub-list",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = ReconAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sub-list of places under this Person (Unfolds when expanded!)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Divider(color = TacticalBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        for (subDest in destinations) {
                            val subDist = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, subDest.lat, subDest.lon)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TacticalObsidian)
                                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                                    .clickable { onNavigate("$personName - ${subDest.name}", subDest.lat, subDest.lon) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sub-destination Photo (ONLY if user added one!)
                                if (!subDest.photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = subDest.photoUri,
                                        contentDescription = subDest.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, ReconCyan, RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    val icon = when (subDest.iconType) {
                                        "HOME" -> Icons.Default.Home
                                        "WORK" -> Icons.Default.Work
                                        "STAR" -> Icons.Default.Star
                                        else -> Icons.Default.Place
                                    }
                                    Icon(imageVector = icon, contentDescription = null, tint = ReconAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subDest.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = subDest.addressOrSubtext,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.1f km away".format(subDist),
                                        color = Color(mapSkinHighwayColor),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = { onNavigate("$personName - ${subDest.name}", subDest.lat, subDest.lon) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "ENGAGE", color = TacticalObsidian, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                IconButton(onClick = { onDeleteDestination(subDest.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(12.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // "+ Add Location for [Person]"
                        Surface(
                            color = TacticalObsidian,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddDestinationToPerson() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = ReconAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+ ADD PLACE FOR ${personName.uppercase()}",
                                    color = ReconAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
