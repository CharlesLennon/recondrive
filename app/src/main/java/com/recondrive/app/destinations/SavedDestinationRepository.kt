package com.recondrive.app.destinations

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import timber.log.Timber

/**
 * Manages user saved locations and contacts sub-lists.
 * Persists data across app launches and device reboots via JSON storage.
 */
class SavedDestinationRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _destinations = MutableStateFlow<List<SavedDestination>>(emptyList())
    val destinations: StateFlow<List<SavedDestination>> = _destinations.asStateFlow()

    init {
        loadDestinations()
    }

    private fun loadDestinations() {
        val raw = prefs.getString(KEY_DESTINATIONS, null)
        if (raw.isNullOrEmpty()) {
            _destinations.value = emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<SavedDestination>()
                for (i in 0 until array.length()) {
                    list.add(SavedDestination.fromJson(array.getJSONObject(i)))
                }
                // Purge any legacy dummy records from older test builds
                val sanitized = list.filterNot {
                    it.id.startsWith("def_") ||
                    it.id.startsWith("annabell_") ||
                    it.personName.equals("Annabell", ignoreCase = true)
                }
                _destinations.value = sanitized
                if (sanitized.size != list.size) {
                    saveToDisk(sanitized)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse saved destinations")
                _destinations.value = emptyList()
            }
        }
    }

    private fun saveToDisk(list: List<SavedDestination>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_DESTINATIONS, array.toString()).apply()
    }

    fun addDestination(destination: SavedDestination) {
        val updated = _destinations.value.toMutableList().apply { add(destination) }
        _destinations.value = updated
        saveToDisk(updated)
    }

    fun updateDestination(destination: SavedDestination) {
        val updated = _destinations.value.map { if (it.id == destination.id) destination else it }
        _destinations.value = updated
        saveToDisk(updated)
    }

    fun removeDestination(id: String) {
        val updated = _destinations.value.filterNot { it.id == id }
        _destinations.value = updated
        saveToDisk(updated)
    }

    fun removePersonGroup(personName: String) {
        val updated = _destinations.value.filterNot { it.personName.equals(personName, ignoreCase = true) }
        _destinations.value = updated
        saveToDisk(updated)
    }

    companion object {
        private const val PREFS_NAME = "recondrive_saved_destinations"
        private const val KEY_DESTINATIONS = "saved_destinations_json"
    }
}
