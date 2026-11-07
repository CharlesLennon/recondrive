package com.recondrive.app.scanner

import android.bluetooth.BluetoothClass
import java.util.Locale

/**
 * Tactical RF Spectrum Intelligence Classifier.
 * Identifies nearby vehicles (Tesla, Ford, BMW, Dashcams, OBD-II, TPMS),
 * surveillance optics (Hikvision, Dahua, Ring, ANPR), and aerial drones
 * from Wi-Fi SSIDs, Bluetooth LE advertisements, Bluetooth Classic classes, and MAC address OUIs.
 */
object TacticalRfClassifier {

    // Known Organizationally Unique Identifiers (OUIs) for vehicles, optics, and tactical emitters
    private val OUI_MAP = mapOf(
        // Tesla Motors
        "4C:FC:AA" to "Tesla Motors",
        "98:ED:5C" to "Tesla Motors",
        "B0:72:BF" to "Tesla Motors",
        "F4:F5:DB" to "Tesla Motors",
        "38:18:4C" to "Tesla Motors",
        "60:57:18" to "Tesla Motors",
        "00:01:48" to "Tesla Motors",
        
        // Ford Motor Company
        "00:1C:12" to "Ford Motor Company",
        "00:26:7E" to "Ford Motor Company",
        "E4:90:7E" to "Ford Motor Company",
        "FC:C2:DE" to "Ford Motor Company",

        // BMW & Mini
        "00:01:A9" to "Bayerische Motoren Werke (BMW)",
        "E0:BA:E8" to "Bayerische Motoren Werke (BMW)",
        "00:1A:7D" to "Bayerische Motoren Werke (BMW)",

        // Volkswagen / Audi / Porsche / VAG
        "00:0C:E7" to "Audi / Volkswagen AG",
        "00:26:5A" to "Audi / Volkswagen AG",
        "A4:D1:8C" to "Volkswagen AG",
        "68:9E:19" to "Audi AG",

        // Mercedes-Benz / Daimler
        "00:1E:B2" to "Mercedes-Benz AG",
        "00:21:CC" to "Mercedes-Benz AG",
        "40:B0:34" to "Mercedes-Benz AG",

        // Toyota / Lexus
        "00:12:1C" to "Toyota Motor Corp",
        "20:47:DA" to "Toyota Motor Corp",

        // General Motors / OnStar
        "00:19:01" to "General Motors / OnStar",
        "00:24:E4" to "General Motors",

        // Dashcams & Action Cameras
        "9C:8E:99" to "VIOFO Dashcams",
        "70:B3:D5" to "VIOFO Dashcams",
        "D8:96:85" to "GoPro Inc.",
        "F4:DD:9E" to "GoPro Inc.",

        // Aerial Drones & UAVs
        "60:60:1F" to "DJI Innovations",
        "48:1C:B9" to "DJI Innovations",
        "60:55:F9" to "DJI Innovations",
        "34:D2:62" to "DJI Innovations",
        "90:03:B7" to "Parrot Drones",

        // CCTV & Surveillance Optics
        "44:19:B6" to "Hikvision Digital Tech",
        "C0:56:E3" to "Hikvision Digital Tech",
        "E0:50:8B" to "Hikvision Digital Tech",
        "DC:68:EB" to "Hikvision Digital Tech",
        "58:03:FB" to "Hikvision Digital Tech",
        "B4:FB:E4" to "Hikvision / ANPR Optic",
        "3C:EF:8C" to "Dahua Technology",
        "E4:AA:EC" to "Dahua Technology",
        "90:02:A9" to "Dahua Technology",
        "54:C4:15" to "Dahua Technology",
        "00:62:EC" to "Amazon Ring",
        "50:F1:4A" to "Amazon Ring",
        "B8:38:61" to "Amazon Ring",
        "18:B4:30" to "Google Nest",
        "64:16:66" to "Google Nest",
        "00:40:8C" to "Axis Communications",
        "AC:CC:8E" to "Axis Communications",
        "00:16:6C" to "Hanwha Techwin (Samsung CCTV)",

        // Automated License Plate Readers (ALPR / ANPR) & Law Enforcement RF
        "B4:1E:52" to "Flock Safety ALPR",
        "00:03:7F" to "Flock Safety / Qualcomm",
        "00:20:4A" to "Lantronix (Flock ALPR SoM)",
        "00:80:A3" to "Lantronix (Flock ALPR SoM)",
        "00:25:DF" to "Axon Enterprise (Police Signal)",
        "00:19:15" to "Axon Enterprise (Police Signal)",
        "00:0A:28" to "Motorola Solutions / Vigilant ALPR",
        "00:1B:44" to "Motorola Solutions / ALPR",
        "00:1F:F8" to "Siemens / Yunex Traffic ANPR",
        "00:0C:4D" to "Jenoptik / Gatso Speed Enforcement",

        // IoT, Single Board Computers & Beacon Hardware
        "24:0A:C4" to "Espressif Systems (ESP32 IoT)",
        "30:AE:A4" to "Espressif Systems (ESP32 IoT)",
        "84:0D:8E" to "Espressif Systems (ESP32 IoT)",
        "B8:27:EB" to "Raspberry Pi Foundation",
        "DC:A6:32" to "Raspberry Pi Foundation",
        "E4:5F:01" to "Raspberry Pi Foundation",

        // Consumer Tech & Mobile
        "00:88:65" to "Apple Inc.",
        "AC:63:BE" to "Apple Inc.",
        "F0:18:98" to "Apple Inc.",
        "00:12:47" to "Samsung Electronics",
        "00:15:99" to "Samsung Electronics"
    )

