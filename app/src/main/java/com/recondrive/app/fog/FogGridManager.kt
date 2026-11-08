package com.recondrive.app.fog

import android.content.Context
import android.location.Location
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.math.*

/**
 * Tactical Fog of War Grid Manager
 * Handles real-time spatial indexing, circular beam unmasking, and exploration metrics.
 */
class FogGridManager(
    context: Context,
    private val fogPreferences: FogPreferences? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = Room.databaseBuilder(
        context.applicationContext,
        FogDatabase::class.java,
        "tactical_fog.db"
    ).fallbackToDestructiveMigration().build()
    private val dao = db.fogDao()

    private val _stats = MutableStateFlow(ExplorationStats())
    val stats: StateFlow<ExplorationStats> = _stats.asStateFlow()

    val allExploredCells: StateFlow<List<ExploredCell>> = dao.getAllExploredCells()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _recentlyUncovered = MutableSharedFlow<List<ExploredCell>>(extraBufferCapacity = 64)
    val recentlyUncovered: SharedFlow<List<ExploredCell>> = _recentlyUncovered.asSharedFlow()

    private var lastLocation: Location? = null
    private var currentStreakMeters = 0.0
    private var totalDistanceMeters = 0.0

    // Zoom 20 tile resolution is ~24 meters at 50° latitude (~566 m² per cell)
    private val zoomLevel = 20
    private val approxCellAreaKm2 = 0.000566

    init {
        // Load initial count on startup
        scope.launch {
            val count = dao.getExploredCellCountSync()
            val area = count * approxCellAreaKm2
            _stats.update {
                it.copy(
                    totalCellsExplored = count,
                    totalAreaKm2 = area,
                    explorationRank = TacticalRank.fromArea(area)
                )
            }
        }
    }

    /**
     * Ingests a new GPS fix and burns away the fog around the vehicle.
     * Speed-adaptive scanning radius: 60m (walking/slow) up to 200m (highway driving).
     */
    fun processLocation(location: Location) {
        scope.launch {
            val speedKmh = location.speed * 3.6f
            val baseRange = fogPreferences?.fogRangeMeters?.value?.toDouble() ?: 50.0
            val speedFactor = when {
                speedKmh > 60f -> 1.25
                speedKmh > 25f -> 1.0
                else -> 0.75
            }
            val scanRadiusMeters = (baseRange * speedFactor).coerceIn(35.0, 650.0)

            // Calculate distance delta from previous fix
            var distanceDelta = 0.0
            lastLocation?.let { prev ->
                distanceDelta = prev.distanceTo(location).toDouble()
                if (distanceDelta > 1.0 && distanceDelta < 500.0) { // filter GPS teleports
                    totalDistanceMeters += distanceDelta
                }
            }
            lastLocation = location

            // Calculate bounding tile range for scan radius
            val centerTileX = lonToTileX(location.longitude, zoomLevel)
            val centerTileY = latToTileY(location.latitude, zoomLevel)

            // Radius in tile units (approx 24m per tile)
            val tileRadius = ceil(scanRadiusMeters / 24.0).toInt().coerceIn(1, 28)

            val candidateCells = mutableListOf<ExploredCell>()

            for (dx in -tileRadius..tileRadius) {
                for (dy in -tileRadius..tileRadius) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy

                    val cellCenterLat = tileYToLat(ty, zoomLevel)
                    val cellCenterLon = tileXToLon(tx, zoomLevel)

                    // Check if inside circle radius
                    val dist = calculateHaversineMeters(
                        location.latitude, location.longitude,
                        cellCenterLat, cellCenterLon
                    )

                    if (dist <= scanRadiusMeters) {
                        val cellId = packCellId(zoomLevel, tx, ty)
                        candidateCells.add(
                            ExploredCell(
                                cellId = cellId,
                                zoomLevel = zoomLevel,
                                tileX = tx,
                                tileY = ty,
                                centerLat = cellCenterLat,
                                centerLon = cellCenterLon
                            )
                        )
                    }
                }
            }

            if (candidateCells.isNotEmpty()) {
                val insertResults = dao.insertCells(candidateCells)
                val newlyUncovered = mutableListOf<ExploredCell>()
                for (i in candidateCells.indices) {
                    if (insertResults[i] != -1L) {
                        newlyUncovered.add(candidateCells[i])
                    }
                }

                val newlyUncoveredCount = newlyUncovered.size

                // Streak tracking: if newly uncovered cells exist, streak continues
                if (newlyUncoveredCount > 0) {
                    currentStreakMeters += distanceDelta
                    _recentlyUncovered.emit(newlyUncovered)
                } else if (distanceDelta > 15.0) {
                    // Driving in already-explored territory resets current streak
                    currentStreakMeters = 0.0
                }

                // Update stats
                val currentCount = _stats.value.totalCellsExplored + newlyUncoveredCount
                val areaKm2 = currentCount * approxCellAreaKm2

                _stats.update {
                    it.copy(
                        totalCellsExplored = currentCount,
                        totalAreaKm2 = areaKm2,
                        currentUnchartedStreakKm = currentStreakMeters / 1000.0,
                        totalExplorationDistanceKm = totalDistanceMeters / 1000.0,
                        explorationRank = TacticalRank.fromArea(areaKm2),
                        lastDiscoveredCellLat = location.latitude,
                        lastDiscoveredCellLon = location.longitude
                    )
                }

                if (newlyUncoveredCount > 0) {
                    Timber.d("Tactical Fog: Uncovered $newlyUncoveredCount new cells! Total: $currentCount, Area: %.2f km²".format(areaKm2))
                }
            }
        }
    }

    suspend fun getExploredCellsInBox(minX: Int, maxX: Int, minY: Int, maxY: Int): List<ExploredCell> {
        return dao.getCellsInBoundingBox(minX, maxX, minY, maxY)
    }

    // --- Web Mercator Tile Math ---
    companion object {
        fun lonToTileX(lon: Double, zoom: Int): Int {
            return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
        }

        fun latToTileY(lat: Double, zoom: Int): Int {
            val rad = Math.toRadians(lat)
            return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
        }

        fun tileXToLon(x: Int, zoom: Int): Double {
            return (x.toDouble() + 0.5) / (1 shl zoom) * 360.0 - 180.0
        }

        fun tileYToLat(y: Int, zoom: Int): Double {
            val n = Math.PI - 2.0 * Math.PI * (y.toDouble() + 0.5) / (1 shl zoom)
            return Math.toDegrees(atan(sinh(n)))
        }

        fun packCellId(zoom: Int, x: Int, y: Int): Long {
            return (zoom.toLong() shl 56) or ((x.toLong() and 0x0FFFFFFFL) shl 28) or (y.toLong() and 0x0FFFFFFFL)
        }

        fun calculateHaversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadius = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadius * c
        }
    }
}
