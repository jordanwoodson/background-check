package dev.bgcheck.network.data.datasource

import dev.bgcheck.network.domain.model.PackageIdentity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dev.bgcheck.network.utils.AppLogger
import androidx.core.graphics.drawable.toBitmap
import dev.bgcheck.network.data.common.ShizukuManager
import dev.bgcheck.network.data.model.PackageEntity
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.repository.FirewallRepository
import dev.bgcheck.network.utils.Constants
import dev.bgcheck.network.utils.PackageSafetyLoader
import dev.bgcheck.network.utils.ShellRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class BlockingState(
    val isNetworkBlocked: Boolean,
    val wifiBlocked: Boolean,
    val mobileBlocked: Boolean,
    val roamingBlocked: Boolean,
    val backgroundBlocked: Boolean,
    val lanBlocked: Boolean
)

private data class PackageMetadata(
    val permissions: List<String>,
    val isVpnApp: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val installTime: Long?,
    val updateTime: Long?,
    val hasNetworkAccess: Boolean,
    val permissionsKnown: Boolean = false,
)

class AndroidPackageDataSource(
    private val context: Context,
    private val firewallRepository: FirewallRepository,
    private val shizukuManager: ShizukuManager
) : PackageDataSource {

    private val packageManager = context.packageManager
    private val _profileWarnings = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    override val profileWarnings: kotlinx.coroutines.flow.StateFlow<List<String>> = _profileWarnings

    private val packagesFlow = MutableSharedFlow<List<PackageEntity>>(replay = 1)

    private val loadMutex = Mutex()
    // @Volatile because the outer guard reads these OUTSIDE loadMutex. They are only safe today
    // because every collector happens to run on Main; one flowOn would break that silently.
    @Volatile private var isLoading = false
    @Volatile private var lastLoadTime = 0L

    /**
     * A rescan was asked for. Survives a scan that is already running - see [invalidateCache].
     */
    @Volatile private var invalidated = false
    private val CACHE_TTL = 1000L

    companion object {
        private const val TAG = "AndroidPackageDataSource"
    }

    override fun getPackages(): Flow<List<PackageEntity>> = packagesFlow
        .onStart {
            val now = System.currentTimeMillis()
            val cacheExpired = (now - lastLoadTime) > CACHE_TTL

            // An outstanding invalidation WAITS. Everything else may skip while a scan is running.
            //
            // Skipping on an invalidation looked safe - the collector subscribes right after onStart,
            // and a scan in flight honours the flag in its own loop. That premise is false for the
            // one case that matters: a settings or rule change invalidates AND cancels the running
            // load in the same breath, so the scan that was supposed to honour the flag dies, and
            // the replacement collector - running inline while isLoading is still true - skips. The
            // request then had nobody left to consume it and the list sat on pre-change rows
            // indefinitely. Waiting costs at most one scan; skipping cost correctness.
            //
            // With NO data there is no choice either way: wait, then take the fresh result or scan
            // and throw our OWN error to our OWN screen. That is the hang this guard exists to fix -
            // a collector turned away had no subscription yet, so it could be neither served nor
            // told the scan had failed. (Broadcasting failures instead was worse: it killed other
            // screens that were showing correct lists, and still missed this one.)
            if (invalidated || packagesFlow.replayCache.isEmpty() || (cacheExpired && !isLoading)) {
                loadMutex.withLock {
                    if (invalidated || packagesFlow.replayCache.isEmpty() || (now - lastLoadTime) > CACHE_TTL) {
                        isLoading = true
                        try {
                            // A failed scan THROWS. It used to return emptyList(), which was emitted
                            // and stamped lastLoadTime, so the user saw "no packages" with no error
                            // and every collector for the next second got that cached empty list.
                            //
                            // Swallowing it into null was worse still: nothing was emitted at all, so
                            // PackagesViewModel's .catch never fired and its spinner ran forever, and
                            // SettingsViewModel's getPackages().first() suspended for good.
                            //
                            // Both callers already handle a thrown error. Letting it out is what
                            // reaches them. lastLoadTime is not stamped, so the next collector retries.
                            // Loops while another invalidation arrives DURING the scan. Without
                            // this, a caller that invalidated while a scan was in flight was simply
                            // skipped by the isLoading guard above, and that scan - which read the
                            // OLD settings - then stamped lastLoadTime and wiped the request. The
                            // rows stayed painted with values the user had already changed, and
                            // nothing on the firewall screen ever rescanned.
                            var emittedInThisLoop = false
                            // Bounded. Every waiter on loadMutex is blocked for the WHOLE loop, not
                            // for one scan, and a scan of this device measures in seconds - so an
                            // activity-scoped ViewModel raising an invalidation per settings change
                            // could hold the Settings import dialog spinning through several of
                            // them. Stopping early is safe: `invalidated` stays set, so the next
                            // collector picks the request up.
                            var extraPasses = 0
                            do {
                                invalidated = false
                                try {
                                    val packages = loadPackagesInternal()
                                    lastLoadTime = System.currentTimeMillis()
                                    packagesFlow.emit(packages)
                                    emittedInThisLoop = true
                                } catch (t: Throwable) {
                                    // A CANCELLATION always re-raises the request.
                                    //
                                    // So does a failure that comes AFTER this loop already emitted:
                                    // that means an invalidation arrived mid-loop, an earlier pass
                                    // stamped lastLoadTime with pre-change rows, and a waiter would
                                    // otherwise find a fresh-looking replay and skip - leaving the
                                    // screen painted from settings that had already been rewritten.
                                    //
                                    // A first pass that fails does NOT re-raise. It stamps nothing,
                                    // so the next collector rescans anyway, and latching there made
                                    // a device that simply cannot scan rescan for ever.
                                    if (t is CancellationException || emittedInThisLoop) invalidated = true
                                    throw t
                                }
                            } while (invalidated && ++extraPasses <= 2)
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }
        }

    override fun invalidateCache() {
        // Both, and the flag is what makes this reliable. Zeroing the timestamp alone was not
        // enough: onStart also skips while isLoading is true, and the scan already running would
        // stamp lastLoadTime on the way out and wipe the request. The flag is checked again after
        // every scan, so an invalidation raised mid-scan costs one more pass instead of being lost.
        invalidated = true
        lastLoadTime = 0L
    }

    /** @throws Exception when the scan fails. A failure is not an empty device - see the caller. */
    private suspend fun loadPackagesInternal(): List<PackageEntity> = withContext(Dispatchers.IO) {
        val flowStartTime = System.currentTimeMillis()
        AppLogger.i(TAG, "⏱️ TIMING: getPackages START at $flowStartTime")
        try {
            HiddenApiHelper.clearDisabledPackagesCache()
            HiddenApiHelper.clearInstalledAppsCache()

            val getUsersStart = System.currentTimeMillis()
            val userProfiles = HiddenApiHelper.getUsers(context)
            val getUsersEnd = System.currentTimeMillis()
            AppLogger.i(TAG, "⏱️ TIMING: getUsers took ${getUsersEnd - getUsersStart}ms, returned ${userProfiles.size} profiles")
            AppLogger.d(TAG, "📱 Enumerating packages for ${userProfiles.size} user profiles")

            val rulesStart = System.currentTimeMillis()
            val firewallRules = firewallRepository.getAllRules().first()
            val rulesEnd = System.currentTimeMillis()
            AppLogger.i(TAG, "⏱️ TIMING: getAllRules().first() took ${rulesEnd - rulesStart}ms, returned ${firewallRules.size} rules")
            val rulesByKey = firewallRules.associateBy { "${it.packageName}:${it.userId}" }

            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            val defaultPolicy = prefs.getString(
                Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                Constants.Settings.DEFAULT_FIREWALL_POLICY
            ) ?: Constants.Settings.DEFAULT_FIREWALL_POLICY
            val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL
            val allowCritical = prefs.getBoolean(
                Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
                Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
            )

            val allPackages = mutableListOf<PackageEntity>()
            val warnings = mutableListOf<String>()

            AppLogger.i(TAG, "📱 MULTI-USER SUMMARY: ${userProfiles.size} profiles detected:")
            for (profile in userProfiles) {
                AppLogger.i(TAG, "   → userId=${profile.userId}, name=${profile.displayName}, isWork=${profile.isWorkProfile}, isClone=${profile.isCloneProfile}")
            }

            for (profile in userProfiles) {
                if (!profile.available) warnings += "${profile.displayName} (user ${profile.userId}): ${profile.unavailableReason ?: "Profile unavailable"}"
                val profileStart = System.currentTimeMillis()
                val installedPackages = try {
                    HiddenApiHelper.getInstalledApplicationsAsUser(context, PackageManager.GET_META_DATA, profile.userId)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { emptyList() }
                val profileEnd = System.currentTimeMillis()

                AppLogger.i(TAG, "⏱️ TIMING: Profile ${profile.userId} (${profile.displayName}): getInstalledApplicationsAsUser took ${profileEnd - profileStart}ms, returned ${installedPackages.size} packages")

                AppLogger.d(TAG, "📦 User ${profile.userId} (${profile.displayName}): ${installedPackages.size} packages")

                // Logged, NOT thrown. Emptiness is not a failure signal at profile level:
                // HiddenApiHelper.getInstalledApplicationsAsUser returns an empty list as its
                // documented "all methods failed" outcome for any user other than De1984's own, which is what
                // happens on every device with no root and no Shizuku that has a work or clone
                // profile. Throwing here replaced a complete personal list with "could not read the
                // list of apps" for all of those users, permanently. The filter it was protecting is
                // protected where the damage actually was - the reset is no longer persisted.
                if (installedPackages.isEmpty()) {
                    warnings += "${profile.displayName} (user ${profile.userId}) could not be read. Start Shizuku or resume this profile, then refresh."
                    continue
                }

                if (profile.userId != 0 && installedPackages.isNotEmpty()) {
                    val sampleApps = installedPackages.take(5).map { it.packageName }
                    AppLogger.i(TAG, "📦 MULTI-USER: Sample apps from ${profile.displayName} profile: $sampleApps")
                }

                val chunkSize = 25
                val packageChunks = installedPackages
                    .filter { !Constants.App.isOwnApp(it.packageName) }
                    .chunked(chunkSize)

                for (chunk in packageChunks) {
                    val chunkResults = chunk.map { appInfo ->
                        async {
                            val ruleKey = "${appInfo.packageName}:${profile.userId}"
                            val rule = rulesByKey[ruleKey]

                            // OPTIMIZATION: Single batch call instead of 7 separate calls
                            // This reduces ~1400+ system calls to ~200 for typical device
                            val metadata = getPackageMetadataBatch(appInfo.packageName, profile.userId)

                            val absoluteUid = appInfo.uid

                            if (metadata.isVpnApp) {
                                AppLogger.d(TAG, "🔍 VPN APP DETECTED: ${appInfo.packageName} (user ${profile.userId}), hasRule=${rule != null}")
                            }

                            val isCriticalPackage = Constants.Firewall.isSystemCritical(appInfo.packageName) || metadata.isVpnApp

                            val blockingState = if (isCriticalPackage && !allowCritical) {
                                BlockingState(
                                    isNetworkBlocked = false,
                                    wifiBlocked = false,
                                    mobileBlocked = false,
                                    roamingBlocked = false,
                                    backgroundBlocked = false,
                                    lanBlocked = false
                                )
                            } else if (rule != null && rule.enabled) {
                                BlockingState(
                                    isNetworkBlocked = rule.wifiBlocked || rule.mobileBlocked,
                                    wifiBlocked = rule.wifiBlocked,
                                    mobileBlocked = rule.mobileBlocked,
                                    roamingBlocked = rule.blockWhenRoaming,
                                    backgroundBlocked = rule.blockWhenBackground,
                                    lanBlocked = rule.lanBlocked
                                )
                            } else if (isCriticalPackage && allowCritical) {
                                BlockingState(
                                    isNetworkBlocked = false,
                                    wifiBlocked = false,
                                    mobileBlocked = false,
                                    roamingBlocked = false,
                                    backgroundBlocked = false,
                                    lanBlocked = false
                                )
                            } else {
                                BlockingState(
                                    isNetworkBlocked = isBlockAllDefault,
                                    wifiBlocked = isBlockAllDefault,
                                    mobileBlocked = isBlockAllDefault,
                                    roamingBlocked = isBlockAllDefault,
                                    backgroundBlocked = false,
                                    lanBlocked = isBlockAllDefault
                                )
                            }

                            val criticality = PackageSafetyLoader.getCriticality(context, appInfo.packageName)
                            val category = PackageSafetyLoader.getCategory(context, appInfo.packageName)
                            val affects = PackageSafetyLoader.getAffects(context, appInfo.packageName)

                            PackageEntity(
                                // Recorded, not re-read later: these are the values THIS scan
                                // painted the blocking flags with. See PackageEntity.
                                paintedAllowCritical = allowCritical,
                                paintedBlockAllDefault = isBlockAllDefault,
                                packageName = appInfo.packageName,
                                userId = profile.userId,
                                uid = absoluteUid,
                                name = getAppName(appInfo),
                                icon = getAppIconEmoji(appInfo),
                                isEnabled = appInfo.enabled,
                                type = if (isSystemApp(appInfo)) Constants.Packages.TYPE_SYSTEM else Constants.Packages.TYPE_USER,
                                versionName = metadata.versionName,
                                versionCode = metadata.versionCode,
                                installTime = metadata.installTime,
                                updateTime = metadata.updateTime,
                                permissions = metadata.permissions,
                                permissionsKnown = metadata.permissionsKnown,
                                hasNetworkAccess = metadata.hasNetworkAccess,
                                isNetworkBlocked = blockingState.isNetworkBlocked,
                                wifiBlocked = blockingState.wifiBlocked,
                                mobileBlocked = blockingState.mobileBlocked,
                                roamingBlocked = blockingState.roamingBlocked,
                                backgroundBlocked = blockingState.backgroundBlocked,
                                lanBlocked = blockingState.lanBlocked,
                                isVpnApp = metadata.isVpnApp,
                                hasExplicitRule = rule?.enabled == true,
                                criticality = criticality,
                                category = category,
                                affects = affects,
                                isWorkProfile = profile.isWorkProfile,
                                userSerial = profile.serialNumber,
                                isCloneProfile = profile.isCloneProfile
                            )
                        }
                    }.awaitAll()

                    allPackages.addAll(chunkResults)
                }
            }

            val personalCount = allPackages.count { !it.isWorkProfile && !it.isCloneProfile }
            val workCount = allPackages.count { it.isWorkProfile }
            val cloneCount = allPackages.count { it.isCloneProfile }
            AppLogger.i(TAG, "📊 MULTI-USER FINAL: Total ${allPackages.size} packages (Personal: $personalCount, Work: $workCount, Clone: $cloneCount)")

            val workApps = allPackages.filter { it.isWorkProfile }.take(5).map { it.packageName }
            val cloneApps = allPackages.filter { it.isCloneProfile }.take(5).map { it.packageName }
            if (workApps.isNotEmpty()) {
                AppLogger.i(TAG, "📊 MULTI-USER: Work profile apps sample: $workApps")
            }
            if (cloneApps.isNotEmpty()) {
                AppLogger.i(TAG, "📊 MULTI-USER: Clone profile apps sample: $cloneApps")
            }

            val flowEndTime = System.currentTimeMillis()
            AppLogger.i(TAG, "⏱️ TIMING: getPackages COMPLETE - Total time: ${flowEndTime - flowStartTime}ms for ${allPackages.size} packages")

            // An EMPTY scan is a failed scan, not an empty device. Every Android device has
            // packages other than this one, so zero means the enumeration itself did not work -
            // HiddenApiHelper.getUsers came back with no profiles, or getInstalledApplicationsAsUser
            // answered empty because root or Shizuku was not granted yet on a fresh install.
            //
            // Returning it as a success stamped lastLoadTime and emitted the empty list into a
            // replay(1) flow, so the screen showed "no apps" with no error and every filter chip
            // kept showing it - the CACHE_TTL retry never fires because nothing collects again.
            // Only a force-stop cleared it. Throw instead: the caller leaves lastLoadTime alone and
            // the collectors' existing .catch reports it.
            if (allPackages.isEmpty()) {
                throw IllegalStateException(
                    "Package scan returned no packages for ${userProfiles.size} profile(s) - " +
                        "the enumeration failed rather than the device being empty"
                )
            }

            _profileWarnings.value = warnings
            allPackages.sortedBy { it.name.lowercase() }
        } catch (e: CancellationException) {
            // Straight through. This is loadJob.cancel() replacing its own load, not a failure, and
            // structured concurrency requires it to propagate untouched.
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get packages: ${e.message}", e)
            // Wrapped, not rethrown raw. The screens have to tell the user "could not read the app
            // list" in their own language, and they can only do that reliably if every failure from
            // this scan arrives as one recognisable type.
            throw PackageScanException("Package scan failed: ${e.message}", e)
        }
    }

    override suspend fun getPackage(packageName: String, userId: Int): PackageEntity? {
        if (Constants.App.isOwnApp(packageName)) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext null

                val rule = firewallRepository.getRuleByPackage(packageName, userId).first()
                val metadata = getPackageMetadataBatch(packageName, userId)
                val isVpnApp = metadata.isVpnApp

                val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
                val defaultPolicy = prefs.getString(
                    Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                    Constants.Settings.DEFAULT_FIREWALL_POLICY
                ) ?: Constants.Settings.DEFAULT_FIREWALL_POLICY
                val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL
                val allowCritical = prefs.getBoolean(
                    Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
                    Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
                )

                if (isVpnApp) {
                    AppLogger.d(TAG, "🔍 VPN APP DETECTED (getPackage): $packageName, hasRule=${rule != null}, isSystemCritical=${Constants.Firewall.isSystemCritical(packageName)}")
                }

                val isCriticalPackage = Constants.Firewall.isSystemCritical(packageName) || isVpnApp

                val blockingState = if (isCriticalPackage && !allowCritical) {
                    // Setting OFF: Critical packages are FORCED to ALLOW (locked, cannot be changed)
                    AppLogger.d(TAG, "✅ $packageName: Critical package (setting OFF) → FORCE ALLOW")
                    BlockingState(
                        isNetworkBlocked = false,
                        wifiBlocked = false,
                        mobileBlocked = false,
                        roamingBlocked = false,
                        backgroundBlocked = false,
                        lanBlocked = false
                    )
                } else if (rule != null && rule.enabled) {
                    BlockingState(
                        isNetworkBlocked = rule.wifiBlocked || rule.mobileBlocked,
                        wifiBlocked = rule.wifiBlocked,
                        mobileBlocked = rule.mobileBlocked,
                        roamingBlocked = rule.blockWhenRoaming,
                        backgroundBlocked = rule.blockWhenBackground,
                        lanBlocked = rule.lanBlocked
                    )
                } else if (isCriticalPackage && allowCritical) {
                    AppLogger.d(TAG, "✅ $packageName: Critical package (setting ON, no rule) → DEFAULT ALLOW")
                    BlockingState(
                        isNetworkBlocked = false,
                        wifiBlocked = false,
                        mobileBlocked = false,
                        roamingBlocked = false,
                        backgroundBlocked = false,
                        lanBlocked = false
                    )
                } else {
                    BlockingState(
                        isNetworkBlocked = isBlockAllDefault,
                        wifiBlocked = isBlockAllDefault,
                        mobileBlocked = isBlockAllDefault,
                        roamingBlocked = isBlockAllDefault,
                        backgroundBlocked = false,
                        lanBlocked = isBlockAllDefault
                    )
                }

                val criticality = PackageSafetyLoader.getCriticality(context, appInfo.packageName)
                val category = PackageSafetyLoader.getCategory(context, appInfo.packageName)
                val affects = PackageSafetyLoader.getAffects(context, appInfo.packageName)

                val isWorkProfile = userId in 10..99
                val isCloneProfile = userId >= 100

                PackageEntity(
                    // Recorded for the same reason the main scan records them - every protection
                    // test in the UI now trusts these rather than re-reading the preferences.
                    paintedAllowCritical = allowCritical,
                    paintedBlockAllDefault = isBlockAllDefault,
                    packageName = appInfo.packageName,
                    userId = userId,
                    uid = appInfo.uid,
                    name = getAppName(appInfo),
                    icon = getAppIconEmoji(appInfo),
                    isEnabled = appInfo.enabled,
                    type = if (isSystemApp(appInfo)) Constants.Packages.TYPE_SYSTEM else Constants.Packages.TYPE_USER,
                    versionName = metadata.versionName,
                    versionCode = metadata.versionCode,
                    installTime = metadata.installTime,
                    updateTime = metadata.updateTime,
                    permissions = metadata.permissions,
                    permissionsKnown = metadata.permissionsKnown,
                    hasNetworkAccess = metadata.hasNetworkAccess,
                    isNetworkBlocked = blockingState.isNetworkBlocked,
                    wifiBlocked = blockingState.wifiBlocked,
                    mobileBlocked = blockingState.mobileBlocked,
                    roamingBlocked = blockingState.roamingBlocked,
                    backgroundBlocked = blockingState.backgroundBlocked,
                    isVpnApp = isVpnApp,
                    hasExplicitRule = rule?.enabled == true,
                    criticality = criticality,
                    category = category,
                    affects = affects,
                    isWorkProfile = isWorkProfile,
                    userSerial = HiddenApiHelper.currentProfile(context, userId)?.serialNumber,
                    isCloneProfile = isCloneProfile
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setPackageEnabled(packageName: String, userId: Int, enabled: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)
        return setPackageEnabledSetting(packageName, userId, if (enabled) 1 else 3, identity)
    }

    override suspend fun setPackageEnabledSetting(packageName: String, userId: Int, setting: Int, expected: PackageIdentity): Boolean {
        val operation = dev.bgcheck.network.domain.model.PackageEnabledSetting.command(setting)
        val identity = validateTarget(packageName, userId, expected, retained = false)

        if (Constants.App.isOwnApp(packageName)) {
            return false
        }

        return withContext(Dispatchers.IO) {
            if (shizukuManager.isShizukuAvailable()) {
                if (!shizukuManager.hasShizukuPermission) {
                    shizukuManager.requestShizukuPermission()
                    kotlinx.coroutines.delay(500)
                }

                if (shizukuManager.hasShizukuPermission) {
                    val command = "pm $operation --user $userId $packageName"

                    // No catch: executeShellCommand reports every process failure as exitCode -1
                    // rather than throwing. The `catch (e: Exception) {}` that used to sit here
                    // swallowed coroutine cancellation, which must reach the caller.
                    validateTarget(packageName, userId, identity, retained = false)
                    val (exitCode, _) = shizukuManager.executeShellCommand(command)
                    if (exitCode == 0) {
                        HiddenApiHelper.clearDisabledPackagesCache()
                        HiddenApiHelper.clearInstalledAppsCache()
                        return@withContext true
                    }
                }
            }

            val rootCommand = "pm $operation --user $userId $packageName"

            // ShellRunner bounds the wait, drains both pipes together, and reports a failed start
            // as exitCode -1 instead of throwing - so there is nothing left here to catch. The
            // `catch (e: Exception) {}` this replaces also swallowed coroutine cancellation.
            validateTarget(packageName, userId, identity, retained = false)
            val rootResult = ShellRunner.run("root: $rootCommand") {
                Runtime.getRuntime().exec(arrayOf("su", "-c", rootCommand))
            }

            if (rootResult.exitCode == 0) {
                HiddenApiHelper.clearDisabledPackagesCache()
                HiddenApiHelper.clearInstalledAppsCache()
                return@withContext true
            }

            false
        }
    }

    override suspend fun getUninstalledSystemPackages(): List<PackageEntity> = withContext(Dispatchers.IO) {
        HiddenApiHelper.awaitCrossUserPermission(context)
        val warnings = mutableListOf<String>()
        val result = HiddenApiHelper.getUsers(context).flatMap { profile ->
            if (!profile.available) warnings += "${profile.displayName} (user ${profile.userId}): ${profile.unavailableReason ?: "Profile unavailable"}"
            val apps = try { HiddenApiHelper.getInstalledApplicationsAsUser(context, PackageManager.MATCH_UNINSTALLED_PACKAGES, profile.userId) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { emptyList() }
            if (apps.isEmpty()) {
                warnings += "Retained apps in ${profile.displayName} (user ${profile.userId}) could not be read. Start Shizuku or resume this profile."
                return@flatMap emptyList()
            }
            apps.filter { it.flags and ApplicationInfo.FLAG_INSTALLED == 0 && !Constants.App.isOwnApp(it.packageName) }
                .map { info ->
                    val metadata = HiddenApiHelper.getPackageInfoAsUser(context, info.packageName,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_PERMISSIONS, profile.userId)
                    PackageEntity(
                        packageName = info.packageName, userId = profile.userId, uid = info.uid,
                        name = runCatching { packageManager.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                        icon = "⚙️", isEnabled = false, isInstalled = false,
                        type = if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) Constants.Packages.TYPE_SYSTEM else Constants.Packages.TYPE_USER,
                        versionName = metadata?.versionName, versionCode = metadata?.longVersionCode,
                        permissions = metadata?.requestedPermissions?.toList().orEmpty(),
                        hasNetworkAccess = metadata?.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true,
                        permissionsKnown = metadata != null,
                        criticality = PackageSafetyLoader.getCriticality(context, info.packageName),
                        category = PackageSafetyLoader.getCategory(context, info.packageName),
                        affects = PackageSafetyLoader.getAffects(context, info.packageName),
                        isWorkProfile = profile.isWorkProfile, userSerial = profile.serialNumber,
                                isCloneProfile = profile.isCloneProfile
                    )
                }
        }.sortedBy { it.name.lowercase() }
        _profileWarnings.value = warnings
        result
    }

    override suspend fun uninstallPackage(packageName: String, userId: Int, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        if (Constants.App.isOwnApp(packageName)) {
            return false
        }

        return withContext(Dispatchers.IO) {
            if (shizukuManager.isShizukuAvailable()) {
                if (!shizukuManager.hasShizukuPermission) {
                    shizukuManager.requestShizukuPermission()
                    kotlinx.coroutines.delay(500)
                }

                if (shizukuManager.hasShizukuPermission) {
                    val command = "pm uninstall --user $userId $packageName"
                    validateTarget(packageName, userId, identity, retained = false)
                    val (exitCode, _) = shizukuManager.executeShellCommand(command)
                    if (exitCode == 0) {
                        return@withContext true
                    }
                }
            }

            val rootCommand = "pm uninstall --user $userId $packageName"
            validateTarget(packageName, userId, identity, retained = false)
            val rootResult = ShellRunner.run("root: $rootCommand") {
                Runtime.getRuntime().exec(arrayOf("su", "-c", rootCommand))
            }

            if (rootResult.exitCode == 0) {
                return@withContext true
            }

            false
        }
    }

    override suspend fun reinstallPackage(packageName: String, userId: Int, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = true)

        if (Constants.App.isOwnApp(packageName)) {
            return false
        }

        return withContext(Dispatchers.IO) {
            if (shizukuManager.isShizukuAvailable()) {
                if (!shizukuManager.hasShizukuPermission) {
                    shizukuManager.requestShizukuPermission()
                    kotlinx.coroutines.delay(500)
                }

                if (shizukuManager.hasShizukuPermission) {
                    val command = "cmd package install-existing --user $userId $packageName"
                    validateTarget(packageName, userId, identity, retained = true)
                    val (exitCode, _) = shizukuManager.executeShellCommand(command)
                    if (exitCode == 0) {
                        return@withContext true
                    }
                }
            }

            val rootCommand = "cmd package install-existing --user $userId $packageName"
            validateTarget(packageName, userId, identity, retained = true)
            val rootResult = ShellRunner.run("root: $rootCommand") {
                Runtime.getRuntime().exec(arrayOf("su", "-c", rootCommand))
            }

            if (rootResult.exitCode == 0) {
                return@withContext true
            }

            false
        }
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        if (Constants.App.isOwnApp(packageName)) {
            return false
        }

        return withContext(Dispatchers.IO) {
            if (shizukuManager.isShizukuAvailable()) {
                if (!shizukuManager.hasShizukuPermission) {
                    shizukuManager.requestShizukuPermission()
                    kotlinx.coroutines.delay(500)
                }

                if (shizukuManager.hasShizukuPermission) {
                    val command = "am force-stop --user $userId $packageName"
                    validateTarget(packageName, userId, identity, retained = false)
                    val (exitCode, _) = shizukuManager.executeShellCommand(command)
                    if (exitCode == 0) {
                        return@withContext true
                    }
                }
            }

            val rootCommand = "am force-stop --user $userId $packageName"
            validateTarget(packageName, userId, identity, retained = false)
            val rootResult = ShellRunner.run("root: $rootCommand") {
                Runtime.getRuntime().exec(arrayOf("su", "-c", rootCommand))
            }

            if (rootResult.exitCode == 0) {
                return@withContext true
            }

            false
        }
    }

    private suspend fun validateTarget(packageName: String, userId: Int, expected: PackageIdentity?, retained: Boolean): PackageIdentity = withContext(Dispatchers.IO) {
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*").matches(packageName)) { "Invalid package identity" }
        val profile = HiddenApiHelper.currentProfile(context, userId) ?: error("Profile no longer exists")
        val info = HiddenApiHelper.getApplicationInfoAsUser(context, packageName,
            if (retained) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0, userId)
            ?: error("App is unavailable in the selected profile")
        val identity = expected ?: PackageIdentity(info.uid, profile.serialNumber)
        val installTime = if (identity.firstInstallTime != null) {
            HiddenApiHelper.getVerifiedPackageInfoAsUser(context, packageName,
                if (retained) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0, userId)?.firstInstallTime
        } else null
        identity.requireCurrent(userId, info.uid, profile.serialNumber, profile.available, installTime)
        identity
    }

    private fun getAppName(appInfo: ApplicationInfo): String {
        return try {
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appInfo.packageName
        }
    }

    private fun getAppIconEmoji(appInfo: ApplicationInfo): String {
        return if (isSystemApp(appInfo)) "⚙️" else "📦"
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun getPackageMetadataBatch(packageName: String, userId: Int): PackageMetadata {
        return try {
            val packageInfo = HiddenApiHelper.getPackageInfoAsUser(
                context,
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
                userId
            )

            if (packageInfo == null) {
                return PackageMetadata(
                    permissions = emptyList(),
                    isVpnApp = false,
                    versionName = null,
                    versionCode = null,
                    installTime = null,
                    updateTime = null,
                    hasNetworkAccess = false
                )
            }

            val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

            val isVpnApp = packageInfo.services?.any { serviceInfo ->
                serviceInfo.permission == Constants.Firewall.VPN_SERVICE_PERMISSION
            } ?: false

            val hasNetworkAccess = permissions.any { it in Constants.Firewall.NETWORK_PERMISSIONS }

            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            PackageMetadata(
                permissions = permissions,
                isVpnApp = isVpnApp,
                versionName = packageInfo.versionName,
                versionCode = versionCode,
                installTime = packageInfo.firstInstallTime,
                updateTime = packageInfo.lastUpdateTime,
                hasNetworkAccess = hasNetworkAccess,
                permissionsKnown = true,
            )
        } catch (e: Exception) {
            AppLogger.d(TAG, "Failed to get metadata for $packageName: ${e.message}")
            PackageMetadata(
                permissions = emptyList(),
                isVpnApp = false,
                versionName = null,
                versionCode = null,
                installTime = null,
                updateTime = null,
                hasNetworkAccess = false
            )
        }
    }

    private fun getAppPermissions(packageName: String, userId: Int = 0): List<String> {
        return try {
            val packageInfo = HiddenApiHelper.getPackageInfoAsUser(context, packageName, PackageManager.GET_PERMISSIONS, userId)
            packageInfo?.requestedPermissions?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if an app has a VPN service by looking for services with BIND_VPN_SERVICE permission.
     *
     * VPN apps don't REQUEST the BIND_VPN_SERVICE permission - they DECLARE it on their service.
     * This is a service permission that protects the VPN service from being bound by unauthorized apps.
     *
     * Example from a VPN app's AndroidManifest.xml:
     * <service android:name=".VpnService" android:permission="android.permission.BIND_VPN_SERVICE">
     *     <intent-filter>
     *         <action android:name="android.net.VpnService" />
     *     </intent-filter>
     * </service>
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
            val packageInfo = HiddenApiHelper.getPackageInfoAsUser(
                context,
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
                userId
            ) ?: return false

            val isVpn = packageInfo.services?.any { serviceInfo ->
                serviceInfo.permission == Constants.Firewall.VPN_SERVICE_PERMISSION
            } ?: false

            if (isVpn) {
                AppLogger.d(TAG, "🔍 hasVpnService($packageName, userId=$userId) = true (found VPN service)")
            }

            isVpn
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ hasVpnService($packageName, userId=$userId) failed", e)
            false
        }
    }

    /**
     * Does this package request any permission the firewall cares about?
     *
     * Reads [Constants.Firewall.NETWORK_PERMISSIONS] rather than a list of its own. It used to check
     * three permissions inline - INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE - while that
     * constant, which the firewall backends use, holds five. An app requesting only
     * CHANGE_WIFI_STATE or CHANGE_NETWORK_STATE was shown here as having no network permission while
     * the firewall was applying a policy to it.
     *
     * Deliberately still one direct `getPackageInfoAsUser` per package, NOT a lookup in
     * HiddenApiHelper's cached whole-profile list. That list is only as good as the enumeration
     * behind it, and the enumeration is flaky: measured twice on 2026-08-25, user 10 returned 0
     * packages from the hidden API before a Shizuku fallback found 216. Answering a per-package
     * question from a whole-profile scan would turn one failed scan into "no network permission"
     * for every app in that profile. A direct call cannot fail that way.
     */
    private fun hasNetworkPermissions(packageName: String, userId: Int = 0): Boolean {
        val permissions = getAppPermissions(packageName, userId)
        return permissions.any { Constants.Firewall.NETWORK_PERMISSIONS.contains(it) }
    }

    override suspend fun setNetworkAccess(packageName: String, userId: Int, allowed: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()
                val rule = if (existingRule != null) {
                    val updated = if (allowed) {
                        existingRule.allowAll()
                    } else {
                        existingRule.blockAll()
                    }
                    updated
                } else {
                    val newRule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = !allowed,
                        mobileBlocked = !allowed,
                        blockWhenRoaming = !allowed,
                        lanBlocked = !allowed,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    newRule
                }

                validateTarget(packageName, userId, identity, retained = false)
                firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setWifiBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateWifiBlocking(packageName, userId, blocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    )
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = blocked,
                        mobileBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        blockWhenRoaming = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setMobileBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateMobileBlocking(packageName, userId, blocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    )
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        mobileBlocked = blocked,
                        blockWhenRoaming = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setRoamingBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateRoamingBlocking(packageName, userId, blocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    )
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        mobileBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        blockWhenRoaming = blocked,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setBackgroundBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateBackgroundBlocking(packageName, userId, blocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    )
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        // Flat false, not the Block All default. This toggle is only SHOWN when the
                        // row is not fully blocked, so a first rule created through it always comes
                        // from a row displaying its networks as allowed - in either direction.
                        // Inheriting the default here meant that turning "Allow in Background" ON
                        // wrote a full network block.
                        wifiBlocked = false,
                        mobileBlocked = false,
                        blockWhenRoaming = false,
                        blockWhenBackground = blocked,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setLanBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateLanBlocking(packageName, userId, blocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    ) ?: Constants.Settings.DEFAULT_FIREWALL_POLICY
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        mobileBlocked = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        blockWhenRoaming = isBlockAllDefault && !blocked && stampDefaultOnUntouched,
                        lanBlocked = blocked,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setAllNetworkBlocking(packageName: String, userId: Int, blocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateAllNetworkBlocking(packageName, userId, blocked)
                } else {
                    // Create new rule with the three internet transports set to the same state.
                    // LAN is left at its default on purpose - see updateAllNetworkBlocking.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = blocked,
                        mobileBlocked = blocked,
                        blockWhenRoaming = blocked,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun setMobileAndRoaming(packageName: String, userId: Int, mobileBlocked: Boolean, roamingBlocked: Boolean, stampDefaultOnUntouched: Boolean, expected: PackageIdentity?): Boolean {
        val identity = validateTarget(packageName, userId, expected, retained = false)

        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val allowCritical = prefs.getBoolean(
            Constants.Settings.KEY_ALLOW_CRITICAL_FIREWALL,
            Constants.Settings.DEFAULT_ALLOW_CRITICAL_FIREWALL
        )

        if (Constants.Firewall.isSystemCritical(packageName) && !allowCritical) {
            return false
        }

        if (hasVpnService(packageName, userId) && !allowCritical) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val appInfo = HiddenApiHelper.getApplicationInfoAsUser(
                    context, packageName, PackageManager.GET_META_DATA, userId
                ) ?: return@withContext false
                val existingRule = firewallRepository.getRuleByPackage(packageName, userId).first()

                if (existingRule != null) {
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.updateMobileAndRoaming(packageName, userId, mobileBlocked, roamingBlocked)
                } else {
                    val defaultPolicy = prefs.getString(
                        Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY,
                        Constants.Settings.DEFAULT_FIREWALL_POLICY
                    )
                    val isBlockAllDefault = defaultPolicy == Constants.Settings.POLICY_BLOCK_ALL

                    // A FIRST rule created by a BLOCK tap must not stamp the Block All default onto the
                    // networks the user did not touch. That only ever happens on a row already showing
                    // Allowed - one Block All is not reaching - so inheriting the default there turned
                    // one tap on one icon into a block on three, with an Undo that lifted only one.
                    // An UNBLOCK tap keeps the old behaviour: the row was showing those as blocked.
                    val rule = FirewallRule(
                        packageName = packageName,
                        userId = userId,
                        uid = appInfo.uid,
                        appName = getAppName(appInfo),
                        wifiBlocked = isBlockAllDefault && !(mobileBlocked || roamingBlocked) && stampDefaultOnUntouched,
                        mobileBlocked = mobileBlocked,
                        blockWhenRoaming = roamingBlocked,
                        enabled = true,
                        isSystemApp = isSystemApp(appInfo),
                        hasInternetPermission = hasNetworkPermissions(packageName, userId)
                    )
                    validateTarget(packageName, userId, identity, retained = false)
                    firewallRepository.insertRule(rule.copy(uid = identity.uid, userSerial = identity.userSerial))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
