package net.djeebus.ssseats

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private var mAdapter: BluetoothAdapter? = null
    private var mDevice: BluetoothDevice? = null
    private var mTool: Scantool? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.content_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val mAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mAdapter == null) {
            alert("failed to find the default adapter")
            return
        }

        configureDeviceList()

        findViewById<Button>(R.id.connect).setOnClickListener {
            connect()
        }

        findViewById<Button>(R.id.disconnect).setOnClickListener {
            disconnect()
        }

        findViewById<Button>(R.id.drivers_heat).setOnClickListener {
            sendButtonPush(Mode.DriverHeat)
        }

        findViewById<Button>(R.id.drivers_cool).setOnClickListener {
            sendButtonPush(Mode.DriverCool)
        }

        findViewById<Button>(R.id.passenger_heat).setOnClickListener {
            sendButtonPush(Mode.PassengerHeat)
        }

        findViewById<Button>(R.id.passenger_cool).setOnClickListener {
            sendButtonPush(Mode.PassengerCool)
        }
    }

    private fun alert(message: String, e: Throwable? = null) {
        var msg = message

        Log.e(TAG, msg, e)

        if (e != null) {
            msg = "$msg: $e"
        }

        val view = findViewById<View>(R.id.content_main)
        Snackbar.make(view, msg, 10 * 1000)
    }

    private fun configureDeviceList() {
        val view = findViewById<ListView>(R.id.device_list)

        val list = ArrayList<BluetoothDevice>()

        if (mAdapter == null) {
            alert("must have an adapter")
            return
        }

        val adapter = mAdapter!!

        for (dev in adapter.bondedDevices) {
            if (dev.bondState == BluetoothDevice.BOND_BONDED) {
                list.add(dev)
            }
        }

        val arrayAdapter = ArrayAdapter<BluetoothDevice>(applicationContext, R.layout.device_item_template)
        view.adapter = arrayAdapter
        view.setOnItemClickListener { parent, _, position, _ -> onDeviceSelected(parent.getItemAtPosition(position) as BluetoothDevice) }
    }

    private fun onDeviceSelected(device: BluetoothDevice) {
        this.mDevice = device
    }

    private fun connect() {
        if (this.mDevice == null) {
            alert("you must select a device first")
        }

        val tool: Scantool

        try {
            tool = Scantool(this.mDevice!!)
            tool.connect()
        } catch (e: IOException) {
            alert("failed to create rfcomm socket: $e")
            return
        }

        mTool = tool
    }

    private fun disconnect() {
        mTool?.close()
    }

    val buttons = mapOf<Mode, String>(
        Mode.DriverHeat to "01",
        Mode.DriverCool to "04",
        Mode.PassengerHeat to "08",
        Mode.PassengerCool to "20"
    )

    private fun sendButtonPush(mode: Mode) {
        if (!buttons.containsKey(mode)) {
            Log.w(TAG, "failed to find mode: $mode")
            return
        }

        val pushCmd = buttons.get(mode)!!

        val tool = mTool!!
        tool.sendCommand("ATR 0")    // turn off responses
        tool.sendCommand("ATAL")     // allow messages > 8 bytes

        // send magic wake up packet (?)
        tool.sendCommand("STP 61")   // set protocol to gmlan, 11-bit
        tool.sendCommand("STCSWM 2")  // sw-can mode 3 (wake up)
        tool.sendCommand("ATSH 621")  // set header, magic wake up source?
        tool.sendCommand("01 FF FF FF FF 00 00 00")  // magic wake up packet?

        // prepare for actual commands
        tool.sendCommand("STCSWM 3")   // set protocol to gmlan, 29-bit
        tool.sendCommand("STP 62")     // maybe not necessary? seems weird
        tool.sendCommand("ATCP 00")  // set priority
        tool.sendCommand("ATSH 72 40 66")  // set header

        tool.sendCommand(pushCmd) // button down
        Thread.sleep(500) // sleep between buttons, seems necessary
        tool.sendCommand("00") // button up
    }
}
