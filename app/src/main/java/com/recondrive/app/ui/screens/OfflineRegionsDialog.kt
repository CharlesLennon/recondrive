package com.recondrive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.recondrive.app.map.OfflineTileRepository
import com.recondrive.app.map.RegionDownloadState
import com.recondrive.app.map.UkRegionPack
import com.recondrive.app.ui.theme.*

@Composable
fun OfflineRegionsDialog(
    repository: OfflineTileRepository,
    onDismiss: () -> Unit
) {
    val downloadState by repository.downloadState.collectAsState()
    var storageBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(downloadState) {
        storageBytes = repository.getStorageUsageBytes()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(12.dp)),
            color = TacticalObsidian
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OFFLINE OSM DATA",
                            color = ReconCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "United Kingdom Vector Tile Extracts",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Storage Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LOCAL OFFLINE STORAGE",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            val mb = storageBytes.toDouble() / (1024.0 * 1024.0)
                            Text(
                                text = "%.1f MB / 32 GB Free".format(mb),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            color = TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen)
                        ) {
                            Text(
                                text = "OFFLINE READY",
                                color = ReconGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of UK Region Packs
                Text(
                    text = "AVAILABLE UK PACKS",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(repository.UK_REGIONS) { region ->
                        RegionPackItem(
                            region = region,
                            repository = repository,
                            downloadState = downloadState
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                ) {
                    Text(text = "DONE", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun RegionPackItem(
    region: UkRegionPack,
    repository: OfflineTileRepository,
    downloadState: RegionDownloadState
) {
    val isDownloaded = remember(downloadState) { repository.isRegionDownloaded(region) }
    val isCurrentlyDownloading = downloadState is RegionDownloadState.Downloading && downloadState.regionId == region.id

    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDownloaded) ReconGreen else TacticalBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.title,
                        color = if (isDownloaded) ReconGreen else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = region.description,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = region.formattedSize,
                    color = ReconCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action / Progress Row
            if (isCurrentlyDownloading) {
                val pct = (downloadState as RegionDownloadState.Downloading).progressPercent
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = ReconCyan,
                        trackColor = TacticalObsidian
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Extracting OSM Vector Linework: $pct%",
                        color = ReconCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDownloaded) "STATUS: INSTALLED" else "STATUS: ONLINE STREAMING",
                        color = if (isDownloaded) ReconGreen else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (isDownloaded) {
                        OutlinedButton(
                            onClick = { repository.deleteRegion(region) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(text = "REMOVE", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        Button(
                            onClick = { repository.downloadRegion(region) },
                            colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "DOWNLOAD", color = TacticalObsidian, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
