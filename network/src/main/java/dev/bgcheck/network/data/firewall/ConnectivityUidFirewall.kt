package dev.bgcheck.network.data.firewall

import android.net.ConnectivityManager
import android.os.IBinder
import dev.bgcheck.network.data.common.ShizukuManager
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.domain.firewall.UidFirewallPort
import java.lang.reflect.InvocationTargetException

/** Uses Android's UID API. Shell's package command deliberately changes every profile. */
class ConnectivityUidFirewall private constructor(private val service: Any) : UidFirewallPort {
    private val api = Class.forName("android.net.IConnectivityManager")
    private val chain = ConnectivityManager::class.java.getField("FIREWALL_CHAIN_OEM_DENY_3").getInt(null)
        .also { check(it == 9) { "Unexpected OEM deny chain ID: $it" } }
    private val read = api.getMethod("getUidFirewallRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val write = api.getMethod("setUidFirewallRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val readEnabled = api.getMethod("getFirewallChainEnabled", Int::class.javaPrimitiveType)
    private val writeEnabled = api.getMethod("setFirewallChainEnabled", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)

    override fun getRule(uid: Int): Int = invoke { read.invoke(service, chain, uid) as Int }
    override fun setRule(uid: Int, rule: Int) { invoke { write.invoke(service, chain, uid, rule) } }
    fun isEnabled(): Boolean = invoke { readEnabled.invoke(service, chain) as Boolean }
    fun setEnabled(enabled: Boolean) {
        invoke { writeEnabled.invoke(service, chain, enabled) }
        check(isEnabled() == enabled) { "Android did not change the UID firewall chain" }
    }

    private fun <T> invoke(block: () -> T): T = try { block() }
        catch (e: InvocationTargetException) { throw (e.targetException ?: e) }

    companion object {
        suspend fun connect(manager: ShizukuManager): ConnectivityUidFirewall {
            HiddenApiHelper.initialize()
            val binder = manager.getSystemServiceBinder("connectivity")
                ?: error("Start Shizuku and grant access to use the UID firewall")
            return fromBinder(binder)
        }

        fun fromBinder(binder: IBinder): ConnectivityUidFirewall {
            HiddenApiHelper.initialize()
            val service = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            return ConnectivityUidFirewall(service).also {
                it.isEnabled()
                it.getRule(android.os.Process.myUid())
            }
        }
    }
}
