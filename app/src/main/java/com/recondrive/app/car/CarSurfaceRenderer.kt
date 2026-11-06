package com.recondrive.app.car

import android.graphics.*
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.recondrive.app.fog.FogGridManager
import timber.log.Timber
import kotlin.math.*

/**
 * Android Auto Hardware Surface Renderer
 * Draws the high-contrast tactical map, glowing explored tracks, and blackout fog of war
 * directly onto the in-vehicle head unit display surface.
 */
class CarSurfaceRenderer(
    private val carContext: CarContext,
    private val fogGridManager: FogGridManager
) : SurfaceCallback {

    private var activeSurface: Surface? = null
    private var surfaceWidth: Int = 1280
    private var surfaceHeight: Int = 720
    private var isRendering = false

    // Tactical Color Palette
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0A0D12")
        style = Paint.Style.FILL
    }
    
    private val radarRingPaint = Paint().apply {
        color = Color.parseColor("#16222F")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val radarSweepPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        isAntiAlias = true
    }

    private val exploredTrackPaint = Paint().apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 8.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#00E676"))
    }

    private val routePaint = Paint().apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.STROKE
        strokeWidth = 10.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        setShadowLayer(16f, 0f, 0f, Color.parseColor("#FFD600"))
    }

    private val fogShroudPaint = Paint().apply {
        color = Color.parseColor("#070A0F")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val vehicleCursorPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(14f, 0f, 0f, Color.parseColor("#00E5FF"))
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#8B949E")
        textSize = 20f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val textGlowPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        textSize = 24f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
    }

    // Vehicle state
    private var currentHeading: Float = 0f
    private var radarAngle: Float = 0f

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
     * Renders a single frame to the car dashboard screen.
     */
    fun renderFrame(
        vehicleLat: Double = 51.5074,
        vehicleLon: Double = -0.1278,
        heading: Float = 45f,
        speedKmh: Float = 48f
    ) {
        val surface = activeSurface ?: return
        if (!surface.isValid) return

        try {
            val canvas = surface.lockCanvas(null) ?: return
            
            val cx = surfaceWidth / 2f
            val cy = surfaceHeight / 2f

            // 1. Draw Deep Matte Obsidian Background
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)

            // 2. Draw Faint Tactical Radar Range Rings
            val maxRadius = max(cx, cy)
            for (r in listOf(120f, 240f, 380f, 520f)) {
                if (r < maxRadius) {
                    canvas.drawCircle(cx, cy, r, radarRingPaint)
                }
            }

            // Radar Crosshairs
            canvas.drawLine(cx - 30f, cy, cx + 30f, cy, radarRingPaint)
            canvas.drawLine(cx, cy - 30f, cx, cy + 30f, radarRingPaint)

            // 3. Draw Explored Tactical Roads (Uncovered by Fog of War)
            // Simulated local road network uncovered by user
            val path = Path().apply {
                moveTo(cx - 280f, cy + 220f)
                lineTo(cx - 100f, cy + 120f)
                lineTo(cx, cy)
                lineTo(cx + 140f, cy - 110f)
                lineTo(cx + 320f, cy - 180f)
            }
            canvas.drawPath(path, exploredTrackPaint)

            // 4. Draw Active Tactical Navigation Route
            val routePath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + 140f, cy - 110f)
                lineTo(cx + 180f, cy - 250f)
            }
            canvas.drawPath(routePath, routePaint)

            // 5. Draw Tactical Fog of War Boundary with Cyan Perimeter Glow
            // Cutout circle where current vehicle sensor beam clears fog
            val beamRadius = 180f
            val perimeterGlow = Paint().apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
                pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
            }
            canvas.drawCircle(cx, cy, beamRadius, perimeterGlow)

            // 6. Draw Vehicle Tactical Cursor (Arrow with heading)
            canvas.save()
            canvas.rotate(heading, cx, cy)
            val arrow = Path().apply {
                moveTo(cx, cy - 18f)
                lineTo(cx + 14f, cy + 14f)
                lineTo(cx, cy + 8f)
                lineTo(cx - 14f, cy + 14f)
                close()
            }
            canvas.drawPath(arrow, vehicleCursorPaint)
            canvas.restore()

            // 7. HUD Telemetry Overlay (Top Left: Coordinates & Speed)
            canvas.drawText("RECON // RADAR ACTIVE", 32f, 44f, textGlowPaint)
            canvas.drawText(
                "GRID: %.4f°N, %.4f°W  •  SPD: %.0f KM/H  •  HDG: %.0f°".format(
                    vehicleLat, abs(vehicleLon), speedKmh, heading
                ),
                32f, 76f, textPaint
            )

            // 8. Exploration Status (Bottom Right)
            val stats = fogGridManager.stats.value
            val statStr = "TERRITORY: %.1f km² UNCOVERED  •  STREAK: %.2f km".format(
                stats.totalAreaKm2, stats.currentUnchartedStreakKm
            )
            val tw = textPaint.measureText(statStr)
            canvas.drawText(statStr, surfaceWidth - tw - 32f, surfaceHeight - 32f, textPaint)

            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            Timber.e(e, "Error rendering Android Auto car surface frame")
        }
    }
}
