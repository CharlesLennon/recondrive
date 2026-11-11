package com.recondrive.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recondrive.app.scanner.DetectedRfTarget
import com.recondrive.app.scanner.DroneTelemetry
import com.recondrive.app.ui.theme.*
import kotlin.math.abs

/**
 * Deep Forensic SIGINT Dialog for OpenDroneID (ASTM F3411 / ASD-STAN / DJI DroneID).
 * Surfaces all broadcast fields: 3D flight vectors, pilot GCS, CAA registration,
 * accuracy integrity, geofence, and raw SIGINT hex stream.
 */
@Composable
fun OpenDroneIdForensicDialog(
    target: DetectedRfTarget,
    onDismiss: () -> Unit,
    onNavigateToPilot: (lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val dt = target.droneTelemetry ?: DroneTelemetry(uasId = target.macAddress)
    var showRawHex by remember { mutableStateOf(false) }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = TacticalObsidian,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE040FB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // =============================================================
                // 1. DIALOG HEADER (Title, Close Button)
                // =============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE040FB).copy(alpha = 0.20f))
                                .border(1.dp, Color(0xFFE040FB), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flight,
                                contentDescription = "Drone",
                                tint = Color(0xFFE040FB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "AIRSPACE SIGINT // OPENDRONEID",
                                color = Color(0xFFE040FB),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "ASTM F3411-22a & ASD-STAN DIRECT REMOTE ID FORENSICS",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(TacticalSurface, CircleShape)
                            .border(1.dp, TacticalBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // =============================================================
                // 2. SCROLLABLE TELEMETRY CONTENT
                // =============================================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ---------------------------------------------------------
                    // A. AIRFRAME & UAS IDENTIFICATION CARD
                    // ---------------------------------------------------------
                    ForensicCard(title = "AIRCRAFT IDENTITY & AIRFRAME") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dt.modelName.ifBlank { "UAV DRONE" },
                                    color = Color(0xFFE040FB),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "MFR: ${dt.manufacturer} • ${dt.uasType}",
                                    color = ReconCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Flight Status Badge
                            Surface(
                                color = if (dt.isEmergency) Color(0xFFFF1744).copy(alpha = 0.25f) else if (dt.isAirborne) ReconGreen.copy(alpha = 0.20f) else ReconAmber.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (dt.isEmergency) Color(0xFFFF1744) else if (dt.isAirborne) ReconGreen else ReconAmber)
                            ) {
                                Text(
                                    text = dt.operationalStatus.uppercase(),
                                    color = if (dt.isEmergency) Color(0xFFFF1744) else if (dt.isAirborne) ReconGreen else ReconAmber,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Serial Number with Copy Action
                        dt.uasId?.let { serial ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TacticalObsidian, RoundedCornerShape(4.dp))
                                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "UAS SERIAL NUMBER (ANSI/CTA-2063-A)", color = TextSecondary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                    Text(text = serial, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                IconButton(
                                    onClick = { copyToClipboard("UAS Serial", serial) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ReconCyan, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        if (dt.ceClass != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "REGULATORY AIRFRAME: ${dt.ceClass}", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // ---------------------------------------------------------
                    // B. REAL-TIME 3D FLIGHT VECTORS & AIRSPACE COMPLIANCE
                    // ---------------------------------------------------------
                    ForensicCard(title = "3D FLIGHT DYNAMICS & AIRSPACE VECTORS") {
                        // Legal Ceiling Compliance Banner
                        Surface(
                            color = if (dt.isCeilingExceeded) Color(0xFFFF1744).copy(alpha = 0.20f) else ReconGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (dt.isCeilingExceeded) Color(0xFFFF1744) else ReconGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (dt.isCeilingExceeded) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (dt.isCeilingExceeded) Color(0xFFFF1744) else ReconGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (dt.isCeilingExceeded) {
                                        "⚠️ UK/EU CEILING EXCEEDED (>120m / 400ft AGL) • CAP 722 VIOLATION"
                                    } else {
                                        "✅ WITHIN LEGAL OPEN AIRSPACE CEILING (<=120m / 400ft AGL)"
                                    },
                                    color = if (dt.isCeilingExceeded) Color(0xFFFF5252) else ReconGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid of Altitude, Speed, Heading, Vertical Rate
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricBox(
                                label = "ALTITUDE AGL",
                                value = dt.altitudeAglMeters?.let { "${it.toInt()}m AGL" } ?: "N/A",
                                subValue = dt.heightType,
                                color = if (dt.isCeilingExceeded) Color(0xFFFF5252) else ReconCyan,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "ALTITUDE MSL",
                                value = dt.altitudeMslMeters?.let { "${"%.0f".format(it)}m MSL" } ?: "N/A",
                                subValue = "True Geodetic",
                                color = ReconCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricBox(
                                label = "GROUND SPEED",
                                value = dt.speedKmh?.let { "${"%.1f".format(it)} km/h" } ?: "0 km/h",
                                subValue = dt.speedMph?.let { "${"%.1f".format(it)} mph" } ?: "0 mph",
                                color = ReconGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "VERTICAL RATE",
                                value = dt.verticalSpeedMps?.let {
                                    val sign = if (it > 0f) "+" else ""
                                    "$sign${"%.1f".format(it)} m/s"
                                } ?: "0.0 m/s",
                                subValue = when {
                                    (dt.verticalSpeedMps ?: 0f) > 0.5f -> "CLIMBING"
                                    (dt.verticalSpeedMps ?: 0f) < -0.5f -> "DESCENDING"
                                    else -> "LEVEL FLIGHT"
                                },
                                color = when {
                                    (dt.verticalSpeedMps ?: 0f) > 0.5f -> ReconAmber
                                    (dt.verticalSpeedMps ?: 0f) < -0.5f -> Color(0xFF00E5FF)
                                    else -> TextSecondary
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "HEADING",
                                value = dt.headingDeg?.let { "${it.toInt()}°" } ?: "N/A",
                                subValue = dt.headingCardinal,
                                color = Color(0xFFFFD600),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Aircraft Lat/Lon coordinates
                        if (dt.droneLat != null && dt.droneLon != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TacticalObsidian, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UAV COORDS: ${"%.6f".format(dt.droneLat)}°N, ${"%.6f".format(abs(dt.droneLon))}°W",
                                    color = TextSecondary,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "DIST: ${"%.0f".format(target.estimatedDistanceMeters)}m",
                                    color = ReconCyan,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // C. REMOTE PILOT GROUND CONTROL STATION (GCS)
                    // ---------------------------------------------------------
                    ForensicCard(title = "REMOTE PILOT GROUND CONTROL (GCS)") {
                        if (dt.pilotLat != null && dt.pilotLon != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = dt.pilotLocationType,
                                        color = Color(0xFFE040FB),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${"%.6f".format(dt.pilotLat)}°N, ${"%.6f".format(abs(dt.pilotLon))}°W",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    dt.pilotAltitudeMslMeters?.let {
                                        Text(text = "Pilot Elevation: ${"%.0f".format(it)}m MSL", color = TextSecondary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Surface(
                                    color = Color(0xFFE040FB).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE040FB)),
                                    modifier = Modifier.clickable {
                                        onNavigateToPilot(dt.pilotLat, dt.pilotLon)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Navigation, contentDescription = "Navigate", tint = Color(0xFFE040FB), modifier = Modifier.size(13.dp))
                                        Text(text = "NAVIGATE TO PILOT", color = Color(0xFFE040FB), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                dt.distanceHostToPilotMeters?.let { dHost ->
                                    MetricBox(
                                        label = "VEHICLE -> PILOT",
                                        value = "${"%.0f".format(dHost)}m",
                                        subValue = "Direct Distance",
                                        color = Color(0xFFE040FB),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                dt.distanceDroneToPilotMeters?.let { dDrone ->
                                    MetricBox(
                                        label = "DRONE -> PILOT (LOS)",
                                        value = "${"%.0f".format(dDrone)}m",
                                        subValue = "Control Link Range",
                                        color = ReconCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Waiting for System Message 0x4 (Pilot coordinates broadcast periodically by GCS controller)...",
                                color = TextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // D. CIVIL AVIATION REGISTRATION & MISSION DECLARATION
                    // ---------------------------------------------------------
                    ForensicCard(title = "CAA REGISTRATION & MISSION SELF-ID") {
                        if (dt.operatorId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TacticalObsidian, RoundedCornerShape(4.dp))
                                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "CIVIL AVIATION OPERATOR / FLYER ID (MSG 0x5)", color = TextSecondary, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                    Text(text = dt.operatorId, color = ReconGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                IconButton(
                                    onClick = { copyToClipboard("Operator ID", dt.operatorId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ReconGreen, modifier = Modifier.size(13.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (!dt.description.isNullOrBlank()) {
                            Text(
                                text = "DECLARED MISSION: \"${dt.description}\"",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (dt.operatorId == null && dt.description.isNullOrBlank()) {
                            Text(
                                text = "Awaiting Message 0x3 / 0x5 (Operator ID & Mission description)...",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // E. GNSS NAVIGATION QUALITY & SIGNAL INTEGRITY
                    // ---------------------------------------------------------
                    ForensicCard(title = "GNSS INTEGRITY & SIGNAL FORENSICS") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricBox(
                                label = "H-ACCURACY",
                                value = dt.horizontalAccuracy ?: "Unknown",
                                subValue = "Horiz Navigation",
                                color = ReconCyan,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "V-ACCURACY",
                                value = dt.verticalAccuracy ?: "Unknown",
                                subValue = "Vert Navigation",
                                color = ReconCyan,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "SPEED ACCURACY",
                                value = dt.speedAccuracy ?: "Unknown",
                                subValue = "Velocity Fix",
                                color = ReconCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "SIGNAL: ${target.rssi} dBm (2.4 GHz)", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            dt.gpsTimestampSeconds?.let {
                                Text(text = "GPS TIME: ${"%.1f".format(it)}s past hr", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // F. SWARM FORMATION & GEOFENCE PERIMETER
                    // ---------------------------------------------------------
                    if (dt.areaCount > 1 || dt.areaRadiusMeters != null) {
                        ForensicCard(title = "SWARM & OPERATIONAL PERIMETER") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetricBox(
                                    label = "SWARM COUNT",
                                    value = "${dt.areaCount} Drones",
                                    subValue = "Group Formation",
                                    color = Color(0xFFFFD600),
                                    modifier = Modifier.weight(1f)
                                )
                                dt.areaRadiusMeters?.let { r ->
                                    MetricBox(
                                        label = "GEOFENCE RADIUS",
                                        value = "${r}m",
                                        subValue = "Operation Perimeter",
                                        color = Color(0xFFFFD600),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // G. RAW SIGINT HEX STREAM INSPECTOR
                    // ---------------------------------------------------------
                    Surface(
                        color = TacticalSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRawHex = !showRawHex },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RAW OPENDRONEID BYTE STREAM (FORENSIC DUMP)",
                                    color = ReconCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    imageVector = if (showRawHex) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand",
                                    tint = ReconCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (showRawHex) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val hexDump = dt.rawPayloadHex ?: "4F50454E44524F4E454944"
                                Text(
                                    text = hexDump.chunked(2).joinToString(" "),
                                    color = TextSecondary,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Close Button
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "DISMISS AIRSPACE INTEL",
                            color = TextSecondary,
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
private fun ForensicCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                color = ReconCyan,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    subValue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TacticalObsidian,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(text = label, color = TextSecondary, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text(text = value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = subValue, color = TextSecondary.copy(alpha = 0.7f), fontSize = 6.5.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
