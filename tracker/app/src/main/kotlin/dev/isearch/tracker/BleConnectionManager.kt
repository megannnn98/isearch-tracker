package dev.isearch.tracker

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.isearch.tracker.core.BleProtocol
import dev.isearch.tracker.core.ConnectionSupervisor

/**
 * Owns the GATT connection to one fob. Retry/timeout decisions live in [ConnectionSupervisor];
 * this class only translates between it and the Android BLE API. Main-thread only; GATT callbacks
 * are re-posted to the main looper.
 */
@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val context: Context,
    private val mac: String,
    private val listener: Listener,
) : ConnectionSupervisor.Driver {

    interface Listener {
        fun onStateChanged(state: ConnectionSupervisor.State)
        fun onButtonPress()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val supervisor = ConnectionSupervisor(this)
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var alertCharacteristic: BluetoothGattCharacteristic? = null
    private var scheduled: Runnable? = null

    fun start() {
        supervisor.start()
        if (adapter?.isEnabled != true) {
            supervisor.onBluetoothOff()
        }
    }

    fun currentState(): ConnectionSupervisor.State = supervisor.state

    fun stop() = supervisor.stop()
    fun onBluetoothOff() = supervisor.onBluetoothOff()
    fun onBluetoothOn() = supervisor.onBluetoothOn()

    /** Rings (or silences) the fob itself via Alert Level 2A06. Returns false if not possible now. */
    fun sendAlert(on: Boolean): Boolean {
        val g = gatt ?: return false
        val c = alertCharacteristic ?: return false
        val value = if (on) BleProtocol.ALERT_ON else BleProtocol.ALERT_OFF
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, value, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = type
                    c.value = value
                    g.writeCharacteristic(c)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "alert write denied", e)
            false
        }
    }

    // --- ConnectionSupervisor.Driver ---

    override fun openGatt() {
        try {
            val device = adapter?.getRemoteDevice(mac) ?: throw IllegalStateException("no adapter")
            // EXPERIMENT: autoConnect=false (autoConnect=true stayed in Connecting for 25+ s on POCO).
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            Log.i(TAG, "connectGatt $mac")
        } catch (e: RuntimeException) {
            Log.e(TAG, "connectGatt failed", e)
            handler.post { supervisor.onDisconnected() }
        }
    }

    override fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        alertCharacteristic = null
        try {
            g.disconnect()
        } catch (e: RuntimeException) {
            Log.w(TAG, "disconnect failed", e)
        }
        g.close()
    }

    override fun discoverServices() {
        if (gatt?.discoverServices() != true) {
            Log.e(TAG, "discoverServices rejected")
            handler.post { supervisor.onServicesReady(false) }
        }
    }

    override fun schedule(delayMs: Long, task: Runnable) {
        cancelScheduled()
        scheduled = task
        handler.postDelayed(task, delayMs)
    }

    override fun cancelScheduled() {
        scheduled?.let { handler.removeCallbacks(it) }
        scheduled = null
    }

    override fun onStateChanged(state: ConnectionSupervisor.State) {
        Log.i(TAG, "state=$state")
        listener.onStateChanged(state)
    }

    // --- GATT ---

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "connectionState status=$status newState=$newState")
            handler.post {
                if (g !== gatt) {
                    return@post
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> supervisor.onConnected()
                    BluetoothProfile.STATE_DISCONNECTED -> supervisor.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                if (g !== gatt) {
                    return@post
                }
                val ok = status == BluetoothGatt.GATT_SUCCESS && setUpCharacteristics(g)
                supervisor.onServicesReady(ok)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handleNotification(g, characteristic, value)

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleNotification(g, characteristic, characteristic.value)
            }
        }
    }

    private fun setUpCharacteristics(g: BluetoothGatt): Boolean {
        for (service in g.services) {
            for (c in service.characteristics) {
                Log.i(
                    TAG,
                    "service=${service.uuid} char=${c.uuid} props=${c.properties} " +
                        "descriptors=${c.descriptors.map { it.uuid }}",
                )
            }
        }
        val button = g.services.firstNotNullOfOrNull { it.getCharacteristic(BleProtocol.BUTTON_CHARACTERISTIC) }
        if (button == null) {
            Log.e(TAG, "button characteristic FFE1 not found")
            return false
        }
        g.setCharacteristicNotification(button, true)
        val cccd = button.getDescriptor(BleProtocol.CCCD)
        if (cccd == null) {
            Log.w(TAG, "FFE1 has no CCCD; relying on the fob to notify unprompted")
        } else {
            val value = if (button.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            writeDescriptor(g, cccd, value)
        }
        alertCharacteristic = g.getService(BleProtocol.IMMEDIATE_ALERT_SERVICE)
            ?.getCharacteristic(BleProtocol.ALERT_LEVEL_CHARACTERISTIC)
        return true
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray) {
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                d.value = value
                g.writeDescriptor(d)
            }
        }
        Log.i(TAG, "CCCD write started=$started")
    }

    private fun handleNotification(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray?) {
        if (c.uuid != BleProtocol.BUTTON_CHARACTERISTIC) {
            return
        }
        val message = BleProtocol.parseButtonNotification(value)
        Log.i(TAG, "notify FFE1 ${value?.joinToString(" ") { "%02x".format(it) }} -> $message")
        if (message is BleProtocol.Message.ButtonPress) {
            handler.post {
                if (g === gatt) {
                    listener.onButtonPress()
                }
            }
        }
    }

    private companion object {
        const val TAG = "TrackerBle"
    }
}
