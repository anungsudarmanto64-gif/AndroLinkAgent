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

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var detail: TextView
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

        storage = Storage(this)

        status =
            findViewById(R.id.tvStatus)

        detail =
            findViewById(R.id.tvDetail)

        connect =
            findViewById(R.id.btnConnect)

        disconnect =
            findViewById(R.id.btnDisconnect)

        findViewById<TextView>(
            R.id.tvDevice
        ).text =
            "Perangkat: ${DeviceInfo.name()}"

        findViewById<TextView>(
            R.id.tvAndroid
        ).text =
            "Android: ${DeviceInfo.android()}"

        connect.text =
            "HUBUNGKAN AGENT"

        connect.setOnClickListener {
            pair()
        }

        disconnect.setOnClickListener {
            disconnectAgent()
        }

        requestNotificationPermission()

        /*
         * AUTOMATIC ENROLLMENT
         */
        if (Config.isEnrolled()) {

            detail.text =
                "Device ID: ${Config.DEVICE_ID}"

            pair()

        } else {

            status.text =
                "Status: Konfigurasi APK tidak lengkap"

            detail.text =
                "Device ID / token / enrollment code tidak ditemukan."
        }
    }

    private fun requestNotificationPermission() {

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
    }

    private fun pair() {

        if (!Config.isEnrolled()) {

            status.text =
                "Status: APK belum terdaftar"

            detail.text =
                "Buat Agent baru dari dashboard."

            return
        }

        connect.isEnabled = false

        status.text =
            "Status: Mendaftarkan Agent..."

        detail.text =
            "Menghubungkan ke server..."

        CoroutineScope(
            Dispatchers.Main
        ).launch {

            try {

                val response =
                    withContext(
                        Dispatchers.IO
                    ) {

                        Api.pair(
                            Config.DEVICE_ID,
                            Config.DEVICE_TOKEN,
                            Config.ENROLLMENT_CODE
                        )
                    }

                if (
                    response.optBoolean(
                        "success",
                        false
                    )
                ) {

                    /*
                     * Server kita mengembalikan
                     * token di root JSON.
                     */
                    val sessionToken =
                        response
                            .optString(
                                "session_token",
                                ""
                            )
                            .takeIf {
                                it.isNotBlank()
                            }

                    val pcToken =
                        response
                            .optString(
                                "pc_token",
                                ""
                            )
                            .takeIf {
                                it.isNotBlank()
                            }

                    storage.sessionToken =
                        sessionToken

                    storage.pcToken =
                        pcToken

                    storage.deviceToken =
                        Config.DEVICE_TOKEN

                    storage.mode =
                        response.optString(
                            "mode",
                            Config.MODE
                        )

                    status.text =
                        "Status: TERHUBUNG"

                    detail.text =
                        "Device ID: ${Config.DEVICE_ID}"

                    disconnect.visibility =
                        View.VISIBLE

                    connect.visibility =
                        View.GONE

                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(
                            this@MainActivity,
                            HeartbeatService::class.java
                        )
                    )

                } else {

                    val message =
                        response.optString(
                            "message",
                            "Pendaftaran ditolak."
                        )

                    val httpCode =
                        response.optInt(
                            "http_code",
                            0
                        )

                    val raw =
                        response.optString(
                            "raw_response",
                            ""
                        )

                    status.text =
                        if (httpCode > 0) {

                            "Status: $message (HTTP $httpCode)"

                        } else {

                            "Status: $message"
                        }

                    detail.text =
                        if (raw.isNotBlank()) {

                            raw.take(500)

                        } else {

                            "Device ID: ${Config.DEVICE_ID}"
                        }
                }

            } catch (e: Exception) {

                status.text =
                    "Status: Gagal terhubung"

                detail.text =
                    e.message
                        ?: "Kesalahan tidak diketahui."

            } finally {

                connect.isEnabled = true
            }
        }
    }

    private fun disconnectAgent() {

        storage.clear()

        stopService(
            Intent(
                this,
                HeartbeatService::class.java
            )
        )

        status.text =
            "Status: Terputus"

        detail.text =
            "Agent tidak terhubung."

        disconnect.visibility =
            View.GONE

        connect.visibility =
            View.VISIBLE
    }
}
