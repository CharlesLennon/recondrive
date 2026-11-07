package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * Clean AI Navigation Cockpit & Tactical HUD
 * Features an intelligent prompt that types in like ChatGPT on load,
 * centered animated galaxy core canvas, followed by a smooth animated reveal of
 * user-saved destinations and stacked contact groups.
 * Speed defaults to MPH across all primary instrumentation.
 */
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
    deviceHeading: Float = currentHeading,
    currentLat: Double = 51.5074,
    currentLon: Double = -0.1278,
    onSearchClick: () -> Unit = {},
    onOfflinePacksClick: () -> Unit = {},
    onToggleBackgroundRadar: () -> Unit = {},
    onNavigateToDestination: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> },
    onOpenTimeline: () -> Unit = {}
) {
    var showSkinDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogPersonName by remember { mutableStateOf<String?>(null) }
    var isListeningVoice by remember { mutableStateOf(false) }

    // Expanded contacts state for stacked cards
    val expandedContacts = remember { mutableStateMapOf<String, Boolean>() }

    val mapSkin by skinManager.currentMapSkin.collectAsState()
    val fogSkin by skinManager.currentFogSkin.collectAsState()
    val currentRange by fogPreferences.fogRangeMeters.collectAsState()
    val currentOpacity by fogPreferences.fogOpacity.collectAsState()
    val updateState by updateManager.updateState.collectAsState()
    val mapUpdateState by osmMapUpdateManager.mapUpdateState.collectAsState()
    val isServiceRunning by com.recondrive.app.service.ExplorationTrackingService.isRunning.collectAsState()
    val destinations by savedDestinationRepository.destinations.collectAsState()

    // -------------------------------------------------------------------------
    // GPT-Style Typewriter Text Streaming Animation on Load
    // -------------------------------------------------------------------------
    val fullAiGreeting = remember { "Where to? All tactical navigation subsystems online." }
    var displayedAiGreeting by remember { mutableStateOf("") }
    var isAiStreamingComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        displayedAiGreeting = ""
        isAiStreamingComplete = false
        for (i in 1..fullAiGreeting.length) {
            displayedAiGreeting = fullAiGreeting.substring(0, i)
            delay(24L)
        }
        delay(120L)
        isAiStreamingComplete = true
    }

    // Cursor blink animation while streaming
    val cursorTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

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
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // =====================================================================
        // 1. TOP STATUS BAR (AI Navigation Core Telemetry Status)
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
                    text = "TACTICAL NAVIGATION CORE",
                    color = Color(mapSkin.highwayColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "GRID: %.4f°N, %.4f°W • ALL SYSTEMS NOMINAL".format(currentLat, kotlin.math.abs(currentLon)),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sleek, compact radar status pill (never stretches, perfectly bounded)
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isServiceRunning) ReconGreen.copy(alpha = 0.6f) else TacticalBorder
                ),
                modifier = Modifier.clickable { onToggleBackgroundRadar() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) ReconGreen else ReconAmber)
                    )
                    Text(
                        text = if (isServiceRunning) "RADAR 24/7" else "PAUSED",
                        color = if (isServiceRunning) ReconGreen else ReconAmber,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 2. PRIMARY TELEMETRY COCKPIT (MPH Default, Heading & Fog Optics)
        // =====================================================================
        val currentSpeedMph = currentSpeedKmh * 0.621371f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalSurface)
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Column (MPH Default!)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(currentSpeedMph),
                    color = Color(mapSkin.highwayColor),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MPH (%.0f KM/H)".format(currentSpeedKmh),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(
                modifier = Modifier
                    .height(34.dp)
                    .width(1.dp),
                color = TacticalBorder
            )

            // Heading Column
            val headingName = when ((currentHeading / 45f).toInt() % 8) {
                0 -> "N"
                1 -> "NE"
                2 -> "E"
                3 -> "SE"
                4 -> "S"
                5 -> "SW"
                6 -> "W"
                else -> "NW"
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f°".format(currentHeading),
                    color = Color(mapSkin.routeColor),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "HEADING // $headingName",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(
                modifier = Modifier
                    .height(34.dp)
                    .width(1.dp),
                color = TacticalBorder
            )

            // Fog Optics Column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentRange}m",
                    color = Color(fogSkin.perimeterGlowColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "FOG @ ${(currentOpacity * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 3. AI NAVIGATION COCKPIT & GALAXY CORE (Types in like GPT on load)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(mapSkin.highwayColor).copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ReconGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI NAVIGATION CORE // ONLINE",
                        color = ReconGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Centered Animated Galaxy Core (Compact 76dp so whole HUD fits without scrolling)
                GalaxyCoreAnimation(
                    modifier = Modifier.size(76.dp),
                    coreColor = Color(mapSkin.highwayColor),
                    sizeDp = 76.dp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Centered GPT Typewriter Streaming Text
                Text(
                    text = displayedAiGreeting + if (!isAiStreamingComplete && cursorAlpha > 0.4f) " ▋" else "",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isListeningVoice) "Listening for voice command coordinates..." else "Standing by for tactical destination instructions.",
                    color = if (isListeningVoice) ReconAmber else TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row (Add Destination, Open Map, Voice Command)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            addDialogPersonName = null
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(mapSkin.highwayColor)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddLocationAlt, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "ADD TARGET", color = TacticalObsidian, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = onSearchClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = Color(mapSkin.highwayColor), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "OPEN MAP", color = TextPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    IconButton(
                        onClick = { isListeningVoice = !isListeningVoice },
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isListeningVoice) ReconAmber else TacticalBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .border(1.dp, if (isListeningVoice) ReconAmber else TacticalBorder, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Command",
                            tint = if (isListeningVoice) TacticalObsidian else Color(mapSkin.highwayColor),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================================
        // 4. SAVED TARGETS (Horizontal Row Carousel - Fits on Screen, No Scroll!)
        // =====================================================================
        val standaloneDestinations = destinations.filter { it.personName.isNullOrBlank() }
        val personGroups = destinations.filterNot { it.personName.isNullOrBlank() }.groupBy { it.personName!! }

        AnimatedVisibility(
            visible = isAiStreamingComplete,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                animationSpec = tween(400),
                initialOffsetY = { 30 }
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Section Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED TARGETS (${destinations.size})",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "+ ADD",
                        color = Color(mapSkin.highwayColor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            addDialogPersonName = null
                            showAddDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Empty State: compact sleek bar
                if (standaloneDestinations.isEmpty() && personGroups.isEmpty()) {
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                addDialogPersonName = null
                                showAddDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                tint = Color(mapSkin.highwayColor),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NO SAVED TARGETS • TAP TO REGISTER",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    // Populated: horizontal LazyRow carousel
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(standaloneDestinations, key = { it.id }) { dest ->
                            CompactTargetChip(
                                destination = dest,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                mapSkinHighwayColor = mapSkin.highwayColor,
                                onNavigate = { onNavigateToDestination(dest.name, dest.lat, dest.lon) },
                                onDelete = { savedDestinationRepository.removeDestination(dest.id) }
                            )
                        }

                        items(personGroups.entries.toList(), key = { it.key }) { (person, places) ->
                            CompactPersonChip(
                                personName = person,
                                destinations = places,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                mapSkinHighwayColor = mapSkin.highwayColor,
                                onNavigate = { name, lat, lon -> onNavigateToDestination(name, lat, lon) },
                                onAddDestination = {
                                    addDialogPersonName = person
                                    showAddDialog = true
                                }
                            )
                        }

                        item {
                            Surface(
                                color = TacticalSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(mapSkin.highwayColor).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .height(60.dp)
                                    .clickable {
                                        addDialogPersonName = null
                                        showAddDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(mapSkin.highwayColor), modifier = Modifier.size(16.dp))
                                    Text(text = "+ TARGET", color = Color(mapSkin.highwayColor), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact Saved Destination Chip for Horizontal Carousel (Fits screen without vertical scroll)
 */
@Composable
private fun CompactTargetChip(
    destination: SavedDestination,
    currentLat: Double,
    currentLon: Double,
    mapSkinHighwayColor: Int,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    val distKm = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, destination.lat, destination.lon)
    val distMi = distKm * 0.621371

    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .width(175.dp)
            .height(60.dp)
            .clickable { onNavigate() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(mapSkinHighwayColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!destination.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = destination.photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (destination.name.equals("Home", true)) Icons.Default.Home
                        else if (destination.name.equals("Work", true)) Icons.Default.Work
                        else Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(mapSkinHighwayColor),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.name,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "%.1f mi".format(distMi),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onNavigate,
                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text("GO", color = TacticalObsidian, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Compact Contact Group Chip for Horizontal Carousel
 */
@Composable
private fun CompactPersonChip(
    personName: String,
    destinations: List<SavedDestination>,
    currentLat: Double,
    currentLon: Double,
    mapSkinHighwayColor: Int,
    onNavigate: (name: String, lat: Double, lon: Double) -> Unit,
    onAddDestination: () -> Unit
) {
    val firstDest = destinations.firstOrNull()
    val distKm = if (firstDest != null) UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, firstDest.lat, firstDest.lon) else 0.0
    val distMi = distKm * 0.621371

    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber.copy(alpha = 0.6f)),
        modifier = Modifier
            .width(185.dp)
            .height(60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ReconAmber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ReconAmber,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = personName,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "${destinations.size} pts • %.1fmi".format(distMi),
                    color = ReconAmber,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (firstDest != null) {
                Button(
                    onClick = { onNavigate("$personName - ${firstDest.name}", firstDest.lat, firstDest.lon) },
                    colors = ButtonDefaults.buttonColors(containerColor = ReconAmber),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("GO", color = TacticalObsidian, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}


/**
 * Celestial star particle data for the Galaxy animation.
 */
private data class GalaxyStar(
    val normalizedRadius: Float,
    val baseAngleRad: Float,
    val radiusPx: Float,
    val color: Color,
    val alpha: Float,
    val speedMultiplier: Float
)

/**
 * Centered Swirling Cosmic Galaxy Animation.
 * Features rotating logarithmic spiral arms, differential orbital velocity,
 * luminous cosmic core glow, and subtle tactical targeting reticles.
 */
@Composable
fun GalaxyCoreAnimation(
    modifier: Modifier = Modifier,
    coreColor: Color = ReconCyan,
    sizeDp: Dp = 104.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "galaxy_rot")

    // Primary galactic rotation: 18s per full 360-degree loop
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "galaxy_rotation"
    )

    // Breathing / pulsing core expansion
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "galaxy_pulse"
    )

    // Pre-calculate celestial stars distributed along spiral arms and core
    val stars = remember(coreColor) {
        val list = mutableListOf<GalaxyStar>()
        val rng = Random(42) // Fixed seed for stable stellar placement
        val arms = 3
        val starsPerArm = 28

        for (arm in 0 until arms) {
            val armBaseAngle = arm * (2f * Math.PI.toFloat() / arms)
            for (i in 0 until starsPerArm) {
                val t = (i + 1).toFloat() / starsPerArm
                val radiusFactor = t.pow(0.85f)
                val spiralAngle = armBaseAngle + 2.8f * t.pow(0.7f)
                val scatterAngle = (rng.nextFloat() - 0.5f) * 0.35f
                val scatterRadius = (rng.nextFloat() - 0.5f) * 0.08f

                val finalR = (radiusFactor + scatterRadius).coerceIn(0.12f, 0.96f)
                val finalAngle = spiralAngle + scatterAngle
                val starSize = if (finalR < 0.3f) 2.2f + rng.nextFloat() * 1.5f else 1.2f + rng.nextFloat() * 2.0f

                val starColor = when {
                    finalR < 0.25f -> if (rng.nextFloat() < 0.5f) Color.White else coreColor
                    rng.nextFloat() < 0.6f -> coreColor
                    rng.nextFloat() < 0.85f -> Color(0xFF64B5F6) // Electric blue
                    else -> ReconAmber.copy(alpha = 0.9f)
                }

                val starAlpha = (0.4f + rng.nextFloat() * 0.6f) * (1f - (finalR * 0.25f))
                val speedMult = 1.15f - (finalR * 0.30f)

                list.add(GalaxyStar(finalR, finalAngle, starSize, starColor, starAlpha, speedMult))
            }
        }

        // Additional galactic bulge stars near core
        for (i in 0 until 18) {
            val r = (rng.nextFloat().pow(1.8f) * 0.32f).coerceAtLeast(0.05f)
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val size = 1.5f + rng.nextFloat() * 2.0f
            val starColor = if (rng.nextFloat() < 0.6f) Color.White else coreColor
            list.add(GalaxyStar(r, angle, size, starColor, 0.85f, 1.25f))
        }

        list
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(TacticalObsidian)
            .border(1.dp, coreColor.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            val radAngle = rotationDeg * (Math.PI.toFloat() / 180f)

            // 1. Deep Cosmic Radial Core Glow
            val glowRadius = maxR * 0.58f * corePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f * corePulse),
                        coreColor.copy(alpha = 0.45f * corePulse),
                        coreColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. Faint Outer HUD Perimeter Ring with tactical dashes
            drawCircle(
                color = coreColor.copy(alpha = 0.30f),
                radius = maxR * 0.94f,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            )

            // 3. Counter-rotating Inner Tactical Orbit Ring
            val innerRingAngle = -radAngle * 0.5f
            drawCircle(
                color = coreColor.copy(alpha = 0.20f),
                radius = maxR * 0.50f,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), phase = innerRingAngle * 10f)
                )
            )

            // 4. Spiral Arms Glowing Dust Lanes
            val arms = 3
            for (arm in 0 until arms) {
                val armBaseAngle = arm * (2f * Math.PI.toFloat() / arms)
                val path = Path()
                var first = true
                val steps = 24
                for (s in 2..steps) {
                    val t = s.toFloat() / steps
                    val r = t * maxR * 0.92f
                    val a = armBaseAngle + 2.8f * t.pow(0.7f) + radAngle * (1.1f - t * 0.2f)
                    val x = center.x + r * cos(a)
                    val y = center.y + r * sin(a)
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = coreColor.copy(alpha = 0.20f * corePulse),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. Draw Orbiting Celestial Galaxy Stars
            for (star in stars) {
                val currentAngle = star.baseAngleRad + (radAngle * star.speedMultiplier)
                val starR = star.normalizedRadius * maxR
                val sx = center.x + starR * cos(currentAngle)
                val sy = center.y + starR * sin(currentAngle)

                val twinkle = (0.8f + 0.2f * sin(currentAngle * 3f)).coerceIn(0.6f, 1f)
                drawCircle(
                    color = star.color.copy(alpha = (star.alpha * twinkle).coerceIn(0f, 1f)),
                    radius = star.radiusPx,
                    center = Offset(sx, sy)
                )
            }

            // 6. Supermassive Central Cosmic Core Nucleus
            drawCircle(
                color = Color.White,
                radius = 3.5f * corePulse,
                center = center
            )
            drawCircle(
                color = coreColor,
                radius = 6.0f * corePulse,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // 7. Tactical Reticle Crosshair Ticks at Outer Edge (N, S, E, W)
            val tickLen = 5.dp.toPx()
            val tickRadius = maxR * 0.94f
            for (i in 0 until 4) {
                val a = i * (Math.PI.toFloat() / 2f)
                val x1 = center.x + (tickRadius - tickLen / 2f) * cos(a)
                val y1 = center.y + (tickRadius - tickLen / 2f) * sin(a)
                val x2 = center.x + (tickRadius + tickLen / 2f) * cos(a)
                val y2 = center.y + (tickRadius + tickLen / 2f) * sin(a)
                drawLine(
                    color = coreColor.copy(alpha = 0.5f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}
