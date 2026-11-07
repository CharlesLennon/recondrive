package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class RttBeacon(
    val bssid: String,
    val distanceMeters: Float,
    val distanceStdDevMeters: Float,
    val rssi: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

data class WifiRttTelemetry(
    val isRttSupported: Boolean = false,
    val isRangingActive: Boolean = false,
    val rangingNodes: List<RttBeacon> = emptyList(),
    val closestDistanceMeters: Float? = null,
    val statusDesc: String = "RTT STANDBY"
)

/**
 * Tactical Wi-Fi Round-Trip-Time (RTT) 802.11mc Engine
 * 1. Sub-Meter Centimeter Ranging: Measures radio flight time (nanoseconds) between device and 802.11mc APs
 * 2. GPS-Denied Navigation: Provides high-accuracy ranging inside multi-story parking structures and tunnels
 * 3. Indoor Proximity Sensing: Resolves distance down to 0.1m - 1.0m without satellite line-of-sight
 */
class TacticalWifiRttEngine(private val context: Context) {

    private val rttManager: WifiRttManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.applicationContext.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
    } else null

    private val isHardwareSupported: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT) && (rttManager != null)
    } else false

    private val _telemetry = MutableStateFlow(
        WifiRttTelemetry(
            isRttSupported = isHardwareSupported,
            statusDesc = if (isHardwareSupported) "RTT 802.11mc HARDWARE READY" else "RTT NOT SUPPORTED ON HARDWARE"
        )
    )
    val telemetry: StateFlow<WifiRttTelemetry> = _telemetry.asStateFlow()

    private var isRanging = false

    fun start() {
        if (!isHardwareSupported || isRanging) return
        isRanging = true
        _telemetry.update {
            it.copy(isRangingActive = true, statusDesc = "RTT 802.11mc RANGING ACTIVE")
        }
        Timber.i("TacticalWifiRttEngine: 802.11mc FTM Ranging active")
    }

    fun stop() {
        isRanging = false
        _telemetry.update {
            it.copy(isRangingActive = false, statusDesc = "RTT STANDBY")
        }
        Timber.i("TacticalWifiRttEngine: 802.11mc Ranging stopped")
    }

    @SuppressLint("MissingPermission")
    fun evaluateScanResults(results: List<ScanResult>) {
        if (!isHardwareSupported || !isRanging || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val rtt = rttManager ?: return

        val hasFineLoc = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLoc) return

        // Filter for access points that support 802.11mc Fine Timing Measurement (FTM)
        val rttResponders = results.filter { it.is80211mcResponder }
        if (rttResponders.isEmpty()) {
            _telemetry.update {
                it.copy(
                    rangingNodes = emptyList(),
                    closestDistanceMeters = null,
                    statusDesc = "RTT ACTIVE: NO 802.11mc APs IN RANGE"
                )
            }
            return
        }

        try {
            val builder = RangingRequest.Builder()
            rttResponders.take(5).forEach { builder.addAccessPoint(it) }
            val request = builder.build()

            rtt.startRanging(request, context.mainExecutor, object : RangingResultCallback() {
                override fun onRangingResults(results: List<RangingResult>) {
                    val nodes = mutableListOf<RttBeacon>()
                    for (res in results) {
                        if (res.status == RangingResult.STATUS_SUCCESS) {
                            val distMeters = res.distanceMm / 1000f
                            val stdDevMeters = res.distanceStdDevMm / 1000f
                            val bssid = res.macAddress?.toString() ?: "UNKNOWN"
                            nodes.add(
                                RttBeacon(
                                    bssid = bssid,
                                    distanceMeters = distMeters,
                                    distanceStdDevMeters = stdDevMeters,
                                    rssi = res.rssi
                                )
                            )
                        }
                    }

                    val closest = nodes.minOfOrNull { it.distanceMeters }
                    val desc = if (nodes.isNotEmpty()) {
                        "RTT LOCKED: ${nodes.size} APs (CLOSEST: %.1fm)".format(closest ?: 0f)
                    } else "RTT SCANNING: WAITING FIX"

                    _telemetry.update {
                        it.copy(
                            rangingNodes = nodes,
                            closestDistanceMeters = closest,
                            statusDesc = desc
                        )
                    }
                }

                override fun onRangingFailure(code: Int) {
                    Timber.w("TacticalWifiRttEngine: Ranging failed code=$code")
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "TacticalWifiRttEngine: Exception during startRanging")
        }
    }
}
