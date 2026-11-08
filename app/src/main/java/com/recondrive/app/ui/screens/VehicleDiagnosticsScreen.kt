package com.recondrive.app.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recondrive.app.obd.DtcSeverity
import com.recondrive.app.obd.ObdConnectionStatus
import com.recondrive.app.obd.PsaDtcItem
import com.recondrive.app.obd.PsaLiveTelemetry
import com.recondrive.app.obd.PsaObdManager
import com.recondrive.app.ui.theme.*

/**
 * Citroën Grand C4 Picasso Mk1 (1.6 HDi DV6 • EGS6 / BMP6) Specialist Diagnostics & Telemetry Screen.
 */
@Composable
fun VehicleDiagnosticsScreen(
    obdManager: PsaObdManager,
    modifier: Modifier = Modifier
) {
    val connectionStatus by obdManager.connectionStatus.collectAsState()
    val statusMessage by obdManager.statusMessage.collectAsState()
    val scannedDtcs by obdManager.scannedDtcs.collectAsState()
    val liveTelemetry by obdManager.liveTelemetry.collectAsState()
    val isScanning by obdManager.isScanning.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: FAULTS, 1: TELEMETRY, 2: SUBSYSTEMS
    var selectedDtcForDetail by remember { mutableStateOf<PsaDtcItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalObsidian)
    ) {
        // 1. Vehicle & Profile Header
        VehicleHeaderCard()

        // 2. Hardware Adapter Connection Bar
        ObdConnectionHeader(
            obdManager = obdManager,
            connectionStatus = connectionStatus,
            statusMessage = statusMessage
        )

        // 3. Sub-Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTabButton(
                title = "FAULTS & DTCS (${scannedDtcs.size})",
                icon = Icons.Default.Warning,
                isSelected = activeSubTab == 0,
                color = if (scannedDtcs.isNotEmpty()) AlertRed else ReconCyan,
                modifier = Modifier.weight(1f),
                onClick = { activeSubTab = 0 }
            )

            SubTabButton(
                title = "LIVE TELEMETRY",
                icon = Icons.Default.Speed,
                isSelected = activeSubTab == 1,
                color = ReconCyan,
                modifier = Modifier.weight(1f),
                onClick = { activeSubTab = 1 }
            )

            SubTabButton(
                title = "C4 PICASSO HEALTH",
                icon = Icons.Default.Build,
                isSelected = activeSubTab == 2,
                color = TacticalAmber,
                modifier = Modifier.weight(1f),
                onClick = { activeSubTab = 2 }
            )
        }

        // 4. Tab Content Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            when (activeSubTab) {
                0 -> FaultsScannerTab(
                    scannedDtcs = scannedDtcs,
                    isScanning = isScanning,
                    connectionStatus = connectionStatus,
                    onRunScan = { obdManager.runDeepVehicleScan() },
                    onClearCodes = { showClearConfirmDialog = true },
                    onSelectDtc = { selectedDtcForDetail = it }
                )
                1 -> LiveTelemetryTab(
                    telemetry = liveTelemetry,
                    connectionStatus = connectionStatus
                )
                2 -> SpecialistSubsystemsTab(
                    telemetry = liveTelemetry
                )
            }
        }
    }

    // Detail Dialog for selected DTC
    selectedDtcForDetail?.let { dtc ->
        DtcDetailDialog(
            dtc = dtc,
            onDismiss = { selectedDtcForDetail = null }
        )
    }

    // Confirmation Dialog for Clearing Codes
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "CLEAR ALL VEHICLE FAULTS?",
                    color = AlertRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    text = "This will broadcast standard Mode 04 and UDS Service 0x14 clear commands across Engine (EDC16), EGS6 Gearbox, ESP 8.1, EPB Handbrake, and BSI.\n\nEnsure the vehicle ignition is ON (Position 2) with the engine OFF.",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        showClearConfirmDialog = false
                        obdManager.clearAllDtcs()
                    }
                ) {
                    Text(
                        text = "CONFIRM CLEAR",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    shape = RoundedCornerShape(4.dp),
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(
                        text = "CANCEL",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = TacticalSurface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun VehicleHeaderCard() {
    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CITROËN GRAND C4 PICASSO",
                        color = ReconCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = TacticalAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "MK1 • NOV 2010",
                            color = TacticalAmber,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "1.6 HDi DV6 Euro 4/5 • 6-Speed EGS6 / BMP6 • Bosch EDC16C34 • ESP 8.1 • BSI 2004",
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = ReconGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "40,752 PSA DTCs",
                    color = ReconGreen,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ObdConnectionHeader(
    obdManager: PsaObdManager,
    connectionStatus: ObdConnectionStatus,
    statusMessage: String
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(2) } // 0: BT, 1: WIFI, 2: SIM
    var pairedBtDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedBtDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showBtMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter
            pairedBtDevices = adapter?.bondedDevices?.toList() ?: emptyList()
            selectedBtDevice = pairedBtDevices.firstOrNull {
                it.name?.contains("OBD", ignoreCase = true) == true ||
                it.name?.contains("ELM", ignoreCase = true) == true ||
                it.name?.contains("vLinker", ignoreCase = true) == true
            } ?: pairedBtDevices.firstOrNull()
        } catch (_: Exception) {}
    }

    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mode Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionModeChip(
                    title = "BLUETOOTH",
                    isSelected = selectedMode == 0,
                    onClick = { selectedMode = 0 }
                )
                ConnectionModeChip(
                    title = "WI-FI TCP",
                    isSelected = selectedMode == 1,
                    onClick = { selectedMode = 1 }
                )
                ConnectionModeChip(
                    title = "C4 PICASSO SIM",
                    isSelected = selectedMode == 2,
                    onClick = { selectedMode = 2 }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Connect / Disconnect button
                val isConnected = connectionStatus == ObdConnectionStatus.CONNECTED || connectionStatus == ObdConnectionStatus.SCANNING
                if (isConnected) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        onClick = { obdManager.disconnect() }
                    ) {
                        Text(
                            text = "DISCONNECT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        enabled = connectionStatus != ObdConnectionStatus.CONNECTING && connectionStatus != ObdConnectionStatus.INITIALIZING_ELM,
                        onClick = {
                            when (selectedMode) {
                                0 -> selectedBtDevice?.let { obdManager.connectBluetooth(it) } ?: obdManager.connectSimulation()
                                1 -> obdManager.connectWifi("192.168.0.10", 35000)
                                2 -> obdManager.connectSimulation()
                            }
                        }
                    ) {
                        Text(
                            text = if (connectionStatus == ObdConnectionStatus.CONNECTING || connectionStatus == ObdConnectionStatus.INITIALIZING_ELM) "LINKING..." else "CONNECT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black
                        )
                    }
                }
            }

            // Connection Target & Status Message
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val statusDotColor = when (connectionStatus) {
                        ObdConnectionStatus.CONNECTED -> ReconGreen
                        ObdConnectionStatus.SCANNING -> ReconCyan
                        ObdConnectionStatus.CONNECTING, ObdConnectionStatus.INITIALIZING_ELM -> TacticalAmber
                        ObdConnectionStatus.ERROR -> AlertRed
                        ObdConnectionStatus.DISCONNECTED -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusDotColor)
                    )
                    Text(
                        text = statusMessage,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectedMode == 0 && pairedBtDevices.isNotEmpty()) {
                    Box {
                        Surface(
                            color = TacticalObsidian,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                            modifier = Modifier.clickable { showBtMenu = true }
                        ) {
                            Text(
                                text = selectedBtDevice?.name ?: "Select Device",
                                color = ReconCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showBtMenu,
                            onDismissRequest = { showBtMenu = false },
                            modifier = Modifier.background(TacticalSurface)
                        ) {
                            pairedBtDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${dev.name ?: "Unknown"} (${dev.address})",
                                            color = TextPrimary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    onClick = {
                                        selectedBtDevice = dev
                                        showBtMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionModeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ReconCyan.copy(alpha = 0.2f) else TacticalObsidian,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ReconCyan else TacticalBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            color = if (isSelected) ReconCyan else TextSecondary,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SubTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.18f) else TacticalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) color else TacticalBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) color else TextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = if (isSelected) color else TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

// ==============================================================================
// SUB-TAB 0: FAULTS SCANNER
// ==============================================================================

@Composable
private fun FaultsScannerTab(
    scannedDtcs: List<PsaDtcItem>,
    isScanning: Boolean,
    connectionStatus: ObdConnectionStatus,
    onRunScan: () -> Unit,
    onClearCodes: () -> Unit,
    onSelectDtc: (PsaDtcItem) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredDtcs = remember(scannedDtcs, selectedFilter) {
        when (selectedFilter) {
            "ENGINE" -> scannedDtcs.filter { it.system.contains("CMM", true) || it.ecuCanId == "7E8" }
            "GEARBOX" -> scannedDtcs.filter { it.system.contains("BVA", true) || it.system.contains("BVMP", true) || it.ecuCanId == "7E9" }
            "BRAKES" -> scannedDtcs.filter { it.system.contains("ABS", true) || it.system.contains("ESP", true) || it.ecuCanId == "768" }
            "HANDBRAKE" -> scannedDtcs.filter { it.system.contains("FSE", true) || it.system.contains("EPB", true) || it.ecuCanId == "79A" }
            "BODY" -> scannedDtcs.filter { it.system.contains("BSI", true) || it.ecuCanId == "7D2" }
            else -> scannedDtcs
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Scan & Clear Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                shape = RoundedCornerShape(4.dp),
                enabled = !isScanning && connectionStatus != ObdConnectionStatus.DISCONNECTED,
                modifier = Modifier.weight(1f),
                onClick = onRunScan
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "SCANNING CAN BUS..." else "RUN DEEP VEHICLE SCAN",
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }

            if (scannedDtcs.isNotEmpty()) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onClearCodes
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CLEAR FAULTS",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Module Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChipItem(name = "ALL (${scannedDtcs.size})", isSelected = selectedFilter == "ALL", onClick = { selectedFilter = "ALL" })
            FilterChipItem(name = "ENGINE (EDC16)", isSelected = selectedFilter == "ENGINE", onClick = { selectedFilter = "ENGINE" })
            FilterChipItem(name = "GEARBOX (EGS6)", isSelected = selectedFilter == "GEARBOX", onClick = { selectedFilter = "GEARBOX" })
            FilterChipItem(name = "BRAKES (ESP8.1)", isSelected = selectedFilter == "BRAKES", onClick = { selectedFilter = "BRAKES" })
            FilterChipItem(name = "HANDBRAKE (FSE)", isSelected = selectedFilter == "HANDBRAKE", onClick = { selectedFilter = "HANDBRAKE" })
            FilterChipItem(name = "BODY (BSI)", isSelected = selectedFilter == "BODY", onClick = { selectedFilter = "BODY" })
        }

        // DTC Results List
        if (filteredDtcs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TacticalSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ReconGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (scannedDtcs.isEmpty()) "NO FAULTS RECORDED • SYSTEM NOMINAL" else "NO FAULTS IN SELECTED FILTER",
                        color = ReconGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Tap 'RUN DEEP VEHICLE SCAN' to interrogate all C4 Picasso CAN nodes.",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDtcs) { dtc ->
                    DtcCard(dtc = dtc, onClick = { onSelectDtc(dtc) })
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ReconCyan.copy(alpha = 0.2f) else TacticalSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ReconCyan else TacticalBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = name,
            color = if (isSelected) ReconCyan else TextSecondary,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DtcCard(
    dtc: PsaDtcItem,
    onClick: () -> Unit
) {
    val severityColor = when (dtc.severity) {
        DtcSeverity.CRITICAL -> AlertRed
        DtcSeverity.WARNING -> TacticalAmber
        DtcSeverity.INFO -> ReconCyan
    }

    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header Row: Code, CAN ID, Severity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dtc.code,
                        color = severityColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = severityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "CAN 0x${dtc.ecuCanId} • ${dtc.system}",
                            color = severityColor,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Surface(
                    color = TacticalObsidian,
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
                ) {
                    Text(
                        text = "RAW: 0x${dtc.rawHex}",
                        color = TextSecondary,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Description
            Text(
                text = dtc.descEn,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // French OEM description if present
            if (dtc.descFr.isNotBlank()) {
                Text(
                    text = "FR: ${dtc.descFr}",
                    color = TextSecondary,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Specialist Mechanical Advisory Snippet
            dtc.mechanicalAdvice?.let { advice ->
                Surface(
                    color = TacticalObsidian.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = TacticalAmber,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = advice,
                            color = TacticalAmber,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================================
// SUB-TAB 1: LIVE TELEMETRY
// ==============================================================================

@Composable
private fun LiveTelemetryTab(
    telemetry: PsaLiveTelemetry,
    connectionStatus: ObdConnectionStatus
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Powertrain Gauges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryGaugeCard(
                title = "COMMON RAIL PRESSURE",
                value = "%.1f".format(telemetry.fuelRailPressureBar),
                unit = "BAR",
                subText = "Nominal: 250 - 1600 bar",
                color = ReconCyan,
                progress = (telemetry.fuelRailPressureBar / 1600.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )

            TelemetryGaugeCard(
                title = "TURBO BOOST",
                value = "%.2f".format(telemetry.boostPressureBar),
                unit = "BAR",
                subText = "Nominal: 0.98 - 2.25 bar",
                color = TacticalAmber,
                progress = (telemetry.boostPressureBar / 2.5).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryGaugeCard(
                title = "COOLANT TEMP (ECT)",
                value = "%.1f".format(telemetry.coolantTempC),
                unit = "°C",
                subText = "Thermostat: 83°C (Nom: 82-92°C)",
                color = if (telemetry.coolantTempC > 100) AlertRed else ReconGreen,
                progress = (telemetry.coolantTempC / 120.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )

            TelemetryGaugeCard(
                title = "BATTERY / CHARGE",
                value = "%.2f".format(telemetry.batteryVoltage),
                unit = "V",
                subText = "Crk Min: %.2fV (Drop <10.5V: %s)".format(telemetry.minCrankingVoltage, if (telemetry.isCrankingDropDetected) "ALERT" else "SAFE"),
                color = if (telemetry.isCrankingDropDetected) AlertRed else ReconCyan,
                progress = ((telemetry.batteryVoltage - 10.0) / 5.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryGaugeCard(
                title = "ENGINE RPM",
                value = "${telemetry.engineRpm}",
                unit = "RPM",
                subText = "Idle: 800 - 850 RPM",
                color = ReconCyan,
                progress = (telemetry.engineRpm / 4500.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )

            TelemetryGaugeCard(
                title = "MASS AIR FLOW (MAF)",
                value = "%.1f".format(telemetry.mafGramsSec),
                unit = "G/S",
                subText = "Idle: 12 - 18 g/s (Boost >75 g/s)",
                color = ReconGreen,
                progress = (telemetry.mafGramsSec / 100.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TelemetryGaugeCard(
    title: String,
    value: String,
    unit: String,
    subText: String,
    color: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = unit,
                    color = color.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                color = color,
                trackColor = TacticalObsidian,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Text(
                text = subText,
                color = TextSecondary,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==============================================================================
// SUB-TAB 2: C4 PICASSO SPECIALIST SUBSYSTEMS
// ==============================================================================

@Composable
private fun SpecialistSubsystemsTab(
    telemetry: PsaLiveTelemetry
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. EGS6 Piloted Gearbox
        SpecialistCard(
            title = "1. EGS6 / BMP6 PILOTED GEARBOX (MAGNETI MARELLI CFC300P)",
            icon = Icons.Default.Settings,
            badge = "CAN 0x7E9 • HYDRAULIC PACK",
            badgeColor = TacticalAmber
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubsystemMetric(
                    label = "ACCUMULATOR PRESSURE",
                    value = "%.1f BAR".format(telemetry.egs6PressureBar),
                    status = if (telemetry.egs6PressureBar >= 40.0) "HEALTHY" else "LOW (P17A5 RISK)",
                    isOk = telemetry.egs6PressureBar >= 40.0,
                    modifier = Modifier.weight(1f)
                )
                SubsystemMetric(
                    label = "CLUTCH STROKE / WEAR",
                    value = "%.1f MM (%d%%)".format(telemetry.egs6ClutchPosMm, telemetry.egs6ClutchWearPct),
                    status = if (telemetry.egs6ClutchPosMm < 16.5) "NORMAL" else "NEAR LIMIT (~18MM)",
                    isOk = telemetry.egs6ClutchPosMm < 16.5,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "• Accumulator nitrogen sphere maintains 40 - 50 bar. When degraded, pump primes every 5-10s.\n• Clutch kiss-point relearn eliminates 1st gear shuddering and gear hunting.",
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 2. DPF (FAP) & Eolys Additive
        SpecialistCard(
            title = "2. DIESEL PARTICULATE FILTER (FAP) & EOLYS ADDITIVE",
            icon = Icons.Default.LocalGasStation,
            badge = "CAN 0x77A • ADDGO UNIT",
            badgeColor = ReconCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubsystemMetric(
                    label = "DPF SOOT LOAD",
                    value = "%.1f G".format(telemetry.dpfSootGrams),
                    status = if (telemetry.dpfSootGrams < 25.0) "NOMINAL" else "REGEN REQUIRED",
                    isOk = telemetry.dpfSootGrams < 25.0,
                    modifier = Modifier.weight(1f)
                )
                SubsystemMetric(
                    label = "EXHAUST DIFF PRESSURE",
                    value = "%.1f MBAR".format(telemetry.dpfDiffMbar),
                    status = if (telemetry.dpfDiffMbar < 20.0) "LOW RESTRICTION" else "HIGH BACKPRESSURE",
                    isOk = telemetry.dpfDiffMbar < 20.0,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "• Uses Eolys 176 / PowerFlex fluid bladder under rear chassis. Capacity: 1100 ml.\n• Dual-ECU synchronization required on pouch replacement (ADDGO 0x772 & EDC16 0x7E0).",
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 3. Starter Battery Cranking Voltage Drop
        val crankOk = telemetry.minCrankingVoltage >= 10.5
        SpecialistCard(
            title = "3. STARTER CRANKING VOLTAGE (FALSE ABS/HANDBRAKE FILTER)",
            icon = Icons.Default.BatteryChargingFull,
            badge = if (crankOk) "SAFE (>10.5V)" else "VOLTAGE DROP DETECTED",
            badgeColor = if (crankOk) ReconGreen else AlertRed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubsystemMetric(
                    label = "LOWEST CRANKING VOLT",
                    value = "%.2f V".format(telemetry.minCrankingVoltage),
                    status = if (crankOk) "NOMINAL VOLTAGE" else "DROPPED BELOW 10.5V",
                    isOk = crankOk,
                    modifier = Modifier.weight(1f)
                )
                SubsystemMetric(
                    label = "ABS / FSE FALSE FAULTS",
                    value = if (crankOk) "UNLIKELY" else "HIGH PROBABILITY",
                    status = if (crankOk) "BATTERY HEALTHY" else "EXPLAINS C1385 / C155A",
                    isOk = crankOk,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "• On C4 Picasso, starting with a battery dropping below 10.5V causes Bosch ESP 8.1 and FSE handbrake microcontrollers to reboot and store false DTCs (C1385, C155A). Always check battery before replacing calipers.",
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SpecialistCard(
    title: String,
    icon: ImageVector,
    badge: String,
    badgeColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun SubsystemMetric(
    label: String,
    value: String,
    status: String,
    isOk: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TacticalObsidian,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOk) TacticalBorder else AlertRed.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = if (isOk) Color.White else AlertRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = status,
                color = if (isOk) ReconGreen else AlertRed,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ==============================================================================
// DTC DETAIL DIALOG
// ==============================================================================

@Composable
private fun DtcDetailDialog(
    dtc: PsaDtcItem,
    onDismiss: () -> Unit
) {
    val severityColor = when (dtc.severity) {
        DtcSeverity.CRITICAL -> AlertRed
        DtcSeverity.WARNING -> TacticalAmber
        DtcSeverity.INFO -> ReconCyan
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, severityColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dtc.code,
                            color = severityColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "CAN 0x${dtc.ecuCanId} • ${dtc.systemName}",
                            color = TextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Divider(color = TacticalBorder)

                // Descriptions
                DetailRow(label = "DESCRIPTION (EN)", value = dtc.descEn)
                if (dtc.descFr.isNotBlank()) {
                    DetailRow(label = "ORIGINAL OEM (FR)", value = dtc.descFr)
                }
                DetailRow(label = "RAW CAN HEX", value = "0x${dtc.rawHex}")
                DetailRow(label = "ECU IDENTIFIER", value = dtc.ecu)
                DetailRow(label = "SEVERITY LEVEL", value = dtc.severity.name)

                // Mechanical Advisory
                dtc.mechanicalAdvice?.let { advice ->
                    Surface(
                        color = TacticalObsidian,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = TacticalAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "CITROËN C4 PICASSO MECHANICAL ADVISORY",
                                    color = TacticalAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = advice,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(
                        text = "CLOSE ADVISORY",
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
