package com.androlink.agent

import android.content.Context

class Storage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "androlink_agent",
        Context.MODE_PRIVATE
    )

    var deviceToken: String?
        get() = prefs.getString("device_token", null)
        set(value) {
            prefs.edit().putString("device_token", value).apply()
        }

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(value) {
            prefs.edit().putString("device_id", value).apply()
        }

    var pairingCode: String?
        get() = prefs.getString("pairing_code", null)
        set(value) {
            prefs.edit().putString("pairing_code", value).apply()
        }

    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(value) {
            prefs.edit().putBoolean("connected", value).apply()
        }

    fun clearConnection() {
        prefs.edit()
            .remove("device_token")
            .remove("device_id")
            .remove("pairing_code")
            .putBoolean("connected", false)
            .apply()
    }
}
