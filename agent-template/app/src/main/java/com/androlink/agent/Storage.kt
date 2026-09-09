package com.androlink.agent

import android.content.Context

class Storage(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "androlink_agent",
            Context.MODE_PRIVATE
        )

    var deviceToken: String?
        get() =
            prefs.getString(
                "device_token",
                null
            )
        set(value) {
            prefs.edit()
                .putString(
                    "device_token",
                    value
                )
                .apply()
        }

    var sessionToken: String?
        get() =
            prefs.getString(
                "session_token",
                null
            )
        set(value) {
            prefs.edit()
                .putString(
                    "session_token",
                    value
                )
                .apply()
        }

    var pcToken: String?
        get() =
            prefs.getString(
                "pc_token",
                null
            )
        set(value) {
            prefs.edit()
                .putString(
                    "pc_token",
                    value
                )
                .apply()
        }

    var mode: String?
        get() =
            prefs.getString(
                "mode",
                null
            )
        set(value) {
            prefs.edit()
                .putString(
                    "mode",
                    value
                )
                .apply()
        }

    fun clear() {

        prefs.edit()
            .remove("device_token")
            .remove("session_token")
            .remove("pc_token")
            .remove("mode")
            .apply()
    }
}
