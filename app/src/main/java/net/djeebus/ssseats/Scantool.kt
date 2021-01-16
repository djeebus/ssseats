package net.djeebus.ssseats

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val MAX_PACKET_SIZE = 100
private const val NEWLINE = 0x0D
private const val INVALID_COMMAND = 0x3F // '?'
private const val TAG = "Scantool"

private val SerialUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!! //UUID for serial connection

class Scantool constructor(device: BluetoothDevice) {
    private val mDevice: BluetoothDevice = device

    private var mSocket: BluetoothSocket? = null
    private var mInputStream: InputStream? = null
    private var mOutputStream: OutputStream? = null

    fun connect() {
        val socket = mDevice.createRfcommSocketToServiceRecord(SerialUUID)!!

        socket.connect()

        mSocket = socket
        mInputStream = socket.inputStream
        mOutputStream = socket.outputStream

        setup()
    }

    fun close() {
        mInputStream?.close()
        mInputStream = null

        mOutputStream?.close()
        mOutputStream = null

        mSocket?.close()
        mSocket = null
    }

    private fun setup() {
        write("ATZ")   // reset the adapter
        readUntil(">")

        write("ATE0")
        readUntil(">")

        sendCommand("ATL0")  // lines end in 0x0D
        sendCommand("ATH")   // turn display of headers on (checksum, pci)
        sendCommand("ATS")   // turn on print spaces
        sendCommand("ATAL")  // allow long (>7 byte) messages
    }

    private val charset = Charsets.US_ASCII

    fun write(text: String) {
        Log.i(TAG, "--> $text")
        mOutputStream?.write(text.toByteArray(charset))
        mOutputStream?.write(NEWLINE)
    }

    fun sendCommand(cmd: String) {
        write(cmd)
        readUntil(">")
    }

    private fun readUntil(expected: String) {
        while (true) {
            val p = read()
            if (p == expected) {
                break
            }
        }
    }

    private fun read(): String {
        val inputStream = mInputStream!!

        val p = arrayListOf<Byte>()

        while (p.size < MAX_PACKET_SIZE) {
            val c = inputStream.read()
            if (c == NEWLINE) {
                if (p.size == 0) {
                    continue
                }


                val packet = String(p.toByteArray())
                Log.i(TAG, "<-- $packet")
                return packet
            }
            if (c == INVALID_COMMAND) {
                throw InvalidCommandException()
            }

            p.add(c.toByte())
        }

        throw PacketTooLongException()
    }
}

class InvalidCommandException : Exception()
class PacketTooLongException : Exception()
