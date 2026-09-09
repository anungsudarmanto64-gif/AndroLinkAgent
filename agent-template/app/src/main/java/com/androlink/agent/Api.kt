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

        val url =
            URL(endpoint)

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

            /*
             * POST
             */
            connection.requestMethod =
                "POST"

            /*
             * Timeout
             */
            connection.connectTimeout =
                15000

            connection.readTimeout =
                20000

            /*
             * I/O
             */
            connection.doInput =
                true

            connection.doOutput =
                true

            connection.useCaches =
                false

            /*
             * Ikuti redirect.
             */
            connection.instanceFollowRedirects =
                true

            /*
             * Request headers.
             */
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )

            connection.setRequestProperty(
                "Accept-Language",
                "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
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
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
            )

            /*
             * Jangan menggunakan:
             *
             * Connection: close
             *
             * karena beberapa server hosting
             */security layer dapat memperlakukan
             * request secara berbeda.
             */

            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            /*
             * Kirim POST body.
             */
            connection.outputStream.use { output ->

                output.write(
                    bodyBytes
                )

                output.flush()
            }

            /*
             * Ambil HTTP code.
             */
            val responseCode =
                connection.responseCode

            /*
             * Ambil URL akhir setelah redirect.
             */
            val finalUrl =
                connection.url
                    ?.toString()
                    ?: endpoint

            /*
             * Ambil content type.
             */
            val contentType =
                connection.contentType
                    ?: ""

            /*
             * Ambil response stream.
             */
            val inputStream =
                if (responseCode >= 400) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }

            if (inputStream == null) {

                return errorJson(
                    message =
                        "Server tidak mengirim response.",
                    httpCode =
                        responseCode,
                    extra =
                        "URL=$finalUrl"
                )
            }

            /*
             * Baca seluruh response.
             */
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
                    reader
                        .readLine()
                        .also {
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

            /*
             * Response kosong.
             */
            if (result.isEmpty()) {

                return errorJson(
                    message =
                        "Response server kosong.",
                    httpCode =
                        responseCode,
                    extra =
                        "Content-Type=$contentType"
                )
            }

            /*
             * ----------------------------------------------------------
             * CEK JSON
             * ----------------------------------------------------------
             *
             * Jangan hanya melihat <html>.
             * Kita coba parsing JSON terlebih dahulu.
             */
            if (looksLikeJson(result)) {

                return result
            }

            /*
             * ----------------------------------------------------------
             * RESPONSE HTML
             * ----------------------------------------------------------
             *
             * Di sinilah kita sekarang TIDAK membuang response asli.
             * Kita ambil informasi penting dari HTML.
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
                    extractHtmlTitle(
                        result
                    )

                val snippet =
                    cleanHtmlSnippet(
                        result
                    )

                return errorJson(
                    message =
                        "Server mengembalikan HTML/challenge.",
                    httpCode =
                        responseCode,
                    extra =
                        "URL=$finalUrl | " +
                        "Content-Type=$contentType | " +
                        "Title=$title | " +
                        "Response=$snippet"
                )
            }

            /*
             * Response bukan JSON dan bukan HTML.
             */
            return errorJson(
                message =
                    "Format response server tidak dikenal.",
                httpCode =
                    responseCode,
                extra =
                    "URL=$finalUrl | " +
                    "Content-Type=$contentType | " +
                    "Response=${limitText(result, 300)}"
            )

        } catch (e: Exception) {

            val message =
                e.message
                    ?: e.javaClass.simpleName

            return errorJson(
                message =
                    "Gagal menghubungi server.",
                httpCode =
                    0,
                extra =
                    message
            )

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
    | JSON DETECTION
    |--------------------------------------------------------------------------
    */

    private fun looksLikeJson(
        value: String
    ): Boolean {

        val text =
            value.trim()

        return (
            text.startsWith("{") &&
            text.endsWith("}")
        ) || (
            text.startsWith("[") &&
            text.endsWith("]")
        )
    }

    /*
    |--------------------------------------------------------------------------
    | HTML TITLE
    |--------------------------------------------------------------------------
    */

    private fun extractHtmlTitle(
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

            return cleanHtmlSnippet(
                match.groupValues[1]
            )
        }

        return "Tidak diketahui"
    }

    /*
    |--------------------------------------------------------------------------
    | BERSIHKAN HTML
    |--------------------------------------------------------------------------
    */

    private fun cleanHtmlSnippet(
        html: String
    ): String {

        var text =
            html

        /*
         * Buang script.
         */
        text =
            text.replace(
                Regex(
                    "<script[^>]*>.*?</script>",
                    setOf(
                        RegexOption.IGNORE_CASE,
                        RegexOption.DOT_MATCHES_ALL
                    )
                ),
                " "
            )

        /*
         * Buang style.
         */
        text =
            text.replace(
                Regex(
                    "<style[^>]*>.*?</style>",
                    setOf(
                        RegexOption.IGNORE_CASE,
                        RegexOption.DOT_MATCHES_ALL
                    )
                ),
                " "
            )

        /*
         * Buang tag HTML.
         */
        text =
            text.replace(
                Regex(
                    "<[^>]+>"
                ),
                " "
            )

        /*
         * Decode beberapa entity umum.
         */
        text =
            text
                .replace(
                    "&nbsp;",
                    " "
                )
                .replace(
                    "&amp;",
                    "&"
                )
                .replace(
                    "&quot;",
                    "\""
                )
                .replace(
                    "&#39;",
                    "'"
                )

        /*
         * Rapikan whitespace.
         */
        text =
            text.replace(
                Regex("\\s+"),
                " "
            ).trim()

        return limitText(
            text,
            350
        )
    }

    /*
    |--------------------------------------------------------------------------
    | LIMIT TEXT
    |--------------------------------------------------------------------------
    */

    private fun limitText(
        value: String,
        max: Int
    ): String {

        if (value.length <= max) {
            return value
        }

        return value.substring(
            0,
            max
        ) + "..."
    }

    /*
    |--------------------------------------------------------------------------
    | ERROR JSON
    |--------------------------------------------------------------------------
    */

    private fun errorJson(
        message: String,
        httpCode: Int,
        extra: String = ""
    ): String {

        val fullMessage =
            if (extra.isBlank()) {
                message
            } else {
                "$message $extra"
            }

        return """
        {
          "success": false,
          "message": ${jsonEscape(fullMessage)},
          "http_code": $httpCode
        }
        """.trimIndent()
    }

    /*
    |--------------------------------------------------------------------------
    | JSON ESCAPE
    |--------------------------------------------------------------------------
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
