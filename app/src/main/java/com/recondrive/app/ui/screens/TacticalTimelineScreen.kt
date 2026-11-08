package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recondrive.app.map.OsmTileManager
import com.recondrive.app.scanner.DetectedRfTarget
import com.recondrive.app.scanner.DetectionTechnology
import com.recondrive.app.scanner.RfCategory
import com.recondrive.app.scanner.TacticalRfScannerManager
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.telemetry.LocationTimelineEntry
import com.recondrive.app.telemetry.LocationTimelineManager
import com.recondrive.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Historical snapshot of an RF target observed at a specific moment in time.
 */
data class TimelineTargetSnapshot(
    val target: DetectedRfTarget,
    val displayLat: Double,
    val displayLon: Double,
    val displayRssi: Int,
    val displayDistanceMeters: Double,
    val relativeBearingDeg: Float,
    val observationTimeMs: Long
)

/**
 * Tactical 4D Timeline Console.
 * Combines the multi-spectrum RF scanner and interactive vector map with a time-scrubbing slider:
 * 1. Historical Replay: Drag slider to scrub backward and forward through drive history.
 * 2. Synchronized Map: Centers on the vehicle location at that moment and traces traveled routes.
 * 3. Historical RF Blips: Shows targets detected/active at that exact timestamp on both map and list.
 * 4. Playback Engine: Play, Pause, 1x/2x/5x speed, ±10s/±1m jumps, and jump to Live.
 */
