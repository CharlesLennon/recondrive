package com.recondrive.app.skin

import android.graphics.Color

/**
 * Definition of a Map Skin (OSM Base Layer Styling).
 * Completely independent from the Fog Skin.
 */
enum class MapSkin(
    val id: String,
    val displayName: String,
    val description: String,
    val backgroundColorHex: String,
    val waterColorHex: String,
    val landColorHex: String,
    val minorRoadColorHex: String,
    val majorRoadColorHex: String,
    val highwayColorHex: String,
    val routeColorHex: String,
    val vehicleCursorColorHex: String,
    val labelColorHex: String
) {
    TACTICAL_OBSIDIAN(
        id = "tactical_obsidian",
        displayName = "Tactical Obsidian",
        description = "High-contrast stealth black with electric cyan corridors and deep navy water.",
        backgroundColorHex = "#0A0D12",
        waterColorHex = "#06131D",
        landColorHex = "#0E141E",
        minorRoadColorHex = "#1C2636",
        majorRoadColorHex = "#00A8CC",
        highwayColorHex = "#00E5FF",
        routeColorHex = "#FFD600",
        vehicleCursorColorHex = "#00E5FF",
        labelColorHex = "#8B949E"
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        displayName = "Cyberpunk 2077",
        description = "Pitch-black void with screaming magenta arteries and electric cyan secondary roads.",
        backgroundColorHex = "#05050A",
        waterColorHex = "#0D0826",
        landColorHex = "#120A20",
        minorRoadColorHex = "#2E104D",
        majorRoadColorHex = "#00F0FF",
        highwayColorHex = "#FF007F",
        routeColorHex = "#FFE600",
        vehicleCursorColorHex = "#FF007F",
        labelColorHex = "#E0B0FF"
    ),
    MILITARY_TOPO(
        id = "military_topo",
        displayName = "Military Topo",
        description = "Tactical olive drab terrain, desert tan arteries, and phosphor green waypoints.",
        backgroundColorHex = "#141A12",
        waterColorHex = "#0E1C1A",
        landColorHex = "#1A2218",
        minorRoadColorHex = "#2C3528",
        majorRoadColorHex = "#B58451",
        highwayColorHex = "#D4A373",
        routeColorHex = "#70E000",
        vehicleCursorColorHex = "#70E000",
        labelColorHex = "#A3B18A"
    ),
    MIDNIGHT_STEALTH(
        id = "midnight_stealth",
        displayName = "Midnight Monochrome",
        description = "Understated minimalist graphite and charcoal tones with pure white active routing.",
        backgroundColorHex = "#0E0E10",
        waterColorHex = "#18181C",
        landColorHex = "#141416",
        minorRoadColorHex = "#24242A",
        majorRoadColorHex = "#4A4A55",
        highwayColorHex = "#828292",
        routeColorHex = "#FFFFFF",
        vehicleCursorColorHex = "#FFFFFF",
        labelColorHex = "#9A9AA6"
    ),
    CLASSIC_OSM_LIGHT(
        id = "classic_osm_light",
        displayName = "Classic OSM Daylight",
        description = "Traditional bright paper cartography with soft blue rivers and orange highways.",
        backgroundColorHex = "#F2EFE9",
        waterColorHex = "#AAD3DF",
        landColorHex = "#E0E8D0",
        minorRoadColorHex = "#FFFFFF",
        majorRoadColorHex = "#FED066",
        highwayColorHex = "#FCA765",
        routeColorHex = "#0066FF",
        vehicleCursorColorHex = "#D90429",
        labelColorHex = "#404040"
    ),
    ANTIQUE_PARCHMENT(
        id = "antique_parchment",
        displayName = "Vintage Cartographer",
        description = "18th-century aged parchment canvas with dark walnut engraved linework.",
        backgroundColorHex = "#EADBBF",
        waterColorHex = "#9FB7A8",
        landColorHex = "#E2D0B0",
        minorRoadColorHex = "#C8B594",
        majorRoadColorHex = "#735137",
        highwayColorHex = "#4A321E",
        routeColorHex = "#9E2A2B",
        vehicleCursorColorHex = "#8B1E0F",
        labelColorHex = "#3A281A"
    );

    val backgroundColor: Int get() = Color.parseColor(backgroundColorHex)
    val waterColor: Int get() = Color.parseColor(waterColorHex)
    val landColor: Int get() = Color.parseColor(landColorHex)
    val minorRoadColor: Int get() = Color.parseColor(minorRoadColorHex)
    val majorRoadColor: Int get() = Color.parseColor(majorRoadColorHex)
    val highwayColor: Int get() = Color.parseColor(highwayColorHex)
    val routeColor: Int get() = Color.parseColor(routeColorHex)
    val vehicleCursorColor: Int get() = Color.parseColor(vehicleCursorColorHex)
    val labelColor: Int get() = Color.parseColor(labelColorHex)

    companion object {
        fun fromId(id: String?): MapSkin = values().firstOrNull { it.id == id } ?: TACTICAL_OBSIDIAN
    }
}