    data class ClassificationResult(
        val category: RfCategory,
        val targetName: String,
        val vendor: String,
        val threatLevel: String,
        val details: String,
        val protocolOverride: String? = null
    )

    /**
     * Comprehensive multi-sensor tactical classifier.
     */
    fun classify(
        ssidOrBleName: String,
        macAddress: String,
        bluetoothClass: BluetoothClass? = null,
        serviceUuids: List<String> = emptyList(),
        manufacturerId: Int? = null,
        manufacturerData: ByteArray? = null
    ): ClassificationResult {
        val s = ssidOrBleName.trim()
        val sLower = s.lowercase(Locale.ROOT)
        val macPrefix = macAddress.uppercase(Locale.ROOT).take(8)
        val knownVendor = OUI_MAP[macPrefix]
        val isRandomMac = isLocallyAdministeredMac(macAddress)

        // =====================================================================
        // 1. BLUETOOTH CLASSIC MAJOR DEVICE CLASS (Vehicles, Audio, Telephony)
        // =====================================================================
        if (bluetoothClass != null) {
            val devClass = bluetoothClass.deviceClass
            val isCarAudioOrHandsfree = devClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    devClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            if (isCarAudioOrHandsfree) {
                return ClassificationResult(
                    category = RfCategory.VEHICLE,
                    targetName = if (s.isNotBlank()) "VEHICLE BT ($s)" else "AUTOMOTIVE HANDS-FREE SYSTEM",
                    vendor = knownVendor ?: "Automotive In-Cabin System",
                    threatLevel = "CONNECTED VEHICLE TELEMETRY",
                    details = "Bluetooth Classic BR/EDR vehicle infotainment / hands-free cockpit transceiver.",
                    protocolOverride = "Bluetooth Classic (Car Audio)"
                )
            }
        }

        // =====================================================================
        // 2. DIGITAL CAR KEYS & PHONE-AS-A-KEY (Tesla, BMW, Ford, UWB, CCC 3.0)
        // =====================================================================
        val hasTeslaUuid = serviceUuids.any { it.contains("0211", ignoreCase = true) || it.contains("0212", ignoreCase = true) }
        val isExplicitCarKey = sLower.contains("digitalkey") || sLower.contains("paak") || sLower.contains("carkey") ||
                sLower.contains("keyfob") || sLower.contains("tesla_key") || sLower.contains("bmw_key") ||
                sLower.contains("comfortaccess") || sLower.contains("fordpass_key") || sLower.contains("genesickey")

        if (hasTeslaUuid || sLower.contains("tesla_key") || sLower.contains("tesla_keyfob")) {
            return ClassificationResult(
                category = RfCategory.CAR_KEY,
                targetName = if (s.isNotBlank()) "TESLA DIGITAL KEY ($s)" else "TESLA BLE VEHICLE KEY / FOB",
                vendor = "Tesla Motors, Inc.",
                threatLevel = "ACTIVE VEHICLE KEY",
                details = "Tesla active Phone-as-a-Key transceiver (Service UUID 0x0211) emitting in proximity.",
                protocolOverride = "BLE 5.0 (Tesla Key Transceiver)"
            )
        }

        if (isExplicitCarKey) {
            val keyMake = when {
                sLower.contains("bmw") -> "BMW"
                sLower.contains("ford") -> "FORD"
                sLower.contains("genesis") -> "GENESIS"
                sLower.contains("kia") -> "KIA"
                sLower.contains("tesla") -> "TESLA"
                else -> "DIGITAL"
            }
            return ClassificationResult(
                category = RfCategory.CAR_KEY,
                targetName = "$keyMake DIGITAL CAR KEY ($s)",
                vendor = "$keyMake Motor Corp",
                threatLevel = "ACTIVE VEHICLE KEY",
                details = "Digital vehicle key / Phone-as-a-Key transceiver emitting in physical proximity.",
                protocolOverride = "BLE / UWB Digital Key"
            )
        }

        if (sLower.contains("tesla") || sLower.contains("cybertruck") || knownVendor?.contains("Tesla") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "TESLA ($s)" else "TESLA VEHICLE DETECTED",
                vendor = "Tesla Motors, Inc.",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "In-vehicle Wi-Fi gateway / BLE transceiver active."
            )
        }

