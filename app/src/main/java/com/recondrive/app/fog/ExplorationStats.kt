package com.recondrive.app.fog

/**
 * Real-time exploration statistics and gamification rankings.
 */
data class ExplorationStats(
    val totalCellsExplored: Int = 0,
    val totalAreaKm2: Double = 0.0,
    val currentUnchartedStreakKm: Double = 0.0,
    val totalExplorationDistanceKm: Double = 0.0,
    val explorationRank: TacticalRank = TacticalRank.SCOUT,
    val lastDiscoveredCellLat: Double = 0.0,
    val lastDiscoveredCellLon: Double = 0.0
)

enum class TacticalRank(val title: String, val thresholdKm2: Double, val badgeHex: String) {
    SCOUT("Recon Scout", 0.0, "#00E5FF"),
    PATHFINDER("Sector Pathfinder", 5.0, "#00E676"),
    VANGUARD("Tactical Vanguard", 25.0, "#FFD600"),
    CARTOGRAPHER("Master Cartographer", 100.0, "#FF9100"),
    OVERLORD("Sovereign Surveyor", 500.0, "#FF1744");

    companion object {
        fun fromArea(areaKm2: Double): TacticalRank {
            return values().reversed().firstOrNull { areaKm2 >= it.thresholdKm2 } ?: SCOUT
        }
    }
}
