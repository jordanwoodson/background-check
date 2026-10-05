package dev.bgcheck.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInventory(private val context: Context) {
    suspend fun load(): List<AppEntry> = read().apps

    suspend fun profiles(): List<AndroidProfile> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(android.os.UserManager::class.java)
        dev.bgcheck.network.data.multiuser.HiddenApiHelper.clearUserCache()
        dev.bgcheck.network.data.multiuser.HiddenApiHelper.getUsers(context).map { profile ->
            val handle = android.os.UserHandle.getUserHandleForUid(profile.userId * 100000)
            val quiet = runCatching { manager.isQuietModeEnabled(handle) }.getOrNull()
            val unlocked = runCatching { manager.isUserUnlocked(handle) }.getOrNull()
            val serial = runCatching { manager.getSerialNumberForUser(handle) }.getOrNull()?.takeIf { it >= 0 }
            val reason = when {
                quiet == true -> "Work profile is paused. Resume it in Android Settings."
                unlocked == false -> "Profile is locked or stopped. Unlock or resume it first."
                quiet == null || unlocked == null || serial == null -> "Profile availability could not be verified. Reconnect Shizuku and refresh."
                else -> null
            }
            AndroidProfile(profile.userId, profile.displayName, profile.isWorkProfile, serial,
                available = reason == null, unavailableReason = reason)
        }
    }

    suspend fun read(): AppInventoryResult = withContext(Dispatchers.IO) {
        CanaDebloatGuide.preload()
        val helper = dev.bgcheck.network.data.multiuser.HiddenApiHelper
        // The host grants permission through its own bridge; refresh Network's separate status
        // before asking its package service to read another profile.
        dev.bgcheck.network.De1984Dependencies.getInstance(context).shizukuManager.checkShizukuStatus()
        helper.awaitCrossUserPermission(context)
        val profiles = profiles()
        helper.clearInstalledAppsCache()
        val warnings = mutableListOf<String>()
        val entries = profiles.flatMap { profile ->
            profile.unavailableReason?.let { warnings += "${profile.name}: $it" }
            try {
                @Suppress("DEPRECATION")
                val applications = helper.getInstalledApplicationsAsUser(context,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES, profile.userId)
                check(applications.isNotEmpty()) { "Android did not return this profile's app inventory." }
                check(applications.all { it.uid >= 0 && it.uid / 100000 == profile.userId }) {
                    "Android returned app identities from another profile."
                }
                val profileContext = runCatching {
                    if (profile.userId == android.os.Process.myUid() / 100000) context else
                        Context::class.java.getMethod("createContextAsUser", android.os.UserHandle::class.java,
                            Int::class.javaPrimitiveType).invoke(context,
                            android.os.UserHandle.getUserHandleForUid(profile.userId * 100000), 0) as Context
                }.getOrNull()
                val protected = protectedPackages(profileContext)
                val installedPeers = applications.filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }.groupBy { it.uid }
                applications.distinctBy { it.packageName }.map { info ->
                    val pm = profileContext?.packageManager ?: context.packageManager
                    val installed = info.flags and ApplicationInfo.FLAG_INSTALLED != 0
                    AppEntry(
                        packageName = info.packageName,
                        label = PackageDescriptions.label(info.packageName,
                            runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName)),
                        uid = info.uid, system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        enabled = info.enabled, targetSdk = info.targetSdkVersion,
                        installed = installed, profile = profile,
                        firstInstallTime = if (installed && profile.available) runCatching {
                            helper.getVerifiedPackageInfoAsUser(context, info.packageName, 0, profile.userId)
                                ?.takeIf { it.applicationInfo?.uid == info.uid }?.firstInstallTime?.takeIf { it > 0 }
                        }.getOrNull() else null,
                        description = PackageDescriptions.describe(info.packageName,
                            runCatching { info.loadDescription(pm)?.toString() }.getOrNull(), info.category),
                        protectedReason = when {
                            info.uid % 100000 < 10000 -> "Core Android component. Use Android Settings for this app."
                            protected[info.packageName] != null -> protected[info.packageName]
                            installed && installedPeers[info.uid].orEmpty().size > 1 -> "This app shares an Android identity with other apps in this profile. Use Android Settings."
                            profileContext == null -> "This profile's default apps could not be verified. Reconnect Shizuku and refresh."
                            else -> null
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                warnings += "${profile.name} apps unavailable: ${e.message ?: "Android denied profile access"}"
                emptyList()
            }
        }
        AppInventoryResult(PackageAssociations.enrich(entries).sortedWith(compareBy({ it.label.lowercase() }, { it.key })),
            profiles, warnings)
    }

    private fun protectedPackages(profileContext: Context?): Map<String, String> {
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
        profileContext?.let { target ->
            runCatching { target.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }
                .getOrNull()?.let { protected[it] = "This is this profile's current home screen." }
            runCatching { Settings.Secure.getString(target.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
                .getOrNull()?.substringBefore('/')?.let { protected[it] = "This is this profile's current keyboard." }
            listOfNotNull(
                runCatching { target.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull(),
                runCatching { Telephony.Sms.getDefaultSmsPackage(target) }.getOrNull(),
            ).forEach { protected[it] = "This app handles your calls or messages. Manage it in Android Settings." }
        }
        return protected
    }

    fun battery(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_STATUS) }?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val powerState = BatteryFlow.powerState(status, plugged)
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
        val current = runCatching { context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
        val flow = BatteryFlow.from(current, voltage)
        return BatteryInfo(
            percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0,100) else null,
            charging = powerState == BatteryPowerState.CHARGING || powerState == BatteryPowerState.FULL,
            powerState = powerState,
            temperatureC = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f),
            voltageMv = voltage,
            saver = context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            currentMa = flow.currentMa, powerMw = flow.powerMw,
            capturedAt = System.currentTimeMillis(),
        )
    }
}

data class AppInventoryResult(val apps: List<AppEntry>, val profiles: List<AndroidProfile>, val warnings: List<String>)
