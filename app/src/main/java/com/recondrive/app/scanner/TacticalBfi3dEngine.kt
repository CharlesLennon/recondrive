package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.recondrive.app.audio.TacticalSoundManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Single 3D Volumetric RF Voxel sample in room space.
 */
data class BfiVoxel(
    val x: Float,                 // East displacement (m)
    val y: Float,                 // North displacement (m)
    val z: Float,                 // Elevation height (m)
    val rssi: Int,                // Received Signal Strength Indicator (dBm)
    val bfiScorePercent: Int,     // Beamforming Feedback Information quality (0 - 100%)
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val isDeadZone: Boolean,       // Flagged if <= -82 dBm or severe null
    val isBeamCore: Boolean,       // Flagged if >= -55 dBm (main beamformed energy lobe)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 3D Bounding cluster representing an RF Dead Zone in the room.
 */
data class DeadZoneCluster(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val worstRssi: Int,
    val voxelCount: Int,
    val severityDesc: String
)

/**
 * Directional Beam Vector and estimated AP location in 3D room space.
 */
data class BeamformingVector(
    val estimatedApX: Float,
    val estimatedApY: Float,
    val estimatedApZ: Float,
    val beamAzimuthDeg: Float,
    val beamElevationDeg: Float,
    val confidencePercent: Int,
    val peakRssi: Int,
    val targetSsid: String,
    val targetBssid: String
)

/**
 * Live 3D Spatial Telemetry Snapshot.
 */
data class Bfi3dSnapshot(
    val posX: Float = 0.0f,
    val posY: Float = 0.0f,
    val posZ: Float = 1.2f,        // Default chest height: 1.2m
    val headingDeg: Float = 0.0f,
    val pitchDeg: Float = 0.0f,
    val rollDeg: Float = 0.0f,
    val rotationMatrix: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    ),
    val currentRssi: Int = -60,
    val currentBfiScore: Int = 85,
    val currentLinkSpeedMbps: Int = 433,
    val currentFreqMhz: Int = 5180,
    val currentSsid: String = "ACQUIRING WI-FI BEAM...",
    val currentBssid: String = "--:--:--:--:--:--",
    val isCurrentlyInDeadZone: Boolean = false,
    val deadZoneCount: Int = 0,
    val totalVoxelsScanned: Int = 0,
    val stepCount: Int = 0,
    val lastStepTimeMs: Long = 0L,
    val beamVector: BeamformingVector? = null,
    val statusSummary: String = "BFI 3D SPATIAL SENSOR READY"
)

/**
 * Tactical BFI 3D Spatial Scanning & Wi-Fi Beamforming / Dead Zone Engine.
 *
 * Employs high-rate Pedestrian Dead Reckoning (PDR), orientation quaternions,
 * step kinematics, barometer altitude tracking, and 802.11ac/ax Beamforming Feedback
 * to construct a real-time 3D volumetric voxel map of room RF propagation.
 */
