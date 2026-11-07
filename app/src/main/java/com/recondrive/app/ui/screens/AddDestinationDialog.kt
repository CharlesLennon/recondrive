package com.recondrive.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.recondrive.app.destinations.SavedDestination
import com.recondrive.app.ui.theme.*

@Composable
fun AddDestinationDialog(
    initialPersonName: String? = null,
    currentLat: Double,
    currentLon: Double,
    existingContacts: List<String> = emptyList(),
    onSave: (SavedDestination) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var personName by remember { mutableStateOf(initialPersonName ?: "") }
    var latText by remember { mutableStateOf("%.4f".format(currentLat)) }
    var lonText by remember { mutableStateOf("%.4f".format(currentLon)) }
    var selectedIcon by remember { mutableStateOf("HOME") }
    var photoUriString by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUriString = uri.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(12.dp),
            color = TacticalObsidian,
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                            text = "TACTICAL // TARGET REGISTRATION",
                            color = ReconCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Save personal waypoint or contact destination",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Location Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location Name", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ReconCyan,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Person / Group Name Field (Optional)
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person / Group Name (Optional)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ReconAmber,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (existingContacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Existing:", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        for (c in existingContacts.take(3)) {
                            Surface(
                                color = TacticalSurface,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                modifier = Modifier.clickable { personName = c }
                            ) {
                                Text(
                                    text = c,
                                    color = ReconAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Address / Subtext
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Landmark Subtext", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ReconCyan,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // GPS Coordinates (Lat / Lon)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ReconCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ReconCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tactical Icon Selector
                Text(
                    text = "SELECT TACTICAL ICON",
                    color = ReconCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val icons = listOf(
                        "HOME" to Icons.Default.Home,
                        "WORK" to Icons.Default.Work,
                        "STAR" to Icons.Default.Star,
                        "GYM" to Icons.Default.FitnessCenter,
                        "COFFEE" to Icons.Default.LocalCafe,
                        "PIN" to Icons.Default.Place
                    )
                    for ((type, icon) in icons) {
                        val isSel = selectedIcon == type
                        Surface(
                            color = if (isSel) ReconCyan else TacticalSurface,
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ReconCyan else TacticalBorder),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedIcon = type }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = type,
                                    tint = if (isSel) TacticalObsidian else TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Photo Section (Shown only if added!)
                Text(
                    text = "DESTINATION PHOTO (OPTIONAL)",
                    color = ReconGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (photoUriString != null) {
                    // Preview of selected photo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TacticalSurface)
                            .border(1.dp, ReconGreen, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = photoUriString),
                            contentDescription = "Destination Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, ReconCyan, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "PHOTO ATTACHED", color = ReconGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "Will be shown in quick glance card", color = TextSecondary, fontSize = 10.sp)
                        }
                        IconButton(onClick = { photoUriString = null }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove photo", tint = Color.Red)
                        }
                    }
                } else {
                    // No photo attached yet - action buttons to attach
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ReconGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "CHOOSE PHOTO", color = ReconGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Save Action
                Button(
                    onClick = {
                        val parsedLat = latText.toDoubleOrNull() ?: currentLat
                        val parsedLon = lonText.toDoubleOrNull() ?: currentLon
                        val finalName = name.ifBlank { "Tactical Waypoint" }
                        val finalPerson = personName.trim().ifBlank { null }
                        onSave(
                            SavedDestination(
                                name = finalName,
                                addressOrSubtext = address.ifBlank { "Lat: %.4f, Lon: %.4f".format(parsedLat, parsedLon) },
                                lat = parsedLat,
                                lon = parsedLon,
                                personName = finalPerson,
                                photoUri = photoUriString,
                                iconType = selectedIcon
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = TacticalObsidian)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LOCK & SAVE DESTINATION",
                        color = TacticalObsidian,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
