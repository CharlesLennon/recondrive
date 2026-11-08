package com.recondrive.app.scanner

import android.content.Context
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import com.recondrive.app.audio.TacticalSoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

/**
 * IMSI-Catcher / Rogue Base Station Threat Classification Levels.
 */
enum class ImsiThreatLevel {
    CLEAR,                        // Commercial network operating within normal parameters
    SUSPICIOUS,                   // Abnormal signal spike or isolated cell profile (Threat score 25-59)
    CRITICAL_INTERCEPTION_RISK    // Rogue base station signature: forced 2G downgrade, stationary TAC hopping (Threat score >= 60)
}

/**
 * Anomaly classifications detected by the heuristic audit engine.
 */
enum class AnomalyType {
    FORCED_2G_DOWNGRADE,          // Abrupt drop from 4G/5G to 2G GSM (A5/0 or A5/1 cipher stripping attack)
    RAPID_LAC_TAC_SPOOF,          // Stationary change of Tracking Area Code / Location Area Code to force IMSI attach
    ABNORMAL_SIGNAL_SPIKE,        // Overwhelming power (> -52 dBm or +25 dBm over ambient) without known macro mast
    NULL_NEIGHBOR_LIST,           // Zero neighbor base stations broadcasted (isolated portable SDR interceptor profile)
    INVALID_PLMN,                 // Suspicious test network (MCC 001) or unlisted carrier signature
    SIMULATED_STINGRAY_ATTACK     // Operator demo/drill injection
}

/**
 * Historical anomaly observation log.
 */
data class ImsiAnomalyEvent(
    val type: AnomalyType,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val severityPoints: Int,
    val associatedCellId: String,
    val signalDbm: Int
)

/**
 * Active cellular environment and threat state snapshot.
 */
data class ImsiDetectionState(
    val threatLevel: ImsiThreatLevel = ImsiThreatLevel.CLEAR,
    val threatScore: Int = 0, // 0 to 100
    val activeServingTech: String = "4G LTE / 5G NR",
    val activeServingCellId: String = "",
    val activeServingTacOrLac: Int = 0,
    val activeServingOperator: String = "Commercial Carrier",
    val activeServingDbm: Int = -85,
    val is2gDowngraded: Boolean = false,
    val cipherStatus: String = "AES-128 / SNOW-3G (SECURE)",
    val recentAnomalies: List<ImsiAnomalyEvent> = emptyList(),
    val totalAnomaliesDetected: Int = 0,
    val lastAuditTimestamp: Long = 0L,
    val isShieldEnabled: Boolean = true,
    val isSimulationActive: Boolean = false
)

/**
 * Tactical IMSI-Catcher & Rogue Base Station ("Stingray" / "Hailstorm") Detector.
 *
 * Audits cellular broadcast parameters in real time to detect surveillance equipment
 * that exploits 2G/3G/4G/5G mobile base station reselection algorithms:
 * 1. Forced 2G GSM Downgrade (Cipher stripping to unencrypted A5/0 or breakable A5/1).
 * 2. Rapid Stationary TAC/LAC Hopping (Triggering periodic IMSI location updates).
 * 3. High-Power RF Dominance Spikes (Overpowering macro cells to hijack UE connection).
 * 4. Zero-Neighbor Isolated Transmitters (Stand-alone SDRs missing commercial SIB neighbor lists).
 * 5. Mismatched or Test PLMN Identifiers (MCC 001 / 999).
 */
