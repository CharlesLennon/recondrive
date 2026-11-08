package com.recondrive.app.scanner

/**
 * Tactical classification of detected RF emitters in the local spectrum.
 */
enum class RfCategory {
    VEHICLE,
    CAR_KEY,
    CCTV_SURVEILLANCE,
    DRONE_UAV,
    ACCESS_POINT,
    CELL_TOWER
}

/**
 * Sensor and radio frequency technology utilized by ReconDrive to detect and classify a target.
 */
enum class DetectionTechnology(
    val code: String,
    val displayName: String,
    val shortName: String,
    val frequencyBand: String,
    val detectionPrinciple: String,
    val tacticalSignificance: String,
    val technicalDetails: String
) {
    WIFI_BEACON(
        code = "WIFI_BEACON",
        displayName = "Wi-Fi 802.11 Beacon Sniffing",
        shortName = "Wi-Fi Beacon",
        frequencyBand = "2.4 GHz / 5.0 GHz / 6.0 GHz ISM & UNII Spectrum",
        detectionPrinciple = "Discovered via 802.11 Beacon and Probe Response management frames broadcast periodically (~102.4ms) by access points, mobile hotspots, and vehicle infotainment systems.",
        tacticalSignificance = "Locates fixed infrastructure, smart highway beacons, roadside Wi-Fi relays, and vehicle mobile hotspots. Provides high-reliability environmental RF anchor points.",
        technicalDetails = "Distance is calculated via log-distance path loss: P_rx = P_0 - 10n*log10(d), calibrated at n=2.4 (2.4GHz) and n=2.8 (5GHz). BSSID, SSID, and channel width are parsed directly from Wi-Fi hardware frame buffers."
    ),
    BLE_ADVERTISING(
        code = "BLE_ADVERTISING",
        displayName = "Bluetooth Low Energy (BLE) Advertising",
        shortName = "BLE Sniffer",
        frequencyBand = "2.402 GHz - 2.480 GHz (BLE Channels 37, 38, 39)",
        detectionPrinciple = "Continuous RF passive scanning on primary 2.4 GHz advertising channels (CH 37 = 2402 MHz, CH 38 = 2426 MHz, CH 39 = 2480 MHz) for undirected broadcast advertisement packets (ADV_IND, ADV_NONCONN_IND).",
        tacticalSignificance = "Detects nearby vehicle digital keys (Tesla Phone Key, Ford SYNC, BMW Comfort Access), tracker fobs (Apple AirTag, Samsung SmartTag, Tile), TPMS tire pressure sensors, and covert electronics.",
        technicalDetails = "Extracted via BluetoothLeScanner hardware filters. Decodes 16-bit/128-bit Service UUIDs, 16-bit Manufacturer IDs (e.g. Apple 0x004C, Samsung 0x0075, Tile 0x006B), and calibrated 1-meter RSSI reference power."
    ),
    MAC_SNIFFING(
        code = "MAC_SNIFFING",
        displayName = "MAC Sniffing & OUI Fingerprinting",
        shortName = "MAC Sniffer",
        frequencyBand = "Layer 2 IEEE 802 Data Link Frame Analysis",
        detectionPrinciple = "Deep packet header inspection of 48-bit hardware MAC/BSSID addresses. Deconstructs the 24-bit Organizationally Unique Identifier (OUI) against the IEEE Registration Authority database and inspects the U/L (Universally/Locally Administered) address bit.",
        tacticalSignificance = "Identifies unadvertised hardware vendors (e.g. Flock Safety ALPR cameras, Axon Police Signal, Hikvision optics, DJI UAV controllers) even when SSIDs are hidden or masked. Flags ephemeral randomized addresses used by modern smartphones to evade tracking.",
        technicalDetails = "Bit 1 of Byte 0 indicates Locally Administered / Private Randomized MAC (0x02). Matches manufacturer hardware registries to unmask clandestine surveillance devices and law enforcement traffic sensors."
    ),
    BT_CLASSIC_INQUIRY(
        code = "BT_CLASSIC_INQUIRY",
        displayName = "Bluetooth Classic BR/EDR Inquiry",
        shortName = "BT Classic",
        frequencyBand = "2.400 GHz - 2.4835 GHz Frequency-Hopping Spread Spectrum (FHSS)",
        detectionPrinciple = "Active inquiry scanning sequence sweeping across 79 Bluetooth channels at 1600 hops/second to elicit Inquiry Response packets carrying Class of Device (CoD) and Friendly Name.",
        tacticalSignificance = "Identifies paired vehicle handsfree cockpits, automotive head units (Apple CarPlay, Android Auto), Bluetooth OBD-II engine monitors, and commercial fleet telematics.",
        technicalDetails = "Parses Class of Device (CoD) bitfields: Car Audio (0x0420), Handsfree (0x0408), and Telephony (0x0200) to isolate vehicle infotainment systems."
    ),
    GEOMAGNETIC_MAD(
        code = "GEOMAGNETIC_MAD",
        displayName = "Geomagnetic Flux MAD Sensor",
        shortName = "Geomagnetic MAD",
        frequencyBand = "Triaxial Magnetic Flux Density (DC ~ 50 Hz, microtesla µT)",
        detectionPrinciple = "Magnetic Anomaly Detection (MAD) utilizing the smartphone's triaxial Hall-effect / anisotropic magneto-resistive (AMR) magnetometer. Computes differential magnetic flux deltas ΔB = |B_live - B_baseline| relative to the Earth's ambient geomagnetic field (~30-60 µT).",
        tacticalSignificance = "Passively detects large ferromagnetic masses (vehicle chassis, engines, armored panels) in adjacent lanes or blind spots within 1.5 to 5 meters. Operates through dense fog, complete darkness, and heavy RF jamming without emitting any detectable radiation.",
        technicalDetails = "Flux distortion ΔB > 18 µT indicates vehicle proximity; ΔB > 28 µT indicates heavy adjacent vehicular presence. Completely passive and impervious to RF radar detectors."
    ),
    CELLULAR_RADIO_AUDIT(
        code = "CELLULAR_RADIO_AUDIT",
        displayName = "Cellular Base Station Radio Audit",
        shortName = "Cellular Mast",
        frequencyBand = "700 MHz - 3800 MHz (LTE Bands 1/3/7/20/28, 5G NR n78, GSM 900/1800)",
        detectionPrinciple = "Continuous radio resource monitoring via Android TelephonyManager. Audits Physical Cell IDs (PCI/PSC), Tracking Area Codes (TAC/LAC), EARFCN/NR-ARFCN carriers, and RSRP/RSRQ signal quality across serving and neighbor base stations.",
        tacticalSignificance = "Maps cellular telecommunication infrastructure, tracks cell tower handovers, and detects rogue base stations (IMSI Catchers / Stingrays) attempting forced protocol downgrades (e.g. LTE to unencrypted 2G GSM A5/0) or unauthorized TAC hijacking.",
        technicalDetails = "Monitors Broadcast Control Channel (BCCH) and System Information Blocks (SIB1/SIB2). Flags unlisted cellular transmitters with anomalous transmit power or non-standard carrier parameters."
    ),
    WIFI_DIRECT_P2P(
        code = "WIFI_DIRECT_P2P",
        displayName = "Wi-Fi Direct Peer-to-Peer (P2P)",
        shortName = "Wi-Fi Direct",
        frequencyBand = "2.4 GHz / 5.0 GHz Wi-Fi Direct Peer Channels",
        detectionPrinciple = "Wi-Fi Alliance P2P discovery listening on social channels (1, 6, 11) for probe requests and beacon frames carrying P2P Information Elements (IEs) and Group Owner negotiation packets.",
        tacticalSignificance = "Detects ad-hoc in-vehicle links (wireless Apple CarPlay, Android Auto Wi-Fi projection, Wi-Fi Display / Miracast) and mobile peer devices communicating outside standard router infrastructure.",
        technicalDetails = "Captured via Android WifiP2pManager listeners. Extracts device name, primary/secondary device types, and P2P device MAC addresses."
    ),
    NEAR_INFRARED_OPTICAL(
        code = "NEAR_INFRARED_OPTICAL",
        displayName = "Near-Infrared Optical Sensor (NIR)",
        shortName = "NIR Optical",
        frequencyBand = "850 nm - 940 nm Near-Infrared Spectrum (Optical)",
        detectionPrinciple = "Optical photodiode differential sensing. Monitors rapid lux differentials and high-frequency light pulses in the 850nm and 940nm wavelengths emitted by active infrared CCTV illuminators and laser speed detection equipment.",
        tacticalSignificance = "Reveals covert roadside surveillance cameras, night-vision security optics, and laser speed traps operating in dark or unlit highway environments.",
        technicalDetails = "Filters out slow ambient daylight changes via high-pass differential lux thresholding (Δlux > 15 lux within 80ms) characteristic of pulsed NIR illuminator arrays."
    ),
    NFC_SMARTCARD(
        code = "NFC_SMARTCARD",
        displayName = "NFC Smartcard Proximity Induction",
        shortName = "NFC Induction",
        frequencyBand = "13.56 MHz High Frequency (HF) ISM Band",
        detectionPrinciple = "Near-field electromagnetic induction coupling via 13.56 MHz loop antenna. Engages ISO/IEC 14443 Type A/B and ISO/IEC 18092 contactless communication with smartcards and RFID transponders within 0-4 cm.",
        tacticalSignificance = "Verifies contactless vehicle smart keycards (Tesla Keycard, BMW NFC Keycard), vehicle immobilizer transponders, and physical access credentials tapped near the host console.",
        technicalDetails = "Reads Card Unique Identifier (UID) hex string, Answer To Request (ATQA), Select Acknowledge (SAK), and cryptographic application identifiers (AIDs)."
    ),
    UWB_PULSE_RANGING(
        code = "UWB_PULSE_RANGING",
        displayName = "Ultra-Wideband Impulse Ranging (UWB)",
        shortName = "UWB Ranging",
        frequencyBand = "6.49 GHz - 7.99 GHz (IEEE 802.15.4z Channels 5 & 9)",
        detectionPrinciple = "Nanosecond radio frequency pulses measuring precise two-way Time-of-Flight (ToF) and Phase Difference of Arrival (PDoA) across multiple antenna elements.",
        tacticalSignificance = "Car Connectivity Consortium (CCC) Digital Key 3.0 ranging used by Apple CarKey, BMW Digital Key Plus, and Genesis/Hyundai smart fobs for centimetre-accurate vehicle proximity without relay attack vulnerabilities.",
        technicalDetails = "Delivers ±10 cm spatial ranging accuracy with real-time azimuth and elevation angle calculations."
    ),
    PUBLIC_CCTV_STREAM(
        code = "PUBLIC_CCTV_STREAM",
        displayName = "Geo-Referenced Public CCTV Network",
        shortName = "Public CCTV",
        frequencyBand = "Optical Video / IP Transport Stream (Geo-Spatial Telemetry)",
        detectionPrinciple = "Real-time geo-spatial spatial proximity querying against official public highway transportation feeds (Transport for London TfL, National Highways JamCam, Traffic England).",
        tacticalSignificance = "Provides optical road awareness, traffic flow verification, weather conditions, and visual perimeter surveillance directly ahead of vehicle trajectory.",
        technicalDetails = "Streams live JPEG frames and camera telemetry synced with GPS coordinates, compass bearing, operator attribution, and road junction metadata."
    ),
    ACOUSTIC_SIREN_RECOGNITION(
        code = "ACOUSTIC_SIREN_RECOGNITION",
        displayName = "Acoustic Siren Spectral Recognition",
        shortName = "Acoustic Siren",
        frequencyBand = "500 Hz - 2500 Hz Audio Spectrum (Wail, Yelp, Two-Tone)",
        detectionPrinciple = "Real-time audio microphone FFT frequency domain peak tracking. Detects characteristic frequency modulation cycles of emergency vehicle sirens.",
        tacticalSignificance = "Early auditory warning of approaching police, ambulance, or fire emergency vehicles before visual or RF detection.",
        technicalDetails = "FFT spectral analysis checks for 10-18 cycles/min (wail) or 150-180 cycles/min (yelp) modulation profiles."
    );

    companion object {
        fun fromCode(code: String?): DetectionTechnology {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: WIFI_BEACON
        }
    }
}

