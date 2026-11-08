package com.recondrive.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Interface abstraction for ELM327 OBD-II serial data streams.
 */
interface Elm327Connection {
    val isConnected: Boolean
    val connectionType: String

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendCommand(cmd: String): List<String>
}

/**
 * Bluetooth Classic SPP (RFCOMM) ELM327 connection.
 */
class BluetoothElm327Connection(
    private val device: BluetoothDevice
) : Elm327Connection {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @get:SuppressLint("MissingPermission")
    override val connectionType: String
        get() = "Bluetooth SPP (${device.name ?: device.address})"

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        Timber.i("Connecting to Bluetooth ELM327 on %s...", device.address)
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect()
        socket = s
        inputStream = s.inputStream
        outputStream = s.outputStream
        Timber.i("Bluetooth ELM327 RFCOMM socket established.")
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing Bluetooth socket")
            } finally {
                inputStream = null
                outputStream = null
                socket = null
            }
        }
    }

    override suspend fun sendCommand(cmd: String): List<String> = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IllegalStateException("ELM327 not connected")
        val input = inputStream ?: throw IllegalStateException("ELM327 not connected")

        val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
        out.write(payload)
        out.flush()

        val lines = mutableListOf<String>()
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        val byteBuf = ByteArray(256)

        while (System.currentTimeMillis() - startTime < 2500) {
            if (input.available() > 0) {
                val len = input.read(byteBuf)
                if (len > 0) {
                    val chunk = String(byteBuf, 0, len, Charsets.US_ASCII)
                    buffer.append(chunk)
                    if (buffer.contains('>')) {
                        break
                    }
                }
            } else {
                Thread.sleep(15)
            }
        }

        val rawStr = buffer.toString()
        for (line in rawStr.split('\r', '\n')) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && trimmed != ">" && !trimmed.equals(cmd.trim(), ignoreCase = true)) {
                lines.add(trimmed)
            }
        }
        lines
    }
}

/**
 * Wi-Fi TCP ELM327 connection (typically 192.168.0.10:35000).
 */
class WifiElm327Connection(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) : Elm327Connection {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override val connectionType: String
        get() = "Wi-Fi TCP ($host:$port)"

    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        Timber.i("Connecting to Wi-Fi ELM327 on %s:%d...", host, port)
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 4000)
        s.soTimeout = 2500
        socket = s
        inputStream = s.getInputStream()
        outputStream = s.getOutputStream()
        Timber.i("Wi-Fi ELM327 TCP connection established.")
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing Wi-Fi socket")
            } finally {
                inputStream = null
                outputStream = null
                socket = null
            }
        }
    }

    override suspend fun sendCommand(cmd: String): List<String> = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IllegalStateException("Wi-Fi ELM327 not connected")
        val input = inputStream ?: throw IllegalStateException("Wi-Fi ELM327 not connected")

        val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
        out.write(payload)
        out.flush()

        val lines = mutableListOf<String>()
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        val byteBuf = ByteArray(256)

        while (System.currentTimeMillis() - startTime < 2500) {
            if (input.available() > 0) {
                val len = input.read(byteBuf)
                if (len > 0) {
                    val chunk = String(byteBuf, 0, len, Charsets.US_ASCII)
                    buffer.append(chunk)
                    if (buffer.contains('>')) {
                        break
                    }
                }
            } else {
                Thread.sleep(15)
            }
        }

        val rawStr = buffer.toString()
        for (line in rawStr.split('\r', '\n')) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && trimmed != ">" && !trimmed.equals(cmd.trim(), ignoreCase = true)) {
                lines.add(trimmed)
            }
        }
        lines
    }
}

/**
 * High-fidelity C4 Picasso simulation connection.
 * Emulates the exact CAN responses of a 2010 Citroën Grand C4 Picasso 1.6 HDi EGS6
 * (Bosch EDC16C34, EGS6 CFC300P, Bosch ESP 8.1, FSE electric parking brake).
 */
class SimulatedPicassoConnection : Elm327Connection {

    override var isConnected: Boolean = false
        private set

    override val connectionType: String
        get() = "C4 Picasso Simulator (DV6 HDi + EGS6)"

    private var currentHeader: String = "7DF"
    private var codesCleared: Boolean = false
    private var simulatedRpm: Int = 850
    private var simulatedSpeedKmh: Int = 0
    private var simulatedRailBar: Double = 298.0
    private var simulatedBoostBar: Double = 1.02
    private var simulatedCoolantC: Double = 86.0
    private var simulatedBatteryV: Double = 14.24

