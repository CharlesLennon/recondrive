package com.recondrive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.recondrive.app.map.MapUpdateState
import com.recondrive.app.map.OsmMapUpdateManager
import com.recondrive.app.ui.theme.*
import com.recondrive.app.update.AppUpdateManager
import com.recondrive.app.update.ReleaseInfo
import com.recondrive.app.update.UpdateState

/**
 * Coordinated Startup Update & Sync Dialog
 * Appears when network connectivity is detected on load and updates are available
 * for either the application build or the UK OpenStreetMap vector cartography.
 * Gives the user granular control to update, skip individually, or proceed directly to navigation.
 */
@Composable
fun TacticalStartupUpdateDialog(
    appUpdateManager: AppUpdateManager,
    osmMapUpdateManager: OsmMapUpdateManager,
    onDismiss: () -> Unit
) {
    val appUpdateState by appUpdateManager.updateState.collectAsState()
    val mapUpdateState by osmMapUpdateManager.mapUpdateState.collectAsState()
    val scrollState = rememberScrollState()

    val hasAppUpdate = appUpdateState is UpdateState.UpdateAvailable ||
            appUpdateState is UpdateState.Downloading ||
            appUpdateState is UpdateState.ReadyToInstall ||
            appUpdateState is UpdateState.Installing

    val hasMapUpdate = mapUpdateState is MapUpdateState.UpdateAvailable ||
            mapUpdateState is MapUpdateState.Downloading ||
            mapUpdateState is MapUpdateState.Completed

    // Automatically dismiss dialog when app update enters full-screen download/install mode
    LaunchedEffect(appUpdateState) {
        if (appUpdateState is UpdateState.Downloading ||
            appUpdateState is UpdateState.ReadyToInstall ||
            appUpdateState is UpdateState.Installing) {
            onDismiss()
        }
    }

    // If both updates are no longer pending or both were skipped, auto-dismiss
    if (!hasAppUpdate && !hasMapUpdate) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ReconCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            color = TacticalObsidian
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = ReconCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TACTICAL SYSTEM SYNC",
                                color = ReconCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Network Online • Updates Available",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // =========================================================================
                // 1. UK OPENSTREETMAP DATA UPDATE SECTION
                // =========================================================================
                if (hasMapUpdate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = null,
                                        tint = ReconGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UK OPENSTREETMAP DATA",
                                        color = ReconGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Surface(
                                    color = ReconGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = when (val s = mapUpdateState) {
                                            is MapUpdateState.UpdateAvailable -> s.latestVersion
                                            is MapUpdateState.Downloading -> "SYNCING ${s.progressPercent}%"
                                            is MapUpdateState.Completed -> "UP TO DATE"
                                            else -> "NEW DATA"
                                        },
                                        color = ReconGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "United Kingdom Complete Vector Extract",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "New road geometries, motorways (M1, M25, M6, M4), A-road bypasses, roundabout updates, and speed limits across UK bounds.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            when (val state = mapUpdateState) {
                                is MapUpdateState.UpdateAvailable -> {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Download Size: ${state.formattedSize} • Release: ${state.releaseDate}",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { osmMapUpdateManager.downloadMapUpdate(state) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ReconGreen),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = TacticalObsidian,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "UPDATE MAP DATA",
                                                color = TacticalObsidian,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { osmMapUpdateManager.skipMapUpdate(state) },
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                                        ) {
                                            Text(
                                                text = "SKIP",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                is MapUpdateState.Downloading -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = state.progressPercent / 100f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = ReconGreen,
                                        trackColor = TacticalBorder
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Unpacking UK vector cartography: ${state.progressPercent}%",
                                        color = ReconGreen,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                is MapUpdateState.Completed -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ReconGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "UK OSM map data updated to ${state.version}!",
                                            color = ReconGreen,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }

                if (hasAppUpdate && hasMapUpdate) {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // =========================================================================
                // 2. APPLICATION RELEASE UPDATE SECTION
                // =========================================================================
                if (hasAppUpdate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = ReconAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "APPLICATION UPDATE",
                                        color = ReconAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Surface(
                                    color = ReconAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = when (val s = appUpdateState) {
                                            is UpdateState.UpdateAvailable -> s.release.tagName
                                            is UpdateState.Downloading -> "DL ${s.progressPercent}%"
                                            is UpdateState.ReadyToInstall -> "READY"
                                            is UpdateState.Installing -> "INSTALLING"
                                            else -> "NEW BUILD"
                                        },
                                        color = ReconAmber,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val release = when (val s = appUpdateState) {
                                is UpdateState.UpdateAvailable -> s.release
                                is UpdateState.ReadyToInstall -> s.release
                                is UpdateState.Installing -> s.release
                                else -> null
                            }

                            if (release != null) {
                                Text(
                                    text = release.releaseTitle,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Version: ${release.tagName} (${release.formattedSize}) • GitHub Official Release",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            when (val state = appUpdateState) {
                                is UpdateState.UpdateAvailable -> {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { appUpdateManager.downloadAndInstall(state.release) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ReconAmber),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = TacticalObsidian,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "UPDATE APP",
                                                color = TacticalObsidian,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { appUpdateManager.skipUpdate(state.release) },
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                                        ) {
                                            Text(
                                                text = "SKIP",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                is UpdateState.Downloading -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = state.progressPercent / 100f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = ReconAmber,
                                        trackColor = TacticalBorder
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Downloading APK: ${state.progressPercent}%",
                                        color = ReconAmber,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                is UpdateState.ReadyToInstall -> {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { appUpdateManager.installApk(state.apkFile) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = ReconGreen),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = TacticalObsidian,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "INSTALL APK NOW",
                                            color = TacticalObsidian,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = { appUpdateManager.openReleaseWebPage(state.release.htmlUrl) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = ReconCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "BROWSER APK FALLBACK",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Dismiss / Skip All Action
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                ) {
                    Text(
                        text = "PROCEED TO RADAR (SKIP FOR NOW)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
