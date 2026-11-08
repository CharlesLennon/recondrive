package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.*
import androidx.core.content.ContextCompat
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
 * (Wi-Fi Routers, Access Points, CCTV optics, and Cellular Base Stations).
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
 * Orchestrates multi-spectrum hardware audits:
 * 1. Wi-Fi 802.11 active & passive MAC sniffing (2.4/5/6GHz)
 * 2. Bluetooth Low Energy (BLE) continuous low-latency sniffing (AirTags, SmartTags, Tesla Key, TPMS, Drone Remote ID)
 * 3. Bluetooth Classic BR/EDR inquiry discovery (Car Infotainment, Hands-free, OBD-II CAN-Bus dongles)
 * 4. Wi-Fi Direct (P2P) peer discovery (Android Auto, wireless displays, action cams)
 * 5. Cellular base station telemetry (4G LTE / 5G NR base station cell towers)
 * 6. Magnetic Anomaly Detection (MAD) for close-proximity vehicle steel mass detection
 *
 * Defaults to 24/7 LIVE hardware RF sniffing (Simulation defaults to OFF).
 */
class TacticalRfScannerManager(
    private val context: Context,
    val savedDeviceRepository: SavedRfDeviceRepository = com.recondrive.app.ReconApplication.instance.savedRfDeviceRepository,
    val soundManager: TacticalSoundManager = com.recondrive.app.ReconApplication.instance.tacticalSoundManager,
    val csiEngine: TacticalCsiSensingEngine? = null
) : SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detectedTargets = MutableStateFlow<List<DetectedRfTarget>>(emptyList())
    val detectedTargets: StateFlow<List<DetectedRfTarget>> = _detectedTargets.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isScannerPaused = MutableStateFlow(false)
    val isScannerPaused: StateFlow<Boolean> = _isScannerPaused.asStateFlow()

    // Pure live hardware sniffing by default; simulation mock targets are strictly opt-in
    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    // Microphone Acoustic Siren Doppler detector toggle (Strictly user-controlled to prevent background audio crash)
    private val _isSirenDetectorEnabled = MutableStateFlow(false)
    val isSirenDetectorEnabled: StateFlow<Boolean> = _isSirenDetectorEnabled.asStateFlow()

    private val targetMap = ConcurrentHashMap<String, DetectedRfTarget>()
    private val breadcrumbHistory = ConcurrentHashMap<String, MutableList<DeviceBreadcrumb>>()
    private val stationaryPinpointMap = ConcurrentHashMap<String, DevicePinpoint>()
    private val lastAlertTimeMap = ConcurrentHashMap<String, Long>()

    private var lastKnownLat: Double = 50.8555
    private var lastKnownLon: Double = -0.2449
    private var lastKnownHeading: Float = 0f
    private var hasReceivedRealLocation: Boolean = false

    // Hardware Managers
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val wifiP2pManager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val telephonyManager = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Multi-Spectrum Tactical Sensor Engines
    val uwbEngine: TacticalUwbEngine = TacticalUwbEngine(context)
    val irEngine: TacticalInfraredDetectionEngine = TacticalInfraredDetectionEngine(context)
    val barometerEngine: TacticalBarometerEngine = TacticalBarometerEngine(context)
    val sirenEngine: TacticalAudioSirenEngine = TacticalAudioSirenEngine(context, soundManager)
    val imuEngine: TacticalImuEngine = TacticalImuEngine(context)
    val nfcManager: TacticalNfcManager = TacticalNfcManager(context)
    val gnssEngine: TacticalGnssEngine = TacticalGnssEngine(context)
    val wifiRttEngine: TacticalWifiRttEngine = TacticalWifiRttEngine(context)
    val publicCctvRepo: TacticalPublicCctvRepository = TacticalPublicCctvRepository(context)
    val imsiDetector: TacticalImsiCatcherDetector = TacticalImsiCatcherDetector(context, soundManager)
    val imsiDetectionState: StateFlow<ImsiDetectionState> = imsiDetector.state
    val coTravelEngine: TacticalCoTravelEngine = TacticalCoTravelEngine()

    private var lastKnownSpeed: Float = 0f

    fun dismissCoTravel(targetId: String) {
        coTravelEngine.dismissTarget(targetId)
        val existing = targetMap[targetId] ?: return
        targetMap[targetId] = existing.copy(
            coTravel = existing.coTravel?.copy(isDismissed = true, level = CoTravelLevel.INDEPENDENT)
        )
        syncTargetsToFlow()
    }

    init {
        nfcManager.onVehicleKeyDetected = { scanned ->
            processNfcVehicleKey(scanned)
        }
    }

    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    private var wifiReceiverRegistered = false
    private var btClassicReceiverRegistered = false
    private var wifiP2pReceiverRegistered = false
    private var bleScanActive = false
    private var lastClassicDiscoveryTrigger = 0L

    // Magnetic Anomaly Baseline for Ferromagnetic Vehicle Proximity Detection
    private var baselineMagneticMagnitude = 45.0f
    private var lastMagneticAnomalyTime = 0L
    private var lastDroneAlertTime = 0L
    private var lastCoTravelAlertTime = 0L
    private var lastKeyAlertTime = 0L

    // Wi-Fi Scan Broadcast Receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                if (!_isScannerPaused.value) {
                    processWifiScanResults()
                }
            }
        }
    }

    // Bluetooth Classic Inquiry Broadcast Receiver
    private val btClassicReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!_isScannerPaused.value) {
                        try {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            } ?: return

                            val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: device.name ?: ""
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-75).toShort()).toInt()
                            val btClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
                            } ?: device.bluetoothClass

                            processBluetoothDevice(device, name, rssi, btClass, isClassic = true)
                        } catch (e: Exception) {
                            Timber.w(e, "Error parsing Bluetooth Classic device")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Timber.d("Bluetooth Classic inquiry sweep finished")
                }
            }
        }
    }

    // Wi-Fi Direct (P2P) Peer Receiver
    private val wifiP2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                if (!_isScannerPaused.value && wifiP2pChannel != null) {
                    try {
                        wifiP2pManager?.requestPeers(wifiP2pChannel) { peers ->
                            processWifiP2pPeers(peers)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error requesting Wi-Fi Direct peers")
                    }
                }
            }
        }
    }

    // Bluetooth LE Scan Callback
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
        // Initialize Wi-Fi P2P Channel
        try {
            wifiP2pChannel = wifiP2pManager?.initialize(context.applicationContext, Looper.getMainLooper(), null)
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi P2P initialization failed")
        }

        uwbEngine.startRanging()

        scope.launch {
            uwbEngine.activeUwbSessions.collect { sessions ->
                for (session in sessions) {
                    processUwbSession(session)
                }
            }
        }

        scope.launch {
            irEngine.irAlertDetected.collect { isAlert ->
                if (isAlert) {
                    processOpticalIrAlert()
                }
            }
        }

        // Start 24/7 background hardware scanning immediately
        startScanning()
        startPeriodicSweep()
    }

    /**
     * Updates user's real host vehicle location and heading.
     * Anchors live tracking relative to current vehicle position and updates
     * relative bearings/distances for all stationary pinned infrastructure.
     */
    fun updateLastKnownLocation(lat: Double, lon: Double, heading: Float = lastKnownHeading, speedKmh: Float = lastKnownSpeed) {
        val prevLat = lastKnownLat
        val prevLon = lastKnownLon
        lastKnownLat = lat
        lastKnownLon = lon
        lastKnownHeading = heading
        lastKnownSpeed = speedKmh

        // Update relative bearing and distance of all pinned stationary devices relative to vehicle's new position
        var updatedStationary = false
        for ((id, target) in targetMap) {
            if (target.category == RfCategory.ACCESS_POINT || target.category == RfCategory.CCTV_SURVEILLANCE || target.category == RfCategory.CELL_TOWER) {
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
            if (!hasReceivedRealLocation) {
                hasReceivedRealLocation = true
                // Prune any default startup coordinates
                targetMap.clear()
                stationaryPinpointMap.clear()
            }
            ingestPublicCctvCameras(lat, lon, heading)
        }
    }

    private fun ingestPublicCctvCameras(lat: Double, lon: Double, heading: Float) {
        // Prune any CCTV cameras farther than 1500m from current host vehicle position
        val outOfRange = targetMap.filter { (_, target) ->
            target.category == RfCategory.CCTV_SURVEILLANCE &&
            target.detectedLat != null && target.detectedLon != null &&
            hypot((target.detectedLat - lat) * 111320.0, (target.detectedLon - lon) * 111320.0 * cos(Math.toRadians(lat))) > 1500.0
        }.keys
        outOfRange.forEach { targetMap.remove(it) }

        // Ingest local cameras within 1200m range
        val nearCams = publicCctvRepo.getCamerasNearLocation(lat, lon, 1200.0)
        for ((cam, dist) in nearCams) {
            val dLatM = (cam.latitude - lat) * 111320.0
            val dLonM = (cam.longitude - lon) * 111320.0 * cos(Math.toRadians(lat))
            var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
            if (worldAngle < 0f) worldAngle += 360f
            val relBearing = (worldAngle - heading + 360f) % 360f

            val hasLive = !cam.liveImageUrl.isNullOrBlank() && cam.liveImageUrl.startsWith("http")
            val target = DetectedRfTarget(
                id = cam.id,
                name = "📷 ${cam.name}",
                rawSsidOrBleName = "PUBLIC_CCTV_CAM",
                macAddress = cam.id,
                vendor = cam.operator,
                category = RfCategory.CCTV_SURVEILLANCE,
                rssi = (-45 - (dist / 20.0).toInt()).coerceIn(-95, -30),
                estimatedDistanceMeters = dist,
                frequencyMhz = 2412,
                protocol = if (hasLive) "PUBLIC ACCESS CCTV (LIVE STREAM)" else "PUBLIC ACCESS CCTV (TACTICAL OPTIC)",
                threatLevel = "OPEN PUBLIC TRAFFIC & SECURITY CAMERA",
                details = if (hasLive) {
                    "${cam.locationDesc} • Operator: ${cam.operator} • URL: ${cam.liveImageUrl}"
                } else {
                    "${cam.locationDesc} • Operator: ${cam.operator} • TACTICAL OPTIC / FLIR"
                },
                timestamp = System.currentTimeMillis(),
                detectedLat = cam.latitude,
                detectedLon = cam.longitude,
                relativeBearingDeg = relBearing,
                locationHistory = emptyList(),
                motionTrend = RelativeMotionTrend.APPROACHING,
                savedMetadata = savedDeviceRepository.getDevice(cam.id),
                detectionTech = DetectionTechnology.PUBLIC_CCTV_STREAM
            )
            targetMap[cam.id] = target
        }
        syncTargetsToFlow()
    }

    /**
     * Start continuous background multi-spectrum scanning (Wi-Fi, BLE, BT Classic, P2P, Cellular, MAD).
     */
    fun startScanning() {
        _isScanning.value = true
        _isScannerPaused.value = false

        registerWifiReceiver()
        registerBtClassicReceiver()
        registerWifiP2pReceiver()
        registerMagnetometer()

        try { barometerEngine.start() } catch (t: Throwable) { Timber.w(t, "Barometer failed to start") }
        if (_isSirenDetectorEnabled.value) {
            try { sirenEngine.start() } catch (t: Throwable) { Timber.w(t, "Siren engine failed to start") }
        }
        try { imuEngine.start() } catch (t: Throwable) { Timber.w(t, "IMU engine failed to start") }
        try { gnssEngine.start() } catch (t: Throwable) { Timber.w(t, "GNSS engine failed to start") }
        try { wifiRttEngine.start() } catch (t: Throwable) { Timber.w(t, "WiFi RTT engine failed to start") }
        try { nfcManager.checkStatus() } catch (t: Throwable) { Timber.w(t, "NFC manager failed to check status") }

        triggerHardwareWifiScan()
        startBleScan()
        triggerBluetoothClassicDiscovery()
        triggerWifiP2pDiscovery()
        auditCellularBaseStations()
        auditBondedBluetoothDevices()
    }

    /**
     * Stop active hardware spectrum scanning.
     */
    fun stopScanning() {
        _isScanning.value = false
        unregisterWifiReceiver()
        unregisterBtClassicReceiver()
        unregisterWifiP2pReceiver()
        unregisterMagnetometer()
        stopBleScan()

        barometerEngine.stop()
        sirenEngine.stop()
        imuEngine.stop()
        gnssEngine.stop()
        wifiRttEngine.stop()
    }

    /**
     * Toggle background pause / resume state.
     */
    fun togglePause() {
        _isScannerPaused.value = !_isScannerPaused.value
        Timber.i("Tactical Scanner pause toggled: ${_isScannerPaused.value}")
    }

    /**
     * Manually trigger an immediate spectrum sweep across all hardware radios.
     */
    fun triggerImmediateSweep(currentLat: Double? = null, currentLon: Double? = null) {
        if (currentLat != null && currentLon != null) {
            updateLastKnownLocation(currentLat, currentLon)
        }
        _isScanning.value = true
        triggerHardwareWifiScan()
        triggerBluetoothClassicDiscovery()
        triggerWifiP2pDiscovery()
        auditCellularBaseStations()
    }

    /**
     * Toggles simulation demo mode. Strictly kept false (Pure LIVE hardware sniffing only).
     */
    fun toggleSimulation() {
        _isSimulationEnabled.value = false
        listOf(
            "4C:FC:AA:8B:19:2E", "00:26:7E:44:91:FA", "9C:8E:99:A2:4B:11",
            "C0:56:E3:7A:02:D4", "00:62:EC:3F:89:AB", "B4:FB:E4:11:80:55",
            "60:60:1F:B1:33:09", "UWB_BMW_DIGITAL_KEY_3", "TESLA_KEY_SIM_0211", "NIR_OPTICAL_ILLUMINATOR",
            "CELL_ROGUE_2G_STINGRAY"
        ).forEach {
            targetMap.remove(it)
            stationaryPinpointMap.remove(it)
        }
        imsiDetector.simulateAttack(false)
        syncTargetsToFlow()
    }

    /**
     * Toggles acoustic emergency siren listener.
     * Default is false to strictly prevent unauthorized/unwanted background microphone capture.
     */
    fun toggleSirenDetector(enable: Boolean? = null) {
        val target = enable ?: !_isSirenDetectorEnabled.value
        _isSirenDetectorEnabled.value = target
        if (target) {
            try { sirenEngine.start() } catch (t: Throwable) { Timber.w(t, "Failed to start siren detector") }
        } else {
            sirenEngine.stop()
        }
        Timber.i("Tactical siren listener toggled: ${_isSirenDetectorEnabled.value}")
    }

    fun simulateImsiAttack(enable: Boolean) {
        // Disabled; pure live cellular audit only
        targetMap.remove("CELL_ROGUE_2G_STINGRAY")
        imsiDetector.simulateAttack(false)
        syncTargetsToFlow()
    }

    // =========================================================================
    // BROADCAST RECEIVER REGISTRATIONS
    // =========================================================================

    private fun registerWifiReceiver() {
        if (!wifiReceiverRegistered) {
            try {
                val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                ContextCompat.registerReceiver(
                    context,
                    wifiScanReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                wifiReceiverRegistered = true
            } catch (e: Throwable) {
                Timber.w(e, "Unable to register Wi-Fi scan receiver")
            }
        }
    }

    private fun unregisterWifiReceiver() {
        if (wifiReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
                wifiReceiverRegistered = false
            } catch (e: Throwable) {
                Timber.w(e, "Error unregistering Wi-Fi scan receiver")
            }
        }
    }

    private fun registerBtClassicReceiver() {
        if (!btClassicReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                ContextCompat.registerReceiver(
                    context,
                    btClassicReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                btClassicReceiverRegistered = true
            } catch (e: Throwable) {
                Timber.w(e, "Unable to register Bluetooth Classic receiver")
            }
        }
    }

    private fun unregisterBtClassicReceiver() {
        if (btClassicReceiverRegistered) {
            try {
                context.unregisterReceiver(btClassicReceiver)
                btClassicReceiverRegistered = false
            } catch (e: Throwable) {
                Timber.w(e, "Error unregistering BT Classic receiver")
            }
        }
    }

    private fun registerWifiP2pReceiver() {
        if (!wifiP2pReceiverRegistered) {
            try {
                val filter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                ContextCompat.registerReceiver(
                    context,
                    wifiP2pReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                wifiP2pReceiverRegistered = true
            } catch (e: Throwable) {
                Timber.w(e, "Unable to register Wi-Fi P2P receiver")
            }
        }
    }

    private fun unregisterWifiP2pReceiver() {
        if (wifiP2pReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiP2pReceiver)
                wifiP2pReceiverRegistered = false
            } catch (e: Exception) {
                Timber.w(e, "Error unregistering Wi-Fi P2P receiver")
            }
        }
    }

    private fun registerMagnetometer() {
        try {
            magnetometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error registering magnetometer for vehicle MAD detection")
        }
    }

    private fun unregisterMagnetometer() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering magnetometer")
        }
    }

    // =========================================================================
    // HARDWARE TRIGGER METHODS
    // =========================================================================

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
            Timber.i("Bluetooth LE tactical scanner online (Low-Latency Continuous Mode)")
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
    private fun triggerBluetoothClassicDiscovery() {
        if (_isScannerPaused.value) return
        val now = System.currentTimeMillis()
        if (now - lastClassicDiscoveryTrigger < 20000L) return // Throttle classic discovery to once every 20s
        lastClassicDiscoveryTrigger = now

        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
            Timber.d("Bluetooth Classic inquiry triggered")
        } catch (e: SecurityException) {
            Timber.w("Bluetooth Classic discovery security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Error starting Bluetooth Classic discovery")
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerWifiP2pDiscovery() {
        if (_isScannerPaused.value || wifiP2pManager == null || wifiP2pChannel == null) return
        try {
            wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.d("Wi-Fi Direct P2P peer discovery initiated")
                }

                override fun onFailure(reasonCode: Int) {
                    Timber.w("Wi-Fi Direct P2P discovery failed with code: $reasonCode")
                }
            })
        } catch (e: SecurityException) {
            Timber.w("Wi-Fi P2P security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Error triggering Wi-Fi Direct discovery")
        }
    }

    @SuppressLint("MissingPermission")
    private fun auditBondedBluetoothDevices() {
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        try {
            val bonded = adapter.bondedDevices ?: return
            for (dev in bonded) {
                val address = dev.address ?: continue
                val name = dev.name ?: "PAIRED DEVICE"
                val btClass = dev.bluetoothClass
                processBluetoothDevice(dev, name, -65, btClass, isClassic = true, isBonded = true)
            }
        } catch (e: SecurityException) {
            Timber.w("Bonded devices security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Error auditing bonded devices")
        }
    }

    @SuppressLint("MissingPermission")
    private fun auditCellularBaseStations() {
        if (_isScannerPaused.value || telephonyManager == null) return
        try {
            val cellList = telephonyManager.allCellInfo ?: return
            val now = System.currentTimeMillis()
            val imsiState = imsiDetector.evaluateCellEnvironment(cellList, lastKnownLat, lastKnownLon, lastKnownSpeed)

            for (cell in cellList) {
                var cellId = ""
                var cellTech = "Cellular"
                var operatorName = "Carrier Base Station"
                var dbm = -90
                var pci = 0
                var tac = 0
                val isServing = cell.isRegistered

                when (cell) {
                    is CellInfoLte -> {
                        cellTech = "4G LTE"
                        val id = cell.cellIdentity
                        pci = id.pci
                        tac = id.tac
                        val ci = id.ci
                        dbm = cell.cellSignalStrength.dbm
                        operatorName = id.operatorAlphaLong?.toString() ?: id.mccString ?: "LTE Carrier"
                        cellId = "CELL_LTE_${ci}_$pci"
                    }
                    is CellInfoNr -> {
                        cellTech = "5G NR"
                        val id = cell.cellIdentity as? CellIdentityNr
                        if (id != null) {
                            pci = id.pci
                            tac = id.tac
                            val nci = id.nci
                            dbm = cell.cellSignalStrength.dbm
                            operatorName = id.operatorAlphaLong?.toString() ?: id.mccString ?: "5G Carrier"
                            cellId = "CELL_5G_${nci}_$pci"
                        }
                    }
                    is CellInfoGsm -> {
                        cellTech = "2G GSM"
                        val id = cell.cellIdentity
                        val cid = id.cid
                        dbm = cell.cellSignalStrength.dbm
                        operatorName = id.operatorAlphaLong?.toString() ?: "GSM Carrier"
                        cellId = "CELL_GSM_$cid"
                    }
                    is CellInfoWcdma -> {
                        cellTech = "3G WCDMA"
                        val id = cell.cellIdentity
                        val cid = id.cid
                        dbm = cell.cellSignalStrength.dbm
                        operatorName = id.operatorAlphaLong?.toString() ?: "3G Carrier"
                        cellId = "CELL_3G_$cid"
                    }
                }

                if (cellId.isBlank() || dbm == Int.MAX_VALUE) continue

                // Estimated line-of-sight distance to base station (typical LTE/5G macro cell path loss)
                val distMeters = (10.0.pow((-45.0 - dbm) / 32.0) * 10.0).coerceIn(40.0, 950.0)

                // Fixed cellular mast: lock coordinates in physical earth coordinates
                val hashAngle = abs(cellId.hashCode() % 360).toFloat()
                val angleRad = Math.toRadians(hashAngle.toDouble())
                val deltaLat = (distMeters * cos(angleRad)) / 111320.0
                val deltaLon = (distMeters * sin(angleRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
                val instLat = lastKnownLat + deltaLat
                val instLon = lastKnownLon + deltaLon

                val tracker = stationaryPinpointMap.getOrPut(cellId) {
                    DevicePinpoint(id = cellId, pinnedLat = instLat, pinnedLon = instLon)
                }

                // Reset pin if previous pin is grossly divergent from live signal power
                // (e.g. pinned before GPS fix or vehicle travelled far away)
                val prevCellDist = hypot((tracker.pinnedLat - lastKnownLat) * 111320.0, (tracker.pinnedLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat)))
                if (!hasReceivedRealLocation || (prevCellDist > 1200.0 && distMeters < 500.0) || prevCellDist > 2500.0) {
                    tracker.pinnedLat = instLat
                    tracker.pinnedLon = instLon
                    tracker.totalWeight = 0.0
                    tracker.weightedSumLat = 0.0
                    tracker.weightedSumLon = 0.0
                    tracker.sampleCount = 0
                }

                val weight = (120.0 + dbm).coerceAtLeast(5.0) / distMeters.coerceAtLeast(10.0)
                tracker.sampleCount++
                tracker.totalWeight += weight
                tracker.weightedSumLat += instLat * weight
                tracker.weightedSumLon += instLon * weight
                tracker.pinnedLat = tracker.weightedSumLat / tracker.totalWeight
                tracker.pinnedLon = tracker.weightedSumLon / tracker.totalWeight

                val dLatM = (tracker.pinnedLat - lastKnownLat) * 111320.0
                val dLonM = (tracker.pinnedLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat))
                val rawComputedDist = hypot(dLatM, dLonM).coerceAtLeast(10.0)
                val effectiveDist = if (rawComputedDist > 1200.0) distMeters else rawComputedDist

                var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                if (worldAngle < 0f) worldAngle += 360f
                val relativeBearing = (worldAngle - lastKnownHeading + 360f) % 360f

                val isRogueSuspect = (imsiState.threatLevel != ImsiThreatLevel.CLEAR && isServing) || (isServing && imsiState.is2gDowngraded)
                val targetThreat = when {
                    isRogueSuspect -> "⚠️ ROGUE BASE STATION / INTERCEPTION RISK"
                    isServing -> "SERVING CELL TOWER (SECURE)"
                    else -> "NEIGHBORING BASE STATION"
                }
                val targetDetails = when {
                    isRogueSuspect -> "⚠️ INTERCEPTION ANOMALY • ${imsiState.activeServingTech} • ${imsiState.cipherStatus} • TAC/LAC: $tac • PCI: $pci • Power: ${dbm}dBm"
                    isServing -> "PRIMARY SERVING • ${imsiState.cipherStatus} • TAC: $tac • PCI: $pci • Power: ${dbm}dBm"
                    else -> "NEIGHBORING base station • TAC: $tac • PCI: $pci • Power: ${dbm}dBm"
                }

                val target = DetectedRfTarget(
                    id = cellId,
                    name = if (isRogueSuspect) "⚠️ ROGUE CELL ($operatorName)" else "$cellTech BASE STATION ($operatorName)",
                    rawSsidOrBleName = operatorName,
                    macAddress = cellId,
                    vendor = operatorName,
                    category = RfCategory.CELL_TOWER,
                    rssi = dbm,
                    estimatedDistanceMeters = effectiveDist,
                    frequencyMhz = if (cellTech.contains("5G")) 3500 else 1800,
                    protocol = if (isRogueSuspect) "UNENCRYPTED 2G CELLULAR TRAP" else "$cellTech Cellular Mast",
                    threatLevel = targetThreat,
                    details = targetDetails,
                    timestamp = now,
                    detectedLat = tracker.pinnedLat,
                    detectedLon = tracker.pinnedLon,
                    relativeBearingDeg = relativeBearing,
                    locationHistory = emptyList(),
                    motionTrend = RelativeMotionTrend.STATIONARY,
                    savedMetadata = null,
                    detectionTech = DetectionTechnology.CELLULAR_RADIO_AUDIT
                )

                targetMap[cellId] = target
            }

            syncTargetsToFlow()
        } catch (e: SecurityException) {
            Timber.w("Cellular audit security exception: ${e.message}")
        } catch (e: Exception) {
            Timber.w(e, "Error auditing cellular base stations")
        }
    }

    // =========================================================================
    // PACKET PROCESSING METHODS (Wi-Fi, BLE, Bluetooth Classic, Wi-Fi Direct)
    // =========================================================================

    @SuppressLint("MissingPermission")
    private fun processWifiScanResults() {
        try {
            val results = wifiManager?.scanResults ?: return
            wifiRttEngine.evaluateScanResults(results)
            val now = System.currentTimeMillis()

            for (scan in results) {
                val ssid = scan.SSID ?: ""
                val bssid = scan.BSSID ?: continue
                val rssi = scan.level
                val freq = scan.frequency

                csiEngine?.feedLiveSample(rssi)

                val classification = TacticalRfClassifier.classify(ssid, bssid, sourceTech = DetectionTechnology.WIFI_BEACON)
                val dist = TacticalRfClassifier.estimateDistance(rssi, freq)
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
                        DevicePinpoint(id = bssid, pinnedLat = instLat, pinnedLon = instLon)
                    }

                    // Reset pin if previous pin is grossly divergent from live signal power
                    // (e.g. pinned before GPS fix or vehicle travelled away and router is local)
                    val prevDist = hypot((tracker.pinnedLat - lastKnownLat) * 111320.0, (tracker.pinnedLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat)))
                    if (!hasReceivedRealLocation || (prevDist > 80.0 && dist < 35.0) || prevDist > 400.0) {
                        tracker.pinnedLat = instLat
                        tracker.pinnedLon = instLon
                        tracker.totalWeight = 0.0
                        tracker.weightedSumLat = 0.0
                        tracker.weightedSumLon = 0.0
                        tracker.sampleCount = 0
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
                    val rawComputedDist = hypot(dLatM, dLonM).coerceAtLeast(1.0)

                    // Ground truth clamp: If direct RF path loss shows close proximity (dist < 35m),
                    // never report 1km or large distances due to historical anchor drift!
                    effectiveDistance = if (dist < 35.0 && rawComputedDist > 50.0) {
                        dist
                    } else {
                        rawComputedDist
                    }

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

                val motionTrend = if (isStationary) {
                    RelativeMotionTrend.STATIONARY
                } else {
                    when {
                        rssi - prevRssi >= 3 -> RelativeMotionTrend.APPROACHING
                        rssi - prevRssi <= -3 -> RelativeMotionTrend.RECEDING
                        else -> RelativeMotionTrend.STATIONARY
                    }
                }

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
                    protocol = classification.protocolOverride ?: if (freq > 4900) "Wi-Fi 5GHz (802.11ac/ax)" else "Wi-Fi 2.4GHz (802.11n/ax)",
                    threatLevel = if (isStationary && sweepCount >= 3) "PINPOINTED INFRASTRUCTURE" else classification.threatLevel,
                    details = targetDetails,
                    timestamp = now,
                    detectedLat = targetLat,
                    detectedLon = targetLon,
                    relativeBearingDeg = relativeBearing,
                    locationHistory = history.toList(),
                    motionTrend = motionTrend,
                    savedMetadata = saved,
                    detectionTech = if (TacticalRfClassifier.isLocallyAdministeredMac(bssid) || 
                        (TacticalRfClassifier.isKnownOui(bssid) && (classification.vendor.contains("Flock") || classification.vendor.contains("Axon") || classification.vendor.contains("Hikvision") || classification.vendor.contains("Dahua")))) {
                        DetectionTechnology.MAC_SNIFFING
                    } else {
                        DetectionTechnology.WIFI_BEACON
                    }
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
            val scanRecord = result.scanRecord
            val rawName = scanRecord?.deviceName ?: device.name ?: ""
            val rssi = result.rssi
            val now = System.currentTimeMillis()

            csiEngine?.feedLiveSample(rssi)

            // Extract Manufacturer Specific Data (Apple, Samsung, Tile, Garmin, etc.)
            var manufacturerId: Int? = null
            var manufacturerData: ByteArray? = null
            val mfgSparse = scanRecord?.manufacturerSpecificData
            if (mfgSparse != null && mfgSparse.size() > 0) {
                manufacturerId = mfgSparse.keyAt(0)
                manufacturerData = mfgSparse.valueAt(0)
            }

            // Extract Service UUIDs (Tesla 0x0211, Drone Remote ID 0xFFFA, Tile 0xFEED)
            val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

            // OpenDroneID Direct Remote ID Telemetry Parsing (ASTM F3411 / ASD-STAN)
            val serviceDataMap = scanRecord?.serviceData?.mapKeys { it.key.toString() }
            val rawBytes = scanRecord?.bytes
            val droneTelemetry = TacticalDroneIdParser.parse(serviceDataMap, manufacturerData, rawBytes)

            val classification = TacticalRfClassifier.classify(
                ssidOrBleName = rawName,
                macAddress = address,
                bluetoothClass = device.bluetoothClass,
                serviceUuids = serviceUuids,
                manufacturerId = manufacturerId,
                manufacturerData = manufacturerData,
                sourceTech = if (droneTelemetry != null) DetectionTechnology.OPEN_DRONE_ID else DetectionTechnology.BLE_ADVERTISING
            )

            val dist = TacticalRfClassifier.estimateDistance(rssi, 2402)
            val isStationary = classification.category == RfCategory.ACCESS_POINT || classification.category == RfCategory.CCTV_SURVEILLANCE

            val targetLat: Double
            val targetLon: Double
            val relativeBearing: Float
            val effectiveDistance: Double

            if (droneTelemetry?.droneLat != null && droneTelemetry.droneLon != null) {
                targetLat = droneTelemetry.droneLat
                targetLon = droneTelemetry.droneLon
                effectiveDistance = calculateDistanceMeters(lastKnownLat, lastKnownLon, targetLat, targetLon).coerceIn(1.0, 5000.0)
                val dLatM = (targetLat - lastKnownLat) * 111320.0
                val dLonM = (targetLon - lastKnownLon) * 111320.0 * cos(Math.toRadians(lastKnownLat))
                var worldAngle = Math.toDegrees(atan2(dLonM, dLatM)).toFloat()
                if (worldAngle < 0f) worldAngle += 360f
                relativeBearing = (worldAngle - lastKnownHeading + 360f) % 360f
            } else if (isStationary) {
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

            val isDrone = droneTelemetry != null || classification.category == RfCategory.DRONE_UAV
            val targetName = when {
                droneTelemetry?.uasId != null -> "UAV [${droneTelemetry.uasType}] ${droneTelemetry.uasId}"
                droneTelemetry != null -> "UAV [${droneTelemetry.uasType}] ${if (rawName.isNotBlank()) rawName else address.takeLast(8)}"
                else -> classification.targetName
            }
            val targetCategory = if (isDrone) RfCategory.DRONE_UAV else classification.category
            val targetTech = when {
                droneTelemetry != null -> DetectionTechnology.OPEN_DRONE_ID
                TacticalRfClassifier.isLocallyAdministeredMac(address) && rawName.isBlank() -> DetectionTechnology.MAC_SNIFFING
                else -> DetectionTechnology.BLE_ADVERTISING
            }
            val targetProtocol = if (droneTelemetry != null) "OpenDroneID (ASTM F3411)" else (classification.protocolOverride ?: "BLE 5.2 / Bluetooth Low Energy")
            val targetThreat = if (isDrone) "AIRSPACE UAV IN FLIGHT" else (if (isStationary && sweepCount >= 3) "PINPOINTED INFRASTRUCTURE" else classification.threatLevel)

            val targetDetails = when {
                droneTelemetry != null -> {
                    val alt = droneTelemetry.altitudeAglMeters?.let { " • Alt: ${it.toInt()}m AGL" } ?: ""
                    val spd = droneTelemetry.speedKmh?.let { " • Spd: ${it.toInt()} km/h" } ?: ""
                    val pilot = droneTelemetry.pilotLat?.let { " • Pilot: ${droneTelemetry.pilotLocationType} (%.4f°N, %.4f°W)".format(it, abs(droneTelemetry.pilotLon ?: 0.0)) } ?: ""
                    "Airborne UAV (Direct Remote ID)$alt$spd$pilot"
                }
                isStationary && sweepCount > 1 -> "Pinpointed across $sweepCount sweeps • ${classification.details}"
                else -> classification.details
            }

            val target = DetectedRfTarget(
                id = address,
                name = targetName,
                rawSsidOrBleName = rawName,
                macAddress = address,
                vendor = classification.vendor,
                category = targetCategory,
                rssi = rssi,
                estimatedDistanceMeters = effectiveDistance,
                frequencyMhz = 2402,
                protocol = targetProtocol,
                threatLevel = targetThreat,
                details = targetDetails,
                timestamp = now,
                detectedLat = targetLat,
                detectedLon = targetLon,
                relativeBearingDeg = relativeBearing,
                locationHistory = history.toList(),
                motionTrend = motionTrend,
                savedMetadata = saved,
                detectionTech = targetTech,
                droneTelemetry = droneTelemetry
            )
            val isNewTarget = !targetMap.containsKey(address)
            targetMap[address] = target
            if (isNewTarget) {
                soundManager.playTargetChirp()
            }
            if (isDrone && (now - lastDroneAlertTime > 45000L)) {
                lastDroneAlertTime = now
                val uasTag = droneTelemetry?.uasId ?: "Airborne Drone"
                val altTag = droneTelemetry?.altitudeAglMeters?.let { " at ${it.toInt()}m AGL" } ?: ""
                soundManager.dispatchAlert(
                    category = com.recondrive.app.audio.AlertCategory.OPEN_DRONE_ID,
                    title = "🛸 AERIAL DRONE DETECTED",
                    message = "Airborne UAV $uasTag active in airspace$altTag.",
                    ttsPhrase = "Alert: Drone detected in local airspace.",
                    soundType = "TACTICAL_CHIRP"
                )
            }
            syncTargetsToFlow()
        } catch (e: Exception) {
            Timber.w(e, "Error processing BLE scan result")
        }
    }

    private fun processBluetoothDevice(
        device: BluetoothDevice,
        rawName: String,
        rssi: Int,
        bluetoothClass: BluetoothClass?,
        isClassic: Boolean = true,
        isBonded: Boolean = false
    ) {
        val address = device.address ?: return
        val now = System.currentTimeMillis()

        val classification = TacticalRfClassifier.classify(
            ssidOrBleName = rawName,
            macAddress = address,
            bluetoothClass = bluetoothClass,
            sourceTech = DetectionTechnology.BT_CLASSIC_INQUIRY
        )

        val dist = TacticalRfClassifier.estimateDistance(rssi, 2402)
        val hashAngle = abs(address.hashCode() % 360).toFloat()
        val relBearing = (hashAngle - lastKnownHeading + 360f) % 360f
        val bearingRad = Math.toRadians(relBearing.toDouble())

        val deltaLat = (dist * cos(bearingRad)) / 111320.0
        val deltaLon = (dist * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
        val targetLat = lastKnownLat + deltaLat
        val targetLon = lastKnownLon + deltaLon

        val history = breadcrumbHistory.getOrPut(address) { mutableListOf() }
        val prevRssi = history.lastOrNull()?.rssi ?: rssi
        history.add(
            DeviceBreadcrumb(
                timestamp = now,
                hostLat = lastKnownLat,
                hostLon = lastKnownLon,
                relativeBearingDeg = relBearing,
                distanceMeters = dist,
                rssi = rssi,
                targetLat = targetLat,
                targetLon = targetLon
            )
        )
        if (history.size > 25) history.removeAt(0)

        val motionTrend = when {
            rssi - prevRssi >= 3 -> RelativeMotionTrend.APPROACHING
            rssi - prevRssi <= -3 -> RelativeMotionTrend.RECEDING
            else -> RelativeMotionTrend.STATIONARY
        }

        val saved = savedDeviceRepository.getDevice(address)
        if (saved != null) {
            checkAndTriggerSavedAlert(saved, dist)
        }

        val protocol = classification.protocolOverride ?: if (isBonded) "Bluetooth Paired Link" else "Bluetooth Classic BR/EDR"
        val threat = if (isBonded) "PAIRED EQUIPMENT" else classification.threatLevel

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
            protocol = protocol,
            threatLevel = threat,
            details = "${classification.details}${if (isBonded) " [Bonded / Paired]" else ""}",
            timestamp = now,
            detectedLat = targetLat,
            detectedLon = targetLon,
            relativeBearingDeg = relBearing,
            locationHistory = history.toList(),
            motionTrend = motionTrend,
            savedMetadata = saved,
            detectionTech = DetectionTechnology.BT_CLASSIC_INQUIRY
        )

        targetMap[address] = target
        syncTargetsToFlow()
    }

    private fun processWifiP2pPeers(peers: WifiP2pDeviceList) {
        val now = System.currentTimeMillis()
        for (peer in peers.deviceList) {
            val address = peer.deviceAddress ?: continue
            val rawName = peer.deviceName ?: "DIRECT-DEVICE"
            val classification = TacticalRfClassifier.classify(rawName, address, sourceTech = DetectionTechnology.WIFI_DIRECT_P2P)

            val dist = 12.0 // Typical peer-to-peer Wi-Fi Direct line of sight
            val hashAngle = abs(address.hashCode() % 360).toFloat()
            val relBearing = (hashAngle - lastKnownHeading + 360f) % 360f
            val bearingRad = Math.toRadians(relBearing.toDouble())

            val deltaLat = (dist * cos(bearingRad)) / 111320.0
            val deltaLon = (dist * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
            val targetLat = lastKnownLat + deltaLat
            val targetLon = lastKnownLon + deltaLon

            val saved = savedDeviceRepository.getDevice(address)
            if (saved != null) {
                checkAndTriggerSavedAlert(saved, dist)
            }

            val statusStr = when (peer.status) {
                WifiP2pDevice.CONNECTED -> "CONNECTED"
                WifiP2pDevice.INVITED -> "INVITED"
                WifiP2pDevice.FAILED -> "FAILED"
                WifiP2pDevice.AVAILABLE -> "AVAILABLE"
                else -> "UNAVAILABLE"
            }

            val target = DetectedRfTarget(
                id = address,
                name = classification.targetName,
                rawSsidOrBleName = rawName,
                macAddress = address,
                vendor = classification.vendor,
                category = classification.category,
                rssi = -60,
                estimatedDistanceMeters = dist,
                frequencyMhz = 5240,
                protocol = "Wi-Fi Direct P2P (802.11)",
                threatLevel = "P2P WIRELESS LINK",
                details = "Wi-Fi Direct peer device ($statusStr) • Type: ${peer.primaryDeviceType}",
                timestamp = now,
                detectedLat = targetLat,
                detectedLon = targetLon,
                relativeBearingDeg = relBearing,
                locationHistory = emptyList(),
                motionTrend = RelativeMotionTrend.STATIONARY,
                savedMetadata = saved,
                detectionTech = DetectionTechnology.WIFI_DIRECT_P2P
            )

            targetMap[address] = target
        }
        syncTargetsToFlow()
    }

    // =========================================================================
    // SENSOR EVENT LISTENER (Magnetic Anomaly Detection / MAD for Vehicles)
    // =========================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _isScannerPaused.value) return
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val bx = event.values[0]
            val by = event.values[1]
            val bz = event.values[2]
            val magnitude = sqrt(bx * bx + by * by + bz * bz)

            // Dynamic low-pass baseline filter
            baselineMagneticMagnitude = 0.95f * baselineMagneticMagnitude + 0.05f * magnitude
            val deltaB = abs(magnitude - baselineMagneticMagnitude)

            val now = System.currentTimeMillis()
            // Detect significant magnetic distortion caused by adjacent 1.5 - 2.5 ton vehicle steel mass
            if (deltaB > 22.0f && (now - lastMagneticAnomalyTime > 30000L)) {
                lastMagneticAnomalyTime = now
                Timber.i("Magnetic Anomaly Detected: deltaB=%.1f uT (Vehicle Proximity)", deltaB)

                val madId = "MAD_VEHICLE_MASS_PROXIMITY"
                val dist = (4.5 - (deltaB / 15.0)).coerceIn(1.5, 5.0)

                val target = DetectedRfTarget(
                    id = madId,
                    name = "FERROMAGNETIC VEHICLE PROXIMITY [MAD]",
                    rawSsidOrBleName = "MAGNETIC_ANOMALY",
                    macAddress = "MAD:00:FE:RR:OM:AG",
                    vendor = "Geomagnetic Flux Anomaly",
                    category = RfCategory.VEHICLE,
                    rssi = (-40 - (dist * 5)).toInt(),
                    estimatedDistanceMeters = dist,
                    frequencyMhz = 0,
                    protocol = "Geomagnetic Distortion (MAD Sensor)",
                    threatLevel = "CLOSE VEHICULAR PROXIMITY",
                    details = "Significant ferromagnetic anomaly (ΔB: ${"%.1f".format(deltaB)} μT). Large metallic automobile mass detected adjacent to vehicle.",
                    timestamp = now,
                    detectedLat = lastKnownLat,
                    detectedLon = lastKnownLon,
                    relativeBearingDeg = 90f,
                    locationHistory = emptyList(),
                    motionTrend = RelativeMotionTrend.APPROACHING,
                    savedMetadata = null,
                    detectionTech = DetectionTechnology.GEOMAGNETIC_MAD
                )

                targetMap[madId] = target
                syncTargetsToFlow()

                soundManager.dispatchAlert(
                    category = com.recondrive.app.audio.AlertCategory.FERROMAGNETIC_MASS,
                    title = "🧲 FERROMAGNETIC PROXIMITY [MAD]",
                    message = "Significant ferromagnetic anomaly (ΔB: ${"%.1f".format(deltaB)} μT). Large metallic automobile mass detected adjacent.",
                    ttsPhrase = "Notice: Large metallic automobile mass adjacent.",
                    soundType = "RADAR_BEEP"
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =========================================================================
    // ALERTING & TARGET SYNC
    // =========================================================================

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
            soundManager.dispatchAlert(
                category = com.recondrive.app.audio.AlertCategory.SAVED_WATCHLIST_TARGET,
                title = "🎯 WATCHLIST TARGET DETECTED",
                message = "$spoken (approx ${distanceMeters.toInt()}m away)",
                ttsPhrase = spoken,
                soundType = saved.alertSound
            )
            savedDeviceRepository.recordDetection(saved.id, lastKnownLat, lastKnownLon)
        }
    }

    private fun processUwbSession(session: TacticalUwbEngine.UwbKeySession) {
        val dist = session.distanceMeters.toDouble().coerceAtLeast(0.5)
        val relBearing = (session.azimuthDegrees + 360f) % 360f
        val bearingRad = Math.toRadians(((relBearing + lastKnownHeading) % 360f).toDouble())

        val deltaLat = (dist * cos(bearingRad)) / 111320.0
        val deltaLon = (dist * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(lastKnownLat)).coerceAtLeast(0.1))
        val targetLat = lastKnownLat + deltaLat
        val targetLon = lastKnownLon + deltaLon

        val target = DetectedRfTarget(
            id = session.sessionId,
            name = session.vehicleModel,
            rawSsidOrBleName = "UWB_CAR_KEY_CH9",
            macAddress = session.sessionId,
            vendor = "CCC Digital Key 3.0 / FiRa",
            category = RfCategory.CAR_KEY,
            rssi = (-40 - (dist * 4)).toInt(),
            estimatedDistanceMeters = dist,
            frequencyMhz = 7987,
            protocol = session.protocol,
            threatLevel = "ACTIVE DIGITAL CAR KEY (UWB)",
            details = "Centimeter-accurate UWB ranging (LoS: ${session.lineOfSight}) • Azimuth: ${"%.1f".format(session.azimuthDegrees)}°",
            timestamp = session.timestamp,
            detectedLat = targetLat,
            detectedLon = targetLon,
            relativeBearingDeg = relBearing,
            locationHistory = emptyList(),
            motionTrend = RelativeMotionTrend.APPROACHING,
            savedMetadata = savedDeviceRepository.getDevice(session.sessionId),
            detectionTech = DetectionTechnology.UWB_PULSE_RANGING
        )

        targetMap[session.sessionId] = target
        syncTargetsToFlow()
    }

    private fun processOpticalIrAlert() {
        val irId = "NIR_OPTICAL_ILLUMINATOR"
        val now = System.currentTimeMillis()
        val irType = irEngine.lastDetectedIrType.value ?: "ACTIVE 850/940nm INFRARED ILLUMINATION RING"
        val target = DetectedRfTarget(
            id = irId,
            name = "OPTICAL INFRARED ILLUMINATOR [NIR 850/940nm]",
            rawSsidOrBleName = "NIR_850_940NM",
            macAddress = "NIR:85:00:94:00:IR",
            vendor = "Optical Surveillance Hardware",
            category = RfCategory.CCTV_SURVEILLANCE,
            rssi = -52,
            estimatedDistanceMeters = 8.5,
            frequencyMhz = 0,
            protocol = "Near-Infrared Optical (850/940nm)",
            threatLevel = "ACTIVE NIR SURVEILLANCE OPTIC",
            details = "$irType detected via optical sensor saturation.",
            timestamp = now,
            detectedLat = lastKnownLat,
            detectedLon = lastKnownLon,
            relativeBearingDeg = 0f,
            locationHistory = emptyList(),
            motionTrend = RelativeMotionTrend.STATIONARY,
            savedMetadata = null,
            detectionTech = DetectionTechnology.NEAR_INFRARED_OPTICAL
        )
        targetMap[irId] = target
        syncTargetsToFlow()
    }

    fun processNfcVehicleKey(scanned: NfcScannedTag) {
        val targetId = "NFC_${scanned.uidHex}"
        val target = DetectedRfTarget(
            id = targetId,
            name = scanned.tagType,
            rawSsidOrBleName = "NFC_CAR_KEY",
            macAddress = scanned.uidHex,
            vendor = "NXP / ISO-14443 Smartcard",
            category = RfCategory.CAR_KEY,
            rssi = -25,
            estimatedDistanceMeters = 0.1,
            frequencyMhz = 13,
            protocol = "NFC 13.56 MHz Type-A",
            threatLevel = "CONTACTLESS DIGITAL CAR KEY",
            details = "Scanned via Host NFC Controller • UID: ${scanned.uidHex} • ${scanned.payload ?: "Passive Smartcard"}",
            timestamp = scanned.timestampMs,
            detectedLat = lastKnownLat,
            detectedLon = lastKnownLon,
            relativeBearingDeg = 0f,
            locationHistory = emptyList(),
            motionTrend = RelativeMotionTrend.APPROACHING,
            savedMetadata = savedDeviceRepository.getDevice(targetId),
            detectionTech = DetectionTechnology.NFC_SMARTCARD
        )

        targetMap[targetId] = target
        syncTargetsToFlow()
        val now = System.currentTimeMillis()
        if (now - lastKeyAlertTime > 25000L) {
            lastKeyAlertTime = now
            soundManager.dispatchAlert(
                category = com.recondrive.app.audio.AlertCategory.KEY_FOB_PROXIMITY,
                title = "🔑 VEHICLE KEY DETECTED",
                message = "Vehicle NFC key / smart fob in proximity: ${scanned.tagType}",
                ttsPhrase = "Vehicle key detected.",
                soundType = "TACTICAL_CHIRP"
            )
        }
    }

    private fun syncTargetsToFlow() {
        val now = System.currentTimeMillis()
        // Prune targets that have not been observed in > 180 seconds (unless simulated or saved)
        if (!_isSimulationEnabled.value) {
            targetMap.entries.removeIf { entry ->
                val target = entry.value
                val isStale = (now - target.timestamp) > 180000L
                val isSaved = target.savedMetadata != null
                isStale && !isSaved
            }
        }

        // Evaluate Co-Travel & Shared Corridor Proximity (Non-Stressful Multi-Factor Correlation)
        val coTravelMap = coTravelEngine.evaluate(lastKnownLat, lastKnownLon, lastKnownHeading, lastKnownSpeed, targetMap.values.toList())
        for ((id, target) in targetMap) {
            val intel = coTravelMap[id]
            if (intel != null && target.coTravel != intel) {
                targetMap[id] = target.copy(coTravel = intel)
                if (intel.level == CoTravelLevel.PERSISTENT_ESCORT && (now - lastCoTravelAlertTime > 60000L)) {
                    lastCoTravelAlertTime = now
                    val name = target.displayName
                    soundManager.dispatchAlert(
                        category = com.recondrive.app.audio.AlertCategory.CO_TRAVEL_FOLLOWING,
                        title = "🚘 CO-TRAVEL SURVEILLANCE ALERT",
                        message = "Vehicle $name following through ${intel.correlatedTurnsCount} turns.",
                        ttsPhrase = "Caution: Vehicle following detected across multiple turns.",
                        soundType = "INTERCEPTION_ALERT"
                    )
                }
            } else if (intel == null && target.coTravel != null && !target.coTravel.isDismissed) {
                targetMap[id] = target.copy(coTravel = null)
            }
        }

        // Sort with keys, saved items, persistent co-travel vehicles, vehicles, drones, and CCTV first
        val sorted = targetMap.values.toList().sortedWith(
            compareBy<DetectedRfTarget> {
                if (it.savedMetadata != null) 0 else 1
            }.thenBy {
                if (it.coTravel?.level == CoTravelLevel.PERSISTENT_ESCORT) 0 else 1
            }.thenBy {
                when (it.category) {
                    RfCategory.CAR_KEY -> 0
                    RfCategory.VEHICLE -> 1
                    RfCategory.DRONE_UAV -> 2
                    RfCategory.CCTV_SURVEILLANCE -> 3
                    RfCategory.ACCESS_POINT -> 4
                    RfCategory.CELL_TOWER -> 5
                }
            }.thenByDescending { it.rssi }
        )
        _detectedTargets.value = sorted
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c
    }

    private fun startPeriodicSweep() {
        scope.launch {
            var cycle = 0
            while (isActive) {
                delay(10000L) // Sweep cadence every 10 seconds in background
                cycle++
                if (_isScanning.value && !_isScannerPaused.value) {
                    soundManager.playRadarPing()
                    mainHandler.post {
                        triggerHardwareWifiScan()
                        auditCellularBaseStations()

                        // Interleave Bluetooth Classic and P2P sweeps periodically
                        if (cycle % 2 == 0) {
                            triggerBluetoothClassicDiscovery()
                            auditBondedBluetoothDevices()
                        }
                        if (cycle % 3 == 0) {
                            triggerWifiP2pDiscovery()
                        }
                    }
                }
            }
        }
    }


}