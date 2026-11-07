package com.recondrive.app.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * High-performance OpenStreetMap Standard Raster Tile Streaming and Caching Engine.
 * Connects directly to open, non-commercial OpenStreetMap Foundation CDNs and OpenTopoMap mirrors.
 * Requires ZERO API keys, has ZERO watermarks, and caches tiles locally for instant offline reuse.
 */
class OsmTileManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = LruCache<String, ImageBitmap>(256)
    private val pendingRequests = mutableSetOf<String>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // Using osm_tiles_v3 to ensure complete separation from poisoned Carto watermarked caches
    private val tileDir = File(context.cacheDir, "osm_tiles_v3").apply {
        if (!exists()) mkdirs()
    }

    init {
        // Automatically purge obsolete Carto cache directories on startup
        scope.launch(Dispatchers.IO) {
            try {
                val oldDir = File(context.cacheDir, "osm_tiles")
                if (oldDir.exists()) {
                    Timber.i("Purging legacy watermarked Carto tile cache directory...")
                    oldDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Timber.w(e, "Error clearing legacy tile cache")
            }
        }
    }

    /**
     * Resolves the open tile server URL template (No API Key Required).
     */
    fun getTileUrlTemplate(skinId: String): String {
        return when (skinId) {
            "military_topo" -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
            else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        }
    }

    /**
     * Fallback open mirror URL template in case of network throttle.
     */
    fun getFallbackUrlTemplate(skinId: String): String {
        return "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png"
    }

    /**
     * Retrieves a tile from memory or disk cache.
     * If not available, schedules background download and returns null for instant vector fallback.
     */
    fun getTile(z: Int, x: Int, y: Int, skinId: String = "tactical_obsidian"): ImageBitmap? {
        val safeZ = z.coerceIn(0, 19)
        val n = 1 shl safeZ
        val safeX = x.coerceIn(0, n - 1)
        val safeY = y.coerceIn(0, n - 1)

        val key = "osm_${safeZ}_${safeX}_${safeY}"

        // 1. Check in-memory LRU cache
        memoryCache.get(key)?.let { return it }

        // 2. Check local disk cache
        val diskFile = File(tileDir, "${key}.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    val imgBmp = bmp.asImageBitmap()
                    memoryCache.put(key, imgBmp)
                    return imgBmp
                }
            } catch (e: Exception) {
                Timber.w(e, "Error decoding disk tile $key")
                diskFile.delete()
            }
        }

        // 3. Queue network download if not already in flight
        synchronized(pendingRequests) {
            if (pendingRequests.contains(key) || pendingRequests.size > 256) {
                return null
            }
            pendingRequests.add(key)
        }

        scope.launch {
            try {
                var bytes = fetchTileBytes(getTileUrlTemplate(skinId), safeZ, safeX, safeY)
                if (bytes == null || bytes.size < 500) {
                    // Try fallback mirror
                    bytes = fetchTileBytes(getFallbackUrlTemplate(skinId), safeZ, safeX, safeY)
                }

                if (bytes != null && bytes.size >= 500) {
                    // Save to disk
                    FileOutputStream(diskFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }

                    // Decode to bitmap
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val imgBmp = bmp.asImageBitmap()
                        memoryCache.put(key, imgBmp)
                        withContext(Dispatchers.Main) {
                            _revision.value++
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore network timeouts or offline state; vector map handles rendering
            } finally {
                synchronized(pendingRequests) {
                    pendingRequests.remove(key)
                }
            }
        }

        return null
    }

    private fun fetchTileBytes(template: String, z: Int, x: Int, y: Int): ByteArray? {
        val urlStr = template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "ReconDrive/1.0 (Android Tactical Navigation; OpenStreetMap)")
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
        }

        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        scope.launch(Dispatchers.IO) {
            tileDir.deleteRecursively()
            tileDir.mkdirs()
        }
    }
}
