package com.recondrive.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.*

/**
 * Tactical Audio & Alert Dispatch Engine.
 * Procedurally generates high-tech radar pings, sonar sweeps, and tactical speech alerts
 * using pure PCM synthesis, Android Text-to-Speech, and high-priority system notifications.
 */
class TacticalSoundManager(
    private val context: Context,
    val soundPreferences: SoundPreferences? = null
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        createNotificationChannel()
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.UK
                    tts?.setPitch(0.95f)
                    val rate = soundPreferences?.ttsSpeechRate?.value ?: 1.05f
                    tts?.setSpeechRate(rate)
                    isTtsInitialized = true
                    Timber.i("Tactical TTS voice engine initialized")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "TTS initialization error")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Tactical Threats & Detections",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority tactical alerts for rogue cell towers, drones, following vehicles, and watchlist targets"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Centralized Tactical Alert Dispatcher.
     * Evaluates user preferences per alert category: Nothing (Mute), Notification, Voice (TTS), or Both.
     */
    fun dispatchAlert(
        category: AlertCategory,
        title: String,
        message: String,
        ttsPhrase: String,
        soundType: String = "RADAR_BEEP"
    ) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        val mode = prefs.getAlertMode(category)
        val isMasterOn = prefs.isMasterSoundEnabled.value

        Timber.d("Dispatch alert [${category.id}]: mode=$mode, isMasterOn=$isMasterOn")

        // 1. Post Android Notification if NOTIFICATION or BOTH
        if (mode == AlertDeliveryMode.NOTIFICATION || mode == AlertDeliveryMode.BOTH) {
            postSystemNotification(category, title, message)
        }

        // 2. Play Tactical Audio / TTS if VOICE or BOTH and master audio is active
        if ((mode == AlertDeliveryMode.VOICE || mode == AlertDeliveryMode.BOTH) && isMasterOn) {
            playVoiceAlert(ttsPhrase)
        }
    }

    /**
     * Posts a high-priority heads-up system notification with vibration and tap-to-focus.
     */
    fun postSystemNotification(category: AlertCategory, title: String, message: String) {
        try {
            val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
            val isVibrationOn = prefs.isNotificationVibrationEnabled.value

            val openIntent = Intent(context, com.recondrive.app.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                category.ordinal + 500,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSubText(category.title.uppercase())
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)

            if (isVibrationOn) {
                builder.setVibrate(longArrayOf(0, 250, 150, 250))
            } else {
                builder.setVibrate(longArrayOf(0))
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_BASE_ID + category.ordinal, builder.build())
        } catch (t: Throwable) {
            Timber.w(t, "Failed to post tactical alert notification")
        }
    }

    /**
     * Plays radar sonar ping respecting Master Audio switch and SoundFxCategory.RADAR_SWEEP_PING preference.
     */
    fun playRadarPing(force: Boolean = false) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!force && (!prefs.isMasterSoundEnabled.value || !prefs.isSoundFxEnabled(SoundFxCategory.RADAR_SWEEP_PING))) {
            return
        }
        playSonarPing()
    }

    /**
     * Plays target acquisition chirp respecting Master Audio switch and SoundFxCategory.TARGET_LOCK_CHIRP preference.
     */
    fun playTargetChirp(force: Boolean = false) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!force && (!prefs.isMasterSoundEnabled.value || !prefs.isSoundFxEnabled(SoundFxCategory.TARGET_LOCK_CHIRP))) {
            return
        }
        playTacticalChirp()
    }

    /**
     * Plays mechanical tactical UI click feedback respecting SoundFxCategory.TACTICAL_UI_CLICKS preference.
     */
    fun playUiClick(force: Boolean = false) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!force && (!prefs.isMasterSoundEnabled.value || !prefs.isSoundFxEnabled(SoundFxCategory.TACTICAL_UI_CLICKS))) {
            return
        }
        playClickPcm()
    }

    /**
     * Dispatches an interactive simulation preview test alert for any category.
     */
    fun testAlert(category: AlertCategory) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        val samplePhrase = when (category) {
            AlertCategory.SUSPICIOUS_CELL_TOWER -> "Warning: Rogue base station detected. High interception risk."
            AlertCategory.CELLULAR_ANOMALY -> "Caution: Cellular encryption anomaly detected."
            AlertCategory.OPEN_DRONE_ID -> "Alert: Airborne drone active in local airspace."
            AlertCategory.CO_TRAVEL_FOLLOWING -> "Caution: Vehicle following correlated across turns."
            AlertCategory.EMERGENCY_SIREN -> "Alert: Emergency vehicle acoustic siren detected."
            AlertCategory.SAVED_WATCHLIST_TARGET -> "Target Recon Alpha in range."
            AlertCategory.FERROMAGNETIC_MASS -> "Notice: Ferromagnetic automobile mass adjacent."
            AlertCategory.KEY_FOB_PROXIMITY -> "Notice: Vehicle smart key beacon detected."
        }
        val mode = prefs.getAlertMode(category)
        if (mode == AlertDeliveryMode.OFF) {
            // Give user feedback if currently muted
            playUiClick(force = true)
            playVoiceAlert("Alert delivery for ${category.title} is currently set to Nothing.")
            return
        }
        dispatchAlert(
            category = category,
            title = "${category.iconEmoji} TEST: ${category.title.uppercase()}",
            message = "Simulated test alert. Mode: ${mode.name}",
            ttsPhrase = samplePhrase,
            soundType = "INTERCEPTION_ALERT"
        )
    }

    /**
     * Previews synthesized sound FX directly regardless of toggle state.
     */
    fun testSoundFx(fx: SoundFxCategory) {
        when (fx) {
            SoundFxCategory.RADAR_SWEEP_PING -> playSonarPing()
            SoundFxCategory.TARGET_LOCK_CHIRP -> playTacticalChirp()
            SoundFxCategory.TACTICAL_UI_CLICKS -> playClickPcm()
        }
    }

    /**
     * Legacy compatibility bridge for custom alert sound by name.
     */
    fun playAlert(alertType: String, spokenPhrase: String = "") {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        when (alertType.uppercase()) {
            "SONAR_PING" -> playSonarPing()
            "TACTICAL_CHIRP" -> playTacticalChirp()
            "RADAR_BEEP" -> playRadarBeep()
            "INTERCEPTION_ALERT" -> playInterceptionAlert()
            "VOICE_ALERT" -> playVoiceAlert(spokenPhrase.ifBlank { "Tactical target in range" })
            "SILENT" -> { /* No audio */ }
            else -> playSonarPing()
        }
    }

    /**
     * Classic submarine / tactical sonar ping (880Hz decaying sine wave with acoustic resonance).
     */
    fun playSonarPing() {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 600
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 880.0

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val envelope = exp(-time * 6.5)
                    val sinVal = sin(2.0 * Math.PI * freq * time)
                    val overtone = 0.25 * sin(2.0 * Math.PI * (freq * 1.5) * time)
                    buffer[i] = ((sinVal + overtone) * envelope * Short.MAX_VALUE * 0.85).toInt().toShort()
                }

                playPcmBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing sonar ping")
            }
        }
    }

    /**
     * Ascending frequency chirp (1100Hz to 2600Hz quick sweep).
     */
    fun playTacticalChirp() {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 250
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val startFreq = 1100.0
                val endFreq = 2600.0

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val currentFreq = startFreq + (endFreq - startFreq) * progress
                    val time = i.toDouble() / sampleRate
                    val envelope = if (progress < 0.1) progress / 0.1 else (1.0 - progress)
                    val sinVal = sin(2.0 * Math.PI * currentFreq * time)
                    buffer[i] = (sinVal * envelope * Short.MAX_VALUE * 0.75).toInt().toShort()
                }

                playPcmBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing chirp")
            }
        }
    }

    /**
     * Sharp dual-strobe radar beeps (1600Hz pulses).
     */
    fun playRadarBeep() {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 300
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 1600.0

                val pulse1End = (numSamples * 0.35).toInt()
                val pauseEnd = (numSamples * 0.50).toInt()

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val inPulse = i < pulse1End || i > pauseEnd
                    val envelope = if (inPulse) 0.8 else 0.0
                    val sinVal = sin(2.0 * Math.PI * freq * time)
                    buffer[i] = (sinVal * envelope * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing radar beep")
            }
        }
    }

    /**
     * High-priority tactical interception alarm (alternating 2200Hz and 1400Hz warble).
     */
    fun playInterceptionAlert() {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 500
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val freq1 = 2200.0
                val freq2 = 1400.0

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val isHigh = (i / (sampleRate * 0.10).toInt()) % 2 == 0
                    val currentFreq = if (isHigh) freq1 else freq2
                    val sinVal = sin(2.0 * Math.PI * currentFreq * time)
                    val overtone = 0.3 * sin(2.0 * Math.PI * (currentFreq * 2.0) * time)
                    buffer[i] = ((sinVal + overtone) * 0.85 * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing interception alert")
            }
        }
    }

    /**
     * Short crisp mechanical UI click.
     */
    private fun playClickPcm() {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 25
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val startFreq = 2200.0
                val endFreq = 800.0

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val currentFreq = startFreq + (endFreq - startFreq) * progress
                    val time = i.toDouble() / sampleRate
                    val envelope = (1.0 - progress) * (1.0 - progress)
                    val sinVal = sin(2.0 * Math.PI * currentFreq * time)
                    buffer[i] = (sinVal * envelope * Short.MAX_VALUE * 0.50).toInt().toShort()
                }

                playPcmBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing click")
            }
        }
    }

    /**
     * Speaks tactical intel string via Text-To-Speech engine.
     */
    fun playVoiceAlert(phrase: String) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch(Dispatchers.Main) {
            try {
                if (isTtsInitialized && tts != null) {
                    val rate = prefs.ttsSpeechRate.value
                    tts?.setSpeechRate(rate)
                    tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "TACTICAL_ALERT_${System.currentTimeMillis()}")
                } else {
                    playSonarPing()
                }
            } catch (e: Exception) {
                Timber.w(e, "Error playing voice alert")
                playSonarPing()
            }
        }
    }

    /**
     * Synthesizes rapid acoustic Geiger clicks for 3D RF beam mapping
     * or low-frequency warning thumps when entering a dead zone.
     */
    fun playBfiGeigerClick(isDeadZone: Boolean) {
        val prefs = soundPreferences ?: com.recondrive.app.ReconApplication.instance.soundPreferences
        if (!prefs.isMasterSoundEnabled.value) return

        scope.launch {
            try {
                val sampleRate = 44100
                if (isDeadZone) {
                    // Low ominous warning thump (300Hz)
                    val durationMs = 60
                    val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                    val buffer = ShortArray(numSamples)
                    val freq = 320.0
                    for (i in 0 until numSamples) {
                        val time = i.toDouble() / sampleRate
                        val envelope = exp(-i.toDouble() / (sampleRate * 0.025))
                        val sinVal = sin(2.0 * Math.PI * freq * time)
                        buffer[i] = (sinVal * envelope * Short.MAX_VALUE * 0.70).toInt().toShort()
                    }
                    playPcmBuffer(buffer, sampleRate)
                } else {
                    // Crisp high-frequency Geiger ping (2600Hz)
                    val durationMs = 18
                    val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                    val buffer = ShortArray(numSamples)
                    val freq = 2600.0
                    for (i in 0 until numSamples) {
                        val time = i.toDouble() / sampleRate
                        val envelope = exp(-i.toDouble() / (sampleRate * 0.005))
                        val sinVal = sin(2.0 * Math.PI * freq * time)
                        buffer[i] = (sinVal * envelope * Short.MAX_VALUE * 0.45).toInt().toShort()
                    }
                    playPcmBuffer(buffer, sampleRate)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error synthesizing BFI Geiger click")
            }
        }
    }

    private fun playPcmBuffer(buffer: ShortArray, sampleRate: Int) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        } catch (t: Throwable) {
            Timber.w(t, "AudioTrack playback error")
        }
    }

    companion object {
        const val CHANNEL_ALERTS_ID = "recondrive_tactical_alerts"
        private const val NOTIFICATION_BASE_ID = 2000
    }
}
