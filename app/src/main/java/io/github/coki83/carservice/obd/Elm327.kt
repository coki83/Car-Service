package io.github.coki83.carservice.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Minimal client for an ELM327-compatible Bluetooth Classic (SPP) OBD-II adapter.
 * Blocking calls: run off the main thread.
 */
class Elm327(private val address: String) {

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission") // caller checks BLUETOOTH_CONNECT
    @Throws(IOException::class)
    fun connect(adapter: BluetoothAdapter) {
        val device = adapter.getRemoteDevice(address)
        adapter.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            // Many cheap clones only accept the insecure variant.
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        }
        socket = s
        input = s.inputStream
        output = s.outputStream

        command("ATZ", timeoutMs = 3000)   // reset
        command("ATE0")                      // echo off
        command("ATL0")                      // linefeeds off
        command("ATS0")                      // spaces off
        command("ATH0")                      // headers off
        command("ATSP0")                     // auto protocol
        // First real query lets the adapter find the protocol; can take a few seconds.
        command("0100", timeoutMs = 10_000)
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    val isConnected: Boolean get() = socket?.isConnected == true

    /** Vehicle speed in km/h (PID 0D), or null if not available. */
    fun speedKmh(): Int? = query("010D", "410D", 1)?.toInt()

    /**
     * Odometer in km (PID A6). Only some cars (mostly 2019+) report this.
     * Returns null when unsupported.
     */
    fun odometerKm(): Double? = query("01A6", "41A6", 4)?.let { it / 10.0 }

    private fun query(cmd: String, prefix: String, bytes: Int): Long? {
        val resp = command(cmd)
        return parse(resp, prefix, bytes)
    }

    @Synchronized
    @Throws(IOException::class)
    fun command(cmd: String, timeoutMs: Long = 2000): String {
        val out = output ?: throw IOException("not connected")
        val inp = input ?: throw IOException("not connected")
        out.write("$cmd\r".toByteArray())
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) throw IOException("stream closed")
                if (c.toChar() == '>') break
                sb.append(c.toChar())
            } else {
                Thread.sleep(10)
            }
        }
        return sb.toString()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Pull [bytes] data bytes following [prefix] out of a raw ELM response. */
        fun parse(raw: String, prefix: String, bytes: Int): Long? {
            val clean = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
            val idx = clean.indexOf(prefix)
            if (idx < 0) return null
            val start = idx + prefix.length
            val end = start + bytes * 2
            if (clean.length < end) return null
            return clean.substring(start, end).toLongOrNull(16)
        }
    }
}
