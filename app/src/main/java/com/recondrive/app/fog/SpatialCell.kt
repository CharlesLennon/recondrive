package com.recondrive.app.fog

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Represents an uncovered spatial grid cell in the tactical fog of war.
 * Uses a quadtree / web-mercator tile index (~20m - 30m resolution at zoom 16/17).
 */
@Entity(tableName = "explored_cells")
data class ExploredCell(
    @PrimaryKey
    val cellId: Long,              // Packed tile index: (zoom shl 32) or (x shl 16) or y
    val zoomLevel: Int = 20,
    val tileX: Int,
    val tileY: Int,
    val centerLat: Double,
    val centerLon: Double,
    val discoveredAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

@Dao
interface FogDao {
    @Query("SELECT * FROM explored_cells")
    fun getAllExploredCells(): Flow<List<ExploredCell>>

    @Query("SELECT * FROM explored_cells WHERE tileX BETWEEN :minX AND :maxX AND tileY BETWEEN :minY AND :maxY")
    suspend fun getCellsInBoundingBox(minX: Int, maxX: Int, minY: Int, maxY: Int): List<ExploredCell>

    @Query("SELECT COUNT(*) FROM explored_cells")
    fun getExploredCellCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM explored_cells")
    suspend fun getExploredCellCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCells(cells: List<ExploredCell>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCell(cell: ExploredCell): Long

    @Query("UPDATE explored_cells SET visitCount = visitCount + 1 WHERE cellId = :cellId")
    suspend fun incrementVisit(cellId: Long)

    @Query("DELETE FROM explored_cells")
    suspend fun clearFog()
}

@Database(entities = [ExploredCell::class], version = 1, exportSchema = false)
abstract class FogDatabase : RoomDatabase() {
    abstract fun fogDao(): FogDao
}
