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
    val detectedLon: Double? = null
) {
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
