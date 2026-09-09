package com.androlink.agent

import android.content.Context

class Storage(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "androlink_agent",
            Context.MODE_PRIVATE
        )

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(value) {
            prefs.edit()
                .putString("device_id", value)
                .apply()
        }

    var deviceToken: String?
        get() = prefs.getString("device_token", null)
        set(value) {
            prefs.edit()
                .putString("device_token", value)
                .apply()
        }

    var enrollmentCode: String?
        get() = prefs.getString("enrollment_code", null)
        set(value) {
            prefs.edit()
                .putString("enrollment_code", value)
                .apply()
        }

    var sessionToken: String?
        get() = prefs.getString("session_token", null)
        set(value) {
            prefs.edit()
                .putString("session_token", value)
                .apply()
        }

    var pcToken: String?
        get() = prefs.getString("pc_token", null)
        set(value) {
            prefs.edit()
                .putString("pc_token", value)
                .apply()
        }

    var mode: String
        get() =
            prefs.getString(
                "mode",
                "temporary"
            ) ?: "temporary"

        set(value) {
            prefs.edit()
                .putString("mode", value)
                .apply()
        }

    /*
     * Status Agent ditentukan SERVER.
     *
     * false = Agent MATI
     * true  = Agent AKTIF
     *
     * Default HARUS false.
     */
    var agentEnabled: Boolean
        get() =
            prefs.getBoolean(
                "agent_enabled",
                false
            )

        set(value) {
            prefs.edit()
                .putBoolean(
                    "agent_enabled",
                    value
                )
                .apply()
        }

    fun clearSession() {

        prefs.edit()
            .remove("session_token")
            .remove("pc_token")
            .remove("agent_enabled")
            .apply()
    }

    fun clearAll() {

        prefs.edit()
            .clear()
            .apply()
    }
}
