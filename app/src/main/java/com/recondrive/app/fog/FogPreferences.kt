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

    // Exploration scan radius in meters (50m to 500m, default 180m)
    private val _fogRangeMeters = MutableStateFlow(
        prefs.getInt(KEY_FOG_RANGE, DEFAULT_RANGE_METERS)
    )
    val fogRangeMeters: StateFlow<Int> = _fogRangeMeters.asStateFlow()

    // Shroud opacity (0.20f to 1.00f, default 0.95f)
    private val _fogOpacity = MutableStateFlow(
        prefs.getFloat(KEY_FOG_OPACITY, DEFAULT_OPACITY)
    )
    val fogOpacity: StateFlow<Float> = _fogOpacity.asStateFlow()

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
        val presets = listOf(60, 120, 180, 260, 400)
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
        val presets = listOf(0.40f, 0.70f, 0.90f, 1.00f)
        val current = _fogOpacity.value
        val nextIdx = (presets.indexOfFirst { it >= current - 0.05f } + 1) % presets.size
        val nextOpacity = presets[nextIdx]
        setFogOpacity(nextOpacity)
        return nextOpacity
    }

    companion object {
        private const val PREFS_NAME = "recondrive_fog_prefs"
        private const val KEY_FOG_RANGE = "pref_fog_range_meters"
        private const val KEY_FOG_OPACITY = "pref_fog_opacity"

        const val DEFAULT_RANGE_METERS = 180
        const val MIN_RANGE_METERS = 50
        const val MAX_RANGE_METERS = 500

        const val DEFAULT_OPACITY = 0.95f
        const val MIN_OPACITY = 0.20f
        const val MAX_OPACITY = 1.00f
    }
}
