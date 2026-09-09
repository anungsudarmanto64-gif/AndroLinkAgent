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

        Config.load(this)

        storage =
            Storage(this)

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

        findViewById<TextView>(
            R.id.tvDevice
        ).text =
            "Perangkat: ${DeviceInfo.name()}"

        findViewById<TextView>(
            R.id.tvAndroid
        ).text =
            "Android: ${DeviceInfo.android()}"

        connect.text =
            "Hubungkan Agent"

        connect.setOnClickListener {
            pair()
        }

        disconnect.setOnClickListener {

            /*
             * Hentikan service terlebih dahulu.
             */
            stopService(
                Intent(
                    this,
                    HeartbeatService::class.java
                )
            )

            /*
             * Hapus session pairing.
             *
             * clearAll() digunakan karena
             * tombol disconnect berarti
             * memutus seluruh data pairing lokal.
             */
            storage.clearAll()

            /*
             * Status lokal selalu MATI setelah disconnect.
             */
            storage.agentEnabled = false

            status.text =
                "Status: Terputus"

            disconnect.visibility =
                View.GONE

            connect.visibility =
                View.VISIBLE
        }

        /*
         * Permission notifikasi Android 13+.
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
         * Kalau APK memiliki konfigurasi
         * enrollment, coba pairing otomatis.
         */
        if (
            Config.DEVICE_ID.isNotBlank() &&
            Config.DEVICE_TOKEN.isNotBlank() &&
            Config.ENROLLMENT_CODE.isNotBlank()
        ) {

            pair()

        } else {

            status.text =
                "Status: APK belum memiliki konfigurasi enrollment"
        }
    }

    private fun pair() {

        if (
            Config.ENROLLMENT_CODE.isBlank()
        ) {

            status.text =
                "Status: Kode enrollment belum tersedia"

            return
        }

        connect.isEnabled =
            false

        status.text =
            "Status: Menghubungkan ke server..."

        CoroutineScope(
            Dispatchers.Main
        ).launch {

            try {

                /*
                 * Token dari konfigurasi generator.
                 *
                 * Jika tidak tersedia,
                 * gunakan token lokal.
                 */
                val token =
                    if (
                        Config.DEVICE_TOKEN
                            .isNotBlank()
                    ) {

                        Config.DEVICE_TOKEN

                    } else {

                        storage.deviceToken
                            ?: UUID
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

                /*
                 * Device ID dari konfigurasi generator.
                 *
                 * Jika kosong, gunakan ANDROID_ID.
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

                storage.deviceToken =
                    token

                storage.deviceId =
                    deviceId

                storage.enrollmentCode =
                    Config.ENROLLMENT_CODE

                status.text =
                    "Status: Mengirim data perangkat..."

                /*
                 * Request pairing dilakukan di IO thread.
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
                 * Api.pair() mengembalikan STRING JSON.
                 *
                 * Jadi harus diubah menjadi JSONObject
                 * terlebih dahulu.
                 */
                val result =
                    try {

                        JSONObject(
                            resultString.trim()
                        )

                    } catch (e: Exception) {

                        JSONObject(
                            """
                            {
                              "success": false,
                              "message": "Response server bukan JSON valid.",
                              "raw_response": ${jsonEscape(resultString)}
                            }
                            """.trimIndent()
                        )
                    }

                /*
                 * Ambil hasil dari JSON.
                 */
                val success =
                    result.optBoolean(
                        "success",
                        false
                    )

                val httpCode =
                    result.optInt(
                        "http_code",
                        0
                    )

                val message =
                    result.optString(
                        "message",
                        "Response server tidak diketahui."
                    )

                if (success) {

                    /*
                     * Session token hasil pairing.
                     */
                    val session =
                        result.optString(
                            "session_token",
                            ""
                        )
                            .takeIf {
                                it.isNotBlank()
                            }

                    /*
                     * PC token hasil pairing.
                     */
                    val pc =
                        result.optString(
                            "pc_token",
                            ""
                        )
                            .takeIf {
                                it.isNotBlank()
                            }

                    /*
                     * Mode pairing.
                     */
                    val mode =
                        result.optString(
                            "mode",
                            ""
                        )
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?: Config.MODE

                    /*
                     * Server bisa mengirim status
                     * agent_enabled.
                     *
                     * DEFAULT WAJIB FALSE.
                     */
                    val agentEnabled =
                        result.optBoolean(
                            "agent_enabled",
                            false
                        )

                    /*
                     * Simpan hasil pairing.
                     */
                    storage.deviceId =
                        deviceId

                    storage.deviceToken =
                        token

                    storage.enrollmentCode =
                        Config.ENROLLMENT_CODE

                    storage.sessionToken =
                        session

                    storage.pcToken =
                        pc

                    storage.mode =
                        mode

                    /*
                     * Status agent mengikuti server.
                     *
                     * Pairing baru seharusnya false.
                     */
                    storage.agentEnabled =
                        agentEnabled

                    status.text =
                        if (agentEnabled) {
                            "Status: TERHUBUNG • AGENT AKTIF"
                        } else {
                            "Status: TERHUBUNG • AGENT MATI"
                        }

                    disconnect.visibility =
                        View.VISIBLE

                    connect.visibility =
                        View.GONE

                    /*
                     * Jalankan heartbeat service.
                     *
                     * Service akan terus bertanya ke server
                     * mengenai agent_enabled.
                     */
                    ContextCompat
                        .startForegroundService(
                            this@MainActivity,
                            Intent(
                                this@MainActivity,
                                HeartbeatService::class.java
                            )
                        )

                } else {

                    /*
                     * Pairing gagal.
                     */
                    status.text =
                        if (httpCode > 0) {
                            "HTTP $httpCode - $message"
                        } else {
                            message
                        }
                }

            } catch (e: Exception) {

                status.text =
                    "Gagal: ${
                        e.message
                            ?: e.javaClass.simpleName
                    }"

            } finally {

                connect.isEnabled =
                    true
            }
        }
    }

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
