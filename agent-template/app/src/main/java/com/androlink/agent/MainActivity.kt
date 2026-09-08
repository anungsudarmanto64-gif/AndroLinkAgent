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
        Config.load(this)
        storage = Storage(this)
        status = findViewById(R.id.tvStatus)
        connect = findViewById(R.id.btnConnect)
        disconnect = findViewById(R.id.btnDisconnect)

        findViewById<TextView>(R.id.tvDevice).text = "Perangkat: ${DeviceInfo.name()}"
        findViewById<TextView>(R.id.tvAndroid).text = "Android: ${DeviceInfo.android()}"

        // Agent menggunakan konfigurasi yang sudah ditanam ke APK.
        // Tidak ada input kode pairing manual.
        connect.text = "Hubungkan Agent"
        connect.setOnClickListener { pair() }
        disconnect.setOnClickListener {
            storage.clear()
            stopService(Intent(this, HeartbeatService::class.java))
            status.text = "Status: Terputus"
            disconnect.visibility = View.GONE
            connect.visibility = View.VISIBLE
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }

        if (Config.DEVICE_ID.isNotBlank() && Config.DEVICE_TOKEN.isNotBlank()) {
            pair()
        } else {
            status.text = "Status: APK belum memiliki konfigurasi enrollment"
        }
    }

    private fun pair() {
        connect.isEnabled = false
        status.text = "Status: Mendaftarkan Agent..."
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = if (Config.DEVICE_TOKEN.isNotBlank()) {
                    Config.DEVICE_TOKEN
                } else {
                    storage.deviceToken ?: UUID.randomUUID().toString().replace("-", "")
                        .also { storage.deviceToken = it }
                }
                val deviceId = if (Config.DEVICE_ID.isNotBlank()) Config.DEVICE_ID else DeviceInfo.id(this@MainActivity)
                storage.deviceToken = token
                val r = withContext(Dispatchers.IO) { Api.pair(deviceId, token) }
                if (r.optBoolean("success")) {
                    storage.sessionToken = r.optString("session_token").takeIf { it.isNotBlank() }
                    storage.pcToken = r.optString("pc_token").takeIf { it.isNotBlank() }
                    storage.mode = r.optString("mode").takeIf { it.isNotBlank() } ?: Config.MODE
                    status.text = "Status: TERHUBUNG"
                    disconnect.visibility = View.VISIBLE
                    connect.visibility = View.GONE
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, HeartbeatService::class.java))
                } else {
                    status.text = "Status: ${r.optString("message", "Pendaftaran ditolak")}"
                }
            } catch (e: Exception) {
                status.text = "Status: Gagal terhubung"
            } finally {
                connect.isEnabled = true
            }
        }
    }
}
