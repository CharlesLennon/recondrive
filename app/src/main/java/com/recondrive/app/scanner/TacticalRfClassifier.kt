package com.recondrive.app.scanner

import java.util.Locale

/**
 * Tactical RF Spectrum Intelligence Classifier.
 * Identifies nearby vehicles (Tesla, Ford, BMW, Dashcams) and surveillance optics (Hikvision, Dahua, Ring, ANPR)
 * from Wi-Fi SSIDs, Bluetooth LE advertisements, and MAC address OUIs.
 */
object TacticalRfClassifier {

    private val OUI_MAP = mapOf(
        "4C:FC:AA" to "Tesla Motors",
        "98:ED:5C" to "Tesla Motors",
        "B0:72:BF" to "Tesla Motors",
        "F4:F5:DB" to "Tesla Motors",
        "38:18:4C" to "Tesla Motors",
        "00:1C:12" to "Ford Motor Company",
        "00:26:7E" to "Ford Motor Company",
        "44:19:B6" to "Hikvision Digital Technology",
        "C0:56:E3" to "Hikvision Digital Technology",
        "E0:50:8B" to "Hikvision Digital Technology",
        "DC:68:EB" to "Hikvision Digital Technology",
        "58:03:FB" to "Hikvision Digital Technology",
        "3C:EF:8C" to "Dahua Technology",
        "E4:AA:EC" to "Dahua Technology",
        "90:02:A9" to "Dahua Technology",
        "00:62:EC" to "Amazon Ring",
        "50:F1:4A" to "Amazon Ring",
        "18:B4:30" to "Google Nest",
        "64:16:66" to "Google Nest"
    )

    data class ClassificationResult(
        val category: RfCategory,
        val targetName: String,
        val vendor: String,
        val threatLevel: String,
        val details: String
    )

    fun classify(ssidOrBleName: String, macAddress: String): ClassificationResult {
        val s = ssidOrBleName.trim()
        val sLower = s.lowercase(Locale.ROOT)
        val macPrefix = macAddress.uppercase(Locale.ROOT).take(8)
        val knownVendor = OUI_MAP[macPrefix]

        // 1. Vehicles: Tesla
        if (sLower.contains("tesla") || sLower.contains("cybertruck") || knownVendor == "Tesla Motors") {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "TESLA ($s)" else "TESLA VEHICLE DETECTED",
                vendor = "Tesla Motors, Inc.",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "In-vehicle Wi-Fi gateway / BLE phone-key transceiver active."
            )
        }

