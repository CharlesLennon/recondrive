package com.recondrive.app.scanner

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Direct Remote ID (ASTM F3411 / ASD-STAN prEN 4709-002) SIGINT Parser.
 *
 * Passively extracts and reconstructs aerial drone flight vectors, altitude,
 * airframe classification, and remote pilot ground control coordinates from
 * BLE 2.4 GHz broadcast frames (Service UUID 0xFFFA).
 */
object TacticalDroneIdParser {

    private const val OPENDIS_UUID_SHORT = "fffa"

    // UA Type Mapping (ASTM F3411 Table 1)
    private val UA_TYPE_MAP = mapOf(
        0 to "UAV (UNDECLARED)",
        1 to "AEROBATIC / DRONE",
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

    /**
     * Parses BLE scan record service data or raw payload for OpenDroneID frames.
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

            // 2. Fallback: Search raw advertisement bytes for 0xFFFA AD structure (Type 0x16 Service Data)
            if (rawBytes != null && rawBytes.size >= 10) {
                val extracted = extractServiceDataFromRaw(rawBytes, 0xFFFA)
                if (extracted != null && extracted.isNotEmpty()) {
                    val parsed = parsePayload(extracted)
                    if (parsed != null) return parsed
                }
            }

            // 3. Fallback: Parse manufacturer data for OpenDroneID or DJI Remote ID
            if (manufacturerData != null && manufacturerData.size >= 15) {
                val parsed = parsePayload(manufacturerData)
                if (parsed != null) return parsed
            }
        } catch (e: Exception) {
            Timber.d("Error parsing OpenDroneID: ${e.message}")
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
    private fun parsePayload(data: ByteArray): DroneTelemetry? {
        if (data.isEmpty()) return null

        var uasId: String? = null
        var uasType = "QUADCOPTER / UAV"
        var isAirborne = true
        var droneLat: Double? = null
        var droneLon: Double? = null
        var altitudeAgl: Float? = null
        var speedKmh: Float? = null
        var headingDeg: Float? = null
        var pilotLat: Double? = null
        var pilotLon: Double? = null
        var pilotLocationType = "OPERATOR GCS"
        var description: String? = null

        val firstByte = data[0].toInt() and 0xFF
        val msgType = (firstByte shr 4) and 0x0F

        // Check if Message Pack (0xF)
        if (msgType == 0x0F && data.size >= 26) {
            // Message pack contains packed messages (each typically 25 bytes)
            val msgSize = 25
            var offset = 1
            while (offset + msgSize <= data.size) {
                val subMsg = ByteArray(msgSize)
                System.arraycopy(data, offset, subMsg, 0, msgSize)
                val subType = (subMsg[0].toInt() and 0xFF shr 4) and 0x0F
                when (subType) {
                    0x0 -> { // Basic ID
                        uasId = parseBasicId(subMsg)
                        uasType = parseUaType(subMsg)
                    }
                    0x1 -> { // Location / Vector
                        val loc = parseLocationVector(subMsg)
                        if (loc != null) {
                            droneLat = loc.lat
                            droneLon = loc.lon
                            altitudeAgl = loc.altitudeAgl
                            speedKmh = loc.speedKmh
                            headingDeg = loc.heading
                            isAirborne = loc.isAirborne
                        }
                    }
                    0x4 -> { // System / Operator
                        val sys = parseSystemMessage(subMsg)
                        if (sys != null) {
                            pilotLat = sys.lat
                            pilotLon = sys.lon
                            pilotLocationType = sys.locationType
                        }
                    }
                    0x3 -> { // Self-ID
                        description = parseSelfId(subMsg)
                    }
                }
                offset += msgSize
            }
        } else {
            // Single Message (typically 25 bytes or variable)
            when (msgType) {
                0x0 -> { // Basic ID
                    uasId = parseBasicId(data)
                    uasType = parseUaType(data)
                }
                0x1 -> { // Location / Vector
                    val loc = parseLocationVector(data)
                    if (loc != null) {
                        droneLat = loc.lat
                        droneLon = loc.lon
                        altitudeAgl = loc.altitudeAgl
                        speedKmh = loc.speedKmh
                        headingDeg = loc.heading
                        isAirborne = loc.isAirborne
                    }
                }
                0x4 -> { // System / Operator
                    val sys = parseSystemMessage(data)
                    if (sys != null) {
                        pilotLat = sys.lat
                        pilotLon = sys.lon
                        pilotLocationType = sys.locationType
                    }
                }
            }
        }

        // Return telemetry if any meaningful Drone ID or Location was parsed
        if (uasId != null || droneLat != null || pilotLat != null) {
            return DroneTelemetry(
                uasId = uasId,
                uasType = uasType,
                droneLat = droneLat,
                droneLon = droneLon,
                isAirborne = isAirborne,
                altitudeAglMeters = altitudeAgl,
                speedKmh = speedKmh,
                headingDeg = headingDeg,
                pilotLat = pilotLat,
                pilotLon = pilotLon,
                pilotLocationType = pilotLocationType,
                description = description
            )
        }
        return null
    }

    /**
     * Decodes Basic ID Message (Type 0x0, 25 bytes).
     * Bytes 2..21 contain the 20-character ASCII UAS Serial Number (ANSI/CTA-2063-A).
     */
    private fun parseBasicId(msg: ByteArray): String? {
        if (msg.size < 22) return null
        val serialBytes = ByteArray(20)
        System.arraycopy(msg, 2, serialBytes, 0, 20)
        val rawSerial = String(serialBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
        return rawSerial.takeIf { it.isNotBlank() && it.length >= 4 }
    }

    private fun parseUaType(msg: ByteArray): String {
        if (msg.size < 2) return "QUADCOPTER / UAV"
        val typeCode = (msg[1].toInt() and 0xFF) and 0x0F
        return UA_TYPE_MAP[typeCode] ?: "UAV DRONE"
    }

    private data class ParsedLocation(
        val lat: Double,
        val lon: Double,
        val altitudeAgl: Float?,
        val speedKmh: Float?,
        val heading: Float?,
        val isAirborne: Boolean
    )

    /**
     * Decodes Location / Vector Message (Type 0x1, 25 bytes).
     */
    private fun parseLocationVector(msg: ByteArray): ParsedLocation? {
        if (msg.size < 19) return null
        val buffer = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)

        val statusByte = buffer.get(1).toInt() and 0xFF
        val isAirborne = (statusByte and 0xF0) != 0x10 // 0x10 = Ground, 0x20 = Airborne

        val dirByte = buffer.get(2).toInt() and 0xFF
        val headingDeg = if (dirByte <= 180) (dirByte * 2).toFloat() else null

        val speedByte = buffer.get(3).toInt() and 0xFF
        val speedKmh = if (speedByte < 255) (speedByte * 0.25f * 3.6f) else null

        val latRaw = buffer.getInt(5)
        val lonRaw = buffer.getInt(9)

        val lat = latRaw / 1e7
        val lon = lonRaw / 1e7

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 || (lat == 0.0 && lon == 0.0)) {
            return null
        }

        val altAglRaw = buffer.getShort(17).toInt() and 0xFFFF
        val altAglMeters = if (altAglRaw != 0xFFFF) {
            (altAglRaw * 0.5f) - 1000f
        } else null

        return ParsedLocation(
            lat = lat,
            lon = lon,
            altitudeAgl = altAglMeters?.coerceAtLeast(0f),
            speedKmh = speedKmh,
            heading = headingDeg,
            isAirborne = isAirborne
        )
    }

