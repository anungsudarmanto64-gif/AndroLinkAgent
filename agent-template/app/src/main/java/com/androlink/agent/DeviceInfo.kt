package com.androlink.agent
import android.content.Context
import android.os.Build
import android.provider.Settings
object DeviceInfo {
 fun id(c: Context): String = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
 fun name(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
 fun android(): String = Build.VERSION.RELEASE ?: "Unknown"
}
