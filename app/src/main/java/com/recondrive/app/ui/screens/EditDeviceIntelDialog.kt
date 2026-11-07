package com.recondrive.app.ui.screens

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
import androidx.compose.ui.window.Dialog
import com.recondrive.app.audio.TacticalSoundManager
import com.recondrive.app.scanner.DetectedRfTarget
import com.recondrive.app.scanner.SavedRfDevice
import com.recondrive.app.scanner.SavedRfDeviceRepository
import com.recondrive.app.ui.theme.*

/**
 * Tactical Intel Editor Dialog.
 * Allows users to register and annotate detected emitters with custom nicknames,
 * vehicle number plates, physical door/house numbers, tactical group tags, and custom alert sounds.
 */
@Composable
fun EditDeviceIntelDialog(
    target: DetectedRfTarget,
    repository: SavedRfDeviceRepository = com.recondrive.app.ReconApplication.instance.savedRfDeviceRepository,
    soundManager: TacticalSoundManager = com.recondrive.app.ReconApplication.instance.tacticalSoundManager,
    onDismiss: () -> Unit
) {
    val existing = remember(target.id) { repository.getDevice(target.id) }

    var nickname by remember { mutableStateOf(existing?.customNickname ?: "") }
    var numberPlate by remember { mutableStateOf(existing?.numberPlate ?: "") }
    var doorNumber by remember { mutableStateOf(existing?.doorNumberOrAddress ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var group by remember { mutableStateOf(existing?.group ?: "SURVEILLANCE") }
    var alertSound by remember { mutableStateOf(existing?.alertSound ?: "SONAR_PING") }
    var notifyOnDetection by remember { mutableStateOf(existing?.notifyOnDetection ?: true) }

    val presetGroups = listOf("KNOWN VEHICLES", "NEIGHBORS", "SURVEILLANCE", "WORK", "VIP", "CUSTOM")
    val soundOptions = listOf(
        "SONAR_PING" to "Sonar Ping",
        "TACTICAL_CHIRP" to "Tactical Chirp",
        "RADAR_BEEP" to "Radar Beep",
        "VOICE_ALERT" to "Voice Alert",
        "SILENT" to "Silent"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TARGET INTEL & ANNOTATION",
                            color = ReconCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${target.name} • ${target.macAddress}",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Nickname Input
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Custom Nickname / Alias", color = TextSecondary, fontSize = 10.sp) },
                    placeholder = { Text("e.g. Neighbor's Blue Tesla", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ReconCyan,
                        unfocusedBorderColor = TacticalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Number Plate & Door Number Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Number Plate
                    OutlinedTextField(
                        value = numberPlate,
                        onValueChange = { numberPlate = it.uppercase() },
                        label = { Text("Number Plate", color = TextSecondary, fontSize = 10.sp) },
                        placeholder = { Text("LJ71 WXA", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ReconAmber,
                            unfocusedTextColor = ReconAmber,
                            focusedBorderColor = ReconAmber,
                            unfocusedBorderColor = TacticalBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Door Number / Address
                    OutlinedTextField(
                        value = doorNumber,
                        onValueChange = { doorNumber = it },
                        label = { Text("Door # / House", color = TextSecondary, fontSize = 10.sp) },
                        placeholder = { Text("Door #42", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ReconCyan,
                            unfocusedBorderColor = TacticalBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Group Tags
                Text(text = "TACTICAL GROUP CLASSIFICATION", color = ReconCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetGroups.take(3).forEach { g ->
                        val isSel = group == g
                        Surface(
                            color = if (isSel) ReconCyan.copy(alpha = 0.2f) else TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ReconCyan else TacticalBorder),
                            modifier = Modifier.clickable { group = g }
                        ) {
                            Text(
                                text = g,
                                color = if (isSel) ReconCyan else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetGroups.drop(3).forEach { g ->
                        val isSel = group == g
                        Surface(
                            color = if (isSel) ReconCyan.copy(alpha = 0.2f) else TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ReconCyan else TacticalBorder),
                            modifier = Modifier.clickable { group = g }
                        ) {
                            Text(
                                text = g,
                                color = if (isSel) ReconCyan else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Alert Sound Selector & Preview
                Text(text = "CUSTOM NOTIFICATION AUDIO CUE", color = ReconCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    soundOptions.forEach { (sndKey, sndLabel) ->
                        val isSel = alertSound == sndKey
                        Surface(
                            color = if (isSel) ReconGreen.copy(alpha = 0.15f) else TacticalObsidian,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ReconGreen else TacticalBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alertSound = sndKey }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) ReconGreen else TacticalBorder)
                                    )
                                    Text(
                                        text = sndLabel,
                                        color = if (isSel) ReconGreen else TextPrimary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                if (sndKey != "SILENT") {
                                    Surface(
                                        color = ReconGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            soundManager.playAlert(sndKey, "Target ${numberPlate.ifBlank { nickname.ifBlank { target.name } }} in range")
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Test", tint = ReconGreen, modifier = Modifier.size(10.dp))
                                            Text(text = "TEST", color = ReconGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notify on Detection Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { notifyOnDetection = !notifyOnDetection },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Audio Alert When Seen", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Plays cue automatically whenever emitter appears in wardrive range", color = TextSecondary, fontSize = 8.sp)
                    }
                    Switch(
                        checked = notifyOnDetection,
                        onCheckedChange = { notifyOnDetection = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ReconGreen, checkedTrackColor = ReconGreen.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tactical Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Field Intelligence Notes", color = TextSecondary, fontSize = 10.sp) },
                    placeholder = { Text("e.g. Parked in front drive, active motion sensor", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ReconCyan,
                        unfocusedBorderColor = TacticalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (existing != null) {
                        OutlinedButton(
                            onClick = {
                                repository.deleteDevice(target.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "DELETE", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Button(
                        onClick = {
                            val toSave = SavedRfDevice(
                                id = target.id,
                                originalName = target.name,
                                customNickname = nickname.trim(),
                                numberPlate = numberPlate.trim().uppercase(),
                                doorNumberOrAddress = doorNumber.trim(),
                                notes = notes.trim(),
                                group = group,
                                alertSound = alertSound,
                                notifyOnDetection = notifyOnDetection,
                                lastObservedLat = target.detectedLat,
                                lastObservedLon = target.detectedLon
                            )
                            repository.saveDevice(toSave)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "SAVE INTEL", color = TacticalObsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
