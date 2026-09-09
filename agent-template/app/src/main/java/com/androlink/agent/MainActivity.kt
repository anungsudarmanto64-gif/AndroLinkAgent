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
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var storage: Storage

    override fun onCreate(b: Bundle?) {

        super.onCreate(b)

        setContentView(R.layout.activity_main)

        /*
         * Load konfigurasi yang ditanam ke APK.
         */
        Config.load(this)

        storage = Storage(this)

        status = findViewById(R.id.tvStatus)
        connect = findViewById(R.id.btnConnect)
        disconnect = findViewById(R.id.btnDisconnect)

        findViewById<TextView>(R.id.tvDevice).text =
            "Perangkat: ${DeviceInfo.name()}"

        findViewById<TextView>(R.id.tvAndroid).text =
            "Android: ${DeviceInfo.android()}"

        /*
         * Tidak ada input pairing code.
         *
         * Device ID
         * Device Token
         * Enrollment Code
         *
         * semuanya berasal dari konfigurasi APK.
         */

        connect.text = "Hubungkan Agent"

        connect.setOnClickListener {
            pair()
        }

        disconnect.setOnClickListener {

            storage.clear()

            stopService(
                Intent(
                    this,
                    HeartbeatService::class.java
                )
            )

            status.text = "Status: Terputus"

            disconnect.visibility = View.GONE
            connect.visibility = View.VISIBLE
        }

        /*
         * Android 13+
         */
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                11
            )
        }

        /*
         * Automatic enrollment.
         *
         * Harus mempunyai:
         * DEVICE_ID
         * DEVICE_TOKEN
         * ENROLLMENT_CODE
         */
        if (
            Config.DEVICE_ID.isNotBlank() &&
            Config.DEVICE_TOKEN.isNotBlank() &&
            Config.ENROLLMENT_CODE.isNotBlank()
        ) {

            pair()

        } else {

            status.text =
                "Status: Konfigurasi enrollment APK tidak lengkap"

            connect.isEnabled = true
        }
    }


    /**
     * ============================================================
     * AUTOMATIC PAIRING
     * ============================================================
     */
    private fun pair() {

        connect.isEnabled = false

        status.text =
            "Status: Mendaftarkan Agent..."

        CoroutineScope(Dispatchers.Main).launch {

            try {

                /*
                 * =================================================
                 * DEVICE TOKEN
                 * =================================================
                 *
                 * Untuk APK hasil generator, token berasal
                 * dari konfigurasi yang ditanam ke APK.
                 *
                 * Fallback tetap dipertahankan untuk testing
                 * APK template.
                 */
                val token =
                    if (Config.DEVICE_TOKEN.isNotBlank()) {

                        Config.DEVICE_TOKEN

                    } else {

                        storage.deviceToken
                            ?: UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .also {
                                    storage.deviceToken = it
                                }
                    }


                /*
                 * =================================================
                 * DEVICE ID
                 * =================================================
                 */
                val deviceId =
                    if (Config.DEVICE_ID.isNotBlank()) {

                        Config.DEVICE_ID

                    } else {

                        DeviceInfo.id(this@MainActivity)
                    }


                /*
                 * =================================================
                 * ENROLLMENT CODE
                 * =================================================
                 *
                 * Ini adalah perubahan utama.
                 *
                 * Kode tidak lagi dimasukkan manual.
                 * Agent mengambilnya dari:
                 *
                 * assets/androlink-agent.json
                 */
                val enrollmentCode =
                    Config.ENROLLMENT_CODE.trim()


                /*
                 * Simpan token lokal.
                 */
                storage.deviceToken = token


                /*
                 * Pastikan enrollment code tersedia.
                 */
                if (enrollmentCode.isBlank()) {

                    status.text =
                        "Status: Enrollment code tidak tersedia"

                    return@launch
                }


                /*
                 * Debug status tanpa menampilkan token.
                 */
                status.text =
                    "Status: Menghubungkan perangkat..."


                /*
                 * =================================================
                 * PAIR REQUEST
                 * =================================================
                 *
                 * Sekarang mengirim:
                 *
                 * device_id
                 * device_token
                 * enrollment_code
                 */
                val r = withContext(Dispatchers.IO) {

                    Api.pair(
                        deviceId,
                        token,
                        enrollmentCode
                    )
                }


                /*
                 * =================================================
                 * RESPONSE
                 * =================================================
                 */
                if (r.optBoolean("success")) {

                    /*
                     * Session token dari server.
                     */
                    storage.sessionToken =
                        r.optString(
                            "session_token"
                        ).takeIf {
                            it.isNotBlank()
                        }


                    /*
                     * PC token jika server menyediakannya.
                     */
                    storage.pcToken =
                        r.optString(
                            "pc_token"
                        ).takeIf {
                            it.isNotBlank()
                        }


                    /*
                     * Mode device.
                     */
                    storage.mode =
                        r.optString(
                            "mode"
                        ).takeIf {
                            it.isNotBlank()
                        } ?: Config.MODE


                    /*
                     * BERHASIL
                     */
                    status.text =
                        "Status: TERHUBUNG"

                    disconnect.visibility =
                        View.VISIBLE

                    connect.visibility =
                        View.GONE


                    /*
                     * Jalankan heartbeat service.
                     */
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(
                            this@MainActivity,
                            HeartbeatService::class.java
                        )
                    )

                } else {

                    /*
                     * Server menolak pairing.
                     *
                     * Tampilkan pesan asli dari server
                     * agar mudah mengetahui penyebabnya.
                     */
                    val message =
                        r.optString(
                            "message",
                            "Pendaftaran ditolak"
                        )

                    val httpCode =
                        if (r.has("http_code")) {
                            r.optInt("http_code")
                        } else {
                            0
                        }

                    status.text =
                        if (httpCode > 0) {
                            "Status: $message (HTTP $httpCode)"
                        } else {
                            "Status: $message"
                        }
                }

            } catch (e: Exception) {

                status.text =
                    "Status: Gagal terhubung: ${
                        e.message ?: "Kesalahan tidak diketahui"
                    }"

            } finally {

                connect.isEnabled = true
            }
        }
    }
}
