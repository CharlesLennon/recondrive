package com.recondrive.app.ui.screens

import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.scanner.Bfi3dSnapshot
import com.recondrive.app.scanner.BfiVoxel
import com.recondrive.app.scanner.DeadZoneCluster
import com.recondrive.app.scanner.TacticalBfi3dEngine
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Filter mode for 3D voxel rendering.
 */
enum class VoxelFilterMode {
    ALL_VOXELS,
    DEAD_ZONES_ONLY,
    BEAM_LOBE_ONLY
}

/**
 * Tactical BFI 3D Spatial Scanner & Wi-Fi Beam / Dead Zone Mapper.
 *
 * Implements a real-time 3D perspective projection viewport rendering room RF propagation,
 * beamforming energy lobes, and dead zone null pockets as the user walks through physical space.
 */
@Composable
fun Bfi3dSpatialScreen(
    bfiEngine: TacticalBfi3dEngine,
    onClose: () -> Unit
) {
    val snapshot by bfiEngine.snapshot.collectAsState()
    val isScanning by bfiEngine.isScanning.collectAsState()
    val isGeigerAudioOn by bfiEngine.isGeigerAudioEnabled.collectAsState()
    val voxels by bfiEngine.voxels.collectAsState()
    val deadZones by bfiEngine.deadZones.collectAsState()

    // 3D Camera Orbit State
    var cameraAzimuthDeg by remember { mutableFloatStateOf(45.0f) }   // Horizontal orbit angle (0 - 360°)
    var cameraPitchDeg by remember { mutableFloatStateOf(35.0f) }     // Vertical tilt angle (15 - 85°)
    var cameraDistance by remember { mutableFloatStateOf(8.5f) }      // Camera distance from room center (4 - 20m)
    var followUserPosition by remember { mutableStateOf(false) }

    // Filter Mode
    var filterMode by remember { mutableStateOf(VoxelFilterMode.ALL_VOXELS) }

    // Animated Strobe for Dead Zone Warnings and Beam Wave
    val infiniteTransition = rememberInfiniteTransition(label = "Bfi3dStrobe")
    val deadZoneStrobe by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DeadZoneAlpha"
    )
    val beamWavePhase by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BeamWavePhase"
    )

    // Gesture handling for 3D Camera Orbit & Pinch Zoom
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        cameraDistance = (cameraDistance / zoomChange).coerceIn(4.0f, 22.0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
    ) {
        // =====================================================================
        // 1. 3D PERSPECTIVE CANVAS VIEWPORT
        // =====================================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformableState)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        cameraAzimuthDeg = (cameraAzimuthDeg - dragAmount.x * 0.35f + 360f) % 360f
                        cameraPitchDeg = (cameraPitchDeg + dragAmount.y * 0.25f).coerceIn(12.0f, 85.0f)
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f
            val focalLength = min(canvasWidth, canvasHeight) * 0.95f

            val camTargetX = if (followUserPosition) snapshot.posX else 0.0f
            val camTargetY = if (followUserPosition) snapshot.posY else 0.0f
            val camTargetZ = 1.0f

            // Helper to project 3D point (x, y, z) to screen coordinates (u, v)
            fun project(x: Float, y: Float, z: Float): ProjectedPoint? {
                val azRad = Math.toRadians(cameraAzimuthDeg.toDouble())
                val pitchRad = Math.toRadians(cameraPitchDeg.toDouble())

                val cosAz = cos(azRad).toFloat()
                val sinAz = sin(azRad).toFloat()
                val cosPitch = cos(pitchRad).toFloat()
                val sinPitch = sin(pitchRad).toFloat()

                val dx = x - camTargetX
                val dy = y - camTargetY
                val dz = z - camTargetZ

                // Rotate Azimuth around Z
                val x1 = dx * cosAz - dy * sinAz
                val y1 = dx * sinAz + dy * cosAz
                val z1 = dz

                // Rotate Pitch around X
                val x2 = x1
                val y2 = y1 * cosPitch - z1 * sinPitch
                val z2 = y1 * sinPitch + z1 * cosPitch

                val depth = y2 + cameraDistance
                if (depth <= 0.4f) return null

                val scale = focalLength / depth
                val screenX = centerX + x2 * scale
                val screenY = centerY - z2 * scale

                return ProjectedPoint(screenX, screenY, depth, scale)
            }

            // -----------------------------------------------------------------
            // A. Draw Room Tactical Grid (-4m to +4m on floor plane Z = 0)
            // -----------------------------------------------------------------
            val gridBounds = 4
            for (i in -gridBounds..gridBounds) {
                val fi = i.toFloat()
                // X lines (running along Y)
                val p1 = project(fi, -gridBounds.toFloat(), 0f)
                val p2 = project(fi, gridBounds.toFloat(), 0f)
                if (p1 != null && p2 != null) {
                    val isCenter = i == 0
                    val lineColor = if (isCenter) ReconCyan.copy(alpha = 0.5f) else RadarGrid.copy(alpha = 0.85f)
                    val strokeW = if (isCenter) 2.dp.toPx() else 1.dp.toPx()
                    drawLine(lineColor, Offset(p1.screenX, p1.screenY), Offset(p2.screenX, p2.screenY), strokeWidth = strokeW)
                }

                // Y lines (running along X)
                val p3 = project(-gridBounds.toFloat(), fi, 0f)
                val p4 = project(gridBounds.toFloat(), fi, 0f)
                if (p3 != null && p4 != null) {
                    val isCenter = i == 0
                    val lineColor = if (isCenter) ReconGreen.copy(alpha = 0.5f) else RadarGrid.copy(alpha = 0.85f)
                    val strokeW = if (isCenter) 2.dp.toPx() else 1.dp.toPx()
                    drawLine(lineColor, Offset(p3.screenX, p3.screenY), Offset(p4.screenX, p4.screenY), strokeWidth = strokeW)
                }
            }

            // -----------------------------------------------------------------
            // B. Draw Corner Altitude Pillars (Z = 0m to 2.5m)
            // -----------------------------------------------------------------
            val corners = listOf(
                -gridBounds.toFloat() to -gridBounds.toFloat(),
                gridBounds.toFloat() to -gridBounds.toFloat(),
                gridBounds.toFloat() to gridBounds.toFloat(),
                -gridBounds.toFloat() to gridBounds.toFloat()
            )
            for ((cx, cy) in corners) {
                val base = project(cx, cy, 0f)
                val top = project(cx, cy, 2.5f)
                if (base != null && top != null) {
                    drawLine(
                        color = TacticalBorder.copy(alpha = 0.7f),
                        start = Offset(base.screenX, base.screenY),
                        end = Offset(top.screenX, top.screenY),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Altitude tick marks at 1.0m and 2.0m
                    for (tz in listOf(1.0f, 2.0f)) {
                        val tick = project(cx, cy, tz)
                        if (tick != null) {
                            drawCircle(ReconCyan.copy(alpha = 0.4f), radius = 2.dp.toPx(), center = Offset(tick.screenX, tick.screenY))
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // C. Draw 3D Coordinate Arrows at Origin (0, 0, 0)
            // -----------------------------------------------------------------
            val orig = project(0f, 0f, 0f)
            val axX = project(1.2f, 0f, 0f) // East (Red)
            val axY = project(0f, 1.2f, 0f) // North (Green)
            val axZ = project(0f, 0f, 1.2f) // Up (Blue/Cyan)
            if (orig != null) {
                axX?.let {
                    drawLine(Color(0xFFFF5252), Offset(orig.screenX, orig.screenY), Offset(it.screenX, it.screenY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                axY?.let {
                    drawLine(ReconGreen, Offset(orig.screenX, orig.screenY), Offset(it.screenX, it.screenY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                axZ?.let {
                    drawLine(ReconCyan, Offset(orig.screenX, orig.screenY), Offset(it.screenX, it.screenY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            // -----------------------------------------------------------------
            // D. Draw Beamforming Corridor & Energy Lobe Cone
            // -----------------------------------------------------------------
            snapshot.beamVector?.let { beam ->
                val apProj = project(beam.estimatedApX, beam.estimatedApY, beam.estimatedApZ)
                if (apProj != null) {
                    // Pulsing RF concentric rings radiating from estimated AP router
                    val apScreen = Offset(apProj.screenX, apProj.screenY)
                    for (r in 1..3) {
                        val ringPhase = (beamWavePhase + (r * 0.33f)) % 1.0f
                        val ringRadius = (12f + ringPhase * 36f) * (apProj.scale / 100f).coerceIn(0.5f, 2.5f)
                        val ringAlpha = (1.0f - ringPhase) * 0.7f
                        drawCircle(
                            color = ReconCyan.copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = apScreen,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // AP Core Marker
                    drawCircle(ReconCyan, radius = 6.dp.toPx(), center = apScreen)
                    drawCircle(TacticalObsidian, radius = 2.5.dp.toPx(), center = apScreen)

                    // 3D Beam Axis Corridor connecting AP to User position
                    val userProj = project(snapshot.posX, snapshot.posY, snapshot.posZ)
                    if (userProj != null) {
                        drawLine(
                            color = ReconCyan.copy(alpha = 0.85f),
                            start = apScreen,
                            end = Offset(userProj.screenX, userProj.screenY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Beam Conical Energy Lobe Ray lines
                        val beamAngleRad = Math.toRadians(beam.beamAzimuthDeg.toDouble())
                        val perpX = (-sin(beamAngleRad) * 0.8f).toFloat()
                        val perpY = (cos(beamAngleRad) * 0.8f).toFloat()

                        val lobeLeft = project(snapshot.posX + perpX, snapshot.posY + perpY, snapshot.posZ)
                        val lobeRight = project(snapshot.posX - perpX, snapshot.posY - perpY, snapshot.posZ)

                        if (lobeLeft != null && lobeRight != null) {
                            val beamPath = Path().apply {
                                moveTo(apScreen.x, apScreen.y)
                                lineTo(lobeLeft.screenX, lobeLeft.screenY)
                                lineTo(lobeRight.screenX, lobeRight.screenY)
                                close()
                            }
                            drawPath(beamPath, color = ReconCyan.copy(alpha = 0.12f))
                            drawLine(ReconCyan.copy(alpha = 0.4f), apScreen, Offset(lobeLeft.screenX, lobeLeft.screenY), strokeWidth = 1.dp.toPx())
                            drawLine(ReconCyan.copy(alpha = 0.4f), apScreen, Offset(lobeRight.screenX, lobeRight.screenY), strokeWidth = 1.dp.toPx())
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // E. Draw 3D Dead Zone Hazard Bounding Boxes
            // -----------------------------------------------------------------
            if (filterMode != VoxelFilterMode.BEAM_LOBE_ONLY) {
                for (cluster in deadZones) {
                    val cProj = project(cluster.centerX, cluster.centerY, cluster.centerZ)
                    val bProj = project(cluster.centerX, cluster.centerY, 0f)

                    if (cProj != null) {
                        val clusterCenter = Offset(cProj.screenX, cProj.screenY)

                        // Drop line down to floor
                        if (bProj != null) {
                            drawLine(
                                color = AlertRed.copy(alpha = 0.45f),
                                start = clusterCenter,
                                end = Offset(bProj.screenX, bProj.screenY),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(AlertRed.copy(alpha = 0.35f), radius = 5.dp.toPx(), center = Offset(bProj.screenX, bProj.screenY))
                        }

                        // Pulsing red hazard sphere/halo
                        val hazardRadius = (16.dp.toPx() * (cProj.scale / 100f)).coerceIn(10f, 40f)
                        drawCircle(
                            color = AlertRed.copy(alpha = 0.25f * deadZoneStrobe),
                            radius = hazardRadius,
                            center = clusterCenter
                        )
                        drawCircle(
                            color = AlertRed.copy(alpha = 0.8f * deadZoneStrobe),
                            radius = hazardRadius * 0.4f,
                            center = clusterCenter
                        )

                        // Floating label
                        val textPaint = Paint().apply {
                            color = android.graphics.Color.argb((220 * deadZoneStrobe).toInt(), 255, 23, 68)
                            textSize = 24f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "NULL #${cluster.id} [${cluster.worstRssi}dBm]",
                            clusterCenter.x + hazardRadius + 6f,
                            clusterCenter.y + 8f,
                            textPaint
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // F. Render 3D Depth-Sorted Voxel Point Cloud
            // -----------------------------------------------------------------
            val filteredVoxels = when (filterMode) {
                VoxelFilterMode.ALL_VOXELS -> voxels
                VoxelFilterMode.DEAD_ZONES_ONLY -> voxels.filter { it.isDeadZone }
                VoxelFilterMode.BEAM_LOBE_ONLY -> voxels.filter { it.isBeamCore }
            }

            // Project all voxels and sort by depth (far points first, near points last)
            val projectedVoxels = filteredVoxels.mapNotNull { voxel ->
                project(voxel.x, voxel.y, voxel.z)?.let { proj ->
                    Triple(voxel, proj, proj.depth)
                }
            }.sortedByDescending { it.third }

            for ((voxel, proj, _) in projectedVoxels) {
                val screenPos = Offset(proj.screenX, proj.screenY)
                val baseRadius = when {
                    voxel.isDeadZone -> 8.dp.toPx()
                    voxel.isBeamCore -> 7.dp.toPx()
                    else -> 5.5.dp.toPx()
                }
                val scaleMultiplier = (proj.scale / 100f).coerceIn(0.45f, 2.2f)
                val dotRadius = baseRadius * scaleMultiplier

                val (dotColor, coreColor) = when {
                    voxel.isDeadZone -> AlertRed.copy(alpha = 0.85f * deadZoneStrobe) to Color.White
                    voxel.isBeamCore -> ReconCyan to Color(0xFFE0F7FA)
                    voxel.rssi >= -68 -> ReconGreen to Color(0xFFB9F6CA)
                    voxel.rssi >= -81 -> TacticalAmber to Color(0xFFFFF9C4)
                    else -> AlertRed.copy(alpha = 0.7f) to Color(0xFFFFCDD2)
                }

                // Outer glow aura
                drawCircle(color = dotColor.copy(alpha = 0.35f), radius = dotRadius * 1.6f, center = screenPos)
                // Solid voxel sphere
                drawCircle(color = dotColor, radius = dotRadius, center = screenPos)
                // Hot core point
                drawCircle(color = coreColor, radius = dotRadius * 0.35f, center = screenPos)
            }

            // -----------------------------------------------------------------
            // G. Draw Real-Time User / Phone 3D Avatar
            // -----------------------------------------------------------------
            val userProj = project(snapshot.posX, snapshot.posY, snapshot.posZ)
            val userFloorProj = project(snapshot.posX, snapshot.posY, 0f)

            if (userProj != null) {
                val userScreen = Offset(userProj.screenX, userProj.screenY)

                // Vertical drop line to floor shadow
                if (userFloorProj != null) {
                    val floorScreen = Offset(userFloorProj.screenX, userFloorProj.screenY)
                    drawLine(
                        color = ReconCyan.copy(alpha = 0.5f),
                        start = userScreen,
                        end = floorScreen,
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Floor shadow circle
                    drawCircle(ReconCyan.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = floorScreen)
                }

                // Phone heading arrow in 3D
                val headingRad = Math.toRadians(snapshot.headingDeg.toDouble())
                val tipX = snapshot.posX + (sin(headingRad) * 0.55f).toFloat()
                val tipY = snapshot.posY + (cos(headingRad) * 0.55f).toFloat()
                val tipProj = project(tipX, tipY, snapshot.posZ)

                if (tipProj != null) {
                    drawLine(
                        color = ReconCyan,
                        start = userScreen,
                        end = Offset(tipProj.screenX, tipProj.screenY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Outer tactical phone reticle
                drawCircle(
                    color = ReconCyan,
                    radius = 12.dp.toPx(),
                    center = userScreen,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(color = ReconCyan, radius = 4.dp.toPx(), center = userScreen)
            }
        }

        // =====================================================================
        // 2. TOP HUD HEADER & TELEMETRY DASHBOARD
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Classified Black Ops Banner & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = TacticalSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ BLACK OPS // BFI 3D SPATIAL SCANNER",
                            color = AlertRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .background(TacticalSurface.copy(alpha = 0.9f), CircleShape)
                        .border(1.dp, TacticalBorder, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // Live Network Telemetry Card
            Surface(
                color = TacticalSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (snapshot.isCurrentlyInDeadZone) AlertRed else ReconCyan.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📡", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = snapshot.currentSsid,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (isScanning) "● REC MAPPING" else "❚❚ PAUSED",
                            color = if (isScanning) ReconGreen else TacticalAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Telemetry Readouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RSSI: ${snapshot.currentRssi} dBm",
                            color = if (snapshot.isCurrentlyInDeadZone) AlertRed else ReconCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "BFI SCORE: ${snapshot.currentBfiScore}%",
                            color = ReconGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${snapshot.currentLinkSpeedMbps} Mbps",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${snapshot.currentFreqMhz} MHz",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Dead Zone Proximity Alert
                    if (snapshot.isCurrentlyInDeadZone) {
                        Surface(
                            color = AlertRed.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️ CRITICAL WI-FI DEAD ZONE DETECTED (RSSI: ${snapshot.currentRssi} dBm)",
                                    color = AlertRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time 3D Coordinates & Beam Direction Strip
            Surface(
                color = TacticalObsidian.copy(alpha = 0.88f),
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
                    Text(
                        text = "POS: X: ${"%.2f".format(snapshot.posX)}m | Y: ${"%.2f".format(snapshot.posY)}m | Z: ${"%.2f".format(snapshot.posZ)}m",
                        color = ReconCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "HDG: ${snapshot.headingDeg.toInt()}° | PITCH: ${snapshot.pitchDeg.toInt()}°",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // =====================================================================
        // 3. BOTTOM TACTICAL ACTION & CALIBRATION DECK
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Elevation Preset Strip (Floor, Desk, Chest, Overhead, Ceiling)
            Surface(
                color = TacticalSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ELEV:",
                        color = ReconCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    val elevationPresets = listOf(
                        "FLOOR\n0.1m" to 0.10f,
                        "DESK\n0.8m" to 0.80f,
                        "CHEST\n1.2m" to 1.20f,
                        "HEAD\n1.8m" to 1.80f,
                        "CEIL\n2.5m" to 2.50f
                    )

                    elevationPresets.forEach { (label, zValue) ->
                        val isSelected = abs(snapshot.posZ - zValue) < 0.25f
                        Button(
                            onClick = { bfiEngine.setManualElevation(zValue) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ReconCyan else TacticalObsidian
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ReconCyan else TacticalBorder
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) TacticalObsidian else TextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }

            // Primary Scanning & Action Buttons
            Surface(
                color = TacticalSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // START / PAUSE SCAN BUTTON
                        Button(
                            onClick = { bfiEngine.toggleScanning() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) ReconGreen else TacticalAmber
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = TacticalObsidian,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isScanning) "PAUSE MAPPING" else "START MAPPING",
                                color = TacticalObsidian,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // RE-ZERO ORIGIN
                        OutlinedButton(
                            onClick = { bfiEngine.reZeroOrigin() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RE-ZERO", color = ReconCyan, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        // CLEAR MAP
                        OutlinedButton(
                            onClick = { bfiEngine.clearMap() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = AlertRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLEAR", color = AlertRed, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Second Row: View Presets, Filter, Geiger Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera Angle Presets
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 3D Isometric View
                            OutlinedButton(
                                onClick = {
                                    cameraAzimuthDeg = 45f
                                    cameraPitchDeg = 35f
                                    cameraDistance = 8.5f
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("3D ISO", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = ReconCyan)
                            }

                            // Top-Down Plan View
                            OutlinedButton(
                                onClick = {
                                    cameraAzimuthDeg = 0f
                                    cameraPitchDeg = 84f
                                    cameraDistance = 9.0f
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("TOP-DOWN", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }

                        // Filter Mode Toggle
                        OutlinedButton(
                            onClick = {
                                filterMode = when (filterMode) {
                                    VoxelFilterMode.ALL_VOXELS -> VoxelFilterMode.DEAD_ZONES_ONLY
                                    VoxelFilterMode.DEAD_ZONES_ONLY -> VoxelFilterMode.BEAM_LOBE_ONLY
                                    VoxelFilterMode.BEAM_LOBE_ONLY -> VoxelFilterMode.ALL_VOXELS
                                }
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (filterMode == VoxelFilterMode.DEAD_ZONES_ONLY) AlertRed else ReconCyan
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = when (filterMode) {
                                    VoxelFilterMode.ALL_VOXELS -> "VIEW: ALL"
                                    VoxelFilterMode.DEAD_ZONES_ONLY -> "VIEW: NULLS"
                                    VoxelFilterMode.BEAM_LOBE_ONLY -> "VIEW: BEAM"
                                },
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (filterMode == VoxelFilterMode.DEAD_ZONES_ONLY) AlertRed else ReconCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Geiger Counter Acoustic Audio Toggle
                        Button(
                            onClick = { bfiEngine.toggleGeigerAudio() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGeigerAudioOn) ReconCyan.copy(alpha = 0.2f) else TacticalObsidian
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isGeigerAudioOn) ReconCyan else TacticalBorder
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isGeigerAudioOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (isGeigerAudioOn) ReconCyan else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isGeigerAudioOn) "GEIGER ON" else "MUTED",
                                color = if (isGeigerAudioOn) ReconCyan else TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Scanned Voxels & Dead Zones Stat Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "VOXELS MAPPED: ${voxels.size} (0.25m³)",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "DEAD ZONE POCKETS: ${deadZones.size}",
                            color = if (deadZones.isNotEmpty()) AlertRed else ReconGreen,
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

/**
 * 3D Projected Screen Point.
 */
data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val scale: Float
)