@Composable
fun TacticalTimelineScreen(
    timelineManager: LocationTimelineManager,
    scannerManager: TacticalRfScannerManager,
    osmTileManager: OsmTileManager,
    skinManager: SkinManager,
    currentLat: Double,
    currentLon: Double,
    currentHeading: Float,
    currentSpeedKmh: Float,
    onNavigateToTarget: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
) {
    val rawEntries by timelineManager.timelineEntries.collectAsState()
    val allDetectedTargets by scannerManager.detectedTargets.collectAsState()
    val isScanning by scannerManager.isScanning.collectAsState()
    val tileRevision by osmTileManager.revision.collectAsState()

    // 1. Effective Timeline Entries (strictly genuine recorded GPS fixes)
    val effectiveEntries = remember(rawEntries, currentLat, currentLon, currentHeading, currentSpeedKmh) {
        if (rawEntries.isNotEmpty()) {
            rawEntries.sortedBy { it.timestamp }
        } else {
            // Strictly genuine single current fix when sitting stationary with no accumulated history
            listOf(
                LocationTimelineEntry(
                    id = System.currentTimeMillis(),
                    timestamp = System.currentTimeMillis(),
                    latitude = currentLat,
                    longitude = currentLon,
                    altitudeMeters = 0.0,
                    speedKmh = currentSpeedKmh,
                    gpsBearing = currentHeading,
                    deviceHeading = currentHeading,
                    accuracyMeters = 4f,
                    source = "LIVE GPS"
                )
            )
        }
    }

    // 2. Playback & Time Scrubbing State
    var sliderProgress by remember { mutableFloatStateOf(1.0f) } // 0.0f = oldest, 1.0f = live/newest
    var isLiveMode by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // If in Live mode and new timeline fixes arrive, keep slider pinned to live end
    LaunchedEffect(effectiveEntries.size, currentLat, currentLon) {
        if (isLiveMode) {
            sliderProgress = 1.0f
        }
    }

    // Playback loop
    LaunchedEffect(isPlaying, playbackSpeed) {
        if (isPlaying) {
            while (isActive && isPlaying) {
                delay(80L)
                val step = (0.004f * playbackSpeed).coerceAtLeast(0.001f)
                val next = sliderProgress + step
                if (next >= 1.0f) {
                    sliderProgress = 1.0f
                    isPlaying = false
                    isLiveMode = true
                } else {
                    sliderProgress = next
                }
            }
        }
    }

    // 3. Current Scrubbed Fix Calculation (with smooth road interpolation)
    val currentIndex = remember(sliderProgress, effectiveEntries.size) {
        if (effectiveEntries.isEmpty()) 0
        else ((effectiveEntries.size - 1) * sliderProgress).toInt().coerceIn(0, effectiveEntries.size - 1)
    }

    val scrubFix = remember(currentIndex, effectiveEntries, sliderProgress) {
        if (effectiveEntries.isEmpty()) {
            LocationTimelineEntry(
                id = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis(),
                latitude = currentLat,
                longitude = currentLon,
                altitudeMeters = 0.0,
                speedKmh = currentSpeedKmh,
                gpsBearing = currentHeading,
                deviceHeading = currentHeading,
                accuracyMeters = 4f,
                source = "LIVE GPS"
            )
        } else if (effectiveEntries.size == 1) {
            effectiveEntries[0]
        } else {
            // Smooth sub-fix interpolation along the road between baseIndex and nextIndex
            val floatIndex = (effectiveEntries.size - 1) * sliderProgress
            val baseIndex = floatIndex.toInt().coerceIn(0, effectiveEntries.size - 1)
            val nextIndex = (baseIndex + 1).coerceAtMost(effectiveEntries.size - 1)
            val frac = floatIndex - baseIndex

            val curr = effectiveEntries[baseIndex]
            val next = effectiveEntries[nextIndex]

            val dist = calculateDistanceMeters(curr.latitude, curr.longitude, next.latitude, next.longitude)
            if (frac > 0.001f && baseIndex != nextIndex && dist in 0.5..65.0) {
                // Smooth interpolation along the road segment
                val interpLat = curr.latitude + (next.latitude - curr.latitude) * frac
                val interpLon = curr.longitude + (next.longitude - curr.longitude) * frac
                val interpTime = (curr.timestamp + ((next.timestamp - curr.timestamp) * frac)).toLong()
                val interpBearing = if (dist > 1.5) {
                    val dLatM = (next.latitude - curr.latitude) * 111320.0
                    val dLonM = (next.longitude - curr.longitude) * 111320.0 * cos(Math.toRadians(curr.latitude))
                    var b = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                    if (b < 0f) b += 360f
                    b
                } else curr.gpsBearing

                curr.copy(
                    timestamp = interpTime,
                    latitude = interpLat,
                    longitude = interpLon,
                    gpsBearing = interpBearing,
                    speedKmh = curr.speedKmh + (next.speedKmh - curr.speedKmh) * frac.toFloat()
                )
            } else {
                curr
            }
        }
    }

    val scrubTimeMs = scrubFix.timestamp
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeFormatted = remember(scrubTimeMs) { timeFormat.format(Date(scrubTimeMs)) }

    val relativeTimeDesc = remember(scrubTimeMs, isLiveMode) {
        if (isLiveMode) "● LIVE STREAM"
        else {
            val diffSec = max(0L, (System.currentTimeMillis() - scrubTimeMs) / 1000L)
            val mins = diffSec / 60L
            val secs = diffSec % 60L
            "-%02dm %02ds AGO".format(mins, secs)
        }
    }

    // 4. Targets Active at Scrubbed Time Window (±3 minutes)
    val activeTargetsAtTime = remember(scrubTimeMs, allDetectedTargets, scrubFix.latitude, scrubFix.longitude, scrubFix.gpsBearing) {
        computeTargetsAtTime(allDetectedTargets, scrubTimeMs, scrubFix.latitude, scrubFix.longitude, scrubFix.gpsBearing)
    }

    var selectedFilterCategory by remember { mutableStateOf<RfCategory?>(null) }
    var selectedTarget by remember { mutableStateOf<TimelineTargetSnapshot?>(null) }

    val filteredTargets = remember(activeTargetsAtTime, selectedFilterCategory) {
        if (selectedFilterCategory == null) activeTargetsAtTime
        else activeTargetsAtTime.filter { it.target.category == selectedFilterCategory }
    }

    // 5. Map Viewport State
    var mapZoom by remember { mutableFloatStateOf(16.5f) }
    var cameraLat by remember { mutableDoubleStateOf(scrubFix.latitude) }
    var cameraLon by remember { mutableDoubleStateOf(scrubFix.longitude) }
    var isFollowingVehicle by remember { mutableStateOf(true) }

    // Follow vehicle during playback or live scrub
    LaunchedEffect(scrubFix.latitude, scrubFix.longitude) {
        if (isFollowingVehicle) {
            cameraLat = scrubFix.latitude
            cameraLon = scrubFix.longitude
        }
    }

    // Dark tile color filter for tactical HUD style
    val tileColorFilter = remember {
        val matrix = ColorMatrix().apply {
            setToSaturation(0.08f)
            val darkScale = 0.32f
            val r = floatArrayOf(
                darkScale * 0.40f, 0f, 0f, 0f, 6f,
                0f, darkScale * 0.70f, 0f, 0f, 14f,
                0f, 0f, darkScale * 0.95f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
            set(ColorMatrix(r))
        }
        ColorFilter.colorMatrix(matrix)
    }

    // Pulse animation for radar blips
    val infiniteTransition = rememberInfiniteTransition(label = "timeline_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
    ) {
        // =====================================================================
        // 1. TOP HEADER & TELEMETRY BAR
        // =====================================================================
        Surface(
            color = TacticalSurface,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLiveMode) ReconGreen else ReconAmber)
                        )
                        Text(
                            text = "4D TIMELINE REPLAY",
                            color = ReconCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = relativeTimeDesc,
                            color = if (isLiveMode) ReconGreen else ReconAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "SPD: ${"%.0f".format(scrubFix.speedKmh)} MPH • DIR: ${scrubFix.gpsBearing.toInt()}° • (%.4f°N, %.4f°W)".format(
                            scrubFix.latitude,
                            abs(scrubFix.longitude)
                        ),
                        color = TextSecondary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // In-Range Target Counter Pill
                Surface(
                    color = ReconCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan)
                ) {
                    Text(
                        text = "${activeTargetsAtTime.size} SIGNALS",
                        color = ReconCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // =====================================================================
        // 2. HERO INTERACTIVE MAP WITH TIMELINE VEHICLE & RF TARGET BLIPS
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f)
                .background(TacticalObsidian)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomFactor, _ ->
                            val scale = 256.0 * 2.0.pow(mapZoom.toDouble())
                            val dLon = -(pan.x / scale) * 360.0
                            val dLat = (pan.y / scale) * 180.0

                            cameraLon += dLon
                            cameraLat += dLat

                            if (zoomFactor != 1.0f) {
                                mapZoom = (mapZoom + (zoomFactor - 1.0f) * 2.0f).coerceIn(12.0f, 19.0f)
                            }
                            isFollowingVehicle = false
                        }
                    }
                    .pointerInput(activeTargetsAtTime) {
                        detectTapGestures { tapOffset ->
                            // Hit test for RF target blips
                            val scale = 256.0 * 2.0.pow(mapZoom.toDouble())
                            val cx = (cameraLon + 180.0) / 360.0 * scale
                            val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / PI) / 2.0 * scale

                            var hit: TimelineTargetSnapshot? = null
                            for (targetSnapshot in activeTargetsAtTime) {
                                val tx = (targetSnapshot.displayLon + 180.0) / 360.0 * scale
                                val ty = (1.0 - ln(tan(Math.toRadians(targetSnapshot.displayLat)) + 1.0 / cos(Math.toRadians(targetSnapshot.displayLat))) / PI) / 2.0 * scale
                                val sx = (tx - cx + size.width / 2.0).toFloat()
                                val sy = (ty - cy + size.height / 2.0).toFloat()

                                val dx = tapOffset.x - sx
                                val dy = tapOffset.y - sy
                                if (dx * dx + dy * dy <= 30f * 30f) {
                                    hit = targetSnapshot
                                    break
                                }
                            }
                            selectedTarget = hit
                        }
                    }
            ) {
                @Suppress("UNUSED_VARIABLE")
                val rev = tileRevision
                val width = size.width
                val height = size.height

                // Web Mercator Projector
                fun project(lat: Double, lon: Double): Offset {
                    val scale = 256.0 * 2.0.pow(mapZoom.toDouble())
                    val cx = (cameraLon + 180.0) / 360.0 * scale
                    val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / PI) / 2.0 * scale

                    val x = (lon + 180.0) / 360.0 * scale
                    val y = (1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * scale

                    return Offset(
                        (x - cx + width / 2.0).toFloat(),
                        (y - cy + height / 2.0).toFloat()
                    )
                }

                // A. Draw OSM Raster Tiles
                val tileZ = mapZoom.toInt().coerceIn(1, 18)
                val n = 1 shl tileZ
                val tileScale = 2.0.pow((mapZoom - tileZ).toDouble())
                val tileSizeOnScreen = (256.0 * tileScale).toFloat()

                val cameraLatRad = Math.toRadians(cameraLat.coerceIn(-85.0511, 85.0511))
                val cameraTileX = (cameraLon + 180.0) / 360.0 * n
                val cameraTileY = (1.0 - ln(tan(cameraLatRad) + 1.0 / cos(cameraLatRad)) / PI) / 2.0 * n

                val halfTilesW = (width / (2.0 * tileSizeOnScreen))
                val halfTilesH = (height / (2.0 * tileSizeOnScreen))

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

                val vehiclePos = project(scrubFix.latitude, scrubFix.longitude)

                // B. Pixels per ground meter calculation
                val cosLat = cos(Math.toRadians(scrubFix.latitude)).coerceAtLeast(0.01)
                val deltaLon1m = 1.0 / (111320.0 * cosLat)
                val p1m = project(scrubFix.latitude, scrubFix.longitude + deltaLon1m)
                val pixelsPerMeter = abs(p1m.x - vehiclePos.x).coerceAtLeast(0.001f)

                // C. Traveled Path up to scrubbed point (Illuminated road corridor)
                if (effectiveEntries.size >= 2 && currentIndex > 0) {
                    val traveledPath = Path()
                    var prevLat = effectiveEntries[0].latitude
                    var prevLon = effectiveEntries[0].longitude
                    var prevPt = project(prevLat, prevLon)
                    traveledPath.moveTo(prevPt.x, prevPt.y)

                    for (i in 1..currentIndex) {
                        val e = effectiveEntries[i]
                        val dist = calculateDistanceMeters(prevLat, prevLon, e.latitude, e.longitude)
                        val pt = project(e.latitude, e.longitude)

                        if (dist in 1.5..65.0) {
                            // Smooth road turn with midpoint quadratic curve to avoid cutting corners
                            val midX = (prevPt.x + pt.x) / 2f
                            val midY = (prevPt.y + pt.y) / 2f
                            traveledPath.quadraticBezierTo(prevPt.x, prevPt.y, midX, midY)
                            traveledPath.lineTo(pt.x, pt.y)
                        } else if (dist > 65.0) {
                            // Break path on large gaps/tunnels to prevent straight diagonal lines cutting across town
                            traveledPath.moveTo(pt.x, pt.y)
                        }
                        prevLat = e.latitude
                        prevLon = e.longitude
                        prevPt = pt
                    }

                    // Traveled Path Glow & Core Line
                    drawPath(
                        path = traveledPath,
                        color = ReconCyan.copy(alpha = 0.35f),
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = traveledPath,
                        color = ReconCyan,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    )
                }

                // D. Tactical Range Rings around Vehicle at Scrubbed Time
                for (radiusM in listOf(50.0, 100.0, 200.0)) {
                    val rPx = (radiusM * pixelsPerMeter).toFloat()
                    if (rPx in 10f..1500f) {
                        drawCircle(
                            color = ReconCyan.copy(alpha = 0.18f),
                            radius = rPx,
                            center = vehiclePos,
                            style = Stroke(width = 1f)
                        )
                    }
                }

                // E. RF Targets Active at Scrubbed Time Window
                for (snapshot in filteredTargets) {
                    val blipPos = project(snapshot.displayLat, snapshot.displayLon)
                    val catColor = getCategoryColor(snapshot.target.category)

                    // Target Outer Pulse Aura
                    drawCircle(
                        color = catColor.copy(alpha = pulseAlpha * 0.7f),
                        radius = 8f + (14f * pulseScale),
                        center = blipPos
                    )

                    // Target Core Blip
                    drawCircle(
                        color = catColor,
                        radius = 5.5f,
                        center = blipPos
                    )

                    // Selection Reticle if clicked
                    if (selectedTarget?.target?.id == snapshot.target.id) {
                        drawCircle(
                            color = Color.White,
                            radius = 16f,
                            center = blipPos,
                            style = Stroke(width = 1.8f)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(blipPos.x - 22f, blipPos.y),
                            end = Offset(blipPos.x + 22f, blipPos.y),
                            strokeWidth = 1.2f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(blipPos.x, blipPos.y - 22f),
                            end = Offset(blipPos.x, blipPos.y + 22f),
                            strokeWidth = 1.2f
                        )
                    }

                    // Blip Label
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 20f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        isAntiAlias = true
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val labelStr = "${snapshot.target.displayName.take(14)} (${snapshot.displayRssi}dBm)"
                    drawContext.canvas.nativeCanvas.drawText(labelStr, blipPos.x + 10f, blipPos.y + 6f, labelPaint)
                }

                // F. Host Vehicle Cursor & Heading Cone
                rotate(degrees = scrubFix.gpsBearing, pivot = vehiclePos) {
                    drawCircle(
                        color = ReconCyan.copy(alpha = 0.25f),
                        radius = 18f,
                        center = vehiclePos
                    )
                    val arrowPath = Path().apply {
                        moveTo(vehiclePos.x, vehiclePos.y - 18f)
                        lineTo(vehiclePos.x + 11f, vehiclePos.y + 12f)
                        lineTo(vehiclePos.x, vehiclePos.y + 6f)
                        lineTo(vehiclePos.x - 11f, vehiclePos.y + 12f)
                        close()
                    }
                    drawPath(path = arrowPath, color = ReconCyan)
                }
            }

            // Top-Right Floating Map Overlay Controls
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Zoom In
                IconButton(
                    onClick = { mapZoom = (mapZoom + 0.6f).coerceAtMost(19.0f) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(TacticalSurface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", tint = ReconCyan, modifier = Modifier.size(16.dp))
                }

                // Zoom Out
                IconButton(
                    onClick = { mapZoom = (mapZoom - 0.6f).coerceAtLeast(12.0f) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(TacticalSurface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", tint = ReconCyan, modifier = Modifier.size(16.dp))
                }

                // Recenter
                IconButton(
                    onClick = {
                        cameraLat = scrubFix.latitude
                        cameraLon = scrubFix.longitude
                        isFollowingVehicle = true
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(TacticalSurface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(1.dp, if (isFollowingVehicle) ReconCyan else TacticalBorder, RoundedCornerShape(6.dp))
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Recenter", tint = if (isFollowingVehicle) ReconCyan else TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Category Filter Pills overlay at top-left
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryPill(
                    label = "ALL (${activeTargetsAtTime.size})",
                    isSelected = selectedFilterCategory == null,
                    color = ReconCyan,
                    onClick = { selectedFilterCategory = null }
                )
                CategoryPill(
                    label = "CAR KEYS (${activeTargetsAtTime.count { it.target.category == RfCategory.CAR_KEY }})",
                    isSelected = selectedFilterCategory == RfCategory.CAR_KEY,
                    color = Color(0xFFFF5252),
                    onClick = { selectedFilterCategory = RfCategory.CAR_KEY }
                )
                CategoryPill(
                    label = "VEHICLES (${activeTargetsAtTime.count { it.target.category == RfCategory.VEHICLE }})",
                    isSelected = selectedFilterCategory == RfCategory.VEHICLE,
                    color = ReconCyan,
                    onClick = { selectedFilterCategory = RfCategory.VEHICLE }
                )
                CategoryPill(
                    label = "CCTV (${activeTargetsAtTime.count { it.target.category == RfCategory.CCTV_SURVEILLANCE }})",
                    isSelected = selectedFilterCategory == RfCategory.CCTV_SURVEILLANCE,
                    color = Color(0xFFFFD600),
                    onClick = { selectedFilterCategory = RfCategory.CCTV_SURVEILLANCE }
                )
                CategoryPill(
                    label = "CELL (${activeTargetsAtTime.count { it.target.category == RfCategory.CELL_TOWER }})",
                    isSelected = selectedFilterCategory == RfCategory.CELL_TOWER,
                    color = ReconAmber,
                    onClick = { selectedFilterCategory = RfCategory.CELL_TOWER }
                )
                CategoryPill(
                    label = "WI-FI (${activeTargetsAtTime.count { it.target.category == RfCategory.ACCESS_POINT }})",
                    isSelected = selectedFilterCategory == RfCategory.ACCESS_POINT,
                    color = ReconGreen,
                    onClick = { selectedFilterCategory = RfCategory.ACCESS_POINT }
                )
            }
        }

        // =====================================================================
        // 3. TIME SLIDER & TRANSPORT REPLAY CONTROLS (MIDDLE SECTION)
        // =====================================================================
        Surface(
            color = TacticalSurface,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Time Readout Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(13.dp))
                        Text(
                            text = timeFormatted,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = relativeTimeDesc,
                            color = if (isLiveMode) ReconGreen else ReconAmber,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "FIX ${currentIndex + 1}/${effectiveEntries.size}",
                        color = TextSecondary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Interactive Slider
                Slider(
                    value = sliderProgress,
                    onValueChange = { newVal ->
                        sliderProgress = newVal
                        isPlaying = false
                        isLiveMode = (newVal >= 0.999f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = ReconCyan,
                        activeTrackColor = ReconCyan,
                        inactiveTrackColor = TacticalBorder
                    )
                )

                // Timeline Start / End Time Labels
                val startTimeStr = remember(effectiveEntries) {
                    if (effectiveEntries.isNotEmpty()) timeFormat.format(Date(effectiveEntries.first().timestamp)) else "--:--:--"
                }
                val endTimeStr = remember(effectiveEntries) {
                    if (effectiveEntries.isNotEmpty()) timeFormat.format(Date(effectiveEntries.last().timestamp)) else "--:--:--"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "START: $startTimeStr", color = TextSecondary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "SCRUB: $timeFormatted", color = ReconCyan, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(text = "LATEST: $endTimeStr", color = TextSecondary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Transport Controls Row (Jump -1m, Jump -10s, Play/Pause, Jump +10s, Jump +1m, Speed, Live)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Jump -1m
                    TransportButton(label = "◀◀ 1m") {
                        val step = (60_000L.toFloat() / max(1L, (effectiveEntries.last().timestamp - effectiveEntries.first().timestamp))).coerceAtLeast(0.02f)
                        sliderProgress = (sliderProgress - step).coerceIn(0f, 1f)
                        isPlaying = false
                        isLiveMode = false
                    }

                    // Jump -10s
                    TransportButton(label = "◀ 10s") {
                        val step = (10_000L.toFloat() / max(1L, (effectiveEntries.last().timestamp - effectiveEntries.first().timestamp))).coerceAtLeast(0.005f)
                        sliderProgress = (sliderProgress - step).coerceIn(0f, 1f)
                        isPlaying = false
                        isLiveMode = false
                    }

                    // Play / Pause Main Button
                    Surface(
                        color = ReconCyan.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        modifier = Modifier.clickable {
                            if (sliderProgress >= 1.0f) {
                                sliderProgress = 0.0f
                            }
                            isPlaying = !isPlaying
                            if (isPlaying) isLiveMode = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ReconCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isPlaying) "PAUSE" else "PLAY",
                                color = ReconCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Jump +10s
                    TransportButton(label = "10s ▶") {
                        val step = (10_000L.toFloat() / max(1L, (effectiveEntries.last().timestamp - effectiveEntries.first().timestamp))).coerceAtLeast(0.005f)
                        sliderProgress = (sliderProgress + step).coerceIn(0f, 1f)
                        if (sliderProgress >= 1.0f) isLiveMode = true
                    }

                    // Jump +1m
                    TransportButton(label = "1m ▶▶") {
                        val step = (60_000L.toFloat() / max(1L, (effectiveEntries.last().timestamp - effectiveEntries.first().timestamp))).coerceAtLeast(0.02f)
                        sliderProgress = (sliderProgress + step).coerceIn(0f, 1f)
                        if (sliderProgress >= 1.0f) isLiveMode = true
                    }

                    // Speed Toggle
                    Surface(
                        color = TacticalObsidian,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier.clickable {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 2.0f
                                2.0f -> 5.0f
                                else -> 1.0f
                            }
                        }
                    ) {
                        Text(
                            text = "${playbackSpeed.toInt()}x",
                            color = ReconAmber,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Snap to LIVE Button
                    Surface(
                        color = if (isLiveMode) ReconGreen.copy(alpha = 0.20f) else TacticalObsidian,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLiveMode) ReconGreen else TacticalBorder),
                        modifier = Modifier.clickable {
                            sliderProgress = 1.0f
                            isLiveMode = true
                            isPlaying = false
                            isFollowingVehicle = true
                        }
                    ) {
                        Text(
                            text = "● LIVE",
                            color = if (isLiveMode) ReconGreen else TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Reset / Clear History Button
                    Surface(
                        color = TacticalObsidian,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, TacticalBorder),
                        modifier = Modifier.clickable {
                            timelineManager.clearTimeline()
                            sliderProgress = 1.0f
                            isLiveMode = true
                            isPlaying = false
                        }
                    ) {
                        Text(
                            text = "RESET",
                            color = TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. SCANNER TARGET INTELLIGENCE LIST AT SCRUBBED TIME (BOTTOM SECTION)
        // =====================================================================
        Surface(
            color = TacticalObsidian,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.95f)
        ) {
            if (filteredTargets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Sensors, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Text(
                            text = "NO RF EMITTERS DETECTED IN THIS TIME SECTOR",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredTargets, key = { it.target.id }) { snapshot ->
                        TimelineTargetCard(
                            snapshot = snapshot,
                            isSelected = selectedTarget?.target?.id == snapshot.target.id,
                            onClick = {
                                selectedTarget = snapshot
                                cameraLat = snapshot.displayLat
                                cameraLon = snapshot.displayLon
                                isFollowingVehicle = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Target Detail Dialog
    selectedTarget?.let { snap ->
        TimelineTargetDetailDialog(
            snapshot = snap,
            onDismiss = { selectedTarget = null },
            onNavigate = {
                onNavigateToTarget(snap.target.displayName, snap.displayLat, snap.displayLon)
                selectedTarget = null
            }
        )
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.22f) else TacticalSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) color else TacticalBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) color else TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TransportButton(label: String, onClick: () -> Unit) {
    Surface(
        color = TacticalObsidian,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, TacticalBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TimelineTargetCard(
    snapshot: TimelineTargetSnapshot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val catColor = getCategoryColor(snapshot.target.category)

    Surface(
        color = if (isSelected) catColor.copy(alpha = 0.15f) else TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) catColor else TacticalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.20f))
                        .border(1.dp, catColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(snapshot.target.category),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Column {
                    Text(
                        text = snapshot.target.displayName,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = ReconCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(3.dp),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, ReconCyan.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = snapshot.target.detectionTech.shortName.uppercase(),
                                color = ReconCyan,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                            )
                        }
                        Text(
                            text = "${snapshot.target.macAddress} • ${snapshot.target.vendor}",
                            color = TextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            // Signal & Distance Readout
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${snapshot.displayRssi} dBm",
                    color = catColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${snapshot.displayDistanceMeters.toInt()}m • ${snapshot.relativeBearingDeg.toInt()}°",
                    color = ReconCyan,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TimelineTargetDetailDialog(
    snapshot: TimelineTargetSnapshot,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit
) {
    val target = snapshot.target
    val catColor = getCategoryColor(target.category)
    var inspectingTech by remember { mutableStateOf<DetectionTechnology?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, catColor, RoundedCornerShape(12.dp)),
            color = TacticalObsidian
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = getCategoryIcon(target.category), contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                        Text(text = target.category.name, color = catColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }

                Text(text = target.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Prominent Detection Technology Attribution with Clickable Info Icon
                        Surface(
                            color = ReconCyan.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, ReconCyan.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { inspectingTech = target.detectionTech }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "DETECTION TECH",
                                        color = ReconCyan,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = target.detectionTech.displayName,
                                        color = TextPrimary,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(ReconCyan.copy(alpha = 0.25f))
                                            .border(0.8.dp, ReconCyan, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Inspect Tech",
                                            tint = ReconCyan,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DetailRow("MAC / BSSID", target.macAddress)
                        DetailRow("MANUFACTURER", target.vendor)
                        DetailRow("PROTOCOL", target.protocol)
                        DetailRow("SIGNAL @ TIME", "${snapshot.displayRssi} dBm (${snapshot.displayDistanceMeters.toInt()}m away)")
                        DetailRow("BEARING", "${snapshot.relativeBearingDeg.toInt()}° relative")
                        DetailRow("COORDINATES", "%.4f°N, %.4f°W".format(snapshot.displayLat, abs(snapshot.displayLon)))
                        if (target.details.isNotBlank()) {
                            DetailRow("INTEL", target.details)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onNavigate,
                        colors = ButtonDefaults.buttonColors(containerColor = ReconCyan.copy(alpha = 0.22f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "NAVIGATE", color = ReconCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    // Technology Specs and Operational Mechanics Dialog
    inspectingTech?.let { tech ->
        TacticalTechInfoDialog(
            tech = tech,
            onDismiss = { inspectingTech = null }
        )
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = TextPrimary, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

private fun getCategoryColor(category: RfCategory): Color {
    return when (category) {
        RfCategory.CAR_KEY -> Color(0xFFFF5252)
        RfCategory.VEHICLE -> ReconCyan
        RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFFD600)
        RfCategory.DRONE_UAV -> Color(0xFFE040FB)
        RfCategory.CELL_TOWER -> ReconAmber
        RfCategory.ACCESS_POINT -> ReconGreen
    }
}

private fun getCategoryIcon(category: RfCategory) = when (category) {
    RfCategory.CAR_KEY -> Icons.Default.Key
    RfCategory.VEHICLE -> Icons.Default.DirectionsCar
    RfCategory.CCTV_SURVEILLANCE -> Icons.Default.Videocam
    RfCategory.DRONE_UAV -> Icons.Default.Flight
    RfCategory.CELL_TOWER -> Icons.Default.CellTower
    RfCategory.ACCESS_POINT -> Icons.Default.Wifi
}

/**
 * Computes RF targets observed / active within a ±3 minute window around scrubTimeMs.
 */
private fun computeTargetsAtTime(
    targets: List<DetectedRfTarget>,
    scrubTimeMs: Long,
    hostLat: Double,
    hostLon: Double,
    hostBearing: Float
): List<TimelineTargetSnapshot> {
    val results = mutableListOf<TimelineTargetSnapshot>()

    for (t in targets) {
        var matchedLat: Double? = null
        var matchedLon: Double? = null
        var matchedRssi = t.rssi
        var matchedDist = t.estimatedDistanceMeters
        var matchedBearing = t.relativeBearingDeg
        var observationTime = t.timestamp

        // Check if target has observation breadcrumbs near this scrub time
        val nearestBreadcrumb = t.locationHistory.minByOrNull { abs(it.timestamp - scrubTimeMs) }
        if (nearestBreadcrumb != null && abs(nearestBreadcrumb.timestamp - scrubTimeMs) <= 240_000L) {
            matchedLat = nearestBreadcrumb.targetLat
            matchedLon = nearestBreadcrumb.targetLon
            matchedRssi = nearestBreadcrumb.rssi
            matchedDist = nearestBreadcrumb.distanceMeters
            matchedBearing = nearestBreadcrumb.relativeBearingDeg
            observationTime = nearestBreadcrumb.timestamp
        } else if (t.detectedLat != null && t.detectedLon != null) {
            val distM = calculateDistanceMeters(hostLat, hostLon, t.detectedLat, t.detectedLon)
            val isStaticInfra = t.category == RfCategory.CELL_TOWER || t.category == RfCategory.CCTV_SURVEILLANCE
            if (isStaticInfra || abs(t.timestamp - scrubTimeMs) <= 300_000L) {
                matchedLat = t.detectedLat
                matchedLon = t.detectedLon
                matchedDist = distM
                matchedRssi = (-42 - (distM / 22.0).toInt()).coerceIn(-95, -35)

                val dLatM = (t.detectedLat - hostLat) * 111320.0
                val dLonM = (t.detectedLon - hostLon) * 111320.0 * cos(Math.toRadians(hostLat))
                var wAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                if (wAngle < 0f) wAngle += 360f
                matchedBearing = (wAngle - hostBearing + 360f) % 360f
                observationTime = scrubTimeMs
            }
        }

        if (matchedLat != null && matchedLon != null && matchedDist <= 900.0) {
            results.add(
                TimelineTargetSnapshot(
                    target = t,
                    displayLat = matchedLat,
                    displayLon = matchedLon,
                    displayRssi = matchedRssi,
                    displayDistanceMeters = matchedDist,
                    relativeBearingDeg = matchedBearing,
                    observationTimeMs = observationTime
                )
            )
        }
    }

    return results.sortedBy { it.displayDistanceMeters }
}



private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6371000.0 * c
}

@Composable
fun TacticalTechInfoDialog(
    tech: DetectionTechnology,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.2.dp, ReconCyan, RoundedCornerShape(12.dp)),
            color = TacticalObsidian
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ReconCyan.copy(alpha = 0.2f))
                                .border(1.dp, ReconCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ReconCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "DETECTION TECHNOLOGY",
                            color = ReconCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Tech Display Name Banner
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = tech.displayName.uppercase(),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "FREQUENCY / SPECTRUM: ${tech.frequencyBand}",
                            color = ReconAmber,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Section 1: How ReconDrive Detects It
                TechSpecSection(
                    title = "DETECTION PRINCIPLE (HOW IT WORKS)",
                    body = tech.detectionPrinciple,
                    accentColor = ReconCyan
                )

                // Section 2: Tactical Significance
                TechSpecSection(
                    title = "TACTICAL UTILITY & INTELLIGENCE",
                    body = tech.tacticalSignificance,
                    accentColor = Color(0xFF00E5FF)
                )

                // Section 3: Technical Details & Formulas
                TechSpecSection(
                    title = "PHYSICS & SPECTRUM ANALYSIS",
                    body = tech.technicalDetails,
                    accentColor = ReconGreen
                )

                // Dismiss Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ReconCyan.copy(alpha = 0.22f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DISMISS",
                            color = ReconCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechSpecSection(
    title: String,
    body: String,
    accentColor: Color
) {
    Surface(
        color = TacticalSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, TacticalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = body,
                color = TextSecondary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 11.sp
            )
        }
    }
}
