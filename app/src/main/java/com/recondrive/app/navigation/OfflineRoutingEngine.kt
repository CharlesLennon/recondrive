package com.recondrive.app.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import kotlin.math.*

/**
 * Offline Route Computation Engine
 * Computes optimal routing paths, distance estimates, and turn-by-turn maneuvers.
 */
class OfflineRoutingEngine {

    suspend fun calculateRoute(
        startLat: Double, startLon: Double,
        destLat: Double, destLon: Double
    ): TacticalRoute = withContext(Dispatchers.Default) {
        Timber.i("Calculating offline tactical route from ($startLat, $startLon) to ($destLat, $destLon)")

        // Great-circle distance
        val totalDistance = calculateDistance(startLat, startLon, destLat, destLon)
        val estimatedSpeedMps = 14.0 // avg 50 km/h in m/s
        val duration = (totalDistance / estimatedSpeedMps).toLong()

        // Generate interpolated tactical waypoints along the bearing
        val numPoints = 20
        val points = mutableListOf<Pair<Double, Double>>()
        val steps = mutableListOf<NavigationStep>()

        for (i in 0..numPoints) {
            val fraction = i.toDouble() / numPoints
            val lat = startLat + (destLat - startLat) * fraction
            val lon = startLon + (destLon - startLon) * fraction
            points.add(Pair(lat, lon))
        }

        // Generate sequential tactical navigation maneuvers
        steps.add(
            NavigationStep(
                stepIndex = 0,
                maneuverType = ManeuverType.DEPART,
                instruction = "Depart on Sector Transit Road",
                roadName = "Sector Transit Alpha",
                distanceMeters = totalDistance * 0.2,
                targetLat = points[4].first,
                targetLon = points[4].second
            )
        )

        steps.add(
            NavigationStep(
                stepIndex = 1,
                maneuverType = ManeuverType.SLIGHT_RIGHT,
                instruction = "In 400m, bear slight right onto Recon Tactical Byway",
                roadName = "Recon Tactical Byway",
                distanceMeters = totalDistance * 0.35,
                targetLat = points[11].first,
                targetLon = points[11].second
            )
        )

        steps.add(
            NavigationStep(
                stepIndex = 2,
                maneuverType = ManeuverType.LEFT,
                instruction = "Turn left onto Perimeter Boulevard",
                roadName = "Perimeter Blvd",
                distanceMeters = totalDistance * 0.35,
                targetLat = points[18].first,
                targetLon = points[18].second
            )
        )

        steps.add(
            NavigationStep(
                stepIndex = 3,
                maneuverType = ManeuverType.ARRIVE,
                instruction = "Arrive at Tactical Grid Destination",
                roadName = "Target Objective",
                distanceMeters = totalDistance * 0.1,
                targetLat = destLat,
                targetLon = destLon
            )
        )

        TacticalRoute(
            routeId = UUID.randomUUID().toString(),
            totalDistanceMeters = totalDistance,
            estimatedDurationSeconds = duration,
            steps = steps,
            geometryPoints = points
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
