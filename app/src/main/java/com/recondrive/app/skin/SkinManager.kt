package com.recondrive.app.skin

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages active Map Skins and Fog Skins independently.
 * Persists selections in SharedPreferences so choices persist across handset and Android Auto sessions.
 */
class SkinManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentMapSkin = MutableStateFlow(
        MapSkin.fromId(prefs.getString(KEY_MAP_SKIN, MapSkin.TACTICAL_OBSIDIAN.id))
    )
    val currentMapSkin: StateFlow<MapSkin> = _currentMapSkin.asStateFlow()

    private val _currentFogSkin = MutableStateFlow(
        FogSkin.fromId(prefs.getString(KEY_FOG_SKIN, FogSkin.RADAR_TACTICAL.id))
    )
    val currentFogSkin: StateFlow<FogSkin> = _currentFogSkin.asStateFlow()

    fun setMapSkin(skin: MapSkin) {
        if (_currentMapSkin.value == skin) return
        Timber.i("Setting active Map Skin: ${skin.displayName}")
        _currentMapSkin.value = skin
        prefs.edit().putString(KEY_MAP_SKIN, skin.id).apply()
    }

    fun setFogSkin(skin: FogSkin) {
        if (_currentFogSkin.value == skin) return
        Timber.i("Setting active Fog Skin: ${skin.displayName}")
        _currentFogSkin.value = skin
        prefs.edit().putString(KEY_FOG_SKIN, skin.id).apply()
    }

    fun cycleNextMapSkin(): MapSkin {
        val all = MapSkin.values()
        val nextIdx = (all.indexOf(_currentMapSkin.value) + 1) % all.size
        val next = all[nextIdx]
        setMapSkin(next)
        return next
    }

    fun cycleNextFogSkin(): FogSkin {
        val all = FogSkin.values()
        val nextIdx = (all.indexOf(_currentFogSkin.value) + 1) % all.size
        val next = all[nextIdx]
        setFogSkin(next)
        return next
    }

    companion object {
        private const val PREFS_NAME = "recondrive_skin_prefs"
        private const val KEY_MAP_SKIN = "active_map_skin_id"
        private const val KEY_FOG_SKIN = "active_fog_skin_id"
    }
}
