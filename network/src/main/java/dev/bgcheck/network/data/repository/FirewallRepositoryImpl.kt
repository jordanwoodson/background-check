package dev.bgcheck.network.data.repository

import dev.bgcheck.network.data.history.NetworkChangeHistoryStore
import dev.bgcheck.network.data.history.NetworkChangeIdentityReader
import dev.bgcheck.network.data.history.NetworkChangeTransaction
import dev.bgcheck.network.data.history.NetworkUndoPreflightException
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.NetworkChangeGuard
import dev.bgcheck.network.domain.model.NetworkChangeHistoryCodec
import dev.bgcheck.network.domain.model.NetworkChangeKind
import dev.bgcheck.network.domain.model.NetworkChangeRecord
import dev.bgcheck.network.domain.model.NetworkChangeValue
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val history: NetworkChangeHistoryStore? = null,
    private val onDataChanged: (() -> Unit)? = null
) : FirewallRepository {

    companion object {
        private const val TAG = "FirewallRepository"
    }


    private val mutationMutex = Mutex()
    private val identityReader = NetworkChangeIdentityReader(context)

    /** Checkpoint each affected target before any write. A process crash leaves visible recovery rows. */
    private suspend fun recordMutation(targets: List<PackageId>? = null, seeds: List<FirewallRule> = emptyList(), targetUserId: Int? = null,
        block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            if (history == null) {
                block()
                return@withLock
            }
            val before = firewallRuleDao.getAllRulesSync().toDomain()
            val beforeById = before.associateBy { PackageId(it.packageName, it.userId) }
            val seedsById = seeds.associateBy { PackageId(it.packageName, it.userId) }
            val ids = (targets?.toSet() ?: (beforeById.keys + seedsById.keys)).filter { targetUserId == null || it.userId == targetUserId }.toSet()
            // Metadata belongs to the pre-write installation. Never stamp a newer installation onto an old change.
            val identities = ids.associateWith { id -> runCatching { identityReader.read(id.packageName, id.userId) }.getOrNull() }
            val pending = ids.mapNotNull { id ->
                val old = beforeById[id]
                val snapshot = identities[id]
                val seed = old ?: seedsById[id]?.let { it.copy(userSerial = it.userSerial ?: snapshot?.identity?.userSerial) }
                    ?: return@mapNotNull null
                NetworkChangeRecord(
                    id = UUID.randomUUID().toString(), packageName = id.packageName, label = seed.appName,
                    userId = id.userId, uid = seed.uid, userSerial = seed.userSerial,
                    firstInstallTime = snapshot?.takeIf { it.identity.uid == seed.uid && it.identity.userSerial == seed.userSerial }?.identity?.firstInstallTime,
                    profileName = snapshot?.profileName ?: "User ${id.userId}", timestamp = System.currentTimeMillis(),
                    kind = NetworkChangeKind.FIREWALL_RULE,
                    before = NetworkChangeValue(firewallRule = old), after = NetworkChangeValue(firewallRule = old), recovery = true,
                )
            }
            val pendingById = pending.associateBy { PackageId(it.packageName, it.userId) }
            NetworkChangeTransaction(history).execute(pending, operation = block, verify = { completed ->
                val after = firewallRuleDao.getAllRulesSync().toDomain()
                val changes = NetworkChangeHistoryCodec.firewallDiff(before, after).map { (old, new) ->
                    val rule = new ?: requireNotNull(old)
                    val id = PackageId(rule.packageName, rule.userId)
                    val initial = identities[id]
                    val current = runCatching { identityReader.read(id.packageName, id.userId) }.getOrNull()
                    val matches = NetworkChangeGuard.sameInstallation(initial?.identity, current?.identity) &&
                        initial?.identity?.uid == rule.uid && initial.identity.userSerial == rule.userSerial
                    val recovery = !completed || !matches
                    NetworkChangeRecord(
                        id = pendingById[id]?.id?.takeIf { old != null || beforeById[id] == null } ?: UUID.randomUUID().toString(),
                        packageName = rule.packageName, label = rule.appName, userId = rule.userId, uid = rule.uid,
                        userSerial = rule.userSerial,
                        firstInstallTime = initial?.takeIf { it.identity.uid == rule.uid && it.identity.userSerial == rule.userSerial }?.identity?.firstInstallTime,
                        profileName = initial?.profileName ?: "User ${rule.userId}",
                        timestamp = pendingById[id]?.timestamp ?: System.currentTimeMillis(), kind = NetworkChangeKind.FIREWALL_RULE,
                        before = NetworkChangeValue(firewallRule = old), after = NetworkChangeValue(firewallRule = new),
                        recovery = recovery, recoveryReason = when {
                            !completed -> "Operation was interrupted or failed. Inspect the current saved rule."
                            !matches -> "Installation identity changed or could not be verified across the change. Inspect the current saved rule."
                            else -> null
                        },
                    )
                }
                if (!completed && changes.isEmpty()) pending.map { it.copy(recoveryReason = "Operation did not complete; inspect current saved rules.") }
                else changes
            })
        }
    }

    /** Restores saved policy only. FIREWALL_RULES_CHANGED still uses the service that owns enforcement. */
    suspend fun restoreHistoryChange(record: NetworkChangeRecord): String = mutationMutex.withLock {
        require(record.kind == NetworkChangeKind.FIREWALL_RULE)
        val snapshot = try {
            val current = identityReader.read(record.packageName, record.userId)
            val saved = firewallRuleDao.getRuleByPackageSync(record.packageName, record.userId)?.toDomain()
            NetworkChangeGuard.requireUndo(record, current.identity, current.available, NetworkChangeValue(firewallRule = saved))
            current
        } catch (error: Exception) { throw NetworkUndoPreflightException(error) }
        val previous = record.before.firewallRule
        if (previous == null) firewallRuleDao.deleteRule(record.packageName, record.userId)
        else firewallRuleDao.insertRule(validated(previous.copy(updatedAt = System.currentTimeMillis())).toEntity())
        notifyRulesChanged()
        val restored = firewallRuleDao.getRuleByPackageSync(record.packageName, record.userId)?.toDomain()
        check(record.before.sameSetting(NetworkChangeValue(firewallRule = restored))) { "Saved firewall rule restoration could not be verified" }
        val verified = identityReader.read(record.packageName, record.userId)
        check(verified.available && verified.identity == snapshot.identity) { "Installation changed while restoring; inspect the current saved rule" }
        "Saved firewall rule restored. Check firewall status for active protection."
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

    override suspend fun insertRule(rule: FirewallRule) = recordMutation(listOf(PackageId(rule.packageName, rule.userId)), listOf(rule)) {
        if (rule.packageName.contains("chrome", ignoreCase = true)) {
            AppLogger.d(TAG, "insertRule: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
            AppLogger.d(TAG, "  Stack trace:", Exception("insertRule called"))
        }
        firewallRuleDao.insertRule(validated(rule).toEntity())
        notifyRulesChanged()
    }

    override suspend fun restoreRules(rules: List<FirewallRule>, replaceExisting: Boolean) = recordMutation(
        if (replaceExisting) null else rules.map { PackageId(it.packageName, it.userId) }, rules) {
        firewallRuleDao.restoreRules(rules.map { validated(it).toEntity() }, replaceExisting)
        notifyRulesChanged()
    }

    override suspend fun insertRules(rules: List<FirewallRule>) = recordMutation(rules.map { PackageId(it.packageName, it.userId) }, rules) {
        rules.filter { it.packageName.contains("chrome", ignoreCase = true) }.forEach { rule ->
            AppLogger.d(TAG, "insertRules: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
        }
        firewallRuleDao.insertRules(rules.map { validated(it).toEntity() })
        notifyRulesChanged()
    }

    override suspend fun updateRule(rule: FirewallRule) = recordMutation(listOf(PackageId(rule.packageName, rule.userId)), listOf(rule)) {
        if (rule.packageName.contains("chrome", ignoreCase = true)) {
            AppLogger.d(TAG, "updateRule: ${rule.packageName} - wifi=${rule.wifiBlocked}, mobile=${rule.mobileBlocked}, roaming=${rule.blockWhenRoaming}")
            AppLogger.d(TAG, "  Stack trace:", Exception("updateRule called"))
        }
        firewallRuleDao.updateRule(validated(rule).toEntity())
        notifyRulesChanged()
    }

    override suspend fun deleteRule(packageName: String, userId: Int) = recordMutation(listOf(PackageId(packageName, userId))) {
        firewallRuleDao.deleteRule(packageName, userId)
        notifyRulesChanged()
    }

    override suspend fun deleteRulesByUserId(userId: Int) = recordMutation(targetUserId = userId) {
        firewallRuleDao.deleteRulesByUserId(userId)
        notifyRulesChanged()
    }

    override suspend fun deleteAllRules() = recordMutation {
        firewallRuleDao.deleteAllRules()
        notifyRulesChanged()
    }


    override suspend fun blockAllApps() = recordMutation {
        firewallRuleDao.blockAllApps(Constants.Firewall.SYSTEM_RECOMMENDED_ALLOW.toList())
        notifyRulesChanged()
    }

    override suspend fun allowAllApps() = recordMutation {
        firewallRuleDao.allowAllApps(Constants.Firewall.SYSTEM_RECOMMENDED_ALLOW.toList())
        notifyRulesChanged()
    }


    override suspend fun updateWifiBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateWifiBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateMobileBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateMobileBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateRoamingBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateRoamingBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateBackgroundBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateBackgroundBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateLanBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateLanBlocking(packageName, userId, blocked)
        notifyRulesChanged()
    }

    override suspend fun updateAllNetworkBlocking(packageName: String, userId: Int, blocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
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

    override suspend fun updateMobileAndRoaming(packageName: String, userId: Int, mobileBlocked: Boolean, roamingBlocked: Boolean) = recordMutation(listOf(PackageId(packageName, userId))) {
        val saved = firewallRuleDao.getRuleByPackageSync(packageName, userId)?.toDomain()
            ?: error("No saved rule for this profile")
        validated(saved)

        firewallRuleDao.updateMobileAndRoaming(packageName, userId, mobileBlocked, roamingBlocked)
        notifyRulesChanged()
    }
}
