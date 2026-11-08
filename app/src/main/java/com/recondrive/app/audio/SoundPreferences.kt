package com.recondrive.app.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Alert Delivery Mode chosen by the user for tactical threats & sensor detections.
 */
enum class AlertDeliveryMode(
    val label: String,
    val shortLabel: String,
    val description: String
) {
    OFF("Nothing", "OFF", "Completely silent, zero notifications or audio alerts"),
    NOTIFICATION("Notification", "NOTIF", "System notification with chime and vibration"),
    VOICE("Voice Alert", "VOICE", "Tactical Text-To-Speech voice alert only"),
    BOTH("Both", "BOTH", "System notification + tactical voice alert together")
}

/**
 * Granular Threat & Sensor Alert Categories.
 */
enum class AlertCategory(
    val id: String,
    val title: String,
    val description: String,
    val defaultMode: AlertDeliveryMode,
    val iconEmoji: String
) {
    SUSPICIOUS_CELL_TOWER(
        id = "suspicious_cell_tower",
        title = "Suspicious Cell Tower / IMSI Catcher",
        description = "Rogue cellular base station, fake cell tower, or stand-alone SDR interception transmitter",
        defaultMode = AlertDeliveryMode.BOTH,
        iconEmoji = "📡"
    ),
    CELLULAR_ANOMALY(
        id = "cellular_anomaly",
        title = "Cellular Encryption & Cipher Anomaly",
        description = "Forced 2G GSM downgrade, unencrypted A5/0 cipher, or rapid TAC/LAC hopping",
        defaultMode = AlertDeliveryMode.NOTIFICATION,
        iconEmoji = "📶"
    ),
    OPEN_DRONE_ID(
        id = "open_drone_id",
        title = "Airborne Drone / OpenDroneID Alert",
        description = "OpenDroneID broadcast from active aerial drone or remote pilot ground controller",
        defaultMode = AlertDeliveryMode.BOTH,
        iconEmoji = "🛸"
    ),
    CO_TRAVEL_FOLLOWING(
        id = "co_travel_following",
        title = "Co-Travel & Surveillance Following",
        description = "Correlated vehicle or mobile device following across multiple distinct turns",
        defaultMode = AlertDeliveryMode.BOTH,
        iconEmoji = "🚘"
    ),
    EMERGENCY_SIREN(
        id = "emergency_siren",
        title = "Emergency Siren Acoustic Doppler",
        description = "Acoustic detection of emergency vehicle siren spectrum (600–1600 Hz)",
        defaultMode = AlertDeliveryMode.VOICE,
        iconEmoji = "🚨"
    ),
    SAVED_WATCHLIST_TARGET(
        id = "saved_watchlist_target",
        title = "Saved Watchlist & ALPR Target",
        description = "Target vehicle plate, custom nickname, or saved beacon in proximity",
        defaultMode = AlertDeliveryMode.BOTH,
        iconEmoji = "🎯"
    ),
    FERROMAGNETIC_MASS(
        id = "ferromagnetic_mass",
        title = "Ferromagnetic Proximity (MAD)",
        description = "Large metallic vehicle mass detected adjacent via magnetometer flux anomaly",
        defaultMode = AlertDeliveryMode.NOTIFICATION,
        iconEmoji = "🧲"
    ),
    KEY_FOB_PROXIMITY(
        id = "key_fob_proximity",
        title = "Car Key / Smart Fob RF Beacon",
        description = "Automotive smart key or immobilizer RF beacon detected in vicinity",
        defaultMode = AlertDeliveryMode.NOTIFICATION,
        iconEmoji = "🔑"
    )
}

/**
 * Noise & Ambient Sound FX Categories (ON/OFF toggles).
 */
enum class SoundFxCategory(
    val id: String,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean,
    val iconEmoji: String
) {
    RADAR_SWEEP_PING(
        id = "radar_sweep_ping",
        title = "Radar Sweep Sonar Ping",
        description = "Synthesized submarine sonar ping (880Hz) during active RF scanning",
        defaultEnabled = true,
        iconEmoji = "🛰️"
    ),
    TARGET_LOCK_CHIRP(
        id = "target_lock_chirp",
        title = "Target Acquisition Chirp",
        description = "Tactical high-frequency audio chirp (1200–2600Hz) when a target enters range",
        defaultEnabled = true,
        iconEmoji = "🔊"
    ),
    TACTICAL_UI_CLICKS(
        id = "tactical_ui_clicks",
        title = "Tactical UI Clicks",
        description = "Mechanical acoustic feedback on button taps and mode switches",
        defaultEnabled = true,
        iconEmoji = "🎛️"
    )
}