/**
 * Real-time motion trend of a target relative to host vehicle.
 */
enum class RelativeMotionTrend {
    APPROACHING,     // Signal increasing / distance closing
    RECEDING,        // Signal decreasing / target moving away
    STATIONARY       // Distance steady / co-moving
}

/**
 * Single observation breadcrumb of a target relative to host device location.
 */
data class DeviceBreadcrumb(
    val timestamp: Long = System.currentTimeMillis(),
    val hostLat: Double,
    val hostLon: Double,
    val relativeBearingDeg: Float,     // Relative angle (0° = straight ahead of host vehicle)
    val distanceMeters: Double,
    val rssi: Int,
    val targetLat: Double,
    val targetLon: Double
)

/**
 * User-saved intelligence and custom annotations for a specific emitter/device.
 */
data class SavedRfDevice(
    val id: String,                         // MAC Address or BSSID (Primary Key)
    val originalName: String,               // Original detected name
    val customNickname: String = "",       // Custom user alias
    val numberPlate: String = "",           // UK Vehicle registration mark
    val doorNumberOrAddress: String = "",   // Physical house / door number
    val notes: String = "",                 // User notes
    val group: String = "SURVEILLANCE",     // Group / Category (e.g. "KNOWN VEHICLES", "SURVEILLANCE", "WORK", "CUSTOM")
    val alertSound: String = "SONAR_PING",  // "SONAR_PING", "TACTICAL_CHIRP", "RADAR_BEEP", "VOICE_ALERT", "SILENT"
    val notifyOnDetection: Boolean = true,  // Trigger acoustic alert and notification when seen
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val detectionCount: Int = 1,
    val lastObservedLat: Double? = null,
    val lastObservedLon: Double? = null
)