    override suspend fun connect() {
        isConnected = true
        Timber.i("Citroën Grand C4 Picasso 1.6 HDi EGS6 Simulation active.")
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun sendCommand(cmd: String): List<String> = withContext(Dispatchers.Default) {
        val c = cmd.trim().uppercase()
        // Simulate realistic 30ms CAN latency
        kotlinx.coroutines.delay(35)

        when {
            c == "ATZ" -> listOf("ELM327 v1.5 (PSA-RE Picasso SIM)")
            c in listOf("ATE0", "ATL0", "ATS0", "ATH1", "ATH0") -> listOf("OK")
            c.startsWith("ATSP") -> listOf("OK")
            c.startsWith("ATSH") -> {
                val parts = c.split(" ")
                if (parts.size > 1) {
                    currentHeader = parts[1].trim()
                }
                listOf("OK")
            }
            c == "0100" -> listOf("7E8 06 41 00 BE 3F B8 13", "7E9 06 41 00 98 18 80 01")
            
            // Mode 03 (Confirmed DTCs)
            c == "03" -> {
                if (codesCleared) {
                    listOf("7E8 02 43 00 00 00 00 00", "7E9 02 43 00 00 00 00 00")
                } else {
                    when (currentHeader) {
                        "7DF" -> listOf(
                            "7E8 06 43 02 02 99 13 51 00", // P0299 Turbo, P1351 Glow Plug Relay
                            "7E9 04 43 01 17 A5 00 00 00"  // P17A5 EGS6 1st gear coordination
                        )
                        "7E0" -> listOf("7E8 06 43 02 02 99 13 51 00")
                        "7E1", "7E2" -> listOf("7E9 04 43 01 17 A5 00 00 00")
                        "760" -> listOf("768 04 43 01 53 85 00 00 00") // C1385 ABS Solenoid Voltage
                        "792" -> listOf("79A 04 43 01 55 5A 00 00 00") // C155A EPB Cable Force
                        "752" -> listOf("7D2 02 43 00 00 00 00 00")
                        else -> listOf("NO DATA")
                    }
                }
            }

            // Mode 07 (Pending DTCs)
            c == "07" -> {
                if (codesCleared) {
                    listOf("7E8 02 47 00 00 00 00 00")
                } else {
                    listOf("7E8 04 47 01 02 99 00 00 00") // P0299 pending boost deviation
                }
            }

            // UDS 0x19 02 FF (Report DTC by status mask)
            c in listOf("1902FF", "19 02 FF") -> {
                if (codesCleared) {
                    listOf("59 02 FF 00")
                } else {
                    when (currentHeader) {
                        "7E1", "7E2" -> listOf("7E9 06 59 02 FF 17 A5 29")
                        "760" -> listOf("768 06 59 02 FF 53 85 1C")
                        else -> listOf("7E8 08 59 02 FF 02 99 21 13 51")
                    }
                }
            }

            // Mode 04 (Clear DTCs)
            c == "04" || c in listOf("14FFFFFF", "14 FF FF FF") -> {
                codesCleared = true
                listOf("44")
            }

            // Mode 01 Live PIDs
            c == "010C" -> { // RPM
                val rpmHex = "%04X".format(simulatedRpm * 4)
                listOf("7E8 04 41 0C ${rpmHex.substring(0, 2)} ${rpmHex.substring(2, 4)}")
            }
            c == "010D" -> { // Speed km/h
                val spdHex = "%02X".format(simulatedSpeedKmh)
                listOf("7E8 03 41 0D $spdHex")
            }
            c == "0105" -> { // Coolant Temp (°C + 40)
                val coolHex = "%02X".format((simulatedCoolantC + 40).toInt())
                listOf("7E8 03 41 05 $coolHex")
            }
            c == "010F" -> { // Intake Air Temp (°C + 40)
                listOf("7E8 03 41 0F 42") // ~26°C
            }
            c == "0110" -> { // MAF g/s (A*256+B)/100
                listOf("7E8 04 41 10 05 EE") // ~15.18 g/s
            }
            c == "0123" -> { // Fuel Rail Pressure (A*256+B)*10 kPa (1 bar = 100 kPa)
                val pVal = (simulatedRailBar * 10).toInt()
                val hexA = "%02X".format((pVal shr 8) and 0xFF)
                val hexB = "%02X".format(pVal and 0xFF)
                listOf("7E8 04 41 23 $hexA $hexB")
            }
            c == "010B" -> { // Intake Manifold Pressure / Boost kPa (1 bar = 100 kPa)
                val mapKpa = (simulatedBoostBar * 100).toInt()
                listOf("7E8 03 41 0B %02X".format(mapKpa))
            }
            c == "0142" -> { // Control Module Voltage ((A*256+B)/1000 V)
                val vVal = (simulatedBatteryV * 1000).toInt()
                val hexA = "%02X".format((vVal shr 8) and 0xFF)
                val hexB = "%02X".format(vVal and 0xFF)
                listOf("7E8 04 41 42 $hexA $hexB")
            }
            c == "012F" -> { // Fuel Tank Level (A * 100 / 255)
                listOf("7E8 03 41 2F B8") // ~72%
            }
            else -> listOf("OK")
        }
    }
}
