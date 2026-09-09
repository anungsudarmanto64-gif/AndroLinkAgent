package com.androlink.agent

import android.content.Context
import org.json.JSONObject

object Config {

    var BASE_URL: String =
        "https://androlink.xo.je/api/"
        private set

var PAIR_ENDPOINT: String =
    "https://androlink.xo.je/android_post_test.php"
        private set

    var HEARTBEAT_ENDPOINT: String =
        BASE_URL + "heartbeat.php"
        private set

    var DEVICE_ID: String = ""
        private set

    var DEVICE_TOKEN: String = ""
        private set

    var ENROLLMENT_CODE: String = ""
        private set

    var MODE: String = "temporary"
        private set

    @Volatile
    private var loaded = false

    fun load(context: Context) {

        if (loaded) return

        synchronized(this) {

            if (loaded) return

            try {

                val json =
                    context.assets
                        .open("androlink-agent.json")
                        .bufferedReader()
                        .use {
                            JSONObject(it.readText())
                        }

                BASE_URL =
                    json.optString(
                        "api_base",
                        BASE_URL
                    )
                        .trimEnd('/') + "/"

                PAIR_ENDPOINT =
                    BASE_URL +
                    json.optString(
                        "pair_endpoint",
                        "pair_device.php"
                    )

                HEARTBEAT_ENDPOINT =
                    BASE_URL +
                    json.optString(
                        "heartbeat_endpoint",
                        "heartbeat.php"
                    )

                DEVICE_ID =
                    json.optString(
                        "device_id",
                        ""
                    ).trim()

                DEVICE_TOKEN =
                    json.optString(
                        "device_token",
                        ""
                    ).trim()

                ENROLLMENT_CODE =
                    json.optString(
                        "enrollment_code",
                        ""
                    ).trim()
                        .uppercase()

                MODE =
                    json.optString(
                        "mode",
                        "temporary"
                    ).trim()

            } catch (_: Exception) {

                /*
                 * Gunakan default.
                 */
            }

            loaded = true
        }
    }
}
