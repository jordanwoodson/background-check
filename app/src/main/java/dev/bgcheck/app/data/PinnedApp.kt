package dev.bgcheck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A pin follows an installation, never just a reusable package or user ID. */
data class PinnedApp(val packageName: String, val uid: Int, val userSerial: Long, val firstInstallTime: Long) {
    fun matches(app: AppEntry): Boolean = app.installed && app.packageName == packageName && app.uid == uid &&
        app.profile.serialNumber == userSerial && app.firstInstallTime == firstInstallTime

    companion object {
        fun from(app: AppEntry): PinnedApp? {
            if (!app.installed || app.uid < 0 || app.profile.userId != app.userId) return null
            return PinnedApp(app.packageName, app.uid, app.profile.serialNumber?.takeIf { it >= 0 } ?: return null,
                app.firstInstallTime?.takeIf { it > 0 } ?: return null)
        }
        fun encode(pins: List<PinnedApp>): String = JSONArray().apply {
            pins.distinct().forEach { pin -> put(JSONObject().apply {
                put("package", pin.packageName); put("uid", pin.uid)
                put("serial", pin.userSerial); put("installed", pin.firstInstallTime)
            }) }
        }.toString()
        fun decode(value: String?): List<PinnedApp> = runCatching {
            val json = JSONArray(value ?: "[]")
            (0 until json.length().coerceAtMost(500)).mapNotNull { index -> runCatching {
                val item = json.getJSONObject(index)
                PinnedApp(item.getString("package"), item.getInt("uid"), item.getLong("serial"), item.getLong("installed"))
                    .also { require(ShellParsers.validPackage(it.packageName) && it.uid >= 0 && it.userSerial >= 0 && it.firstInstallTime > 0) }
            }.getOrNull() }.distinct()
        }.getOrDefault(emptyList())
    }
}
