package com.recondrive.app.fog

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages user preferences for Fog of War surveillance range and shroud opacity.
 * Persists settings across mobile handset and Android Auto sessions.
 */
class FogPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Automatically migrate legacy default settings (180m / 95% opacity) to new standard: 50m range and 50% opacity
        val prefsVer = prefs.getInt(KEY_PREFS_VERSION, 0)
        if (prefsVer < CURRENT_PREFS_VERSION) {
            val oldRange = prefs.getInt(KEY_FOG_RANGE, -1)
            val oldOpacity = prefs.getFloat(KEY_FOG_OPACITY, -1f)
            val editor = prefs.edit().putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION)

            // If user was on old default 180m, migrate to 50m
            if (oldRange == -1 || oldRange == 180) {
                editor.putInt(KEY_FOG_RANGE, DEFAULT_RANGE_METERS)
            }
            // If user was on old default 0.95f, migrate to 0.50f (50%)
            if (oldOpacity == -1f || kotlin.math.abs(oldOpacity - 0.95f) < 0.02f) {
                editor.putFloat(KEY_FOG_OPACITY, DEFAULT_OPACITY)
            }
            editor.apply()
        }
    }

    // Exploration scan radius in meters (50m to 500m, default 50m)
    private val _fogRangeMeters = MutableStateFlow(
        prefs.getInt(KEY_FOG_RANGE, DEFAULT_RANGE_METERS)
    )
    val fogRangeMeters: StateFlow<Int> = _fogRangeMeters.asStateFlow()

    // Shroud opacity (0.20f to 1.00f, default 0.50f = 50%)
    private val _fogOpacity = MutableStateFlow(
        prefs.getFloat(KEY_FOG_OPACITY, DEFAULT_OPACITY)
    )
    val fogOpacity: StateFlow<Float> = _fogOpacity.asStateFlow()

    // 24/7 Background Radar Exploration (Unmasks fog even when app is closed / in background)
    private val _isBackgroundTrackingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_TRACKING, true)
    )
    val isBackgroundTrackingEnabled: StateFlow<Boolean> = _isBackgroundTrackingEnabled.asStateFlow()

    fun setBackgroundTrackingEnabled(enabled: Boolean) {
        if (_isBackgroundTrackingEnabled.value == enabled) return
        Timber.i("Setting 24/7 Background Fog Tracking: $enabled")
        _isBackgroundTrackingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BACKGROUND_TRACKING, enabled).apply()
    }

    fun setFogRange(rangeMeters: Int) {
        val clamped = rangeMeters.coerceIn(MIN_RANGE_METERS, MAX_RANGE_METERS)
        if (_fogRangeMeters.value == clamped) return
        Timber.i("Setting Fog Range: ${clamped}m")
        _fogRangeMeters.value = clamped
        prefs.edit().putInt(KEY_FOG_RANGE, clamped).apply()
    }

    fun setFogOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
        if (_fogOpacity.value == clamped) return
        Timber.i("Setting Fog Opacity: %.2f".format(clamped))
        _fogOpacity.value = clamped
        prefs.edit().putFloat(KEY_FOG_OPACITY, clamped).apply()
    }

    /**
     * Cycles to the next range preset (useful for Android Auto in-car buttons).
     */
    fun cycleNextRange(): Int {
        val presets = listOf(50, 100, 180, 260, 400)
        val current = _fogRangeMeters.value
        val nextIdx = (presets.indexOfFirst { it >= current } + 1) % presets.size
        val nextRange = presets[nextIdx]
        setFogRange(nextRange)
        return nextRange
    }

    /**
     * Cycles to the next opacity preset (useful for Android Auto in-car buttons).
     */
    fun cycleNextOpacity(): Float {
        val presets = listOf(0.30f, 0.50f, 0.75f, 1.00f)
        val current = _fogOpacity.value
        val nextIdx = (presets.indexOfFirst { it >= current - 0.05f } + 1) % presets.size
        val nextOpacity = presets[nextIdx]
        setFogOpacity(nextOpacity)
        return nextOpacity
    }

    companion object {
        private const val PREFS_NAME = "recondrive_fog_prefs"
        private const val KEY_PREFS_VERSION = "pref_fog_prefs_version"
        private const val CURRENT_PREFS_VERSION = 2
        private const val KEY_FOG_RANGE = "pref_fog_range_meters"
        private const val KEY_FOG_OPACITY = "pref_fog_opacity"
        private const val KEY_BACKGROUND_TRACKING = "pref_background_tracking"

        const val DEFAULT_RANGE_METERS = 50
        const val MIN_RANGE_METERS = 50
        const val MAX_RANGE_METERS = 500

        const val DEFAULT_OPACITY = 0.50f
        const val MIN_OPACITY = 0.20f
        const val MAX_OPACITY = 1.00f
    }
}