/**
 * Manages user sound preferences, alert delivery modes, and ambient sound FX.
 * Fully reactive with StateFlow and backed by SharedPreferences.
 */
class SoundPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Master Audio Switch: Instant mute/unmute of all audio output
    private val _isMasterSoundEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_SOUND, true)
    )
    val isMasterSoundEnabled: StateFlow<Boolean> = _isMasterSoundEnabled.asStateFlow()

    // Vibration on notifications
    private val _isNotificationVibrationEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIF_VIBRATION, true)
    )
    val isNotificationVibrationEnabled: StateFlow<Boolean> = _isNotificationVibrationEnabled.asStateFlow()

    // Text-To-Speech speech rate (0.8x to 1.4x, default 1.05x)
    private val _ttsSpeechRate = MutableStateFlow(
        prefs.getFloat(KEY_TTS_RATE, 1.05f)
    )
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    // Reactive StateFlows for each AlertCategory
    private val alertModeFlows: Map<AlertCategory, MutableStateFlow<AlertDeliveryMode>> =
        AlertCategory.values().associateWith { category ->
            val savedName = prefs.getString(KEY_ALERT_PREFIX + category.id, category.defaultMode.name)
            val initialMode = try {
                AlertDeliveryMode.valueOf(savedName ?: category.defaultMode.name)
            } catch (_: Exception) {
                category.defaultMode
            }
            MutableStateFlow(initialMode)
        }

    // Reactive StateFlows for each SoundFxCategory
    private val soundFxFlows: Map<SoundFxCategory, MutableStateFlow<Boolean>> =
        SoundFxCategory.values().associateWith { fx ->
            val initial = prefs.getBoolean(KEY_FX_PREFIX + fx.id, fx.defaultEnabled)
            MutableStateFlow(initial)
        }

    fun getAlertModeFlow(category: AlertCategory): StateFlow<AlertDeliveryMode> {
        return alertModeFlows[category]?.asStateFlow()
            ?: MutableStateFlow(category.defaultMode).asStateFlow()
    }

    fun getAlertMode(category: AlertCategory): AlertDeliveryMode {
        return alertModeFlows[category]?.value ?: category.defaultMode
    }

    fun setAlertMode(category: AlertCategory, mode: AlertDeliveryMode) {
        val flow = alertModeFlows[category] ?: return
        if (flow.value == mode) return
        flow.value = mode
        prefs.edit().putString(KEY_ALERT_PREFIX + category.id, mode.name).apply()
        Timber.i("SoundPreferences: ${category.id} alert mode set to ${mode.name}")
    }

    fun getSoundFxFlow(fx: SoundFxCategory): StateFlow<Boolean> {
        return soundFxFlows[fx]?.asStateFlow()
            ?: MutableStateFlow(fx.defaultEnabled).asStateFlow()
    }

    fun isSoundFxEnabled(fx: SoundFxCategory): Boolean {
        return soundFxFlows[fx]?.value ?: fx.defaultEnabled
    }

    fun setSoundFxEnabled(fx: SoundFxCategory, enabled: Boolean) {
        val flow = soundFxFlows[fx] ?: return
        if (flow.value == enabled) return
        flow.value = enabled
        prefs.edit().putBoolean(KEY_FX_PREFIX + fx.id, enabled).apply()
        Timber.i("SoundPreferences: Sound FX ${fx.id} enabled = $enabled")
    }

    fun setMasterSoundEnabled(enabled: Boolean) {
        if (_isMasterSoundEnabled.value == enabled) return
        _isMasterSoundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MASTER_SOUND, enabled).apply()
        Timber.i("SoundPreferences: Master sound set to $enabled")
    }

    fun setNotificationVibrationEnabled(enabled: Boolean) {
        if (_isNotificationVibrationEnabled.value == enabled) return
        _isNotificationVibrationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NOTIF_VIBRATION, enabled).apply()
        Timber.i("SoundPreferences: Notification vibration set to $enabled")
    }

    fun setTtsSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.75f, 1.5f)
        if (_ttsSpeechRate.value == clamped) return
        _ttsSpeechRate.value = clamped
        prefs.edit().putFloat(KEY_TTS_RATE, clamped).apply()
        Timber.i("SoundPreferences: TTS speech rate set to $clamped")
    }

    companion object {
        private const val PREFS_NAME = "recondrive_tactical_sound_prefs"
        private const val KEY_MASTER_SOUND = "master_sound_enabled"
        private const val KEY_NOTIF_VIBRATION = "notif_vibration_enabled"
        private const val KEY_TTS_RATE = "tts_speech_rate"
        private const val KEY_ALERT_PREFIX = "alert_delivery_"
        private const val KEY_FX_PREFIX = "sound_fx_"
    }
}
