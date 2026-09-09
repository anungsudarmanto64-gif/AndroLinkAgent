package com.androlink.agent

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    data class ApiResult(
        val httpCode: Int,
        val body: String,
        val json: JSONObject?
    )

    private fun postRaw(
        url: String,
        fields: Map<String, String>
    ): ApiResult {

        var connection: HttpURLConnection? = null

        return try {

            connection =
                URL(url).openConnection() as HttpURLConnection

            // =====================================================
            // METHOD
            // =====================================================
            connection.requestMethod = "POST"

            // =====================================================
            // TIMEOUT
            // =====================================================
            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.doInput = true
            connection.doOutput = true

            // Jangan gunakan cache
            connection.useCaches = false

            // Tutup koneksi setelah request selesai
            connection.setRequestProperty(
                "Connection",
                "close"
            )

            // User-Agent normal agar request Android tidak dianggap
            // request aneh oleh server/WAF
            connection.setRequestProperty(
                "User-Agent",
                "AndroLinkAgent/1.0 Android"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            // =====================================================
            // BUILD POST BODY
            // =====================================================
            val body =
                fields.entries.joinToString("&") { entry ->

                    URLEncoder.encode(
                        entry.key,
                        "UTF-8"
                    ) +
                    "=" +
                    URLEncoder.encode(
                        entry.value,
                        "UTF-8"
                    )
                }

            val bodyBytes =
                body.toByteArray(Charsets.UTF_8)

            // Penting:
            // kirim ukuran body secara eksplisit
            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            // =====================================================
            // SEND POST
            // =====================================================
            connection.outputStream.use { output ->

                output.write(bodyBytes)
                output.flush()
            }

            // =====================================================
            // RESPONSE CODE
            // =====================================================
            val httpCode =
                connection.responseCode

            // =====================================================
            // RESPONSE STREAM
            // =====================================================
            val stream =
                if (httpCode in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                if (stream != null) {

                    BufferedReader(
                        InputStreamReader(
                            stream,
                            Charsets.UTF_8
                        )
                    ).use { reader ->
                        reader.readText()
                    }

                } else {
                    ""
                }

            // =====================================================
            // PARSE JSON
            // =====================================================
            val json =
                try {

                    if (
                        responseText
                            .trim()
                            .isNotEmpty()
                    ) {
                        JSONObject(
                            responseText
                        )
                    } else {
                        null
                    }

                } catch (_: Exception) {

                    null
                }

            ApiResult(
                httpCode = httpCode,
                body = responseText,
                json = json
            )

        } catch (e: Exception) {

            ApiResult(
                httpCode = -1,
                body =
                    e.message
                        ?: e.javaClass.simpleName,
                json = null
            )

        } finally {

            connection?.disconnect()
        }
    }

    // =============================================================
    // PAIR DEVICE
    // =============================================================

    fun pair(
        deviceId: String,
        deviceToken: String,
        enrollmentCode: String,
        deviceName: String
    ): JSONObject {

        val fields =
            linkedMapOf(
                "device_id" to deviceId,
                "device_token" to deviceToken,
                "device_name" to deviceName
            )

        if (
            enrollmentCode.isNotBlank()
        ) {

            fields[
                "enrollment_code"
            ] = enrollmentCode
        }

        val result =
            postRaw(
                Config.PAIR_ENDPOINT,
                fields
            )

        // =========================================================
        // JSON RESPONSE
        // =========================================================

        if (result.json != null) {

            result.json.put(
                "http_code",
                result.httpCode
            )

            result.json.put(
                "raw_response",
                result.body
            )

            return result.json
        }

        // =========================================================
        // NON JSON RESPONSE
        // =========================================================

        return JSONObject().apply {

            put(
                "success",
                false
            )

            put(
                "http_code",
                result.httpCode
            )

            put(
                "message",
                if (
                    result.body.isNotBlank()
                ) {
                    result.body
                } else {
                    "Server tidak mengirim response JSON."
                }
            )

            put(
                "raw_response",
                result.body
            )
        }
    }

    // =============================================================
    // HEARTBEAT
    // =============================================================

    fun heartbeat(
        deviceId: String,
        deviceToken: String,
        sessionToken: String?
    ): JSONObject {

        val fields =
            linkedMapOf(
                "device_id" to deviceId,
                "device_token" to deviceToken
            )

        if (
            !sessionToken.isNullOrBlank()
        ) {

            fields[
                "session_token"
            ] = sessionToken
        }

        val result =
            postRaw(
                Config.HEARTBEAT_ENDPOINT,
                fields
            )

        // =========================================================
        // JSON RESPONSE
        // =========================================================

        if (result.json != null) {

            result.json.put(
                "http_code",
                result.httpCode
            )

            result.json.put(
                "raw_response",
                result.body
            )

            return result.json
        }

        // =========================================================
        // NON JSON RESPONSE
        // =========================================================

        return JSONObject().apply {

            put(
                "success",
                false
            )

            put(
                "http_code",
                result.httpCode
            )

            put(
                "message",
                if (
                    result.body.isNotBlank()
                ) {
                    result.body
                } else {
                    "Server tidak mengirim response."
                }
            )

            put(
                "raw_response",
                result.body
            )
        }
    }
}