/**
 * A detected RF target identified via Wi-Fi 802.11 beacon frames or Bluetooth LE advertisement packets.
 */
data class DetectedRfTarget(
    val id: String,                         // Unique key (BSSID or BLE address)
    val name: String,                       // Classified name (e.g. "Tesla Model 3/Y", "Hikvision CCTV")
    val rawSsidOrBleName: String,           // Raw SSID or advertised Bluetooth name
    val macAddress: String,                 // MAC address / BSSID with OUI
    val vendor: String,                     // Manufacturer / Vendor (e.g. "Tesla Motors", "Hikvision")
    val category: RfCategory,               // Primary classification
    val rssi: Int,                          // Received Signal Strength Indicator in dBm (-30 to -100)
    val estimatedDistanceMeters: Double,    // Distance estimate in meters calculated from RSSI
    val frequencyMhz: Int = 2412,           // Operating frequency in MHz
    val protocol: String = "Wi-Fi 802.11",  // Protocol e.g. "Wi-Fi 6", "BLE 5.2"
    val threatLevel: String = "INFORMATIONAL", // Threat / Intel level e.g. "SURVEILLANCE OPTIC", "MOBILE TELEMETRY"
    val details: String = "",               // Descriptive intel notes
    val timestamp: Long = System.currentTimeMillis(),
    val detectedLat: Double? = null,
    val detectedLon: Double? = null,
    val relativeBearingDeg: Float = 0f,     // Bearing relative to vehicle heading (0° = ahead)
    val locationHistory: List<DeviceBreadcrumb> = emptyList(), // History trail of observations relative to host
    val motionTrend: RelativeMotionTrend = RelativeMotionTrend.STATIONARY,
    val savedMetadata: SavedRfDevice? = null, // Saved notes, number plate, door number, group
    val detectionTech: DetectionTechnology = DetectionTechnology.WIFI_BEACON // Technology utilized for detection
) {
    /**
     * Display name taking into account custom user nickname or number plate if saved
     */
    val displayName: String
        get() = when {
            savedMetadata?.customNickname?.isNotBlank() == true -> savedMetadata.customNickname
            savedMetadata?.numberPlate?.isNotBlank() == true -> "[${savedMetadata.numberPlate}] $name"
            else -> name
        }

    /**
     * Normalized signal strength percentage (0% to 100%)
     */
    val signalPercent: Int
        get() = when {
            rssi >= -50 -> 100
            rssi <= -95 -> 5
            else -> (((rssi + 95) / 45.0) * 100).toInt().coerceIn(5, 100)
        }

    /**
     * Tactical signal bars (1 to 4)
     */
    val signalBars: Int
        get() = when {
            rssi >= -60 -> 4
            rssi >= -72 -> 3
            rssi >= -84 -> 2
            else -> 1
        }

    /**
     * Proximity classification for tactical RF distance estimation.
     */
    val proximityZone: String
        get() = when {
            estimatedDistanceMeters < 5.0 -> "IMMEDIATE (<5m)"
            estimatedDistanceMeters < 15.0 -> "NEAR (5-15m)"
            estimatedDistanceMeters < 40.0 -> "MEDIUM (15-40m)"
            else -> "PERIMETER (>40m)"
        }

    /**
     * Determines whether the MAC address is IEEE locally administered / randomized.
     * (Bit 1 of first byte set indicates private/randomized MAC).
     */
    val isRandomizedMac: Boolean
        get() {
            val clean = macAddress.replace(":", "").replace("-", "")
            if (clean.length < 2) return false
            val firstByte = clean.take(2).toIntOrNull(16) ?: return false
            return (firstByte and 0x02) != 0
        }

    /**
     * Whether this target represents stationary physical infrastructure.
     */
    val isStationary: Boolean
        get() = category == RfCategory.ACCESS_POINT || category == RfCategory.CCTV_SURVEILLANCE || category == RfCategory.CELL_TOWER
}
