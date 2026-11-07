package com.recondrive.app.ui.screens

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recondrive.app.telemetry.LocationTimelineEntry
import com.recondrive.app.telemetry.LocationTimelineManager
import com.recondrive.app.ui.theme.*
import kotlin.math.abs

/**
 * Tactical Location Timeline Dialog.
 * Displays real-time and historical logged telemetry:
 * Speed, GPS direction, handset compass heading, drift angles, and altitude.
 */
@Composable
fun LocationTimelineSheet(
    timelineManager: LocationTimelineManager,
    currentSpeedKmh: Float,
    currentGpsBearing: Float,
    currentDeviceHeading: Float,
    onJumpToLocation: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val entries by timelineManager.timelineEntries.collectAsState()
    val isLogging by timelineManager.isLoggingEnabled.collectAsState()
    val maxSpeed by timelineManager.maxSpeedKmh.collectAsState()
    val avgSpeed by timelineManager.avgSpeedKmh.collectAsState()
    val totalDistanceM by timelineManager.totalDistanceMeters.collectAsState()
    val context = LocalContext.current

    val currentDrift = remember(currentGpsBearing, currentDeviceHeading) {
        var diff = (currentGpsBearing - currentDeviceHeading) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        diff
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ReconCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            color = TacticalObsidian
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isLogging) ReconGreen else ReconAmber, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCATION TIMELINE",
                                color = ReconCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "SPEED • GPS VECTOR VS COMPASS HEADING LOG",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Telemetry Quick Stats Card
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Speed column
                            Column {
                                Text(
                                    text = "SPEED",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "%.1f km/h".format(currentSpeedKmh),
                                    color = ReconGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "MAX: %.0f • AVG: %.0f".format(maxSpeed, avgSpeed),
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // GPS Direction Column
                            Column {
                                Text(
                                    text = "GPS VECTOR",
                                    color = ReconCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "%.0f° %s".format(
                                        currentGpsBearing,
                                        LocationTimelineEntry.bearingToCardinal(currentGpsBearing)
                                    ),
                                    color = ReconCyan,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "MOTION COURSE",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Device Heading Column
                            Column {
                                Text(
                                    text = "DEVICE HEADING",
                                    color = ReconAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "%.0f° %s".format(
                                        currentDeviceHeading,
                                        LocationTimelineEntry.bearingToCardinal(currentDeviceHeading)
                                    ),
                                    color = ReconAmber,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "HANDSET AZIMUTH",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Drift Angle
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "DRIFT YAW",
                                    color = if (abs(currentDrift) > 15f) ReconAmber else TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "%+.0f°".format(currentDrift),
                                    color = if (abs(currentDrift) > 15f) ReconAmber else TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "CRAB ANGLE",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Distance & Entry Count Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LOGGED: ${entries.size} POINTS • DIST: %.2f KM".format(totalDistanceM / 1000.0),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isLogging) "● LOGGING ACTIVE" else "○ LOGGING PAUSED",
                                color = if (isLogging) ReconGreen else ReconAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Bar (Toggle Log, Clear, Export GPX)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { timelineManager.toggleLogging() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isLogging) ReconAmber else ReconGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLogging) ReconAmber.copy(alpha = 0.5f) else ReconGreen.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isLogging) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLogging) "PAUSE" else "RESUME",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { timelineManager.clearTimeline() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val gpxContent = timelineManager.exportToGpx()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "ReconDrive Telemetry Track")
                                putExtra(Intent.EXTRA_TEXT, gpxContent)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Tactical GPX Track"))
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ReconCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = TacticalObsidian,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "EXPORT GPX",
                            color = TacticalObsidian,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Timeline Entries List
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Awaiting movement telemetry...",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Start driving to record speed, GPS vectors and compass drift.",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            TimelineEntryCard(
                                entry = entry,
                                onJump = { onJumpToLocation(entry.latitude, entry.longitude) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: LocationTimelineEntry,
    onJump: () -> Unit
) {
    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJump() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Row 1: Time, Speed Pill, Jump Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.formattedTime,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = when {
                            entry.speedKmh > 70f -> ReconAmber.copy(alpha = 0.2f)
                            entry.speedKmh > 15f -> ReconGreen.copy(alpha = 0.2f)
                            else -> ReconCyan.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "%.1f km/h".format(entry.speedKmh),
                            color = when {
                                entry.speedKmh > 70f -> ReconAmber
                                entry.speedKmh > 15f -> ReconGreen
                                else -> ReconCyan
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "JUMP",
                        color = ReconCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = ReconCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: GPS Direction vs Device Direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GPS Vector
                Surface(
                    color = ReconCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ReconCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GPS: %.0f° %s".format(entry.gpsBearing, entry.cardinalGpsBearing),
                            color = ReconCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Device Compass Heading
                Surface(
                    color = ReconAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ReconAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPASS: %.0f° %s".format(entry.deviceHeading, entry.cardinalDeviceHeading),
                            color = ReconAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Drift Badge
                val drift = entry.driftAngleDeg
                Surface(
                    color = (if (abs(drift) > 15f) ReconAmber else TacticalBorder).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Δ %+.0f°".format(drift),
                        color = if (abs(drift) > 15f) ReconAmber else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Coords & Altitude
            Text(
                text = "%.4f°N, %.4f°W • %.0fm alt • ±%.0fm".format(
                    entry.latitude,
                    entry.longitude,
                    entry.altitudeMeters,
                    entry.accuracyMeters
                ),
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
