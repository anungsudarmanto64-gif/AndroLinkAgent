package com.androlink.agent

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    private fun encode(
        value: String
    ): String {
        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    private fun post(
        endpoint: String,
        params: Map<String, String>
    ): String {

        val url = URL(endpoint)

        val body =
            params.entries.joinToString("&") {
                "${encode(it.key)}=${encode(it.value)}"
            }

        val bodyBytes =
            body.toByteArray(
                Charsets.UTF_8
            )

        var connection:
            HttpURLConnection? = null

        try {

            connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                15000

            connection.readTimeout =
                20000

            connection.doInput =
                true

            connection.doOutput =
                true

            connection.useCaches =
                false

            connection.instanceFollowRedirects =
                true

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
                "Pragma",
                "no-cache"
            )

            connection.setRequestProperty(
                "User-Agent",
                "AndroLinkAgent/1.0 Android"
            )

            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            connection.outputStream.use { output ->

                output.write(
                    bodyBytes
                )

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

                return errorJson(
                    "Server tidak mengirim response.",
                    responseCode
                )
            }

            val response =
                StringBuilder()

            BufferedReader(
                InputStreamReader(
                    inputStream,
                    Charsets.UTF_8
                )
            ).use { reader ->

                var line: String?

                while (
                    reader.readLine().also {
                        line = it
                    } != null
                ) {

                    response.append(
                        line
                    )

                    response.append(
                        '\n'
                    )
                }
            }

            val result =
                response
                    .toString()
                    .trim()

            if (result.isEmpty()) {

                return errorJson(
                    "Response server kosong.",
                    responseCode
                )
            }

            /*
             * Server seharusnya mengembalikan JSON.
             * Kalau ternyata HTML, tampilkan informasi
             * secukupnya supaya sumber masalah terlihat.
             */
            if (
                result.contains(
                    "<html",
                    ignoreCase = true
                ) ||
                result.contains(
                    "<!doctype",
                    ignoreCase = true
                ) ||
                result.contains(
                    "<head",
                    ignoreCase = true
                ) ||
                result.contains(
                    "<body",
                    ignoreCase = true
                )
            ) {

                val title =
                    extractTitle(result)

                return errorJson(
                    "Server mengembalikan HTML/challenge. " +
                    "HTTP $responseCode. " +
                    "Title: $title"
                )
            }

            return result

        } catch (e: Exception) {

            return errorJson(
                "Gagal menghubungi server: ${
                    e.message
                        ?: e.javaClass.simpleName
                }"
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

    private fun extractTitle(
        html: String
    ): String {

        val regex =
            Regex(
                "<title[^>]*>(.*?)</title>",
                setOf(
                    RegexOption.IGNORE_CASE,
                    RegexOption.DOT_MATCHES_ALL
                )
            )

        val match =
            regex.find(html)

        if (match != null) {

            return match
                .groupValues[1]
                .replace(
                    Regex("<[^>]+>"),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
                .take(150)
        }

        return "Tidak diketahui"
    }

    private fun errorJson(
        message: String,
        httpCode: Int = 0
    ): String {

        return """
        {
          "success": false,
          "message": ${jsonEscape(message)},
          "http_code": $httpCode
        }
        """.trimIndent()
    }

    private fun jsonEscape(
        value: String
    ): String {

        val escaped =
            value
                .replace(
                    "\\",
                    "\\\\"
                )
                .replace(
                    "\"",
                    "\\\""
                )
                .replace(
                    "\r",
                    "\\r"
                )
                .replace(
                    "\n",
                    "\\n"
                )
                .replace(
                    "\t",
                    "\\t"
                )

        return "\"$escaped\""
    }
}
