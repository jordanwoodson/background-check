package dev.bgcheck.network.data.firewall

import dev.bgcheck.network.utils.AppLogger
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.bgcheck.network.data.common.ErrorHandler
import dev.bgcheck.network.data.common.ShizukuManager
import dev.bgcheck.network.data.service.PrivilegedFirewallService
import dev.bgcheck.network.domain.firewall.FirewallBackend
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.PartialApplyException
import dev.bgcheck.network.domain.firewall.rulesByCurrentUid
import dev.bgcheck.network.domain.firewall.uidBlockedNow
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.model.NetworkType
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConnectivityManagerFirewallBackend(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val errorHandler: ErrorHandler
) : FirewallBackend {

    companion object {
        private const val TAG = "ConnectivityManagerFirewall"
        private const val SERVICE_NAME = "connectivity"
        private const val MIN_API_LEVEL = Build.VERSION_CODES.TIRAMISU // Android 13
        private const val UID_JOURNAL = "cm_uid_original_rules"
        private const val UID_MODE = "cm_independent_uid_mode"
        private const val ORIGINAL_CHAIN = "cm_uid_original_chain_enabled"

        /**
         * How long the WHOLE restore may take, not one command of it.
         *
         * ShellRunner bounds each command, but [restoreBlockedPackages] issues one per denied
         * package, so N packages simply bought N ceilings and the job itself stayed unbounded.
         *
         * Sixty seconds is far above the honest cost - `cmd connectivity` answers in tens of
         * milliseconds, so even several hundred packages finish in seconds - and it only ever bites
         * when something is already wrong. Anything not reached comes back as failed, which keeps it
         * in the durable record, so the next stop retries it. The loop was already built to resume.
         */
        private const val RESTORE_DEADLINE_MS = 60_000L

        // Android's exit-255 answer to an enable when the app holds no denial: the goal state, not a failure.
        private const val NO_DENIAL_ANSWER = "sUidOwnerMap does not have entry for uid"

        /**
         * Process-wide, NOT per-instance.
         *
         * Everything this backend guards - the OEM_DENY_3 chain, the system's per-uid denials,
         * the on-disk record - is shared by every instance. FirewallManager.cleanupAllBackends()
         * builds a second instance while the privileged service may still be inside applyRules, and
         * a per-instance lock let the sweep restore every package and write an empty record while
         * that apply re-denied them, leaving apps offline with the firewall off.
         */
        private val mutex = Mutex()
    }

    private val appliedPolicies = mutableMapOf<String, Boolean>()

    override suspend fun start(): Result<Unit> = mutex.withLock {
        return try {
            AppLogger.d(TAG, "=== ConnectivityManagerFirewallBackend.start() ===")
            AppLogger.d(TAG, "Starting PrivilegedFirewallService with ConnectivityManager backend")

            val intent = Intent(context, PrivilegedFirewallService::class.java).apply {
                action = PrivilegedFirewallService.ACTION_START
                putExtra(PrivilegedFirewallService.EXTRA_BACKEND_TYPE, "CONNECTIVITY_MANAGER")
            }
            context.startService(intent)

            AppLogger.d(TAG, "✅ ConnectivityManager firewall service started")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ConnectivityManager firewall", e)
            Result.failure(errorHandler.handleError(e, "start ConnectivityManager firewall"))
        }
    }

    suspend fun startInternal(): Result<Unit> = mutex.withLock {
        runCatching {
            val controller = ConnectivityUidFirewall.connect(shizukuManager)
            // An upgrade can still have denials from the package-based backend. Lift those before
            // the first UID rule; otherwise the old personal verdict continues blocking work apps.
            check(restoreBlockedPackages().isEmpty()) { "Could not restore legacy package rules" }
            check(context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(UID_MODE, true).commit()) { "Cannot record firewall mode" }
            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            val wasEnabled = controller.isEnabled()
            if (!wasEnabled) {
                check(readDeniedUids().isEmpty()) {
                    "OEM deny chain contains another controller's inactive rules. Choose root or a VPN in the target profile."
                }
            }
            if (!prefs.contains(ORIGINAL_CHAIN)) {
                check(prefs.edit().putBoolean(ORIGINAL_CHAIN, wasEnabled).commit()) { "Cannot record original chain state" }
            }
            controller.setEnabled(true)
        }.map { Unit }
    }

    override suspend fun stop(): Result<Unit> = mutex.withLock {
        return try {
            AppLogger.d(TAG, "Stopping ConnectivityManager firewall backend")
            AppLogger.d(TAG, "Stopping PrivilegedFirewallService")

            val intent = Intent(context, PrivilegedFirewallService::class.java).apply {
                action = PrivilegedFirewallService.ACTION_STOP
                // Name the backend. The service holds ONE currentBackend, so an unqualified stop
                // tears down whatever it happens to be running - which during a switch is the
                // backend that was just STARTED, not this one.
                putExtra(PrivilegedFirewallService.EXTRA_BACKEND_TYPE, "CONNECTIVITY_MANAGER")
            }
            context.startService(intent)

            AppLogger.d(TAG, "ConnectivityManager firewall service stopped successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop ConnectivityManager firewall", e)
            Result.failure(errorHandler.handleError(e, "stop ConnectivityManager firewall"))
        }
    }

    suspend fun stopInternal(): Result<Unit> = mutex.withLock {
        runCatching {
            restoreUidPolicies()
            check(restoreBlockedPackages().isEmpty()) { "Could not restore legacy package rules" }
            appliedPolicies.clear()
            restoreChainState()
            setChain3EnabledByUs(false)
        }
    }

    override suspend fun applyRules(
        rules: List<FirewallRule>,
        networkType: NetworkType,
        screenOn: Boolean
    ): Result<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val controller = ConnectivityUidFirewall.connect(shizukuManager)
                check(controller.isEnabled()) { "UID firewall chain is disabled" }
                val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
                val blockAll = prefs.getString(Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                    Constants.Settings.DEFAULT_FIREWALL_POLICY) == Constants.Settings.POLICY_BLOCK_ALL
                val allowCritical = prefs.getBoolean(Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
                    Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL)
                val installed = dev.bgcheck.network.data.multiuser.HiddenApiHelper.getInstalledUids(context)
                check(installed.isNotEmpty() && installed.values.none { it.isEmpty() }) {
                    "Cannot verify every profile; UID firewall rules were not changed"
                }
                val byUid = rulesByCurrentUid(rules, installed)
                val packages = dev.bgcheck.network.data.multiuser.HiddenApiHelper.getPackagesWithNetworkPermissions(context)
                check(packages.isNotEmpty()) { "Cannot read network apps" }
                val critical = packages.filter {
                    Constants.Firewall.isSystemCritical(it.packageName) || hasVpnService(it.packageName, it.uid / 100000)
                }.mapTo(hashSetOf()) { it.uid }
                val denied = packages.groupBy { it.uid }.mapNotNull { (uid, _) ->
                    if (!Constants.Firewall.isFirewallableAppUid(uid)) return@mapNotNull null
                    val protected = uid in critical
                    if (protected && !allowCritical) return@mapNotNull null
                    uid.takeIf { uidBlockedNow(byUid[uid], blockAll, protected, false, networkType, screenOn) }
                }.toSet()
                uidSession(controller).apply(denied)
            }
        }
    }

    private fun uidSession(controller: ConnectivityUidFirewall) = dev.bgcheck.network.domain.firewall.UidFirewallSession(
        controller,
        load = {
            context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(UID_JOURNAL, emptySet()).orEmpty().associate { record ->
                    val parts = record.split(":")
                    require(parts.size == 2) { "Invalid UID firewall recovery record" }
                    val uid = parts[0].toInt()
                    val rule = parts[1].toInt()
                    require(uid >= 0 && rule in 0..1) { "Invalid UID firewall recovery identity" }
                    uid to rule
                }
        },
        save = { journal ->
            check(context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putStringSet(UID_JOURNAL, journal.map { "${it.key}:${it.value}" }.toSet()).commit()) {
                "Cannot save UID firewall recovery record"
            }
        }
    )

    private suspend fun readDeniedUids(): Set<Int> {
        val (code, output) = shizukuManager.executeShellCommand("dumpsys connectivity trafficcontroller")
        check(code == 0) { "Cannot inspect Android OEM firewall rules" }
        return dev.bgcheck.network.domain.firewall.OemDenyChainRules.parse(output)
    }

    private suspend fun restoreChainState() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(ORIGINAL_CHAIN)) return
        if (!prefs.getBoolean(ORIGINAL_CHAIN, true)) {
            // A complete owner-map snapshot includes isolated UIDs absent from PackageManager.
            // If an OEM added a denial meanwhile, leave its chain running and preserve that rule.
            if (readDeniedUids().isEmpty()) ConnectivityUidFirewall.connect(shizukuManager).setEnabled(false)
        }
        check(prefs.edit().remove(ORIGINAL_CHAIN).commit()) { "Cannot save restored chain state" }
    }

    private suspend fun restoreUidPolicies() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getStringSet(UID_JOURNAL, emptySet()).orEmpty().isNotEmpty()) {
            uidSession(ConnectivityUidFirewall.connect(shizukuManager)).restore()
        }
    }

    override fun isActive(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            val isServiceRunning = prefs.getBoolean(Constants.Settings.KEY_PRIVILEGED_SERVICE_RUNNING, false)
            val backendType = prefs.getString(Constants.Settings.KEY_PRIVILEGED_BACKEND_TYPE, null)

            if (!isServiceRunning || backendType != "CONNECTIVITY_MANAGER") {
                return false
            }

            // SharedPreferences says service is running, but verify the service is actually alive
            // This is important after app reinstall (e.g., dev.sh update) where SharedPreferences
            // persist but the service process is killed
            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (activityManager != null) {
                @Suppress("DEPRECATION")
                val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                val serviceClassName = "dev.bgcheck.network.data.service.PrivilegedFirewallService"
                val isServiceActuallyRunning = runningServices.any { service ->
                    service.service.className == serviceClassName
                }

                if (!isServiceActuallyRunning) {
                    AppLogger.w(TAG, "SharedPreferences says privileged service is running, but service is not actually running. Clearing flags.")
                    prefs.edit()
                        .putBoolean(Constants.Settings.KEY_PRIVILEGED_SERVICE_RUNNING, false)
                        .remove(Constants.Settings.KEY_PRIVILEGED_BACKEND_TYPE)
                        .apply()
                    return false
                }

                return true
            }

            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check if ConnectivityManager firewall is active", e)
            false
        }
    }

    override fun getType(): FirewallBackendType = FirewallBackendType.CONNECTIVITY_MANAGER

    override suspend fun checkAvailability(): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
                val error = "ConnectivityManager firewall requires Android 13+"
                return Result.failure(Exception(error))
            }

            if (!shizukuManager.hasShizukuPermission) {
                val error = "Shizuku permission required"
                return Result.failure(Exception(error))
            }

            val controller = ConnectivityUidFirewall.connect(shizukuManager)
            if (!controller.isEnabled()) check(readDeniedUids().isEmpty()) {
                "Another controller has inactive OEM firewall rules"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ConnectivityManager firewall not available", e)
            Result.failure(IllegalStateException("Independent profile firewall APIs are unavailable on this device. Use root, or run the VPN in the profile you want to protect.", e))
        }
    }

    override fun supportsGranularControl(): Boolean = false

    fun clearAppliedPoliciesCache() {
        appliedPolicies.clear()
        AppLogger.d(TAG, "Cleared applied policies cache (forced)")
    }

    /**
     * Undo everything this backend did to the system, from any process.
     *
     * Exists because none of it lives in this app. `set-package-networking-enabled false` and
     * `set-chain3-enabled true` are system state: if De1984 is force-stopped, crashes, or is
     * uninstalled while this backend is running, nothing calls [stopInternal] and the denied apps
     * stay offline with no De1984 on screen to explain it. FirewallManager.cleanupAllBackends()
     * calls this on every stop, so a fresh process cleans up after a dead one.
     *
     * Returns success without touching anything when there is no record of ours - the normal case
     * for the many users who never run this backend. That matters: blindly disabling chain3 could
     * clobber an OEM that uses it for its own purposes.
     */
    suspend fun clearOrphanedPolicies(): Result<Unit> = mutex.withLock {
        try {
            restoreUidPolicies()
            restoreChainState()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val blocked = loadBlockedPackages()
        val weEnabledChain3 = chain3EnabledByUs()

        if (blocked.isEmpty() && !weEnabledChain3) {
            AppLogger.d(TAG, "No ConnectivityManager state of ours to undo")
            appliedPolicies.clear()
            return Result.success(Unit)
        }

        AppLogger.d(TAG, "Undoing ConnectivityManager state: ${blocked.size} denied packages, chain3ByUs=$weEnabledChain3")

        val notRestored = restoreBlockedPackages()

        // A non-empty package record is equally good evidence that this backend ran, and covers an
        // upgrade from a build that never wrote the flag.
        var chainStillEnabled = false
        val independent = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE).getBoolean(UID_MODE, false)
        if (!independent && (weEnabledChain3 || blocked.isNotEmpty())) {
            val (exitCode, output) = shizukuManager.executeShellCommand("cmd connectivity set-chain3-enabled false")
            if (exitCode == 0) {
                setChain3EnabledByUs(false)
            } else {
                AppLogger.e(TAG, "Failed to disable firewall chain during cleanup: $output")
                // Reported, not just logged - same reasoning as stopInternal. Read the flag back
                // rather than reusing weEnabledChain3: it stays true on a failed disable, and that
                // is exactly the state that means the chain is still ours and still on.
                chainStillEnabled = chain3EnabledByUs()
            }
        }

        appliedPolicies.clear()

        if (notRestored.isEmpty() && chainStillEnabled) {
            return Result.failure(
                errorHandler.handleError(
                    Exception("the OEM_DENY_3 firewall chain we enabled could not be disabled"),
                    "disable ConnectivityManager firewall chain"
                )
            )
        }

        return if (notRestored.isEmpty()) {
            AppLogger.d(TAG, "✅ Restored networking for ${blocked.size} packages")
            Result.success(Unit)
        } else {
            AppLogger.e(TAG, "❌ ${notRestored.size} of ${blocked.size} packages could not have networking restored")
            Result.failure(
                errorHandler.handleError(
                    Exception("${notRestored.size} packages could not have networking restored"),
                    "restore ConnectivityManager package networking"
                )
            )
        }
    }

    private suspend fun restoreBlockedPackages(): Set<String> {
        val blocked = loadBlockedPackages()
        if (blocked.isEmpty()) return emptySet()

        val restored = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + RESTORE_DEADLINE_MS

        for (packageName in blocked) {
            if (System.currentTimeMillis() >= deadline) {
                val unreached = blocked - restored - failed
                failed.addAll(unreached)
                AppLogger.e(
                    TAG,
                    "❌ Restore gave up after ${RESTORE_DEADLINE_MS}ms with ${unreached.size} of " +
                        "${blocked.size} package(s) not reached - they stay in the record and the " +
                        "next stop retries them"
                )
                break
            }

            try {
                val (done, output) = setPackageNetworking(packageName, enabled = true)
                if (done) {
                    restored.add(packageName)
                    appliedPolicies.remove(packageName)
                    AppLogger.d(TAG, "Restored networking for $packageName")
                } else if (!isInstalled(packageName)) {
                    // Proof that there is nothing left to undo: the package is gone, so the system
                    // has no denial to hold against it. Without this a package uninstalled while
                    // denied stayed in the record forever, and every stop from then on reported a
                    // teardown failure that could never be cleared.
                    restored.add(packageName)
                    appliedPolicies.remove(packageName)
                    AppLogger.w(TAG, "$packageName is no longer installed - dropping it from the record")
                } else {
                    failed.add(packageName)
                    AppLogger.e(TAG, "Failed to restore networking for $packageName: $output")
                }
            } catch (e: CancellationException) {
                // The teardown itself was cancelled - PrivilegedFirewallService waits five seconds
                // for it and then cancels its scope. Let the cancel through, but write down what
                // was restored first, so the next stop retries only what is genuinely left.
                //
                // The `catch (e: Exception)` this splits out of used to swallow it and then grind
                // through every remaining package, each one failing instantly on the same
                // cancellation, before saving anyway.
                withContext(NonCancellable) {
                    saveBlockedPackages(loadBlockedPackages() - restored, durable = true)
                }
                throw e
            } catch (e: Exception) {
                failed.add(packageName)
                AppLogger.e(TAG, "Failed to restore networking for $packageName", e)
            }
        }

        // Re-read the record instead of writing the snapshot taken before the loop. This function
        // can run on a second backend instance - cleanupAllBackends() builds a fresh one - while the
        // service instance is still inside applyRules and denying new packages. Writing the stale
        // snapshot would erase whatever it recorded in the meantime.
        saveBlockedPackages(loadBlockedPackages() - restored, durable = true)
        return failed
    }

    private suspend fun setPackageNetworking(packageName: String, enabled: Boolean): Pair<Boolean, String> {
        val (exitCode, output) = shizukuManager.executeShellCommand(
            "cmd connectivity set-package-networking-enabled $enabled $packageName"
        )
        return (exitCode == 0 || (enabled && output.contains(NO_DENIAL_ANSWER))) to output
    }

    /**
     * Is this package still installed for any user?
     *
     * Deliberately conservative: any failure to answer returns true, so an unreadable package is
     * kept in the record rather than dropped. Dropping is only ever allowed on a definite "gone".
     */
    private fun isInstalled(packageName: String): Boolean {
        return try {
            // MATCH_UNINSTALLED_PACKAGES covers "pm uninstall -k" - app removed, data kept - and
            // MATCH_DISABLED_COMPONENTS covers an app the user or a device admin disabled. Plain
            // getApplicationInfo(name, 0) throws NameNotFound for both, so a package that is only
            // temporarily out of sight was counted as gone and erased from the record. Re-enable or
            // reinstall it and it comes back with our denial still applied and nothing left able to
            // undo it. Neither is "gone"; only a package with no trace at all is.
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
                PackageManager.MATCH_DISABLED_COMPONENTS
            context.packageManager.getApplicationInfo(packageName, flags)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            true
        }
    }

    private fun loadBlockedPackages(): Set<String> {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(Constants.Settings.KEY_CM_BLOCKED_PACKAGES, emptySet())?.toSet()
            ?: emptySet()
    }

    /**
     * @param durable flush synchronously. Use it before denying anything and after restoring, so
     * the record cannot be lost by a process death that lands between the write and the command.
     */
    private suspend fun saveBlockedPackages(packages: Set<String>, durable: Boolean = false) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit().putStringSet(Constants.Settings.KEY_CM_BLOCKED_PACKAGES, packages)
            if (durable) {
                @Suppress("ApplySharedPref")
                editor.commit()
            } else {
                editor.apply()
            }
        }
    }

    private fun chain3EnabledByUs(): Boolean {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.Settings.KEY_CM_CHAIN3_ENABLED_BY_US, false)
    }

    private suspend fun setChain3EnabledByUs(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            @Suppress("ApplySharedPref")
            prefs.edit().putBoolean(Constants.Settings.KEY_CM_CHAIN3_ENABLED_BY_US, enabled).commit()
        }
    }

    /**
     * Check if an app has a VPN service by looking for services with BIND_VPN_SERVICE permission.
     *
     * VPN apps don't REQUEST the BIND_VPN_SERVICE permission - they DECLARE it on their service.
     * This is a service permission that protects the VPN service from being bound by unauthorized apps.
     *
     * userId has NO DEFAULT on purpose. It used to default to 0, and every enforcement call site
     * omitted it - so a VPN app installed only in the work profile was looked up in the personal
     * profile, not found, and treated as an ordinary app. Block All then cut the work profile's
     * VPN. Making it required means the compiler catches the next caller that forgets.
     */
    private fun hasVpnService(packageName: String, userId: Int): Boolean {
        return try {
                // GET_PERMISSIONS is requested but never read. It is here so this shares a cache
                // entry with the getPackagesWithNetworkPermissions sweep, which asks for both.
                // getPackageInfoAsUser keys its cache on "userId:flags:packageName", so GET_SERVICES
                // alone (4) is a different key from GET_PERMISSIONS or GET_SERVICES (4100) and every
                // call was a guaranteed miss - one binder round trip per app, inside a filter over
                // every package. Measured on hardware: 53 hit / 587 miss before, and the whole
                // Block All start took 8.35s.
            val packageInfo = dev.bgcheck.network.data.multiuser.HiddenApiHelper.getPackageInfoAsUser(
                context,
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
                userId
            ) ?: return false

            packageInfo.services?.any { serviceInfo ->
                serviceInfo.permission == Constants.Firewall.VPN_SERVICE_PERMISSION
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
