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
            val defaults = createDefaultDestinations()
            _destinations.value = defaults
            saveToDisk(defaults)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<SavedDestination>()
                for (i in 0 until array.length()) {
                    list.add(SavedDestination.fromJson(array.getJSONObject(i)))
                }
                _destinations.value = list
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse saved destinations, restoring defaults")
                val defaults = createDefaultDestinations()
                _destinations.value = defaults
                saveToDisk(defaults)
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

    private fun createDefaultDestinations(): List<SavedDestination> {
        return listOf(
            SavedDestination(
                id = "def_home",
                name = "Home",
                addressOrSubtext = "Brighton Seafront // Sector 7",
                lat = 50.8225,
                lon = -0.1372,
                personName = null,
                iconType = "HOME",
                orderIndex = 0
            ),
            SavedDestination(
                id = "def_work",
                name = "Work // Innovation HQ",
                addressOrSubtext = "Falmer Science Park, Brighton",
                lat = 50.8650,
                lon = -0.0870,
                personName = null,
                iconType = "WORK",
                orderIndex = 1
            ),
            // Stacked Sub-list: Annabell
            SavedDestination(
                id = "annabell_home",
                name = "Home",
                addressOrSubtext = "18 Brunswick Square, Hove",
                lat = 50.8248,
                lon = -0.1580,
                personName = "Annabell",
                iconType = "HOME",
                orderIndex = 0
            ),
            SavedDestination(
                id = "annabell_work",
                name = "Design Studio",
                addressOrSubtext = "Kemp Town Creative Quarter",
                lat = 50.8190,
                lon = -0.1230,
                personName = "Annabell",
                iconType = "WORK",
                orderIndex = 1
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "recondrive_saved_destinations"
        private const val KEY_DESTINATIONS = "saved_destinations_json"
    }
}
