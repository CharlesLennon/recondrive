package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class GnssTelemetry(
    val isAvailable: Boolean = false,
    val totalSatellitesInView: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val gpsCount: Int = 0,
    val glonassCount: Int = 0,
    val galileoCount: Int = 0,
    val beidouCount: Int = 0,
    val avgCn0DbHz: Float = 0f,
    val isDualFrequencyActive: Boolean = false,
    val isJammingSuspected: Boolean = false,
    val isSpoofingSuspected: Boolean = false,
    val securityStatusDesc: String = "GNSS INTEGRITY: NOMINAL"
)

/**
 * Tactical GNSS Engine
 * 1. Raw Satellite Constellation Dome: Tracks GPS (USA), GLONASS (Russia), GALILEO (Europe), BEIDOU (China)
 * 2. Multi-Frequency L1/L5 Carrier Lock: Detects sub-meter dual-frequency tracking
 * 3. Anti-Spoofing & GPS Jamming Anomaly Detector: Detects artificial RF noise floors, AGC drops, and simulator spoofing
 */
class TacticalGnssEngine(private val context: Context) {

    private val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _telemetry = MutableStateFlow(GnssTelemetry(isAvailable = locationManager != null))
    val telemetry: StateFlow<GnssTelemetry> = _telemetry.asStateFlow()

    private var isListening = false

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            try {
                val count = status.satelliteCount
                var usedCount = 0
                var gps = 0
                var glonass = 0
                var galileo = 0
                var beidou = 0
                var totalCn0 = 0f
                var dualFreqL5 = false

                val cn0List = mutableListOf<Float>()

                for (i in 0 until count) {
                    val cn0 = status.getCn0DbHz(i)
                    totalCn0 += cn0
                    cn0List.add(cn0)

                    if (status.usedInFix(i)) {
                        usedCount++
                    }

                    when (status.getConstellationType(i)) {
                        GnssStatus.CONSTELLATION_GPS -> gps++
                        GnssStatus.CONSTELLATION_GLONASS -> glonass++
                        GnssStatus.CONSTELLATION_GALILEO -> galileo++
                        GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            if (status.hasCarrierFrequencyHz(i)) {
                                val freq = status.getCarrierFrequencyHz(i)
                                if (freq in 1.15e9f..1.20e9f) {
                                    dualFreqL5 = true
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }

                val avgCn0 = if (count > 0) totalCn0 / count else 0f

                // Anti-Spoofing Heuristics:
                var isSpoofing = false
                if (cn0List.size >= 8) {
                    val mean = avgCn0
                    val variance = cn0List.map { (it - mean) * (it - mean) }.average()
                    val stdDev = kotlin.math.sqrt(variance)
                    if (stdDev < 0.65) {
                        isSpoofing = true
                        Timber.w("TacticalGnssEngine: GPS Spoofing anomaly! Ultra-low C/N0 variance: %.2f dB-Hz".format(stdDev))
                    }
                }

                // Anti-Jamming Heuristics:
                val isJamming = count >= 6 && avgCn0 < 15.0f && usedCount <= 1

                val statusDesc = when {
                    isSpoofing -> "⚠️ SPOOFING ALERT: SYNTHETIC RF DETECTED"
                    isJamming -> "🚨 JAMMING DETECTED: CARRIER COLLAPSE"
                    dualFreqL5 -> "NOMINAL • DUAL-BAND L1+L5 LOCKED"
                    else -> "NOMINAL • $usedCount/$count SATS LOCKED"
                }

                _telemetry.update {
                    it.copy(
                        isAvailable = true,
                        totalSatellitesInView = count,
                        satellitesUsedInFix = usedCount,
                        gpsCount = gps,
                        glonassCount = glonass,
                        galileoCount = galileo,
                        beidouCount = beidou,
                        avgCn0DbHz = avgCn0,
                        isDualFrequencyActive = dualFreqL5,
                        isJammingSuspected = isJamming,
                        isSpoofingSuspected = isSpoofing,
                        securityStatusDesc = statusDesc
                    )
                }
            } catch (e: Throwable) {
                Timber.w(e, "Error processing GNSS satellite status")
            }
        }
    }

    private val gnssMeasurementsCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
                try {
                    // Monitor Automatic Gain Control (AGC) level for RF jammer interference
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        var agcDropDetected = false
                        for (m in eventArgs.measurements) {
                            try {
                                if (m.hasAutomaticGainControlLevelDb()) {
                                    val agc = m.automaticGainControlLevelDb
                                    if (agc < -15.0) {
                                        agcDropDetected = true
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                        if (agcDropDetected) {
                            _telemetry.update {
                                it.copy(
                                    isJammingSuspected = true,
                                    securityStatusDesc = "🚨 RF JAMMING: AGC GAIN ATTENUATION DETECTED"
                                )
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Timber.w(e, "Error processing GNSS measurements")
                }
            }
        }
    } else null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isListening || locationManager == null) return
        val hasLoc = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLoc) {
            Timber.w("TacticalGnssEngine: Missing FINE_LOCATION permission")
            return
        }

        var anyRegistered = false
        try {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
            anyRegistered = true
        } catch (e: Throwable) {
            Timber.w(e, "TacticalGnssEngine: Could not register GNSS status callback")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssMeasurementsCallback != null) {
                locationManager.registerGnssMeasurementsCallback(gnssMeasurementsCallback, null)
                anyRegistered = true
            }
        } catch (e: Throwable) {
            Timber.w(e, "TacticalGnssEngine: Could not register GNSS measurements callback")
        }

        isListening = anyRegistered
        if (anyRegistered) {
            Timber.i("TacticalGnssEngine: GNSS raw constellation listener registered")
        }
    }
    }

    fun stop() {
        if (!isListening || locationManager == null) return
        try {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssMeasurementsCallback != null) {
                locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
            }
        } catch (e: Exception) {
            Timber.w(e, "TacticalGnssEngine: Note during unregister")
        }
        isListening = false
        Timber.i("TacticalGnssEngine: Stopped GNSS listener")
    }
}
