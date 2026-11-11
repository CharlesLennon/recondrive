package com.recondrive.app.scanner

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.*

/**
 * Direct Remote ID (ASTM F3411-19/22a, ASD-STAN prEN 4709-002, and DJI DroneID)
 * Tactical SIGINT Parser.
 *
 * Decodes airborne UAV telemetry:
 *  - Aircraft identity (UAS Serial, ANSI/CTA-2063-A manufacturer, airframe type)
 *  - 3D Flight vectors (Latitude, Longitude, Altitude AGL & MSL, Horizontal speed, Vertical climb/sink, Heading)
 *  - Remote Pilot Ground Station (Pilot coordinates, controller elevation, home point)
 *  - Civil Aviation Authority Registration (CAA Operator ID / Flyer ID)
 *  - GNSS navigation accuracy (horizontal, vertical, speed accuracy metrics)
 *  - Operational perimeter (Swarm count, geofenced radius, ceiling, floor)
 *  - Self-ID mission declarations
 */
object TacticalDroneIdParser {

    private const val OPENDIS_UUID_SHORT = "fffa"

    // UA Type Mapping (ASTM F3411 Table 1)
    private val UA_TYPE_MAP = mapOf(
        0 to "UAV (UNDECLARED)",
        1 to "MULTI-ROTOR / QUADCOPTER",
        2 to "AIRPLANE (FIXED WING)",
        3 to "HELICOPTER",
        4 to "GYROPLANE",
        5 to "HYBRID / VTOL",
        6 to "ORNITHOPTER",
        7 to "GLIDER",
        8 to "KITE",
        9 to "FREE BALLOON",
        10 to "CAPTIVE BALLOON",
        11 to "AIRSHIP",
        12 to "FREE FALL / PARACHUTE",
        13 to "ROCKET",
        14 to "TETHERED POWERED AIRCRAFT",
        15 to "GROUND OBSTACLE"
    )

    // ANSI/CTA-2063-A 4-Character Manufacturer Code Mapping
    private val CTA_MFR_MAP = mapOf(
        "1581" to "DJI (Da-Jiang Innovations)",
        "1668" to "Autel Robotics",
        "1633" to "Skydio",
        "1149" to "Parrot",
        "1748" to "Yuneec",
        "1805" to "Teal Drones",
        "1773" to "Freefly Systems",
        "1828" to "Wingtra",
        "1722" to "Flyability",
        "1617" to "senseFly / AgEagle",
        "1684" to "Delair",
        "1852" to "Quantum-Systems",
        "1941" to "Sony (Airpeak)",
        "1972" to "Inspired Flight",
        "1001" to "FLIR Systems"
    )

    // Horizontal Accuracy (HAcc - ASTM F3411 Table 4)
    private val HORIZ_ACC_MAP = mapOf(
        0 to "Unknown (>18.5 km)",
        1 to "< 18.52 km (10 NM)",
        2 to "< 7.408 km (4 NM)",
        3 to "< 3.704 km (2 NM)",
        4 to "< 1.852 km (1 NM)",
        5 to "< 926 m (0.5 NM)",
        6 to "< 555.6 m (0.3 NM)",
        7 to "< 185.2 m (0.1 NM)",
        8 to "< 92.6 m (0.05 NM)",
        9 to "< 30 m",
        10 to "< 10 m",
        11 to "< 3 m",
        12 to "< 1 m (RTK / Sub-meter)"
    )

    // Vertical Accuracy (VAcc - ASTM F3411 Table 5)
    private val VERT_ACC_MAP = mapOf(
        0 to "Unknown (>150m)",
        1 to "< 150 m",
        2 to "< 45 m",
        3 to "< 25 m",
        4 to "< 10 m",
        5 to "< 3 m",
        6 to "< 1 m (High Precision)"
    )

    // Speed Accuracy (SpdAcc - ASTM F3411 Table 6)
    private val SPEED_ACC_MAP = mapOf(
        0 to "Unknown (>10 m/s)",
        1 to "< 10 m/s",
        2 to "< 3 m/s",
        3 to "< 1 m/s",
        4 to "< 0.3 m/s"
    )

