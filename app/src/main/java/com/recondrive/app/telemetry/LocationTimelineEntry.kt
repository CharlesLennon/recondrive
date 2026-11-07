package com.recondrive.app.telemetry

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tactical Telemetry Log Entry representing a single snapshot in the location timeline.
 */
data class LocationTimelineEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val speedKmh: Float,
    val gpsBearing: Float, // Satellite course over ground 0..360°
    val deviceHeading: Float, // Handset compass azimuth 0..360°
    val accuracyMeters: Float = 0f,
    val source: String = "GPS_FUSED"
) {
    /**
     * Angular drift between GPS velocity vector (course) and handset compass orientation.
     * Normalized between -180° and +180°.
     * Positive = GPS moving right of device heading; Negative = moving left.
     */
    val driftAngleDeg: Float
        get() {
            var diff = (gpsBearing - deviceHeading) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return diff
        }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val cardinalGpsBearing: String
        get() = bearingToCardinal(gpsBearing)

    val cardinalDeviceHeading: String
        get() = bearingToCardinal(deviceHeading)

    companion object {
        fun bearingToCardinal(deg: Float): String {
            val normalized = (deg % 360f + 360f) % 360f
            return when (((normalized + 22.5f) / 45f).toInt() % 8) {
                0 -> "N"
                1 -> "NE"
                2 -> "E"
                3 -> "SE"
                4 -> "S"
                5 -> "SW"
                6 -> "W"
                7 -> "NW"
                else -> "N"
            }
        }
    }
}
