package dev.bgcheck.network.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/** The installation, not just a reusable package name or UID. Unknown metadata cannot authorize Undo. */
data class NetworkInstallationIdentity(val uid: Int, val userSerial: Long?, val firstInstallTime: Long?)

@Serializable
enum class NetworkChangeKind { FIREWALL_RULE, PACKAGE_ENABLED }

@Serializable
data class NetworkChangeValue(val firewallRule: FirewallRule? = null, val packageEnabled: Boolean? = null,
    val packageEnabledSetting: Int? = null,
    val packageState: Boolean = packageEnabled != null || packageEnabledSetting != null) {
    fun summary(): String {
        if (packageState) {
            val effective = packageEnabled?.let { if (it) "Enabled" else "Disabled" } ?: "Effective state unavailable"
            return "$effective · ${PackageEnabledSetting.label(packageEnabledSetting)}"
        }
        val rule = firewallRule ?: return "No saved rule (uses default policy)"
        fun access(blocked: Boolean) = if (blocked) "blocked" else "allowed"
        return "Rule ${if (rule.enabled) "enabled" else "disabled"}; " +
            "Wi-Fi ${access(rule.wifiBlocked)}; Mobile ${access(rule.mobileBlocked)}; " +
            "Roaming ${access(rule.blockWhenRoaming)}; LAN ${access(rule.lanBlocked)}; " +
            "Background block ${if (rule.blockWhenBackground) "on" else "off"}"
    }

    /** Names and write timestamps may change without changing the saved policy. */
    fun sameSetting(other: NetworkChangeValue): Boolean = packageEnabled == other.packageEnabled &&
        packageEnabledSetting == other.packageEnabledSetting && packageState == other.packageState &&
        firewallRule?.settingKey() == other.firewallRule?.settingKey()
}

private fun FirewallRule.settingKey(): List<Any?> = listOf(packageName, userId, uid, userSerial,
    wifiBlocked, mobileBlocked, blockWhenRoaming, lanBlocked, blockWhenBackground, enabled)

@Serializable
data class NetworkChangeRecord(
    val id: String,
    val packageName: String,
    val label: String,
    val userId: Int,
    val uid: Int,
    val userSerial: Long?,
    val firstInstallTime: Long?,
    val profileName: String,
    val timestamp: Long,
    val kind: NetworkChangeKind,
    val before: NetworkChangeValue,
    val after: NetworkChangeValue,
    val undone: Boolean = false,
    val recovery: Boolean = false,
    val recoveryReason: String? = null,
    val description: String = when (kind) {
        NetworkChangeKind.FIREWALL_RULE -> "Saved firewall rule changed"
        NetworkChangeKind.PACKAGE_ENABLED -> "Package enabled state changed"
    },
) {
    val canUndo: Boolean get() = !undone && !recovery && userSerial != null && firstInstallTime != null &&
        (kind != NetworkChangeKind.PACKAGE_ENABLED || before.packageEnabledSetting != null && after.packageEnabledSetting != null)
    val identity: NetworkInstallationIdentity get() = NetworkInstallationIdentity(uid, userSerial, firstInstallTime)
}

object NetworkChangeGuard {
    fun sameInstallation(before: NetworkInstallationIdentity?, after: NetworkInstallationIdentity?): Boolean =
        before != null && after != null && before.userSerial != null && before.firstInstallTime != null && before == after

    fun requireUndo(record: NetworkChangeRecord, currentIdentity: NetworkInstallationIdentity,
        available: Boolean, currentValue: NetworkChangeValue) {
        require(record.canUndo) { "This change cannot be undone: already restored or installation identity unavailable" }
        require(available) { "Profile is paused, locked or unavailable" }
        require(record.userSerial == currentIdentity.userSerial) { "Profile identity changed; Undo cancelled" }
        require(record.uid >= 0 && record.uid / 100000 == record.userId && record.uid == currentIdentity.uid &&
            record.firstInstallTime == currentIdentity.firstInstallTime) { "App installation changed; Undo cancelled" }
        require(record.after.sameSetting(currentValue)) { "Current settings differ from this change; Undo cancelled" }
    }
}

