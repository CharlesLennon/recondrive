package com.recondrive.app.ui.screens

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recondrive.app.skin.FogSkin
import com.recondrive.app.skin.MapSkin
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*

@Composable
fun SkinSelectorDialog(
    skinManager: SkinManager,
    onDismiss: () -> Unit
) {
    val currentMapSkin by skinManager.currentMapSkin.collectAsState()
    val currentFogSkin by skinManager.currentFogSkin.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Map Skins, 1 = Fog Skins

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
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
                            text = "SKIN CUSTOMIZATION",
                            color = ReconCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Map and Fog skins operate completely independently",
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

                // Tabs (Map Skins vs Fog Skins)
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
                                text = "MAP THEMES (${MapSkin.values().size})",
                                fontSize = 12.sp,
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
                                text = "FOG OF WAR (${FogSkin.values().size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content List
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        MapSkinList(
                            currentSkin = currentMapSkin,
                            onSelect = { skinManager.setMapSkin(it) }
                        )
                    } else {
                        FogSkinList(
                            currentSkin = currentFogSkin,
                            onSelect = { skinManager.setFogSkin(it) }
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
                                text = "ACTIVE PAIRING",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${currentMapSkin.displayName} + ${currentFogSkin.displayName}",
                                color = TextPrimary,
                                fontSize = 12.sp,
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
                    // Swatches Preview
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
                    // Fog Swatches Preview
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
