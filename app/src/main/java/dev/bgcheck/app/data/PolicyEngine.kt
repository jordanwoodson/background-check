package dev.bgcheck.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Mutations affect only the selected mechanism; snapshots never infer policy from process state. */
class PolicyEngine(private val execute: suspend (List<String>) -> String, private val userId: Int) {
    private val mutations = Mutex()

    suspend fun snapshot(): Snapshot {
        val warnings = mutableListOf<String>()
        suspend fun <T> attempt(label: String, block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { warnings += "$label unavailable: ${e.message ?: "Android command failed"}"; null }
        val processes = attempt("Process states") { ShellParsers.processes(execute(listOf("dumpsys", "activity", "processes")), userId) }
        val buckets = attempt("Standby buckets") { ShellParsers.buckets(execute(listOf("am", "get-standby-bucket", "--user", userId.toString()))) }
        val exemptions = attempt("Doze exemptions") { ShellParsers.allowlist(execute(allowlistCommand)) }
        return Snapshot(
            processes = processes.orEmpty(), buckets = buckets.orEmpty(),
            exemptPackages = exemptions?.exempt.orEmpty(), exemptAppIds = exemptions?.exemptAppIds.orEmpty(),
            processesKnown = processes != null, exemptionsKnown = exemptions != null, warnings = warnings,
        )
    }

    suspend fun read(app: AppEntry): PolicyState {
        validateIdentity(app)
        val errors = mutableListOf<String>()
        suspend fun <T> attempt(label: String, block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { errors += "$label: ${e.message ?: "read failed"}"; null }
        val background = attempt("Background access") { readOp(app, backgroundOp) }
        val legacy = if (app.targetSdk < 26) attempt("Legacy background access") { readOp(app, legacyOp) } else null
        val exemptions = attempt("Doze exemption") { ShellParsers.allowlist(execute(allowlistCommand)) }
        val bucket = attempt("Standby bucket") { ShellParsers.bucket(execute(listOf("am", "get-standby-bucket", "--user", userId.toString(), app.packageName))) }
        return PolicyState(
            packageName = app.packageName,
            backgroundMode = background?.mode, legacyMode = legacy?.mode,
            dozeExempt = exemptions?.let { app.packageName in it.exempt || app.uid % 100000 in it.exemptAppIds },
            systemExempt = exemptions?.let { app.packageName in it.system || app.uid % 100000 in it.systemAppIds } ?: false,
            userExempt = exemptions?.let { app.packageName in it.user },
            uidOverride = background?.uidOverride == true || legacy?.uidOverride == true,
            standbyBucket = bucket, error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    suspend fun apply(
        app: AppEntry,
        before: PolicyState,
        action: ControlAction,
        beforeWrite: suspend (PolicyState, PolicyState) -> Unit = { _, _ -> },
    ): PolicyState = mutations.withLock {
        validateMutable(app, before)
        val current = read(app)
        validateMutable(app, current)
        check(samePolicy(before, current)) { "Policy changed since it was read. Refresh and try again." }
        val desired = desiredPolicy(app, current, action)
        beforeWrite(current, desired)
        change(app, current, desired)
    }

    /** The app persists this target before sending the first mutating command. */
    fun desiredPolicy(app: AppEntry, before: PolicyState, action: ControlAction): PolicyState {
        validateMutable(app, before)
        if (action == ControlAction.OPTIMIZE_DOZE) check(!before.systemExempt) { "Android manages this system exemption; it cannot be removed here." }
        return when (action) {
            ControlAction.ALLOW_BACKGROUND -> before.copy(backgroundMode = "allow", legacyMode = if (app.targetSdk < 26) "allow" else null)
            ControlAction.RESTRICT_BACKGROUND -> before.copy(backgroundMode = "ignore", legacyMode = if (app.targetSdk < 26) "ignore" else null)
            ControlAction.EXEMPT_DOZE -> before.copy(userExempt = true, dozeExempt = true)
            ControlAction.OPTIMIZE_DOZE -> before.copy(userExempt = false, dozeExempt = false)
        }
    }

    /** Recovery accepts only the original state, intended state, or a partial application of it. */
    suspend fun recover(app: AppEntry, before: PolicyState, intended: PolicyState): PolicyState = mutations.withLock {
        validateMutable(app, before)
        validateMutable(app, intended)
        val actual = read(app)
        validateMutable(app, actual)
        check(compatiblePartial(actual, before, intended)) {
            "Policy changed independently of the interrupted action. Refresh instead of overwriting that change."
        }
        change(app, actual, before)
    }

    suspend fun restore(
        app: AppEntry,
        before: PolicyState,
        expectedCurrent: PolicyState,
        beforeWrite: suspend () -> Unit = {},
    ): PolicyState = mutations.withLock {
        validateMutable(app, before)
        validateMutable(app, expectedCurrent)
        val current = read(app)
        validateMutable(app, current)
        check(samePolicy(current, expectedCurrent)) { "Policy changed after this action. Refresh instead of undoing an outdated change." }
        check(current.systemExempt == before.systemExempt) { "Android's system exemption changed; this action cannot be undone safely." }
        beforeWrite()
        change(app, current, before)
    }

    private suspend fun change(app: AppEntry, before: PolicyState, desired: PolicyState): PolicyState {
        if (samePolicy(before, desired)) return before
        try {
            writeDifferences(app, before, desired)
            val actual = read(app)
            validateMutable(app, actual)
            check(samePolicy(actual, desired)) { "Android did not report the requested policy after the command." }
            return actual
        } catch (failure: Exception) {
            val recovery = withContext(NonCancellable) { rollback(app, before, desired) }
            if (failure is CancellationException) throw failure
            throw IllegalStateException("${failure.message ?: "Change failed"} $recovery", failure)
        }
    }

    private suspend fun rollback(app: AppEntry, before: PolicyState, desired: PolicyState): String = try {
        val actual = read(app)
        validateMutable(app, actual)
        check(compatiblePartial(actual, before, desired)) { "Policy changed independently" }
        writeDifferences(app, actual, before)
        val restored = read(app)
        check(restored.error == null && samePolicy(restored, before)) { "Restore could not be verified" }
        "Previous policy is restored and verified."
    } catch (_: Exception) {
        "The previous policy could not be safely restored. Refresh to inspect Android's current state."
    }

    private suspend fun writeDifferences(app: AppEntry, from: PolicyState, to: PolicyState) {
        if (from.backgroundMode != to.backgroundMode) writeOp(app, backgroundOp, requireNotNull(to.backgroundMode))
        if (app.targetSdk < 26 && from.legacyMode != to.legacyMode) writeOp(app, legacyOp, requireNotNull(to.legacyMode))
        if (from.userExempt != to.userExempt) {
            check(!(to.userExempt == false && from.systemExempt && to.dozeExempt == false)) { "Cannot remove a system exemption" }
            execute(allowlistCommand + "${if (to.userExempt == true) "+" else "-"}${app.packageName}")
        }
    }

    private suspend fun readOp(app: AppEntry, op: String): ShellParsers.AppOp =
        ShellParsers.appOp(execute(listOf("cmd", "appops", "get", "--user", userId.toString(), app.packageName, op)), op)

    private suspend fun writeOp(app: AppEntry, op: String, mode: String) {
        check(mode in writableModes) { "This Android app-op mode cannot be changed safely" }
        execute(listOf("cmd", "appops", "set", "--user", userId.toString(), app.packageName, op, mode))
    }

    private fun validateIdentity(app: AppEntry) {
        require(userId >= 0 && ShellParsers.validPackage(app.packageName)) { "Invalid package or Android user" }
        require(app.uid >= 0 && app.uid / 100000 == userId) { "The app belongs to another Android user" }
    }

    private fun validateMutable(app: AppEntry, state: PolicyState) {
        validateIdentity(app)
        check(state.packageName == app.packageName) { "Policy belongs to another app" }
        check(app.protectedReason == null) { app.protectedReason ?: "This app is protected" }
        check(app.uid % 100000 >= 10000 && app.packageName !in protectedPackages) { "This essential app is protected" }
        check(!state.uidOverride) { "A shared UID app-op override controls this app. Manage it in Android settings." }
        check(state.error == null && state.backgroundMode in writableModes && state.dozeExempt != null && state.userExempt != null &&
            (app.targetSdk >= 26 || state.legacyMode in writableModes)) { "Read the complete current policy before making changes." }
    }

    private fun samePolicy(a: PolicyState, b: PolicyState): Boolean =
        a.packageName == b.packageName && a.backgroundMode == b.backgroundMode && a.legacyMode == b.legacyMode &&
            a.dozeExempt == b.dozeExempt && a.userExempt == b.userExempt && a.systemExempt == b.systemExempt && a.uidOverride == b.uidOverride

    private fun compatiblePartial(actual: PolicyState, before: PolicyState, desired: PolicyState): Boolean =
        actual.packageName == before.packageName && actual.systemExempt == before.systemExempt && actual.uidOverride == before.uidOverride &&
            actual.backgroundMode in setOf(before.backgroundMode, desired.backgroundMode) &&
            actual.legacyMode in setOf(before.legacyMode, desired.legacyMode) &&
            actual.userExempt in setOf(before.userExempt, desired.userExempt) &&
            actual.dozeExempt in setOf(before.dozeExempt, desired.dozeExempt)

    private companion object {
        const val backgroundOp = "RUN_ANY_IN_BACKGROUND"
        const val legacyOp = "RUN_IN_BACKGROUND"
        val allowlistCommand = listOf("cmd", "deviceidle", "whitelist")
        val writableModes = setOf("allow", "ignore", "deny", "default")
        val protectedPackages = setOf("android", "dev.bgcheck.app", "moe.shizuku.privileged.api", "com.android.systemui", "com.android.phone", "com.android.settings", "com.android.shell")
    }
}
