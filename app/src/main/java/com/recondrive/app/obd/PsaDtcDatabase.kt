package com.recondrive.app.obd

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Severity level of an automotive Diagnostic Trouble Code.
 */
enum class DtcSeverity {
    CRITICAL,   // May cause limp-mode, gearbox drop to Neutral, or mechanical damage
    WARNING,    // Emission or comfort system degradation (DPF, glow plugs, EGR)
    INFO        // Intermittent or historical advisory code
}

/**
 * Enriched PSA Diagnostic Trouble Code data item.
 */
data class PsaDtcItem(
    val code: String,
    val rawHex: String,
    val system: String,
    val systemName: String,
    val ecu: String,
    val ecuCanId: String,
    val descEn: String,
    val descFr: String,
    val comment: String?,
    val mechanicalAdvice: String?,
    val severity: DtcSeverity
)

/**
 * Database helper and repository for querying the reverse-engineered PSA DTC database (40,752 codes).
 * Specialized for Citroën Grand C4 Picasso Mk1 (B58, November 2010, 1.6 HDi DV6, EGS6 / BMP6).
 */
class PsaDtcDatabase(private val context: Context) {

    private val dbFileName = "psa_dtc.db"
    private var sqliteDb: SQLiteDatabase? = null

    init {
        openOrCopyDatabase()
    }

