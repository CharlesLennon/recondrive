package com.recondrive.app.scanner

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Tactical Co-Travel & Shared Route Proximity Engine.
 *
 * Implements a conservative, anti-false-positive correlation engine that
 * tracks vehicles traveling along the same corridor without causing unnecessary
 * alarm or stress.
 *
 * Distinguishes everyday highway commuters from correlated multi-turn escorts:
 * - Filters out stationary infrastructure (APs, CCTV, cell towers).
 * - Ignores red lights, queues, and parking lots via a minimum speed gate (>18 km/h).
 * - Evaluates turn maneuver correlation (>35° course changes).
 * - Provides single-tap instant dismissal for confirmed normal traffic.
 */
class TacticalCoTravelEngine {

    private data class HostManeuver(
        val id: Long,
        val timestamp: Long,
        val lat: Double,
        val lon: Double,
        val headingBefore: Float,
        val headingAfter: Float
    )

    private data class TargetTrack(
        val id: String,
        val firstSeenMs: Long,
        var lastSeenMs: Long,
        var startLat: Double,
        var startLon: Double,
        var lastLat: Double,
        var lastLon: Double,
        var accumulatedDistanceMeters: Double = 0.0,
        var observationCount: Int = 1,
        val correlatedManeuverIds: MutableSet<Long> = mutableSetOf(),
        var isDismissed: Boolean = false
    )

    private val targetTracks = ConcurrentHashMap<String, TargetTrack>()
    private val hostManeuvers = mutableListOf<HostManeuver>()
    private var maneuverIdCounter = 1L

    private var lastHostLat = 0.0
    private var lastHostLon = 0.0
    private var lastHostHeading = 0f
    private var isFirstHostFix = true

