package com.androlink.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var storage: Storage

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        /*
         * Load konfigurasi APK.
         */
        Config.load(this)

        /*
         * Storage lokal.
         */
        storage = Storage(this)

        /*
         * UI.
         */
        status =
            findViewById(
                R.id.tvStatus
            )

        connect =
            findViewById(
                R.id.btnConnect
            )

        disconnect =
            findViewById(
                R.id.btnDisconnect
            )

        /*
         * Informasi perangkat.
         */
        findViewById<TextView>(
            R.id.tvDevice
        ).text =
            "Perangkat: ${DeviceInfo.name()}"

        findViewById<TextView>(
            R.id.tvAndroid
        ).text =
            "Android: ${DeviceInfo.android()}"

        /*
         * Default UI.
         */
        connect.text =
            "Hubungkan Agent"

        disconnect.visibility =
            View.GONE

        /*
         * Tombol CONNECT.
         */
        connect.setOnClickListener {

            pair()
        }

        /*
         * Tombol DISCONNECT.
         */
        disconnect.setOnClickListener {

            disconnectAgent()
        }

        /*
         * Permission notifikasi Android 13+.
         */
        requestNotificationPermission()

        /*
         * Jika APK hasil generator mempunyai
         * konfigurasi enrollment lengkap,
         * langsung coba pairing.
         */
        if (
            Config.DEVICE_ID.isNotBlank() &&
            Config.DEVICE_TOKEN.isNotBlank() &&
            Config.ENROLLMENT_CODE.isNotBlank()
        ) {

            status.text =
                "Status: Konfigurasi ditemukan\nMemulai pairing..."

            pair()

        } else {

            status.text =
                "Status: APK belum memiliki konfigurasi enrollment"
        }
    }

    /*
     * =========================================================
     * PAIRING
     * =========================================================
     */
    private fun pair() {

        /*
         * Enrollment code wajib ada.
         */
        if (
            Config.ENROLLMENT_CODE.isBlank()
        ) {

            status.text =
                "Status: Kode enrollment belum tersedia"

            return
        }

        /*
         * Cegah tombol ditekan berkali-kali.
         */
        connect.isEnabled =
            false

        disconnect.visibility =
            View.GONE

        status.text =
            "Status: Menghubungkan ke server..."

        CoroutineScope(
            Dispatchers.Main
        ).launch {

            try {

                /*
                 * =================================================
                 * DEVICE TOKEN
                 * =================================================
                 *
                 * Prioritas:
                 *
                 * 1. Token dari APK generator.
                 * 2. Token lokal yang tersimpan.
                 * 3. Generate token baru.
                 */
                val token =
                    when {

                        Config.DEVICE_TOKEN
                            .isNotBlank() -> {

                            Config.DEVICE_TOKEN
                        }

                        !storage.deviceToken
                            .isNullOrBlank() -> {

                            storage.deviceToken!!
                        }

                        else -> {

                            UUID
                                .randomUUID()
                                .toString()
                                .replace(
                                    "-",
                                    ""
                                )
                                .also {

                                    storage.deviceToken =
                                        it
                                }
                        }
                    }

                /*
                 * =================================================
                 * DEVICE ID
                 * =================================================
                 *
                 * Jika generator memberikan Device ID,
                 * gunakan Device ID tersebut.
                 *
                 * Kalau kosong, gunakan DeviceInfo.id().
                 */
                val deviceId =
                    if (
                        Config.DEVICE_ID
                            .isNotBlank()
                    ) {

                        Config.DEVICE_ID

                    } else {

                        DeviceInfo.id(
                            this@MainActivity
                        )
                    }

                /*
                 * Simpan identitas lokal.
                 */
                storage.deviceId =
                    deviceId

                storage.deviceToken =
                    token

                storage.enrollmentCode =
                    Config.ENROLLMENT_CODE

                /*
                 * Informasi proses.
                 */
                status.text =
                    """
                    Status: Mempersiapkan pairing...
                    
                    Device ID:
                    $deviceId
                    
                    Enrollment:
                    ${Config.ENROLLMENT_CODE}
                    
                    Menghubungi server...
                    """.trimIndent()

                /*
                 * =================================================
                 * REQUEST PAIRING
                 * =================================================
                 *
                 * Network dijalankan di IO thread.
                 */
                val resultString =
                    withContext(
                        Dispatchers.IO
                    ) {

                        Api.pair(
                            deviceId =
                                deviceId,

                            deviceToken =
                                token,

                            enrollmentCode =
                                Config.ENROLLMENT_CODE,

                            deviceName =
                                DeviceInfo.name()
                        )
                    }

                /*
                 * =================================================
                 * VALIDASI RESPONSE
                 * =================================================
                 */
                val raw =
                    resultString
                        .trim()

                /*
                 * Response kosong.
                 */
                if (raw.isBlank()) {

                    status.text =
                        """
                        PAIRING GAGAL
                        
                        Server mengirim response kosong.
                        """.trimIndent()

                    return@launch
                }

                /*
                 * Jangan langsung JSONObject jika
                 * response jelas bukan JSON.
                 */
                if (
                    raw.startsWith("<") ||
                    raw.contains(
                        "<html",
                        ignoreCase = true
                    ) ||
                    raw.contains(
                        "<!doctype",
                        ignoreCase = true
                    ) ||
                    raw.contains(
                        "<head",
                        ignoreCase = true
                    ) ||
                    raw.contains(
                        "<body",
                        ignoreCase = true
                    )
                ) {

                    status.text =
                        """
                        PAIRING GAGAL
                        
                        Server tidak mengirim JSON.
                        
                        Response:
                        ${cleanPreview(raw)}
                        """.trimIndent()

                    return@launch
                }

                /*
                 * =================================================
                 * PARSE JSON
                 * =================================================
                 */
                val result =
                    try {

                        JSONObject(raw)

                    } catch (e: Exception) {

                        /*
                         * Ini bagian penting untuk error:
                         *
                         * Unexpected non-whitespace character
                         *
                         * Kita tampilkan response sebenarnya.
                         */
                        status.text =
                            """
                            PAIRING GAGAL
                            
                            Response server bukan JSON valid.
                            
                            Error:
                            ${e.message ?: "JSON parse error"}
                            
                            Response:
                            ${cleanPreview(raw)}
                            """.trimIndent()

                        return@launch
                    }

                /*
                 * =================================================
                 * HASIL JSON
                 * =================================================
                 */
                val success =
                    result.optBoolean(
                        "success",
                        false
                    )

                /*
                 * Beberapa endpoint mungkin menaruh
                 * HTTP code di root.
                 */
                val httpCode =
                    result.optInt(
                        "http_code",
                        0
                    )

                /*
                 * Kalau http_code ada di data,
                 * coba ambil juga.
                 */
                val data =
                    result.optJSONObject(
                        "data"
                    )

                val dataHttpCode =
                    data?.optInt(
                        "http_code",
                        0
                    ) ?: 0

                val finalHttpCode =
                    if (
                        httpCode > 0
                    ) {
                        httpCode
                    } else {
                        dataHttpCode
                    }

                /*
                 * Message root.
                 */
                var message =
                    result.optString(
                        "message",
                        ""
                    )

                /*
                 * Kalau message kosong,
                 * coba dari data.
                 */
                if (
                    message.isBlank() &&
                    data != null
                ) {

                    message =
                        data.optString(
                            "message",
                            ""
                        )
                }

                if (
                    message.isBlank()
                ) {

                    message =
                        "Response server tidak diketahui."
                }

                /*
                 * =================================================
                 * PAIRING BERHASIL
                 * =================================================
                 */
                if (success) {

                    /*
                     * Session token.
                     */
                    val session =
                        firstNonBlank(
                            result.optString(
                                "session_token",
                                ""
                            ),
                            data?.optString(
                                "session_token",
                                ""
                            )
                        )

                    /*
                     * PC token.
                     */
                    val pc =
                        firstNonBlank(
                            result.optString(
                                "pc_token",
                                ""
                            ),
                            data?.optString(
                                "pc_token",
                                ""
                            )
                        )

                    /*
                     * Mode.
                     */
                    val mode =
                        firstNonBlank(
                            result.optString(
                                "mode",
                                ""
                            ),
                            data?.optString(
                                "mode",
                                ""
                            ),
                            Config.MODE,
                            "temporary"
                        )

                    /*
                     * agent_enabled.
                     *
                     * DEFAULT WAJIB FALSE.
                     */
                    val agentEnabled =
                        if (
                            result.has(
                                "agent_enabled"
                            )
                        ) {

                            result.optBoolean(
                                "agent_enabled",
                                false
                            )

                        } else if (
                            data?.has(
                                "agent_enabled"
                            ) == true
                        ) {

                            data.optBoolean(
                                "agent_enabled",
                                false
                            )

                        } else {

                            false
                        }

                    /*
                     * =================================================
                     * SIMPAN SESSION
                     * =================================================
                     */
                    storage.deviceId =
                        deviceId

                    storage.deviceToken =
                        token

                    storage.enrollmentCode =
                        Config.ENROLLMENT_CODE

                    storage.sessionToken =
                        session.ifBlank {
                            null
                        }

                    storage.pcToken =
                        pc.ifBlank {
                            null
                        }

                    storage.mode =
                        mode

                    /*
                     * Server adalah sumber kebenaran.
                     *
                     * Pairing baru:
                     * agent_enabled = false.
                     */
                    storage.agentEnabled =
                        agentEnabled

                    /*
                     * =================================================
                     * UI
                     * =================================================
                     */
                    status.text =
                        if (
                            agentEnabled
                        ) {

                            """
                            Status: TERHUBUNG
                            AGENT: AKTIF
                            Mode: ${mode.uppercase()}
                            """.trimIndent()

                        } else {

                            """
                            Status: TERHUBUNG
                            AGENT: MATI
                            Mode: ${mode.uppercase()}
                            """.trimIndent()
                        }

                    disconnect.visibility =
                        View.VISIBLE

                    connect.visibility =
                        View.GONE

                    /*
                     * =================================================
                     * HEARTBEAT SERVICE
                     * =================================================
                     *
                     * Service akan mengambil keputusan
                     * agent_enabled dari server.
                     */
                    startHeartbeatService()

                } else {

                    /*
                     * =================================================
                     * PAIRING GAGAL
                     * =================================================
                     */
                    storage.agentEnabled =
                        false

                    val errorText =
                        if (
                            finalHttpCode > 0
                        ) {

                            "HTTP $finalHttpCode\n$message"

                        } else {

                            message
                        }

                    status.text =
                        """
                        PAIRING GAGAL
                        
                        $errorText
                        """.trimIndent()

                    disconnect.visibility =
                        View.GONE

                    connect.visibility =
                        View.VISIBLE
                }

            } catch (e: Exception) {

                /*
                 * Exception umum.
                 */
                storage.agentEnabled =
                    false

                status.text =
                    """
                    KONEKSI GAGAL
                    
                    ${e.message ?: e.javaClass.simpleName}
                    """.trimIndent()

                disconnect.visibility =
                    View.GONE

                connect.visibility =
                    View.VISIBLE

            } finally {

                connect.isEnabled =
                    true
            }
        }
    }

    /*
     * =========================================================
     * DISCONNECT
     * =========================================================
     */
    private fun disconnectAgent() {

        /*
         * Stop heartbeat.
         */
        stopService(
            Intent(
                this,
                HeartbeatService::class.java
            )
        )

        /*
         * Hapus seluruh session lokal.
         */
        storage.clearAll()

        /*
         * Pastikan agent mati.
         */
        storage.agentEnabled =
            false

        /*
         * UI.
         */
        status.text =
            "Status: Terputus"

        disconnect.visibility =
            View.GONE

        connect.visibility =
            View.VISIBLE

        connect.isEnabled =
            true
    }

    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */
    private fun startHeartbeatService() {

        try {

            ContextCompat.startForegroundService(
                this,
                Intent(
                    this,
                    HeartbeatService::class.java
                )
            )

        } catch (e: Exception) {

            status.text =
                """
                TERHUBUNG
                AGENT: MATI
                
                Heartbeat gagal dimulai:
                ${e.message ?: e.javaClass.simpleName}
                """.trimIndent()
        }
    }

    /*
     * =========================================================
     * NOTIFICATION PERMISSION
     * =========================================================
     */
    private fun requestNotificationPermission() {

        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                11
            )
        }
    }

    /*
     * =========================================================
     * HELPER
     * =========================================================
     */

    private fun firstNonBlank(
        vararg values: String?
    ): String {

        for (value in values) {

            if (
                !value.isNullOrBlank()
            ) {

                return value
            }
        }

        return ""
    }

    /*
     * Ambil response maksimal 500 karakter
     * supaya UI tidak penuh HTML.
     */
    private fun cleanPreview(
        value: String
    ): String {

        return value
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .take(500)
    }

    /*
     * Escape JSON string.
     *
     * Dipertahankan sebagai helper apabila
     * diperlukan oleh debugging lain.
     */
    private fun jsonEscape(
        value: String
    ): String {

        return "\"${
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
        }\""
    }
}
