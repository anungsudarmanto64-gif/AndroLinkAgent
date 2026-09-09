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
                URL(url)
                    .openConnection() as HttpURLConnection

            connection.requestMethod = "POST"

            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val body =
                fields.entries.joinToString("&") {

                    URLEncoder.encode(
                        it.key,
                        "UTF-8"
                    ) +
                    "=" +
                    URLEncoder.encode(
                        it.value,
                        "UTF-8"
                    )
                }

            connection.outputStream.use {
                it.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val httpCode =
                connection.responseCode

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
                    ).use {
                        it.readText()
                    }

                } else {
                    ""
                }

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
            enrollmentCode
                .isNotBlank()
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

        if (result.json != null) {

            result.json.put(
                "http_code",
                result.httpCode
            )

            if (
                result.body
                    .isNotBlank()
            ) {

                result.json.put(
                    "raw_response",
                    result.body
                )
            }

            return result.json
        }

        /*
         * Jangan lagi membuat:
         *
         * HTTP 200 - HTTP 200
         *
         * sebagai response palsu.
         */
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
                    result.body
                        .isNotBlank()
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
            !sessionToken
                .isNullOrBlank()
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

        if (result.json != null) {

            result.json.put(
                "http_code",
                result.httpCode
            )

            return result.json
        }

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
                    result.body
                        .isNotBlank()
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
