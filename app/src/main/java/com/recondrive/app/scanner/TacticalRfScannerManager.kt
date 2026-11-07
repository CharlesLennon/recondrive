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
import com.recondrive.app.audio.TacticalSoundManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Multi-scan weighted running average pinpoint tracker for stationary RF infrastructure
 * (Wi-Fi Routers, Access Points, and fixed CCTV optics).
 */
data class DevicePinpoint(
    val id: String,
    var sampleCount: Int = 0,
    var totalWeight: Double = 0.0,
    var weightedSumLat: Double = 0.0,
    var weightedSumLon: Double = 0.0,
    var pinnedLat: Double = 0.0,
    var pinnedLon: Double = 0.0,
    var bestRssi: Int = -100
)

/**
 * Tactical RF Scanner Manager.
 * Orchestrates Wi-Fi 802.11 and Bluetooth Low Energy spectrum audits in the background,
 * maintaining persistent device location history relative to the host vehicle,
 * multi-scan stationary router pinpointing, and triggering custom tactical audio alerts.
 */
class TacticalRfScannerManager(
    private val context: Context,
    val savedDeviceRepository: SavedRfDeviceRepository = com.recondrive.app.ReconApplication.instance.savedRfDeviceRepository,
    val soundManager: TacticalSoundManager = com.recondrive.app.ReconApplication.instance.tacticalSoundManager,
    val csiEngine: TacticalCsiSensingEngine? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detectedTargets = MutableStateFlow<List<DetectedRfTarget>>(emptyList())
    val detectedTargets: StateFlow<List<DetectedRfTarget>> = _detectedTargets.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isScannerPaused = MutableStateFlow(false)
    val isScannerPaused: StateFlow<Boolean> = _isScannerPaused.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(true)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private val targetMap = ConcurrentHashMap<String, DetectedRfTarget>()
    private val breadcrumbHistory = ConcurrentHashMap<String, MutableList<DeviceBreadcrumb>>()
    private val stationaryPinpointMap = ConcurrentHashMap<String, DevicePinpoint>()
    private val lastAlertTimeMap = ConcurrentHashMap<String, Long>()

    private var lastKnownLat: Double = 50.8555
    private var lastKnownLon: Double = -0.2449
    private var lastKnownHeading: Float = 0f
    private var hasReceivedRealLocation: Boolean = false

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var wifiReceiverRegistered = false
    private var bleScanActive = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                if (!_isScannerPaused.value) {
                    processWifiScanResults()
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (!_isScannerPaused.value) {
                result?.let { processBleScanResult(it) }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            if (!_isScannerPaused.value) {
                results?.forEach { processBleScanResult(it) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("BLE Scan failed with code: $errorCode")
        }
    }

    init {
        populateTacticalSimulationTargets(lastKnownLat, lastKnownLon)
        startPeriodicSweep()
    }

    /**
     * Updates user's real host vehicle location and heading.
     * Anchors live tracking relative to current vehicle position and updates
     * relative bearings/distances for all stationary pinned infrastructure.
     */
    fun updateLastKnownLocation(lat: Double, lon: Double, heading: Float = lastKnownHeading) {
        val prevLat = lastKnownLat
        val prevLon = lastKnownLon
        lastKnownLat = lat
        lastKnownLon = lon
        lastKnownHeading = heading

        // Update relative bearing and distance of all pinned stationary devices relative to vehicle's new position
        var updatedStationary = false
        for ((id, target) in targetMap) {
            if (target.category == RfCategory.ACCESS_POINT || target.category == RfCategory.CCTV_SURVEILLANCE) {
                val tLat = target.detectedLat ?: continue
                val tLon = target.detectedLon ?: continue
                val dLatM = (tLat - lat) * 111320.0
                val dLonM = (tLon - lon) * 111320.0 * cos(Math.toRadians(lat))
                val currentDist = hypot(dLatM, dLonM).coerceAtLeast(1.0)
                var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                if (worldAngle < 0f) worldAngle += 360f
                val relBearing = (worldAngle - heading + 360f) % 360f

                targetMap[id] = target.copy(
                    estimatedDistanceMeters = currentDist,
                    relativeBearingDeg = relBearing
                )
                updatedStationary = true
            }
        }
        if (updatedStationary) {
            syncTargetsToFlow()
        }

        val distMoved = hypot(lat - prevLat, lon - prevLon) * 111320.0
        if (!hasReceivedRealLocation || distMoved > 40.0) {
            hasReceivedRealLocation = true
            populateTacticalSimulationTargets(lat, lon)
        }
    }

    /**
     * Start continuous background spectrum scanning (both Wi-Fi & BLE).
     */
    fun startScanning() {
        _isScanning.value = true
        _isScannerPaused.value = false
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
     * Toggle background pause / resume state.
     */
    fun togglePause() {
        _isScannerPaused.value = !_isScannerPaused.value
        Timber.i("Tactical Scanner pause toggled: ${_isScannerPaused.value}")
    }

    /**
     * Manually trigger an immediate spectrum sweep.
     */
    fun triggerImmediateSweep(currentLat: Double? = null, currentLon: Double? = null) {
        if (currentLat != null && currentLon != null) {
            updateLastKnownLocation(currentLat, currentLon)
        }
        _isScanning.value = true
        triggerHardwareWifiScan()
    }

    fun toggleSimulation() {
        _isSimulationEnabled.value = !_isSimulationEnabled.value
        if (_isSimulationEnabled.value) {
            populateTacticalSimulationTargets(lastKnownLat, lastKnownLon)
        } else {
            listOf(
                "4C:FC:AA:8B:19:2E", "00:26:7E:44:91:FA", "9C:8E:99:A2:4B:11",
                "C0:56:E3:7A:02:D4", "00:62:EC:3F:89:AB", "B4:FB:E4:11:80:55",
                "60:60:1F:B1:33:09"
            ).forEach {
                targetMap.remove(it)
                stationaryPinpointMap.remove(it)
            }
            syncTargetsToFlow()
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
        if (_isScannerPaused.value) return
        try {
            wifiManager?.startScan()
        } catch (e: SecurityException) {
            Timber.w("Wi-Fi startScan security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi scan trigger failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bleScanActive || _isScannerPaused.value) return
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
    private fun processWifiScanResults() {
        try {
            val results = wifiManager?.scanResults ?: return
            val now = System.currentTimeMillis()

            for (scan in results) {
                val ssid = scan.SSID ?: ""
                val bssid = scan.BSSID ?: continue
                val rssi = scan.level
                val freq = scan.frequency

                csiEngine?.feedLiveSample(rssi)

                val classification = TacticalRfClassifier.classify(ssid, bssid)
                val dist = TacticalRfClassifier.estimateDistance(rssi)
                val isStationary = classification.category == RfCategory.ACCESS_POINT || classification.category == RfCategory.CCTV_SURVEILLANCE

                val targetLat: Double
                val targetLon: Double
                val relativeBearing: Float
                val effectiveDistance: Double

                if (isStationary) {
                    // Multi-scan weighted running average pinpointing for fixed routers / CCTV
                    val hashAngle = abs(bssid.hashCode() % 360).toFloat()
                    val angleRad = Math.toRadians(hashAngle.toDouble())
                    val deltaLat = (dist * cos(angleRad)) / 111320.0
                    val deltaLon = (dist * sin(angleRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
                    val instLat = lastKnownLat + deltaLat
                    val instLon = lastKnownLon + deltaLon

                    val tracker = stationaryPinpointMap.getOrPut(bssid) {
                        DevicePinpoint(
                            id = bssid,
                            pinnedLat = instLat,
                            pinnedLon = instLon
                        )
                    }

                    // Weight by signal power: close readings dominate, averaging out RF multipath noise
                    val weight = (100.0 + rssi).coerceAtLeast(5.0) / dist.coerceAtLeast(1.0)
                    tracker.sampleCount++
                    tracker.totalWeight += weight
                    tracker.weightedSumLat += instLat * weight
                    tracker.weightedSumLon += instLon * weight
                    tracker.pinnedLat = tracker.weightedSumLat / tracker.totalWeight
                    tracker.pinnedLon = tracker.weightedSumLon / tracker.totalWeight
                    if (rssi > tracker.bestRssi) {
                        tracker.bestRssi = rssi
                    }

                    // Stationary device location remains locked to physical world coordinates
                    targetLat = tracker.pinnedLat
                    targetLon = tracker.pinnedLon

                    // Accurate bearing and distance relative to vehicle's live position
                    val dLatM = (targetLat - lastKnownLat) * 111320.0
                    val dLonM = (targetLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat))
                    effectiveDistance = hypot(dLatM, dLonM).coerceAtLeast(1.0)

                    var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                    if (worldAngle < 0f) worldAngle += 360f
                    relativeBearing = (worldAngle - lastKnownHeading + 360f) % 360f
                } else {
                    // Mobile devices (vehicles, drones) track dynamic relative motion
                    val hashAngle = abs(bssid.hashCode() % 360).toFloat()
                    relativeBearing = (hashAngle - lastKnownHeading + 360f) % 360f
                    val bearingRad = Math.toRadians(relativeBearing.toDouble())

                    val deltaLat = (dist * cos(bearingRad)) / 111320.0
                    val deltaLon = (dist * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
                    targetLat = lastKnownLat + deltaLat
                    targetLon = lastKnownLon + deltaLon
                    effectiveDistance = dist
                }

                // Record relative breadcrumb history
                val history = breadcrumbHistory.getOrPut(bssid) { mutableListOf() }
                val prevRssi = history.lastOrNull()?.rssi ?: rssi
                history.add(
                    DeviceBreadcrumb(
                        timestamp = now,
                        hostLat = lastKnownLat,
                        hostLon = lastKnownLon,
                        relativeBearingDeg = relativeBearing,
                        distanceMeters = effectiveDistance,
                        rssi = rssi,
                        targetLat = targetLat,
                        targetLon = targetLon
                    )
                )
                if (history.size > 25) {
                    history.removeAt(0)
                }

                // Determine relative motion trend: routers are physically stationary!
                val motionTrend = if (isStationary) {
                    RelativeMotionTrend.STATIONARY
                } else {
                    when {
                        rssi - prevRssi >= 3 -> RelativeMotionTrend.APPROACHING
                        rssi - prevRssi <= -3 -> RelativeMotionTrend.RECEDING
                        else -> RelativeMotionTrend.STATIONARY
                    }
                }

                // Check saved user intel
                val saved = savedDeviceRepository.getDevice(bssid)
                if (saved != null) {
                    checkAndTriggerSavedAlert(saved, effectiveDistance)
                }

                val sweepCount = stationaryPinpointMap[bssid]?.sampleCount ?: 1
                val targetDetails = if (isStationary && sweepCount > 1) {
                    "Pinpointed across $sweepCount sweeps • ${classification.details}"
                } else {
                    classification.details
                }

                val target = DetectedRfTarget(
                    id = bssid,
                    name = classification.targetName,
                    rawSsidOrBleName = ssid,
                    macAddress = bssid,
                    vendor = classification.vendor,
                    category = classification.category,
                    rssi = rssi,
                    estimatedDistanceMeters = effectiveDistance,
                    frequencyMhz = freq,
                    protocol = if (freq > 4900) "Wi-Fi 5GHz (802.11ac/ax)" else "Wi-Fi 2.4GHz (802.11n/ax)",
                    threatLevel = if (isStationary && sweepCount >= 3) "PINPOINTED INFRASTRUCTURE" else classification.threatLevel,
                    details = targetDetails,
                    timestamp = now,
                    detectedLat = targetLat,
                    detectedLon = targetLon,
                    relativeBearingDeg = relativeBearing,
                    locationHistory = history.toList(),
                    motionTrend = motionTrend,
                    savedMetadata = saved
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
    private fun processBleScanResult(result: ScanResult) {
        try {
            val device = result.device ?: return
            val address = device.address ?: return
            val rawName = result.scanRecord?.deviceName ?: device.name ?: ""
            val rssi = result.rssi
            val now = System.currentTimeMillis()

            csiEngine?.feedLiveSample(rssi)

            val classification = TacticalRfClassifier.classify(rawName, address)
            val dist = TacticalRfClassifier.estimateDistance(rssi)
            val isStationary = classification.category == RfCategory.ACCESS_POINT || classification.category == RfCategory.CCTV_SURVEILLANCE

            // Only retain recognized vehicles, cameras, or identifiable BLE beacons to avoid flooding with raw tags
            if (classification.category != RfCategory.ACCESS_POINT || rawName.isNotBlank()) {
                val targetLat: Double
                val targetLon: Double
                val relativeBearing: Float
                val effectiveDistance: Double

                if (isStationary) {
                    val hashAngle = abs(address.hashCode() % 360).toFloat()
                    val angleRad = Math.toRadians(hashAngle.toDouble())
                    val deltaLat = (dist * cos(angleRad)) / 111320.0
                    val deltaLon = (dist * sin(angleRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
                    val instLat = lastKnownLat + deltaLat
                    val instLon = lastKnownLon + deltaLon

                    val tracker = stationaryPinpointMap.getOrPut(address) {
                        DevicePinpoint(id = address, pinnedLat = instLat, pinnedLon = instLon)
                    }

                    val weight = (100.0 + rssi).coerceAtLeast(5.0) / dist.coerceAtLeast(1.0)
                    tracker.sampleCount++
                    tracker.totalWeight += weight
                    tracker.weightedSumLat += instLat * weight
                    tracker.weightedSumLon += instLon * weight
                    tracker.pinnedLat = tracker.weightedSumLat / tracker.totalWeight
                    tracker.pinnedLon = tracker.weightedSumLon / tracker.totalWeight

                    targetLat = tracker.pinnedLat
                    targetLon = tracker.pinnedLon

                    val dLatM = (targetLat - lastKnownLat) * 111320.0
                    val dLonM = (targetLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat))
                    effectiveDistance = hypot(dLatM, dLonM).coerceAtLeast(1.0)

                    var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                    if (worldAngle < 0f) worldAngle += 360f
                    relativeBearing = (worldAngle - lastKnownHeading + 360f) % 360f
                } else {
                    val hashAngle = abs(address.hashCode() % 360).toFloat()
                    relativeBearing = (hashAngle - lastKnownHeading + 360f) % 360f
                    val bearingRad = Math.toRadians(relativeBearing.toDouble())

                    val deltaLat = (dist * cos(bearingRad)) / 111320.0
                    val deltaLon = (dist * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
                    targetLat = lastKnownLat + deltaLat
                    targetLon = lastKnownLon + deltaLon
                    effectiveDistance = dist
                }

                val history = breadcrumbHistory.getOrPut(address) { mutableListOf() }
                val prevRssi = history.lastOrNull()?.rssi ?: rssi
                history.add(
                    DeviceBreadcrumb(
                        timestamp = now,
                        hostLat = lastKnownLat,
                        hostLon = lastKnownLon,
                        relativeBearingDeg = relativeBearing,
                        distanceMeters = effectiveDistance,
                        rssi = rssi,
                        targetLat = targetLat,
                        targetLon = targetLon
                    )
                )
                if (history.size > 25) {
                    history.removeAt(0)
                }

                val motionTrend = if (isStationary) {
                    RelativeMotionTrend.STATIONARY
                } else {
                    when {
                        rssi - prevRssi >= 3 -> RelativeMotionTrend.APPROACHING
                        rssi - prevRssi <= -3 -> RelativeMotionTrend.RECEDING
                        else -> RelativeMotionTrend.STATIONARY
                    }
                }

                val saved = savedDeviceRepository.getDevice(address)
                if (saved != null) {
                    checkAndTriggerSavedAlert(saved, effectiveDistance)
                }

                val sweepCount = stationaryPinpointMap[address]?.sampleCount ?: 1
                val targetDetails = if (isStationary && sweepCount > 1) {
                    "Pinpointed across $sweepCount sweeps • ${classification.details}"
                } else {
                    classification.details
                }

                val target = DetectedRfTarget(
                    id = address,
                    name = classification.targetName,
                    rawSsidOrBleName = rawName,
                    macAddress = address,
                    vendor = classification.vendor,
                    category = classification.category,
                    rssi = rssi,
                    estimatedDistanceMeters = effectiveDistance,
                    frequencyMhz = 2402,
                    protocol = "BLE 5.2 / Bluetooth Low Energy",
                    threatLevel = if (isStationary && sweepCount >= 3) "PINPOINTED INFRASTRUCTURE" else classification.threatLevel,
                    details = targetDetails,
                    timestamp = now,
                    detectedLat = targetLat,
                    detectedLon = targetLon,
                    relativeBearingDeg = relativeBearing,
                    locationHistory = history.toList(),
                    motionTrend = motionTrend,
                    savedMetadata = saved
                )
                targetMap[address] = target
                syncTargetsToFlow()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error processing BLE scan result")
        }
    }

    private fun checkAndTriggerSavedAlert(saved: SavedRfDevice, distanceMeters: Double) {
        if (!saved.notifyOnDetection) return
        val now = System.currentTimeMillis()
        val lastAlert = lastAlertTimeMap[saved.id] ?: 0L
        if (now - lastAlert > 45000L) { // Debounce alerts to once every 45s per target
            lastAlertTimeMap[saved.id] = now
            val spoken = when {
                saved.numberPlate.isNotBlank() -> "Target ${saved.numberPlate} in range"
                saved.customNickname.isNotBlank() -> "${saved.customNickname} in range"
                else -> "${saved.originalName} detected"
            }
            soundManager.playAlert(saved.alertSound, spoken)
            savedDeviceRepository.recordDetection(saved.id, lastKnownLat, lastKnownLon)
        }
    }

    private fun syncTargetsToFlow() {
        // Sort with saved items and high-threat items (Vehicles & CCTV) first, followed by signal strength
        val sorted = targetMap.values.toList().sortedWith(
            compareBy<DetectedRfTarget> {
                if (it.savedMetadata != null) 0 else 1
            }.thenBy {
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
                delay(10000L) // Sweep every 10 seconds in background
                if (_isScanning.value && !_isScannerPaused.value) {
                    mainHandler.post {
                        triggerHardwareWifiScan()
                    }
                }
            }
        }
    }

    /**
     * Seeds tactical RF targets (Tesla vehicle, DJI drone, CCTV cameras, Dashcam, Ford)
     * anchored tightly around user's GPS position (within 8m - 38m) so they appear on the map.
     */
    fun populateTacticalSimulationTargets(userLat: Double, userLon: Double) {
        val now = System.currentTimeMillis()

        // Offsets in meters converted to precise lat/lon delta
        fun getCoord(distMeters: Double, angleDeg: Double): Pair<Double, Double> {
            val rad = Math.toRadians(angleDeg)
            val dLat = (distMeters * cos(rad)) / 111320.0
            val dLon = (distMeters * sin(rad)) / (111320.0 * cos(Math.toRadians(userLat)).coerceAtLeast(0.1))
            return (userLat + dLat) to (userLon + dLon)
        }

        val teslaCoord = getCoord(12.4, 45.0)
        val fordCoord = getCoord(18.2, 120.0)
        val viofoCoord = getCoord(14.5, 210.0)
        val hikvisionCoord = getCoord(22.2, 315.0)
        val ringCoord = getCoord(11.8, 80.0)
        val anprCoord = getCoord(38.0, 5.0)
        val droneCoord = getCoord(28.5, 175.0)

        fun createBreadcrumbs(c: Pair<Double, Double>, dist: Double, rssi: Int, b: Float): List<DeviceBreadcrumb> {
            return listOf(
                DeviceBreadcrumb(now - 6000, userLat - 0.0001, userLon, b, dist + 3.0, rssi - 3, c.first, c.second),
                DeviceBreadcrumb(now - 3000, userLat - 0.00005, userLon, b, dist + 1.5, rssi - 1, c.first, c.second),
                DeviceBreadcrumb(now, userLat, userLon, b, dist, rssi, c.first, c.second)
            )
        }

        // Pin stationary surveillance devices to their physical world coordinates
        listOf(
            Triple("C0:56:E3:7A:02:D4", hikvisionCoord.first, hikvisionCoord.second),
            Triple("00:62:EC:3F:89:AB", ringCoord.first, ringCoord.second),
            Triple("B4:FB:E4:11:80:55", anprCoord.first, anprCoord.second)
        ).forEach { (id, pLat, pLon) ->
            stationaryPinpointMap.putIfAbsent(id, DevicePinpoint(
                id = id,
                sampleCount = 3,
                totalWeight = 30.0,
                weightedSumLat = pLat * 30.0,
                weightedSumLon = pLon * 30.0,
                pinnedLat = pLat,
                pinnedLon = pLon,
                bestRssi = -65
            ))
        }

        val targets = listOf(
            DetectedRfTarget(
                id = "4C:FC:AA:8B:19:2E",
                name = "TESLA MODEL 3 (CONNECTED)",
                rawSsidOrBleName = "Tesla_Guest_9182",
                macAddress = "4C:FC:AA:8B:19:2E",
                vendor = "Tesla Motors, Inc.",
                category = RfCategory.VEHICLE,
                rssi = -58,
                estimatedDistanceMeters = 12.4,
                frequencyMhz = 5180,
                protocol = "Wi-Fi 6 (802.11ax) + BLE Key",
                threatLevel = "MOBILE TELEMETRY",
                details = "Tesla in-cabin infotainment hotspot & BLE phone-key link in range.",
                timestamp = now,
                detectedLat = teslaCoord.first,
                detectedLon = teslaCoord.second,
                relativeBearingDeg = 45f,
                locationHistory = createBreadcrumbs(teslaCoord, 12.4, -58, 45f),
                motionTrend = RelativeMotionTrend.APPROACHING,
                savedMetadata = savedDeviceRepository.getDevice("4C:FC:AA:8B:19:2E")
            ),
            DetectedRfTarget(
                id = "00:26:7E:44:91:FA",
                name = "FORD SYNC VEHICLE INFOTAINMENT",
                rawSsidOrBleName = "Ford Audio_SYNC3",
                macAddress = "00:26:7E:44:91:FA",
                vendor = "Ford Motor Company",
                category = RfCategory.VEHICLE,
                rssi = -68,
                estimatedDistanceMeters = 18.2,
                frequencyMhz = 2412,
                protocol = "Wi-Fi 802.11n + Bluetooth",
                threatLevel = "MOBILE TELEMETRY",
                details = "In-vehicle SYNC cockpit infotainment network.",
                timestamp = now,
                detectedLat = fordCoord.first,
                detectedLon = fordCoord.second,
                relativeBearingDeg = 120f,
                locationHistory = createBreadcrumbs(fordCoord, 18.2, -68, 120f),
                motionTrend = RelativeMotionTrend.STATIONARY,
                savedMetadata = savedDeviceRepository.getDevice("00:26:7E:44:91:FA")
            ),
            DetectedRfTarget(
                id = "9C:8E:99:A2:4B:11",
                name = "VIOFO 4K AUTOMOTIVE DASHCAM",
                rawSsidOrBleName = "VIOFO_A129PRO_4B11",
                macAddress = "9C:8E:99:A2:4B:11",
                vendor = "VIOFO Ltd.",
                category = RfCategory.VEHICLE,
                rssi = -64,
                estimatedDistanceMeters = 14.5,
                frequencyMhz = 5240,
                protocol = "Wi-Fi 5GHz (802.11ac)",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Vehicle forward-facing optic streaming video.",
                timestamp = now,
                detectedLat = viofoCoord.first,
                detectedLon = viofoCoord.second,
                relativeBearingDeg = 210f,
                locationHistory = createBreadcrumbs(viofoCoord, 14.5, -64, 210f),
                motionTrend = RelativeMotionTrend.RECEDING,
                savedMetadata = savedDeviceRepository.getDevice("9C:8E:99:A2:4B:11")
            ),
            DetectedRfTarget(
                id = "C0:56:E3:7A:02:D4",
                name = "HIKVISION IP SURVEILLANCE CCTV",
                rawSsidOrBleName = "HIKVISION_DS2CD2043",
                macAddress = "C0:56:E3:7A:02:D4",
                vendor = "Hangzhou Hikvision Digital Tech",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -72,
                estimatedDistanceMeters = 22.2,
                frequencyMhz = 2437,
                protocol = "Wi-Fi 802.11n",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Commercial optic security camera actively streaming.",
                timestamp = now,
                detectedLat = hikvisionCoord.first,
                detectedLon = hikvisionCoord.second,
                relativeBearingDeg = 315f,
                locationHistory = createBreadcrumbs(hikvisionCoord, 22.2, -72, 315f),
                motionTrend = RelativeMotionTrend.STATIONARY,
                savedMetadata = savedDeviceRepository.getDevice("C0:56:E3:7A:02:D4")
            ),
            DetectedRfTarget(
                id = "00:62:EC:3F:89:AB",
                name = "AMAZON RING SMART DOORBELL / CAM",
                rawSsidOrBleName = "Ring-Setup-89ab",
                macAddress = "00:62:EC:3F:89:AB",
                vendor = "Amazon Ring LLC",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -61,
                estimatedDistanceMeters = 11.8,
                frequencyMhz = 2462,
                protocol = "Wi-Fi 802.11n",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "HD optic camera monitoring entry perimeter.",
                timestamp = now,
                detectedLat = ringCoord.first,
                detectedLon = ringCoord.second,
                relativeBearingDeg = 80f,
                locationHistory = createBreadcrumbs(ringCoord, 11.8, -61, 80f),
                motionTrend = RelativeMotionTrend.APPROACHING,
                savedMetadata = savedDeviceRepository.getDevice("00:62:EC:3F:89:AB")
            ),
            DetectedRfTarget(
                id = "B4:FB:E4:11:80:55",
                name = "MUNICIPAL ANPR / SPEED CAMERA",
                rawSsidOrBleName = "ANPR_SURVEILLANCE_NODE_4",
                macAddress = "B4:FB:E4:11:80:55",
                vendor = "Highways & Traffic Enforcement",
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = -82,
                estimatedDistanceMeters = 38.0,
                frequencyMhz = 5745,
                protocol = "Wi-Fi 802.11a/n",
                threatLevel = "HIGHWAY ENFORCEMENT OPTIC",
                details = "Automatic Number Plate Recognition / Speed Radar telemetry.",
                timestamp = now,
                detectedLat = anprCoord.first,
                detectedLon = anprCoord.second,
                relativeBearingDeg = 5f,
                locationHistory = createBreadcrumbs(anprCoord, 38.0, -82, 5f),
                motionTrend = RelativeMotionTrend.STATIONARY,
                savedMetadata = savedDeviceRepository.getDevice("B4:FB:E4:11:80:55")
            ),
            DetectedRfTarget(
                id = "60:60:1F:B1:33:09",
                name = "DJI AERIAL DRONE (DIRECT REMOTE ID)",
                rawSsidOrBleName = "DJI-Mini3Pro-RemoteID",
                macAddress = "60:60:1F:B1:33:09",
                vendor = "DJI Innovations",
                category = RfCategory.DRONE_UAV,
                rssi = -76,
                estimatedDistanceMeters = 28.5,
                frequencyMhz = 2412,
                protocol = "BLE 5.0 Broadcast",
                threatLevel = "AERIAL UAV IN AIRSPACE",
                details = "Active airborne drone broadcasting FAA/CAA Remote ID.",
                timestamp = now,
                detectedLat = droneCoord.first,
                detectedLon = droneCoord.second,
                relativeBearingDeg = 175f,
                locationHistory = createBreadcrumbs(droneCoord, 28.5, -76, 175f),
                motionTrend = RelativeMotionTrend.RECEDING,
                savedMetadata = savedDeviceRepository.getDevice("60:60:1F:B1:33:09")
            )
        )

        for (t in targets) {
            targetMap[t.id] = t
        }
        syncTargetsToFlow()
    }
}
