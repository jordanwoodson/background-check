package dev.bgcheck.network.ui

import android.content.Context
import android.content.Intent
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Resolve the host launcher without coupling the library to its activity class or launch flags.
 *
 * The launcher activity is exported, so any app can send it these actions. Intents built here carry a
 * per-install secret that only this app can read; the host performs firewall operations (start, stop,
 * VPN consent) only for intents where [isTrusted] is true and treats everything else as navigation.
 */
object NetworkEntryIntents {
    const val EXTRA_OPEN_NETWORK = "dev.bgcheck.network.extra.OPEN_NETWORK"
    const val EXTRA_TOKEN = "dev.bgcheck.network.extra.ENTRY_TOKEN"
    const val ACTION_OPEN_SETTINGS = "dev.bgcheck.network.OPEN_SETTINGS"
    private const val PREFS = "network_entry"
    private const val KEY_TOKEN = "token"

    fun create(context: Context): Intent {
        val component = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)?.component) {
            "The host app must declare a launcher activity"
        }
        return Intent().setComponent(component).putExtra(EXTRA_OPEN_NETWORK, true).putExtra(EXTRA_TOKEN, token(context))
    }

    fun isTrusted(context: Context, intent: Intent): Boolean {
        val supplied = runCatching { intent.getStringExtra(EXTRA_TOKEN) }.getOrNull() ?: return false
        return MessageDigest.isEqual(supplied.toByteArray(), token(context).toByteArray())
    }

    @Synchronized
    private fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE).also {
            prefs.edit().putString(KEY_TOKEN, it).commit()
        }
    }
}
