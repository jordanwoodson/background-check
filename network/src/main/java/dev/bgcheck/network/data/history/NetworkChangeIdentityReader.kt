package dev.bgcheck.network.data.history

import android.content.Context
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.domain.model.NetworkInstallationIdentity

/** Do not use synthetic work-profile APK metadata as proof of an installation. */
class NetworkChangeIdentityReader(private val context: Context) {
    data class Snapshot(
        val identity: NetworkInstallationIdentity,
        val available: Boolean,
        val profileName: String,
        val label: String,
        val enabled: Boolean?,
        val enabledSetting: Int?,
    )

    fun read(packageName: String, userId: Int): Snapshot {
        val profile = HiddenApiHelper.currentProfile(context, userId) ?: error("Profile no longer exists")
        require(profile.available) { profile.unavailableReason ?: "Profile is paused, locked or unavailable" }
        val verified = HiddenApiHelper.getVerifiedPackageInfoAsUser(context, packageName, 0, userId)
        val info = verified?.applicationInfo
            ?: HiddenApiHelper.getApplicationInfoAsUser(context, packageName, 0, userId)
            ?: error("App is no longer installed in this profile")
        return Snapshot(
            identity = NetworkInstallationIdentity(info.uid, profile.serialNumber, verified?.firstInstallTime?.takeIf { it > 0 }),
            available = profile.available,
            profileName = profile.displayName,
            label = runCatching { context.packageManager.getApplicationLabel(info).toString() }.getOrDefault(packageName),
            // Synthetic ApplicationInfo may use another profile or a stale disabled-package cache.
            enabled = verified?.applicationInfo?.enabled,
            enabledSetting = verified?.applicationInfo?.let { verifiedInfo ->
                runCatching { verifiedInfo.javaClass.getField("enabledSetting").getInt(verifiedInfo) }
                    .getOrNull()?.takeIf { it in 0..4 }
            },
        )
    }
}
