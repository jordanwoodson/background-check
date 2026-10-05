package dev.bgcheck.network.utils

import android.content.Context

enum class NetworkList(val preferenceKey: String) {
    FIREWALL("firewall_compact_layout"),
    PACKAGES("packages_compact_layout"),
}

/** Each list remembers its own density; old or malformed values use the comfortable layout. */
object CompactListPreferences {
    private fun preferences(context: Context) =
        context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompact(context: Context, list: NetworkList): Boolean =
        preferences(context).all[list.preferenceKey] as? Boolean ?: false

    fun save(context: Context, list: NetworkList, compact: Boolean) {
        preferences(context).edit().putBoolean(list.preferenceKey, compact).apply()
    }
}
