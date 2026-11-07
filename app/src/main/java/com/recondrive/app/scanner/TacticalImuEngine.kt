package com.recondrive.app.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

data class ImuTelemetry(
    val isAvailable: Boolean = false,
    val lateralG: Float = 0.0f,
    val longitudinalG: Float = 0.0f,
    val verticalG: Float = 1.0f,
    val peakG: Float = 0.0f,
    val launchTimerState: String = "IDLE", // IDLE, ARMED, RUNNING, FINISHED
    val time0to30Sec: Float? = null,
    val time0to60Sec: Float? = null,
    val time0to100KmhSec: Float? = null,
    val isDrifting: Boolean = false,
    val driftAngleDeg: Float = 0.0f,
    val lastPotholeG: Float = 0.0f,
    val lastPotholeDesc: String = "ROAD SURFACE NOMINAL",
    val lastPotholeTimeMs: Long = 0L
)

/**
 * Tactical Inertial Measurement Unit (IMU) Engine
 * Leverages 6-Axis High-Rate MEMS Sensors (Accelerometer + Gyroscope):
 * 1. Tactical G-Force Skidpad: Lateral cornering Gs, braking deceleration, acceleration grip
 * 2. 0-60 MPH / 0-100 KM/H Precision Drag Launch Timer: Auto-arms at stop, logs sub-millisecond splits
 * 3. Vehicle Drift Angle & Yaw Slip: Compares gyro yaw rate against GPS trajectory curvature
 * 4. Pothole & Road Seismograph: High-G vertical transient detector (flags rough asphalt & hazards)
 */
class TacticalImuEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _telemetry = MutableStateFlow(
        ImuTelemetry(isAvailable = accelerometer != null)
    )
    val telemetry: StateFlow<ImuTelemetry> = _telemetry.asStateFlow()

    private var isListening = false
    private val gConstant = 9.80665f

    // Low-pass filter coefficients for steady-state G-force
    private var filteredAx = 0f
    private var filteredAy = 0f
    private var filteredAz = gConstant
    private val alpha = 0.18f

    // Session Peak G
    private var sessionPeakG = 0f

    // Launch Timer Variables
    private var launchStartTimeNanos = 0L
    private var armed = false
    private var running = false
    private var recorded0to30: Float? = null
    private var recorded0to60: Float? = null
    private var recorded0to100Kmh: Float? = null

    // Gyro Yaw & Drift Tracking
    private var lastGyroYawRate = 0f
    private var lastHeading = 0f
    private var lastHeadingTimeMs = 0L

    fun start() {
        try {
            if (isListening || accelerometer == null) return
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            isListening = true
            Timber.i("TacticalImuEngine: High-rate 6-axis IMU tracking active")
        } catch (t: Throwable) {
            Timber.w(t, "TacticalImuEngine: Failed to register IMU sensors")
        }
    }

    fun stop() {
        try {
            if (!isListening) return
            sensorManager?.unregisterListener(this)
            isListening = false
            Timber.i("TacticalImuEngine: IMU tracking stopped")
        } catch (t: Throwable) {
            Timber.w(t, "TacticalImuEngine: Failed to unregister IMU sensors")
        }
    }

    fun resetLaunchTimer() {
        armed = false
        running = false
        recorded0to30 = null
        recorded0to60 = null
        recorded0to100Kmh = null
        _telemetry.update {
            it.copy(
                launchTimerState = "IDLE",
                time0to30Sec = null,
                time0to60Sec = null,
                time0to100KmhSec = null
            )
        }
    }

    fun updateVehicleSpeed(speedKmh: Float, headingDeg: Float) {
        val speedMph = speedKmh * 0.621371f
        val nowMs = System.currentTimeMillis()

        // 1. Auto-Arm Drag Launch Timer when stopped
        if (speedMph < 1.0f && !running) {
            if (!armed) {
                armed = true
                _telemetry.update { it.copy(launchTimerState = "ARMED (STANDBY AT 0 MPH)") }
            }
        } else if (armed && !running && speedMph >= 1.5f) {
            // Punch detected! Start clock
            armed = false
            running = true
            launchStartTimeNanos = SystemClock.elapsedRealtimeNanos()
            _telemetry.update { it.copy(launchTimerState = "LAUNCHING!") }
        }

        // 2. Track Speed Thresholds
        if (running) {
            val elapsedSec = (SystemClock.elapsedRealtimeNanos() - launchStartTimeNanos) / 1_000_000_000f

            if (speedMph >= 30f && recorded0to30 == null) {
                recorded0to30 = elapsedSec
            }
            if (speedMph >= 60f && recorded0to60 == null) {
                recorded0to60 = elapsedSec
            }
            if (speedKmh >= 100f && recorded0to100Kmh == null) {
                recorded0to100Kmh = elapsedSec
                running = false
                _telemetry.update {
                    it.copy(
                        launchTimerState = "COMPLETE: %.2fs (0-60)".format(recorded0to60 ?: elapsedSec),
                        time0to30Sec = recorded0to30,
                        time0to60Sec = recorded0to60,
                        time0to100KmhSec = recorded0to100Kmh
                    )
                }
            } else {
                _telemetry.update {
                    it.copy(
                        time0to30Sec = recorded0to30,
                        time0to60Sec = recorded0to60,
                        time0to100KmhSec = recorded0to100Kmh
                    )
                }
            }
        }

        // 3. Drift Angle Estimation
        if (lastHeadingTimeMs > 0L && nowMs - lastHeadingTimeMs in 80L..500L && speedKmh > 20f) {
            val dt = (nowMs - lastHeadingTimeMs) / 1000f
            var deltaHeading = headingDeg - lastHeading
            while (deltaHeading > 180f) deltaHeading -= 360f
            while (deltaHeading < -180f) deltaHeading += 360f
            val gpsHeadingRate = deltaHeading / dt // deg/sec

            val gyroYawDeg = Math.toDegrees(lastGyroYawRate.toDouble()).toFloat()
            val slipDiscrepancy = abs(gyroYawDeg - gpsHeadingRate)

            val isDrift = slipDiscrepancy > 14.0f && abs(gyroYawDeg) > 12.0f
            _telemetry.update {
                it.copy(
                    isDrifting = isDrift,
                    driftAngleDeg = slipDiscrepancy.coerceAtMost(45f)
                )
            }
        }
        lastHeading = headingDeg
        lastHeadingTimeMs = nowMs
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        try {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val rawX = event.values[0]
                    val rawY = event.values[1]
                    val rawZ = event.values[2]

                    // Low-pass filter
                    filteredAx = alpha * rawX + (1 - alpha) * filteredAx
                    filteredAy = alpha * rawY + (1 - alpha) * filteredAy
                    filteredAz = alpha * rawZ + (1 - alpha) * filteredAz

                    // In phone portrait orientation mounted in vehicle:
                    // X = Lateral force (left/right turn G)
                    // Y = Longitudinal force (throttle accel / braking deceleration G)
                    // Z = Vertical force (gravity + road shocks)
                    val latG = (filteredAx / gConstant)
                    val longG = (filteredAy / gConstant)
                    val vertG = (filteredAz / gConstant)

                    val currentTotalG = kotlin.math.sqrt(latG * latG + longG * longG)
                    sessionPeakG = max(sessionPeakG, currentTotalG)

                    // High-pass dynamic transient for Pothole / Road Impact Detection
                    val zDelta = abs(rawZ - filteredAz) / gConstant
                    val now = System.currentTimeMillis()
                    var potholeG = _telemetry.value.lastPotholeG
                    var potholeDesc = _telemetry.value.lastPotholeDesc
                    var potholeTime = _telemetry.value.lastPotholeTimeMs

                    if (zDelta > 1.65f) { // Vertical shock threshold
                        potholeG = zDelta
                        potholeTime = now
                        potholeDesc = "POTHOLE IMPACT: %.1fG VERTICAL SHOCK".format(zDelta)
                        Timber.w("TacticalImuEngine: $potholeDesc")
                    } else if (now - potholeTime > 3500L) {
                        potholeDesc = "ROAD SURFACE NOMINAL"
                    }

                    _telemetry.update {
                        it.copy(
                            isAvailable = true,
                            lateralG = latG,
                            longitudinalG = longG,
                            verticalG = vertG,
                            peakG = sessionPeakG,
                            lastPotholeG = potholeG,
                            lastPotholeDesc = potholeDesc,
                            lastPotholeTimeMs = potholeTime
                        )
                    }
                }

                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroYawRate = event.values[2] // Z axis rotational velocity (rad/s)
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "TacticalImuEngine: Error processing IMU sensor event")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