    /**
     * Evaluates live targets against host vehicle movement.
     * Returns an updated map of targetId -> CoTravelIntel.
     */
    fun evaluate(
        hostLat: Double,
        hostLon: Double,
        hostHeading: Float,
        hostSpeedKmh: Float,
        targets: List<DetectedRfTarget>
    ): Map<String, CoTravelIntel> {
        val now = System.currentTimeMillis()
        val results = mutableMapOf<String, CoTravelIntel>()

        if (hostLat == 0.0 && hostLon == 0.0) {
            return emptyMap()
        }

        // 1. Detect Host Vehicle Turn Maneuvers (>35° heading change with spatial displacement)
        if (!isFirstHostFix) {
            val distFromLast = calculateDistanceMeters(lastHostLat, lastHostLon, hostLat, hostLon)
            if (distFromLast >= 30.0 && hostSpeedKmh >= 15f) {
                val headingDelta = abs((hostHeading - lastHostHeading + 540f) % 360f - 180f)
                if (headingDelta >= 35f && headingDelta <= 145f) {
                    val maneuver = HostManeuver(
                        id = maneuverIdCounter++,
                        timestamp = now,
                        lat = hostLat,
                        lon = hostLon,
                        headingBefore = lastHostHeading,
                        headingAfter = hostHeading
                    )
                    hostManeuvers.add(maneuver)
                    Timber.d("Host maneuver detected: turn of %.0f° at %.4f, %.4f", headingDelta, hostLat, hostLon)
                }
                lastHostLat = hostLat
                lastHostLon = hostLon
                lastHostHeading = hostHeading
            }
        } else {
            lastHostLat = hostLat
            lastHostLon = hostLon
            lastHostHeading = hostHeading
            isFirstHostFix = false
        }

        // Prune old maneuvers (>15 minutes)
        hostManeuvers.removeAll { now - it.timestamp > 15 * 60 * 1000L }

        // 2. Track Mobile Targets
        val activeTargetIds = mutableSetOf<String>()

        for (target in targets) {
            // Rejection Filter: Never evaluate stationary infrastructure
            if (target.isStationary || target.category == RfCategory.ACCESS_POINT ||
                target.category == RfCategory.CCTV_SURVEILLANCE || target.category == RfCategory.CELL_TOWER
            ) {
                continue
            }

            activeTargetIds.add(target.id)
            val track = targetTracks.getOrPut(target.id) {
                TargetTrack(
                    id = target.id,
                    firstSeenMs = now,
                    lastSeenMs = now,
                    startLat = hostLat,
                    startLon = hostLon,
                    lastLat = hostLat,
                    lastLon = hostLon
                )
            }

            // Accumulate distance only if vehicle is in motion (>18 km/h) to ignore traffic jams
            if (hostSpeedKmh >= 18f) {
                val stepDist = calculateDistanceMeters(track.lastLat, track.lastLon, hostLat, hostLon)
                if (stepDist in 10.0..500.0) {
                    track.accumulatedDistanceMeters += stepDist
                    track.lastLat = hostLat
                    track.lastLon = hostLon
                }
            }

            track.lastSeenMs = now
            track.observationCount++

            // Correlate with recent turns
            val recentManeuvers = hostManeuvers.filter { now - it.timestamp <= 90_000L }
            for (m in recentManeuvers) {
                val distToTurn = calculateDistanceMeters(hostLat, hostLon, m.lat, m.lon)
                if (distToTurn <= 180.0 && target.estimatedDistanceMeters <= 90.0) {
                    track.correlatedManeuverIds.add(m.id)
                }
            }

            // 3. Compute Calm, Multi-Tiered Intelligence
            val durationMs = now - track.firstSeenMs
            val distKm = track.accumulatedDistanceMeters / 1000.0
            val turnsCount = track.correlatedManeuverIds.size

            val (level, confidence, summary) = when {
                track.isDismissed -> {
                    Triple(CoTravelLevel.INDEPENDENT, 0, "Acknowledged as normal commuter traffic.")
                }
                // High Confidence: Must have traveled >1.5 km AND made at least 2 turns together
                turnsCount >= 2 && track.accumulatedDistanceMeters >= 1500.0 && durationMs >= 180_000L -> {
                    val conf = (70 + (turnsCount * 8) + (distKm * 3)).toInt().coerceIn(70, 95)
                    Triple(
                        CoTravelLevel.PERSISTENT_ESCORT,
                        conf,
                        "Co-traveled across $turnsCount route turns (${"%.1f".format(distKm)} km) • Sustained proximity"
                    )
                }
                // Moderate Confidence: Traveled >1.0 km on the same corridor (single highway/commute)
                track.accumulatedDistanceMeters >= 1000.0 && durationMs >= 150_000L -> {
                    val conf = (35 + (distKm * 10)).toInt().coerceIn(35, 65)
                    Triple(
                        CoTravelLevel.SHARED_ROUTE,
                        conf,
                        "Shared travel corridor (${"%.1f".format(distKm)} km) • Normal traffic flow"
                    )
                }
                else -> {
                    Triple(CoTravelLevel.INDEPENDENT, 10, "")
                }
            }

            if (level != CoTravelLevel.INDEPENDENT) {
                results[target.id] = CoTravelIntel(
                    coTravelDistanceMeters = track.accumulatedDistanceMeters,
                    durationMs = durationMs,
                    correlatedTurnsCount = turnsCount,
                    confidenceScore = confidence,
                    level = level,
                    summary = summary,
                    isDismissed = track.isDismissed
                )
            }
        }

        // Clean up tracks not seen in > 5 minutes
        targetTracks.entries.removeIf { (id, track) ->
            !activeTargetIds.contains(id) && (now - track.lastSeenMs > 300_000L)
        }

        return results
    }

    /**
     * User single-tap action to dismiss co-travel notice for a target.
     */
    fun dismissTarget(id: String) {
        targetTracks[id]?.let {
            it.isDismissed = true
        }
        Timber.i("Co-Travel notice dismissed for target: %s", id)
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c
    }
}