    private fun openOrCopyDatabase() {
        try {
            val dbFile = context.getDatabasePath(dbFileName)
            if (!dbFile.exists() || dbFile.length() < 100_000) {
                dbFile.parentFile?.mkdirs()
                Timber.i("Extracting PSA DTC database asset to %s...", dbFile.absolutePath)
                val assetStream: InputStream = context.assets.open(dbFileName)
                val outStream = FileOutputStream(dbFile)
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (assetStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
                outStream.close()
                assetStream.close()
                Timber.i("PSA DTC database extracted successfully (%d bytes)", dbFile.length())
            }

            if (dbFile.exists()) {
                sqliteDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                Timber.i("PSA DTC database opened successfully.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PSA DTC SQLite database")
        }
    }

    /**
     * Looks up a DTC code (e.g. "P17A5", "P1351", "C1385") and returns enriched diagnostic data.
     */
    fun lookupDtc(code: String, rawHex: String? = null, ecuCanId: String? = null): PsaDtcItem {
        val cleanCode = code.trim().uppercase()
        val hex = rawHex ?: cleanCode.drop(1)
        val defaultSystem = mapCanIdToSystem(ecuCanId ?: "7DF")

        var foundSystem = defaultSystem.first
        var foundEcu = defaultSystem.second
        var descEn: String? = null
        var descFr: String? = null
        var comment: String? = null

        try {
            sqliteDb?.let { db ->
                val cursor = db.rawQuery(
                    "SELECT system, ecu, desc_en, desc_fr, comment FROM dtcs WHERE code = ? LIMIT 1",
                    arrayOf(cleanCode)
                )
                if (cursor.moveToFirst()) {
                    foundSystem = cursor.getString(0) ?: foundSystem
                    foundEcu = cursor.getString(1) ?: foundEcu
                    descEn = cursor.getString(2)
                    descFr = cursor.getString(3)
                    comment = cursor.getString(4)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying PSA DTC DB for code %s", cleanCode)
        }

        // Fallback default descriptions if database didn't have exact match
        if (descEn == null) {
            descEn = getDefaultDescription(cleanCode)
        }
        if (descFr == null) {
            descFr = ""
        }

        val advice = PICASSO_MECHANICAL_ADVISORIES[cleanCode]
            ?: generateGenericAdvisory(cleanCode, foundSystem)

        val severity = when {
            cleanCode in CRITICAL_CODES || cleanCode.startsWith("P17") || cleanCode.startsWith("C13") -> DtcSeverity.CRITICAL
            cleanCode.startsWith("P") || cleanCode.startsWith("C") -> DtcSeverity.WARNING
            else -> DtcSeverity.INFO
        }

        val systemName = mapSystemCodeToFullName(foundSystem, ecuCanId)

        return PsaDtcItem(
            code = cleanCode,
            rawHex = hex,
            system = foundSystem,
            systemName = systemName,
            ecu = foundEcu,
            ecuCanId = ecuCanId ?: mapSystemToCanId(foundSystem),
            descEn = descEn,
            descFr = descFr,
            comment = comment,
            mechanicalAdvice = advice,
            severity = severity
        )
    }

    private fun mapCanIdToSystem(canId: String): Pair<String, String> {
        return when (canId.uppercase()) {
            "7E8", "7E0" -> "CMM" to "Bosch EDC16C34 / EDC17 (1.6 HDi Engine)"
            "7E9", "7E1", "7E2" -> "BVA" to "Magneti Marelli CFC300P (EGS6 Gearbox)"
            "768", "760" -> "ABS" to "Bosch ESP 8.1 (Dynamic Stability)"
            "79A", "792" -> "FSE" to "Motorized Electric Parking Brake (FSE)"
            "7D2", "752" -> "BSI" to "Built-in Systems Interface (BSI 2004)"
            "77A", "772" -> "ADDGO" to "Eolys DPF Additive Dosing Unit"
            else -> "POWERTRAIN" to "CAN Diagnostic Node ($canId)"
        }
    }

    private fun mapSystemToCanId(system: String): String {
        return when (system.uppercase()) {
            "CMM", "ENGINE" -> "7E8"
            "BVA", "BVMP", "GEARBOX" -> "7E9"
            "ABS", "ESP" -> "768"
            "FSE", "EPB" -> "79A"
            "BSI" -> "7D2"
            "ADDGO" -> "77A"
            else -> "7DF"
        }
    }

    private fun mapSystemCodeToFullName(systemCode: String, canId: String?): String {
        return when (systemCode.uppercase()) {
            "CMM" -> "Engine Management ECU (Bosch EDC16C34)"
            "BVA", "BVMP" -> "Piloted Transmission (EGS6 CFC300P)"
            "ABS", "ESP" -> "Dynamic Stability Control (Bosch ESP 8.1)"
            "FSE" -> "Electric Parking Brake (Motorized Caliper)"
            "BSI" -> "Central Body Systems Interface (BSI 2004)"
            "ADDGO" -> "Diesel Particulate Additive Dosing (Eolys)"
            else -> canId?.let { "ECU Node 0x$it" } ?: "Vehicle Multiplex System"
        }
    }

    private fun getDefaultDescription(code: String): String {
        return when {
            code.startsWith("P1") -> "PSA Proprietary Powertrain Diagnostic Fault"
            code.startsWith("P0") -> "Standard SAE Powertrain Regulation Fault"
            code.startsWith("C1") -> "PSA Proprietary Chassis / Braking Fault"
            code.startsWith("C0") -> "Standard Chassis / ABS System Fault"
            code.startsWith("B1") -> "PSA Proprietary Body / BSI Multiplex Fault"
            code.startsWith("U1") -> "PSA Inter-Module CAN Bus Network Frame Timeout"
            else -> "Manufacturer Diagnostic Trouble Code"
        }
    }

    private fun generateGenericAdvisory(code: String, system: String): String {
        return when (code.firstOrNull()) {
            'P' -> "Engine / Transmission system telemetry anomaly on 1.6 HDi DV6. Inspect wiring harness, sensor supply voltages (5V reference), and vacuum hoses."
            'C' -> "Chassis & stability circuit check. On the C4 Picasso, test battery cranking voltage (>10.5V) and wheel speed sensor magnetic target rings."
            'B' -> "Body / BSI electronics advisory. Verify scuttle drain panels are not blocked and battery connections are secure."
            'U' -> "CAN bus multiplexing communication interruption. Ensure battery voltage remains above 12.2V with engine off."
            else -> "Inspect vehicle diagnostic log and live parameters."
        }
    }

    companion object {
        private val CRITICAL_CODES = setOf(
            "P17A5", "P0299", "C1385", "C155A", "P0218", "P0215", "P0087", "P0088", "P1445"
        )

        /**
         * Specialist mechanical & electrical advisories engineered specifically
         * for the 2010 Citroën Grand C4 Picasso Mk1 (B58, 1.6 HDi DV6, 6-Speed EGS6).
         */
        val PICASSO_MECHANICAL_ADVISORIES = mapOf(
            "P17A5" to "EGS6 / BMP6 1st Gear Coordination Fault (CFC300P): Shuddering or jumping to Neutral. Overwhelmingly caused by: 1) Hydraulic accumulator sphere losing nitrogen charge (< 40 bar), causing the electric pump to prime too frequently. 2) Clutch actuator linear stroke wear exceeding nominal threshold (new: 10mm, worn limit: 18mm). 3) 3-step kiss-point relearn required.",
            "P1351" to "Pre/Post-Heating Relay Circuit Coherence: Extremely common on the DV6 1.6 HDi. Glow plugs remain supplied when relay is commanded off, or one or more plugs are open-circuit. Critical Note: Faulty glow plugs prevent the engine ECU from initiating passive DPF (FAP) soot regeneration cycles, causing premature filter clogging.",
            "C1385" to "Bosch ESP 8.1 Solenoid Valve Supply Voltage: High false-alarm likelihood! On the C4 Picasso, this is almost always triggered by starter battery cranking voltage dropping below 10.5V during ignition, NOT a bad ABS pump. Verify 12V battery health under load before replacing any braking components.",
            "C155A" to "Electric Parking Brake (FSE) Force Calibration: Motor cable clamping tension out of range or emergency release cable disturbed. Check the manual release loop under the front center floor compartment and verify rear brake caliper slider pins are lubricated.",
            "P0299" to "Garrett GT1544V Turbo Underboost: Turbo boost regulation below target. Common DV6 1.6 HDi failure points: 1) Pierburg vacuum electrovalve behind the engine block (cracked rubber elbows). 2) Carbon buildup sticking the variable vane geometry (VNT). 3) Split orange/black silicone intercooler charge air hoses.",
            "P1445" to "Eolys DPF Additive Maximum Threshold Exceeded: Additive level calculation in particulate filter has reached capacity limit. Check underbody additive bladder pouch condition and perform dual-ECU counter synchronization across ADDGO (0x772) and EDC16C34 (0x7E0).",
            "P1446" to "Eolys DPF Additive Empty: Particulate filter additive reservoir empty. Must refill with Eolys DPX42 / 176 / PowerFlex and execute the 15-second doser pump prime cycle to clear dashboard warnings.",
            "P0490" to "EGR Electric Valve Position Error: Exhaust Gas Recirculation valve position copy signal offset. Valve mechanism jammed with sticky carbon sludge. Recommend ultrasonic valve cleaning or replacement to restore low-RPM throttle response.",
            "P1459" to "EGR Valve Mechanically Jammed Open: EGR valve stuck during driving cycle. Causes sudden hesitation at 1500 - 2000 RPM, black exhaust smoke, and sluggish turbo spool-up.",
            "P0087" to "Common Rail Fuel Pressure Too Low: High-pressure fuel rail delivery below setpoint (< 250 bar idle). Check fuel filter for wax or metal shavings, inspect high-pressure pump suction regulator (VC), and test injector leak-back rates.",
            "P0088" to "Common Rail Fuel Pressure Too High: Fuel rail pressure exceeding safety envelope (> 1600 bar). High-pressure pump pressure regulator valve sticking or fuel return line obstructed.",
            "P0215" to "Main Engine Relay Supply Circuit: Double relay / BSM engine bay fuse box power feed interruption. Common symptom: Engine cranks but fails to fire, or dies intermittently over speed bumps.",
            "P0100" to "Mass Air Flow (MAF) Sensor Signal Incoherent: Post-airbox intake flow reading out of range. Check 4-pin connector wiring and verify air filter housing lid is clipped tightly.",
            "U1208" to "CAN Bus Frame Transmission Interrupted: ESP 8.1 or EGS6 transmission missing torque frames from the engine management ECU. Check battery terminal clamp tightness and ABS connector harness pins."
        )
    }
}
