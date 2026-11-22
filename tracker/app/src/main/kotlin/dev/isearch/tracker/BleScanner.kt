package dev.isearch.tracker

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dev.isearch.tracker.core.BleProtocol

/** Short foreground scan used only to pick the fob. Filter: service FFE0 (as in the original app). */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null

    fun start(onFound: (device: BluetoothDevice, name: String?, rssi: Int) -> Unit): Boolean {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        stop()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name
                onFound(result.device, name, result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "scan failed: $errorCode")
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProtocol.SERVICE_SCAN_FILTER)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return try {
            scanner.startScan(listOf(filter), settings, cb)
            callback = cb
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "scan denied", e)
            false
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopScan failed", e)
        }
    }

    private companion object {
        const val TAG = "TrackerScan"
    }
}
