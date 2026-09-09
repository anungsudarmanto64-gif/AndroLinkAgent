package com.androlink.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HeartbeatService : Service() {

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private lateinit var storage: Storage

    override fun onCreate() {

        super.onCreate()

        Config.load(this)

        storage = Storage(this)

        createQuietChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        scope.launch {

            while (isActive) {

                try {

                    val token =
                        storage.deviceToken

                    if (!token.isNullOrBlank()) {

                        val result =
                            Api.heartbeat(
                                deviceId =
                                    DeviceInfo.id(
                                        this@HeartbeatService
                                    ),
                                deviceToken =
                                    token,
                                sessionToken =
                                    storage.sessionToken
                            )

                        if (result.success) {

                            result.json
                                ?.optString(
                                    "session_token",
                                    ""
                                )
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    storage.sessionToken = it
                                }

                        } else {

                            /*
                             * Jangan langsung memutus aplikasi.
                             * Heartbeat akan dicoba lagi pada siklus berikutnya.
                             */
                        }
                    }

                } catch (_: Exception) {

                    /*
                     * Coba lagi pada siklus berikutnya.
                     */
                }

                delay(30_000)
            }
        }
    }

    private fun createQuietChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "AndroLink berjalan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "Status koneksi AndroLink Agent"

                setSound(null, null)

                enableVibration(false)

                setShowBadge(false)
            }

        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_upload
            )
            .setContentTitle(
                "AndroLink Agent"
            )
            .setContentText(
                "Koneksi perangkat aktif"
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setShowWhen(false)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    companion object {

        private const val CHANNEL_ID =
            "androlink_service"

        private const val NOTIFICATION_ID =
            7
    }
}
