package com.androlink.agent

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun post(
        endpoint: String,
        params: Map<String, String>
    ): String {

        /*
         * Config.PAIR_ENDPOINT dan Config.HEARTBEAT_ENDPOINT
         * sudah berisi URL lengkap.
         *
         * Contoh:
         * https://androlink.xo.je/api/pair_device.php
         */
        val url = URL(endpoint)

        val body = params.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }

        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null

        try {

            connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"

            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.doInput = true
            connection.doOutput = true

            connection.useCaches = false

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )

            connection.setRequestProperty(
                "Connection",
                "close"
            )

            connection.setRequestProperty(
                "User-Agent",
                "AndroLinkAgent/1.0 Android"
            )

            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            connection.outputStream.use { output ->
                output.write(bodyBytes)
                output.flush()
            }

            val responseCode =
                connection.responseCode

            val inputStream =
                if (responseCode in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            if (inputStream == null) {
                return """
                    {
                      "success": false,
                      "message": "Server tidak mengirim response.",
                      "http_code": $responseCode
                    }
                """.trimIndent()
            }

            val response = StringBuilder()

            BufferedReader(
                InputStreamReader(
                    inputStream,
                    Charsets.UTF_8
                )
            ).use { reader ->

                var line: String?

                while (reader.readLine().also {
                    line = it
                } != null) {

                    response.append(line)
                }
            }

            val result =
                response.toString().trim()

            /*
             * Jika server mengembalikan HTML,
             * berarti bukan response JSON API.
             */
            if (
                result.contains("<html", true) ||
                result.contains("<!doctype", true) ||
                result.contains("aes.js", true)
            ) {

                return """
                    {
                      "success": false,
                      "message": "Server mengembalikan halaman HTML/challenge, bukan JSON.",
                      "http_code": $responseCode
                    }
                """.trimIndent()
            }

            if (result.isEmpty()) {

                return """
                    {
                      "success": false,
                      "message": "Response server kosong.",
                      "http_code": $responseCode
                    }
                """.trimIndent()
            }

            /*
             * JSON dikembalikan apa adanya.
             *
             * Parsing dilakukan oleh:
             * MainActivity
             * HeartbeatService
             */
            return result

        } catch (e: Exception) {

            val message =
                e.message
                    ?: e.javaClass.simpleName

            return """
                {
                  "success": false,
                  "message": ${jsonEscape(message)}
                }
            """.trimIndent()

        } finally {

            connection?.disconnect()
        }
    }

    /*
    |--------------------------------------------------------------------------
    | PAIR DEVICE
    |--------------------------------------------------------------------------
    */

    fun pair(
        deviceId: String,
        deviceToken: String,
        enrollmentCode: String,
        deviceName: String
    ): String {

        return post(
            Config.PAIR_ENDPOINT,
            mapOf(
                "device_id" to deviceId,
                "device_token" to deviceToken,
                "enrollment_code" to enrollmentCode,
                "device_name" to deviceName
            )
        )
    }

    /*
    |--------------------------------------------------------------------------
    | HEARTBEAT
    |--------------------------------------------------------------------------
    */

    fun heartbeat(
        deviceId: String,
        deviceToken: String,
        sessionToken: String
    ): String {

        return post(
            Config.HEARTBEAT_ENDPOINT,
            mapOf(
                "device_id" to deviceId,
                "device_token" to deviceToken,
                "session_token" to sessionToken
            )
        )
    }

    /*
    |--------------------------------------------------------------------------
    | JSON STRING ESCAPE
    |--------------------------------------------------------------------------
    */

    private fun jsonEscape(value: String): String {

        val escaped =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")

        return "\"$escaped\""
    }
}