        // 2. Vehicles: In-Car Dashcams (VIOFO, BlackVue, Nextbase, 70mai, Thinkware, Garmin)
        if (sLower.contains("viofo")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "VIOFO 4K AUTOMOTIVE DASHCAM",
                vendor = "VIOFO Ltd.",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Vehicle forward/rear facing video surveillance active."
            )
        }
        if (sLower.contains("blackvue")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "BLACKVUE CLOUD DASHCAM",
                vendor = "Pittasoft BlackVue",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Cloud-connected vehicle surveillance camera."
            )
        }
        if (sLower.contains("nextbase")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "NEXTBASE AUTOMOTIVE DASHCAM",
                vendor = "Nextbase UK",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "High-definition automotive optics."
            )
        }
        if (sLower.contains("70mai")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "70MAI SMART DASHCAM",
                vendor = "70mai Xiaomi Ecosystem",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "In-cabin smart dashcam Wi-Fi AP."
            )
        }
        if (sLower.contains("garmindash") || sLower.contains("garmin dash")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "GARMIN AUTOMOTIVE DASH CAM",
                vendor = "Garmin Ltd.",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Garmin vehicular optic recorder."
            )
        }
        if (sLower.contains("thinkware")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "THINKWARE VEHICLE DASH CAM",
                vendor = "Thinkware Corp",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Automotive road recording system."
            )
        }

        // 3. Vehicles: Ford, GM, BMW, Audi, Mercedes, Toyota
        if (sLower.contains("ford") || sLower.contains("sync") || sLower.contains("fordpass") || sLower.contains("lincoln") || knownVendor == "Ford Motor Company") {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "FORD SYNC VEHICLE INFOTAINMENT",
                vendor = "Ford Motor Company",
                threatLevel = "MOBILE TELEMETRY",
                details = "Ford in-vehicle SYNC cockpit network."
            )
        }
        if (sLower.contains("onstar") || sLower.contains("chevrolet") || sLower.contains("chevy") || sLower.contains("mylink") || sLower.contains("cadillac") || sLower.contains("gmc")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "GM ONSTAR / CHEVROLET VEHICLE",
                vendor = "General Motors",
                threatLevel = "MOBILE TELEMETRY",
                details = "GM vehicular telematics uplink."
            )
        }
        if (sLower.contains("bmw") || sLower.contains("connecteddrive") || sLower.contains("mini_connected")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "BMW CONNECTEDDRIVE VEHICLE",
                vendor = "Bayerische Motoren Werke AG",
                threatLevel = "MOBILE TELEMETRY",
                details = "BMW automotive network."
            )
        }
        if (sLower.contains("audi") || sLower.contains("mmi") || sLower.contains("volkswagen") || sLower.contains("vw_bt") || sLower.contains("vw_car") || sLower.contains("porsche")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "VAG MMI / AUDI VEHICLE LINK",
                vendor = "Volkswagen AG",
                threatLevel = "MOBILE TELEMETRY",
                details = "Audi / VW in-car digital cockpit AP."
            )
        }
        if (sLower.contains("mbux") || sLower.contains("mercedes") || sLower.contains("mb_bt")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "MERCEDES MBUX VEHICLE COCKPIT",
                vendor = "Mercedes-Benz AG",
                threatLevel = "MOBILE TELEMETRY",
                details = "Mercedes-Benz vehicular telemetry AP."
            )
        }
        if (sLower.contains("toyota") || sLower.contains("lexus") || sLower.contains("entune")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = "TOYOTA / LEXUS VEHICLE NETWORK",
                vendor = "Toyota Motor Corporation",
                threatLevel = "MOBILE TELEMETRY",
                details = "Automotive telemetry link."
            )
        }

        // 4. CCTV & Surveillance: Hikvision, Dahua, Ring, Nest, ANPR, Traffic
        if (sLower.contains("hikvision") || sLower.contains("ds-2cd") || sLower.contains("ezviz") || knownVendor?.contains("Hikvision") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "HIKVISION IP SURVEILLANCE CCTV",
                vendor = "Hangzhou Hikvision Digital Tech",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Active commercial IP CCTV camera stream."
            )
        }
        if (sLower.contains("dahua") || sLower.contains("dh_") || sLower.contains("imou") || knownVendor?.contains("Dahua") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "DAHUA SECURITY CCTV OPTIC",
                vendor = "Zhejiang Dahua Technology",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Security surveillance camera unit."
            )
        }
        if (sLower.contains("ring-") || sLower.contains("ring_") || sLower.contains("ring ") || knownVendor?.contains("Ring") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "AMAZON RING SMART DOORBELL / CAM",
                vendor = "Amazon Ring LLC",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "Residential security video sensor."
            )
        }
        if (sLower.contains("nestcam") || sLower.contains("dropcam") || knownVendor?.contains("Google Nest") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "GOOGLE NEST SECURITY CAMERA",
                vendor = "Google Nest",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "HD optic camera telemetry."
            )
        }
        if (sLower.contains("eufy") || sLower.contains("reolink") || sLower.contains("arlo") || sLower.contains("wyze") || sLower.contains("blink") || sLower.contains("amcrest")) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "WIRELESS CCTV SURVEILLANCE OPTIC",
                vendor = "Security Optic Vendor",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "Wireless security camera AP detected."
            )
        }
        if (sLower.contains("anpr") || sLower.contains("trafficcam") || sLower.contains("speedcam") || sLower.contains("gatso") || sLower.contains("redflex") || sLower.contains("truvelo")) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = "MUNICIPAL ANPR / SPEED CAMERA",
                vendor = "Highways & Traffic Enforcement",
                threatLevel = "HIGHWAY ENFORCEMENT OPTIC",
                details = "Automatic Number Plate Recognition / Speed Radar telemetry."
            )
        }

        // 5. Aerial Drones (DJI)
        if (sLower.contains("dji") || sLower.contains("mavic") || sLower.contains("phantom") || sLower.contains("inspire")) {
            return ClassificationResult(
                category = RfCategory.DRONE_UAV,
                targetName = "DJI AERIAL DRONE (DIRECT REMOTE ID)",
                vendor = "DJI Innovations",
                threatLevel = "AERIAL UAV IN AIRSPACE",
                details = "Active airborne unmanned aerial vehicle."
            )
        }

        // Default AP
        return ClassificationResult(
            category = RfCategory.ACCESS_POINT,
            targetName = if (s.isNotBlank()) s else "UNKNOWN 802.11 AP",
            vendor = knownVendor ?: "Standard Wi-Fi Infrastructure",
            threatLevel = "INFORMATIONAL",
            details = "Local area wireless emitter."
        )
    }

    /**
     * Estimates line-of-sight distance in meters from RSSI using log-distance path loss.
     */
    fun estimateDistance(rssi: Int): Double {
        if (rssi >= 0) return 1.0
        val measuredPowerAt1m = -45.0
        val pathLossExponent = 2.4
        val ratio = (measuredPowerAt1m - rssi) / (10.0 * pathLossExponent)
        val rawDistance = Math.pow(10.0, ratio)
        return (Math.round(rawDistance * 10.0) / 10.0).coerceIn(0.5, 120.0)
    }
}
