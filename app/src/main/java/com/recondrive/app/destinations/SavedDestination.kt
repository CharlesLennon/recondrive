package com.recondrive.app.destinations

import org.json.JSONObject

/**
 * Tactical Saved Destination / Location
 * Supports standalone locations (Home, Work) as well as sub-lists grouped by Person (e.g. Annabell).
 * Displays photos when available, with graceful icon-only fallback.
 */
data class SavedDestination(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,                    // e.g. "Home", "Work", "Design Studio", "Penthouse"
    val addressOrSubtext: String = "",   // e.g. "18 Brunswick Square, Hove"
    val lat: Double,
    val lon: Double,
    val personName: String? = null,      // e.g. "Annabell" (null if top-level personal saved place)
    val photoUri: String? = null,        // Content URI, file path, or web URL (only shown if present)
    val iconType: String = "PIN",        // "HOME", "WORK", "STAR", "HEART", "PIN", "COFFEE", "GYM", "AIRPORT"
    val orderIndex: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("addressOrSubtext", addressOrSubtext)
        put("lat", lat)
        put("lon", lon)
        if (personName != null) put("personName", personName)
        if (photoUri != null) put("photoUri", photoUri)
        put("iconType", iconType)
        put("orderIndex", orderIndex)
    }

    companion object {
        fun fromJson(json: JSONObject): SavedDestination {
            return SavedDestination(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                name = json.optString("name", "Waypoint"),
                addressOrSubtext = json.optString("addressOrSubtext", ""),
                lat = json.optDouble("lat", 50.8225),
                lon = json.optDouble("lon", -0.1372),
                personName = if (json.has("personName") && !json.isNull("personName")) json.optString("personName") else null,
                photoUri = if (json.has("photoUri") && !json.isNull("photoUri")) json.optString("photoUri") else null,
                iconType = json.optString("iconType", "PIN"),
                orderIndex = json.optInt("orderIndex", 0)
            )
        }
    }
}
