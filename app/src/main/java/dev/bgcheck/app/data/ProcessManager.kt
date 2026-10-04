package dev.bgcheck.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StopResult(val packageName: String, val stopped: Boolean, val message: String)

/** Stops a single package for one Android user; a successful command alone is never verification. */
class ProcessManager(private val execute: suspend (List<String>) -> String, private val userId: Int) {
    private val mutations = Mutex()
    private val identities = PackageIdentityGuard(execute, userId)

    suspend fun forceStop(app: AppEntry): StopResult = mutations.withLock {
        var commandStarted = false
        var commandCompleted = false
        try {
            validateLocalIdentity(app)
            identities.validate(app)
            commandStarted = true
            val output = execute(listOf("am", "force-stop", "--user", userId.toString(), app.packageName))
            check(output.isBlank()) { "Android returned an unexpected force-stop response: ${output.trim().take(300)}" }
            commandCompleted = true

            // An uninstall, reinstall, or shared-UID change must not turn absence into a success claim.
            identities.validate(app)
            val stopped = packageStopped(execute(listOf("dumpsys", "package", app.packageName)), app.packageName)
            val processes = ShellParsers.processes(execute(listOf("dumpsys", "activity", "processes")), userId)
            check(stopped) { "Android does not report the package as stopped for this user" }
            check(app.packageName !in processes) { "Android still reports a process for this app; it may have restarted" }
            StopResult(app.packageName, true, "Force-stop verified: Android reports the app stopped and no app process was observed. Opening the app can start it again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: "Android command failed"
            val message = when {
                commandCompleted -> "Force-stop was sent, but stopping could not be verified: $reason. Refresh to inspect the current state."
                commandStarted -> "Force-stop could not be verified: $reason. Refresh to inspect the current state."
                else -> "Force-stop was not sent: $reason."
            }
            StopResult(app.packageName, false, message)
        }
    }

    private fun validateLocalIdentity(app: AppEntry) {
        check(app.installed) { "This package is not installed for this Android user" }
        require(userId >= 0 && ShellParsers.validPackage(app.packageName)) { "Invalid package or Android user" }
        require(app.uid >= 0 && app.uid / 100000 == userId) { "The app belongs to another Android user" }
        check(app.profile.userId == userId) { "The profile does not match this app's Android user" }
        check(app.profile.available) { app.profile.unavailableReason ?: "This Android profile is unavailable" }
        check(app.protectedReason == null) { app.protectedReason ?: "This app is protected" }
        check(!app.system) { "System apps cannot be force-stopped here" }
        check(app.uid % 100000 >= 10000 && app.packageName !in protectedPackages) { "This essential app is protected" }
    }

    /** Read only the active Packages section; hidden system copies and other users are not evidence. */
    private fun packageStopped(output: String, packageName: String): Boolean {
        var inPackages = false
        var inPackage = false
        var matchingPackages = 0
        var stopped: Boolean? = null
        output.lineSequence().forEach { line ->
            if (line == "Packages:") {
                check(!inPackages && matchingPackages == 0) { "Android returned duplicate package sections" }
                inPackages = true
            } else if (line.isNotBlank() && !line.first().isWhitespace()) {
                inPackages = false
                inPackage = false
            } else if (inPackages && line.isNotBlank()) {
                val header = packageHeader.matchEntire(line)
                if (header != null) {
                    inPackage = header.groupValues[1] == packageName
                    if (inPackage) matchingPackages++
                } else if (line.takeWhile(Char::isWhitespace).length <= 2) {
                    inPackage = false
                } else if (inPackage) {
                    val user = packageUser.matchEntire(line.trim())
                    if (user != null && user.groupValues[1].toIntOrNull() == userId) {
                        check(stopped == null) { "Android returned duplicate user package state" }
                        val fields = user.groupValues[2].split(Regex("\\s+"))
                        check(fields.count { it.startsWith("installed=") } == 1 && fields.count { it.startsWith("stopped=") } == 1) {
                            "Android returned incomplete user package state"
                        }
                        check("installed=true" in fields) { "The app is no longer installed for this user" }
                        stopped = when {
                            "stopped=true" in fields -> true
                            "stopped=false" in fields -> false
                            else -> error("Android returned an unrecognized stopped flag")
                        }
                    }
                }
            }
        }
        check(matchingPackages == 1 && stopped != null) { "Android did not return the app's stopped flag for this user" }
        return requireNotNull(stopped)
    }

    private companion object {
        val packageHeader = Regex("  Package \\[([^]]+)\\] \\([^)]+\\):")
        val packageUser = Regex("User (\\d+):\\s+(.+)")
        val protectedPackages = setOf("android", "dev.bgcheck.app", "moe.shizuku.privileged.api", "com.android.systemui", "com.android.phone", "com.android.settings", "com.android.shell")
    }
}

/** Recheck the real, full Android UID immediately around mutations, including rollback writes. */
internal class PackageIdentityGuard(private val execute: suspend (List<String>) -> String, private val userId: Int) {
    suspend fun validate(app: AppEntry) {
        val command = listOf("pm", "list", "packages", "-U", "--user", userId.toString())
        val all = packageUids(execute(command))
        check(all[app.packageName] == app.uid) { "The app identity changed or the app is no longer installed. Refresh the app list" }
        check(all.count { it.value == app.uid } == 1) { "This app shares its Android UID with another package and cannot be managed independently" }
        // pm's positional package argument is a substring filter, so it still needs an exact match.
        val filtered = packageUids(execute(command + app.packageName))
        check(filtered[app.packageName] == app.uid) { "The app identity changed or the app is no longer installed. Refresh the app list" }
        check(filtered.count { it.value == app.uid } == 1) { "This app shares its Android UID with another package and cannot be managed independently" }
    }

    private fun packageUids(output: String): Map<String, Int> = buildMap {
        output.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            val match = packageUid.matchEntire(line)
            require(match != null && ShellParsers.validPackage(match.groupValues[1])) { "Android returned an unrecognized package identity list" }
            val uid = match.groupValues[2].toIntOrNull()
            require(uid != null && uid >= 0 && uid / 100000 == userId) { "Android returned an invalid package UID for this user" }
            val packageName = match.groupValues[1]
            require(!containsKey(packageName)) { "Android returned duplicate or conflicting package identities" }
            put(packageName, uid)
        }
    }

    private companion object {
        val packageUid = Regex("package:([^\\s]+)\\s+uid:(\\d+)")
    }
}
