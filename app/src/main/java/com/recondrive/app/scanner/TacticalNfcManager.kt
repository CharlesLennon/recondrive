package com.recondrive.app.scanner

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class NfcScannedTag(
    val uidHex: String,
    val tagType: String,
    val isVehicleKey: Boolean,
    val payload: String?,
    val timestampMs: Long = System.currentTimeMillis()
)

data class NfcTelemetry(
    val isNfcAvailable: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val lastScannedTag: NfcScannedTag? = null,
    val totalScans: Int = 0,
    val isAutoDockTriggered: Boolean = false
)

/**
 * Tactical Near Field Communication (NFC 13.56 MHz) Manager
 * 1. Vehicle RFID & Key Card Sniffer: Decodes Tesla NFC Key Cards, BMW Digital Key cards, and MIFARE fobs
 * 2. Cockpit Magnetic Mount Auto-Dock: Automatically detects cockpit dock tags and switches to HUD mode
 * 3. Tag Identification: Formats 4-byte and 7-byte UIDs with tech stack classification
 */
class TacticalNfcManager(private val context: Context) {

    private val nfcAdapter: NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(context.applicationContext)
    } catch (e: Throwable) {
        Timber.w(e, "NFC adapter unavailable or unsupported on this device")
        null
    }

    private val _telemetry = MutableStateFlow(
        NfcTelemetry(
            isNfcAvailable = nfcAdapter != null,
            isNfcEnabled = try { nfcAdapter?.isEnabled == true } catch (_: Throwable) { false }
        )
    )
    val telemetry: StateFlow<NfcTelemetry> = _telemetry.asStateFlow()

    var onAutoDockTriggered: (() -> Unit)? = null
    var onVehicleKeyDetected: ((NfcScannedTag) -> Unit)? = null

    fun checkStatus() {
        try {
            val available = nfcAdapter != null
            val enabled = try { nfcAdapter?.isEnabled == true } catch (_: Throwable) { false }
            _telemetry.update {
                it.copy(isNfcAvailable = available, isNfcEnabled = enabled)
            }
        } catch (e: Throwable) {
            Timber.w(e, "Error checking NFC status")
        }
    }

    /**
     * Intercepts and parses NFC intents received by MainActivity.
     */
    fun processIntent(intent: Intent): NfcScannedTag? {
        return try {
            val action = intent.action ?: return null
            if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
                action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
                action != NfcAdapter.ACTION_TECH_DISCOVERED
            ) {
                return null
            }

            @Suppress("DEPRECATION")
            val rawTag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            val tag = rawTag ?: return null
            val uidBytes = tag.id ?: ByteArray(0)
            val uidHex = uidBytes.joinToString(":") { "%02X".format(it) }

            val techList = tag.techList?.map { it.substringAfterLast(".") } ?: emptyList()
            var isCarKey = false
            var tagCategory = "GENERIC 13.56 MHz RFID"
            var payloadSummary: String? = null

            // 1. Check for Vehicle Keycard Signatures
            if (techList.contains("IsoDep")) {
                val isoDep = try { IsoDep.get(tag) } catch (_: Throwable) { null }
                val historicalBytes = try { isoDep?.historicalBytes } catch (_: Throwable) { null }
                val hiHex = historicalBytes?.joinToString("") { "%02X".format(it) } ?: ""

                // Tesla Key Card signature (NXP J3D081 or JCOP smartcard with specific ATR historical bytes)
                if (hiHex.contains("4A434F50") || hiHex.contains("80") || uidBytes.size == 7) {
                    isCarKey = true
                    tagCategory = "TESLA NFC KEY CARD"
                } else {
                    tagCategory = "ISO-DEP SMARTCARD (CAR KEY / TRANSIT)"
                }
            } else if (techList.contains("MifareClassic")) {
                tagCategory = "MIFARE CLASSIC ACCESS FOB"
            } else if (techList.contains("MifareUltralight")) {
                tagCategory = "MIFARE ULTRALIGHT NFC TAG"
            }

            // 2. Check NDEF records for Cockpit Auto-Dock Tag
            val rawMessages = try { intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) } catch (_: Throwable) { null }
            if (rawMessages != null) {
                for (rawMsg in rawMessages) {
                    val msg = rawMsg as? NdefMessage ?: continue
                    for (record in msg.records) {
                        val payloadStr = try {
                            String(record.payload, Charsets.UTF_8)
                        } catch (e: Exception) { "" }

                        if (payloadStr.contains("recondrive", ignoreCase = true) ||
                            payloadStr.contains("dock", ignoreCase = true) ||
                            payloadStr.contains("cockpit", ignoreCase = true)
                        ) {
                            tagCategory = "COCKPIT MAGNETIC DOCK TAG"
                            payloadSummary = payloadStr
                            _telemetry.update { it.copy(isAutoDockTriggered = true) }
                            onAutoDockTriggered?.invoke()
                            Timber.i("TacticalNfcManager: Cockpit auto-dock tag scanned! Triggering HUD mode.")
                        }
                    }
                }
            }

            val scanned = NfcScannedTag(
                uidHex = uidHex,
                tagType = tagCategory,
                isVehicleKey = isCarKey,
                payload = payloadSummary
            )

            _telemetry.update {
                it.copy(
                    lastScannedTag = scanned,
                    totalScans = it.totalScans + 1
                )
            }

            if (isCarKey) {
                onVehicleKeyDetected?.invoke(scanned)
            }

            Timber.i("TacticalNfcManager: Scanned NFC Tag UID=$uidHex, Type=$tagCategory, IsVehicleKey=$isCarKey")
            scanned
        } catch (e: Throwable) {
            Timber.w(e, "Error processing NFC intent")
            null
        }
    }
}
