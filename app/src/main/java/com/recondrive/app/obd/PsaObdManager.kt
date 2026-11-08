package com.recondrive.app.obd

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Status of the ELM327 OBD-II connection.
 */
enum class ObdConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING_ELM,
    CONNECTED,
    SCANNING,
    ERROR
}

/**
 * Real-time telemetry snapshot for Citroën Grand C4 Picasso 1.6 HDi DV6 & EGS6.
 */
data class PsaLiveTelemetry(
    val engineRpm: Int = 0,
    val speedKmh: Int = 0,
    val coolantTempC: Double = 0.0,
    val fuelRailPressureBar: Double = 0.0,
    val boostPressureBar: Double = 0.0,
    val batteryVoltage: Double = 12.6,
    val minCrankingVoltage: Double = 12.6,
    val mafGramsSec: Double = 0.0,
    val fuelTankPercent: Int = 0,
    val egs6PressureBar: Double = 48.0,
    val egs6ClutchPosMm: Double = 14.8,
    val egs6ClutchWearPct: Int = 60,
    val dpfSootGrams: Double = 18.5,
    val dpfDiffMbar: Double = 8.0,
    val egs6Gear: String = "N",
    val isCrankingDropDetected: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

/**
 * Comprehensive PSA OBD-II & ELM327 Diagnostic Manager.
 * Engineered for Citroën Grand C4 Picasso Mk1 (November 2010, 1.6 HDi DV6, EGS6 / BMP6).
 */
class PsaObdManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val dtcDatabase = PsaDtcDatabase(context)

    private var activeConnection: Elm327Connection? = null
    private var telemetryJob: Job? = null

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("OBD-II Interface Idle (Not Connected)")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _scannedDtcs = MutableStateFlow<List<PsaDtcItem>>(emptyList())
    val scannedDtcs: StateFlow<List<PsaDtcItem>> = _scannedDtcs.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(PsaLiveTelemetry())
    val liveTelemetry: StateFlow<PsaLiveTelemetry> = _liveTelemetry.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var lowestVoltageObserved = 12.6

    fun connectBluetooth(device: BluetoothDevice) {
        connectInternal(BluetoothElm327Connection(device))
    }

    fun connectWifi(host: String = "192.168.0.10", port: Int = 35000) {
        connectInternal(WifiElm327Connection(host, port))
    }

    fun connectSimulation() {
        connectInternal(SimulatedPicassoConnection())
    }

    private fun connectInternal(connection: Elm327Connection) {
        scope.launch(Dispatchers.IO) {
            disconnectInternal()
            _connectionStatus.value = ObdConnectionStatus.CONNECTING
            _statusMessage.value = "Connecting to ${connection.connectionType}..."

            try {
                connection.connect()
                activeConnection = connection

                _connectionStatus.value = ObdConnectionStatus.INITIALIZING_ELM
                _statusMessage.value = "Initializing ELM327 on PSA CAN bus (500 kbps)..."
                initElm327(connection)

                _connectionStatus.value = ObdConnectionStatus.CONNECTED
                _statusMessage.value = "Connected: ${connection.connectionType} (Ready)"

                // Start continuous live telemetry
                startTelemetryPolling()
            } catch (e: Exception) {
                Timber.e(e, "ELM327 connection failed")
                _connectionStatus.value = ObdConnectionStatus.ERROR
                _statusMessage.value = "Connection error: ${e.message ?: "Timeout"}"
                disconnectInternal()
            }
        }
    }

    private suspend fun initElm327(conn: Elm327Connection) {
        val setupCommands = listOf(
            "ATZ",   // Reset ELM327
            "ATE0",  // Echo off
            "ATL0",  // Linefeeds off
            "ATS0",  // Spaces off
            "ATH1",  // Headers on (Crucial for CAN ID filtering: 7E8, 7E9, 768, 79A)
            "ATSP6"  // Protocol ISO 15765-4 CAN 11-bit ID, 500 kbaud (PSA standard)
        )
        for (cmd in setupCommands) {
            val resp = conn.sendCommand(cmd)
            Timber.d("ELM Init %s -> %s", cmd, resp.joinToString("; "))
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            disconnectInternal()
            _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
            _statusMessage.value = "Disconnected"
        }
    }

    private suspend fun disconnectInternal() {
        telemetryJob?.cancel()
        telemetryJob = null
        try {
            activeConnection?.disconnect()
        } catch (e: Exception) {
            Timber.w(e, "Error disconnecting")
        } finally {
            activeConnection = null
        }
    }

    /**
     * Executes a deep multi-module diagnostic scan across all Citroën C4 Picasso ECUs:
     * - Engine Bosch EDC16C34 / EDC17C10 (0x7E0 / 0x7E8)
     * - EGS6 Magneti Marelli CFC300P Gearbox (0x7E1 / 0x7E9)
     * - Bosch ESP 8.1 Stability & ABS (0x760 / 0x768)
     * - Electric Parking Brake FSE (0x792 / 0x79A)
     * - Built-in Systems Interface BSI 2004 (0x752 / 0x7D2)
     */
    fun runDeepVehicleScan() {
        val conn = activeConnection ?: run {
            _statusMessage.value = "Cannot scan: No active ELM327 connection"
            return
        }

        if (_isScanning.value) return

        scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val prevStatus = _connectionStatus.value
            _connectionStatus.value = ObdConnectionStatus.SCANNING
            _statusMessage.value = "Performing deep multi-ECU scan across PSA CAN bus..."

            val allRawLines = mutableListOf<String>()

            try {
                // 1. Broadcast Powertrain (Engine + Gearbox)
                _statusMessage.value = "Scanning Powertrain (Engine + EGS6)..."
                conn.sendCommand("ATSH 7DF")
                allRawLines.addAll(conn.sendCommand("03")) // Confirmed DTCs
                allRawLines.addAll(conn.sendCommand("07")) // Pending DTCs

                // 2. Directed Engine ECU (Bosch EDC16C34)
                _statusMessage.value = "Interrogating Engine ECU (Bosch EDC16)..."
                conn.sendCommand("ATSH 7E0")
                allRawLines.addAll(conn.sendCommand("03"))
                allRawLines.addAll(conn.sendCommand("19 02 FF")) // UDS DTC by status mask

                // 3. Directed EGS6 Gearbox ECU (Magneti Marelli CFC300P)
                _statusMessage.value = "Interrogating EGS6 Robotized Transmission..."
                conn.sendCommand("ATSH 7E1")
                allRawLines.addAll(conn.sendCommand("03"))
                allRawLines.addAll(conn.sendCommand("19 02 FF"))

                // 4. Directed Dynamic Stability (Bosch ESP 8.1)
                _statusMessage.value = "Interrogating ABS / ESP 8.1..."
                conn.sendCommand("ATSH 760")
                allRawLines.addAll(conn.sendCommand("03"))
                allRawLines.addAll(conn.sendCommand("19 02 FF"))

                // 5. Directed Electric Parking Brake (FSE / EPB)
                _statusMessage.value = "Interrogating Electric Handbrake (FSE)..."
                conn.sendCommand("ATSH 792")
                allRawLines.addAll(conn.sendCommand("03"))

                // 6. Directed BSI Body Interface
                _statusMessage.value = "Interrogating BSI Central Gateway..."
                conn.sendCommand("ATSH 752")
                allRawLines.addAll(conn.sendCommand("03"))

                // Restore broadcast header
                conn.sendCommand("ATSH 7DF")

                // Parse raw frames into structured DTCs
                val parsedMap = parseRawDtcFrames(allRawLines)
                val enrichedItems = mutableListOf<PsaDtcItem>()

                for ((canId, dtcList) in parsedMap) {
                    for ((code, rawHex) in dtcList) {
                        val item = dtcDatabase.lookupDtc(code, rawHex, canId)
                        enrichedItems.add(item)
                    }
                }

                _scannedDtcs.value = enrichedItems
                _statusMessage.value = if (enrichedItems.isEmpty()) {
                    "Scan complete: No DTCs detected across all modules (System Healthy)"
                } else {
                    "Scan complete: ${enrichedItems.size} fault(s) detected across modules"
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during multi-ECU scan")
                _statusMessage.value = "Scan error: ${e.message}"
            } finally {
                _isScanning.value = false
                _connectionStatus.value = if (conn.isConnected) ObdConnectionStatus.CONNECTED else prevStatus
            }
        }
    }

    /**
     * Clears diagnostic trouble codes across all ECUs after user confirmation.
     */
    fun clearAllDtcs() {
        val conn = activeConnection ?: return
        scope.launch(Dispatchers.IO) {
            _statusMessage.value = "Sending Clear DTC command across all vehicle ECUs..."
            try {
                conn.sendCommand("ATSH 7DF")
                conn.sendCommand("04")

                conn.sendCommand("ATSH 7E0")
                conn.sendCommand("04")
                conn.sendCommand("14 FF FF FF")

                conn.sendCommand("ATSH 7E1")
                conn.sendCommand("04")
                conn.sendCommand("14 FF FF FF")

                conn.sendCommand("ATSH 760")
                conn.sendCommand("04")

                conn.sendCommand("ATSH 792")
                conn.sendCommand("04")

                conn.sendCommand("ATSH 7DF")
                _scannedDtcs.value = emptyList()
                _statusMessage.value = "Clear command executed. Re-running verification scan..."
                delay(800)
                runDeepVehicleScan()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing DTCs")
                _statusMessage.value = "Clear error: ${e.message}"
            }
        }
    }

    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.IO) {
            val conn = activeConnection ?: return@launch
            while (isActive && conn.isConnected) {
                try {
                    conn.sendCommand("ATSH 7DF")

                    // 1. RPM (010C)
                    val rpmLines = conn.sendCommand("010C")
                    val rpm = parseRpm(rpmLines)

                    // 2. Speed (010D)
                    val spdLines = conn.sendCommand("010D")
                    val spd = parseSpeed(spdLines)

                    // 3. Coolant (0105)
                    val coolLines = conn.sendCommand("0105")
                    val cool = parseCoolant(coolLines)

                    // 4. Fuel Rail Pressure (0123)
                    val railLines = conn.sendCommand("0123")
                    val railBar = parseRailPressure(railLines)

                    // 5. Boost Pressure (010B)
                    val mapLines = conn.sendCommand("010B")
                    val boostBar = parseBoost(mapLines)

                    // 6. Battery Voltage (0142)
                    val voltLines = conn.sendCommand("0142")
                    val volt = parseVoltage(voltLines)

                    // 7. MAF (0110)
                    val mafLines = conn.sendCommand("0110")
                    val maf = parseMaf(mafLines)

                    // Track starter cranking voltage drop
                    if (volt in 5.0..lowestVoltageObserved) {
                        lowestVoltageObserved = volt
                    }
                    val isCrankingDrop = lowestVoltageObserved < 10.5 && rpm > 0

                    _liveTelemetry.value = _liveTelemetry.value.copy(
                        engineRpm = if (rpm >= 0) rpm else _liveTelemetry.value.engineRpm,
                        speedKmh = if (spd >= 0) spd else _liveTelemetry.value.speedKmh,
                        coolantTempC = if (cool > -40) cool else _liveTelemetry.value.coolantTempC,
                        fuelRailPressureBar = if (railBar >= 0) railBar else _liveTelemetry.value.fuelRailPressureBar,
                        boostPressureBar = if (boostBar >= 0) boostBar else _liveTelemetry.value.boostPressureBar,
                        batteryVoltage = if (volt > 0) volt else _liveTelemetry.value.batteryVoltage,
                        minCrankingVoltage = lowestVoltageObserved,
                        mafGramsSec = if (maf >= 0) maf else _liveTelemetry.value.mafGramsSec,
                        isCrankingDropDetected = isCrankingDrop,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )

                    delay(250) // Update 4 times a second
                } catch (e: Exception) {
                    delay(1000)
                }
            }
        }
    }

    private fun parseRpm(lines: List<String>): Int {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("410C([0-9A-F]{4})").matcher(clean)
            if (m.find()) {
                val raw = m.group(1).toInt(16)
                return raw / 4
            }
        }
        return -1
    }

    private fun parseSpeed(lines: List<String>): Int {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("410D([0-9A-F]{2})").matcher(clean)
            if (m.find()) {
                return m.group(1).toInt(16)
            }
        }
        return -1
    }

    private fun parseCoolant(lines: List<String>): Double {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("4105([0-9A-F]{2})").matcher(clean)
            if (m.find()) {
                return m.group(1).toInt(16) - 40.0
            }
        }
        return -100.0
    }

    private fun parseRailPressure(lines: List<String>): Double {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("4123([0-9A-F]{4})").matcher(clean)
            if (m.find()) {
                val rawKpa = m.group(1).toInt(16) * 10.0
                return rawKpa / 100.0 // bar
            }
        }
        return -1.0
    }

    private fun parseBoost(lines: List<String>): Double {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("410B([0-9A-F]{2})").matcher(clean)
            if (m.find()) {
                val mapKpa = m.group(1).toInt(16)
                return mapKpa / 100.0 // bar
            }
        }
        return -1.0
    }

    private fun parseVoltage(lines: List<String>): Double {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("4142([0-9A-F]{4})").matcher(clean)
            if (m.find()) {
                val mv = m.group(1).toInt(16)
                return mv / 1000.0
            }
        }
        return -1.0
    }

    private fun parseMaf(lines: List<String>): Double {
        for (l in lines) {
            val clean = l.replace(" ", "").uppercase()
            val m = Pattern.compile("4110([0-9A-F]{4})").matcher(clean)
            if (m.find()) {
                val raw = m.group(1).toInt(16)
                return raw / 100.0
            }
        }
        return -1.0
    }

    /**
     * Parses raw ELM327 hex responses with headers enabled (ATH1).
     * Reassembles ISO-TP payloads and extracts (Standard DTC, Raw Hex) per ECU.
     */
    private fun parseRawDtcFrames(lines: List<String>): Map<String, List<Pair<String, String>>> {
        val ecuFrames = mutableMapOf<String, MutableList<String>>()

        for (line in lines) {
            val cleaned = line.replace(" ", "").trim().uppercase()
            if (cleaned.isBlank() || cleaned.contains("NODATA") || cleaned.contains("SEARCHING") ||
                cleaned.contains("OK") || cleaned.contains("STOPPED") || cleaned.contains("ERROR")) {
                continue
            }

            // Extract 3-char hex CAN ID
            val m = Pattern.compile("^([0-9A-F]{3})(.*)").matcher(cleaned)
            if (m.matches()) {
                val ecuId = m.group(1)
                val payload = m.group(2)
                ecuFrames.getOrPut(ecuId) { mutableListOf() }.add(payload)
            } else {
                ecuFrames.getOrPut("UNKNOWN") { mutableListOf() }.add(cleaned)
            }
        }

        val results = mutableMapOf<String, MutableList<Pair<String, String>>>()

        for ((ecuId, frames) in ecuFrames) {
            val combinedPayload = StringBuilder()
            for (f in frames) {
                when {
                    f.matches(Regex("^0[0-9A-F].*")) -> combinedPayload.append(f.substring(2)) // Single frame
                    f.matches(Regex("^1[0-9A-F]{3}.*")) -> combinedPayload.append(f.substring(4)) // First frame
                    f.matches(Regex("^2[0-9A-F].*")) -> combinedPayload.append(f.substring(2)) // Consecutive frame
                    else -> combinedPayload.append(f)
                }
            }

            val payloadStr = combinedPayload.toString()
            val dtcs = mutableListOf<Pair<String, String>>()

            // Mode 43 (response to 03) or 47 (response to 07)
            val modePattern = Pattern.compile("(43|47)([0-9A-F]{2})([0-9A-F]+)")
            val modeMatcher = modePattern.matcher(payloadStr)
            if (modeMatcher.find()) {
                val count = modeMatcher.group(2).toInt(16)
                val dtcBytes = modeMatcher.group(3)
                var idx = 0
                var parsedCount = 0
                while (idx + 4 <= dtcBytes.length && parsedCount < count) {
                    val chunk = dtcBytes.substring(idx, idx + 4)
                    if (chunk != "0000") {
                        val parsed = decodeStandardDtc(chunk)
                        dtcs.add(parsed)
                    }
                    idx += 4
                    parsedCount++
                }
            }

            // UDS Service 0x59 (response to 19 02 FF)
            val udsPattern = Pattern.compile("5902FF([0-9A-F]+)")
            val udsMatcher = udsPattern.matcher(payloadStr)
            if (udsMatcher.find()) {
                val udsBytes = udsMatcher.group(1)
                var idx = 0
                while (idx + 4 <= udsBytes.length) {
                    val chunk = udsBytes.substring(idx, idx + 4)
                    if (chunk != "0000") {
                        val parsed = decodeStandardDtc(chunk)
                        if (!dtcs.any { it.first == parsed.first }) {
                            dtcs.add(parsed)
                        }
                    }
                    idx += 6 // 3 bytes (4 hex DTC + 2 hex status)
                }
            }

            if (dtcs.isNotEmpty()) {
                results[ecuId] = dtcs
            }
        }

        return results
    }

    private fun decodeStandardDtc(hex4: String): Pair<String, String> {
        val rawVal = hex4.toInt(16)
        val pIdx = (rawVal shr 14) and 0x03
        val pChar = arrayOf('P', 'C', 'B', 'U')[pIdx]
        val tChar = (rawVal shr 12) and 0x03
        val subHex = "%03X".format(rawVal and 0x0FFF)
        val stdCode = "$pChar$tChar$subHex"
        return stdCode to hex4
    }
}
