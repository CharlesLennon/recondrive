package com.recondrive.app.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class BarometerTelemetry(
    val isAvailable: Boolean = false,
    val currentHpa: Float = 1013.25f,
    val baselineHpa: Float = 1013.25f,
    val relativeAltitudeM: Float = 0.0f,
    val parkingFloor: String = "G",
    val isCabinPressureSpike: Boolean = false,
    val lastShockwaveDesc: String = "CABIN NOMINAL",
    val lastShockwaveTimeMs: Long = 0L,
    val baroTrend: String = "STEADY"
)

/**
 * Tactical Barometer Engine
 * Leverages high-precision MEMS piezoresistive pressure sensors (Bosch BMP380/390 resolution ~0.01 hPa / 8cm)
 * 1. Cabin Shockwave & Infrasound: Detects door slams, window cracks, and vehicle cabin intrusion (<120ms delta)
 * 2. Multi-Story Parking Garage Floor Tracker: Detects subterranean levels (B1, B2) and elevated decks (L1, L2, L3)
 * 3. Atmospheric Gradient & Weather Fronts: Identifies rapid barometric drops indicating incoming storms
 */
class TacticalBarometerEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _telemetry = MutableStateFlow(
        BarometerTelemetry(isAvailable = pressureSensor != null)
    )
    val telemetry: StateFlow<BarometerTelemetry> = _telemetry.asStateFlow()

    private var isListening = false
    private var baselinePressureHpa: Float? = null

    // Rolling history for fast impulse shockwaves (door slam, cabin breach)
    private data class PressureSample(val timestampMs: Long, val hpa: Float)
    private val rollingSamples = ConcurrentLinkedDeque<PressureSample>()
    private val shockwaveWindowMs = 150L
    private val shockwaveThresholdHpa = 0.28f // ~2.5 meters equivalent instantaneous air pulse

    // Long-term history for weather trend (last 15 minutes)
    private var trendAnchorHpa: Float = 1013.25f
    private var trendAnchorTimeMs: Long = System.currentTimeMillis()

    fun start() {
        if (isListening || pressureSensor == null) return
        sensorManager?.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_FASTEST)
        isListening = true
        Timber.i("TacticalBarometerEngine: Started high-precision pressure sensing (Available: true)")
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        Timber.i("TacticalBarometerEngine: Stopped pressure sensing")
    }

    fun calibrateBaseline() {
        val current = _telemetry.value.currentHpa
        baselinePressureHpa = current
        _telemetry.update {
            it.copy(
                baselineHpa = current,
                relativeAltitudeM = 0.0f,
                parkingFloor = "G"
            )
        }
        Timber.i("TacticalBarometerEngine: Calibrated baseline pressure to %.2f hPa".format(current))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return

        try {
            val currentHpa = event.values[0]
            val now = System.currentTimeMillis()

            if (baselinePressureHpa == null) {
                baselinePressureHpa = currentHpa
                trendAnchorHpa = currentHpa
                trendAnchorTimeMs = now
            }

            val base = baselinePressureHpa ?: currentHpa

            // Hypsometric relative altitude calculation:
            // h = 44330 * (1 - (P / P0)^(1 / 5.255))
            val altitudeRel = 44330f * (1.0f - (currentHpa / base).pow(1.0f / 5.255f))

            // Multi-Story Parking Deck Level:
            // 1 floor is approx 3.0m - 3.2m -> approx 0.38 hPa pressure drop per floor ascent
            val deltaHpa = base - currentHpa // positive when going up
            val floorIndex = (deltaHpa / 0.38f).roundToInt()
            val floorLabel = when {
                floorIndex == 0 -> "G"
                floorIndex > 0 -> "L$floorIndex"
                else -> "B${abs(floorIndex)}"
            }

            // Cabin Infrasound / Shockwave Detection (Window crack, door slam, airbag / cabin breach)
            rollingSamples.addLast(PressureSample(now, currentHpa))
            while (rollingSamples.isNotEmpty()) {
                val oldest = rollingSamples.peekFirst() ?: break
                if (now - oldest.timestampMs > shockwaveWindowMs) {
                    rollingSamples.pollFirst()
                } else {
                    break
                }
            }

            var isSpike = false
            var shockDesc = _telemetry.value.lastShockwaveDesc
            var shockTime = _telemetry.value.lastShockwaveTimeMs

            val oldestInWindow = rollingSamples.peekFirst()
            val latestInWindow = rollingSamples.peekLast()
            if (oldestInWindow != null && latestInWindow != null && oldestInWindow != latestInWindow) {
                val impulseDelta = currentHpa - oldestInWindow.hpa
                if (abs(impulseDelta) >= shockwaveThresholdHpa) {
                    isSpike = true
                    shockTime = now
                    val dir = if (impulseDelta > 0) "INWARD COMPRESSION" else "DECOMPRESSION / DRAFT"
                    shockDesc = "CABIN SHOCK: %+.2f hPa (%s)".format(impulseDelta, dir)
                    Timber.w("TacticalBarometerEngine: $shockDesc")
                }
            }

            // Clear spike flag after 2.5 seconds
            if (!isSpike && (now - shockTime) > 2500L) {
                shockDesc = "CABIN NOMINAL"
            }

            // Barometric Trend (Update every 60s)
            var trend = _telemetry.value.baroTrend
            if (now - trendAnchorTimeMs > 60_000L) {
                val trendDelta = currentHpa - trendAnchorHpa
                trend = when {
                    trendDelta > 0.5f -> "RISING (CLEAR/FAIR)"
                    trendDelta < -0.8f -> "RAPID DROP (STORM/RAIN)"
                    trendDelta < -0.3f -> "FALLING (UNSETTLED)"
                    else -> "STEADY"
                }
                trendAnchorHpa = currentHpa
                trendAnchorTimeMs = now
            }

            _telemetry.update {
                it.copy(
                    isAvailable = true,
                    currentHpa = currentHpa,
                    baselineHpa = base,
                    relativeAltitudeM = altitudeRel,
                    parkingFloor = floorLabel,
                    isCabinPressureSpike = isSpike || (now - shockTime < 1800L),
                    lastShockwaveDesc = shockDesc,
                    lastShockwaveTimeMs = shockTime,
                    baroTrend = trend
                )
            }
        } catch (e: Throwable) {
            Timber.w(e, "Error processing barometer sensor event")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
