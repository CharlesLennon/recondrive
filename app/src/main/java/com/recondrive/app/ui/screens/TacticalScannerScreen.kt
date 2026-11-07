package com.recondrive.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.scanner.DetectedRfTarget
import com.recondrive.app.scanner.RfCategory
import com.recondrive.app.scanner.TacticalRfScannerManager
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Tactical SIGINT RF Spectrum & Wardrive Scanner Screen.
 * Employs Wi-Fi 802.11 beacon auditing and Bluetooth Low Energy sniffing
 * to identify nearby vehicles (Tesla, Ford, Dashcams) and surveillance optics (Hikvision, Dahua, Ring, ANPR).
 */
@Composable
fun TacticalScannerScreen(
    scannerManager: TacticalRfScannerManager = com.recondrive.app.ReconApplication.instance.rfScannerManager,
    currentLat: Double = 50.8555,
    currentLon: Double = -0.2449,
    onNavigateToLocation: (name: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> }
) {
    val detectedTargets by scannerManager.detectedTargets.collectAsState()
    val isScanning by scannerManager.isScanning.collectAsState()
    val isSimulation by scannerManager.isSimulationEnabled.collectAsState()

    var selectedFilter by remember { mutableStateOf<RfCategory?>(null) } // null = ALL

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // =====================================================================
        // 1. TOP STATUS ROW (SIGINT Scanner Title & Live Sweep LED)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIGINT RF SCANNER // SPECTRUM AUDIT",
                    color = ReconCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "SWEEPING 2.4GHz • 5.0GHz • BLUETOOTH LE",
                    color = TextSecondary,
                    fontSize = 10.sp,
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
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
                    .size(32.dp)
                    .background(TacticalSurface, CircleShape)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sweep Spectrum",
                    tint = ReconCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 2. QUICK THREAT TELEMETRY PILLS (Vehicles, CCTV, Total RF)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Vehicles Pill
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(14.dp))
                        Text(text = "CARS", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(text = "$vehicleCount", color = ReconCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            // CCTV Pill
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                        Text(text = "CCTV", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(text = "$cctvCount", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            // Total RF Pill
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Sensors, contentDescription = null, tint = ReconGreen, modifier = Modifier.size(14.dp))
                        Text(text = "TOTAL", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(text = "${detectedTargets.size}", color = ReconGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================================
        // 3. 360° CIRCULAR RADAR SPECTRUM SCOPE
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            RadarScopeVisualizer(targets = detectedTargets)
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================================
        // 5. DETECTED TARGETS LIST (CAR, CCTV, DRONE & AP CARDS)
        // =====================================================================
        if (filteredTargets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Sensors, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "NO RF EMITTERS IN CURRENT FILTER", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filteredTargets, key = { it.id }) { target ->
                    RfTargetCard(
                        target = target,
                        onPinToMap = {
                            val pinLat = target.detectedLat ?: (currentLat + 0.0002)
                            val pinLon = target.detectedLon ?: (currentLon + 0.0002)
                            onNavigateToLocation(target.name, pinLat, pinLon)
                        }
                    )
                }
            }
        }
    }
}

/**
 * High-tech Tactical Radar Scope with sweeping phosphor beam and blip targets.
 */
@Composable
private fun RadarScopeVisualizer(targets: List<DetectedRfTarget>) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) * 0.44f

        // Range rings (10m, 25m, 50m)
        drawCircle(color = TacticalBorder.copy(alpha = 0.5f), radius = maxRadius * 0.33f, center = center, style = Stroke(1f))
        drawCircle(color = TacticalBorder.copy(alpha = 0.5f), radius = maxRadius * 0.66f, center = center, style = Stroke(1f))
        drawCircle(color = TacticalBorder.copy(alpha = 0.7f), radius = maxRadius, center = center, style = Stroke(1f))

        // Crosshairs
        drawLine(color = TacticalBorder.copy(alpha = 0.4f), start = Offset(center.x - maxRadius, center.y), end = Offset(center.x + maxRadius, center.y), strokeWidth = 1f)
        drawLine(color = TacticalBorder.copy(alpha = 0.4f), start = Offset(center.x, center.y - maxRadius), end = Offset(center.x, center.y + maxRadius), strokeWidth = 1f)

        // Sweeping Radar Line with faint phosphor trail
        val sweepRad = sweepAngle * (Math.PI.toFloat() / 180f)
        val sweepX = center.x + maxRadius * cos(sweepRad)
        val sweepY = center.y + maxRadius * sin(sweepRad)
        drawLine(
            color = ReconGreen,
            start = center,
            end = Offset(sweepX, sweepY),
            strokeWidth = 2f
        )

        // Plot Detected Target Blips
        for (target in targets) {
            // Derive deterministic polar angle from MAC address hash so blip stays steady in scope
            val hashAngle = abs(target.id.hashCode() % 360).toFloat()
            val targetRad = hashAngle * (Math.PI.toFloat() / 180f)
            val distanceRatio = (target.estimatedDistanceMeters / 50.0).coerceIn(0.15, 0.92).toFloat()
            val blipDist = maxRadius * distanceRatio

            val bx = center.x + blipDist * cos(targetRad)
            val by = center.y + blipDist * sin(targetRad)

            val blipColor = when (target.category) {
                RfCategory.VEHICLE -> ReconCyan
                RfCategory.CCTV_SURVEILLANCE -> Color(0xFFFF5252)
                RfCategory.DRONE_UAV -> Color(0xFFBA68C8)
                RfCategory.ACCESS_POINT -> Color(0xFF64B5F6)
            }

            // Calculate angle difference to flash blip when sweep line passes over
            val angleDiff = abs((sweepAngle - hashAngle + 360f) % 360f)
            val isSwept = angleDiff < 30f
            val blipAlpha = if (isSwept) 1.0f else 0.5f
            val blipRadius = if (isSwept) 5.5f else 3.5f

            drawCircle(
                color = blipColor.copy(alpha = blipAlpha),
                radius = blipRadius,
                center = Offset(bx, by)
            )

            if (isSwept) {
                drawCircle(
                    color = blipColor.copy(alpha = 0.35f),
                    radius = blipRadius * 2.2f,
                    center = Offset(bx, by),
                    style = Stroke(1.5f)
                )
            }
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Individual Tactical Target Card (Vehicles, CCTV, Dashcams, IoT)
 */
@Composable
private fun RfTargetCard(
    target: DetectedRfTarget,
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(categoryColor.copy(alpha = 0.15f))
                    .border(1.dp, categoryColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = iconVector, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Intel Details Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = target.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Subtext with Vendor, SSID, Protocol
                Text(
                    text = "${target.vendor} • ${target.protocol}",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )

                if (target.details.isNotBlank()) {
                    Text(
                        text = target.details,
                        color = categoryColor.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                // MAC and Raw SSID
                Text(
                    text = "MAC: ${target.macAddress} • SSID: ${target.rawSsidOrBleName.ifBlank { "<HIDDEN / BLE>" }}",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Column: Signal Strength & Pin to Map Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Signal / Distance Badge
                Surface(
                    color = TacticalObsidian,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SignalStrengthBars(bars = target.signalBars, color = categoryColor)
                        Text(
                            text = "${target.rssi}dBm (~%.0fm)".format(target.estimatedDistanceMeters),
                            color = TextPrimary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Pin to Map Button
                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onPinToMap() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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