    private val OPERATOR_LOC_MAP = mapOf(
        0 to "TAKEOFF SPOT (HOME POINT)",
        1 to "DYNAMIC LIVE PILOT (FOLLOWS GCS)",
        2 to "FIXED GROUND STATION",
        3 to "PILOT GCS"
    )

    /**
     * Parses BLE scan record service data, manufacturer data, or raw bytes for OpenDroneID frames.
     */
    fun parse(
        serviceDataMap: Map<String, ByteArray>?,
        manufacturerData: ByteArray?,
        rawBytes: ByteArray?
    ): DroneTelemetry? {
        try {
            // 1. Look for standard OpenDroneID Service UUID 0xFFFA in service data
            if (serviceDataMap != null) {
                for ((uuid, data) in serviceDataMap) {
                    if (uuid.contains(OPENDIS_UUID_SHORT, ignoreCase = true) && data.isNotEmpty()) {
                        val parsed = parsePayload(data)
                        if (parsed != null) return parsed
                    }
                }
            }

            // 2. Search raw advertisement bytes for 0xFFFA AD structure (Type 0x16 Service Data)
            if (rawBytes != null && rawBytes.size >= 10) {
                val extracted = extractServiceDataFromRaw(rawBytes, 0xFFFA)
                if (extracted != null && extracted.isNotEmpty()) {
                    val parsed = parsePayload(extracted)
                    if (parsed != null) return parsed
                }
            }

            // 3. Parse manufacturer data for OpenDroneID (0x08CD ASD-STAN) or DJI DroneID (0x0842 / 0x004C)
            if (manufacturerData != null && manufacturerData.size >= 15) {
                val parsed = parsePayload(manufacturerData) ?: parseDjiDroneId(manufacturerData)
                if (parsed != null) return parsed
            }

            // 4. Scan raw advertisement bytes directly for DJI DroneID frame headers (0x10, 0x01, or 0x2637)
            if (rawBytes != null && rawBytes.size >= 25) {
                val parsedDji = parseDjiDroneId(rawBytes)
                if (parsedDji != null) return parsedDji
            }
        } catch (e: Exception) {
            Timber.d("Error parsing OpenDroneID: ${e.message}")
        }
        return null
    }

    /**
     * Parses Wi-Fi Beacon / Wi-Fi NAN scan results for OpenDroneID frames.
     */
    fun parseWifi(
        ssid: String,
        bssid: String,
        informationElements: ByteArray? = null
    ): DroneTelemetry? {
        // Standard OpenDroneID Wi-Fi Beacon uses SSID starting with "RID-" followed by UAS ID
        if (ssid.startsWith("RID-", ignoreCase = true) && ssid.length >= 8) {
            val serial = ssid.substring(4).trim()
            val mfr = lookupManufacturer(serial)
            val model = determineModel(serial, mfr)
            return DroneTelemetry(
                uasId = serial,
                idType = "SERIAL_NUMBER",
                uasType = "MULTI-ROTOR / QUADCOPTER",
                manufacturer = mfr,
                modelName = model,
                protocolStandard = "Wi-Fi Beacon OpenDroneID (ASTM F3411)",
                description = "Direct Remote ID via 802.11 Beacon Broadcast"
            )
        }

        // DJI Wi-Fi Remote ID SSIDs (e.g. DJI-Mini-..., DroneID-...)
        if (ssid.startsWith("DJI-", ignoreCase = true) || ssid.startsWith("DroneID-", ignoreCase = true)) {
            val serial = ssid.substringAfter("-").trim()
            return DroneTelemetry(
                uasId = serial.takeIf { it.isNotBlank() },
                idType = "SERIAL_NUMBER",
                uasType = "MULTI-ROTOR / QUADCOPTER",
                manufacturer = "DJI (Da-Jiang Innovations)",
                modelName = "DJI UAV",
                protocolStandard = "DJI Wi-Fi Remote ID"
            )
        }

        // Check raw Information Elements for OpenDroneID Vendor Specific OUI (FA:0B:BC)
        if (informationElements != null && informationElements.size >= 25) {
            val parsed = extractOdidFromVendorIe(informationElements)
            if (parsed != null) return parsed
        }

        return null
    }

