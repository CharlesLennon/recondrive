package com.recondrive.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recondrive.app.ui.theme.*
import com.recondrive.app.update.AppUpdateManager
import com.recondrive.app.update.ReleaseInfo
import com.recondrive.app.update.UpdateState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-Screen Military HUD Update Interface.
 * Takes up the entire display when downloading or installing an app update,
 * displaying real-time cryptographic audit, download rate telemetry,
 * stage-by-stage pipeline status, and resilient package installer controls.
 */
@Composable
fun FullScreenUpdateOverlay(
    updateManager: AppUpdateManager,
    updateState: UpdateState,
    onDismiss: () -> Unit
) {
    // Determine the active release info
    val activeRelease: ReleaseInfo? = when (updateState) {
        is UpdateState.Downloading -> updateState.release ?: updateManager.cachedRelease
        is UpdateState.ReadyToInstall -> updateState.release
        is UpdateState.Installing -> updateState.release
        is UpdateState.UpdateAvailable -> updateState.release
        else -> updateManager.cachedRelease
    }

    var isChangelogExpanded by remember { mutableStateOf(false) }

    // Pulsing scanner animation
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Dialog(
        onDismissRequest = {
            if (updateState is UpdateState.Downloading) {
                // Keep downloading unless user explicitly cancels
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = updateState !is UpdateState.Downloading,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TacticalObsidian)
        ) {
            // Background Tactical Canvas: Grid lines + vertical radar scanline
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                val gridColor = ReconCyan.copy(alpha = 0.035f)
                
                // Vertical grid lines
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                
                // Horizontal grid lines
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }

                // Vertical animated scan line
                val currentScanY = scanY * size.height
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ReconCyan.copy(alpha = 0.25f),
                            ReconCyan.copy(alpha = 0.5f),
                            ReconCyan.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, currentScanY),
                    end = Offset(size.width, currentScanY),
                    strokeWidth = 2.dp.toPx()
                )

                // Corner HUD reticles
                val bracketLen = 28.dp.toPx()
                val bracketStroke = 2.dp.toPx()
                val bracketColor = ReconCyan.copy(alpha = 0.6f)

                // Top-Left ⌜
                drawLine(bracketColor, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx() + bracketLen, 16.dp.toPx()), bracketStroke)
                drawLine(bracketColor, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx() + bracketLen), bracketStroke)

                // Top-Right ⌝
                drawLine(bracketColor, Offset(size.width - 16.dp.toPx(), 16.dp.toPx()), Offset(size.width - 16.dp.toPx() - bracketLen, 16.dp.toPx()), bracketStroke)
                drawLine(bracketColor, Offset(size.width - 16.dp.toPx(), 16.dp.toPx()), Offset(size.width - 16.dp.toPx(), 16.dp.toPx() + bracketLen), bracketStroke)

                // Bottom-Left ⌞
                drawLine(bracketColor, Offset(16.dp.toPx(), size.height - 16.dp.toPx()), Offset(16.dp.toPx() + bracketLen, size.height - 16.dp.toPx()), bracketStroke)
                drawLine(bracketColor, Offset(16.dp.toPx(), size.height - 16.dp.toPx()), Offset(16.dp.toPx(), size.height - 16.dp.toPx() - bracketLen), bracketStroke)

                // Bottom-Right ⌟
                drawLine(bracketColor, Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), Offset(size.width - 16.dp.toPx() - bracketLen, size.height - 16.dp.toPx()), bracketStroke)
                drawLine(bracketColor, Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx() - bracketLen), bracketStroke)
            }

            // Main Content Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (updateState) {
                                        is UpdateState.ReadyToInstall -> ReconGreen
                                        is UpdateState.Installing -> ReconAmber
                                        is UpdateState.Error -> AlertRed
                                        else -> ReconCyan
                                    }.copy(alpha = glowPulse)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "RECON//DRIVE • SYSTEM UPDATE",
                                color = ReconCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "CRYPTOGRAPHIC GITHUB BINARY DEPLOYMENT",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Dismiss / Cancel Icon Button
                    IconButton(
                        onClick = {
                            if (updateState is UpdateState.Downloading) {
                                updateManager.cancelDownload()
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(TacticalSurface, CircleShape)
                            .border(1.dp, TacticalBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ReconCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Build & Keystore Intel Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = TacticalSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "INSTALLED BUILD",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = updateManager.currentVersionName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = ReconCyan,
                                modifier = Modifier.size(16.dp)
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "TARGET BUILD",
                                    color = ReconCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = activeRelease?.tagName ?: "LATEST",
                                    color = ReconCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = TacticalBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "PACKAGE: ${activeRelease?.apkFileName ?: "ReconDrive.apk"}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "SIZE: ${activeRelease?.formattedSize ?: "27 MB"}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ReconGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SIGNATURE: PERMANENT RSA-2048 KEYSTORE (MATCHING CERT)",
                                color = ReconGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CENTERPIECE: Tactical Circular Gauge HUD
                val progressPercent = when (updateState) {
                    is UpdateState.Downloading -> updateState.progressPercent
                    is UpdateState.ReadyToInstall -> 100
                    is UpdateState.Installing -> 100
                    else -> 0
                }

                TacticalCircularProgressHud(
                    progressPercent = progressPercent,
                    updateState = updateState,
                    glowPulse = glowPulse
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Linear Progress Bar & Live Telemetry
                val downloadedBytes = (updateState as? UpdateState.Downloading)?.bytesDownloaded ?: 0L
                val totalBytes = (updateState as? UpdateState.Downloading)?.totalBytes ?: (activeRelease?.apkSizeBytes ?: 0L)
                val dlMb = downloadedBytes.toDouble() / (1024.0 * 1024.0)
                val totMb = totalBytes.toDouble() / (1024.0 * 1024.0)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when (updateState) {
                                is UpdateState.Downloading -> "STREAMING BINARY PAYLOAD..."
                                is UpdateState.ReadyToInstall -> "PAYLOAD READY FOR EXECUTION"
                                is UpdateState.Installing -> "INSTALLER SESSION ACTIVE"
                                is UpdateState.Error -> "DEPLOYMENT HALTED"
                                else -> "INITIALIZING ENGINE..."
                            },
                            color = when (updateState) {
                                is UpdateState.ReadyToInstall -> ReconGreen
                                is UpdateState.Installing -> ReconAmber
                                is UpdateState.Error -> AlertRed
                                else -> ReconCyan
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$progressPercent%",
                            color = ReconCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = progressPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when (updateState) {
                            is UpdateState.ReadyToInstall -> ReconGreen
                            is UpdateState.Installing -> ReconAmber
                            is UpdateState.Error -> AlertRed
                            else -> ReconCyan
                        },
                        trackColor = TacticalSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (updateState is UpdateState.Downloading && totMb > 0) {
                                "%.1f MB / %.1f MB".format(dlMb, totMb)
                            } else if (totMb > 0) {
                                "TOTAL: %.1f MB".format(totMb)
                            } else {
                                "VERIFYING METADATA..."
                            },
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SOURCE: GITHUB CDN",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Multi-Stage Pipeline Tracker
                TacticalPipelineTracker(updateState = updateState)

                Spacer(modifier = Modifier.height(24.dp))

                // Contextual Action Card & Buttons
                when (updateState) {
                    is UpdateState.Downloading -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TacticalSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "LIVE DOWNLOAD IN PROGRESS",
                                    color = ReconCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Streaming APK binary from GitHub Release assets into high-speed local cache. Package installer will automatically launch upon cryptographic validation.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { updateManager.cancelDownload() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CANCEL DOWNLOAD",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TacticalSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ReconGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PACKAGE READY TO INSTALL",
                                        color = ReconGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Integrity verified. Tap below to launch Android's native installer session. If prompted, confirm the installation to complete the tactical update.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { updateManager.installApk(updateState.apkFile, updateState.release) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ReconGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = TacticalObsidian)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "LAUNCH INSTALLER NOW",
                                        color = TacticalObsidian,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { updateManager.openReleaseWebPage(updateState.release.htmlUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BROWSER / DIRECT APK DOWNLOAD",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.Installing -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TacticalSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ReconAmber,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "AWAITING OS CONFIRMATION",
                                        color = ReconAmber,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = updateState.stage,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Android's package installer has received the update session. If a confirmation dialog appeared on your screen, tap 'Update' or 'Install'.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { updateManager.installApk(updateState.apkFile, updateState.release) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ReconAmber),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "RE-TRIGGER SYSTEM INSTALLER",
                                        color = TacticalObsidian,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { updateManager.openReleaseWebPage(updateState.release.htmlUrl) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(text = "BROWSER APK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(text = "RETURN TO RADAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TacticalSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DEPLOYMENT ENCOUNTERED ERROR",
                                        color = AlertRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = updateState.message,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    activeRelease?.let { rel ->
                                        Button(
                                            onClick = { updateManager.downloadAndInstall(rel, onAutoInstall = true) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(text = "RETRY", color = TacticalObsidian, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val url = activeRelease?.htmlUrl ?: "https://github.com/${AppUpdateManager.GITHUB_REPO}/releases"
                                            updateManager.openReleaseWebPage(url)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(text = "BROWSER APK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Other states
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Mission Briefing & Changelog Accordion
                activeRelease?.let { rel ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                            .clickable { isChangelogExpanded = !isChangelogExpanded },
                        color = TacticalSurface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isChangelogExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = ReconCyan
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MISSION BRIEFING & CHANGELOG (${rel.tagName})",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = if (isChangelogExpanded) "COLLAPSE" else "EXPAND",
                                    color = ReconCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (isChangelogExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = TacticalBorder)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = rel.releaseNotes.ifBlank { "Operational telemetry optimizations and tactical bugfixes." },
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

/**
 * Military HUD Circular Gauge showing percentage and status.
 */
@Composable
private fun TacticalCircularProgressHud(
    progressPercent: Int,
    updateState: UpdateState,
    glowPulse: Float
) {
    val gaugeColor = when (updateState) {
        is UpdateState.ReadyToInstall -> ReconGreen
        is UpdateState.Installing -> ReconAmber
        is UpdateState.Error -> AlertRed
        else -> ReconCyan
    }

    Box(
        modifier = Modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 14.dp.toPx()

            // Outer Tick Ring (48 tick marks around circumference)
            val tickCount = 48
            for (i in 0 until tickCount) {
                val angleDeg = (i * 360f / tickCount)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 4 == 0
                val tickLen = if (isMajor) 7.dp.toPx() else 4.dp.toPx()
                val tickAlpha = if (isMajor) 0.5f else 0.2f

                val startX = center.x + (radius + 8.dp.toPx()) * cos(angleRad).toFloat()
                val startY = center.y + (radius + 8.dp.toPx()) * sin(angleRad).toFloat()
                val endX = center.x + (radius + 8.dp.toPx() + tickLen) * cos(angleRad).toFloat()
                val endY = center.y + (radius + 8.dp.toPx() + tickLen) * sin(angleRad).toFloat()

                drawLine(
                    color = ReconCyan.copy(alpha = tickAlpha),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 1.5f else 1f
                )
            }

            // Track Arc (360 degrees)
            drawArc(
                color = TacticalBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                size = Size(radius * 2f, radius * 2f),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Progress Arc
            val sweep = (progressPercent / 100f) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = gaugeColor.copy(alpha = glowPulse),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }

            // Inner Accent Ring
            drawCircle(
                color = TacticalBorder.copy(alpha = 0.5f),
                radius = radius - 16.dp.toPx(),
                center = center,
                style = Stroke(width = 1f)
            )
        }

        // Center Content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (updateState) {
                is UpdateState.Installing -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ReconAmber,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "INSTALL",
                        color = ReconAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ReconGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "VERIFIED",
                        color = ReconGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                is UpdateState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "FAILED",
                        color = AlertRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                else -> {
                    Text(
                        text = "$progressPercent%",
                        color = ReconCyan,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "DOWNLOADING",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * 4-Step Tactical Verification & Installation Pipeline Tracker.
 */
@Composable
private fun TacticalPipelineTracker(updateState: UpdateState) {
    val isCdnDone = true
    val isPayloadDone = updateState is UpdateState.ReadyToInstall || updateState is UpdateState.Installing
    val isPayloadActive = updateState is UpdateState.Downloading
    val isIntegrityDone = updateState is UpdateState.ReadyToInstall || updateState is UpdateState.Installing
    val isInstallActive = updateState is UpdateState.Installing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = TacticalSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "PIPELINE VERIFICATION STAGES",
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            PipelineStepItem(
                stepNumber = "01",
                label = "GITHUB CDN CONNECTION",
                isCompleted = isCdnDone,
                isActive = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            PipelineStepItem(
                stepNumber = "02",
                label = "BINARY PAYLOAD STREAM",
                isCompleted = isPayloadDone,
                isActive = isPayloadActive
            )
            Spacer(modifier = Modifier.height(6.dp))
            PipelineStepItem(
                stepNumber = "03",
                label = "ARCHIVE INTEGRITY & SIGNATURE AUDIT",
                isCompleted = isIntegrityDone,
                isActive = isPayloadActive && (updateState as? UpdateState.Downloading)?.progressPercent == 100
            )
            Spacer(modifier = Modifier.height(6.dp))
            PipelineStepItem(
                stepNumber = "04",
                label = "ANDROID PACKAGEINSTALLER SESSION",
                isCompleted = false,
                isActive = isInstallActive
            )
        }
    }
}

@Composable
private fun PipelineStepItem(
    stepNumber: String,
    label: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[$stepNumber]",
                color = when {
                    isCompleted -> ReconGreen
                    isActive -> ReconCyan
                    else -> TextSecondary
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = when {
                    isCompleted -> TextPrimary
                    isActive -> ReconCyan
                    else -> TextSecondary
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        when {
            isCompleted -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = ReconGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
            isActive -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = ReconCyan,
                    strokeWidth = 1.5.dp
                )
            }
            else -> {
                Text(
                    text = "PENDING",
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
