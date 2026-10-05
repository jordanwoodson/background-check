package dev.bgcheck.network.ui

import android.content.Context
import android.content.Intent

/** Resolve the host launcher without coupling the library to its activity class or launch flags. */
object NetworkEntryIntents {
    const val EXTRA_OPEN_NETWORK = "dev.bgcheck.network.extra.OPEN_NETWORK"
    const val ACTION_OPEN_SETTINGS = "dev.bgcheck.network.OPEN_SETTINGS"

    fun create(context: Context): Intent {
        val component = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)?.component) {
            "The host app must declare a launcher activity"
        }
        return Intent().setComponent(component).putExtra(EXTRA_OPEN_NETWORK, true)
    }
}
