package com.recondrive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.audio.*
import com.recondrive.app.fog.FogPreferences
import com.recondrive.app.map.OfflineTileRepository
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*

/**
 * Tactical Settings & Audio Sound Arsenal Screen.
 * Provides granular alert delivery controls (Nothing, Notification, Voice, Both)
 * for all threats, continuous radar sound FX toggles, map theme shortcuts, and storage management.
 */
@Composable
fun TacticalSettingsScreen(
    soundPreferences: SoundPreferences,
    soundManager: TacticalSoundManager,
    skinManager: SkinManager,
    fogPreferences: FogPreferences,
    offlineTileRepository: OfflineTileRepository,
    onOpenSkins: () -> Unit,
    onOpenOfflinePacks: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Master Sound Telemetry
    val isMasterSoundOn by soundPreferences.isMasterSoundEnabled.collectAsState()
    val isVibrationOn by soundPreferences.isNotificationVibrationEnabled.collectAsState()
    val ttsRate by soundPreferences.ttsSpeechRate.collectAsState()

    // Visual Skin Telemetry
    val currentMapSkin by skinManager.currentMapSkin.collectAsState()
    val currentFogSkin by skinManager.currentFogSkin.collectAsState()
    val fogRange by fogPreferences.fogRangeMeters.collectAsState()
    val fogOpacity by fogPreferences.fogOpacity.collectAsState()

    val downloadedPacks = remember { offlineTileRepository.getDownloadedRegions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalObsidian)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =====================================================================
        // 1. TOP HEADER & SCREEN BANNER
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = ReconCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = ReconCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TACTICAL SETTINGS",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "AUDIO ARSENAL • SENSOR ROUTING • OPTICS",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Master Audio Pill Badge
                    Surface(
                        color = if (isMasterSoundOn) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isMasterSoundOn) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                    ) {
                        Text(
                            text = if (isMasterSoundOn) "🔊 AUDIO ACTIVE" else "🔇 MUTED",
                            color = if (isMasterSoundOn) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. MASTER AUDIO CONTROLS
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "MASTER AUDIO CONTROLS",
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Master Audio Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Master Sound Engine",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Global toggle for all synthesized audio, voice speech alerts, and radar pings",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = isMasterSoundOn,
                        onCheckedChange = {
                            soundPreferences.setMasterSoundEnabled(it)
                            soundManager.playUiClick(force = true)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TacticalObsidian,
                            checkedTrackColor = ReconCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = TacticalBorder
                        )
                    )
                }

                Divider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Haptic / Vibration on Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Vibration",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Tactile haptic pulses for high-priority notification drawer alerts",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = isVibrationOn,
                        onCheckedChange = {
                            soundPreferences.setNotificationVibrationEnabled(it)
                            soundManager.playUiClick()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TacticalObsidian,
                            checkedTrackColor = ReconCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = TacticalBorder
                        )
                    )
                }

                Divider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Speech Rate Cadence
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voice Alert Cadence (TTS Rate)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "%.2fx".format(ttsRate),
                            color = ReconCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = ttsRate,
                        onValueChange = { soundPreferences.setTtsSpeechRate(it) },
                        valueRange = 0.8f..1.35f,
                        colors = SliderDefaults.colors(
                            thumbColor = ReconCyan,
                            activeTrackColor = ReconCyan,
                            inactiveTrackColor = TacticalBorder
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tactical Normal (1.0x)",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { soundPreferences.setTtsSpeechRate(1.0f) }
                        )
                        Text(
                            text = "Rapid Intel (1.20x)",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { soundPreferences.setTtsSpeechRate(1.20f) }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 3. GRANULAR THREAT & SENSOR ALERT ROUTING
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "THREAT & DETECTION ALERT ROUTING",
                            color = ReconCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Configure delivery: Nothing (Mute), Notification, Voice (TTS), or Both",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Render each Alert Category with its 4-way Segmented Button Row
                AlertCategory.values().forEach { category ->
                    val currentMode by soundPreferences.getAlertModeFlow(category).collectAsState()

                    AlertCategoryConfigRow(
                        category = category,
                        currentMode = currentMode,
                        onModeSelected = { mode ->
                            soundPreferences.setAlertMode(category, mode)
                            soundManager.playUiClick()
                        },
                        onTestAlert = {
                            soundManager.testAlert(category)
                        }
                    )
                    Divider(color = TacticalBorder.copy(alpha = 0.35f), thickness = 0.5.dp)
                }
            }
        }

        // =====================================================================
        // 4. RADAR SOUND FX & ACOUSTIC SWEEPS
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "RADAR SOUND FX & ACOUSTIC SWEEPS",
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Synthesized ambient audio effects (Radar sonar ping, target lock chirps, UI feedback)",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                SoundFxCategory.values().forEach { fx ->
                    val isFxOn by soundPreferences.getSoundFxFlow(fx).collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = fx.iconEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = fx.title,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = fx.description,
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Test sound button
                            Surface(
                                color = TacticalObsidian,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                modifier = Modifier.clickable { soundManager.testSoundFx(fx) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Test",
                                        tint = ReconCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "TEST",
                                        color = ReconCyan,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isFxOn,
                                onCheckedChange = {
                                    soundPreferences.setSoundFxEnabled(fx, it)
                                    soundManager.playUiClick()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TacticalObsidian,
                                    checkedTrackColor = ReconCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = TacticalBorder
                                )
                            )
                        }
                    }

                    if (fx != SoundFxCategory.values().last()) {
                        Divider(color = TacticalBorder.copy(alpha = 0.35f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // =====================================================================
        // 5. TACTICAL MAP OPTICS & VISUAL SKINS SHORTCUT
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "MAP THEMES & FOG OF WAR OPTICS",
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Map: [${currentMapSkin.displayName}]",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Fog Shroud: [${currentFogSkin.displayName}] • ${fogRange}m @ ${(fogOpacity * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Button(
                        onClick = onOpenSkins,
                        colors = ButtonDefaults.buttonColors(containerColor = TacticalObsidian),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SKINS", color = ReconCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // =====================================================================
        // 6. OFFLINE MAP PACKS & CARTOGRAPHY STORAGE
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "OFFLINE CARTOGRAPHY & PACKS",
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Offline Regions: ${downloadedPacks.size} Installed",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Standalone offline vector navigation data (Zero cellular dependency)",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Button(
                        onClick = onOpenOfflinePacks,
                        colors = ButtonDefaults.buttonColors(containerColor = TacticalObsidian),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PACKS", color = ReconCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // =====================================================================
        // 7. SYSTEM TELEMETRY & BUILD INFO
        // =====================================================================
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RECONDRIVE TACTICAL OPERATING SUITE",
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Build: v${com.recondrive.app.BuildConfig.VERSION_NAME} • Code: ${com.recondrive.app.BuildConfig.VERSION_CODE}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Multi-Spectrum Signals Intelligence • OpenDroneID • PSA EDC16/EGS6 Diagnostics",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Single Threat Category Row with 4-button Segmented Controller (NOTHING, NOTIF, VOICE, BOTH)
 */
@Composable
private fun AlertCategoryConfigRow(
    category: AlertCategory,
    currentMode: AlertDeliveryMode,
    onModeSelected: (AlertDeliveryMode) -> Unit,
    onTestAlert: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = category.iconEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = category.title,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = category.description,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Test Button
            Surface(
                color = TacticalObsidian,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                modifier = Modifier.clickable { onTestAlert() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test",
                        tint = ReconCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "TEST",
                        color = ReconCyan,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4-Way Segmented Control: [OFF] [NOTIF] [VOICE] [BOTH]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalObsidian)
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AlertDeliveryMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                val activeColor = when (mode) {
                    AlertDeliveryMode.OFF -> Color(0xFFFF5252)
                    AlertDeliveryMode.NOTIFICATION -> ReconCyan
                    AlertDeliveryMode.VOICE -> Color(0xFFFFD600)
                    AlertDeliveryMode.BOTH -> Color(0xFF00E676)
                }

                Surface(
                    color = if (isSelected) activeColor.copy(alpha = 0.20f) else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, activeColor) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelected(mode) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            color = if (isSelected) activeColor else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
