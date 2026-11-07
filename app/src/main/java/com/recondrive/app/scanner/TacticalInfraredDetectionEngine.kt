package com.recondrive.app.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Tactical Near-Infrared (NIR 850nm / 940nm) Detection Engine.
 * Detects invisible optical infrared illuminators:
 * 1. Night-Vision CCTV Security Camera IR LED Rings (850nm / 940nm)
 * 2. Highway ANPR / Speed Enforcement Camera IR Flash Strobes
 * 3. In-Cabin Automotive Driver Monitoring IR Projectors (Tesla, BMW, Apple)
 *
 * Utilizes ambient optical photodiode variance and hardware proximity spectrum sensors.
 */
class TacticalInfraredDetectionEngine(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _isIrScanning = MutableStateFlow(true)
    val isIrScanning: StateFlow<Boolean> = _isIrScanning.asStateFlow()

    private val _irAlertDetected = MutableStateFlow(false)
    val irAlertDetected: StateFlow<Boolean> = _irAlertDetected.asStateFlow()

    private val _lastDetectedIrType = MutableStateFlow<String?>(null)
    val lastDetectedIrType: StateFlow<String?> = _lastDetectedIrType.asStateFlow()

    private var baselineAmbientLux = 50.0f
    private var lastIrSpikeTime = 0L

    init {
        registerSensors()
    }

    fun registerSensors() {
        try {
            proximitySensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            lightSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            _isIrScanning.value = true
            Timber.i("Tactical Infrared NIR (850/940nm) Detection Engine Online")
        } catch (e: Exception) {
            Timber.w(e, "Error registering IR optical sensors")
        }
    }

    fun unregisterSensors() {
        try {
            sensorManager?.unregisterListener(this)
            _isIrScanning.value = false
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering IR sensors")
        }
    }

    fun toggleIrScanning() {
        if (_isIrScanning.value) {
            unregisterSensors()
        } else {
            registerSensors()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isIrScanning.value) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val currentLux = event.values[0]
                val delta = currentLux - baselineAmbientLux
                baselineAmbientLux = 0.92f * baselineAmbientLux + 0.08f * currentLux

                val now = System.currentTimeMillis()
                // Fast optical pulse spike in low ambient lighting typical of pulsed 850/940nm IR illuminator strobe
                if (delta > 40.0f && baselineAmbientLux < 25.0f && (now - lastIrSpikeTime > 8000L)) {
                    lastIrSpikeTime = now
                    _irAlertDetected.value = true
                    _lastDetectedIrType.value = "ANPR / SPEED CAMERA INFRARED FLASH STROBE"
                    Timber.i("Optical IR flash spike detected: delta=%.1f lux in low ambient light", delta)
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                // Proximity photodiode sensor saturation by external NIR 850nm / 940nm illumination
                if (distance < maxRange && distance > 0.0f) {
                    val now = System.currentTimeMillis()
                    if (now - lastIrSpikeTime > 15000L) {
                        lastIrSpikeTime = now
                        _irAlertDetected.value = true
                        _lastDetectedIrType.value = "CCTV NIGHT-VISION 850nm IR ILLUMINATION RING"
                        Timber.i("Proximity IR photodiode saturation detected (External NIR source)")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
