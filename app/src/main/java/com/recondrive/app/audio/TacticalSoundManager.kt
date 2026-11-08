package com.recondrive.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.*

/**
 * Tactical Audio Alert Engine.
 * Procedurally generates high-tech radar pings, sonar sweeps, and tactical speech alerts
 * using pure PCM synthesis and Android Text-to-Speech without external sound file dependencies.
 */
class TacticalSoundManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.UK
                    tts?.setPitch(0.95f)
                    tts?.setSpeechRate(1.1f)
                    isTtsInitialized = true
                    Timber.i("Tactical TTS voice engine initialized")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "TTS initialization error")
        }
    }

    /**
     * Play custom alert sound by name.
     */
    fun playAlert(alertType: String, spokenPhrase: String = "") {
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
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 600
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 880.0

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    // Exponential acoustic decay
                    val envelope = exp(-time * 6.5)
                    val sinVal = sin(2.0 * Math.PI * freq * time)
                    // Add subtle overtone for metallic resonance
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
     * Ascending frequency chirp (1200Hz to 2400Hz quick sweep).
     */
    fun playTacticalChirp() {
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
                    // Alternating warble every 100ms
                    val isHigh = (i / (sampleRate * 0.10).toInt()) % 2 == 0
                    val currentFreq = if (isHigh) freq1 else freq2
                    val sinVal = sin(2.0 * Math.PI * currentFreq * time)
                    // Add harsh square-ish overtone for urgent warning
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
     * Speaks tactical intel string via Text-To-Speech engine.
     */
    fun playVoiceAlert(phrase: String) {
        scope.launch(Dispatchers.Main) {
            try {
                if (isTtsInitialized && tts != null) {
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

    private fun playPcmBuffer(buffer: ShortArray, sampleRate: Int) {
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
    }
}
