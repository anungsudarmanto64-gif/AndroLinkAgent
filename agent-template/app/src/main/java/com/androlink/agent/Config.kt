package com.androlink.agent

import android.content.Context
import org.json.JSONObject

object Config {

    var BASE_URL: String =
        "https://androlink.xo.je/api/"
        private set

    var PAIR_ENDPOINT: String =
        BASE_URL + "pair_device.php"
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

    var JOB_ID: String = ""
        private set

    @Volatile
    private var loaded = false

    fun load(context: Context) {

        if (loaded) return

        synchronized(this) {

            if (loaded) return

            try {

                val text = context.assets
                    .open("androlink-agent.json")
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(text)

                BASE_URL =
                    json.optString(
                        "api_base",
                        BASE_URL
                    )
                        .trim()
                        .trimEnd('/') + "/"

                PAIR_ENDPOINT =
                    BASE_URL +
                    json.optString(
                        "pair_endpoint",
                        "pair_device.php"
                    )
                        .trim()
                        .trimStart('/')

                HEARTBEAT_ENDPOINT =
                    BASE_URL +
                    json.optString(
                        "heartbeat_endpoint",
                        "heartbeat.php"
                    )
                        .trim()
                        .trimStart('/')

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
                    )
                        .trim()
                        .uppercase()

                MODE =
                    json.optString(
                        "mode",
                        "temporary"
                    ).trim()

                JOB_ID =
                    json.optString(
                        "job_id",
                        ""
                    ).trim()

            } catch (e: Exception) {

                DEVICE_ID = ""
                DEVICE_TOKEN = ""
                ENROLLMENT_CODE = ""

            }

            loaded = true
        }
    }

    fun isEnrolled(): Boolean {

        return DEVICE_ID.isNotBlank() &&
                DEVICE_TOKEN.isNotBlank() &&
                ENROLLMENT_CODE.isNotBlank()
    }
}
