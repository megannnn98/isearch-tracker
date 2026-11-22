package dev.isearch.tracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object Permissions {
    /** Permissions needed for scan + connect on the current Android version. */
    fun bluetooth(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Bluetooth permissions plus notifications on Android 13+. */
    fun all(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        bluetooth() + Manifest.permission.POST_NOTIFICATIONS
    } else {
        bluetooth()
    }

    fun hasBluetooth(context: Context): Boolean = bluetooth().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}
