```kotlin
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
        val json: JSONObject?,
        val error: String? = null
    )

    /**
     * ============================================================
     * POST RAW
     * ============================================================
     */
    private fun postRaw(
        url: String,
        fields: Map<String, String>
    ): ApiResult {

        var connection: HttpURLConnection? = null

        return try {

            connection =
                URL(url)
                    .openConnection() as HttpURLConnection

            /*
             * ====================================================
             * REQUEST
             * ====================================================
             */

            connection.requestMethod = "POST"

            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.doInput = true
            connection.doOutput = true

            connection.useCaches = false

            /*
             * Jangan pertahankan koneksi.
             */
            connection.setRequestProperty(
                "Connection",
                "close"
            )

            /*
             * Identitas client.
             */
            connection.setRequestProperty(
                "User-Agent",
                "AndroLinkAgent/1.0 Android"
            )

            /*
             * Kita mengharapkan JSON.
             */
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            /*
             * PHP membaca $_POST,
             * jadi gunakan form-urlencoded.
             */
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            /*
             * ====================================================
             * BUILD POST BODY
             * ====================================================
             */

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

            /*
             * Tentukan ukuran body secara eksplisit.
             */
            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )

            /*
             * ====================================================
             * SEND
             * ====================================================
             */

            connection.outputStream.use { output ->

                output.write(bodyBytes)
                output.flush()
            }

            /*
             * ====================================================
             * HTTP CODE
             * ====================================================
             */

            val httpCode =
                connection.responseCode

            /*
             * ====================================================
             * RESPONSE STREAM
             * ====================================================
             */

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

            /*
             * ====================================================
             * PARSE JSON
             * ====================================================
             */

            val json =
                try {

                    val clean =
                        responseText.trim()

                    if (clean.isNotEmpty()) {
                        JSONObject(clean)
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
                body = "",
                json = null,
                error =
                    e.message
                        ?: e.javaClass.simpleName
            )

        } finally {

            connection?.disconnect()
        }
    }

    /**
     * ============================================================
     * DETEKSI RESPONSE HTML / CHALLENGE
     * ============================================================
     */
    private fun isHtmlResponse(body: String): Boolean {

        val text =
            body.trim()
                .lowercase()

        return text.startsWith("<!doctype html") ||
               text.startsWith("<html") ||
               text.contains("<script") ||
               text.contains("aes.js") ||
               text.contains("__test=") ||
               text.contains("javascript") ||
               text.contains("requires javascript")
    }

    /**
     * ============================================================
     * RINGKAS RESPONSE UNTUK DEBUG
     * ============================================================
     */
    private fun responseDescription(
        result: ApiResult,
        endpoint: String
    ): String {

        if (!result.error.isNullOrBlank()) {

            return "Koneksi gagal: ${result.error}"
        }

        if (result.body.isBlank()) {

            return "Server tidak mengirim response."
        }

        if (isHtmlResponse(result.body)) {

            return buildString {

                append(
                    "Server mengembalikan HTML/challenge, bukan JSON."
                )

                append(
                    "\nHTTP: ${result.httpCode}"
                )

                append(
                    "\nEndpoint: $endpoint"
                )

                append(
                    "\nKemungkinan request dihentikan oleh server/hosting sebelum PHP API dijalankan."
                )
            }
        }

        return result.body
            .trim()
            .take(500)
    }

    /**
     * ============================================================
     * PAIR DEVICE
     * ============================================================
     */
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

        /*
         * Enrollment code hanya dikirim jika tersedia.
         */
        if (enrollmentCode.isNotBlank()) {

            fields[
                "enrollment_code"
            ] = enrollmentCode
        }

        val result =
            postRaw(
                Config.PAIR_ENDPOINT,
                fields
            )

        /*
         * ========================================================
         * RESPONSE JSON
         * ========================================================
         */

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

        /*
         * ========================================================
         * RESPONSE BUKAN JSON
         * ========================================================
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
                responseDescription(
                    result,
                    Config.PAIR_ENDPOINT
                )
            )

            /*
             * Untuk diagnosis tetap simpan response asli.
             */
            put(
                "raw_response",
                result.body
            )

            if (!result.error.isNullOrBlank()) {

                put(
                    "error",
                    result.error
                )
            }
        }
    }

    /**
     * ============================================================
     * HEARTBEAT
     * ============================================================
     */
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

        if (!sessionToken.isNullOrBlank()) {

            fields[
                "session_token"
            ] = sessionToken
        }

        val result =
            postRaw(
                Config.HEARTBEAT_ENDPOINT,
                fields
            )

        /*
         * ========================================================
         * RESPONSE JSON
         * ========================================================
         */

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

        /*
         * ========================================================
         * RESPONSE BUKAN JSON
         * ========================================================
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
                responseDescription(
                    result,
                    Config.HEARTBEAT_ENDPOINT
                )
            )

            put(
                "raw_response",
                result.body
            )

            if (!result.error.isNullOrBlank()) {

                put(
                    "error",
                    result.error
                )
            }
        }
    }
}
```

[/writing]

**Yang perlu Anda lakukan sekarang:**

1. Buka `Api.kt`.
2. Hapus seluruh isinya.
3. Paste kode di atas.
4. **Build APK.**
5. Jalankan APK.
6. Tekan **Hubungkan Agent**.

Kalau masih muncul error, **jangan ubah file lain dulu**. Kirim teks/status yang muncul di aplikasi.

Kalau yang muncul misalnya:

> `HTTP 200 — Server mengembalikan HTML/challenge, bukan JSON`

maka kita sudah punya bukti kuat bahwa **request Android sudah keluar dan yang menghalangi adalah lapisan server/hosting**, bukan `pair_device.php`.

Catatan: kode ini tetap memakai `application/x-www-form-urlencoded`, sesuai dengan `$_POST` yang digunakan `pair_device.php`, jadi kita **tidak mengubah format API backend**.
