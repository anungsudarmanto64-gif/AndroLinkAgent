package com.androlink.agent

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

object DeviceInfo {

    fun id(
        context: Context
    ): String {

        val androidId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
                ?.trim()
                ?.uppercase(Locale.US)
                .orEmpty()

        /*
         * ANDROID_ID biasanya berupa hexadecimal.
         * Kita ambil hanya karakter A-F dan 0-9.
         */
        val hex =
            androidId
                .filter {
                    it in '0'..'9' ||
                    it in 'A'..'F'
                }

        /*
         * Server membutuhkan:
         *
         * ANDRO-XXXXXXXXXXXX
         *
         * tepat 12 karakter hexadecimal.
         */
        val normalized =
            when {
                hex.length >= 12 ->
                    hex.take(12)

                hex.isNotEmpty() ->
                    hex.padEnd(
                        12,
                        '0'
                    )

                else ->
                    "000000000000"
            }

        return "ANDRO-$normalized"
    }

    fun name(): String {

        return "${Build.MANUFACTURER} ${Build.MODEL}"
            .trim()
    }

    fun android(): String {

        return Build.VERSION.RELEASE
            ?: "Unknown"
    }
}
