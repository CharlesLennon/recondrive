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
import com.recondrive.app.ui.theme.*
import com.recondrive.app.update.AppUpdateManager
import com.recondrive.app.update.ReleaseInfo
import com.recondrive.app.update.UpdateState

@Composable
fun UpdateDialog(
    updateManager: AppUpdateManager,
    onDismiss: () -> Unit
) {
    val updateState by updateManager.updateState.collectAsState()
    val scrollState = rememberScrollState()

    // Automatically dismiss dialog when downloading or installing so FullScreenUpdateOverlay takes over
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Downloading ||
            updateState is UpdateState.ReadyToInstall ||
            updateState is UpdateState.Installing) {
            onDismiss()
        }
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
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SYSTEM UPDATER",
                            color = ReconCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "GitHub Releases Channel",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
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

                Spacer(modifier = Modifier.height(14.dp))

                // Current Installed Version Card
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

                        Surface(
                            color = TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan)
                        ) {
                            Text(
                                text = "GITHUB API",
                                color = ReconCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic State View
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        IdleStateView(
                            onCheckClick = { updateManager.checkForUpdates(isManual = true) }
                        )
                    }

                    is UpdateState.Checking -> {
                        CheckingStateView()
                    }

                    is UpdateState.UpdateAvailable -> {
                        UpdateAvailableView(
                            release = state.release,
                            onDownloadClick = {
                                updateManager.downloadAndInstall(state.release, onAutoInstall = true)
                            },
                            onViewGitHubClick = {
                                updateManager.openReleaseWebPage(state.release.htmlUrl)
                            }
                        )
                    }

                    is UpdateState.Downloading -> {
                        DownloadingStateView(state = state)
                    }

                    is UpdateState.ReadyToInstall -> {
                        ReadyToInstallView(
                            apkFile = state.apkFile,
                            release = state.release,
                            onInstallClick = {
                                updateManager.installApk(state.apkFile)
                            },
                            onBrowserInstallClick = {
                                updateManager.openReleaseWebPage(state.release.htmlUrl)
                            }
                        )
                    }

                    is UpdateState.Installing -> {
                        // Handled by FullScreenUpdateOverlay
                    }

                    is UpdateState.UpToDate -> {
                        UpToDateView(
                            state = state,
                            onRecheckClick = { updateManager.checkForUpdates(isManual = true) },
                            onReDownloadClick = state.latestRelease?.let { rel ->
                                { updateManager.downloadAndInstall(rel, onAutoInstall = true) }
                            },
                            onViewGitHubClick = state.latestRelease?.let { rel ->
                                { updateManager.openReleaseWebPage(rel.htmlUrl) }
                            }
                        )
                    }

                    is UpdateState.Error -> {
                        ErrorStateView(
                            message = state.message,
                            onRetryClick = { updateManager.checkForUpdates(isManual = true) },
                            onViewReleasesClick = {
                                updateManager.openReleaseWebPage("https://github.com/${AppUpdateManager.GITHUB_REPO}/releases")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleStateView(onCheckClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Check GitHub for new tactical updates and bugfixes.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onCheckClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TacticalObsidian)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CHECK FOR UPDATES",
                color = TacticalObsidian,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CheckingStateView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = ReconCyan,
            trackColor = TacticalSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "SCANNING GITHUB RELEASES...",
            color = ReconCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Auditing release tags and APK binaries",
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun UpdateAvailableView(
    release: ReleaseInfo,
    onDownloadClick: () -> Unit,
    onViewGitHubClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEW BUILD AVAILABLE",
                        color = ReconAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = release.tagName,
                        color = ReconCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = release.releaseTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Asset: ${release.apkFileName} (${release.formattedSize})",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Release Notes Card
        Text(
            text = "RELEASE INTEL & CHANGELOG",
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp),
            color = TacticalSurface,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = release.releaseNotes,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons
        Button(
            onClick = onDownloadClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TacticalObsidian)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "DOWNLOAD & INSTALL UPDATE",
                color = TacticalObsidian,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onViewGitHubClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "VIEW ON GITHUB",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DownloadingStateView(state: UpdateState.Downloading) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DOWNLOADING APK...",
                color = ReconCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${state.progressPercent}%",
                color = ReconCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = state.progressPercent / 100f,
            modifier = Modifier.fillMaxWidth(),
            color = ReconCyan,
            trackColor = TacticalSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        val dlMb = state.bytesDownloaded.toDouble() / (1024.0 * 1024.0)
        val totMb = state.totalBytes.toDouble() / (1024.0 * 1024.0)
        Text(
            text = "%.1f MB / %.1f MB • %s".format(dlMb, totMb, state.fileName),
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ReadyToInstallView(
    apkFile: java.io.File,
    release: ReleaseInfo,
    onInstallClick: () -> Unit,
    onBrowserInstallClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ReconGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PACKAGE READY TO INSTALL",
                        color = ReconGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${release.apkFileName} (${release.formattedSize}) verified and ready.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onInstallClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ReconGreen),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, tint = TacticalObsidian)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LAUNCH INSTALLER NOW",
                color = TacticalObsidian,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBrowserInstallClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "BROWSER APK FALLBACK",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UpToDateView(
    state: UpdateState.UpToDate,
    onRecheckClick: () -> Unit,
    onReDownloadClick: (() -> Unit)?,
    onViewGitHubClick: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ReconGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "RECONDRIVE IS UP TO DATE",
                        color = ReconGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Current build matches latest release (${state.latestRelease?.tagName ?: state.currentVersion})",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRecheckClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "RE-CHECK", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            if (onReDownloadClick != null) {
                Button(
                    onClick = onReDownloadClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = ReconCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "RE-DOWNLOAD", color = ReconCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (onViewGitHubClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onViewGitHubClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "VIEW RELEASES ON GITHUB", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ErrorStateView(
    message: String,
    onRetryClick: () -> Unit,
    onViewReleasesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ReconAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UPDATE CHECK FAILED",
                        color = ReconAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetryClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TacticalObsidian)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "RETRY", color = TacticalObsidian, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onViewReleasesClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = "BROWSER", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
