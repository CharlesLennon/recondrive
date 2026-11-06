package com.recondrive.app.map

import android.content.Context
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages local offline .mbtiles vector packs and region downloads.
 */
class OfflineTileRepository(private val context: Context) {

    private val offlineDir = File(context.filesDir, "tactical_mbtiles").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Lists all locally downloaded offline vector map files.
     */
    fun getDownloadedRegions(): List<File> {
        return offlineDir.listFiles { file -> file.extension == "mbtiles" }?.toList() ?: emptyList()
    }

    /**
     * Resolves the primary active offline map path, or null if none downloaded.
     */
    fun getActiveOfflinePack(): File? {
        val files = getDownloadedRegions()
        return files.firstOrNull()
    }

    /**
     * Generates a MapLibre compatible file URL for a local .mbtiles pack.
     */
    fun getLocalPackUri(mbtilesFile: File): String {
        return "mbtiles://${mbtilesFile.absolutePath}"
    }

    suspend fun getStorageUsageBytes(): Long = withContext(Dispatchers.IO) {
        offlineDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    companion object {
        const val DEFAULT_REGION_NAME = "uk_south_tactical.mbtiles"
    }
}
