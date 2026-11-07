package com.recondrive.app.scanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent Repository for Saved Tactical RF Targets.
 * Saves user annotations including vehicle registration number plates, physical door numbers,
 * group tags, and custom alert sound triggers to local JSON storage.
 */
class SavedRfDeviceRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val storageFile = File(context.filesDir, "saved_rf_devices.json")

    private val deviceMap = ConcurrentHashMap<String, SavedRfDevice>()
    private val _savedDevices = MutableStateFlow<List<SavedRfDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedRfDevice>> = _savedDevices.asStateFlow()

    init {
        loadFromDisk()
    }

    fun getDevice(id: String): SavedRfDevice? {
        return deviceMap[id.uppercase()]
    }

    fun saveDevice(device: SavedRfDevice) {
        val normalized = device.copy(
            id = device.id.uppercase(),
            numberPlate = device.numberPlate.uppercase().trim()
        )
        deviceMap[normalized.id] = normalized
        syncAndPersist()
    }

    fun deleteDevice(id: String) {
        deviceMap.remove(id.uppercase())
        syncAndPersist()
    }

    fun recordDetection(id: String, lat: Double?, lon: Double?) {
        val existing = deviceMap[id.uppercase()] ?: return
        val updated = existing.copy(
            detectionCount = existing.detectionCount + 1,
            lastSeenTimestamp = System.currentTimeMillis(),
            lastObservedLat = lat ?: existing.lastObservedLat,
            lastObservedLon = lon ?: existing.lastObservedLon
        )
        deviceMap[updated.id] = updated
        syncAndPersist()
    }

    fun getDistinctGroups(): List<String> {
        val groups = deviceMap.values.map { it.group }.filter { it.isNotBlank() }.distinct()
        return if (groups.isEmpty()) {
            listOf("KNOWN VEHICLES", "SURVEILLANCE", "NEIGHBORS", "WORK", "CUSTOM")
        } else {
            groups
        }
    }

    private fun syncAndPersist() {
        val list = deviceMap.values.toList().sortedByDescending { it.lastSeenTimestamp }
        _savedDevices.value = list

        scope.launch {
            try {
                val array = JSONArray()
                for (dev in list) {
                    val obj = JSONObject().apply {
                        put("id", dev.id)
                        put("originalName", dev.originalName)
                        put("customNickname", dev.customNickname)
                        put("numberPlate", dev.numberPlate)
                        put("doorNumberOrAddress", dev.doorNumberOrAddress)
                        put("notes", dev.notes)
                        put("group", dev.group)
                        put("alertSound", dev.alertSound)
                        put("notifyOnDetection", dev.notifyOnDetection)
                        put("firstSeenTimestamp", dev.firstSeenTimestamp)
                        put("lastSeenTimestamp", dev.lastSeenTimestamp)
                        put("detectionCount", dev.detectionCount)
                        dev.lastObservedLat?.let { put("lastObservedLat", it) }
                        dev.lastObservedLon?.let { put("lastObservedLon", it) }
                    }
                    array.put(obj)
                }
                storageFile.writeText(array.toString(2))
            } catch (e: Exception) {
                Timber.w(e, "Error saving RF devices to disk")
            }
        }
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) {
            // Seed a sample saved target to showcase feature immediately
            val sample = SavedRfDevice(
                id = "4C:FC:AA:8B:19:2E",
                originalName = "TESLA MODEL 3 (CONNECTED)",
                customNickname = "Neighbor's Tesla",
                numberPlate = "LJ71 WXA",
                doorNumberOrAddress = "Door #42, High Street",
                notes = "White Model 3, parked on front gravel drive",
                group = "KNOWN VEHICLES",
                alertSound = "SONAR_PING",
                notifyOnDetection = true
            )
            deviceMap[sample.id] = sample
            syncAndPersist()
            return
        }

        try {
            val text = storageFile.readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dev = SavedRfDevice(
                    id = obj.getString("id"),
                    originalName = obj.optString("originalName", "Unknown Target"),
                    customNickname = obj.optString("customNickname", ""),
                    numberPlate = obj.optString("numberPlate", ""),
                    doorNumberOrAddress = obj.optString("doorNumberOrAddress", ""),
                    notes = obj.optString("notes", ""),
                    group = obj.optString("group", "SURVEILLANCE"),
                    alertSound = obj.optString("alertSound", "SONAR_PING"),
                    notifyOnDetection = obj.optBoolean("notifyOnDetection", true),
                    firstSeenTimestamp = obj.optLong("firstSeenTimestamp", System.currentTimeMillis()),
                    lastSeenTimestamp = obj.optLong("lastSeenTimestamp", System.currentTimeMillis()),
                    detectionCount = obj.optInt("detectionCount", 1),
                    lastObservedLat = if (obj.has("lastObservedLat")) obj.getDouble("lastObservedLat") else null,
                    lastObservedLon = if (obj.has("lastObservedLon")) obj.getDouble("lastObservedLon") else null
                )
                deviceMap[dev.id.uppercase()] = dev
            }
            _savedDevices.value = deviceMap.values.toList().sortedByDescending { it.lastSeenTimestamp }
        } catch (e: Exception) {
            Timber.w(e, "Error loading saved RF devices from disk")
        }
    }
}
