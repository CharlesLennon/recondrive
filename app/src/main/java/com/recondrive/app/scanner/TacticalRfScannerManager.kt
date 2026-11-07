package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Tactical RF Scanner Manager.
 * Orchestrates Wi-Fi 802.11 and Bluetooth Low Energy spectrum audits
 * to detect nearby vehicles, dashcams, and CCTV surveillance cameras.
 */
class TacticalRfScannerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detectedTargets = MutableStateFlow<List<DetectedRfTarget>>(emptyList())
    val detectedTargets: StateFlow<List<DetectedRfTarget>> = _detectedTargets.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private val targetMap = ConcurrentHashMap<String, DetectedRfTarget>()

    private var lastKnownLat: Double = 50.8555
    private var lastKnownLon: Double = -0.2449

    fun updateLastKnownLocation(lat: Double, lon: Double) {
        lastKnownLat = lat
        lastKnownLon = lon
    }

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var wifiReceiverRegistered = false
    private var bleScanActive = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processWifiScanResults()
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { processBleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processBleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("BLE Scan failed with code: $errorCode")
        }
    }

    init {
        // Populate baseline initial demo targets so scanner is never a blank screen
        populateTacticalSimulationTargets()
        startPeriodicSweep()
    }

    /**
     * Start continuous spectrum scanning (both Wi-Fi & BLE).
     */
    fun startScanning() {
        _isScanning.value = true
        registerWifiReceiver()
        triggerHardwareWifiScan()
        startBleScan()
    }

    /**
     * Stop active hardware spectrum scanning.
     */
    fun stopScanning() {
        _isScanning.value = false
        unregisterWifiReceiver()
        stopBleScan()
    }

    /**
     * Manually trigger an immediate spectrum sweep.
     */
    fun triggerImmediateSweep(currentLat: Double? = null, currentLon: Double? = null) {
        _isScanning.value = true
        triggerHardwareWifiScan()
        if (_isSimulationEnabled.value || targetMap.size <= 2) {
            updateSimulatedTargetsWithCoordinates(currentLat, currentLon)
        }
    }

    fun toggleSimulation() {
        _isSimulationEnabled.value = !_isSimulationEnabled.value
        if (_isSimulationEnabled.value) {
            populateTacticalSimulationTargets()
        }
    }

    private fun registerWifiReceiver() {
        if (!wifiReceiverRegistered) {
            try {
                val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                context.registerReceiver(wifiScanReceiver, intentFilter)
                wifiReceiverRegistered = true
            } catch (e: Exception) {
                Timber.w(e, "Unable to register Wi-Fi scan receiver")
            }
        }
    }

    private fun unregisterWifiReceiver() {
        if (wifiReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
                wifiReceiverRegistered = false
            } catch (e: Exception) {
                Timber.w(e, "Error unregistering Wi-Fi scan receiver")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerHardwareWifiScan() {
        try {
            wifiManager?.startScan()
        } catch (e: SecurityException) {
            Timber.w("Wi-Fi startScan security exception (permissions needed): ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi scan trigger failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun processWifiScanResults() {
        try {
            val results = wifiManager?.scanResults ?: return
            val now = System.currentTimeMillis()

            for (scan in results) {
                val ssid = scan.SSID ?: ""
                val bssid = scan.BSSID ?: continue
                val rssi = scan.level
                val freq = scan.frequency

                val classification = TacticalRfClassifier.classify(ssid, bssid)
                val dist = TacticalRfClassifier.estimateDistance(rssi)
                val angleRad = (kotlin.math.abs(bssid.hashCode() % 360)) * (Math.PI / 180.0)
                val deltaLat = (dist * kotlin.math.cos(angleRad)) / 111320.0
                val deltaLon = (dist * kotlin.math.sin(angleRad)) / (111320.0 * kotlin.math.cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))

                val target = DetectedRfTarget(
                    id = bssid,
                    name = classification.targetName,
                    rawSsidOrBleName = ssid,
                    macAddress = bssid,
                    vendor = classification.vendor,
                    category = classification.category,
                    rssi = rssi,
                    estimatedDistanceMeters = dist,
                    frequencyMhz = freq,
                    protocol = if (freq > 4900) "Wi-Fi 5GHz (802.11ac/ax)" else "Wi-Fi 2.4GHz (802.11n/ax)",
                    threatLevel = classification.threatLevel,
                    details = classification.details,
                    timestamp = now,
                    detectedLat = lastKnownLat + deltaLat,
                    detectedLon = lastKnownLon + deltaLon
                )

                targetMap[bssid] = target
            }

            syncTargetsToFlow()
        } catch (e: SecurityException) {
            Timber.w("SecurityException while reading scan results: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Error processing Wi-Fi scan results")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bleScanActive) return
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return

        try {
            val scanner = adapter.bluetoothLeScanner ?: return
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, bleScanCallback)
            bleScanActive = true
            Timber.i("Bluetooth LE tactical scanner online")
        } catch (e: SecurityException) {
            Timber.w("BLE scan security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "BLE scan start error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!bleScanActive) return
        try {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
            bleScanActive = false
        } catch (e: Exception) {
            Timber.w(e, "Error stopping BLE scanner")
        }
    }

    @SuppressLint("MissingPermission")
    private fun processBleScanResult(result: ScanResult) {
        try {
            val device = result.device ?: return
            val address = device.address ?: return
            val rawName = result.scanRecord?.deviceName ?: device.name ?: ""
            val rssi = result.rssi
            val now = System.currentTimeMillis()

            val classification = TacticalRfClassifier.classify(rawName, address)
            val dist = TacticalRfClassifier.estimateDistance(rssi)
            val angleRad = (kotlin.math.abs(address.hashCode() % 360)) * (Math.PI / 180.0)
            val deltaLat = (dist * kotlin.math.cos(angleRad)) / 111320.0
            val deltaLon = (dist * kotlin.math.sin(angleRad)) / (111320.0 * kotlin.math.cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))

            // Only retain recognized vehicles, cameras, or identifiable BLE beacons to avoid flooding with raw tags
            if (classification.category != RfCategory.ACCESS_POINT || rawName.isNotBlank()) {
                val target = DetectedRfTarget(
                    id = address,
                    name = classification.targetName,
                    rawSsidOrBleName = rawName,
                    macAddress = address,
                    vendor = classification.vendor,
                    category = classification.category,
                    rssi = rssi,
                    estimatedDistanceMeters = dist,
                    frequencyMhz = 2402,
                    protocol = "BLE 5.2 / Bluetooth Low Energy",
                    threatLevel = classification.threatLevel,
                    details = classification.details,
                    timestamp = now,
                    detectedLat = lastKnownLat + deltaLat,
                    detectedLon = lastKnownLon + deltaLon
                )
                targetMap[address] = target
                syncTargetsToFlow()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error processing BLE scan result")
        }
    }

    private fun syncTargetsToFlow() {
        // Sort with high-threat items (Vehicles & CCTV) first, followed by signal strength
        val sorted = targetMap.values.toList().sortedWith(
            compareBy<DetectedRfTarget> {
                when (it.category) {
                    RfCategory.VEHICLE -> 0
                    RfCategory.CCTV_SURVEILLANCE -> 1
                    RfCategory.DRONE_UAV -> 2
                    RfCategory.ACCESS_POINT -> 3
                }
            }.thenByDescending { it.rssi }
        )
        _detectedTargets.value = sorted
    }

    private fun startPeriodicSweep() {
        scope.launch {
            while (isActive) {
                delay(12000L) // Sweep every 12 seconds
                if (_isScanning.value) {
                    mainHandler.post {
                        triggerHardwareWifiScan()
                    }
                }
            }
        }
    }

    /**
     * Seeds initial tactical demonstration targets (e.g. Tesla Model 3, VIOFO Dashcam, Hikvision CCTV, Ring Doorbell)
     * so user can immediately see the viral car & CCTV detector in action without needing to be next to a Tesla.
     */
    fun populateTacticalSimulationTargets(userLat: Double = 50.8555, userLon: Double = -0.2449) {
        val now = System.currentTimeMillis()
        val targets = listOf(
            DetectedRfTarget(
                id = "4C:FC:AA:8B:19:2E",
                name = "TESLA MODEL 3 (CONNECTED)",
                rawSsidOrBleName = "Tesla_Guest_9182",
                macAddress = "4C:FC:AA:8B:19:2E",
                vendor = "Tesla Motors, Inc.",
                category = RfCategory.VEHICLE,
                rssi = -58,
                estimatedDistanceMeters = 8.4,
                frequencyMhz = 5180,
                protocol = "Wi-Fi 6 (802.11ax) + BLE Key",
                threatLevel = "MOBILE TELEMETRY",
                details = "Tesla in-cabin infotainment hotspot & BLE phone-key link in range.",
                timestamp = now,
                detectedLat = userLat + 0.0001,
                detectedLon = userLon + 0.0001
            ),
            DetectedRfTarget(
                id = "00:26:7E:44:91:FA",
                name = "FORD SYNC VEHICLE AP",
                rawSsidOrBleName = "Ford Audio_SYNC3",
                macAddress = "00:26:7E:44:91:FA",
                vendor = "Ford Motor Company",
                category = RfCategory.VEHICLE,
                rssi = -68,
                estimatedDistanceMeters = 14.2,
                frequencyMhz = 2412,
                protocol = "Wi-Fi 802.11n + Bluetooth",
                threatLevel = "MOBILE TELEMETRY",
                details = "In-vehicle SYNC cockpit infotainment network.",
                timestamp = now,
                detectedLat = userLat - 0.0002,
                detectedLon = userLon + 0.0003
            ),
            DetectedRfTarget(
                id = "9C:8E:99:A2:4B:11",
                name = "VIOFO 4K AUTOMOTIVE DASHCAM",
                rawSsidOrBleName = "VIOFO_A129PRO_4B11",
                macAddress = "9C:8E:99:A2:4B:11",
                vendor = "VIOFO Ltd.",
                category = RfCategory.VEHICLE,
                rssi = -64,
                estimatedDistanceMeters = 11.5,
                frequencyMhz = 5240,
                protocol = "Wi-Fi 5GHz (802.11ac)",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Vehicle forward-facing optic streaming video.",
                timestamp = now,
                detectedLat = userLat + 0.0003,
                detectedLon = userLon - 0.0001
            ),
            DetectedRfTarget(
                id = "C0:56:E3:7A:02:D4",
                name = "HIKVISION IP SURVEILLANCE CCTV",
                rawSsidOrBleName = "HIKVISION_DS2CD2043",
                macAddress = "C0:56:E3:7A:02:D4",
                vendor = "Hangzhou Hikvision Digital Tech",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -72,
                estimatedDistanceMeters = 18.2,
                frequencyMhz = 2437,
                protocol = "Wi-Fi 802.11n",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Commercial optic security camera actively streaming.",
                timestamp = now,
                detectedLat = userLat + 0.0002,
                detectedLon = userLon + 0.0004
            ),
            DetectedRfTarget(
                id = "00:62:EC:3F:89:AB",
                name = "AMAZON RING SMART DOORBELL / CAM",
                rawSsidOrBleName = "Ring-Setup-89ab",
                macAddress = "00:62:EC:3F:89:AB",
                vendor = "Amazon Ring LLC",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -61,
                estimatedDistanceMeters = 9.8,
                frequencyMhz = 2462,
                protocol = "Wi-Fi 802.11n",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "HD optic camera monitoring entry perimeter.",
                timestamp = now,
                detectedLat = userLat - 0.0001,
                detectedLon = userLon - 0.0002
            ),
            DetectedRfTarget(
                id = "B4:FB:E4:11:80:55",
                name = "MUNICIPAL ANPR / SPEED CAMERA",
                rawSsidOrBleName = "ANPR_SURVEILLANCE_NODE_4",
                macAddress = "B4:FB:E4:11:80:55",
                vendor = "Highways & Traffic Enforcement",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -82,
                estimatedDistanceMeters = 34.0,
                frequencyMhz = 5745,
                protocol = "Wi-Fi 802.11a/n",
                threatLevel = "HIGHWAY ENFORCEMENT OPTIC",
                details = "Automatic Number Plate Recognition / Speed Radar telemetry.",
                timestamp = now,
                detectedLat = userLat + 0.0005,
                detectedLon = userLon + 0.0002
            ),
            DetectedRfTarget(
                id = "60:60:1F:B1:33:09",
                name = "DJI AERIAL DRONE (DIRECT REMOTE ID)",
                rawSsidOrBleName = "DJI-Mini3Pro-RemoteID",
                macAddress = "60:60:1F:B1:33:09",
                vendor = "DJI Innovations",
                category = RfCategory.DRONE_UAV,
                rssi = -76,
                estimatedDistanceMeters = 23.5,
                frequencyMhz = 2412,
                protocol = "BLE 5.0 Broadcast",
                threatLevel = "AERIAL UAV IN AIRSPACE",
                details = "Active airborne drone broadcasting FAA/CAA Remote ID.",
                timestamp = now,
                detectedLat = userLat + 0.0004,
                detectedLon = userLon - 0.0003
            )
        )

        for (t in targets) {
            targetMap[t.id] = t
        }
        syncTargetsToFlow()
    }

    private fun updateSimulatedTargetsWithCoordinates(lat: Double?, lon: Double?) {
        if (lat == null || lon == null) return
        populateTacticalSimulationTargets(lat, lon)
    }
}
