package com.recondrive.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.recondrive.app.ui.theme.*

@Composable
fun TacticalHudScreen(
    stats: ExplorationStats,
    currentSpeedKmh: Float = 48f,
    currentHeading: Float = 45f,
    currentLat: Double = 51.5074,
    currentLon: Double = -0.1278,
    onSearchClick: () -> Unit = {},
    onOfflinePacksClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .padding(16.dp)
    ) {
        // 1. Top Status Banner
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
                    color = ReconCyan,
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

            // Connection Badge
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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

        Spacer(modifier = Modifier.height(12.dp))

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
                    color = ReconCyan,
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
                    color = TacticalAmber,
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

        // 3. Tactical Radar Map & Fog Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070A0F))
                .border(1.dp, ReconCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Range rings
                for (r in listOf(60f, 130f, 210f, 290f)) {
                    drawCircle(
                        color = RadarGrid,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Radar Crosshair
                drawLine(
                    color = RadarGrid,
                    start = Offset(cx - 20f, cy),
                    end = Offset(cx + 20f, cy),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = RadarGrid,
                    start = Offset(cx, cy - 20f),
                    end = Offset(cx, cy + 20f),
                    strokeWidth = 1.5f
                )

                // Explored Road Track (Phosphor Green)
                val road = Path().apply {
                    moveTo(cx - 140f, cy + 120f)
                    lineTo(cx - 60f, cy + 60f)
                    lineTo(cx, cy)
                    lineTo(cx + 80f, cy - 70f)
                    lineTo(cx + 180f, cy - 110f)
                }
                drawPath(
                    path = road,
                    color = ReconGreen,
                    style = Stroke(width = 6f)
                )

                // Beam scanning boundary (Fog of War cleared zone)
                drawCircle(
                    color = ReconCyan,
                    radius = 120f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )

                // Vehicle Cursor Arrow
                val cursor = Path().apply {
                    moveTo(cx, cy - 14f)
                    lineTo(cx + 10f, cy + 10f)
                    lineTo(cx, cy + 6f)
                    lineTo(cx - 10f, cy + 10f)
                    close()
                }
                drawPath(path = cursor, color = ReconCyan)
            }

            // Overlay HUD Badge
            Text(
                text = "SURVEILLANCE BEAM: 160M",
                color = ReconCyan,
                fontSize = 10.sp,
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
                            color = ReconGreen,
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
                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = ReconCyan,
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
