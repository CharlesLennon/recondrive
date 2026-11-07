package com.recondrive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recondrive.app.fog.FogPreferences
import com.recondrive.app.map.*
import com.recondrive.app.skin.FogGridOverlay
import com.recondrive.app.skin.SkinManager
import com.recondrive.app.ui.theme.*
import kotlin.math.*

/**
 * Google Maps-style Interactive OpenStreetMap Navigation Screen
 * Supports free panning, pinch-to-zoom, compass bearing, UK city jump presets,
 * live OpenStreetMap / Carto raster tile streaming, rich offline UK vector cartography,
 * target waypoint routing, and toggling real-world Fog of War exploration shroud.
 */
@Composable
fun InteractiveMapScreen(
    currentLat: Double,
    currentLon: Double,
    currentHeading: Float,
    skinManager: SkinManager,
    offlineTileRepository: OfflineTileRepository,
    fogPreferences: FogPreferences = com.recondrive.app.ReconApplication.instance.fogPreferences,
    osmTileManager: OsmTileManager = com.recondrive.app.ReconApplication.instance.osmTileManager,
    initialWaypoint: Pair<Double, Double>? = null,
    initialWaypointName: String? = null,
    savedZoom: Float = 13.5f,
    onZoomChanged: (Float) -> Unit = {},
    onNavigateToWaypoint: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onOpenSkins: () -> Unit = {},
    onOpenOfflinePacks: () -> Unit = {},
    currentSpeedKmh: Float = 0f,
    deviceHeading: Float = currentHeading,
    onOpenTimeline: () -> Unit = {}
) {
    val mapSkin by skinManager.currentMapSkin.collectAsState()
    val fogSkin by skinManager.currentFogSkin.collectAsState()
    val fogRange by fogPreferences.fogRangeMeters.collectAsState()
    val fogOpacity by fogPreferences.fogOpacity.collectAsState()
    val tileRevision by osmTileManager.revision.collectAsState()
    val timelineEntries by com.recondrive.app.ReconApplication.instance.locationTimelineManager.timelineEntries.collectAsState()
    val allExploredCells by com.recondrive.app.ReconApplication.instance.fogGridManager.allExploredCells.collectAsState()

    // Interactive Viewport State (Default: Vehicle Current Position, default zoom 13.5f for clear regional overview)
    var cameraLat by remember { mutableDoubleStateOf(initialWaypoint?.first ?: currentLat) }
    var cameraLon by remember { mutableDoubleStateOf(initialWaypoint?.second ?: currentLon) }
    var zoom by remember { mutableFloatStateOf(savedZoom) }
    var isFollowingGps by remember { mutableStateOf(initialWaypoint == null) }
    var isFogLayerActive by remember { mutableStateOf(true) }

    // Preserve zoom level across tab changes and navigation transitions
    LaunchedEffect(zoom) {
        onZoomChanged(zoom)
    }

    // Selected Target Waypoint
    var selectedWaypoint by remember(initialWaypoint) { mutableStateOf<Pair<Double, Double>?>(initialWaypoint) }
    var waypointName by remember(initialWaypointName) { mutableStateOf(initialWaypointName ?: "Custom Tactical Waypoint") }

    LaunchedEffect(initialWaypoint) {
        if (initialWaypoint != null) {
            cameraLat = initialWaypoint.first
            cameraLon = initialWaypoint.second
            selectedWaypoint = initialWaypoint
            if (initialWaypointName != null) {
                waypointName = initialWaypointName
            }
            if (zoom < 16.0f) {
                zoom = 16.8f
            }
            isFollowingGps = false
        }
    }

    // Map Rendering Mode: "HYBRID" (OSM Tiles + Vector), "VECTOR" (Pure Offline Vector Engine), "TILES" (OSM Streets)
    var mapRenderingMode by remember { mutableStateOf("HYBRID") }
    val downloadedPacks = remember { offlineTileRepository.getDownloadedRegions() }
    val hasOfflineData = downloadedPacks.isNotEmpty()

    // Real-time Tactical ColorFilter applied to OpenStreetMap tiles (Zero API key required)
    val tileColorFilter = remember(mapSkin.id, mapRenderingMode) {
        if (mapRenderingMode == "TILES" || mapSkin.id == "classic_osm_light") {
            null
        } else if (mapSkin.id == "antique_parchment") {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(
                    floatArrayOf(
                        0.9f, 0.05f, 0.05f, 0f, 20f,
                        0.05f, 0.85f, 0.05f, 0f, 15f,
                        0.05f, 0.05f, 0.65f, 0f, 0f,
                        0f, 0f, 0f, 0.95f, 0f
                    )
                )
            )
        } else if (mapSkin.id == "midnight_stealth") {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(
                    floatArrayOf(
                        -0.70f, 0f, 0f, 0f, 160f,
                        0f, -0.70f, 0f, 0f, 160f,
                        0f, 0f, -0.70f, 0f, 160f,
                        0f, 0f, 0f, 0.90f, 0f
                    )
                )
            )
        } else { // tactical_obsidian, cyberpunk_neon, military_topo
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(
                    floatArrayOf(
                        -0.75f, 0f, 0f, 0f, 165f,
                        0f, -0.75f, 0f, 0f, 175f,
                        0f, 0f, -0.65f, 0f, 195f,
                        0f, 0f, 0f, 0.88f, 0f
                    )
                )
            )
        }
    }

    // Sync with GPS if in follow mode
    LaunchedEffect(currentLat, currentLon) {
        if (isFollowingGps) {
            cameraLat = currentLat
            cameraLon = currentLon
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(mapSkin.backgroundColor))
    ) {
        // =====================================================================
        // 1. INTERACTIVE FULL-SCREEN OPENSTREETMAP CANVAS
        // =====================================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        // Panning Math: Convert screen pixel delta to Lat/Lon
                        val scale = 256.0 * 2.0.pow(zoom.toDouble())
                        val dLon = -(pan.x / scale) * 360.0
                        val dLat = (pan.y / scale) * 180.0

                        cameraLon = (cameraLon + dLon).coerceIn(
                            UkOsmDataEngine.UK_FULL_BOUNDS.minLon,
                            UkOsmDataEngine.UK_FULL_BOUNDS.maxLon
                        )
                        cameraLat = (cameraLat + dLat).coerceIn(
                            UkOsmDataEngine.UK_FULL_BOUNDS.minLat,
                            UkOsmDataEngine.UK_FULL_BOUNDS.maxLat
                        )

                        // Pinch Zoom
                        if (zoomFactor != 1.0f) {
                            zoom = (zoom + (zoomFactor - 1.0f) * 2.0f).coerceIn(6.0f, 18.5f)
                        }

                        isFollowingGps = false // User panned away from vehicle GPS
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = (zoom + 1.0f).coerceAtMost(18.5f)
                        },
                        onTap = { tapOffset ->
                            // Convert tap offset to Lat/Lon
                            val scale = 256.0 * 2.0.pow(zoom.toDouble())
                            val dx = tapOffset.x - (size.width / 2f)
                            val dy = tapOffset.y - (size.height / 2f)

                            val tapLon = cameraLon + (dx / scale) * 360.0
                            val tapLat = cameraLat - (dy / scale) * 180.0

                            selectedWaypoint = tapLat to tapLon
                            val dist = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, tapLat, tapLon)
                            waypointName = "Waypoint (%.1f km away)".format(dist)
                        }
                    )
                }
        ) {
            // Touch tileRevision so Compose tracks recomposition on tile arrival
            @Suppress("UNUSED_VARIABLE")
            val rev = tileRevision

            val width = size.width
            val height = size.height

            // Helper to project WGS84 Lat/Lon to Canvas screen coordinates
            fun project(lat: Double, lon: Double): Offset {
                val scale = 256.0 * 2.0.pow(zoom.toDouble())
                val cx = (cameraLon + 180.0) / 360.0 * scale
                val cy = (1.0 - ln(tan(Math.toRadians(cameraLat)) + 1.0 / cos(Math.toRadians(cameraLat))) / PI) / 2.0 * scale

                val x = (lon + 180.0) / 360.0 * scale
                val y = (1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * scale

                return Offset(
                    (x - cx + width / 2.0).toFloat(),
                    (y - cy + height / 2.0).toFloat()
                )
            }

            // Current viewport bounding box
            val scale = 256.0 * 2.0.pow(zoom.toDouble())
            val latSpan = (height / scale) * 180.0
            val lonSpan = (width / scale) * 360.0
            val viewBounds = UkBoundingBox(
                minLat = cameraLat - latSpan / 2.0,
                maxLat = cameraLat + latSpan / 2.0,
                minLon = cameraLon - lonSpan / 2.0,
                maxLon = cameraLon + lonSpan / 2.0
            )

            // -----------------------------------------------------------------
            // A. DRAW UK LANDMASS & COASTLINES (Great Britain & Northern Ireland)
            // -----------------------------------------------------------------
            val gbPath = Path()
            var gbStarted = false
            for (pt in UkOsmDataEngine.UK_COASTLINE) {
                val sc = project(pt.first, pt.second)
                if (!gbStarted) {
                    gbPath.moveTo(sc.x, sc.y)
                    gbStarted = true
                } else {
                    gbPath.lineTo(sc.x, sc.y)
                }
            }
            gbPath.close()

            // Fill UK landmass with subtle contrast
            drawPath(
                path = gbPath,
                color = Color(mapSkin.landColor).copy(alpha = 0.5f)
            )
            // Stroke coastline outline
            drawPath(
                path = gbPath,
                color = Color(mapSkin.minorRoadColor).copy(alpha = 0.30f),
                style = Stroke(width = (zoom * 0.25f).coerceIn(1.5f, 4f))
            )

            val niPath = Path()
            var niStarted = false
            for (pt in UkOsmDataEngine.NORTHERN_IRELAND_COASTLINE) {
                val sc = project(pt.first, pt.second)
                if (!niStarted) {
                    niPath.moveTo(sc.x, sc.y)
                    niStarted = true
                } else {
                    niPath.lineTo(sc.x, sc.y)
                }
            }
            niPath.close()
            drawPath(
                path = niPath,
                color = Color(mapSkin.landColor).copy(alpha = 0.5f)
            )
            drawPath(
                path = niPath,
                color = Color(mapSkin.minorRoadColor).copy(alpha = 0.30f),
                style = Stroke(width = (zoom * 0.25f).coerceIn(1.5f, 4f))
            )

            // -----------------------------------------------------------------
            // B. DRAW REAL OPENSTREETMAP RASTER TILES (Skipped in VECTOR ONLY mode)
            // -----------------------------------------------------------------
            var tilesRenderedCount = 0
            if (mapRenderingMode != "VECTOR") {
                val tileZ = zoom.toInt().coerceIn(1, 18)
                val n = 1 shl tileZ
                val tileScale = 2.0.pow((zoom - tileZ).toDouble())
                val tileSizeOnScreen = (256.0 * tileScale).toFloat()

                val cameraLatRad = Math.toRadians(cameraLat.coerceIn(-85.0511, 85.0511))
                val cameraTileX = (cameraLon + 180.0) / 360.0 * n
                val cameraTileY = (1.0 - ln(tan(cameraLatRad) + 1.0 / cos(cameraLatRad)) / PI) / 2.0 * n

                val halfTilesW = (width / (2.0 * tileSizeOnScreen))
                val halfTilesH = (height / (2.0 * tileSizeOnScreen))

                val minTx = (floor(cameraTileX - halfTilesW).toInt()).coerceIn(0, n - 1)
                val maxTx = (ceil(cameraTileX + halfTilesW).toInt()).coerceIn(0, n - 1)
                val minTy = (floor(cameraTileY - halfTilesH).toInt()).coerceIn(0, n - 1)
                val maxTy = (ceil(cameraTileY + halfTilesH).toInt()).coerceIn(0, n - 1)

                for (tx in minTx..maxTx) {
                    for (ty in minTy..maxTy) {
                        val tileBmp = osmTileManager.getTile(tileZ, tx, ty, mapSkin.id)
                        if (tileBmp != null) {
                            val sX = (width / 2f) + ((tx - cameraTileX) * tileSizeOnScreen).toFloat()
                            val sY = (height / 2f) + ((ty - cameraTileY) * tileSizeOnScreen).toFloat()

                            drawImage(
                                image = tileBmp,
                                dstOffset = androidx.compose.ui.unit.IntOffset(sX.toInt(), sY.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    ceil(tileSizeOnScreen).toInt() + 1,
                                    ceil(tileSizeOnScreen).toInt() + 1
                                ),
                                colorFilter = tileColorFilter
                            )
                            tilesRenderedCount++
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // C. DRAW WATER BODIES & ESTUARIES (Thames, Severn, Forth, Clyde, Lochs)
            // -----------------------------------------------------------------
            val waterColor = Color(mapSkin.waterColor)
            for (water in UkOsmDataEngine.MAJOR_WATERWAYS) {
                val path = Path()
                var started = false
                for (pt in water.points) {
                    val sc = project(pt.first, pt.second)
                    if (!started) {
                        path.moveTo(sc.x, sc.y)
                        started = true
                    } else {
                        path.lineTo(sc.x, sc.y)
                    }
                }
                drawPath(path = path, color = waterColor, style = Stroke(width = (zoom * 0.8f).coerceAtLeast(3f)))
            }

            // -----------------------------------------------------------------
            // D. DRAW SECONDARY & LOCAL STREET GRID (Continuous spatial coverage)
            // -----------------------------------------------------------------
            if (zoom >= 9.5f) {
                val localGrid = UkOsmDataEngine.getLocalStreetGridForBounds(viewBounds, zoom)
                val roadAlpha = if (mapRenderingMode == "VECTOR") 1.0f else 0.95f
                for (road in localGrid) {
                    val path = Path()
                    var started = false
                    for (pt in road.points) {
                        val sc = project(pt.first, pt.second)
                        if (!started) {
                            path.moveTo(sc.x, sc.y)
                            started = true
                        } else {
                            path.lineTo(sc.x, sc.y)
                        }
                    }

                    if (road.type == OsmRoadType.SECONDARY) {
                        val secWidth = (zoom * 0.32f).coerceIn(3.0f, 6.5f)
                        // Dark casing underlay for sharp road edge separation
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.55f),
                            style = Stroke(width = secWidth + 2.0f)
                        )
                        // Secondary arterial line using majorRoadColor
                        drawPath(
                            path = path,
                            color = Color(mapSkin.majorRoadColor).copy(alpha = 0.85f),
                            style = Stroke(width = secWidth)
                        )
                    } else {
                        val minorWidth = (zoom * 0.22f).coerceIn(2.2f, 4.5f)
                        // Contrast casing underlay
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.40f),
                            style = Stroke(width = minorWidth + 1.5f)
                        )
                        // High-contrast minor road line
                        drawPath(
                            path = path,
                            color = Color(mapSkin.minorRoadColor).copy(alpha = roadAlpha),
                            style = Stroke(width = minorWidth)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // E. DRAW MAJOR UK ARTERIALS, LONDON CIRCULARS & MOTORWAYS
            // -----------------------------------------------------------------
            for (road in UkOsmDataEngine.MAJOR_ROADS) {
                val path = Path()
                var started = false
                for (pt in road.points) {
                    val sc = project(pt.first, pt.second)
                    if (!started) {
                        path.moveTo(sc.x, sc.y)
                        started = true
                    } else {
                        path.lineTo(sc.x, sc.y)
                    }
                }

                when (road.type) {
                    OsmRoadType.MOTORWAY -> {
                        val mwBase = (zoom * 0.52f).coerceIn(4.5f, 11f)
                        // Underlay dark border casing
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.85f),
                            style = Stroke(width = mwBase + 5f)
                        )
                        // Tactical motorway glow
                        drawPath(
                            path = path,
                            color = Color(mapSkin.highwayColor).copy(alpha = 0.45f),
                            style = Stroke(width = mwBase + 3.5f)
                        )
                        // Core corridor
                        drawPath(
                            path = path,
                            color = Color(mapSkin.highwayColor),
                            style = Stroke(width = mwBase)
                        )
                    }
                    OsmRoadType.PRIMARY, OsmRoadType.TRUNK -> {
                        // A-Roads: bold, crisp, and high-visibility using majorRoadColor
                        val aRoadBase = (zoom * 0.40f).coerceIn(3.5f, 8.5f)
                        // Underlay dark border casing
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.75f),
                            style = Stroke(width = aRoadBase + 4f)
                        )
                        // A-Road corridor glow
                        drawPath(
                            path = path,
                            color = Color(mapSkin.majorRoadColor).copy(alpha = 0.40f),
                            style = Stroke(width = aRoadBase + 2.5f)
                        )
                        // Core corridor
                        drawPath(
                            path = path,
                            color = Color(mapSkin.majorRoadColor),
                            style = Stroke(width = aRoadBase)
                        )
                    }
                    else -> {
                        val rBase = (zoom * 0.28f).coerceIn(2.8f, 6.0f)
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.6f),
                            style = Stroke(width = rBase + 2f)
                        )
                        drawPath(
                            path = path,
                            color = Color(mapSkin.minorRoadColor),
                            style = Stroke(width = rBase)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // F. DRAW FOG OF WAR SHROUD & EXPLORED TRAILS (If enabled)
            // -----------------------------------------------------------------
            val vehicleScreenPos = project(currentLat, currentLon)

            // Dynamic ground resolution calculation (pixels per meter at current viewport zoom)
            // In Web Mercator, 1 degree longitude = 111,320m * cos(latitude).
            val cosLat = cos(Math.toRadians(currentLat)).coerceAtLeast(0.01)
            val deltaLon1m = 1.0 / (111320.0 * cosLat)
            val p1m = project(currentLat, currentLon + deltaLon1m)
            val pixelsPerMeter = kotlin.math.abs(p1m.x - vehicleScreenPos.x).toFloat().coerceAtLeast(0.001f)

            if (isFogLayerActive) {
                val nativeCanvas = drawContext.canvas.nativeCanvas

                // Surveillance Radar Beam Radius: Anchored 1:1 to real-world meters at the current Web Mercator zoom
                val geoBeamRadius = (pixelsPerMeter * fogRange.toFloat())

                // Relative to zoom level: expands when zooming in, contracts when zooming out
                // Floor at 12f so when zoomed all the way out to UK view, the cursor reticle stays clearly visible
                val screenBeamRadius = geoBeamRadius.coerceAtLeast(12f)

                // 1. Offscreen Composite Layer using saveLayer + PorterDuff.Mode.CLEAR
                // This ensures all historically visited locations (timeline breadcrumbs & explored cells)
                // permanently punch transparent holes through the fog shroud with ZERO inversion glitches.
                val layerId = nativeCanvas.saveLayer(0f, 0f, width, height, null)

                // 2. Base Fog Shroud Fill (Density dynamically driven by fogOpacity 15% to 100%)
                val effectiveOpacity = (fogSkin.shroudOpacity * (0.35f + fogOpacity * 0.65f)).coerceIn(0.25f, 0.98f)
                val shroudPaint = android.graphics.Paint().apply {
                    color = fogSkin.shroudColor
                    alpha = (effectiveOpacity * 255).toInt().coerceIn(0, 255)
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                nativeCanvas.drawRect(0f, 0f, width, height, shroudPaint)

                // 3. Clear Paint for Unmasking Visited Paths and Explored Cells
                val clearHolePaint = android.graphics.Paint().apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    isAntiAlias = true
                }

                // 4. Unmask Continuous Traveled Road Corridors (Timeline Trajectory)
                if (timelineEntries.size >= 2) {
                    val corridorWidth = (screenBeamRadius * 1.8f).coerceAtLeast(20f)
                    val clearCorridorPaint = android.graphics.Paint().apply {
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = corridorWidth
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    val corridorPath = android.graphics.Path().apply {
                        val first = timelineEntries.first()
                        val p0 = project(first.latitude, first.longitude)
                        moveTo(p0.x, p0.y)
                        for (entry in timelineEntries.drop(1)) {
                            val pt = project(entry.latitude, entry.longitude)
                            lineTo(pt.x, pt.y)
                        }
                        lineTo(vehicleScreenPos.x, vehicleScreenPos.y)
                    }
                    nativeCanvas.drawPath(corridorPath, clearCorridorPaint)
                }

                // 5. Unmask All Timeline Fix Points
                for (entry in timelineEntries) {
                    val pt = project(entry.latitude, entry.longitude)
                    if (pt.x in -screenBeamRadius..(width + screenBeamRadius) && pt.y in -screenBeamRadius..(height + screenBeamRadius)) {
                        nativeCanvas.drawCircle(pt.x, pt.y, screenBeamRadius, clearHolePaint)
                    }
                }

                // 6. Unmask All Explored Spatial Cells from Persistent Database
                val cellScreenRadius = (pixelsPerMeter * 35f).coerceIn(10f, 150f)
                for (cell in allExploredCells) {
                    val cp = project(cell.centerLat, cell.centerLon)
                    if (cp.x in -cellScreenRadius..(width + cellScreenRadius) && cp.y in -cellScreenRadius..(height + cellScreenRadius)) {
                        nativeCanvas.drawCircle(cp.x, cp.y, cellScreenRadius, clearHolePaint)
                    }
                }

                // 7. Unmask Current Vehicle Radar Sector
                nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, screenBeamRadius, clearHolePaint)

                // Restore Layer back to screen
                nativeCanvas.restoreToCount(layerId)

                // 3. Vignette Transition Shadow (Atmospheric boundary separating cleared sector from dense shroud)
                if (screenBeamRadius > 20f) {
                    val shadowRingPaint = android.graphics.Paint().apply {
                        color = fogSkin.shroudColor
                        alpha = (effectiveOpacity * 200).toInt().coerceIn(0, 255)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = (screenBeamRadius * 0.15f).coerceIn(6f, 24f)
                        isAntiAlias = true
                    }
                    nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, screenBeamRadius + (screenBeamRadius * 0.08f).coerceIn(4f, 12f), shadowRingPaint)
                }

                // 4. Perimeter Laser Glowing Boundary (Theme-specific laser border)
                val auraPaint = android.graphics.Paint().apply {
                    color = fogSkin.perimeterGlowColor
                    alpha = 85
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = fogSkin.borderStrokeWidth + 4f
                    isAntiAlias = true
                }
                nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, screenBeamRadius + 2f, auraPaint)

                val perimeterPaint = android.graphics.Paint().apply {
                    color = fogSkin.perimeterGlowColor
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = fogSkin.borderStrokeWidth.coerceAtLeast(2.5f)
                    isAntiAlias = true
                    if (fogSkin.isDashedBorder) {
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
                    }
                }
                nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, screenBeamRadius, perimeterPaint)

                // Inner tactical range dashed circle (only visible when cleared sector is wide enough)
                if (screenBeamRadius > 35f) {
                    val innerRingPaint = android.graphics.Paint().apply {
                        color = fogSkin.perimeterGlowColor
                        alpha = 75
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.5f
                        isAntiAlias = true
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
                    }
                    nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, screenBeamRadius * 0.88f, innerRingPaint)
                }

                // 5. Explored Road Trajectory Glowing Track (Real Timeline Breadcrumbs)
                if (timelineEntries.size >= 2) {
                    val realTrail = Path().apply {
                        val first = timelineEntries.first()
                        val p0 = project(first.latitude, first.longitude)
                        moveTo(p0.x, p0.y)
                        for (entry in timelineEntries.drop(1)) {
                            val pt = project(entry.latitude, entry.longitude)
                            lineTo(pt.x, pt.y)
                        }
                        lineTo(vehicleScreenPos.x, vehicleScreenPos.y)
                    }
                    drawPath(
                        path = realTrail,
                        color = Color(fogSkin.exploredRoadGlowColor).copy(alpha = 0.85f),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                }

                // 6. Fog Texture & Grid Overlay (Dynamic Fog Skin Styling)
                val overlayPaint = android.graphics.Paint().apply {
                    color = fogSkin.perimeterGlowColor
                    alpha = (50 * fogOpacity).toInt().coerceIn(15, 90)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.2f
                    isAntiAlias = true
                }

                when (fogSkin.gridOverlay) {
                    FogGridOverlay.RADAR_RINGS -> {
                        overlayPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                        for (mul in floatArrayOf(1.6f, 2.3f, 3.2f, 4.4f)) {
                            val r = screenBeamRadius * mul
                            if (r < width.coerceAtLeast(height) * 1.5f) {
                                nativeCanvas.drawCircle(vehicleScreenPos.x, vehicleScreenPos.y, r, overlayPaint)
                            }
                        }
                        val span = width.coerceAtLeast(height) * 1.5f
                        nativeCanvas.drawLine(vehicleScreenPos.x - span, vehicleScreenPos.y, vehicleScreenPos.x + span, vehicleScreenPos.y, overlayPaint)
                        nativeCanvas.drawLine(vehicleScreenPos.x, vehicleScreenPos.y - span, vehicleScreenPos.x, vehicleScreenPos.y + span, overlayPaint)
                    }
                    FogGridOverlay.HEX_SCANLINES -> {
                        var y = 0f
                        overlayPaint.pathEffect = null
                        while (y < height) {
                            nativeCanvas.drawLine(0f, y, width, y, overlayPaint)
                            y += 28f
                        }
                    }
                    FogGridOverlay.CROSSHAIRS_ONLY -> {
                        overlayPaint.pathEffect = null
                        var x = 60f
                        while (x < width) {
                            var y = 60f
                            while (y < height) {
                                val dx = x - vehicleScreenPos.x
                                val dy = y - vehicleScreenPos.y
                                if (dx * dx + dy * dy > screenBeamRadius * screenBeamRadius) {
                                    nativeCanvas.drawLine(x - 8f, y, x + 8f, y, overlayPaint)
                                    nativeCanvas.drawLine(x, y - 8f, x, y + 8f, overlayPaint)
                                }
                                y += 120f
                            }
                            x += 120f
                        }
                    }
                    FogGridOverlay.BURNT_EMBERS -> {
                        val emberPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#FF6D00")
                            alpha = 140
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        for (angle in 0 until 360 step 20) {
                            val rad = Math.toRadians(angle.toDouble())
                            val offsetDist = screenBeamRadius + 15f + ((angle * 7) % 25)
                            val ex = vehicleScreenPos.x + offsetDist * cos(rad).toFloat()
                            val ey = vehicleScreenPos.y + offsetDist * sin(rad).toFloat()
                            nativeCanvas.drawCircle(ex, ey, 3.5f, emberPaint)
                        }
                    }
                    FogGridOverlay.SMOKE_PARTICLES -> {
                        val mistPaint = android.graphics.Paint().apply {
                            color = fogSkin.perimeterGlowColor
                            alpha = (30 * fogOpacity).toInt().coerceIn(10, 50)
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        for (i in 0 until 12) {
                            val angle = i * 30
                            val rad = Math.toRadians(angle.toDouble())
                            val rDist = screenBeamRadius * (1.4f + (i % 3) * 0.4f)
                            val mx = vehicleScreenPos.x + rDist * cos(rad).toFloat()
                            val my = vehicleScreenPos.y + rDist * sin(rad).toFloat()
                            nativeCanvas.drawCircle(mx, my, screenBeamRadius * 0.45f, mistPaint)
                        }
                    }
                    FogGridOverlay.CLEAN_NONE -> {
                        // Total clean blackout void
                    }
                }

                // 7. Off-Screen Radar Tracker Beacon (If user panned away from vehicle)
                if (vehicleScreenPos.x !in 0f..width || vehicleScreenPos.y !in 0f..height) {
                    val dx = vehicleScreenPos.x - width / 2f
                    val dy = vehicleScreenPos.y - height / 2f
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    val edgeMargin = 45f
                    val clampedX = (width / 2f + (width / 2f - edgeMargin) * cos(angle).toFloat()).coerceIn(edgeMargin, width - edgeMargin)
                    val clampedY = (height / 2f + (height / 2f - edgeMargin) * sin(angle).toFloat()).coerceIn(edgeMargin, height - edgeMargin)
                    val distKm = UkOsmDataEngine.calculateDistanceKm(cameraLat, cameraLon, currentLat, currentLon)

                    val trackerAura = android.graphics.Paint().apply {
                        color = fogSkin.perimeterGlowColor
                        alpha = 100
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    nativeCanvas.drawCircle(clampedX, clampedY, 18f, trackerAura)

                    val trackerCore = android.graphics.Paint().apply {
                        color = fogSkin.perimeterGlowColor
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    nativeCanvas.drawCircle(clampedX, clampedY, 9f, trackerCore)

                    val trackerText = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 22f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        isAntiAlias = true
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val tStr = "RADAR: %.1f km".format(distKm)
                    val tw = trackerText.measureText(tStr)
                    nativeCanvas.drawText(tStr, (clampedX - tw / 2f).coerceIn(10f, width - tw - 10f), (clampedY - 22f).coerceAtLeast(30f), trackerText)
                }
            }

            // -----------------------------------------------------------------
            // G. DRAW WAYPOINT ROUTE CORRIDORS (If destination selected)
            // -----------------------------------------------------------------
            selectedWaypoint?.let { wp ->
                val wpScreenPos = project(wp.first, wp.second)
                // Route line from vehicle to waypoint
                drawLine(
                    color = Color(mapSkin.routeColor),
                    start = vehicleScreenPos,
                    end = wpScreenPos,
                    strokeWidth = 4f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
                )
                // Target Beacon Marker
                drawCircle(color = Color(mapSkin.routeColor), radius = 10f, center = wpScreenPos)
                drawCircle(color = Color(mapSkin.routeColor).copy(alpha = 0.4f), radius = 22f, center = wpScreenPos, style = Stroke(2f))
            }

            // -----------------------------------------------------------------
            // H. DRAW UK SETTLEMENTS & LABELS
            // -----------------------------------------------------------------
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (zoom * 1.5f).coerceIn(16f, 26f)
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
            val townPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = (zoom * 1.1f).coerceIn(12f, 18f)
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            }

            for (place in UkOsmDataEngine.MAJOR_CITIES) {
                val sc = project(place.lat, place.lon)
                if (sc.x in -60f..(width + 60f) && sc.y in -60f..(height + 60f)) {
                    val radius = if (place.isCity) 5f else 3.5f
                    val col = if (place.name == "London") Color(mapSkin.highwayColor) else Color(mapSkin.routeColor)
                    drawCircle(color = col, radius = radius, center = sc)
                    drawCircle(color = col.copy(alpha = 0.35f), radius = radius * 2.2f, center = sc, style = Stroke(1.5f))

                    // Draw text label on canvas
                    if (zoom >= 8.5f || place.isCity) {
                        nativeCanvas.drawText(
                            place.name,
                            sc.x + 8f,
                            sc.y + 4f,
                            if (place.isCity) labelPaint else townPaint
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // I. DRAW DUAL DIRECTION VECTORS (GPS Motion Vector vs Device Compass)
            // -----------------------------------------------------------------
            val radGps = Math.toRadians((currentHeading - 90.0).toDouble())
            val radDev = Math.toRadians((deviceHeading - 90.0).toDouble())

            // Vector lengths anchored to ground meters so they scale up naturally when zooming in (never shrink on zoom in)
            // 28 meters GPS forward projection + speed boost; 24 meters device compass probe
            val groundMetersGps = (28f + currentSpeedKmh * 0.8f)
            val groundMetersDev = (24f + currentSpeedKmh * 0.6f)

            val gpsVectorLen = (groundMetersGps * pixelsPerMeter).coerceIn(48f, 500f)
            val devVectorLen = (groundMetersDev * pixelsPerMeter).coerceIn(42f, 440f)

            val tipGpsX = vehicleScreenPos.x + gpsVectorLen * cos(radGps).toFloat()
            val tipGpsY = vehicleScreenPos.y + gpsVectorLen * sin(radGps).toFloat()

            val tipDevX = vehicleScreenPos.x + devVectorLen * cos(radDev).toFloat()
            val tipDevY = vehicleScreenPos.y + devVectorLen * sin(radDev).toFloat()

            // 1. Vector 1: GPS Motion Vector Line (Cyber Cyan #00E5FF)
            val gpsVectorPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                strokeWidth = 3.5f
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val gpsAuraPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                alpha = 90
                strokeWidth = 8f
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
            }
            nativeCanvas.drawLine(vehicleScreenPos.x, vehicleScreenPos.y, tipGpsX, tipGpsY, gpsAuraPaint)
            nativeCanvas.drawLine(vehicleScreenPos.x, vehicleScreenPos.y, tipGpsX, tipGpsY, gpsVectorPaint)

            // Tip Arrowhead for GPS Vector
            val gpsTipPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            nativeCanvas.drawCircle(tipGpsX, tipGpsY, 4.5f, gpsTipPaint)

            // 2. Vector 2: Handset Device Compass Azimuth Line (Tactical Amber #FF9100)
            val devVectorPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FF9100")
                strokeWidth = 2.8f
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = android.graphics.Paint.Cap.ROUND
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 6f), 0f)
            }
            val devAuraPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FF9100")
                alpha = 80
                strokeWidth = 7f
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
            }
            nativeCanvas.drawLine(vehicleScreenPos.x, vehicleScreenPos.y, tipDevX, tipDevY, devAuraPaint)
            nativeCanvas.drawLine(vehicleScreenPos.x, vehicleScreenPos.y, tipDevX, tipDevY, devVectorPaint)

            // Tip Diamond for Device Compass
            val devTipPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FF9100")
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val diamondPath = android.graphics.Path().apply {
                moveTo(tipDevX, tipDevY - 5f)
                lineTo(tipDevX + 5f, tipDevY)
                lineTo(tipDevX, tipDevY + 5f)
                lineTo(tipDevX - 5f, tipDevY)
                close()
            }
            nativeCanvas.drawPath(diamondPath, devTipPaint)

            // 3. Drift Angle Arc & Telemetry (if angular discrepancy > 6° and moving)
            val driftDiff = ((currentHeading - deviceHeading + 540f) % 360f) - 180f
            if (kotlin.math.abs(driftDiff) > 6f && currentSpeedKmh > 5f) {
                val driftArcPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 140
                    strokeWidth = 1.8f
                    style = android.graphics.Paint.Style.STROKE
                    isAntiAlias = true
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
                }
                val arcRadius = 45f
                val oval = android.graphics.RectF(
                    vehicleScreenPos.x - arcRadius,
                    vehicleScreenPos.y - arcRadius,
                    vehicleScreenPos.x + arcRadius,
                    vehicleScreenPos.y + arcRadius
                )
                val startAngle = (deviceHeading - 90f + 360f) % 360f
                nativeCanvas.drawArc(oval, startAngle, driftDiff, false, driftArcPaint)
            }

            // Labels at tips if moving or zoomed in
            if (currentSpeedKmh > 10f || zoom >= 13f) {
                val vectorLabelPaint = android.graphics.Paint().apply {
                    textSize = 18f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                    setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
                }

                vectorLabelPaint.color = android.graphics.Color.parseColor("#00E5FF")
                nativeCanvas.drawText("GPS %.0f°".format(currentHeading), tipGpsX + 8f, tipGpsY + 4f, vectorLabelPaint)

                vectorLabelPaint.color = android.graphics.Color.parseColor("#FF9100")
                nativeCanvas.drawText("DEV %.0f°".format(deviceHeading), tipDevX + 8f, tipDevY + 4f, vectorLabelPaint)
            }

            // 4. Vehicle GPS Cursor & Heading Cone Reticle
            drawCircle(color = Color(mapSkin.vehicleCursorColor).copy(alpha = 0.25f), radius = 24f, center = vehicleScreenPos)
            val cursorPath = Path().apply {
                val headRad = Math.toRadians((currentHeading - 90.0).toDouble())
                val tipX = vehicleScreenPos.x + 16f * cos(headRad).toFloat()
                val tipY = vehicleScreenPos.y + 16f * sin(headRad).toFloat()
                val leftX = vehicleScreenPos.x + 10f * cos(headRad + 2.5).toFloat()
                val leftY = vehicleScreenPos.y + 10f * sin(headRad + 2.5).toFloat()
                val rightX = vehicleScreenPos.x + 10f * cos(headRad - 2.5).toFloat()
                val rightY = vehicleScreenPos.y + 10f * sin(headRad - 2.5).toFloat()

                moveTo(tipX, tipY)
                lineTo(leftX, leftY)
                lineTo(vehicleScreenPos.x, vehicleScreenPos.y)
                lineTo(rightX, rightY)
                close()
            }
            drawPath(path = cursorPath, color = Color(mapSkin.vehicleCursorColor))
        }

        // =====================================================================
        // 2. TOP FLOATING SEARCH & UK CITY JUMP BAR
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp)),
                color = TacticalSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = ReconCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "United Kingdom OSM • Tap map to drop waypoint",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    // Offline pack badge
                    Surface(
                        color = TacticalObsidian,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReconGreen)
                    ) {
                        Text(
                            text = "UK OSM",
                            color = ReconGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Active OSM, Fog Status & Layer Mode Status Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active Status Chip
                Surface(
                    color = TacticalSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ReconGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (hasOfflineData) "OFFLINE PACK ACTIVE • UK DATA READY" else "OSM ACTIVE • NO API KEY REQUIRED",
                            color = ReconCyan,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Fog Shroud Status Chip (Interactive Toggle)
                Surface(
                    color = TacticalSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isFogLayerActive) Color(fogSkin.perimeterGlowColor) else TacticalBorder
                    ),
                    modifier = Modifier.clickable { isFogLayerActive = !isFogLayerActive }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFogLayerActive) "🌫️ FOG: ${fogRange}m (${(fogOpacity * 100).toInt()}%)" else "👁️ FOG: OFF",
                            color = if (isFogLayerActive) Color(fogSkin.perimeterGlowColor) else TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Layer Mode Selector Chip
                Surface(
                    color = TacticalSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconAmber.copy(alpha = 0.8f)),
                    modifier = Modifier.clickable {
                        mapRenderingMode = when (mapRenderingMode) {
                            "HYBRID" -> "VECTOR"
                            "VECTOR" -> "TILES"
                            else -> "HYBRID"
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MODE: $mapRenderingMode",
                            color = ReconAmber,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Jump Chips: Dynamically populated from User's Saved Destinations (Zero Hardcoded Places)
            val userSavedDestinations by com.recondrive.app.ReconApplication.instance.savedDestinationRepository.destinations.collectAsState()
            if (userSavedDestinations.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (dest in userSavedDestinations) {
                        Surface(
                            color = TacticalSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                            modifier = Modifier.clickable {
                                cameraLat = dest.lat
                                cameraLon = dest.lon
                                zoom = 14.5f
                                isFollowingGps = false
                                selectedWaypoint = dest.lat to dest.lon
                                waypointName = dest.name
                            }
                        ) {
                            val label = if (!dest.personName.isNullOrBlank()) "${dest.personName}: ${dest.name}" else dest.name
                            Text(
                                text = "📍 $label",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. RIGHT FLOATING TACTICAL CONTROLS (Google Maps-style)
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compass Bearing (Reset North)
            FloatingActionButton(
                onClick = { /* Compass reset */ },
                containerColor = TacticalSurface,
                contentColor = ReconCyan,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Navigation, contentDescription = "Compass", modifier = Modifier.size(18.dp))
            }

            // Fog of War Layer Toggle
            FloatingActionButton(
                onClick = { isFogLayerActive = !isFogLayerActive },
                containerColor = if (isFogLayerActive) ReconCyan else TacticalSurface,
                contentColor = if (isFogLayerActive) TacticalObsidian else TextSecondary,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (isFogLayerActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Fog Toggle",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Map Rendering Layer Mode Toggle (HYBRID / VECTOR / TILES)
            FloatingActionButton(
                onClick = {
                    mapRenderingMode = when (mapRenderingMode) {
                        "HYBRID" -> "VECTOR"
                        "VECTOR" -> "TILES"
                        else -> "HYBRID"
                    }
                },
                containerColor = if (mapRenderingMode == "VECTOR") ReconGreen else TacticalSurface,
                contentColor = if (mapRenderingMode == "VECTOR") TacticalObsidian else ReconCyan,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = "Layer Mode", modifier = Modifier.size(18.dp))
            }

            // Skin Selector Shortcut
            FloatingActionButton(
                onClick = onOpenSkins,
                containerColor = TacticalSurface,
                contentColor = Color(mapSkin.highwayColor),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(imageVector = Icons.Default.Palette, contentDescription = "Skins", modifier = Modifier.size(18.dp))
            }

            // Location Timeline & Drift Vector Telemetry Sheet Shortcut
            FloatingActionButton(
                onClick = onOpenTimeline,
                containerColor = TacticalSurface,
                contentColor = ReconGreen,
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, ReconGreen.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Timeline, contentDescription = "Location Timeline", modifier = Modifier.size(18.dp))
            }

            // Zoom In (+)
            FloatingActionButton(
                onClick = { zoom = (zoom + 1.0f).coerceAtMost(18.5f) },
                containerColor = TacticalSurface,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            // Zoom Out (−)
            FloatingActionButton(
                onClick = { zoom = (zoom - 1.0f).coerceAtLeast(6.0f) },
                containerColor = TacticalSurface,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GPS Re-Center "My Location" Floating Action Button
            FloatingActionButton(
                onClick = {
                    cameraLat = currentLat
                    cameraLon = currentLon
                    zoom = 13.5f
                    isFollowingGps = true
                },
                containerColor = if (isFollowingGps) ReconCyan else TacticalSurface,
                contentColor = if (isFollowingGps) TacticalObsidian else ReconCyan,
                shape = CircleShape,
                modifier = Modifier
                    .size(50.dp)
                    .border(
                        1.dp,
                        if (isFollowingGps) ReconCyan else ReconCyan.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // =====================================================================
        // 4. BOTTOM TELEMETRY & SCALE OVERLAY
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
        ) {
            Surface(
                color = TacticalSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = "Z%.1f • %.4f°N, %.4f°W".format(zoom, cameraLat, abs(cameraLon)),
                        color = ReconCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFollowingGps) "LOCK: GPS VEHICLE" else "PAN: FREE ROAM",
                        color = if (isFollowingGps) ReconGreen else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val liveDrift = ((currentHeading - deviceHeading + 540f) % 360f) - 180f
                    val speedMph = currentSpeedKmh * 0.621371f
                    Text(
                        text = "SPD: %.0f MPH • GPS: %.0f° • COMPASS: %.0f° • Δ %+.0f°".format(speedMph, currentHeading, deviceHeading, liveDrift),
                        color = if (abs(liveDrift) > 15f) ReconAmber else ReconCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFogLayerActive) "FOG: ${fogRange}m @ ${(fogOpacity * 100).toInt()}% • v${com.recondrive.app.BuildConfig.VERSION_NAME}" else "FOG: SHROUD OFF • v${com.recondrive.app.BuildConfig.VERSION_NAME}",
                        color = if (isFogLayerActive) Color(fogSkin.perimeterGlowColor) else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // =====================================================================
        // 5. WAYPOINT TARGET SELECTION SHEET (Appears when map is tapped)
        // =====================================================================
        AnimatedVisibility(
            visible = selectedWaypoint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp)
        ) {
            selectedWaypoint?.let { wp ->
                val distKm = UkOsmDataEngine.calculateDistanceKm(currentLat, currentLon, wp.first, wp.second)
                val etaMinutes = ((distKm / 60.0) * 60).toInt().coerceAtLeast(1)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReconCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color(mapSkin.routeColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "TACTICAL DESTINATION",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = waypointName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            IconButton(onClick = { selectedWaypoint = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val distMi = distKm * 0.621371
                        Text(
                            text = "Coords: %.4f°N, %.4f°W  •  Distance: %.1f mi (%.1f km)  •  ETA: ~%d min".format(wp.first, abs(wp.second), distMi, distKm, etaMinutes),
                            color = ReconCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onNavigateToWaypoint(wp.first, wp.second)
                                    selectedWaypoint = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ReconCyan),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = TacticalObsidian, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NAVIGATE HERE",
                                    color = TacticalObsidian,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            OutlinedButton(
                                onClick = { selectedWaypoint = null },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(text = "CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
