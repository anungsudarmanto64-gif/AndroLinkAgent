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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        Config.load(this)

        storage = Storage(this)

        status = findViewById(R.id.tvStatus)
        connect = findViewById(R.id.btnConnect)
        disconnect = findViewById(R.id.btnDisconnect)

        findViewById<TextView>(R.id.tvDevice).text =
            "Perangkat: ${DeviceInfo.name()}"

        findViewById<TextView>(R.id.tvAndroid).text =
            "Android: ${DeviceInfo.android()}"

        connect.text = "Hubungkan Agent"

        connect.setOnClickListener {
            pair()
        }

        disconnect.setOnClickListener {
            disconnectAgent()
        }

        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                11
            )
        }

        /*
         * Jangan langsung melakukan pairing berulang-ulang
         * kalau konfigurasi APK kosong.
         */
        if (
            Config.DEVICE_ID.isNotBlank() &&
            Config.DEVICE_TOKEN.isNotBlank()
        ) {

            status.text = "Status: Siap menghubungkan"

        } else {

            status.text =
                "Status: APK belum memiliki konfigurasi enrollment"
        }
    }

    private fun pair() {

        if (connect.isEnabled.not()) {
            return
        }

        connect.isEnabled = false

        disconnect.visibility = View.GONE

        status.text = "Status: Menghubungkan..."

        CoroutineScope(Dispatchers.Main).launch {

            try {

                val deviceId =
                    if (Config.DEVICE_ID.isNotBlank()) {
                        Config.DEVICE_ID.trim()
                    } else {
                        DeviceInfo.id(this@MainActivity).trim()
                    }

                val deviceToken =
                    if (Config.DEVICE_TOKEN.isNotBlank()) {
                        Config.DEVICE_TOKEN.trim()
                    } else {
                        storage.deviceToken
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .also {
                                    storage.deviceToken = it
                                }
                    }

                if (deviceId.isBlank()) {

                    status.text =
                        "Status: Device ID kosong"

                    return@launch
                }

                if (deviceToken.isBlank()) {

                    status.text =
                        "Status: Device Token kosong"

                    return@launch
                }

                /*
                 * Simpan token sebelum request.
                 */
                storage.deviceToken = deviceToken

                val result =
                    withContext(Dispatchers.IO) {
                        Api.pair(
                            deviceId = deviceId,
                            deviceToken = deviceToken
                        )
                    }

                /*
                 * DEBUG RESPONSE
                 *
                 * Kita sekarang tahu:
                 * - HTTP code
                 * - JSON
                 * - response mentah
                 */

                if (result.success) {

                    val json = result.json

                    storage.sessionToken =
                        json
                            ?.optString("session_token", "")
                            ?.takeIf { it.isNotBlank() }

                    storage.pcToken =
                        json
                            ?.optString("pc_token", "")
                            ?.takeIf { it.isNotBlank() }

                    storage.mode =
                        json
                            ?.optString("mode", "")
                            ?.takeIf { it.isNotBlank() }
                            ?: Config.MODE

                    status.text =
                        "Status: TERHUBUNG"

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

                    val detail =
                        result.message

                    status.text =
                        if (result.httpCode > 0) {
                            "Status: HTTP ${result.httpCode} - $detail"
                        } else {
                            "Status: $detail"
                        }
                }

            } catch (e: Exception) {

                status.text =
                    "Status: Error - ${
                        e.message ?: e.javaClass.simpleName
                    }"

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

        disconnect.visibility =
            View.GONE

        connect.visibility =
            View.VISIBLE

        connect.isEnabled =
            true
    }
}
