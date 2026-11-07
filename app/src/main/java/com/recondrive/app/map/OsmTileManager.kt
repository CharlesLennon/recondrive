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
 * High-performance OpenStreetMap & Carto Raster Tile Streaming and Caching Engine
 * Streams online vector-rendered raster tiles for street-level cartography across the UK and the globe,
 * storing tiles in memory and on disk for instant offline reuse.
 */
class OsmTileManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = LruCache<String, ImageBitmap>(256)
    private val pendingRequests = mutableSetOf<String>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val tileDir = File(context.cacheDir, "osm_tiles").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Resolves the appropriate tile server URL template based on current Map Skin
     */
    fun getTileUrlTemplate(skinId: String): String {
        return when (skinId) {
            "classic_daylight" -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            "vintage_cartographer" -> "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
            "midnight_monochrome" -> "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png"
            else -> "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png" // Tactical Obsidian & Cyberpunk
        }
    }

    /**
     * Retrieves a tile from memory or disk cache.
     * If not available, schedules background download and returns null for instant vector fallback.
     */
    fun getTile(z: Int, x: Int, y: Int, skinId: String = "tactical_obsidian"): ImageBitmap? {
        val key = "${skinId}_${z}_${x}_${y}"

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
                val template = getTileUrlTemplate(skinId)
                val urlStr = template
                    .replace("{z}", z.toString())
                    .replace("{x}", x.toString())
                    .replace("{y}", y.toString())

                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "ReconDrive/1.0 (Android Tactical Navigation; OpenStreetMap)")
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = true
                }

                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()

                    if (bytes.isNotEmpty()) {
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
                } else {
                    conn.disconnect()
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

    fun clearCache() {
        memoryCache.evictAll()
        scope.launch(Dispatchers.IO) {
            tileDir.deleteRecursively()
            tileDir.mkdirs()
        }
    }
}
