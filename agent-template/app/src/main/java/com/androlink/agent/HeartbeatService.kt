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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HeartbeatService : Service() {

    private lateinit var storage: Storage

    private var heartbeatJob: Job? = null

    override fun onCreate() {

        super.onCreate()

        Config.load(this)

        storage =
            Storage(this)

        createNotificationChannel()

        startForeground(
            1001,
            createNotification()
        )

        heartbeatJob =
            CoroutineScope(
                Dispatchers.IO
            ).launch {

                while (isActive) {

                    sendHeartbeat()

                    delay(30_000)
                }
            }
    }

    private suspend fun sendHeartbeat() {

        val token =
            storage.deviceToken

        if (
            token.isNullOrBlank()
        ) {
            return
        }

        val deviceId =
            if (
                Config.DEVICE_ID
                    .isNotBlank()
            ) {

                Config.DEVICE_ID

            } else {

                DeviceInfo.id(this)
            }

        try {

            val result =
                Api.heartbeat(
                    deviceId =
                        deviceId,
                    deviceToken =
                        token,
                    sessionToken =
                        storage.sessionToken
                )

            if (
                result.optBoolean(
                    "success",
                    false
                )
            ) {

                result
                    .optString(
                        "session_token"
                    )
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        storage.sessionToken =
                            it
                    }
            }

        } catch (_: Exception) {
            /*
             * Heartbeat gagal tidak
             * menghentikan service.
             */
        }
    }

    private fun createNotification(): Notification {

        return NotificationCompat
            .Builder(
                this,
                "androlink_agent"
            )
            .setContentTitle(
                "AndroLink Agent"
            )
            .setContentText(
                "Agent aktif dan terhubung"
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_upload
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "androlink_agent",
                    "AndroLink Agent",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        heartbeatJob?.cancel()

        heartbeatJob = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
