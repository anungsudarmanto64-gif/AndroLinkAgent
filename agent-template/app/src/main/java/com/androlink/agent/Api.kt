package com.androlink.agent

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    private fun readResponse(
        connection: HttpURLConnection
    ): String {

        val stream =
            if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        return stream
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            ?: ""
    }

    fun post(
        url: String,
        fields: Map<String, String>
    ): JSONObject {

        var connection: HttpURLConnection? = null

        try {

            connection =
                URL(url).openConnection()
                    as HttpURLConnection

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
                "User-Agent",
                "AndroLink-Agent/1.0"
            )

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

            connection.outputStream.use { output ->

                output.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )

                output.flush()
            }

            val httpCode =
                connection.responseCode

            val response =
                readResponse(connection)

            if (response.isBlank()) {

                return JSONObject().apply {

                    put(
                        "success",
                        false
                    )

                    put(
                        "message",
                        "Server tidak mengembalikan response."
                    )

                    put(
                        "http_code",
                        httpCode
                    )
                }
            }

            return try {

                JSONObject(response)

            } catch (e: Exception) {

                JSONObject().apply {

                    put(
                        "success",
                        false
                    )

                    put(
                        "message",
                        "Server mengembalikan response bukan JSON."
                    )

                    put(
                        "http_code",
                        httpCode
                    )

                    put(
                        "raw_response",
                        response.take(1000)
                    )
                }
            }

        } catch (e: Exception) {

            return JSONObject().apply {

                put(
                    "success",
                    false
                )

                put(
                    "message",
                    "Gagal koneksi: ${
                        e.message ?: "Network error"
                    }"
                )

                put(
                    "error",
                    "NETWORK_ERROR"
                )
            }

        } finally {

            connection?.disconnect()
        }
    }

    fun pair(
        id: String,
        token: String,
        enrollmentCode: String
    ): JSONObject {

        return post(
            Config.PAIR_ENDPOINT,
            mapOf(
                "device_id" to id,
                "device_token" to token,
                "enrollment_code" to enrollmentCode
            )
        )
    }

    fun heartbeat(
        id: String,
        token: String,
        session: String?
    ): JSONObject {

        val fields =
            mutableMapOf(
                "device_id" to id,
                "device_token" to token
            )

        if (!session.isNullOrBlank()) {

            fields["session_token"] =
                session
        }

        return post(
            Config.HEARTBEAT_ENDPOINT,
            fields
        )
    }
}