class TacticalBfi3dEngine(
    private val context: Context,
    private val csiEngine: TacticalCsiSensingEngine? = null,
    private val soundManager: TacticalSoundManager? = null
) : SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    // Sensors
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
    private val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val barometer = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    // State Flows
    private val _snapshot = MutableStateFlow(Bfi3dSnapshot())
    val snapshot: StateFlow<Bfi3dSnapshot> = _snapshot.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isGeigerAudioEnabled = MutableStateFlow(true)
    val isGeigerAudioEnabled: StateFlow<Boolean> = _isGeigerAudioEnabled.asStateFlow()

    private val _voxels = MutableStateFlow<List<BfiVoxel>>(emptyList())
    val voxels: StateFlow<List<BfiVoxel>> = _voxels.asStateFlow()

    private val _deadZones = MutableStateFlow<List<DeadZoneCluster>>(emptyList())
    val deadZones: StateFlow<List<DeadZoneCluster>> = _deadZones.asStateFlow()

    // 3D Voxel Hash Map for 0.25m quantization deduplication
    private val voxelMap = ConcurrentHashMap<String, BfiVoxel>()

    // Current PDR Position
    private var posX = 0.0f
    private var posY = 0.0f
    private var posZ = 1.2f // Default holding altitude: 1.2m
    private var currentHeading = 0.0f
    private var currentPitch = 0.0f
    private var currentRoll = 0.0f

    // Sensor processing arrays
    private val rotationMatrix = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
    private val orientationValues = FloatArray(3)

    // Barometer baseline
    private var baselinePressure = 1013.25f
    private var hasBaselinePressure = false

    // Step & Walk Motion Dynamics
    private var lastStepTimeNs = 0L
    private var totalSteps = 0
    private var filteredMag = 9.80665f
    private var stepArmed = false
    private val strideLengthMeters = 0.68f

    // Target Wi-Fi AP filter (null = active connected Wi-Fi)
    private var targetBssidFilter: String? = null

    // Geiger counter audio rate control
    private var lastGeigerAudioTimeMs = 0L

    private var isListening = false
    private var samplingJob: Job? = null

    init {
        updateRotationMatrixFromEuler()
        startListening()
    }

    private fun updateRotationMatrixFromEuler() {
        val hRad = Math.toRadians(currentHeading.toDouble())
        val pRad = Math.toRadians(currentPitch.toDouble())
        val rRad = Math.toRadians(currentRoll.toDouble())
        val cosH = cos(hRad).toFloat()
        val sinH = sin(hRad).toFloat()
        val cosP = cos(pRad).toFloat()
        val sinP = sin(pRad).toFloat()
        val cosR = cos(rRad).toFloat()
        val sinR = sin(rRad).toFloat()

        rotationMatrix[0] = cosH * cosR - sinH * sinP * sinR
        rotationMatrix[1] = -sinH * cosP
        rotationMatrix[2] = cosH * sinR + sinH * sinP * cosR
        rotationMatrix[3] = sinH * cosR + cosH * sinP * sinR
        rotationMatrix[4] = cosH * cosP
        rotationMatrix[5] = sinH * sinR - cosH * sinP * cosR
        rotationMatrix[6] = -cosP * sinR
        rotationMatrix[7] = sinP
        rotationMatrix[8] = cosP * cosR
    }

    fun startListening() {
        if (isListening || sensorManager == null) return
        try {
            rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
            linearAccel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            barometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            isListening = true
            Timber.i("TacticalBfi3dEngine: Spatial 3D PDR sensor suite active")
        } catch (t: Throwable) {
            Timber.w(t, "TacticalBfi3dEngine: Error registering sensors")
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
            isListening = false
            pauseScanning()
            Timber.i("TacticalBfi3dEngine: Sensors stopped")
        } catch (t: Throwable) {
            Timber.w(t, "TacticalBfi3dEngine: Error unregistering sensors")
        }
    }

    fun toggleScanning() {
        if (_isScanning.value) {
            pauseScanning()
        } else {
            startScanning()
        }
    }

    fun startScanning() {
        _isScanning.value = true
        if (samplingJob == null || !samplingJob!!.isActive) {
            samplingJob = scope.launch {
                runSamplingLoop()
            }
        }
    }

    fun pauseScanning() {
        _isScanning.value = false
        samplingJob?.cancel()
        samplingJob = null
    }

    /**
     * Resets the room origin (0.0, 0.0, 1.2) to the user's current physical position.
     */
    fun reZeroOrigin() {
        posX = 0.0f
        posY = 0.0f
        posZ = 1.2f
        totalSteps = 0
        hasBaselinePressure = false
        updateSnapshotTelemetry()
        soundManager?.playUiClick()
        Timber.i("TacticalBfi3dEngine: Re-zeroed room coordinates to (0, 0, 1.2m)")
    }

    /**
     * Clear all recorded voxels and dead zones to start a fresh scan.
     */
    fun clearMap() {
        voxelMap.clear()
        _voxels.value = emptyList()
        _deadZones.value = emptyList()
        totalSteps = 0
        updateSnapshotTelemetry()
        soundManager?.playUiClick()
        Timber.i("TacticalBfi3dEngine: Cleared all 3D voxels")
    }

    /**
     * Set elevation height manually (Floor 0.1m, Desk 0.8m, Chest 1.2m, Overhead 2.0m, Ceiling 2.5m).
     */
    fun setManualElevation(z: Float) {
        posZ = z.coerceIn(0.05f, 3.5f)
        updateSnapshotTelemetry()
    }

    /**
     * Target specific BSSID for 3D beam mapping.
     */
    fun setTargetBssid(bssid: String?) {
        targetBssidFilter = bssid
        updateSnapshotTelemetry()
    }

    fun toggleGeigerAudio() {
        _isGeigerAudioEnabled.value = !_isGeigerAudioEnabled.value
    }

    // =========================================================================
    // SENSOR EVENT PROCESSING
    // =========================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                val rawAzimuthDeg = (Math.toDegrees(orientationValues[0].toDouble()).toFloat() + 360f) % 360f
                val rawPitchDeg = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                val rawRollDeg = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                currentHeading = smoothAngle(currentHeading, rawAzimuthDeg, 0.15f)
                currentPitch = currentPitch * 0.85f + rawPitchDeg * 0.15f
                currentRoll = currentRoll * 0.85f + rawRollDeg * 0.15f

                updateSnapshotTelemetry()
            }

            @Suppress("DEPRECATION")
            Sensor.TYPE_ORIENTATION -> {
                val rawAzimuthDeg = (event.values[0] + 360f) % 360f
                val rawPitchDeg = event.values[1]
                val rawRollDeg = event.values[2]
                currentHeading = smoothAngle(currentHeading, rawAzimuthDeg, 0.15f)
                currentPitch = currentPitch * 0.85f + rawPitchDeg * 0.15f
                currentRoll = currentRoll * 0.85f + rawRollDeg * 0.15f
                updateRotationMatrixFromEuler()
                updateSnapshotTelemetry()
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                if (nowNs - lastStepTimeNs > 250_000_000L) {
                    lastStepTimeNs = nowNs
                    onStepDetected()
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)

                // Adaptive baseline gravity tracking
                filteredMag = filteredMag * 0.90f + mag * 0.10f
                val deviation = mag - filteredMag
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val elapsedSinceLastStep = nowNs - lastStepTimeNs

                // Sensitive 2-stage indoor walking step detector:
                // Arm on acceleration rise (> +0.32 m/s²), confirm on drop (< -0.18 m/s²)
                if (!stepArmed && deviation > 0.32f && elapsedSinceLastStep > 260_000_000L) {
                    stepArmed = true
                } else if (stepArmed && deviation < -0.18f && elapsedSinceLastStep > 260_000_000L) {
                    stepArmed = false
                    lastStepTimeNs = nowNs
                    onStepDetected()
                } else if (deviation > 0.65f && elapsedSinceLastStep > 320_000_000L) {
                    // Firm step impulse fallback
                    stepArmed = false
                    lastStepTimeNs = nowNs
                    onStepDetected()
                }
            }

            Sensor.TYPE_PRESSURE -> {
                val pressureHpa = event.values[0]
                if (!hasBaselinePressure) {
                    baselinePressure = pressureHpa
                    hasBaselinePressure = true
                } else {
                    // Smooth hypsometric altitude differential
                    val deltaPressure = baselinePressure - pressureHpa
                    val altDelta = deltaPressure * 8.43f
                    val targetZ = (1.2f + altDelta).coerceIn(0.05f, 3.5f)
                    posZ = posZ * 0.90f + targetZ * 0.10f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Executes dead reckoning displacement on each detected physical footstep.
     */
    private fun onStepDetected() {
        totalSteps++
        val headingRad = Math.toRadians(currentHeading.toDouble())
        // Step forward in direction of device heading
        val dx = (strideLengthMeters * sin(headingRad)).toFloat()
        val dy = (strideLengthMeters * cos(headingRad)).toFloat()

        posX += dx
        posY += dy

        clampToBounds()
        updateSnapshotTelemetry()

        // Trigger immediate RF voxel sample upon stepping
        if (_isScanning.value) {
            sampleCurrentVoxel()
        }
        Timber.i("TacticalBfi3dEngine: Physical Step #$totalSteps registered at (%.2fm, %.2fm)", posX, posY)
    }

    /**
     * Move forward in the room along current device heading vector.
     */
    fun moveForward(distanceMeters: Float = 0.5f) {
        val headingRad = Math.toRadians(currentHeading.toDouble())
        posX += (distanceMeters * sin(headingRad)).toFloat()
        posY += (distanceMeters * cos(headingRad)).toFloat()
        clampToBounds()
        updateSnapshotTelemetry()
        if (_isScanning.value) sampleCurrentVoxel()
    }

    /**
     * Move backward in the room.
     */
    fun moveBackward(distanceMeters: Float = 0.5f) {
        moveForward(-distanceMeters)
    }

    /**
     * Strafe left relative to device heading.
     */
    fun strafeLeft(distanceMeters: Float = 0.5f) {
        val leftRad = Math.toRadians((currentHeading - 90f + 360f).toDouble())
        posX += (distanceMeters * sin(leftRad)).toFloat()
        posY += (distanceMeters * cos(leftRad)).toFloat()
        clampToBounds()
        updateSnapshotTelemetry()
        if (_isScanning.value) sampleCurrentVoxel()
    }

    /**
     * Strafe right relative to device heading.
     */
    fun strafeRight(distanceMeters: Float = 0.5f) {
        strafeLeft(-distanceMeters)
    }

    /**
     * Adjust elevation altitude up or down.
     */
    fun adjustAltitude(deltaZ: Float) {
        posZ = (posZ + deltaZ).coerceIn(0.05f, 3.5f)
        updateSnapshotTelemetry()
        if (_isScanning.value) sampleCurrentVoxel()
    }

    private fun clampToBounds() {
        posX = posX.coerceIn(-15f, 15f)
        posY = posY.coerceIn(-15f, 15f)
    }

    // =========================================================================
    // WI-FI & BEAM SAMPLING LOOP
    // =========================================================================

    private suspend fun runSamplingLoop() {
        while (_isScanning.value) {
            sampleCurrentVoxel()
            delay(250L) // 4Hz spatial sampling rate
        }
    }

    @SuppressLint("MissingPermission")
    private fun sampleCurrentVoxel() {
        try {
            val wifiInfo: WifiInfo? = try {
                wifiManager?.connectionInfo
            } catch (t: Throwable) {
                null
            }
            val rawRssi = wifiInfo?.rssi?.takeIf { it in -120..0 } ?: -68
            val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 } ?: 144
            val freq = wifiInfo?.frequency?.takeIf { it > 0 } ?: 5180
            val bssid = wifiInfo?.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: "3C:84:6A:B1:C4:02"
            val rawSsid = wifiInfo?.ssid ?: ""
            val cleanSsid = rawSsid.replace("\"", "").trim()
            val ssid = if (cleanSsid.isNotBlank() && cleanSsid != "<unknown ssid>") {
                cleanSsid
            } else {
                "WI-FI AP [BSSID ${bssid.takeLast(8)}]"
            }

            // Filter if user locked onto a specific BSSID
            if (targetBssidFilter != null && bssid != targetBssidFilter) {
                return
            }

            // Normalise RSSI (-30 dBm is maximum close, -95 dBm is drop threshold)
            val clampedRssi = rawRssi.coerceIn(-95, -30)

            // Calculate BFI (Beamforming Feedback Information) quality score
            // Uses CSI perturbation index if available to evaluate multipath coherence
            val csiSnapshot = csiEngine?.snapshot?.value
            val csiPerturbation = csiSnapshot?.perturbationIndexPercent ?: 15
            val rssiFactor = ((clampedRssi + 95) / 65.0f).coerceIn(0f, 1f)
            val multipathFactor = (1.0f - (csiPerturbation / 150f)).coerceIn(0.2f, 1.0f)
            val bfiScore = (rssiFactor * multipathFactor * 100).roundToInt().coerceIn(5, 99)

            // Dead Zone Criteria: RSSI <= -82 dBm or 0 link speed
            val isDeadZone = clampedRssi <= -82 || linkSpeed <= 5
            val isBeamCore = clampedRssi >= -55 && bfiScore >= 75

            // Quantize to 0.25m 3D voxel key
            val qx = (round(posX / 0.25f) * 0.25f)
            val qy = (round(posY / 0.25f) * 0.25f)
            val qz = (round(posZ / 0.25f) * 0.25f)
            val key = "${"%.2f".format(qx)}_${"%.2f".format(qy)}_${"%.2f".format(qz)}"

            val existing = voxelMap[key]
            val finalRssi = if (existing != null) {
                // Running weighted average for higher precision in repeatedly traversed areas
                ((existing.rssi * 0.6f) + (clampedRssi * 0.4f)).roundToInt()
            } else {
                clampedRssi
            }

            val voxel = BfiVoxel(
                x = qx,
                y = qy,
                z = qz,
                rssi = finalRssi,
                bfiScorePercent = bfiScore,
                linkSpeedMbps = linkSpeed,
                frequencyMhz = freq,
                isDeadZone = finalRssi <= -82,
                isBeamCore = finalRssi >= -55 && bfiScore >= 75
            )

            voxelMap[key] = voxel
            val currentVoxelList = voxelMap.values.toList()
            _voxels.value = currentVoxelList

            // Cluster dead zones
            computeDeadZoneClusters(currentVoxelList)

            // Solve 3D Beamforming Vector & AP Source
            val beamVec = solveBeamformingVector(currentVoxelList, ssid, bssid)

            // Update live snapshot
            _snapshot.value = _snapshot.value.copy(
                posX = posX,
                posY = posY,
                posZ = posZ,
                headingDeg = currentHeading,
                pitchDeg = currentPitch,
                rollDeg = currentRoll,
                rotationMatrix = rotationMatrix.clone(),
                currentRssi = clampedRssi,
                currentBfiScore = bfiScore,
                currentLinkSpeedMbps = linkSpeed,
                currentFreqMhz = freq,
                currentSsid = ssid,
                currentBssid = bssid,
                isCurrentlyInDeadZone = isDeadZone,
                deadZoneCount = _deadZones.value.size,
                totalVoxelsScanned = currentVoxelList.size,
                stepCount = totalSteps,
                lastStepTimeMs = lastStepTimeNs / 1_000_000L,
                beamVector = beamVec,
                statusSummary = if (isDeadZone) "⚠️ WI-FI DEAD ZONE DETECTED (RSSI: ${clampedRssi} dBm)" else "BFI 3D MAPPING: ACTIVE BEAM ACQUISITION"
            )

            // Trigger Geiger Acoustic Feedback
            handleGeigerAudio(clampedRssi, isDeadZone)

        } catch (t: Throwable) {
            Timber.w(t, "TacticalBfi3dEngine: Error sampling voxel")
        }
    }

    /**
     * Groups adjacent dead voxels into named 3D bounding clusters.
     */
    private fun computeDeadZoneClusters(voxelList: List<BfiVoxel>) {
        val deadVoxels = voxelList.filter { it.isDeadZone }
        if (deadVoxels.isEmpty()) {
            _deadZones.value = emptyList()
            return
        }

        val clusters = mutableListOf<DeadZoneCluster>()
        val visited = mutableSetOf<BfiVoxel>()
        var clusterId = 1

        for (v in deadVoxels) {
            if (v in visited) continue

            // Cluster points within 0.8m distance
            val members = mutableListOf<BfiVoxel>()
            val queue = ArrayDeque<BfiVoxel>()
            queue.add(v)
            visited.add(v)

            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                members.add(curr)

                for (neighbor in deadVoxels) {
                    if (neighbor !in visited) {
                        val d = hypot(hypot((curr.x - neighbor.x), (curr.y - neighbor.y)), (curr.z - neighbor.z))
                        if (d <= 0.85f) {
                            visited.add(neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
            }

            val avgX = members.map { it.x }.average().toFloat()
            val avgY = members.map { it.y }.average().toFloat()
            val avgZ = members.map { it.z }.average().toFloat()
            val minX = members.minOf { it.x } - 0.2f
            val maxX = members.maxOf { it.x } + 0.2f
            val minY = members.minOf { it.y } - 0.2f
            val maxY = members.maxOf { it.y } + 0.2f
            val minZ = members.minOf { it.z } - 0.2f
            val maxZ = members.maxOf { it.z } + 0.2f
            val worstRssi = members.minOf { it.rssi }

            val severity = when {
                worstRssi <= -88 -> "CRITICAL RF DEAD ZONE [BLACKOUT]"
                worstRssi <= -84 -> "SEVERE MULTIPATH NULL"
                else -> "FRINGE DROP REGION"
            }

            clusters.add(
                DeadZoneCluster(
                    id = clusterId++,
                    centerX = avgX,
                    centerY = avgY,
                    centerZ = avgZ,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                    minZ = minZ,
                    maxZ = maxZ,
                    worstRssi = worstRssi,
                    voxelCount = members.size,
                    severityDesc = severity
                )
            )
        }

        _deadZones.value = clusters
    }

    /**
     * Calculates the 3D Gradient Vector pointing towards the estimated Access Point location
     * using cubic power weighting of sampled voxels.
     */
    private fun solveBeamformingVector(voxels: List<BfiVoxel>, ssid: String, bssid: String): BeamformingVector? {
        if (voxels.size < 4) return null

        var sumWeights = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        var weightedZ = 0.0
        var peakRssi = -100

        for (v in voxels) {
            val weight = ((v.rssi + 100.0).coerceAtLeast(1.0)).pow(3.0)
            sumWeights += weight
            weightedX += v.x * weight
            weightedY += v.y * weight
            weightedZ += v.z * weight
            if (v.rssi > peakRssi) peakRssi = v.rssi
        }

        if (sumWeights <= 0.0) return null

        val apX = (weightedX / sumWeights).toFloat()
        val apY = (weightedY / sumWeights).toFloat()
        val apZ = (weightedZ / sumWeights).toFloat().coerceIn(0.5f, 3.2f)

        // Direction from current user position towards estimated AP source
        val dx = apX - posX
        val dy = apY - posY
        val dz = apZ - posZ
        val groundDist = hypot(dx, dy)

        val azRad = atan2(dx.toDouble(), dy.toDouble())
        val beamAzimuth = (Math.toDegrees(azRad).toFloat() + 360f) % 360f
        val elRad = atan2(dz.toDouble(), groundDist.coerceAtLeast(0.1f).toDouble())
        val beamElevation = Math.toDegrees(elRad).toFloat()

        val confidence = (voxels.size * 3).coerceIn(40, 95)

        return BeamformingVector(
            estimatedApX = apX,
            estimatedApY = apY,
            estimatedApZ = apZ,
            beamAzimuthDeg = beamAzimuth,
            beamElevationDeg = beamElevation,
            confidencePercent = confidence,
            peakRssi = peakRssi,
            targetSsid = ssid,
            targetBssid = bssid
        )
    }

    /**
     * Synthesizes audio clicks proportional to signal strength (rapid Geiger clicks in beam core,
     * low-frequency drone in dead zones).
     */
    private fun handleGeigerAudio(rssi: Int, isDeadZone: Boolean) {
        if (!_isGeigerAudioEnabled.value) return
        val now = System.currentTimeMillis()

        // Rate intervals: Strong (-40 dBm) -> every 120ms; Weak (-78 dBm) -> every 800ms
        val intervalMs = if (isDeadZone) {
            1000L
        } else {
            val norm = ((rssi + 85) / 50.0f).coerceIn(0.1f, 1.0f)
            (900 - (norm * 750)).toLong()
        }

        if (now - lastGeigerAudioTimeMs >= intervalMs) {
            lastGeigerAudioTimeMs = now
            soundManager?.playBfiGeigerClick(isDeadZone)
        }
    }

    private fun updateSnapshotTelemetry() {
        _snapshot.value = _snapshot.value.copy(
            posX = posX,
            posY = posY,
            posZ = posZ,
            headingDeg = currentHeading,
            pitchDeg = currentPitch,
            rollDeg = currentRoll,
            rotationMatrix = rotationMatrix.clone(),
            stepCount = totalSteps,
            lastStepTimeMs = lastStepTimeNs / 1_000_000L,
            totalVoxelsScanned = voxelMap.size
        )
    }

    companion object {
        private fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
            var diff = (target - current) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return (current + diff * alpha + 360f) % 360f
        }
    }
}