    private data class ParsedSystem(
        val lat: Double,
        val lon: Double,
        val locationType: String
    )

    /**
     * Decodes System / Operator Message (Type 0x4, 25 bytes).
     */
    private fun parseSystemMessage(msg: ByteArray): ParsedSystem? {
        if (msg.size < 10) return null
        val buffer = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)

        val flags = buffer.get(1).toInt() and 0xFF
        val locType = when ((flags shr 4) and 0x03) {
            0 -> "PILOT TAKEOFF SPOT"
            1 -> "DYNAMIC OPERATOR PILOT"
            2 -> "FIXED GROUND STATION"
            else -> "PILOT GCS"
        }

        val latRaw = buffer.getInt(2)
        val lonRaw = buffer.getInt(6)

        val lat = latRaw / 1e7
        val lon = lonRaw / 1e7

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 || (lat == 0.0 && lon == 0.0)) {
            return null
        }

        return ParsedSystem(
            lat = lat,
            lon = lon,
            locationType = locType
        )
    }

    private fun parseSelfId(msg: ByteArray): String? {
        if (msg.size < 25) return null
        val descBytes = ByteArray(23)
        System.arraycopy(msg, 2, descBytes, 0, 23)
        val text = String(descBytes, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
        return text.takeIf { it.isNotBlank() }
    }
}
