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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class HeartbeatService : Service() {

    companion object {

        private const val CHANNEL_ID =
            "androlink_agent_channel"

        private const val CHANNEL_NAME =
            "AnDroid Link Agent"

        private const val NOTIFICATION_ID =
            1001

        private const val HEARTBEAT_INTERVAL =
            30_000L
    }

    private lateinit var storage: Storage

    private val serviceJob =
        SupervisorJob()

    private val serviceScope =
        CoroutineScope(
            Dispatchers.IO + serviceJob
        )

    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        storage =
            Storage(this)

        createNotificationChannel()

        /*
         * Kondisi awal WAJIB MATI.
         *
         * Server tetap menjadi sumber kebenaran.
         * Android tidak boleh mengaktifkan agent
         * sendiri.
         */
        storage.agentEnabled = false

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                active = false,
                message = "Menunggu status dari server..."
            )
        )

        startHeartbeat()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Pastikan service tetap berjalan.
         */
        if (heartbeatJob?.isActive != true) {
            startHeartbeat()
        }

        return START_STICKY
    }

    private fun startHeartbeat() {

        if (heartbeatJob?.isActive == true) {
            return
        }

        heartbeatJob =
            serviceScope.launch {

                /*
                 * Sedikit delay supaya service benar-benar
                 * selesai start sebelum request pertama.
                 */
                delay(1_000L)

                while (isActive) {

                    sendHeartbeat()

                    delay(
                        HEARTBEAT_INTERVAL
                    )
                }
            }
    }

    private suspend fun sendHeartbeat() {

        val deviceId =
            storage.deviceId
                ?.trim()
                .orEmpty()

        val deviceToken =
            storage.deviceToken
                ?.trim()
                .orEmpty()

        val sessionToken =
            storage.sessionToken
                ?.trim()
                .orEmpty()

        /*
         * Belum paired.
         */
        if (
            deviceId.isEmpty() ||
            deviceToken.isEmpty() ||
            sessionToken.isEmpty()
        ) {

            /*
             * Security default:
             * tanpa session -> agent MATI.
             */
            storage.agentEnabled = false

            updateNotification(
                active = false,
                message = "Belum terhubung ke server."
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

            processHeartbeatResponse(
                response
            )

        } catch (e: Exception) {

            /*
             * Jika terjadi error komunikasi,
             * jangan pernah menganggap agent aktif.
             */
            storage.agentEnabled = false

            val message =
                e.message
                    ?.take(80)
                    ?: "Koneksi server gagal."

            updateNotification(
                active = false,
                message = message
            )
        }
    }

    private fun processHeartbeatResponse(
        response: String
    ) {

        try {

            val json =
                JSONObject(
                    response.trim()
                )

            val success =
                json.optBoolean(
                    "success",
                    false
                )

            /*
             * Server gagal / session tidak valid.
             */
            if (!success) {

                storage.agentEnabled = false

                val message =
                    json.optString(
                        "message",
                        "Server menolak heartbeat."
                    )

                updateNotification(
                    active = false,
                    message = message
                )

                return
            }

            /*
             * INI BAGIAN PALING PENTING.
             *
             * Status agent hanya boleh mengikuti
             * nilai yang diberikan SERVER.
             *
             * Default = false.
             */
            val serverAgentEnabled =
                json.optBoolean(
                    "agent_enabled",
                    false
                )

            storage.agentEnabled =
                serverAgentEnabled

            if (serverAgentEnabled) {

                updateNotification(
                    active = true,
                    message = "Agent aktif • Server mengizinkan kontrol."
                )

                /*
                 * Tempat untuk menjalankan fungsi agent
                 * nantinya ketika server mengizinkan.
                 */
                onAgentEnabled()

            } else {

                updateNotification(
                    active = false,
                    message = "Agent mati • Menunggu perintah pemilik."
                )

                /*
                 * Pastikan fungsi agent berhenti.
                 */
                onAgentDisabled()
            }

        } catch (e: Exception) {

            /*
             * Response bukan JSON atau format rusak.
             *
             * Fail-safe:
             * agent MATI.
             */
            storage.agentEnabled = false

            updateNotification(
                active = false,
                message = "Response server tidak valid."
            )
        }
    }

    /**
     * Dipanggil ketika server memberikan:
     *
     * agent_enabled = true
     */
    private fun onAgentEnabled() {

        /*
         * Untuk tahap sekarang heartbeat hanya
         * mengontrol status.
         *
         * Fungsi remote-control dapat ditempatkan
         * di sini pada tahap berikutnya.
         */
    }

    /**
     * Dipanggil ketika server memberikan:
     *
     * agent_enabled = false
     */
    private fun onAgentDisabled() {

        /*
         * Semua pekerjaan agent yang membutuhkan
         * izin server harus dihentikan di sini.
         *
         * Untuk sekarang belum ada worker remote-control.
         */
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Status koneksi AnDroid Link Agent"

            channel.setShowBadge(false)

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
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
            /*
             * Jangan menggunakan:
             * stat_sys_data_sync
             *
             * karena tidak tersedia pada environment
             * compile Android yang sedang digunakan.
             */
            .setSmallIcon(
                android.R.drawable.ic_popup_sync
            )
            .setContentTitle(
                title
            )
            .setContentText(
                message
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun updateNotification(
        active: Boolean,
        message: String
    ) {

        try {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.notify(
                NOTIFICATION_ID,
                buildNotification(
                    active,
                    message
                )
            )

        } catch (_: Exception) {
            /*
             * Jangan membuat service mati hanya
             * karena notification gagal diperbarui.
             */
        }
    }

    override fun onDestroy() {

        heartbeatJob?.cancel()

        heartbeatJob = null

        /*
         * Saat service dihentikan, status lokal
         * kembali MATI.
         */
        if (::storage.isInitialized) {
            storage.agentEnabled = false
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
