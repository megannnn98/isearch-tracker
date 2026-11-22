package dev.isearch.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the service after reboot or app update if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val prefs = Prefs(context)
        if (!prefs.serviceEnabled || prefs.mac == null || !Permissions.hasBluetooth(context)) {
            Log.i(TAG, "not starting: enabled=${prefs.serviceEnabled} mac=${prefs.mac != null}")
            return
        }
        try {
            TrackerService.start(context)
        } catch (e: RuntimeException) {
            Log.e(TAG, "cannot start service from ${intent.action}", e)
        }
    }

    private companion object {
        const val TAG = "TrackerBoot"
    }
}
