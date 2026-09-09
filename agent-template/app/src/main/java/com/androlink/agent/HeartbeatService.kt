package com.androlink.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class HeartbeatService : Service() {

    companion object {

        private const val CHANNEL_ID = "androlink_agent"
        private const val NOTIFICATION_ID = 1001

        private const val HEARTBEAT_INTERVAL = 30_000L
    }

    private val serviceJob = SupervisorJob()

    private val serviceScope =
        CoroutineScope(
            Dispatchers.IO + serviceJob
        )

    private var heartbeatJob: Job? = null

    private lateinit var storage: Storage

    override fun onCreate() {
        super.onCreate()

        storage = Storage(this)

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                active = false,
                message = "Menunggu aktivasi pemilik..."
            )
        )

        startHeartbeat()
    }

    private fun startHeartbeat() {

        heartbeatJob?.cancel()

        heartbeatJob = serviceScope.launch {

            /*
             * Beri sedikit waktu setelah service dimulai.
             */
            delay(1000)

            while (isActive) {

                sendHeartbeat()

                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    private suspend fun sendHeartbeat() {

        val deviceId =
            storage.deviceId
                ?: Config.DEVICE_ID

        val deviceToken =
            storage.deviceToken
                ?: Config.DEVICE_TOKEN

        val sessionToken =
            storage.sessionToken

        /*
         * Belum pairing.
         */
        if (
            deviceId.isBlank() ||
            deviceToken.isBlank() ||
            sessionToken.isNullOrBlank()
        ) {

            updateNotification(
                active = false,
                message = "Belum terhubung ke server"
            )

            return
        }

        try {

            val response =
                Api.heartbeat(
                    deviceId = deviceId,
                    deviceToken = deviceToken,
                    sessionToken = sessionToken
                )

            processHeartbeatResponse(response)

        } catch (e: Exception) {

            updateNotification(
                active = false,
                message = "Koneksi server gagal"
            )
        }
    }

    private fun processHeartbeatResponse(
        response: String
    ) {

        try {

            val json =
                JSONObject(response)

            val success =
                json.optBoolean(
                    "success",
                    false
                )

            /*
             * Server tidak menerima heartbeat.
             */
            if (!success) {

                /*
                 * Jangan pernah menganggap Agent aktif
                 * jika server tidak memberikan izin.
                 */
                storage.agentEnabled = false

                updateNotification(
                    active = false,
                    message = json.optString(
                        "message",
                        "Server menolak heartbeat"
                    )
                )

                return
            }

            /*
             * INI BAGIAN PALING PENTING.
             *
             * Server adalah sumber kebenaran.
             *
             * false = Agent MATI
             * true  = Agent AKTIF
             */
            val agentEnabled =
                json.optBoolean(
                    "agent_enabled",
                    false
                )

            storage.agentEnabled =
                agentEnabled

            if (agentEnabled) {

                updateNotification(
                    active = true,
                    message = "Agent aktif • Server mengizinkan"
                )

            } else {

                updateNotification(
                    active = false,
                    message = "Agent mati • Menunggu aktivasi pemilik"
                )
            }

        } catch (e: Exception) {

            /*
             * Jika response bukan JSON,
             * keamanan default = Agent MATI.
             */
            storage.agentEnabled = false

            updateNotification(
                active = false,
                message = "Response server tidak valid"
            )
        }
    }

    private fun updateNotification(
        active: Boolean,
        message: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                active = active,
                message = message
            )
        )
    }

    private fun buildNotification(
        active: Boolean,
        message: String
    ): Notification {

        val title =
            if (active) {
                "AnDroid Link • AGENT AKTIF"
            } else {
                "AnDroid Link • AGENT MATI"
            }

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.stat_sys_data_sync
            )
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "AnDroid Link Agent",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Status koneksi AnDroid Link Agent"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Jika service dihentikan Android,
         * minta sistem menjalankannya kembali.
         */
        return START_STICKY
    }

    override fun onDestroy() {

        heartbeatJob?.cancel()

        serviceJob.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
