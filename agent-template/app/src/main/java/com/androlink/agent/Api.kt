package com.androlink.agent

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    /*
     * =========================================================
     * URL ENCODING
     * =========================================================
     */
    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    /*
     * =========================================================
     * POST REQUEST
     * =========================================================
     */
    private fun post(
        endpoint: String,
        params: Map<String, String>
    ): String {

        var connection:
            HttpURLConnection? = null

        try {

            /*
             * Endpoint.
             */
            val url =
                URL(endpoint)

            /*
             * Form-urlencoded body.
             */
            val body =
                params.entries.joinToString("&") {

                    "${encode(it.key)}=${encode(it.value)}"
                }

            val bodyBytes =
                body.toByteArray(
                    Charsets.UTF_8
                )

            /*
             * Connection.
             */
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

            /*
             * Header.
             */
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

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )

            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )

            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            /*
             * =====================================================
             * SEND POST
             * =====================================================
             */
            connection.outputStream.use { output ->

                output.write(
                    bodyBytes
                )

                output.flush()
            }

            /*
             * =====================================================
             * HTTP RESPONSE
             * =====================================================
             */
            val responseCode =
                connection.responseCode

            /*
             * Ambil stream sesuai status HTTP.
             */
            val inputStream =
                if (
                    responseCode in 200..399
                ) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }

            /*
             * Tidak ada response.
             */
            if (
                inputStream == null
            ) {

                return errorJson(
                    message =
                        "Server tidak mengirim response.",
                    httpCode =
                        responseCode
                )
            }

            /*
             * =====================================================
             * READ RESPONSE
             * =====================================================
             */
            val response =
                StringBuilder()

            BufferedReader(
                InputStreamReader(
                    inputStream,
                    Charsets.UTF_8
                )
            ).use { reader ->

                while (true) {

                    val line =
                        reader.readLine()
                            ?: break

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

            /*
             * =====================================================
             * RESPONSE KOSONG
             * =====================================================
             */
            if (
                result.isEmpty()
            ) {

                return errorJson(
                    message =
                        "Response server kosong.",
                    httpCode =
                        responseCode
                )
            }

            /*
             * =====================================================
             * HTML / CLOUDFLARE / INFINITYFREE
             * =====================================================
             */
            if (
                isHtml(result)
            ) {

                val title =
                    extractTitle(
                        result
                    )

                return errorJson(
                    message =
                        "Server mengembalikan HTML/challenge. " +
                        "HTTP $responseCode. " +
                        "Title: $title. " +
                        "Preview: ${preview(result)}",
                    httpCode =
                        responseCode
                )
            }

            /*
             * =====================================================
             * VALIDASI RESPONSE JSON
             * =====================================================
             *
             * Jangan mengembalikan response mentah kalau
             * response bukan JSON.
             */
            if (
                !looksLikeJson(result)
            ) {

                return errorJson(
                    message =
                        "Server mengembalikan data bukan JSON. " +
                        "HTTP $responseCode. " +
                        "Preview: ${preview(result)}",
                    httpCode =
                        responseCode
                )
            }

            /*
             * =====================================================
             * JSON CLEANUP
             * =====================================================
             *
             * Hilangkan BOM UTF-8 jika ada.
             */
            val cleanResult =
                result
                    .removePrefix("\uFEFF")
                    .trim()

            /*
             * Pastikan benar-benar JSON object.
             */
            if (
                !cleanResult.startsWith("{") ||
                !cleanResult.endsWith("}")
            ) {

                return errorJson(
                    message =
                        "Format JSON server tidak valid. " +
                        "HTTP $responseCode. " +
                        "Preview: ${preview(cleanResult)}",
                    httpCode =
                        responseCode
                )
            }

            /*
             * Response JSON valid secara bentuk.
             */
            return cleanResult

        } catch (e: Exception) {

            /*
             * =====================================================
             * NETWORK ERROR
             * =====================================================
             */
            return errorJson(
                message =
                    "Gagal menghubungi server: " +
                    (
                        e.message
                            ?: e.javaClass.simpleName
                    ),
                httpCode =
                    0
            )

        } finally {

            connection?.disconnect()
        }
    }

    /*
     * =========================================================
     * PAIR DEVICE
     * =========================================================
     */
    fun pair(
        deviceId: String,
        deviceToken: String,
        enrollmentCode: String,
        deviceName: String
    ): String {

        return post(
            endpoint =
                Config.PAIR_ENDPOINT,

            params =
                mapOf(
                    "device_id" to deviceId,
                    "device_token" to deviceToken,
                    "enrollment_code" to enrollmentCode,
                    "device_name" to deviceName
                )
        )
    }

    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */
    fun heartbeat(
        deviceId: String,
        deviceToken: String,
        sessionToken: String
    ): String {

        return post(
            endpoint =
                Config.HEARTBEAT_ENDPOINT,

            params =
                mapOf(
                    "device_id" to deviceId,
                    "device_token" to deviceToken,
                    "session_token" to sessionToken
                )
        )
    }

    /*
     * =========================================================
     * CHECK HTML
     * =========================================================
     */
    private fun isHtml(
        value: String
    ): Boolean {

        return value.contains(
            "<html",
            ignoreCase = true
        ) ||
        value.contains(
            "<!doctype",
            ignoreCase = true
        ) ||
        value.contains(
            "<head",
            ignoreCase = true
        ) ||
        value.contains(
            "<body",
            ignoreCase = true
        )
    }

    /*
     * =========================================================
     * CHECK JSON
     * =========================================================
     */
    private fun looksLikeJson(
        value: String
    ): Boolean {

        val text =
            value
                .removePrefix("\uFEFF")
                .trim()

        return (
            text.startsWith("{") &&
            text.endsWith("}")
        ) ||
        (
            text.startsWith("[") &&
            text.endsWith("]")
        )
    }

    /*
     * =========================================================
     * RESPONSE PREVIEW
     * =========================================================
     */
    private fun preview(
        value: String
    ): String {

        return value
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .take(300)
    }

    /*
     * =========================================================
     * EXTRACT HTML TITLE
     * =========================================================
     */
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

        if (
            match != null
        ) {

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

    /*
     * =========================================================
     * ERROR JSON
     * =========================================================
     */
    private fun errorJson(
        message: String,
        httpCode: Int
    ): String {

        return """
        {
          "success": false,
          "message": ${jsonEscape(message)},
          "http_code": $httpCode
        }
        """.trimIndent()
    }

    /*
     * =========================================================
     * JSON ESCAPE
     * =========================================================
     */
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
