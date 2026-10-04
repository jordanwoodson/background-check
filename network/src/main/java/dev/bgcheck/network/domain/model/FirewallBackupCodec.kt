package dev.bgcheck.network.domain.model

import kotlinx.serialization.json.Json

/** Imported identities reach privileged lookups, so validate them before touching device state. */
object FirewallBackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")

    fun decode(json: String): FirewallRulesBackup {
        require(json.length <= MAX_BYTES) { "Backup is too large." }
        val backup = Json.decodeFromString<FirewallRulesBackup>(json)
        require(backup.version == 1) { "Unsupported backup version: ${backup.version}" }
        require(backup.rules.size <= 10_000 && backup.rulesCount == backup.rules.size) { "Invalid rule count." }
        require(backup.rules.map { it.userId to it.packageName }.distinct().size == backup.rules.size) { "Duplicate rules in backup." }
        backup.rules.forEach { rule ->
            require(rule.packageName.length <= 255 && packagePattern.matches(rule.packageName)) { "Invalid package name in backup." }
            require(rule.userId in 0..21473 && rule.uid >= -1) { "Invalid app identity in backup." }
            require(rule.appName.length <= 1024) { "App name is too long." }
        }
        return backup
    }
}
