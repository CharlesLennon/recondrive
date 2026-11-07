package com.recondrive.app.sensor

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
 * Tactical Handset Device Orientation Manager.
 * Ingests hardware rotation vector / magnetic compass sensors to compute the real physical
 * handset azimuth / device heading relative to magnetic/true north in real-time.
 */
class DeviceOrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _deviceHeading = MutableStateFlow(0f) // 0..360°
    val deviceHeading: StateFlow<Float> = _deviceHeading.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var smoothedHeading = 0f
    private var isListening = false

    fun startListening() {
        if (isListening || sensorManager == null || rotationSensor == null) return
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        isListening = true
        Timber.i("DeviceOrientationManager started listening using ${rotationSensor.name}")
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        Timber.i("DeviceOrientationManager stopped listening")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                val rawAzimuthRad = orientationValues[0]
                val rawAzimuthDeg = (Math.toDegrees(rawAzimuthRad.toDouble()).toFloat() + 360f) % 360f
                val rawPitchDeg = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                val rawRollDeg = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                // Low-pass exponential smoothing across 0/360 boundary
                smoothedHeading = smoothAngle(smoothedHeading, rawAzimuthDeg, alpha = 0.20f)
                _deviceHeading.value = smoothedHeading
                _pitch.value = rawPitchDeg
                _roll.value = rawRollDeg
            }
            @Suppress("DEPRECATION")
            Sensor.TYPE_ORIENTATION -> {
                val rawAzimuthDeg = (event.values[0] + 360f) % 360f
                smoothedHeading = smoothAngle(smoothedHeading, rawAzimuthDeg, alpha = 0.20f)
                _deviceHeading.value = smoothedHeading
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Smooths angular transitions handling wrap-around at 0° / 360°.
         */
        fun smoothAngle(prev: Float, current: Float, alpha: Float): Float {
            var diff = current - prev
            while (diff < -180f) diff += 360f
            while (diff > 180f) diff -= 360f
            val result = prev + alpha * diff
            return (result + 360f) % 360f
        }
    }
}