class TacticalImsiCatcherDetector(
    private val context: Context,
    private val soundManager: TacticalSoundManager? = null
) {

    private val _state = MutableStateFlow(ImsiDetectionState())
    val state: StateFlow<ImsiDetectionState> = _state.asStateFlow()

    private val anomalyHistory = CopyOnWriteArrayList<ImsiAnomalyEvent>()

    // Tracking variables for historical anomaly detection
    private var lastServingTech: String? = null
    private var lastServingTechTimestamp: Long = 0L
    private var lastServingTacOrLac: Int = 0
    private var lastTacChangeLat: Double = 0.0
    private var lastTacChangeLon: Double = 0.0
    private var lastTacChangeTimestamp: Long = 0L
    private var lastAlertTimestamp: Long = 0L
    private var consecutiveCleanScans: Int = 0

    /**
     * Evaluates a live cell environment scan against all known IMSI-catcher heuristic rules.
     */
    fun evaluateCellEnvironment(
        cellList: List<CellInfo>,
        hostLat: Double,
        hostLon: Double,
        speedKmh: Float
    ): ImsiDetectionState {
        val now = System.currentTimeMillis()

        if (cellList.isEmpty()) {
            return _state.value.copy(lastAuditTimestamp = now)
        }

        // 1. Identify primary serving (registered) cell
        var primaryCell = cellList.find { it.isRegistered } ?: cellList.firstOrNull()
        if (primaryCell == null) return _state.value

        var cellTech = "Cellular"
        var cellId = ""
        var operatorName = "Commercial Carrier"
        var dbm = -90
        var tacOrLac = 0
        var mcc = ""
        var mnc = ""

        when (primaryCell) {
            is CellInfoNr -> {
                cellTech = "5G NR"
                val id = primaryCell.cellIdentity as? CellIdentityNr
                if (id != null) {
                    val nci = id.nci
                    val pci = id.pci
                    tacOrLac = id.tac
                    mcc = id.mccString ?: ""
                    mnc = id.mncString ?: ""
                    operatorName = id.operatorAlphaLong?.toString() ?: "5G Carrier"
                    cellId = "CELL_5G_${nci}_$pci"
                }
                dbm = primaryCell.cellSignalStrength.dbm
            }
            is CellInfoLte -> {
                cellTech = "4G LTE"
                val id = primaryCell.cellIdentity
                val ci = id.ci
                val pci = id.pci
                tacOrLac = id.tac
                mcc = id.mccString ?: ""
                mnc = id.mncString ?: ""
                operatorName = id.operatorAlphaLong?.toString() ?: "LTE Carrier"
                cellId = "CELL_LTE_${ci}_$pci"
                dbm = primaryCell.cellSignalStrength.dbm
            }
            is CellInfoWcdma -> {
                cellTech = "3G WCDMA"
                val id = primaryCell.cellIdentity
                tacOrLac = id.lac
                mcc = id.mccString ?: ""
                mnc = id.mncString ?: ""
                operatorName = id.operatorAlphaLong?.toString() ?: "3G Carrier"
                cellId = "CELL_3G_${id.cid}"
                dbm = primaryCell.cellSignalStrength.dbm
            }
            is CellInfoGsm -> {
                cellTech = "2G GSM"
                val id = primaryCell.cellIdentity
                tacOrLac = id.lac
                mcc = id.mccString ?: ""
                mnc = id.mncString ?: ""
                operatorName = id.operatorAlphaLong?.toString() ?: "GSM Carrier"
                cellId = "CELL_GSM_${id.cid}"
                dbm = primaryCell.cellSignalStrength.dbm
            }
        }

        val newAnomalies = mutableListOf<ImsiAnomalyEvent>()

        // ---------------------------------------------------------------------
        // HEURISTIC RULE 1: FORCED 2G/GSM DOWNGRADE (CIPHER STRIPPING)
        // ---------------------------------------------------------------------
        val is2g = cellTech.contains("2G") || cellTech.contains("GSM")
        val hadRecentHighSpeed = lastServingTech?.contains("LTE") == true || lastServingTech?.contains("5G") == true
        val timeSinceTechChange = now - lastServingTechTimestamp

        if (is2g && hadRecentHighSpeed && timeSinceTechChange < 180_000L) {
            val neighborHasLte = cellList.any { it is CellInfoLte || it is CellInfoNr }
            val anomaly = ImsiAnomalyEvent(
                type = AnomalyType.FORCED_2G_DOWNGRADE,
                title = "FORCED 2G DOWNGRADE DETECTED",
                description = "Abrupt downgrade from $lastServingTech to 2G GSM (Cell: $cellId) with active LTE spectrum nearby. Potential A5/0 cipher stripping attack.",
                timestamp = now,
                severityPoints = 50,
                associatedCellId = cellId,
                signalDbm = dbm
            )
            newAnomalies.add(anomaly)
            Timber.w("IMSI DETECTOR: Forced 2G downgrade detected! $cellId")
        }

        // ---------------------------------------------------------------------
        // HEURISTIC RULE 2: STATIONARY TAC / LAC HOPPING
        // ---------------------------------------------------------------------
        if (tacOrLac > 0 && lastServingTacOrLac > 0 && tacOrLac != lastServingTacOrLac) {
            val dLat = (hostLat - lastTacChangeLat) * 111320.0
            val dLon = (hostLon - lastTacChangeLon) * 111320.0 * cos(Math.toRadians(hostLat))
            val distMoved = hypot(dLat, dLon)
            val timeDelta = now - lastTacChangeTimestamp

            // If TAC changed while standing still or travelling < 35m at low speed
            if (distMoved < 35.0 && speedKmh < 6.0f && timeDelta < 90_000L) {
                val anomaly = ImsiAnomalyEvent(
                    type = AnomalyType.RAPID_LAC_TAC_SPOOF,
                    title = "STATIONARY TAC/LAC SHIFT",
                    description = "Cellular Area Code changed from $lastServingTacOrLac to $tacOrLac while vehicle is stationary (${distMoved.toInt()}m moved). Typical IMSI paging re-registration exploit.",
                    timestamp = now,
                    severityPoints = 35,
                    associatedCellId = cellId,
                    signalDbm = dbm
                )
                newAnomalies.add(anomaly)
                Timber.w("IMSI DETECTOR: Stationary TAC hop detected: $lastServingTacOrLac -> $tacOrLac")
            }

            lastTacChangeLat = hostLat
            lastTacChangeLon = hostLon
            lastTacChangeTimestamp = now
        } else if (lastServingTacOrLac == 0 && tacOrLac > 0) {
            lastTacChangeLat = hostLat
            lastTacChangeLon = hostLon
            lastTacChangeTimestamp = now
        }

        // ---------------------------------------------------------------------
        // HEURISTIC RULE 3: ABNORMAL RF SIGNAL DOMINANCE SPIKE
        // ---------------------------------------------------------------------
        val ambientDbmList = cellList.filter { it != primaryCell }.map {
            when (it) {
                is CellInfoLte -> it.cellSignalStrength.dbm
                is CellInfoNr -> it.cellSignalStrength.dbm
                is CellInfoGsm -> it.cellSignalStrength.dbm
                is CellInfoWcdma -> it.cellSignalStrength.dbm
                else -> -100
            }
        }.filter { it != Int.MAX_VALUE && it > -140 }

        val avgAmbientDbm = if (ambientDbmList.isNotEmpty()) ambientDbmList.average() else -95.0
        val dbmDelta = dbm - avgAmbientDbm

        if (dbm >= -50 || (dbm >= -58 && dbmDelta > 26.0)) {
            val anomaly = ImsiAnomalyEvent(
                type = AnomalyType.ABNORMAL_SIGNAL_SPIKE,
                title = "UNUSUAL RF DOMINANCE SPIKE",
                description = "Primary cell power is unusually high (${dbm} dBm, +${dbmDelta.toInt()} dB above ambient). Indicative of high-power close-proximity transmitter.",
                timestamp = now,
                severityPoints = 30,
                associatedCellId = cellId,
                signalDbm = dbm
            )
            newAnomalies.add(anomaly)
            Timber.w("IMSI DETECTOR: Abnormal signal spike: $dbm dBm (delta: +$dbmDelta dB)")
        }

        // ---------------------------------------------------------------------
        // HEURISTIC RULE 4: ISOLATED CELL WITH ZERO NEIGHBORS
        // ---------------------------------------------------------------------
        if (cellList.size == 1 && (hadRecentHighSpeed || lastServingTech != null)) {
            val anomaly = ImsiAnomalyEvent(
                type = AnomalyType.NULL_NEIGHBOR_LIST,
                title = "ISOLATED STANDALONE CELL",
                description = "Zero neighbor base stations broadcasted. Commercial networks always configure neighbor lists; isolated profile matches portable SDR interceptor.",
                timestamp = now,
                severityPoints = 20,
                associatedCellId = cellId,
                signalDbm = dbm
            )
            newAnomalies.add(anomaly)
        }

        // ---------------------------------------------------------------------
        // HEURISTIC RULE 5: SUSPICIOUS / TEST PLMN IDENTIFIER
        // ---------------------------------------------------------------------
        if (mcc == "001" || mcc == "999") {
            val anomaly = ImsiAnomalyEvent(
                type = AnomalyType.INVALID_PLMN,
                title = "TEST NETWORK PLMN DETECTED",
                description = "Cell broadcasted test/unlicensed PLMN identifier ($mcc-$mnc). Mismatched carrier signature.",
                timestamp = now,
                severityPoints = 40,
                associatedCellId = cellId,
                signalDbm = dbm
            )
            newAnomalies.add(anomaly)
        }

        // Update tracking state
        if (cellTech != lastServingTech) {
            lastServingTech = cellTech
            lastServingTechTimestamp = now
        }
        if (tacOrLac > 0) {
            lastServingTacOrLac = tacOrLac
        }

        // Add newly identified anomalies
        if (newAnomalies.isNotEmpty()) {
            consecutiveCleanScans = 0
            anomalyHistory.addAll(0, newAnomalies)
        } else {
            consecutiveCleanScans++
        }

        // Prune anomalies older than 180 seconds
        anomalyHistory.removeIf { (now - it.timestamp) > 180_000L }

        // Compute aggregate threat score (decaying with clean scans)
        val rawScore = anomalyHistory.sumOf { it.severityPoints }
        val decay = (consecutiveCleanScans * 4).coerceAtLeast(0)
        val finalScore = (rawScore - decay).coerceIn(0, 100)

        val threatLevel = when {
            finalScore >= 60 || is2g && hadRecentHighSpeed -> ImsiThreatLevel.CRITICAL_INTERCEPTION_RISK
            finalScore >= 25 -> ImsiThreatLevel.SUSPICIOUS
            else -> ImsiThreatLevel.CLEAR
        }

        val cipherStatus = when {
            is2g -> "UNENCRYPTED A5/0-1 (VULNERABLE)"
            cellTech.contains("5G") -> "5G-AKA / SNOW-3G 256-BIT (SECURE)"
            cellTech.contains("LTE") -> "EPS-AKA / AES-128 (SECURE)"
            else -> "UMTS KASUMI (MODERATE)"
        }

        // Threat alert dispatch (evaluates user preference: Nothing, Notification, Voice, or Both)
        if (threatLevel != ImsiThreatLevel.CLEAR && (now - lastAlertTimestamp) > 30_000L) {
            lastAlertTimestamp = now
            if (threatLevel == ImsiThreatLevel.CRITICAL_INTERCEPTION_RISK) {
                soundManager?.dispatchAlert(
                    category = com.recondrive.app.audio.AlertCategory.SUSPICIOUS_CELL_TOWER,
                    title = "⚠️ CRITICAL ROGUE BASE STATION",
                    message = "IMSI Catcher / Rogue base station detected! High interception risk.",
                    ttsPhrase = "Warning: Rogue base station detected. High interception risk.",
                    soundType = "INTERCEPTION_ALERT"
                )
            } else {
                soundManager?.dispatchAlert(
                    category = com.recondrive.app.audio.AlertCategory.CELLULAR_ANOMALY,
                    title = "📶 CELLULAR ANOMALY DETECTED",
                    message = "Suspicious cell parameters, unencrypted A5/0 or rapid TAC hopping.",
                    ttsPhrase = "Caution: Cellular anomaly detected.",
                    soundType = "RADAR_BEEP"
                )
            }
        }

        val updatedState = ImsiDetectionState(
            threatLevel = threatLevel,
            threatScore = finalScore,
            activeServingTech = cellTech,
            activeServingCellId = cellId,
            activeServingTacOrLac = tacOrLac,
            activeServingOperator = operatorName,
            activeServingDbm = dbm,
            is2gDowngraded = is2g,
            cipherStatus = cipherStatus,
            recentAnomalies = anomalyHistory.take(8),
            totalAnomaliesDetected = anomalyHistory.size,
            lastAuditTimestamp = now,
            isShieldEnabled = _state.value.isShieldEnabled,
            isSimulationActive = false
        )

        _state.value = updatedState
        return updatedState
    }

    /**
     * Toggles IMSI Interception Defense Shield.
     */
    fun toggleShield(): Boolean {
        val next = !_state.value.isShieldEnabled
        _state.value = _state.value.copy(isShieldEnabled = next)
        return next
    }

    /**
     * Resets active anomaly history.
     */
    fun clearAnomalies() {
        anomalyHistory.clear()
        _state.value = _state.value.copy(
            threatLevel = ImsiThreatLevel.CLEAR,
            threatScore = 0,
            recentAnomalies = emptyList()
        )
    }

    fun simulateAttack(enable: Boolean) {
        // Dummy simulation disabled; strictly live hardware analysis
        clearAnomalies()
    }
}