        // =====================================================================
        // 3. VEHICLE OBD-II TELEMETRY & TPMS (CAN-Bus Dongles & Tire Sensors)
        // =====================================================================
        if (sLower.contains("obd") || sLower.contains("elm327") || sLower.contains("veepeak") ||
            sLower.contains("carista") || sLower.contains("vgate") || sLower.contains("lelink") ||
            sLower.contains("bafx") || sLower.contains("obdlink")
        ) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "OBD-II DIAGNOSTIC ($s)" else "OBD-II VEHICLE TELEMETRY DONGLE",
                vendor = "Automotive CAN-Bus Interface",
                threatLevel = "VEHICLE CAN-BUS TELEMETRY",
                details = "On-Board Diagnostics port wireless adapter emitting vehicle telemetry.",
                protocolOverride = "Bluetooth CAN-Bus Link"
            )
        }

        if (sLower.contains("tpms") || sLower.contains("tire") || sLower.contains("schrader")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "BLE TPMS ($s)" else "TIRE PRESSURE SENSOR (TPMS)",
                vendor = "Automotive Wheel Sensor",
                threatLevel = "VEHICLE SENSOR EMITTER",
                details = "Direct BLE Tire Pressure Monitoring System valve transmitter.",
                protocolOverride = "BLE TPMS Sensor"
            )
        }

        // =====================================================================
        // 4. AUTOMOTIVE DASHCAMS (VIOFO, BlackVue, Nextbase, 70mai, Thinkware)
        // =====================================================================
        if (sLower.contains("viofo") || knownVendor?.contains("VIOFO") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "VIOFO DASHCAM ($s)" else "VIOFO 4K AUTOMOTIVE DASHCAM",
                vendor = "VIOFO Ltd.",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Vehicle forward/rear facing video surveillance active."
            )
        }
        if (sLower.contains("blackvue")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "BLACKVUE DASHCAM ($s)" else "BLACKVUE CLOUD DASHCAM",
                vendor = "Pittasoft BlackVue",
                threatLevel = "SURVEILLANCE OPTIC",
                details = "Cloud-connected vehicle surveillance camera."
            )
        }
        if (sLower.contains("nextbase")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "NEXTBASE DASHCAM ($s)" else "NEXTBASE AUTOMOTIVE DASHCAM",
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

        // =====================================================================
        // 5. VEHICLE MANUFACTURERS (Ford, BMW, Audi, Mercedes, Toyota, GM, etc.)
        // =====================================================================
        if (sLower.contains("ford") || sLower.contains("sync") || sLower.contains("fordpass") || sLower.contains("lincoln") || knownVendor?.contains("Ford") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "FORD SYNC ($s)" else "FORD SYNC VEHICLE INFOTAINMENT",
                vendor = "Ford Motor Company",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "Ford in-vehicle SYNC cockpit network."
            )
        }
        if (sLower.contains("bmw") || sLower.contains("connecteddrive") || sLower.contains("mini_connected") || knownVendor?.contains("BMW") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "BMW VEHICLE ($s)" else "BMW CONNECTEDDRIVE VEHICLE",
                vendor = "Bayerische Motoren Werke AG",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "BMW automotive network."
            )
        }
        if (sLower.contains("audi") || sLower.contains("mmi") || sLower.contains("volkswagen") || sLower.contains("vw_bt") || sLower.contains("vw_car") || sLower.contains("porsche") || knownVendor?.contains("Volkswagen") == true || knownVendor?.contains("Audi") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "VAG / AUDI ($s)" else "VAG MMI / AUDI VEHICLE LINK",
                vendor = "Volkswagen AG",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "Audi / VW in-car digital cockpit AP."
            )
        }
        if (sLower.contains("mbux") || sLower.contains("mercedes") || sLower.contains("mb_bt") || knownVendor?.contains("Mercedes") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "MERCEDES MBUX ($s)" else "MERCEDES MBUX VEHICLE COCKPIT",
                vendor = "Mercedes-Benz AG",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "Mercedes-Benz vehicular telemetry AP."
            )
        }
        if (sLower.contains("toyota") || sLower.contains("lexus") || sLower.contains("entune") || knownVendor?.contains("Toyota") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "TOYOTA / LEXUS ($s)" else "TOYOTA / LEXUS VEHICLE NETWORK",
                vendor = "Toyota Motor Corporation",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "Automotive telemetry link."
            )
        }
        if (sLower.contains("onstar") || sLower.contains("chevrolet") || sLower.contains("chevy") || sLower.contains("mylink") || sLower.contains("cadillac") || sLower.contains("gmc") || knownVendor?.contains("General Motors") == true) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "GM ONSTAR ($s)" else "GM ONSTAR / CHEVROLET VEHICLE",
                vendor = "General Motors",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "GM vehicular telematics uplink."
            )
        }
        if (sLower.contains("nissan") || sLower.contains("honda") || sLower.contains("hyundai") || sLower.contains("kia") || sLower.contains("volvo") || sLower.contains("mazda") || sLower.contains("subaru") || sLower.contains("jaguar") || sLower.contains("land rover")) {
            return ClassificationResult(
                category = RfCategory.VEHICLE,
                targetName = if (s.isNotBlank()) "VEHICLE LINK ($s)" else "PASSENGER VEHICLE COCKPIT",
                vendor = "Automotive Manufacturer",
                threatLevel = "CONNECTED VEHICLE TELEMETRY",
                details = "Passenger automobile cockpit wireless transceiver in range."
            )
        }

        // =====================================================================
        // 6. AERIAL DRONES & UAV REMOTE ID (ASTM F3411 / Open Drone ID / DJI)
        // =====================================================================
        val hasDroneRemoteId = serviceUuids.any { it.contains("fffa", ignoreCase = true) }
        if (sLower.contains("dji") || sLower.contains("mavic") || sLower.contains("phantom") ||
            sLower.contains("inspire") || sLower.contains("remoteid") || sLower.contains("autel") ||
            sLower.contains("skydio") || knownVendor?.contains("DJI") == true || hasDroneRemoteId
        ) {
            val droneDesc = if (hasDroneRemoteId) {
                "Active airborne drone broadcasting FAA/CAA Open Drone ID (UUID 0xFFFA)."
            } else {
                "Active airborne unmanned aerial vehicle telemetry."
            }
            return ClassificationResult(
                category = RfCategory.DRONE_UAV,
                targetName = if (s.isNotBlank()) "UAV DRONE ($s)" else "DJI AERIAL DRONE (DIRECT REMOTE ID)",
                vendor = knownVendor ?: "UAV Drone Manufacturer",
                threatLevel = "AERIAL UAV IN AIRSPACE",
                details = droneDesc,
                protocolOverride = if (hasDroneRemoteId) "BLE OpenDroneID (ASTM F3411)" else "BLE / Wi-Fi Drone Telemetry"
            )
        }

        // =====================================================================
        // 7. CCTV & SURVEILLANCE OPTICS (Hikvision, Dahua, Ring, Nest, ANPR)
        // =====================================================================
        if (sLower.contains("hikvision") || sLower.contains("ds-2cd") || sLower.contains("ezviz") || knownVendor?.contains("Hikvision") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "HIKVISION CCTV ($s)" else "HIKVISION IP SURVEILLANCE CCTV",
                vendor = "Hangzhou Hikvision Digital Tech",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Active commercial IP CCTV camera stream."
            )
        }
        if (sLower.contains("dahua") || sLower.contains("dh_") || sLower.contains("imou") || knownVendor?.contains("Dahua") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "DAHUA CCTV ($s)" else "DAHUA SECURITY CCTV OPTIC",
                vendor = "Zhejiang Dahua Technology",
                threatLevel = "FIXED SURVEILLANCE OPTIC",
                details = "Security surveillance camera unit."
            )
        }
        if (sLower.contains("ring-") || sLower.contains("ring_") || sLower.contains("ring ") || knownVendor?.contains("Ring") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "AMAZON RING ($s)" else "AMAZON RING SMART DOORBELL / CAM",
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
        // Automated License Plate Readers (Flock Safety, Vigilant, Axon, Municipal ANPR)
        if (sLower.contains("flock") || knownVendor?.contains("Flock") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "FLOCK ALPR ($s)" else "FLOCK SAFETY ALPR CAMERA (FALCON)",
                vendor = "Flock Safety, Inc.",
                threatLevel = "AUTOMATED LICENSE PLATE READER (ALPR)",
                details = "Flock Safety municipal/neighborhood ALPR camera node broadcasting Wi-Fi/BLE telemetry."
            )
        }
        if (sLower.contains("axon") || knownVendor?.contains("Axon") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "AXON POLICE TECH ($s)" else "AXON POLICE IN-VEHICLE / BODY-CAM",
                vendor = "Axon Enterprise, Inc.",
                threatLevel = "LAW ENFORCEMENT RF BEACON",
                details = "Axon Signal Bluetooth LE police trigger / camera telemetry active."
            )
        }
        if (sLower.contains("vigilant") || knownVendor?.contains("Vigilant") == true) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "VIGILANT ALPR ($s)" else "MOTOROLA VIGILANT ALPR CAMERA",
                vendor = "Motorola Solutions / Vigilant",
                threatLevel = "AUTOMATED LICENSE PLATE READER (ALPR)",
                details = "Commercial highway automated license plate recognition camera."
            )
        }
        if (sLower.contains("anpr") || sLower.contains("trafficcam") || sLower.contains("speedcam") ||
            sLower.contains("gatso") || sLower.contains("redflex") || sLower.contains("truvelo") ||
            knownVendor?.contains("ANPR") == true || knownVendor?.contains("Jenoptik") == true
        ) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "ANPR / RADAR ($s)" else "MUNICIPAL ANPR / SPEED CAMERA",
                vendor = "Highways & Traffic Enforcement",
                threatLevel = "HIGHWAY ENFORCEMENT OPTIC",
                details = "Automatic Number Plate Recognition / Speed Radar telemetry."
            )
        }
        if (sLower.contains("eufy") || sLower.contains("reolink") || sLower.contains("arlo") ||
            sLower.contains("wyze") || sLower.contains("blink") || sLower.contains("amcrest") ||
            knownVendor?.contains("Axis") == true
        ) {
            return ClassificationResult(
                category = RfCategory.CCTV_SURVEILLANCE,
                targetName = if (s.isNotBlank()) "CCTV OPTIC ($s)" else "WIRELESS CCTV SURVEILLANCE OPTIC",
                vendor = knownVendor ?: "Security Optic Vendor",
                threatLevel = "PERIMETER SURVEILLANCE",
                details = "Security surveillance camera detected."
            )
        }

        // =====================================================================
        // 8. ASSET TRACKERS & SMART BEACONS (AirTag, SmartTag, Tile)
        // =====================================================================
        // Apple Manufacturer ID: 0x004C (76)
        if (manufacturerId == 0x004C) {
            val isAirTag = manufacturerData != null && manufacturerData.isNotEmpty() && manufacturerData[0] == 0x12.toByte()
            val isAirPods = manufacturerData != null && manufacturerData.isNotEmpty() && manufacturerData[0] == 0x07.toByte()
            val isIBeacon = manufacturerData != null && manufacturerData.size >= 23 && manufacturerData[0] == 0x02.toByte()

            return when {
                isAirTag -> ClassificationResult(
                    category = RfCategory.CAR_KEY,
                    targetName = "SMART CAR KEY FOB (AIRTAG / FINDMY)",
                    vendor = "Apple, Inc.",
                    threatLevel = "VEHICLE KEY FOB TRACKER",
                    details = "Apple AirTag / Find My vehicle key fob offline beacon (payload 0x12) in close proximity.",
                    protocolOverride = "BLE 5.2 (Apple FindMy)"
                )
                isAirPods -> ClassificationResult(
                    category = RfCategory.ACCESS_POINT,
                    targetName = "APPLE AIRPODS / AUDIO BEACON",
                    vendor = "Apple, Inc.",
                    threatLevel = "PERSONAL AUDIO DEVICE",
                    details = "Apple Proximity Pairing audio accessory.",
                    protocolOverride = "BLE Apple Proximity"
                )
                isIBeacon -> ClassificationResult(
                    category = RfCategory.ACCESS_POINT,
                    targetName = "APPLE IBEACON (MICRO-LOCATION)",
                    vendor = "Apple, Inc.",
                    threatLevel = "INFORMATIONAL",
                    details = "Calibrated iBeacon micro-location transmitter.",
                    protocolOverride = "BLE iBeacon"
                )
                else -> ClassificationResult(
                    category = RfCategory.ACCESS_POINT,
                    targetName = if (s.isNotBlank()) "APPLE DEVICE ($s)" else "APPLE BLE ECOSYSTEM BEACON",
                    vendor = "Apple, Inc.",
                    threatLevel = "INFORMATIONAL",
                    details = "Apple Continuity / Proximity advertisement.",
                    protocolOverride = "BLE Apple Ecosystem"
                )
            }
        }

        // Samsung Manufacturer ID: 0x0075 (117)
        if (manufacturerId == 0x0075 || sLower.contains("smarttag")) {
            return ClassificationResult(
                category = RfCategory.CAR_KEY,
                targetName = "SAMSUNG SMARTTAG CAR KEY FOB",
                vendor = "Samsung Electronics",
                threatLevel = "VEHICLE KEY FOB TRACKER",
                details = "Samsung SmartThings Find / Galaxy SmartTag vehicle key tracking beacon.",
                protocolOverride = "BLE 5.0 (SmartThings Find)"
            )
        }

        // Tile Tracker (Manufacturer ID 0x006B or Service UUID 0xFEED)
        val hasTileUuid = serviceUuids.any { it.contains("feed", ignoreCase = true) }
        if (manufacturerId == 0x006B || hasTileUuid || sLower.contains("tile")) {
            return ClassificationResult(
                category = RfCategory.CAR_KEY,
                targetName = "TILE SMART CAR KEY FOB",
                vendor = "Tile, Inc.",
                threatLevel = "VEHICLE KEY FOB TRACKER",
                details = "Tile BLE vehicle key smart tracking beacon.",
                protocolOverride = "BLE Tile Network"
            )
        }

        // Garmin Fitness / Tactical
        if (manufacturerId == 0x0087 || sLower.contains("garmin")) {
            return ClassificationResult(
                category = RfCategory.ACCESS_POINT,
                targetName = if (s.isNotBlank()) "GARMIN ($s)" else "GARMIN TACTICAL / FITNESS SENSOR",
                vendor = "Garmin International",
                threatLevel = "INFORMATIONAL",
                details = "Garmin wireless GPS / telemetry sensor.",
                protocolOverride = "BLE Garmin Telemetry"
            )
        }

        // =====================================================================
        // 9. DEFAULT / UNCLASSIFIED RF EMITTERS
        // =====================================================================
        val targetName = when {
            s.isNotBlank() -> s
            knownVendor != null -> "$knownVendor Emitter"
            isRandomMac -> "BLE EMITTER [MAC ${macAddress.takeLast(8)} • RANDOM]"
            else -> "RF EMITTER [MAC ${macAddress.takeLast(8)}]"
        }

        val threatLevel = if (isRandomMac) "RANDOMIZED MAC" else "INFORMATIONAL"
        val details = when {
            knownVendor != null -> "Detected hardware from $knownVendor."
            isRandomMac -> "Privacy-randomized MAC address emitting live radio frames."
            else -> "Local spectrum radio emitter."
        }

        return ClassificationResult(
            category = RfCategory.ACCESS_POINT,
            targetName = targetName,
            vendor = knownVendor ?: if (isRandomMac) "Randomized / Private MAC" else "Standard RF Infrastructure",
            threatLevel = threatLevel,
            details = details
        )
    }

    /**
     * Backward-compatible overload.
     */
    fun classify(ssidOrBleName: String, macAddress: String): ClassificationResult {
        return classify(ssidOrBleName, macAddress, null, emptyList(), null, null)
    }

    /**
     * Estimates line-of-sight distance in meters from RSSI using log-distance path loss.
     * Takes operating frequency into account:
     * - 2.4 GHz: Tx0 = -45 dBm, path loss exponent n = 2.4
     * - 5.0 GHz: Tx0 = -40 dBm, path loss exponent n = 2.8
     */
    fun estimateDistance(rssi: Int, frequencyMhz: Int = 2412): Double {
        if (rssi >= 0) return 1.0
        val is5Ghz = frequencyMhz > 4900
        val measuredPowerAt1m = if (is5Ghz) -40.0 else -45.0
        val pathLossExponent = if (is5Ghz) 2.8 else 2.4
        val ratio = (measuredPowerAt1m - rssi) / (10.0 * pathLossExponent)
        val rawDistance = Math.pow(10.0, ratio)
        return (Math.round(rawDistance * 10.0) / 10.0).coerceIn(0.5, 120.0)
    }

    /**
     * Checks if a MAC address has the IEEE locally administered bit set (bit 1 of first byte).
     * If true, the MAC address is randomized/ephemeral (e.g. Android/iOS privacy MAC).
     */
    fun isLocallyAdministeredMac(mac: String): Boolean {
        val clean = mac.replace(":", "").replace("-", "")
        if (clean.length < 2) return false
        val firstByte = clean.take(2).toIntOrNull(16) ?: return false
        return (firstByte and 0x02) != 0
    }
}