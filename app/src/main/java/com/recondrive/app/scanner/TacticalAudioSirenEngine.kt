package com.recondrive.app.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.*

data class SirenTelemetry(
    val isListening: Boolean = false,
    val isSirenDetected: Boolean = false,
    val sirenType: String = "NONE",
    val isApproaching: Boolean = false,
    val confidencePercent: Int = 0,
    val peakFrequencyHz: Float = 0f,
    val alertMessage: String = "ACOUSTIC SPECTRUM QUIET",
    val lastDetectedTimeMs: Long = 0L
)

/**
 * Tactical Audio Siren Engine
 * Listens to ambient acoustics via microphone array to detect incoming Emergency Vehicles:
 * 1. Multi-rate probing (44.1 kHz, 48 kHz, 16 kHz, 22.05 kHz) ensuring universal CDD compatibility.
 * 2. Analyzes acoustic harmonics in emergency siren bands (600 Hz - 1600 Hz).
 * 3. Classifies Siren Signatures: Police Yelp (180-240 cpm), Police Wail (slow sweep), European Hi-Lo (two-tone), Piercer.
 * 4. Doppler Direction Finding: Measures carrier frequency slope (df/dt) over modulation cycles to determine
 *    if the emergency cruiser is APPROACHING (incoming threat) or RECEDING (moving away).
 * 5. Crash-resilient IO coroutine lifecycle: releases AudioRecord exclusively on thread exit to avoid native SIGSEGV.
 */
class TacticalAudioSirenEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _telemetry = MutableStateFlow(SirenTelemetry())
    val telemetry: StateFlow<SirenTelemetry> = _telemetry.asStateFlow()

    @Volatile
    private var isRunning = false
    private var processingJob: Job? = null

    private val bufferSize = 2048   // ~46.4ms temporal resolution at 44.1 kHz

    // Peak Frequency Rolling History for Siren Modulation & Doppler Drift
    private data class PitchSample(val timestampMs: Long, val peakHz: Float, val snr: Float)
    private val pitchHistory = ConcurrentLinkedDeque<PitchSample>()

    @Synchronized
    fun start() {
        if (isRunning) return
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Timber.w("TacticalAudioSirenEngine: RECORD_AUDIO permission not granted; standby mode active")
            _telemetry.update { it.copy(isListening = false, alertMessage = "STANDBY: MIC PERMISSION REQUIRED") }
            return
        }

        isRunning = true
        _telemetry.update { it.copy(isListening = true, alertMessage = "INITIALIZING ACOUSTIC LISTENER...") }

        processingJob?.cancel()
        processingJob = scope.launch {
            startAcousticWorker()
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        processingJob?.cancel()
        processingJob = null
        pitchHistory.clear()
        _telemetry.update { it.copy(isListening = false, isSirenDetected = false, alertMessage = "ACOUSTIC SENSOR STANDBY") }
        Timber.i("TacticalAudioSirenEngine: Acoustic listener stopped")
    }

    @SuppressLint("MissingPermission")
    private suspend fun startAcousticWorker() {
        var record: AudioRecord? = null
        var activeRate = 44100
        try {
            val (rec, rate) = createAudioRecordCandidate()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                Timber.w("TacticalAudioSirenEngine: AudioRecord initialization failed on all candidate rates")
                _telemetry.update { it.copy(isListening = false, alertMessage = "AUDIO HARDWARE UNAVAILABLE") }
                isRunning = false
                return
            }
            record = rec
            activeRate = rate

            try {
                record.startRecording()
            } catch (t: Throwable) {
                Timber.e(t, "TacticalAudioSirenEngine: Exception starting recording")
                _telemetry.update { it.copy(isListening = false, alertMessage = "RECORDING START FAILED") }
                isRunning = false
                return
            }

            _telemetry.update { it.copy(isListening = true, alertMessage = "MONITORING SIREN SPECTRUM (${activeRate}Hz)") }
            Timber.i("TacticalAudioSirenEngine: Acoustic siren listener active at $activeRate Hz")

            val audioBuffer = ShortArray(bufferSize)

            while (coroutineContext.isActive && isRunning) {
                try {
                    val readCount = record.read(audioBuffer, 0, bufferSize)
                    if (readCount > 0) {
                        processAudioPcm(audioBuffer, readCount, activeRate)
                    } else if (readCount == AudioRecord.ERROR_INVALID_OPERATION || readCount == AudioRecord.ERROR_BAD_VALUE) {
                        Timber.w("TacticalAudioSirenEngine: AudioRecord error read code: $readCount")
                        delay(200)
                    } else {
                        delay(50)
                    }
                } catch (t: Throwable) {
                    Timber.w(t, "TacticalAudioSirenEngine: Exception during audio read loop")
                    delay(100)
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "TacticalAudioSirenEngine: Acoustic worker encountered fatal exception")
        } finally {
            isRunning = false
            try {
                if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Throwable) {}
            try {
                record?.release()
            } catch (_: Throwable) {}
            _telemetry.update { it.copy(isListening = false, isSirenDetected = false, alertMessage = "ACOUSTIC SENSOR STANDBY") }
            Timber.i("TacticalAudioSirenEngine: Acoustic hardware cleanly released")
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecordCandidate(): Pair<AudioRecord?, Int> {
        val candidateRates = listOf(44100, 48000, 16000, 22050)
        for (rate in candidateRates) {
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) continue

                val finalBuf = max(minBuf, bufferSize * 2)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    finalBuf
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    return Pair(record, rate)
                } else {
                    try { record.release() } catch (_: Throwable) {}
                }
            } catch (t: Throwable) {
                Timber.w("TacticalAudioSirenEngine: Probe rate $rate failed: ${t.message}")
            }
        }
        return Pair(null, 44100)
    }

    private fun processAudioPcm(samples: ShortArray, count: Int, currentSampleRate: Int) {
        try {
            val now = System.currentTimeMillis()

            // 1. Calculate RMS Energy to discard near-silence
            var sumSquares = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble()
                sumSquares += s * s
            }
            val rms = sqrt(sumSquares / count)
            if (rms < 120.0) { // low background threshold
                // Spectrum quiet
                if (now - _telemetry.value.lastDetectedTimeMs > 3000L && _telemetry.value.isSirenDetected) {
                    _telemetry.update {
                        it.copy(
                            isSirenDetected = false,
                            sirenType = "NONE",
                            confidencePercent = 0,
                            alertMessage = "ACOUSTIC SPECTRUM QUIET"
                        )
                    }
                }
                return
            }

            // 2. Multi-bin Goertzel DFT frequency estimation focused on siren band: 600 Hz to 1600 Hz
            // Steps of 25 Hz for 41 discrete spectrum bins
            val binStepHz = 25.0
            var maxEnergy = 0.0
            var peakHz = 0.0
            var totalSirenBandEnergy = 0.0

            var testHz = 600.0
            while (testHz <= 1600.0) {
                val energy = computeGoertzelEnergy(samples, count, testHz, currentSampleRate.toDouble())
                totalSirenBandEnergy += energy
                if (energy > maxEnergy) {
                    maxEnergy = energy
                    peakHz = testHz
                }
                testHz += binStepHz
            }

            // Check road noise band (100 Hz to 350 Hz): vehicles and wind rumble reside here
            val roadNoiseEnergy = computeGoertzelEnergy(samples, count, 180.0, currentSampleRate.toDouble()) +
                    computeGoertzelEnergy(samples, count, 260.0, currentSampleRate.toDouble())

            val snr = (maxEnergy / (roadNoiseEnergy + 1.0)).toFloat()

            // 3. Peak Tracking & Modulation Analysis
            if (maxEnergy > 1e7 && peakHz in 650.0..1550.0) {
                pitchHistory.addLast(PitchSample(now, peakHz.toFloat(), snr))
            }

            // Prune history older than 5.0 seconds safely without NoSuchElementException
            while (pitchHistory.isNotEmpty()) {
                val oldest = pitchHistory.peekFirst() ?: break
                if (now - oldest.timestampMs > 5000L) {
                    pitchHistory.pollFirst()
                } else {
                    break
                }
            }

            // 4. Pattern Recognition (Yelp, Wail, Hi-Lo, Piercer)
            if (pitchHistory.size >= 8) {
                val recentList = pitchHistory.toList()
                if (recentList.isEmpty()) return
                val minPitch = recentList.minOf { it.peakHz }
                val maxPitch = recentList.maxOf { it.peakHz }
                val pitchDelta = maxPitch - minPitch

                // Siren criteria: periodic modulation with span >= 250 Hz across siren band
                if (pitchDelta >= 280f && minPitch >= 620f && maxPitch <= 1650f) {
                    // Count frequency directional inflection reversals to determine modulation rate
                    var directionChanges = 0
                    var lastSlope = 0
                    for (i in 1 until recentList.size) {
                        val diff = recentList[i].peakHz - recentList[i - 1].peakHz
                        val slope = when {
                            diff > 35f -> 1
                            diff < -35f -> -1
                            else -> 0
                        }
                        if (slope != 0 && lastSlope != 0 && slope != lastSlope) {
                            directionChanges++
                        }
                        if (slope != 0) lastSlope = slope
                    }

                    // Time span of recent samples
                    val timeSpanSec = (recentList.last().timestampMs - recentList.first().timestampMs) / 1000f

                    val cyclesPerMinute = if (timeSpanSec > 0.5f) {
                        (directionChanges / 2.0f / timeSpanSec) * 60f
                    } else 0f

                    val sirenType = when {
                        cyclesPerMinute >= 160f -> "POLICE YELP"
                        cyclesPerMinute in 12f..55f -> "POLICE/FIRE WAIL"
                        pitchDelta in 150f..280f && abs(recentList.last().peakHz - 800f) < 150f -> "EURO HI-LO (TWO-TONE)"
                        cyclesPerMinute > 55f -> "PRIORITY / PIERCER"
                        else -> "EMERGENCY SIREN"
                    }

                    // 5. Doppler Direction Finding (carrier slope analysis over modulation midpoint)
                    // First half vs second half average pitch
                    val half = recentList.size / 2
                    val firstHalfAvg = recentList.take(half).map { it.peakHz }.average()
                    val secondHalfAvg = recentList.drop(half).map { it.peakHz }.average()
                    val dopplerSlope = secondHalfAvg - firstHalfAvg

                    val isApproaching = dopplerSlope > 18.0 // Positive shift indicates incoming Doppler compression
                    val confidence = min(98, (65 + snr * 3.5f).toInt())

                    val directionText = if (isApproaching) "APPROACHING (INCOMING)" else "RECEDING (DEPARTING)"
                    val msg = "🚨 $sirenType • $directionText (%.0f Hz)".format(peakHz)

                    _telemetry.update {
                        it.copy(
                            isSirenDetected = true,
                            sirenType = sirenType,
                            isApproaching = isApproaching,
                            confidencePercent = confidence,
                            peakFrequencyHz = peakHz.toFloat(),
                            alertMessage = msg,
                            lastDetectedTimeMs = now
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "TacticalAudioSirenEngine: Error processing audio PCM")
        }
    }

    /**
     * Optimized Goertzel algorithm for single-bin discrete Fourier energy calculation.
     */
    private fun computeGoertzelEnergy(samples: ShortArray, numSamples: Int, targetFreq: Double, sampleRate: Double): Double {
        val k = (0.5 + (numSamples * targetFreq) / sampleRate).toInt()
        val omega = (2.0 * Math.PI * k) / numSamples
        val cosine = cos(omega)
        val coeff = 2.0 * cosine

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            val sample = samples[i].toDouble()
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }
}
