package com.androlink.agent

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ApiResult(
    val httpCode: Int,
    val body: String,
    val json: JSONObject?
) {
    val isHttpSuccess: Boolean
        get() = httpCode in 200..299

    val success: Boolean
        get() = json?.optBoolean("success", false) == true

    val message: String
        get() {
            val serverMessage = json?.optString("message", "")?.trim()

            if (!serverMessage.isNullOrEmpty()) {
                return serverMessage
            }

            return when {
                httpCode == 400 -> "Request ditolak server (HTTP 400)"
                httpCode == 401 -> "Tidak diizinkan (HTTP 401)"
                httpCode == 403 -> "Akses ditolak (HTTP 403)"
                httpCode == 404 -> "Endpoint tidak ditemukan (HTTP 404)"
                httpCode in 500..599 -> "Server mengalami kesalahan (HTTP $httpCode)"
                else -> "HTTP $httpCode"
            }
        }
}

object Api {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 20_000

    fun post(
        url: String,
        fields: Map<String, String>
    ): ApiResult {

        var connection: HttpURLConnection? = null

        return try {

            connection = URL(url).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
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
                "User-Agent",
                "AndroLinkAgent/1.0"
            )

            val body = fields.entries.joinToString("&") { entry ->
                val key = URLEncoder.encode(
                    entry.key,
                    "UTF-8"
                )

                val value = URLEncoder.encode(
                    entry.value,
                    "UTF-8"
                )

                "$key=$value"
            }

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: ""

            val json = parseJson(responseText)

            ApiResult(
                httpCode = code,
                body = responseText,
                json = json
            )

        } catch (e: Exception) {

            ApiResult(
                httpCode = -1,
                body = e.message ?: e.javaClass.simpleName,
                json = null
            )

        } finally {
            connection?.disconnect()
        }
    }

    fun pair(
        deviceId: String,
        deviceToken: String
    ): ApiResult {

        return post(
            Config.PAIR_ENDPOINT,
            mapOf(
                "device_id" to deviceId,
                "device_token" to deviceToken
            )
        )
    }

    fun heartbeat(
        deviceId: String,
        deviceToken: String,
        sessionToken: String?
    ): ApiResult {

        val fields = mutableMapOf(
            "device_id" to deviceId,
            "device_token" to deviceToken
        )

        if (!sessionToken.isNullOrBlank()) {
            fields["session_token"] = sessionToken
        }

        return post(
            Config.HEARTBEAT_ENDPOINT,
            fields
        )
    }

    private fun parseJson(text: String): JSONObject? {

        val cleaned = text.trim()

        if (cleaned.isEmpty()) {
            return null
        }

        return try {
            JSONObject(cleaned)
        } catch (_: Exception) {

            /*
             * Kadang server hosting menghasilkan whitespace/BOM.
             * Coba bersihkan BOM sebelum parsing.
             */
            try {
                JSONObject(
                    cleaned
                        .removePrefix("\uFEFF")
                        .trim()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
