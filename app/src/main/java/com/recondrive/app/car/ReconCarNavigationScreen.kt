package com.recondrive.app.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.model.*
import androidx.core.graphics.drawable.IconCompat
import com.recondrive.app.ReconApplication
import com.recondrive.app.navigation.ManeuverType
import com.recondrive.app.navigation.NavigationStep
import timber.log.Timber

/**
 * Android Auto In-Vehicle Navigation Screen
 * Displays turn-by-turn guidance templates over our hardware-accelerated surface map.
 */
class ReconCarNavigationScreen(carContext: CarContext) : Screen(carContext) {

    private val surfaceRenderer = CarSurfaceRenderer(
        carContext,
        ReconApplication.instance.fogGridManager
    )

    private var activeStep = NavigationStep(
        stepIndex = 1,
        maneuverType = ManeuverType.SLIGHT_RIGHT,
        instruction = "Bear slight right onto Recon Tactical Byway",
        roadName = "Recon Tactical Byway",
        distanceMeters = 400.0,
        targetLat = 51.5120,
        targetLon = -0.1220
    )

    private var isFogLayerActive = true

    init {
        // Bind custom hardware surface renderer to car display
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceRenderer)
            Timber.i("Registered CarSurfaceRenderer with Android Auto AppManager")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register CarSurfaceRenderer")
        }
    }

    override fun onGetTemplate(): Template {
        // Construct Navigation Maneuver Icon
        val maneuver = Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_RIGHT)
            .build()

        val step = Step.Builder(activeStep.instruction)
            .setManeuver(maneuver)
            .setRoad(activeStep.roadName)
            .build()

        val routingInfo = RoutingInfo.Builder()
            .setCurrentStep(step, Distance.create(activeStep.distanceMeters, Distance.UNIT_METERS))
            .build()

        // Travel Estimate (Remaining 14.2 km, 18 min ETA)
        val travelEstimate = TravelEstimate.Builder(
            Distance.create(14.2, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(System.currentTimeMillis() + 18 * 60 * 1000, java.util.TimeZone.getDefault())
        ).build()

        val skinManager = ReconApplication.instance.skinManager
        val activeMap = skinManager.currentMapSkin.value
        val activeFog = skinManager.currentFogSkin.value

        // In-Car Action Strip with independent Map and Fog skin toggles
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Map: ${activeMap.displayName.take(8)}")
                    .setOnClickListener {
                        val next = skinManager.cycleNextMapSkin()
                        CarToast.makeText(carContext, "Map Skin: ${next.displayName}", CarToast.LENGTH_SHORT).show()
                        surfaceRenderer.renderFrame()
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Fog: ${activeFog.displayName.take(8)}")
                    .setOnClickListener {
                        val next = skinManager.cycleNextFogSkin()
                        CarToast.makeText(carContext, "Fog Skin: ${next.displayName}", CarToast.LENGTH_SHORT).show()
                        surfaceRenderer.renderFrame()
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Intel")
                    .setOnClickListener {
                        val stats = ReconApplication.instance.fogGridManager.stats.value
                        CarToast.makeText(
                            carContext,
                            "Rank: ${stats.explorationRank.title} • Area: %.1f km²".format(stats.totalAreaKm2),
                            CarToast.LENGTH_LONG
                        ).show()
                    }
                    .build()
            )
            .build()

        // Assemble NavigationTemplate
        return NavigationTemplate.Builder()
            .setNavigationInfo(routingInfo)
            .setDestinationTravelEstimate(travelEstimate)
            .setActionStrip(actionStrip)
            .build()
    }
}
