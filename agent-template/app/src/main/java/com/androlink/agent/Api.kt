package com.androlink.agent

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {

    /**
     * POST form-urlencoded ke server dan membaca response JSON.
     *
     * Perbaikan:
     * - Menangani HTTP 2xx / 4xx / 5xx
     * - Membaca errorStream ketika server mengembalikan error
     * - Tidak langsung crash ketika response bukan JSON
     * - Memberikan informasi HTTP status untuk debugging
     * - Menutup connection dan output stream dengan benar
     */
    fun post(
        url: String,
        fields: Map<String, String>
    ): JSONObject {

        var connection: HttpURLConnection? = null

        try {
            connection = URL(url).openConnection() as HttpURLConnection

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
                "User-Agent",
                "AndroLink-Agent/1.0 Android"
            )

            val body = fields.entries.joinToString("&") { entry ->
                URLEncoder.encode(entry.key, "UTF-8") +
                    "=" +
                    URLEncoder.encode(entry.value, "UTF-8")
            }

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseCode = connection.responseCode

            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.bufferedReader()?.use {
                it.readText()
            }?.trim().orEmpty()

            if (responseText.isBlank()) {
                return JSONObject().apply {
                    put("success", false)
                    put("message", "Server tidak mengembalikan response.")
                    put("http_code", responseCode)
                }
            }

            return try {
                JSONObject(responseText)
            } catch (e: Exception) {

                JSONObject().apply {
                    put("success", false)
                    put(
                        "message",
                        "Response server bukan JSON."
                    )
                    put("http_code", responseCode)

                    /*
                     * Simpan sebagian response untuk debugging.
                     * Jangan terlalu panjang supaya tidak memenuhi UI/log.
                     */
                    put(
                        "response",
                        responseText.take(500)
                    )
                }
            }

        } catch (e: IOException) {

            return JSONObject().apply {
                put("success", false)
                put(
                    "message",
                    "Gagal koneksi ke server: ${
                        e.message ?: "Network error"
                    }"
                )
                put("error", "NETWORK_ERROR")
            }

        } catch (e: Exception) {

            return JSONObject().apply {
                put("success", false)
                put(
                    "message",
                    e.message ?: "Terjadi kesalahan."
                )
                put("error", "CLIENT_ERROR")
            }

        } finally {
            connection?.disconnect()
        }
    }


    /**
     * ============================================================
     * PAIR DEVICE
     * ============================================================
     *
     * Perbaikan utama:
     *
     * Sebelumnya hanya:
     * - device_id
     * - device_token
     *
     * Sekarang juga mengirim:
     * - enrollment_code
     *
     * sehingga APK hasil generate dapat melakukan automatic
     * enrollment ke server.
     */
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


    /**
     * ============================================================
     * HEARTBEAT
     * ============================================================
     *
     * Mengirim status perangkat ke server.
     *
     * session_token hanya dikirim jika sudah tersedia.
     */
    fun heartbeat(
        id: String,
        token: String,
        session: String?
    ): JSONObject {

        val fields = mutableMapOf(
            "device_id" to id,
            "device_token" to token
        )

        if (!session.isNullOrBlank()) {
            fields["session_token"] = session
        }

        return post(
            Config.HEARTBEAT_ENDPOINT,
            fields
        )
    }
}