    /**
     * Extracts OpenDroneID payload from 802.11 Vendor Specific Information Elements (OUI FA:0B:BC).
     */
    private fun extractOdidFromVendorIe(ieBytes: ByteArray): DroneTelemetry? {
        var idx = 0
        while (idx + 2 < ieBytes.size) {
            val eid = ieBytes[idx].toInt() and 0xFF
            val len = ieBytes[idx + 1].toInt() and 0xFF
            if (idx + 2 + len > ieBytes.size) break

            // 0xDD = Vendor Specific Element
            if (eid == 0xDD && len >= 5) {
                val b0 = ieBytes[idx + 2].toInt() and 0xFF
                val b1 = ieBytes[idx + 3].toInt() and 0xFF
                val b2 = ieBytes[idx + 4].toInt() and 0xFF

                // ASTM F3411 OUI: 0xFA, 0x0B, 0xBC
                if (b0 == 0xFA && b1 == 0x0B && b2 == 0xBC) {
                    val payload = ByteArray(len - 4)
                    System.arraycopy(ieBytes, idx + 6, payload, 0, len - 4)
                    return parsePayload(payload)
                }
                // DJI OUI: 0x26, 0x37, 0x12
                if (b0 == 0x26 && b1 == 0x37 && b2 == 0x12) {
                    val payload = ByteArray(len - 4)
                    System.arraycopy(ieBytes, idx + 6, payload, 0, len - 4)
                    return parseDjiDroneId(payload)
                }
            }
            idx += 2 + len
        }
        return null
    }

    /**
     * Scans raw BLE AD structures for Complete/Incomplete Service Data (16-bit UUID 0xFFFA).
     */
    private fun extractServiceDataFromRaw(raw: ByteArray, targetUuid16: Int): ByteArray? {
        var idx = 0
        while (idx < raw.size) {
            val length = raw[idx].toInt() and 0xFF
            if (length == 0 || idx + length >= raw.size) break

            val type = raw[idx + 1].toInt() and 0xFF
            // 0x16 = Service Data - 16-bit UUID
            if (type == 0x16 && length >= 3) {
                val uuidLow = raw[idx + 2].toInt() and 0xFF
                val uuidHigh = raw[idx + 3].toInt() and 0xFF
                val uuid = (uuidHigh shl 8) or uuidLow
                if (uuid == targetUuid16) {
                    val payloadLen = length - 3
                    val payload = ByteArray(payloadLen)
                    System.arraycopy(raw, idx + 4, payload, 0, payloadLen)
                    return payload
                }
            }
            idx += length + 1
        }
        return null
    }

