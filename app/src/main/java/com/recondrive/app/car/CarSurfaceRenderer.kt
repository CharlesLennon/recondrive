package com.recondrive.app.car

import android.graphics.*
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.recondrive.app.ReconApplication
import com.recondrive.app.fog.FogGridManager
import com.recondrive.app.skin.FogGridOverlay
import com.recondrive.app.skin.FogSkin
import com.recondrive.app.skin.MapSkin
import timber.log.Timber
import kotlin.math.*

/**
 * Android Auto Hardware Surface Renderer with Independent Skin Support
 * Decouples Map Skin (OSM cartography, roads, route) from Fog Skin (shroud, laser glow, radar overlay).
 */
class CarSurfaceRenderer(
    private val carContext: CarContext,
    private val fogGridManager: FogGridManager
) : SurfaceCallback {

    private var activeSurface: Surface? = null
    private var surfaceWidth: Int = 1280
    private var surfaceHeight: Int = 720
    private var isRendering = false

    // Reusable Paints
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val roadMinorPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3.5f; isAntiAlias = true }
    private val roadMajorPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 7.5f; isAntiAlias = true }
    private val roadHighwayPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 12.0f; isAntiAlias = true }
    private val routePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val exploredGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val fogShroudPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val perimeterGlowPaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val overlayPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true }
    private val cursorPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#8B949E")
        textSize = 18f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    private val textHeaderPaint = Paint().apply {
        textSize = 22f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Timber.i("Android Auto Surface Available: ${surfaceContainer.width}x${surfaceContainer.height}")
        activeSurface = surfaceContainer.surface
        surfaceWidth = surfaceContainer.width.coerceAtLeast(800)
        surfaceHeight = surfaceContainer.height.coerceAtLeast(480)
        isRendering = true
        renderFrame()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Timber.i("Android Auto Surface Destroyed")
        activeSurface = null
        isRendering = false
    }

    /**
     * Renders a single frame to the car dashboard screen with active Map & Fog skins.
     */
    fun renderFrame(
        vehicleLat: Double = 51.5074,
        vehicleLon: Double = -0.1278,
        heading: Float = 45f,
        speedKmh: Float = 48f
    ) {
        val surface = activeSurface ?: return
        if (!surface.isValid) return

        val skinManager = ReconApplication.instance.skinManager
        val fogPrefs = ReconApplication.instance.fogPreferences
        val mapSkin: MapSkin = skinManager.currentMapSkin.value
        val fogSkin: FogSkin = skinManager.currentFogSkin.value

        val configuredRangeMeters = fogPrefs.fogRangeMeters.value
        val configuredOpacity = fogPrefs.fogOpacity.value

        try {
            val canvas = surface.lockCanvas(null) ?: return
            
            val cx = surfaceWidth / 2f
            val cy = surfaceHeight / 2f

            // 1. Draw Map Base Canvas (Independent Map Skin)
            bgPaint.color = mapSkin.backgroundColor
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)

            // 2. Draw Vector Roads (Map Skin colors)
            roadMinorPaint.color = mapSkin.minorRoadColor
            roadMajorPaint.color = mapSkin.majorRoadColor
            roadHighwayPaint.color = mapSkin.highwayColor

            // Background minor road network
            canvas.drawLine(0f, cy + 180f, surfaceWidth.toFloat(), cy - 80f, roadMinorPaint)
            canvas.drawLine(cx - 350f, 0f, cx + 200f, surfaceHeight.toFloat(), roadMinorPaint)

            // Major thoroughfares
            val majorPath = Path().apply {
                moveTo(0f, cy + 90f)
                cubicTo(cx - 150f, cy + 90f, cx - 50f, cy - 40f, cx + 300f, cy - 140f)
            }
            canvas.drawPath(majorPath, roadMajorPaint)

            // 3. Draw Explored Discovered Track (Driven by Fog Skin Glow)
            exploredGlowPaint.apply {
                color = fogSkin.exploredRoadGlowColor
                setShadowLayer(fogSkin.perimeterGlowRadius, 0f, 0f, fogSkin.exploredRoadGlowColor)
            }
            val exploredPath = Path().apply {
                moveTo(cx - 280f, cy + 220f)
                lineTo(cx - 100f, cy + 120f)
                lineTo(cx, cy)
                lineTo(cx + 140f, cy - 110f)
                lineTo(cx + 320f, cy - 180f)
            }
            canvas.drawPath(exploredPath, exploredGlowPaint)

            // 4. Draw Active Navigation Route (Driven by Map Skin)
            routePaint.apply {
                color = mapSkin.routeColor
                setShadowLayer(14f, 0f, 0f, mapSkin.routeColor)
            }
            val routePath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + 140f, cy - 110f)
                lineTo(cx + 180f, cy - 250f)
            }
            canvas.drawPath(routePath, routePaint)

            // 5. Draw Tactical Fog of War Shroud & Perimeter (Scaled by user Fog Range & Opacity)
            val beamRadius = (70f + (configuredRangeMeters - 50) * (280f / 450f)).coerceIn(50f, 380f)

            // Outer Fog Shroud (Mask outside cleared radius)
            val shroudPath = Path().apply {
                addRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), Path.Direction.CW)
                addCircle(cx, cy, beamRadius, Path.Direction.CCW)
            }
            fogShroudPaint.apply {
                color = fogSkin.shroudColor
                val effectiveOpacity = (fogSkin.shroudOpacity * configuredOpacity).coerceIn(0.15f, 1.0f)
                alpha = (effectiveOpacity * 255).toInt()
            }
            canvas.drawPath(shroudPath, fogShroudPaint)

            // Perimeter Glowing Boundary
            perimeterGlowPaint.apply {
                color = fogSkin.perimeterGlowColor
                strokeWidth = fogSkin.borderStrokeWidth
                pathEffect = fogSkin.createPathEffect()
                setShadowLayer(fogSkin.perimeterGlowRadius, 0f, 0f, fogSkin.perimeterGlowColor)
            }
            canvas.drawCircle(cx, cy, beamRadius, perimeterGlowPaint)

            // 6. Draw Fog Overlay Texture / Grid (Independent Fog Skin)
            drawFogGridOverlay(canvas, cx, cy, fogSkin)

            // 7. Draw Vehicle Cursor (Map Skin Vehicle Color)
            cursorPaint.apply {
                color = mapSkin.vehicleCursorColor
                setShadowLayer(14f, 0f, 0f, mapSkin.vehicleCursorColor)
            }
            canvas.save()
            canvas.rotate(heading, cx, cy)
            val arrow = Path().apply {
                moveTo(cx, cy - 18f)
                lineTo(cx + 14f, cy + 14f)
                lineTo(cx, cy + 8f)
                lineTo(cx - 14f, cy + 14f)
                close()
            }
            canvas.drawPath(arrow, cursorPaint)
            canvas.restore()

            // 8. HUD Telemetry Overlay
            textHeaderPaint.color = mapSkin.highwayColor
            canvas.drawText("RECON // RADAR ACTIVE", 32f, 44f, textHeaderPaint)

            textPaint.color = mapSkin.labelColor
            val speedMph = speedKmh * 0.621371f
            canvas.drawText(
                "GRID: %.4f°N, %.4f°W  •  SPD: %.0f MPH (%.0f KM/H)  •  HDG: %.0f°".format(
                    vehicleLat, abs(vehicleLon), speedMph, speedKmh, heading
                ),
                32f, 74f, textPaint
            )

            // Bottom Skin Info & Exploration Status
            val skinTag = "MAP: [${mapSkin.displayName}] • FOG: [${fogSkin.displayName}] • RANGE: ${configuredRangeMeters}m • v${com.recondrive.app.BuildConfig.VERSION_NAME}"
            canvas.drawText(skinTag, 32f, surfaceHeight - 32f, textPaint)

            val stats = fogGridManager.stats.value
            val statStr = "TERRITORY: %.1f km² UNCOVERED".format(stats.totalAreaKm2)
            val tw = textPaint.measureText(statStr)
            canvas.drawText(statStr, surfaceWidth - tw - 32f, surfaceHeight - 32f, textPaint)

            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            Timber.e(e, "Error rendering Android Auto car surface frame")
        }
    }

    private fun drawFogGridOverlay(canvas: Canvas, cx: Float, cy: Float, fogSkin: FogSkin) {
        overlayPaint.color = fogSkin.perimeterGlowColor
        overlayPaint.alpha = 50

        when (fogSkin.gridOverlay) {
            FogGridOverlay.RADAR_RINGS -> {
                for (r in listOf(120f, 240f, 380f, 520f)) {
                    canvas.drawCircle(cx, cy, r, overlayPaint)
                }
                canvas.drawLine(cx - 30f, cy, cx + 30f, cy, overlayPaint)
                canvas.drawLine(cx, cy - 30f, cx, cy + 30f, overlayPaint)
            }
            FogGridOverlay.HEX_SCANLINES -> {
                var y = 0f
                while (y < surfaceHeight) {
                    canvas.drawLine(0f, y, surfaceWidth.toFloat(), y, overlayPaint)
                    y += 24f
                }
            }
            FogGridOverlay.CROSSHAIRS_ONLY -> {
                canvas.drawLine(cx - 60f, cy, cx + 60f, cy, overlayPaint)
                canvas.drawLine(cx, cy - 60f, cx, cy + 60f, overlayPaint)
                canvas.drawCircle(cx, cy, 60f, overlayPaint)
            }
            FogGridOverlay.BURNT_EMBERS -> {
                for (angle in 0 until 360 step 30) {
                    val rad = Math.toRadians(angle.toDouble())
                    val ex = cx + (180f + (angle % 20) - 10) * cos(rad).toFloat()
                    val ey = cy + (180f + (angle % 20) - 10) * sin(rad).toFloat()
                    canvas.drawCircle(ex, ey, 3f, overlayPaint)
                }
            }
            FogGridOverlay.SMOKE_PARTICLES -> {
                canvas.drawCircle(cx, cy, 210f, overlayPaint)
                canvas.drawCircle(cx, cy, 240f, overlayPaint)
            }
            FogGridOverlay.CLEAN_NONE -> {
                // Total blackout, zero grid lines
            }
        }
    }
}
