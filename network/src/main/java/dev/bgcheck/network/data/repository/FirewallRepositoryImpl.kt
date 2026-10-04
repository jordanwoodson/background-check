package dev.bgcheck.network.data.repository

import dev.bgcheck.network.utils.AppLogger
import android.content.Context
import dev.bgcheck.network.data.database.dao.FirewallRuleDao
import dev.bgcheck.network.data.mapper.toDomain
import dev.bgcheck.network.data.mapper.toEntity
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.repository.FirewallRepository
import dev.bgcheck.network.utils.Constants
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirewallRepositoryImpl(
    private val firewallRuleDao: FirewallRuleDao,
    private val context: Context,
    private val onDataChanged: (() -> Unit)? = null
) : FirewallRepository {

    companion object {
        private const val TAG = "FirewallRepository"
    }

    private fun current(rule: FirewallRule): Boolean {
        val profile = HiddenApiHelper.getUsers(context).firstOrNull { it.userId == rule.userId } ?: return false
        return rule.userSerial != null && rule.userSerial == profile.serialNumber
    }

    private fun validated(rule: FirewallRule): FirewallRule {
        val profile = HiddenApiHelper.currentProfile(context, rule.userId)
            ?: error("Profile ${rule.userId} no longer exists")
        check(profile.available) { profile.unavailableReason ?: "Profile is unavailable" }
        val serial = profile.serialNumber ?: error("Cannot verify profile identity")
        check(rule.userSerial == null || rule.userSerial == serial) { "Profile identity changed; refresh the app list" }
        check(rule.uid >= 0 && rule.uid / 100000 == rule.userId) { "UID belongs to another profile" }
        return rule.copy(userSerial = serial)
    }

    private fun notifyRulesChanged() {
        val intent = android.content.Intent("dev.bgcheck.network.FIREWALL_RULES_CHANGED")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)

        onDataChanged?.invoke()
    }


    override fun getAllRules(): Flow<List<FirewallRule>> {
        return firewallRuleDao.getAllRules().map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override suspend fun getAllRulesSync(): List<FirewallRule> {
        return firewallRuleDao.getAllRulesSync().toDomain().filter(::current)
    }

    override fun getBlockedRules(): Flow<List<FirewallRule>> {
        return firewallRuleDao.getBlockedRules().map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override fun getAllowedRules(): Flow<List<FirewallRule>> {
        return firewallRuleDao.getAllowedRules().map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override fun getUserAppRules(): Flow<List<FirewallRule>> {
        return firewallRuleDao.getUserAppRules().map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override fun getSystemAppRules(): Flow<List<FirewallRule>> {
        return firewallRuleDao.getSystemAppRules().map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override fun getBlockedCount(): Flow<Int> {
        return getAllRules().map { rules -> rules.count { it.enabled && !it.isFullyAllowed() } }
    }


    override fun getRulesByUserId(userId: Int): Flow<List<FirewallRule>> {
        return firewallRuleDao.getRulesByUserId(userId).map { entities ->
            entities.toDomain().filter(::current)
        }
    }

    override suspend fun getRulesByUserIdSync(userId: Int): List<FirewallRule> {
        return firewallRuleDao.getRulesByUserIdSync(userId).toDomain().filter(::current)
    }


    override fun getRuleByPackage(packageName: String, userId: Int): Flow<FirewallRule?> {
        return firewallRuleDao.getRuleByPackage(packageName, userId).map { entity ->
            entity?.toDomain()?.takeIf(::current)
        }
    }

    override suspend fun getRuleByPackageSync(packageName: String, userId: Int): FirewallRule? {
        return firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()?.takeIf(::current)
    }

    override suspend fun insertRule(rule: FirewallRule) {
        if (rule.packageName.contains("chrome", ignoreCase = true)) {
            AppLogger.d(TAG, "insertRule: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
            AppLogger.d(TAG, "  Stack trace:", Exception("insertRule called"))
        }
        firewallRuleDao.insertRule(validated(rule).toEntity())
        notifyRulesChanged()
    }

    override suspend fun restoreRules(rules: List<FirewallRule>, replaceExisting: Boolean) {
        firewallRuleDao.restoreRules(rules.map { validated(it).toEntity() }, replaceExisting)
        notifyRulesChanged()
    }

    override suspend fun insertRules(rules: List<FirewallRule>) {
        rules.filter { it.packageName.contains("chrome", ignoreCase = true) }.forEach { rule ->
            AppLogger.d(TAG, "insertRules: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
        }
        firewallRuleDao.insertRules(rules.map { validated(it).toEntity() })
        notifyRulesChanged()
    }

    override suspend fun updateRule(rule: FirewallRule) {
        if (rule.packageName.contains("chrome", ignoreCase = true)) {
            AppLogger.d(TAG, "updateRule: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
            AppLogger.d(TAG, "  Stack trace:", Exception("updateRule called"))
        }
        firewallRuleDao.updateRule(validated(rule).toEntity())
        notifyRulesChanged()
    }

    override suspend fun deleteRule(packageName: String, userId: Int) {
        firewallRuleDao.deleteRule(packageName, userId)
        notifyRulesChanged()
    }

    override suspend fun deleteRulesByUserId(userId: Int) {
        firewallRuleDao.deleteRulesByUserId(userId)
        notifyRulesChanged()
    }

    override suspend fun deleteAllRules() {
        firewallRuleDao.deleteAllRules()
        notifyRulesChanged()
    }


    override suspend fun blockAllApps() {
        firewallRuleDao.blockAllApps(Constants.Firewall.SYSTEM_RECOMMENDED_ALLOW.toList())
        notifyRulesChanged()
    }

    override suspend fun allowAllApps() {
        firewallRuleDao.allowAllApps(Constants.Firewall.SYSTEM_RECOMMENDED_ALLOW.toList())
        notifyRulesChanged()
    }


    override suspend fun updateWifiBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateWifiBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateMobileBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateMobileBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateRoamingBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateRoamingBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateBackgroundBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateBackgroundBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateLanBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateLanBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateAllNetworkBlocking(packageName: String, userId: Int, blocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        val startTime = System.currentTimeMillis()
        AppLogger.d(TAG, "🔥 [TIMING] updateAllNetworkBlocking START: pkg=$packageName, userId=$userId, blocked=$blocked")
        firewallRuleDao.updateAllNetworkBlocking(packageName, userId, blocked)
        AppLogger.d(TAG, "🔥 [TIMING] DAO update done: +${System.currentTimeMillis() - startTime}ms")
        notifyRulesChanged()
        AppLogger.d(TAG, "🔥 [TIMING] Broadcast sent: +${System.currentTimeMillis() - startTime}ms")
    }

    override suspend fun updateMobileAndRoaming(packageName: String, userId: Int, mobileBlocked: Boolean, roamingBlocked: Boolean) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateMobileAndRoaming(packageName, userId, mobileBlocked, roamingBlocked)
        notifyRulesChanged()
    }
}