    /**
     * Parses an OpenDroneID message or message pack payload.
     */
    fun parsePayload(data: ByteArray): DroneTelemetry? {
        if (data.isEmpty()) return null

        var uasId: String? = null
        var idType = "SERIAL_NUMBER"
        var uasType = "MULTI-ROTOR / QUADCOPTER"
        var manufacturer = "UNKNOWN"
        var modelName = "UAV"
        var ceClass: String? = null

        var isAirborne = true
        var operationalStatus = "AIRBORNE"
        var droneLat: Double? = null
        var droneLon: Double? = null
        var altitudeAgl: Float? = null
        var altitudeMsl: Float? = null
        var heightType = "AGL"
        var speedKmh: Float? = null
        var speedMph: Float? = null
        var verticalSpeedMps: Float? = null
        var headingDeg: Float? = null

        var pilotLat: Double? = null
        var pilotLon: Double? = null
        var pilotAltitudeMsl: Float? = null
        var pilotLocationType = "OPERATOR GCS"

        var operatorId: String? = null
        var operatorIdType = "CAA_OPERATOR_ID"
        var description: String? = null
        var authType: String? = null

        var areaCount = 1
        var areaRadiusMeters: Int? = null
        var areaCeilingMeters: Float? = null
        var areaFloorMeters: Float? = null

        var horizontalAccuracy: String? = null
        var verticalAccuracy: String? = null
        var speedAccuracy: String? = null
        var gpsTimestampSeconds: Float? = null

        val rawHex = data.take(32).joinToString("") { "%02X".format(it) }

        val firstByte = data[0].toInt() and 0xFF
        val msgType = (firstByte shr 4) and 0x0F

        // Check if Message Pack (0xF)
        if (msgType == 0x0F && data.size >= 26) {
            val msgSize = 25
            var offset = 1
            while (offset + msgSize <= data.size) {
                val subMsg = ByteArray(msgSize)
                System.arraycopy(data, offset, subMsg, 0, msgSize)
                val subType = (subMsg[0].toInt() and 0xFF shr 4) and 0x0F
                when (subType) {
                    0x0 -> { // Basic ID
                        val bId = parseBasicId(subMsg)
                        if (bId != null) {
                            uasId = bId.uasId
                            idType = bId.idType
                            uasType = bId.uaType
                            manufacturer = bId.manufacturer
                            modelName = bId.model
                        }
                    }
                    0x1 -> { // Location / Vector
                        val loc = parseLocationVector(subMsg)
                        if (loc != null) {
                            droneLat = loc.lat
                            droneLon = loc.lon
                            altitudeAgl = loc.altitudeAgl
                            altitudeMsl = loc.altitudeMsl
                            heightType = loc.heightType
                            speedKmh = loc.speedKmh
                            speedMph = loc.speedMph
                            verticalSpeedMps = loc.verticalSpeedMps
                            headingDeg = loc.heading
                            isAirborne = loc.isAirborne
                            operationalStatus = loc.operationalStatus
                            horizontalAccuracy = loc.horizontalAccuracy
                            verticalAccuracy = loc.verticalAccuracy
                            speedAccuracy = loc.speedAccuracy
                            gpsTimestampSeconds = loc.gpsTimestampSeconds
                        }
                    }
                    0x2 -> { // Authentication
                        authType = parseAuthMessage(subMsg)
                    }
                    0x3 -> { // Self-ID
                        description = parseSelfId(subMsg)
                    }
                    0x4 -> { // System / Operator
                        val sys = parseSystemMessage(subMsg)
                        if (sys != null) {
                            pilotLat = sys.lat
                            pilotLon = sys.lon
                            pilotAltitudeMsl = sys.altitudeMsl
                            pilotLocationType = sys.locationType
                            ceClass = sys.ceClass
                            areaCount = sys.areaCount
                            areaRadiusMeters = sys.areaRadiusMeters
                            areaCeilingMeters = sys.areaCeilingMeters
                            areaFloorMeters = sys.areaFloorMeters
                        }
                    }
                    0x5 -> { // Operator ID
                        val op = parseOperatorId(subMsg)
                        if (op != null) {
                            operatorId = op.operatorId
                            operatorIdType = op.idType
                        }
                    }
                }
                offset += msgSize
            }
        } else {
            // Single Message (typically 25 bytes)
            when (msgType) {
                0x0 -> { // Basic ID
                    val bId = parseBasicId(data)
                    if (bId != null) {
                        uasId = bId.uasId
                        idType = bId.idType
                        uasType = bId.uaType
                        manufacturer = bId.manufacturer
                        modelName = bId.model
                    }
                }
                0x1 -> { // Location / Vector
                    val loc = parseLocationVector(data)
                    if (loc != null) {
                        droneLat = loc.lat
                        droneLon = loc.lon
                        altitudeAgl = loc.altitudeAgl
                        altitudeMsl = loc.altitudeMsl
                        heightType = loc.heightType
                        speedKmh = loc.speedKmh
                        speedMph = loc.speedMph
                        verticalSpeedMps = loc.verticalSpeedMps
                        headingDeg = loc.heading
                        isAirborne = loc.isAirborne
                        operationalStatus = loc.operationalStatus
                        horizontalAccuracy = loc.horizontalAccuracy
                        verticalAccuracy = loc.verticalAccuracy
                        speedAccuracy = loc.speedAccuracy
                        gpsTimestampSeconds = loc.gpsTimestampSeconds
                    }
                }
                0x2 -> { // Authentication
                    authType = parseAuthMessage(data)
                }
                0x3 -> { // Self-ID
                    description = parseSelfId(data)
                }
                0x4 -> { // System / Operator
                    val sys = parseSystemMessage(data)
                    if (sys != null) {
                        pilotLat = sys.lat
                        pilotLon = sys.lon
                        pilotAltitudeMsl = sys.altitudeMsl
                        pilotLocationType = sys.locationType
                        ceClass = sys.ceClass
                        areaCount = sys.areaCount
                        areaRadiusMeters = sys.areaRadiusMeters
                        areaCeilingMeters = sys.areaCeilingMeters
                        areaFloorMeters = sys.areaFloorMeters
                    }
                }
                0x5 -> { // Operator ID
                    val op = parseOperatorId(data)
                    if (op != null) {
                        operatorId = op.operatorId
                        operatorIdType = op.idType
                    }
                }
            }
        }

        // Return telemetry if any meaningful Drone ID, Location, or Pilot coordinate was parsed
        if (uasId != null || droneLat != null || pilotLat != null || operatorId != null) {
            return DroneTelemetry(
                uasId = uasId,
                idType = idType,
                uasType = uasType,
                manufacturer = manufacturer,
                modelName = modelName,
                ceClass = ceClass,
                droneLat = droneLat,
                droneLon = droneLon,
                isAirborne = isAirborne,
                operationalStatus = operationalStatus,
                altitudeAglMeters = altitudeAgl,
                altitudeMslMeters = altitudeMsl,
                heightType = heightType,
                speedKmh = speedKmh,
                speedMph = speedMph,
                verticalSpeedMps = verticalSpeedMps,
                headingDeg = headingDeg,
                pilotLat = pilotLat,
                pilotLon = pilotLon,
                pilotAltitudeMslMeters = pilotAltitudeMsl,
                pilotLocationType = pilotLocationType,
                operatorId = operatorId,
                operatorIdType = operatorIdType,
                description = description,
                authType = authType,
                areaCount = areaCount,
                areaRadiusMeters = areaRadiusMeters,
                areaCeilingMeters = areaCeilingMeters,
                areaFloorMeters = areaFloorMeters,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speedAccuracy = speedAccuracy,
                gpsTimestampSeconds = gpsTimestampSeconds,
                protocolStandard = "ASTM F3411-22a / Direct Remote ID",
                rawPayloadHex = rawHex,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
        return null
    }

    private data class ParsedBasicId(
        val uasId: String,
        val idType: String,
        val uaType: String,
        val manufacturer: String,
        val model: String
    )

    /**
     * Decodes Basic ID Message (Type 0x0, 25 bytes).
     * Byte 1: ID Type (bits 7..4), UA Type (bits 3..0).
     * Bytes 2..21: UAS ID (20 ASCII chars).
     */
    private fun parseBasicId(msg: ByteArray): ParsedBasicId? {
        if (msg.size < 22) return null
        val idTypeCode = (msg[1].toInt() and 0xFF shr 4) and 0x0F
        val idTypeName = when (idTypeCode) {
            1 -> "SERIAL_NUMBER (ANSI/CTA-2063-A)"
            2 -> "CAA_REGISTRATION_ID"
            3 -> "UTM_ASSIGNED_UUID"
            4 -> "SPECIFIC_SESSION_ID"
            else -> "UAS_ID"
        }

        val uaTypeCode = (msg[1].toInt() and 0xFF) and 0x0F
        val uaTypeName = UA_TYPE_MAP[uaTypeCode] ?: "MULTI-ROTOR / QUADCOPTER"

        val serialBytes = ByteArray(20)
        System.arraycopy(msg, 2, serialBytes, 0, 20)
        val rawSerial = String(serialBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
        if (rawSerial.isBlank() || rawSerial.length < 3) return null

        val mfr = lookupManufacturer(rawSerial)
        val model = determineModel(rawSerial, mfr)

        return ParsedBasicId(
            uasId = rawSerial,
            idType = idTypeName,
            uaType = uaTypeName,
            manufacturer = mfr,
            model = model
        )
    }

    private fun lookupManufacturer(serial: String): String {
        val prefix4 = serial.take(4).uppercase()
        return CTA_MFR_MAP[prefix4] ?: when {
            serial.startsWith("1581", ignoreCase = true) || serial.startsWith("0816") || serial.startsWith("37Q") -> "DJI (Da-Jiang Innovations)"
            serial.startsWith("1668", ignoreCase = true) -> "Autel Robotics"
            serial.startsWith("1633", ignoreCase = true) -> "Skydio"
            serial.startsWith("1149", ignoreCase = true) -> "Parrot"
            serial.startsWith("1748", ignoreCase = true) -> "Yuneec"
            else -> "UNKNOWN"
        }
    }

    private fun determineModel(serial: String, mfr: String): String {
        val sUpper = serial.uppercase()
        return when {
            sUpper.startsWith("1581F4") -> "DJI Mini 4 Pro / Air 3"
            sUpper.startsWith("1581F3") -> "DJI Mini 3 Pro / Mini 3"
            sUpper.startsWith("1581F5") -> "DJI Mavic 3 Enterprise / Thermal"
            sUpper.startsWith("1581F6") -> "DJI Matrice 30 / Matrice 350 RTK"
            sUpper.startsWith("1581F7") -> "DJI Inspire 3"
            sUpper.startsWith("1581F2") -> "DJI Mavic 3 / Cine / Classic"
            sUpper.startsWith("1581F1") -> "DJI Air 2S"
            sUpper.startsWith("1581F0") -> "DJI FPV / Avata"
            sUpper.startsWith("0816") -> "DJI Phantom 4 Pro / RTK"
            sUpper.startsWith("37Q") -> "DJI Matrice 300 RTK"
            sUpper.startsWith("1668") -> "Autel EVO Max / Lite+ / Nano"
            sUpper.startsWith("1633") -> "Skydio 2+ / X2 / X10"
            sUpper.startsWith("1149") -> "Parrot Anafi USA / Ai"
            mfr != "UNKNOWN" -> "$mfr UAV"
            else -> "UAV DRONE"
        }
    }

    private data class ParsedLocation(
        val lat: Double,
        val lon: Double,
        val altitudeAgl: Float?,
        val altitudeMsl: Float?,
        val heightType: String,
        val speedKmh: Float?,
        val speedMph: Float?,
        val verticalSpeedMps: Float?,
        val heading: Float?,
        val isAirborne: Boolean,
        val operationalStatus: String,
        val horizontalAccuracy: String?,
        val verticalAccuracy: String?,
        val speedAccuracy: String?,
        val gpsTimestampSeconds: Float?
    )

    /**
     * Decodes Location / Vector Message (Type 0x1, 25 bytes).
     */
    private fun parseLocationVector(msg: ByteArray): ParsedLocation? {
        if (msg.size < 22) return null
        val buffer = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)

        val statusByte = buffer.get(1).toInt() and 0xFF
        val opStatusCode = (statusByte shr 4) and 0x0F
        val isAirborne = opStatusCode != 0x01 // 0x01 = Ground
        val operationalStatus = when (opStatusCode) {
            1 -> "GROUND / SURFACE"
            2 -> "AIRBORNE"
            3 -> "EMERGENCY ALERT"
            4 -> "REMOTE ID FAILURE"
            else -> if (isAirborne) "AIRBORNE" else "GROUND"
        }

        val heightTypeBit = (statusByte shr 2) and 0x01
        val heightType = if (heightTypeBit == 1) "AGL (Above Ground Level)" else "ATO (Above Takeoff)"

        val dirByte = buffer.get(2).toInt() and 0xFF
        val headingDeg = if (dirByte <= 180) (dirByte * 2).toFloat() else if (dirByte < 255) dirByte.toFloat() else null

        val speedByte = buffer.get(3).toInt() and 0xFF
        val speedKmh = if (speedByte < 255) (speedByte * 0.25f * 3.6f) else null
        val speedMph = speedKmh?.let { it * 0.621371f }

        val vertSpeedByte = buffer.get(4).toInt() // signed 8-bit
        val verticalSpeedMps = if (vertSpeedByte != -128) (vertSpeedByte * 0.5f) else null

        val latRaw = buffer.getInt(5)
        val lonRaw = buffer.getInt(9)

        val lat = latRaw / 1e7
        val lon = lonRaw / 1e7

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 || (lat == 0.0 && lon == 0.0)) {
            return null
        }

        val altMslRaw = buffer.getShort(13).toInt() and 0xFFFF
        val altMslMeters = if (altMslRaw != 0xFFFF) {
            (altMslRaw * 0.5f) - 1000f
        } else null

        val altAglRaw = buffer.getShort(15).toInt() and 0xFFFF
        val altAglMeters = if (altAglRaw != 0xFFFF) {
            (altAglRaw * 0.5f) - 1000f
        } else null

        val accuracyByte = buffer.get(17).toInt() and 0xFF
        val hAccCode = accuracyByte and 0x0F
        val vAccCode = (accuracyByte shr 4) and 0x0F
        val horizontalAccuracy = HORIZ_ACC_MAP[hAccCode]
        val verticalAccuracy = VERT_ACC_MAP[vAccCode]

        val spdAccByte = buffer.get(18).toInt() and 0xFF
        val spdAccCode = (spdAccByte shr 4) and 0x0F
        val speedAccuracy = SPEED_ACC_MAP[spdAccCode]

        val timeRaw = buffer.getShort(19).toInt() and 0xFFFF
        val gpsTimestampSeconds = if (timeRaw <= 36000) (timeRaw * 0.1f) else null

        return ParsedLocation(
            lat = lat,
            lon = lon,
            altitudeAgl = altAglMeters?.coerceAtLeast(0f),
            altitudeMsl = altMslMeters,
            heightType = heightType,
            speedKmh = speedKmh,
            speedMph = speedMph,
            verticalSpeedMps = verticalSpeedMps,
            heading = headingDeg,
            isAirborne = isAirborne,
            operationalStatus = operationalStatus,
            horizontalAccuracy = horizontalAccuracy,
            verticalAccuracy = verticalAccuracy,
            speedAccuracy = speedAccuracy,
            gpsTimestampSeconds = gpsTimestampSeconds
        )
    }

    private data class ParsedSystem(
        val lat: Double,
        val lon: Double,
        val altitudeMsl: Float?,
        val locationType: String,
        val ceClass: String?,
        val areaCount: Int,
        val areaRadiusMeters: Int?,
        val areaCeilingMeters: Float?,
        val areaFloorMeters: Float?
    )

    /**
     * Decodes System / Operator Message (Type 0x4, 25 bytes).
     */
    private fun parseSystemMessage(msg: ByteArray): ParsedSystem? {
        if (msg.size < 19) return null
        val buffer = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)

        val flags = buffer.get(1).toInt() and 0xFF
        val locTypeCode = (flags shr 4) and 0x03
        val locType = OPERATOR_LOC_MAP[locTypeCode] ?: "PILOT GCS"

        val classTypeCode = (flags shr 2) and 0x03
        val ceClass = if (classTypeCode == 1) {
            val classVal = flags and 0x03
            "EU CE Class $classVal"
        } else null

        val latRaw = buffer.getInt(2)
        val lonRaw = buffer.getInt(6)

        val lat = latRaw / 1e7
        val lon = lonRaw / 1e7

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 || (lat == 0.0 && lon == 0.0)) {
            return null
        }

