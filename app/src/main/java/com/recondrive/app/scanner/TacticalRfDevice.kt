package com.recondrive.app.scanner

/**
 * Tactical classification of detected RF emitters in the local spectrum.
 */
enum class RfCategory {
    VEHICLE,
    CCTV_SURVEILLANCE,
    DRONE_UAV,
    ACCESS_POINT
}

/**
 * Real-time motion trend of a target relative to host vehicle.
 */
enum class RelativeMotionTrend {
    APPROACHING,     // Signal increasing / distance closing
    RECEDING,        // Signal decreasing / target moving away
    STATIONARY       // Distance steady / co-moving
}

/**
 * Single observation breadcrumb of a target relative to host device location.
 */
data class DeviceBreadcrumb(
    val timestamp: Long = System.currentTimeMillis(),
    val hostLat: Double,
    val hostLon: Double,
    val relativeBearingDeg: Float,     // Relative angle (0° = straight ahead of host vehicle)
    val distanceMeters: Double,
    val rssi: Int,
    val targetLat: Double,
    val targetLon: Double
)

/**
 * User-saved intelligence and custom annotations for a specific emitter/device.
 */
data class SavedRfDevice(
    val id: String,                         // MAC Address or BSSID (Primary Key)
    val originalName: String,               // Original detected name (e.g. "Tesla Model 3")
    val customNickname: String = "",       // Custom user alias (e.g. "Neighbor's Blue Tesla")
    val numberPlate: String = "",           // UK Vehicle registration mark (e.g. "LJ71 WXA")
    val doorNumberOrAddress: String = "",   // Physical house / door number (e.g. "Door #42")
    val notes: String = "",                 // User notes (e.g. "Parked in driveway facing street")
    val group: String = "SURVEILLANCE",     // Group / Category (e.g. "KNOWN VEHICLES", "NEIGHBORS", "SURVEILLANCE", "WORK", "CUSTOM")
    val alertSound: String = "SONAR_PING",  // "SONAR_PING", "TACTICAL_CHIRP", "RADAR_BEEP", "VOICE_ALERT", "SILENT"
    val notifyOnDetection: Boolean = true,  // Trigger acoustic alert and notification when seen
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val detectionCount: Int = 1,
    val lastObservedLat: Double? = null,
    val lastObservedLon: Double? = null
)

/**
 * A detected RF target identified via Wi-Fi 802.11 beacon frames or Bluetooth LE advertisement packets.
 */
data class DetectedRfTarget(
    val id: String,                         // Unique key (BSSID or BLE address)
    val name: String,                       // Classified name (e.g. "Tesla Model 3/Y", "Hikvision CCTV")
    val rawSsidOrBleName: String,           // Raw SSID or advertised Bluetooth name
    val macAddress: String,                 // MAC address / BSSID with OUI
    val vendor: String,                     // Manufacturer / Vendor (e.g. "Tesla Motors", "Hikvision")
    val category: RfCategory,               // Primary classification
    val rssi: Int,                          // Received Signal Strength Indicator in dBm (-30 to -100)
    val estimatedDistanceMeters: Double,    // Distance estimate in meters calculated from RSSI
    val frequencyMhz: Int = 2412,           // Operating frequency in MHz
    val protocol: String = "Wi-Fi 802.11",  // Protocol e.g. "Wi-Fi 6", "BLE 5.2"
    val threatLevel: String = "INFORMATIONAL", // Threat / Intel level e.g. "SURVEILLANCE OPTIC", "MOBILE TELEMETRY"
    val details: String = "",               // Descriptive intel notes
    val timestamp: Long = System.currentTimeMillis(),
    val detectedLat: Double? = null,
    val detectedLon: Double? = null,
    val relativeBearingDeg: Float = 0f,     // Bearing relative to vehicle heading (0° = ahead)
    val locationHistory: List<DeviceBreadcrumb> = emptyList(), // History trail of observations relative to host
    val motionTrend: RelativeMotionTrend = RelativeMotionTrend.STATIONARY,
    val savedMetadata: SavedRfDevice? = null // Saved notes, number plate, door number, group
) {
    /**
     * Display name taking into account custom user nickname or number plate if saved
     */
    val displayName: String
        get() = when {
            savedMetadata?.customNickname?.isNotBlank() == true -> savedMetadata.customNickname
            savedMetadata?.numberPlate?.isNotBlank() == true -> "[${savedMetadata.numberPlate}] $name"
            else -> name
        }

    /**
     * Normalized signal strength percentage (0% to 100%)
     */
    val signalPercent: Int
        get() = when {
            rssi >= -50 -> 100
            rssi <= -95 -> 5
            else -> (((rssi + 95) / 45.0) * 100).toInt().coerceIn(5, 100)
        }

    /**
     * Tactical signal bars (1 to 4)
     */
    val signalBars: Int
        get() = when {
            rssi >= -60 -> 4
            rssi >= -72 -> 3
            rssi >= -84 -> 2
            else -> 1
        }
}
