package dev.isearch.tracker

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    var mac: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) = prefs.edit().putString(KEY_MAC, value).apply()

    /** Whether a double press on the fob should ring the phone. */
    var findPhoneEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIND_PHONE, true)
        set(value) = prefs.edit().putBoolean(KEY_FIND_PHONE, value).apply()

    /** The user wants the background service running (it is started again after a reboot). */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE, value).apply()

    private companion object {
        const val KEY_MAC = "mac"
        const val KEY_FIND_PHONE = "find_phone"
        const val KEY_SERVICE = "service_enabled"
    }
}