        val areaCountRaw = buffer.getShort(10).toInt() and 0xFFFF
        val areaCount = areaCountRaw.coerceAtLeast(1)

        val areaRadiusRaw = buffer.get(12).toInt() and 0xFF
        val areaRadiusMeters = if (areaRadiusRaw > 0) areaRadiusRaw * 10 else null

        val areaCeilRaw = buffer.getShort(13).toInt() and 0xFFFF
        val areaCeilingMeters = if (areaCeilRaw != 0xFFFF) (areaCeilRaw * 0.5f) - 1000f else null

        val areaFloorRaw = buffer.getShort(15).toInt() and 0xFFFF
        val areaFloorMeters = if (areaFloorRaw != 0xFFFF) (areaFloorRaw * 0.5f) - 1000f else null

        val altMslRaw = buffer.getShort(17).toInt() and 0xFFFF
        val altitudeMsl = if (altMslRaw != 0xFFFF) (altMslRaw * 0.5f) - 1000f else null

        return ParsedSystem(
            lat = lat,
            lon = lon,
            altitudeMsl = altitudeMsl,
            locationType = locType,
            ceClass = ceClass,
            areaCount = areaCount,
            areaRadiusMeters = areaRadiusMeters,
            areaCeilingMeters = areaCeilingMeters,
            areaFloorMeters = areaFloorMeters
        )
    }

    private data class ParsedOperatorId(
        val operatorId: String,
        val idType: String
    )

    /**
     * Decodes Operator ID Message (Type 0x5, 25 bytes).
     * Bytes 2..21 contain the 20-character CAA Registered Operator ID (e.g. GBR-OP-123456789012).
     */
    private fun parseOperatorId(msg: ByteArray): ParsedOperatorId? {
        if (msg.size < 22) return null
        val idBytes = ByteArray(20)
        System.arraycopy(msg, 2, idBytes, 0, 20)
        val text = String(idBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
        if (text.isBlank() || text.length < 4) return null
        return ParsedOperatorId(
            operatorId = text,
            idType = "CAA_REGISTERED_OPERATOR"
        )
    }

    private fun parseAuthMessage(msg: ByteArray): String? {
        if (msg.size < 4) return null
        val authType = (msg[1].toInt() and 0xFF shr 4) and 0x0F
        return when (authType) {
            1 -> "UAS ID Signature (Cryptographic)"
            2 -> "Operator ID Signature"
            3 -> "Message Set Signature"
            4 -> "Network Remote ID Token"
            else -> "Cryptographic Direct Remote ID Signature"
        }
    }

    private fun parseSelfId(msg: ByteArray): String? {
        if (msg.size < 25) return null
        val descBytes = ByteArray(23)
        System.arraycopy(msg, 2, descBytes, 0, 23)
        val text = String(descBytes, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
        return text.takeIf { it.isNotBlank() }
    }

    /**
     * Decodes DJI proprietary DroneID / Aeroscope frames.
     */
    private fun parseDjiDroneId(data: ByteArray): DroneTelemetry? {
        if (data.size < 24) return null
        try {
            var offset = 0
            while (offset + 20 <= data.size) {
                // Search for DJI serial prefix: "1581F", "0816", or ASCII serial structure
                val candidateStr = String(data, offset, min(20, data.size - offset), StandardCharsets.US_ASCII)
                val isDjiSerial = candidateStr.startsWith("1581", ignoreCase = true) || candidateStr.startsWith("0816") || candidateStr.startsWith("37Q")
                if (isDjiSerial) {
                    val serial = candidateStr.takeWhile { it.isLetterOrDigit() }
                    if (serial.length >= 10) {
                        val mfr = "DJI (Da-Jiang Innovations)"
                        val model = determineModel(serial, mfr)

                        // Attempt coordinate extraction if packet is large enough
                        var droneLat: Double? = null
                        var droneLon: Double? = null
                        var altAgl: Float? = null
                        var spd: Float? = null

                        if (offset + 36 <= data.size) {
                            val buffer = ByteBuffer.wrap(data, offset + 16, 20).order(ByteOrder.LITTLE_ENDIAN)
                            val lonRaw = buffer.getInt()
                            val latRaw = buffer.getInt()
                            val lat = latRaw / 1e7
                            val lon = lonRaw / 1e7
                            if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
                                droneLat = lat
                                droneLon = lon
                            }
                            val altRaw = buffer.getShort().toInt() and 0xFFFF
                            if (altRaw != 0xFFFF) altAgl = (altRaw * 0.1f).coerceAtLeast(0f)
                            val spdRaw = buffer.getShort().toInt() and 0xFFFF
                            if (spdRaw != 0xFFFF) spd = spdRaw * 0.01f * 3.6f
                        }

                        return DroneTelemetry(
                            uasId = serial,
                            idType = "SERIAL_NUMBER (DJI DroneID)",
                            uasType = "MULTI-ROTOR / QUADCOPTER",
                            manufacturer = mfr,
                            modelName = model,
                            droneLat = droneLat,
                            droneLon = droneLon,
                            altitudeAglMeters = altAgl,
                            speedKmh = spd,
                            speedMph = spd?.let { it * 0.621371f },
                            protocolStandard = "DJI DroneID / OcuSync Broadcast",
                            rawPayloadHex = data.take(32).joinToString("") { "%02X".format(it) }
                        )
                    }
                }
                offset++
            }
        } catch (e: Exception) {
            Timber.d("Error parsing DJI DroneID: ${e.message}")
        }
        return null
    }
}
