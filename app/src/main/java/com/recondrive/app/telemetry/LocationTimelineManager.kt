package com.recondrive.app.telemetry

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * In-memory & real-time Tactical Location Timeline Engine.
 * Logs speed, heading, GPS course, altitude, and drift angle with high resolution.
 * Provides statistics (max speed, avg speed, total fixes, drift telemetry).
 */
class LocationTimelineManager(private val context: Context) {

    private val maxCapacity = 500
    private val buffer = LinkedList<LocationTimelineEntry>()

    private val _timelineEntries = MutableStateFlow<List<LocationTimelineEntry>>(emptyList())
    val timelineEntries: StateFlow<List<LocationTimelineEntry>> = _timelineEntries.asStateFlow()

    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _maxSpeedKmh = MutableStateFlow(0f)
    val maxSpeedKmh: StateFlow<Float> = _maxSpeedKmh.asStateFlow()

    private val _avgSpeedKmh = MutableStateFlow(0f)
    val avgSpeedKmh: StateFlow<Float> = _avgSpeedKmh.asStateFlow()

    private val _totalDistanceMeters = MutableStateFlow(0.0)
    val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

    private var lastRecordedLocation: Location? = null
    private var speedSumKmh = 0.0
    private var speedCount = 0

    // Anti-jitter & road alignment tracking
    private var smoothedLat: Double = 0.0
    private var smoothedLon: Double = 0.0
    private var stationaryAnchorLocation: Location? = null
    private var lastStableBearing: Float = 0f

    /**
     * Ingest a new location fix combined with current device heading.
     * Applies high-accuracy gating, stationary deadband, and road-smoothing filter
     * so timeline tracks never jitter or criss-cross across roads.
     */
    fun recordTelemetryPoint(
        location: Location,
        deviceHeading: Float
    ) {
        if (!_isLoggingEnabled.value) return

        // 1. Strict Accuracy Gate: Discard low-accuracy fixes that jump across roads/buildings
        if (location.hasAccuracy() && location.accuracy > 20.0f) {
            return
        }

        val speedKmh = location.speed * 3.6f
        val now = System.currentTimeMillis()
        val lastLoc = lastRecordedLocation

        // 2. Teleport / Multipath Glitch Rejection & Stationary Deadband
        if (lastLoc != null) {
            val dist = lastLoc.distanceTo(location)
            val timeDiffMs = now - (buffer.peekFirst()?.timestamp ?: 0L)
            val dtSec = (timeDiffMs / 1000.0).coerceAtLeast(0.1)
            val impliedSpeedKmh = (dist / dtSec) * 3.6
            if (impliedSpeedKmh > 140.0) {
                // Impossible jump (e.g. cellular tower triangulation jump), reject
                return
            }

            // 3. Automotive Stationary Deadband (Prevents criss-crossing roads while stopped)
            if (speedKmh < 1.8f) {
                val anchor = stationaryAnchorLocation ?: lastLoc.also { stationaryAnchorLocation = it }
                val distFromAnchor = anchor.distanceTo(location)
                if (distFromAnchor < 12.0f) {
                    // Within stationary jitter radius; suppress jitter points that bounce across road lanes
                    return
                } else {
                    // Vehicle genuinely moved > 12m away from stationary anchor
                    stationaryAnchorLocation = null
                }
            } else {
                stationaryAnchorLocation = null
            }

            // 4. Moving Throttle: Throttle micro-points within 2.5m to keep track clean
            if (dist < 2.5f && timeDiffMs < 2000L) {
                return
            }

            if (dist > 1.0f) {
                _totalDistanceMeters.value += dist.toDouble()
            }
        } else {
            stationaryAnchorLocation = location
        }

        lastRecordedLocation = location

        // 5. Exponential Coordinate Filter (Smooths road trajectory, eliminates lateral antenna wobble)
        if (smoothedLat == 0.0 || smoothedLon == 0.0) {
            smoothedLat = location.latitude
            smoothedLon = location.longitude
        } else {
            val alpha = if (speedKmh > 5.0f) 0.75 else 0.40
            smoothedLat = alpha * location.latitude + (1.0 - alpha) * smoothedLat
            smoothedLon = alpha * location.longitude + (1.0 - alpha) * smoothedLon
        }

        // 6. Bearing Stability (Hold road heading when stopped instead of spinning across lanes)
        val gpsBearing = when {
            location.hasBearing() && speedKmh > 2.5f -> {
                lastStableBearing = location.bearing
                location.bearing
            }
            lastStableBearing != 0f -> lastStableBearing
            else -> deviceHeading
        }

        val entry = LocationTimelineEntry(
            id = now,
            timestamp = now,
            latitude = smoothedLat,
            longitude = smoothedLon,
            altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
            speedKmh = speedKmh,
            gpsBearing = gpsBearing,
            deviceHeading = deviceHeading,
            accuracyMeters = location.accuracy,
            source = location.provider ?: "GPS"
        )

        synchronized(buffer) {
            buffer.addFirst(entry)
            if (buffer.size > maxCapacity) {
                buffer.removeLast()
            }
            _timelineEntries.value = ArrayList(buffer)
        }

        // Update statistics
        if (speedKmh > _maxSpeedKmh.value) {
            _maxSpeedKmh.value = speedKmh
        }
        if (speedKmh > 1.0f) {
            speedSumKmh += speedKmh
            speedCount++
            _avgSpeedKmh.value = (speedSumKmh / speedCount).toFloat()
        }
    }

    fun toggleLogging() {
        _isLoggingEnabled.value = !_isLoggingEnabled.value
        Timber.i("Location timeline logging toggled: ${_isLoggingEnabled.value}")
    }

    fun clearTimeline() {
        synchronized(buffer) {
            buffer.clear()
            _timelineEntries.value = emptyList()
        }
        _maxSpeedKmh.value = 0f
        _avgSpeedKmh.value = 0f
        _totalDistanceMeters.value = 0.0
        speedSumKmh = 0.0
        speedCount = 0
        lastRecordedLocation = null
        smoothedLat = 0.0
        smoothedLon = 0.0
        stationaryAnchorLocation = null
        lastStableBearing = 0f
        Timber.i("Location timeline cleared")
    }

    /**
     * Exports recorded timeline into standard GPX format string.
     */
    fun exportToGpx(): String {
        val entries = timelineEntries.value.reversed()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"ReconDrive Tactical Navigator\">\n")
        sb.append("  <trk>\n    <name>ReconDrive Telemetry Track</name>\n    <trkseg>\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        for (e in entries) {
            sb.append("      <trkpt lat=\"${e.latitude}\" lon=\"${e.longitude}\">\n")
            sb.append("        <ele>${e.altitudeMeters}</ele>\n")
            sb.append("        <time>${sdf.format(Date(e.timestamp))}</time>\n")
            sb.append("        <extensions>\n")
            sb.append("          <speed>${e.speedKmh / 3.6f}</speed>\n")
            sb.append("          <course>${e.gpsBearing}</course>\n")
            sb.append("          <deviceHeading>${e.deviceHeading}</deviceHeading>\n")
            sb.append("          <drift>${e.driftAngleDeg}</drift>\n")
            sb.append("        </extensions>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }
}