/** Local history and export use the same lossless JSON. Corrupt entries do not discard valid neighbours. */
object NetworkChangeHistoryCodec {
    const val MAX_RECORDS = 500
    private const val MAX_RECOVERY_RECORDS = 10_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")

    fun retained(records: List<NetworkChangeRecord>): List<NetworkChangeRecord> {
        require(records.count { it.recovery } <= MAX_RECOVERY_RECORDS) { "Too many unresolved changes; no further changes were started" }
        var ordinary = 0
        return records.filter { it.recovery || ordinary++ < MAX_RECORDS }
    }

    fun encode(records: List<NetworkChangeRecord>): String = json.encodeToString(retained(records))

    fun decode(value: String?): List<NetworkChangeRecord> = runCatching {
        val array = json.parseToJsonElement(value ?: "[]") as JsonArray
        require(array.size <= MAX_RECORDS + MAX_RECOVERY_RECORDS)
        retained(array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<NetworkChangeRecord>(element).also(::validate) }.getOrNull()
        }.distinctBy { it.id })
    }.getOrDefault(emptyList())

    private fun validate(record: NetworkChangeRecord) {
        require(record.id.isNotBlank() && record.packageName.length <= 255 && packagePattern.matches(record.packageName))
        require(record.userId >= 0 && record.uid >= 0 && record.uid / 100000 == record.userId)
        require(record.userSerial == null || record.userSerial >= 0)
        require(record.firstInstallTime == null || record.firstInstallTime > 0)
        require(record.timestamp >= 0)
        for (value in listOf(record.before, record.after)) {
            when (record.kind) {
                NetworkChangeKind.PACKAGE_ENABLED -> {
                    require(value.firewallRule == null)
                    require(value.packageEnabledSetting == null || value.packageEnabledSetting in 0..4)
                }
                NetworkChangeKind.FIREWALL_RULE -> {
                    require(value.packageEnabled == null && value.packageEnabledSetting == null && !value.packageState)
                    value.firewallRule?.let { rule ->
                        require(rule.packageName == record.packageName && rule.userId == record.userId &&
                            rule.uid == record.uid && rule.userSerial == record.userSerial)
                    }
                }
            }
        }
        require(record.recovery || !record.before.sameSetting(record.after))
    }

    /** Include removals during replace-backup and reset, and additions during initial/default policy stamping. */
    fun firewallDiff(before: List<FirewallRule>, after: List<FirewallRule>): List<Pair<FirewallRule?, FirewallRule?>> {
        val previous = before.associateBy { PackageId(it.packageName, it.userId) }
        val current = after.associateBy { PackageId(it.packageName, it.userId) }
        return (previous.keys + current.keys).flatMap { id ->
            val old = previous[id]
            val new = current[id]
            when {
                NetworkChangeValue(firewallRule = old).sameSetting(NetworkChangeValue(firewallRule = new)) -> emptyList()
                old != null && new != null && (old.uid != new.uid || old.userSerial != new.userSerial) ->
                    listOf(old to null, null to new)
                else -> listOf(old to new)
            }
        }
    }
}

/** Android's enabled override is distinct from the manifest's effective enabled state. */
object PackageEnabledSetting {
    fun command(setting: Int): String = when (setting) {
        0 -> "default-state"
        1 -> "enable"
        2 -> "disable"
        3 -> "disable-user"
        4 -> "disable-until-used"
        else -> throw IllegalArgumentException("Invalid package enabled setting")
    }
    fun label(setting: Int?): String = when (setting) {
        0 -> "Manifest default"
        1 -> "Explicitly enabled"
        2 -> "Disabled"
        3 -> "Disabled by user"
        4 -> "Disabled until used"
        else -> "Enabled override unavailable"
    }
}
