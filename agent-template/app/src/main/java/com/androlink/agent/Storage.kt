package com.androlink.agent

import android.content.Context

class Storage(c: Context) {

    private val p = c.getSharedPreferences(
        "androlink_agent",
        Context.MODE_PRIVATE
    )

    var deviceToken: String?
        get() = p.getString("device_token", null)
        set(v) {
            p.edit()
                .putString("device_token", v)
                .apply()
        }

    var sessionToken: String?
        get() = p.getString("session_token", null)
        set(v) {
            p.edit()
                .putString("session_token", v)
                .apply()
        }

    var pcToken: String?
        get() = p.getString("pc_token", null)
        set(v) {
            p.edit()
                .putString("pc_token", v)
                .apply()
        }

    var mode: String?
        get() = p.getString("mode", null)
        set(v) {
            p.edit()
                .putString("mode", v)
                .apply()
        }

    fun clear() {
        p.edit()
            .remove("session_token")
            .remove("pc_token")
            .remove("mode")
            .apply()
    }
}
