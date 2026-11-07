package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay

/**
 * Clean J.A.R.V.I.S. AI Cockpit & Tactical HUD
 * Features an intelligent prompt that types in like ChatGPT on load,
 * followed by a smooth animated reveal of user-saved destinations and stacked contact groups.
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
    val fullAiGreeting = remember { "Where to, sir? All tactical navigation subsystems online." }
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

    // Holographic Arc Reactor Pulse
    val pulseTransition = rememberInfiniteTransition(label = "arc_reactor")
    val arcPulse by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arc_pulse"
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
                    text = "GRID: %.4f°N, %.4f°W • SYSTEMS NOMINAL".format(currentLat, kotlin.math.abs(currentLon)),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Update indicator if new build is available
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

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 2. PRIMARY TELEMETRY COCKPIT (MPH Default, Heading & Fog Optics)
        // =====================================================================
        val currentSpeedMph = currentSpeedKmh * 0.621371f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TacticalSurface)
                .border(1.dp, TacticalBorder, RoundedCornerShape(10.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Column (MPH Default!)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(currentSpeedMph),
                    color = Color(mapSkin.highwayColor),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MPH (%.0f KM/H)".format(currentSpeedKmh),
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
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "HEADING // $headingName",
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

            // Fog Optics Column
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

        Spacer(modifier = Modifier.height(14.dp))

        // =====================================================================
        // 3. J.A.R.V.I.S. AI GREETING (Types in like GPT on load)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(mapSkin.highwayColor).copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Holographic Arc Reactor Core
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(mapSkin.highwayColor).copy(alpha = 0.15f * arcPulse))
                            .border(1.5.dp, Color(mapSkin.highwayColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            drawCircle(color = Color(mapSkin.highwayColor), radius = (size.minDimension / 4f) * arcPulse)
                            drawCircle(color = Color(mapSkin.highwayColor).copy(alpha = 0.4f), radius = size.minDimension / 2f, style = Stroke(width = 2f))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "J.A.R.V.I.S.",
                                color = Color(mapSkin.highwayColor),
                                fontSize = 11.sp,
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

                        // GPT Typewriter Streaming Text
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayedAiGreeting,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 21.sp
                            )
                            if (!isAiStreamingComplete) {
                                Text(
                                    text = "▋",
                                    color = ReconCyan.copy(alpha = cursorAlpha),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isListeningVoice) "Listening for voice command coordinates..." else "Standing by for tactical destination instructions.",
                            color = if (isListeningVoice) ReconAmber else TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Voice Command Action Button
                    IconButton(
                        onClick = { isListeningVoice = !isListeningVoice },
                        modifier = Modifier
                            .size(38.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
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
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddLocationAlt, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ADD DESTINATION", color = TacticalObsidian, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = onSearchClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "OPEN MAP", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =====================================================================
        // 4. SAVED DESTINATIONS & STACKED CARDS (Loads after GPT text streams in)
        // =====================================================================
        val standaloneDestinations = destinations.filter { it.personName.isNullOrBlank() }
        val personGroups = destinations.filterNot { it.personName.isNullOrBlank() }.groupBy { it.personName!! }

        AnimatedVisibility(
            visible = isAiStreamingComplete,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                animationSpec = tween(400),
                initialOffsetY = { 40 }
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

                // Empty State
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
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                tint = Color(mapSkin.highwayColor),
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "NO SAVED DESTINATIONS YET",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Tap here or '+ ADD' to register your places or contacts.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // A. Render Top-Level Standalone Cards
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

                // B. Render Stacked Cards (Grouped by Person / Contact)
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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Standard Standalone Saved Destination Card
 * Only shows photo thumbnail if user added a photo!
 * Displays distance in miles as default.
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
    val distanceMiles = distanceKm * 0.621371

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
                        "HEART" -> Icons.Default.Favorite
                        "COFFEE" -> Icons.Default.LocalCafe
                        "GYM" -> Icons.Default.FitnessCenter
                        "AIRPORT" -> Icons.Default.Flight
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
                if (destination.addressOrSubtext.isNotBlank()) {
                    Text(
                        text = destination.addressOrSubtext,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Text(
                    text = "%.1f mi (%.1f km) • %.4f°N, %.4f°W".format(distanceMiles, distanceKm, destination.lat, kotlin.math.abs(destination.lon)),
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
 * Stacked Cards for People / Contact Sub-lists
 * Displays visual multi-card stacked layer behind the card.
 * Expands on tap to reveal all places belonging to that person.
 * Displays distance in miles as default.
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
        // Background Stacked Card Layers (Physical stacked cards visual effect)
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
                            val subDistKm = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, subDest.lat, subDest.lon)
                            val subDistMi = subDistKm * 0.621371
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
                                    if (subDest.addressOrSubtext.isNotBlank()) {
                                        Text(
                                            text = subDest.addressOrSubtext,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "%.1f mi (%.1f km) away".format(subDistMi, subDistKm),
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
