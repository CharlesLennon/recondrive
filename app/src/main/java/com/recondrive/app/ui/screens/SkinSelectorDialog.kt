package com.recondrive.app.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recondrive.app.fog.FogPreferences
import com.recondrive.app.skin.FogSkin
import com.recondrive.app.skin.MapSkin
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*

@Composable
fun SkinSelectorDialog(
    skinManager: SkinManager,
    fogPreferences: FogPreferences,
    onDismiss: () -> Unit
) {
    val currentMapSkin by skinManager.currentMapSkin.collectAsState()
    val currentFogSkin by skinManager.currentFogSkin.collectAsState()
    val currentRange by fogPreferences.fogRangeMeters.collectAsState()
    val currentOpacity by fogPreferences.fogOpacity.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Map, 1 = Fog, 2 = Calibration

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(12.dp),
            color = TacticalObsidian,
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
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
                        Text(
                            text = "CUSTOMIZATION & FOG OPTICS",
                            color = ReconCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Independent map cartography, fog skins & optics",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3 Independent Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TacticalSurface,
                    contentColor = ReconCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ReconCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "MAP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "FOG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "RANGE & OPACITY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Panel
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> MapSkinList(
                            currentSkin = currentMapSkin,
                            onSelect = { skinManager.setMapSkin(it) }
                        )
                        1 -> FogSkinList(
                            currentSkin = currentFogSkin,
                            onSelect = { skinManager.setFogSkin(it) }
                        )
                        2 -> FogOpticsCalibrationPanel(
                            currentRange = currentRange,
                            currentOpacity = currentOpacity,
                            currentFogSkin = currentFogSkin,
                            currentMapSkin = currentMapSkin,
                            onRangeChange = { fogPreferences.setFogRange(it) },
                            onOpacityChange = { fogPreferences.setFogOpacity(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Active Summary
                Surface(
                    color = TacticalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE OPTICS PROFILE",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${currentMapSkin.displayName} + ${currentFogSkin.displayName} • ${currentRange}m @ ${(currentOpacity * 100).toInt()}%",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "APPLY",
                                color = TacticalObsidian,
                                fontSize = 11.sp,
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

@Composable
private fun FogOpticsCalibrationPanel(
    currentRange: Int,
    currentOpacity: Float,
    currentFogSkin: FogSkin,
    currentMapSkin: MapSkin,
    onRangeChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Interactive Optics Mini-Radar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(currentMapSkin.backgroundColor))
                .border(1.dp, Color(currentFogSkin.perimeterGlowColor).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Draw base road line
                drawLine(
                    color = Color(currentMapSkin.highwayColor),
                    start = Offset(0f, cy + 20f),
                    end = Offset(size.width, cy - 20f),
                    strokeWidth = 4f
                )

                // Shroud overlay outside dynamic radius (Path Difference Hole Punch)
                val radiusPx = (35f + (currentRange - 50) * (50f / 450f)).coerceIn(30f, 95f)

                val miniShroud = androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                }
                val miniHole = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(cx, cy), radius = radiusPx))
                }
                val maskedMiniShroud = androidx.compose.ui.graphics.Path().apply {
                    op(miniShroud, miniHole, androidx.compose.ui.graphics.PathOperation.Difference)
                }
                val shroudAlpha = (currentFogSkin.shroudOpacity * currentOpacity).coerceIn(0.1f, 1.0f)
                drawPath(
                    path = maskedMiniShroud,
                    color = Color(currentFogSkin.shroudColor).copy(alpha = shroudAlpha)
                )

                // Cleared circle border & glowing reticle boundary
                drawCircle(
                    color = Color(currentFogSkin.perimeterGlowColor),
                    radius = radiusPx,
                    center = Offset(cx, cy),
                    style = Stroke(width = currentFogSkin.borderStrokeWidth)
                )

                // Inner pulse ring
                drawCircle(
                    color = Color(currentFogSkin.perimeterGlowColor).copy(alpha = 0.3f),
                    radius = radiusPx * 0.9f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )

                // Center vehicle dot
                drawCircle(
                    color = Color(currentMapSkin.vehicleCursorColor),
                    radius = 5f,
                    center = Offset(cx, cy)
                )
            }

            Text(
                text = "SURVEILLANCE BEAM: ${currentRange}m  •  OPACITY: ${(currentOpacity * 100).toInt()}%",
                color = Color(currentFogSkin.perimeterGlowColor),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }

        // 2. Fog Range Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FOG OF WAR RANGE",
                        color = ReconCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${currentRange} METERS",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = currentRange.toFloat(),
                    onValueChange = { onRangeChange(it.toInt()) },
                    valueRange = 50f..500f,
                    steps = 44, // 10m steps
                    colors = SliderDefaults.colors(
                        thumbColor = ReconCyan,
                        activeTrackColor = ReconCyan,
                        inactiveTrackColor = TacticalBorder
                    )
                )

                // Quick Range Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val rangePresets = listOf(
                        50 to "50m",
                        100 to "100m",
                        180 to "180m",
                        260 to "260m",
                        450 to "450m"
                    )
                    for ((meters, label) in rangePresets) {
                        Surface(
                            color = if (currentRange == meters) ReconCyan else TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (currentRange == meters) ReconCyan else TacticalBorder),
                            modifier = Modifier.clickable { onRangeChange(meters) }
                        ) {
                            Text(
                                text = label,
                                color = if (currentRange == meters) TacticalObsidian else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Fog Opacity Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SHROUD OPACITY (DARKNESS)",
                        color = ReconGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(currentOpacity * 100).toInt()}% DENSITY",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = currentOpacity,
                    onValueChange = { onOpacityChange(it) },
                    valueRange = 0.20f..1.00f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = ReconGreen,
                        activeTrackColor = ReconGreen,
                        inactiveTrackColor = TacticalBorder
                    )
                )

                // Quick Opacity Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val opacityPresets = listOf(
                        0.30f to "30% Mist",
                        0.50f to "50% Half",
                        0.75f to "75% Dark",
                        1.00f to "100% Void"
                    )
                    for ((opac, label) in opacityPresets) {
                        val isCurrent = kotlin.math.abs(currentOpacity - opac) < 0.04f
                        Surface(
                            color = if (isCurrent) ReconGreen else TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) ReconGreen else TacticalBorder),
                            modifier = Modifier.clickable { onOpacityChange(opac) }
                        ) {
                            Text(
                                text = label,
                                color = if (isCurrent) TacticalObsidian else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSkinList(
    currentSkin: MapSkin,
    onSelect: (MapSkin) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(MapSkin.values()) { skin ->
            val isSelected = skin == currentSkin
            val borderColor = if (isSelected) ReconCyan else TacticalBorder

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(skin) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .size(width = 54.dp, height = 36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(skin.backgroundColor))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(skin.highwayColor)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(skin.routeColor)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(skin.waterColor)))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skin.displayName,
                            color = if (isSelected) ReconCyan else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = skin.description,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = ReconCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FogSkinList(
    currentSkin: FogSkin,
    onSelect: (FogSkin) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(FogSkin.values()) { skin ->
            val isSelected = skin == currentSkin
            val borderColor = if (isSelected) ReconGreen else TacticalBorder

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(skin) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(skin.shroudColor))
                            .border(
                                width = skin.borderStrokeWidth.dp,
                                color = Color(skin.perimeterGlowColor),
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(skin.exploredRoadGlowColor))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skin.displayName,
                            color = if (isSelected) ReconGreen else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = skin.description,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = ReconGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
