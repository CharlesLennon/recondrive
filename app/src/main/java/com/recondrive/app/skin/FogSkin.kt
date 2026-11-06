package com.recondrive.app.skin

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.PathEffect

enum class FogGridOverlay {
    RADAR_RINGS,
    HEX_SCANLINES,
    SMOKE_PARTICLES,
    BURNT_EMBERS,
    CROSSHAIRS_ONLY,
    CLEAN_NONE
}

/**
 * Definition of a Fog of War Skin.
 * Controls the opacity, shroud texture, perimeter laser glow, and explored path illumination.
 * Completely independent from the Map Skin.
 */
enum class FogSkin(
    val id: String,
    val displayName: String,
    val description: String,
    val shroudColorHex: String,
    val shroudOpacity: Float,
    val perimeterGlowColorHex: String,
    val perimeterGlowRadius: Float,
    val borderStrokeWidth: Float,
    val isDashedBorder: Boolean,
    val exploredRoadGlowColorHex: String,
    val gridOverlay: FogGridOverlay
) {
    RADAR_TACTICAL(
        id = "radar_tactical",
        displayName = "Tactical Radar Shroud",
        description = "Military radar shroud with pulsating cyan laser perimeter and range rings.",
        shroudColorHex = "#070A0F",
        shroudOpacity = 0.98f,
        perimeterGlowColorHex = "#00E5FF",
        perimeterGlowRadius = 14f,
        borderStrokeWidth = 3f,
        isDashedBorder = true,
        exploredRoadGlowColorHex = "#00E676",
        gridOverlay = FogGridOverlay.RADAR_RINGS
    ),
    ATMOSPHERIC_MIST(
        id = "atmospheric_mist",
        displayName = "Dense Rolling Fog",
        description = "Natural atmospheric mist with soft feathered cloud borders and vapor particles.",
        shroudColorHex = "#161B22",
        shroudOpacity = 0.88f,
        perimeterGlowColorHex = "#8B949E",
        perimeterGlowRadius = 24f,
        borderStrokeWidth = 1.5f,
        isDashedBorder = false,
        exploredRoadGlowColorHex = "#58A6FF",
        gridOverlay = FogGridOverlay.SMOKE_PARTICLES
    ),
    CYBER_HAZARD(
        id = "cyber_hazard",
        displayName = "Matrix Glitch Void",
        description = "Electric neon green grid with flashing warning perimeter and glitch scanlines.",
        shroudColorHex = "#020408",
        shroudOpacity = 1.0f,
        perimeterGlowColorHex = "#FF0055",
        perimeterGlowRadius = 18f,
        borderStrokeWidth = 4f,
        isDashedBorder = true,
        exploredRoadGlowColorHex = "#39FF14",
        gridOverlay = FogGridOverlay.HEX_SCANLINES
    ),
    BURNT_EMBERS(
        id = "burnt_embers",
        displayName = "Charred Parchment",
        description = "Burnt edges burning away to reveal the world with smoldering amber glow.",
        shroudColorHex = "#1A110B",
        shroudOpacity = 0.95f,
        perimeterGlowColorHex = "#FF5400",
        perimeterGlowRadius = 20f,
        borderStrokeWidth = 3f,
        isDashedBorder = false,
        exploredRoadGlowColorHex = "#FFD166",
        gridOverlay = FogGridOverlay.BURNT_EMBERS
    ),
    DEEP_BLACKOUT(
        id = "deep_blackout",
        displayName = "Total Sensory Blackout",
        description = "Zero-bleed 100% opaque darkness with razor-sharp ice blue laser cutoff.",
        shroudColorHex = "#000000",
        shroudOpacity = 1.0f,
        perimeterGlowColorHex = "#7DF9FF",
        perimeterGlowRadius = 8f,
        borderStrokeWidth = 2f,
        isDashedBorder = false,
        exploredRoadGlowColorHex = "#FFFFFF",
        gridOverlay = FogGridOverlay.CLEAN_NONE
    ),
    RETRO_VECTOR(
        id = "retro_vector",
        displayName = "80s CRT Phosphor",
        description = "Vintage monochrome phosphor amber with CRT scanline raster and crosshairs.",
        shroudColorHex = "#120B00",
        shroudOpacity = 0.94f,
        perimeterGlowColorHex = "#FFB703",
        perimeterGlowRadius = 16f,
        borderStrokeWidth = 2.5f,
        isDashedBorder = true,
        exploredRoadGlowColorHex = "#FB8500",
        gridOverlay = FogGridOverlay.CROSSHAIRS_ONLY
    );

    val shroudColor: Int get() = Color.parseColor(shroudColorHex)
    val perimeterGlowColor: Int get() = Color.parseColor(perimeterGlowColorHex)
    val exploredRoadGlowColor: Int get() = Color.parseColor(exploredRoadGlowColorHex)

    fun createPathEffect(): PathEffect? {
        return if (isDashedBorder) DashPathEffect(floatArrayOf(12f, 6f), 0f) else null
    }

    companion object {
        fun fromId(id: String?): FogSkin = values().firstOrNull { it.id == id } ?: RADAR_TACTICAL
    }
}
