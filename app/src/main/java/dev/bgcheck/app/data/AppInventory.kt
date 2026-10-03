package dev.bgcheck.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInventory(private val context: Context) {
    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val protected = mutableMapOf(
            context.packageName to "This app needs background access to manage your settings.",
            "moe.shizuku.privileged.api" to "Shizuku provides access to your device controls.",
            "com.tailscale.ipn" to "Tailscale keeps your device reachable.",
            "com.termux" to "Termux may provide remote access to this device.",
            "sh.haven.app" to "Haven provides remote access to this device.",
            "com.android.systemui" to "Android needs this component for system navigation.",
            "com.android.settings" to "Android needs Settings to manage the device.",
            "com.google.android.gms" to "Google Play services delivers notifications and essential device services.",
        )
        runCatching { pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }
            .getOrNull()?.let { protected[it] = "This is your current home screen." }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.let { protected[it] = "This is your current keyboard." }
        listOfNotNull(
            runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull(),
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
        ).forEach {
            protected[it] = "This app handles your calls or messages. Manage it in Android Settings."
        }
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0).map { info ->
            AppEntry(
                packageName = info.packageName, label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                uid = info.uid, system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                enabled = info.enabled, targetSdk = info.targetSdkVersion,
                protectedReason = when {
                    info.uid % 100000 < 10000 -> "Core Android component. Use Android Settings for this app."
                    protected[info.packageName] != null -> protected[info.packageName]
                    (pm.getPackagesForUid(info.uid)?.size ?: 0) > 1 -> "This app shares an Android identity with other apps. Changes could affect them; use Android Settings."
                    else -> null
                },
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun battery(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return BatteryInfo(
            percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0,100) else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureC = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f),
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 },
            saver = context.getSystemService(PowerManager::class.java).isPowerSaveMode,
        )
    }
}
