package com.recondrive.app.navigation

/**
 * Tactical Turn-by-Turn Navigation Step
 */
data class NavigationStep(
    val stepIndex: Int,
    val maneuverType: ManeuverType,
    val instruction: String,
    val roadName: String,
    val distanceMeters: Double,
    val targetLat: Double,
    val targetLon: Double
)

enum class ManeuverType {
    DEPART,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    MERGE,
    ARRIVE
}

data class TacticalRoute(
    val routeId: String,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Long,
    val steps: List<NavigationStep>,
    val geometryPoints: List<Pair<Double, Double>> // List of (Lat, Lon)
)
