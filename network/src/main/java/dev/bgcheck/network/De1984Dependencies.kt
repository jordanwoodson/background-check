package dev.bgcheck.network

import dev.bgcheck.network.data.common.awaitPrivilegeProbes
import dev.bgcheck.network.data.common.hasPrivilegedAccess
import android.content.Context
import dev.bgcheck.network.utils.AppLogger
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.bgcheck.network.data.common.BootProtectionManager
import dev.bgcheck.network.data.common.CaptivePortalManager
import dev.bgcheck.network.data.common.ErrorHandler
import dev.bgcheck.network.data.common.PermissionManager
import dev.bgcheck.network.data.common.RootManager
import dev.bgcheck.network.data.common.ShizukuManager
import dev.bgcheck.network.data.database.De1984Database
import dev.bgcheck.network.data.database.dao.FirewallRuleDao
import dev.bgcheck.network.data.datasource.AndroidPackageDataSource
import dev.bgcheck.network.data.datasource.PackageDataSource
import dev.bgcheck.network.data.firewall.FirewallManager
import dev.bgcheck.network.data.firewall.IptablesFirewallBackend
import dev.bgcheck.network.data.monitor.NetworkStateMonitor
import dev.bgcheck.network.data.monitor.ScreenStateMonitor
import dev.bgcheck.network.data.repository.FirewallRepositoryImpl
import dev.bgcheck.network.data.repository.NetworkPackageRepositoryImpl
import dev.bgcheck.network.data.repository.PackageRepositoryImpl
import dev.bgcheck.network.data.service.NewAppNotificationManager
import dev.bgcheck.network.domain.repository.FirewallRepository
import dev.bgcheck.network.domain.repository.NetworkPackageRepository
import dev.bgcheck.network.domain.repository.PackageRepository
import dev.bgcheck.network.domain.usecase.*
import dev.bgcheck.network.ui.common.SuperuserBannerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class De1984Dependencies(private val context: Context) {

    companion object {
        private const val TAG = "De1984Dependencies"

        @Volatile
        private var INSTANCE: De1984Dependencies? = null

        fun getInstance(context: Context): De1984Dependencies {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: De1984Dependencies(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun get(): De1984Dependencies {
            return INSTANCE ?: throw IllegalStateException(
                "De1984Dependencies not initialized. Call getInstance(context) first."
            )
        }
    }


    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    private val _packageDataChanged = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val packageDataChanged: SharedFlow<Unit> = _packageDataChanged.asSharedFlow()

    fun notifyPackageDataChanged() {
        _packageDataChanged.tryEmit(Unit)
    }


    init {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                dev.bgcheck.network.data.multiuser.HiddenApiHelper.clearUserCache()
                packageDataSource.invalidateCache()
                notifyPackageDataChanged()
                applicationScope.launch(Dispatchers.IO) {
                    // Removal deletes only the departed user's rules; a later profile may reuse its ID.
                    if (intent.action == android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED) {
                        @Suppress("DEPRECATION")
                        val handle = intent.getParcelableExtra<android.os.UserHandle>(android.content.Intent.EXTRA_USER)
                        if (handle != null && dev.bgcheck.network.data.multiuser.HiddenApiHelper.currentProfile(context, handle.hashCode()) == null) {
                            firewallRepository.deleteRulesByUserId(handle.hashCode())
                        }
                    }
                    context.sendBroadcast(android.content.Intent("dev.bgcheck.network.FIREWALL_RULES_CHANGED").setPackage(context.packageName))
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            addAction(android.content.Intent.ACTION_USER_UNLOCKED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        // LauncherApps delivers package events from visible managed profiles too. A manifest
        // PACKAGE_ADDED receiver does not, and polling alone leaves new apps waiting for a tick.
        context.getSystemService(android.content.pm.LauncherApps::class.java).registerCallback(
            object : android.content.pm.LauncherApps.Callback() {
                private fun changed(packageName: String?, user: android.os.UserHandle, added: Boolean = false) {
                    dev.bgcheck.network.data.multiuser.HiddenApiHelper.clearInstalledAppsCache()
                    packageDataSource.invalidateCache()
                    notifyPackageDataChanged()
                    context.sendBroadcast(android.content.Intent("dev.bgcheck.network.FIREWALL_RULES_CHANGED").setPackage(context.packageName))
                    if (added && packageName != null) applicationScope.launch(Dispatchers.IO) {
                        val userId = user.hashCode()
                        val info = dev.bgcheck.network.data.multiuser.HiddenApiHelper.getApplicationInfoAsUser(context, packageName, 0, userId)
                        if (info != null && provideHandleNewAppInstallUseCase().execute(packageName, info.uid).isSuccess) {
                            newAppNotificationManager.showNewAppNotification(packageName, userId)
                        }
                    }
                }
                override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = changed(packageName, user, true)
                override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = changed(packageName, user)
                override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = changed(packageName, user)
                override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = changed(null, user)
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = changed(null, user)
            }, android.os.Handler(android.os.Looper.getMainLooper())
        )
    }

    /**
     * v1.0.0 shipped database version 3, and version 4 renamed `blockWhenScreenOff` to
     * `blockWhenBackground`. No 3->4 migration was ever written, so every install from before
     * 2025-11-17 fell through to fallbackToDestructiveMigration on its next update and lost every
     * firewall rule, with nothing but a logcat line to show for it.
     *
     * SQLite on minSdk 26 predates ALTER TABLE ... RENAME COLUMN, so the column is renamed the same
     * way MIGRATION_5_6 changes its primary key: create, copy, drop, rename.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE firewall_rules_new (
                    packageName TEXT NOT NULL,
                    uid INTEGER NOT NULL,
                    appName TEXT NOT NULL,
                    wifiBlocked INTEGER NOT NULL DEFAULT 0,
                    mobileBlocked INTEGER NOT NULL DEFAULT 0,
                    blockWhenBackground INTEGER NOT NULL DEFAULT 0,
                    blockWhenRoaming INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    isSystemApp INTEGER NOT NULL DEFAULT 0,
                    hasInternetPermission INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(packageName)
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO firewall_rules_new (
                    packageName, uid, appName, wifiBlocked, mobileBlocked,
                    blockWhenBackground, blockWhenRoaming, enabled,
                    isSystemApp, hasInternetPermission, createdAt, updatedAt
                )
                SELECT
                    packageName, uid, appName, wifiBlocked, mobileBlocked,
                    blockWhenScreenOff, blockWhenRoaming, enabled,
                    isSystemApp, hasInternetPermission, createdAt, updatedAt
                FROM firewall_rules
            """.trimIndent())

            db.execSQL("DROP TABLE firewall_rules")
            db.execSQL("ALTER TABLE firewall_rules_new RENAME TO firewall_rules")

            AppLogger.i(TAG, "Database migrated to version 4: blockWhenScreenOff renamed to blockWhenBackground")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN lanBlocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN userId INTEGER NOT NULL DEFAULT 0")

            // Recreate table with composite primary key (packageName, userId)
            // Room doesn't support changing primary key via ALTER TABLE, so we need to:
            // 1. Create new table with correct schema
            // 2. Copy data from old table
            // 3. Drop old table
            // 4. Rename new table
            db.execSQL("""
                CREATE TABLE firewall_rules_new (
                    packageName TEXT NOT NULL,
                    userId INTEGER NOT NULL DEFAULT 0,
                    uid INTEGER NOT NULL,
                    appName TEXT NOT NULL,
                    wifiBlocked INTEGER NOT NULL DEFAULT 0,
                    mobileBlocked INTEGER NOT NULL DEFAULT 0,
                    blockWhenBackground INTEGER NOT NULL DEFAULT 0,
                    blockWhenRoaming INTEGER NOT NULL DEFAULT 0,
                    lanBlocked INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    isSystemApp INTEGER NOT NULL DEFAULT 0,
                    hasInternetPermission INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(packageName, userId)
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO firewall_rules_new (
                    packageName, userId, uid, appName, wifiBlocked, mobileBlocked,
                    blockWhenBackground, blockWhenRoaming, lanBlocked, enabled,
                    isSystemApp, hasInternetPermission, createdAt, updatedAt
                )
                SELECT
                    packageName, userId, uid, appName, wifiBlocked, mobileBlocked,
                    blockWhenBackground, blockWhenRoaming, lanBlocked, enabled,
                    isSystemApp, hasInternetPermission, createdAt, updatedAt
                FROM firewall_rules
            """.trimIndent())

            db.execSQL("DROP TABLE firewall_rules")
            db.execSQL("ALTER TABLE firewall_rules_new RENAME TO firewall_rules")

            AppLogger.i(TAG, "Database migrated to version 6: Added userId column for multi-user support")
        }
    }

    val database: De1984Database by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            De1984Database::class.java,
            "de1984_database"
        )
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, dev.bgcheck.network.data.database.FirewallDatabaseMigrations.profileIdentity(context))
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    AppLogger.w(TAG, "Database destructive migration triggered - firewall rules reset to defaults")
                }
            })
            .build()
    }

    val firewallRuleDao: FirewallRuleDao by lazy {
        database.firewallRuleDao()
    }


    val rootManager: RootManager by lazy {
        RootManager(context)
    }

    val shizukuManager: ShizukuManager by lazy {
        ShizukuManager(context)
    }

    val errorHandler: ErrorHandler by lazy {
        ErrorHandler()
    }

    val permissionManager: PermissionManager by lazy {
        PermissionManager(context, rootManager, shizukuManager)
    }

    val superuserBannerState: SuperuserBannerState by lazy {
        SuperuserBannerState()
    }

    val captivePortalManager: CaptivePortalManager by lazy {
        CaptivePortalManager(context, rootManager, shizukuManager)
    }

    val bootProtectionManager: BootProtectionManager by lazy {
        BootProtectionManager(context, rootManager, shizukuManager)
    }


    val firewallRepository: FirewallRepository by lazy {
        FirewallRepositoryImpl(firewallRuleDao, context) { notifyPackageDataChanged() }
    }

    // Note: PackageDataSource depends on FirewallRepository (circular dependency)
    // We use lazy initialization to break the cycle
    val packageDataSource: PackageDataSource by lazy {
        AndroidPackageDataSource(context, firewallRepository, shizukuManager)
    }

    val packageRepository: PackageRepository by lazy {
        PackageRepositoryImpl(
            context,
            packageDataSource,
            accessMissing = {
                awaitPrivilegeProbes(rootManager.rootStatus, shizukuManager.shizukuStatus, FirewallManager.PRIVILEGE_ANSWER_TIMEOUT_MS) &&
                    !hasPrivilegedAccess(rootManager.rootStatus.value, shizukuManager.shizukuStatus.value)
            }
        ) { notifyPackageDataChanged() }
    }

    val networkPackageRepository: NetworkPackageRepository by lazy {
        NetworkPackageRepositoryImpl(context, packageDataSource)
    }


    val newAppNotificationManager: NewAppNotificationManager by lazy {
        NewAppNotificationManager(context) { firewallManager.activeBackendType.value }
    }

    val screenStateMonitor: ScreenStateMonitor by lazy {
        ScreenStateMonitor(context)
    }

    val networkStateMonitor: NetworkStateMonitor by lazy {
        NetworkStateMonitor(context)
    }

    /**
     * The one and only iptables backend in this process.
     *
     * It has to be a singleton because it is **stateful about the kernel**: `chainNeedsResync`,
     * `blockedUids` and `blockedLanUids` describe one chain, `de1984_output`, and there is only one
     * of those. FirewallManager and PrivilegedFirewallService used to build one each, and each
     * arrived with `chainNeedsResync` already true - the service via `startInternal()`, the manager
     * simply from the field's initialiser, since the manager never calls `startInternal()` at all.
     * So every start paid for TWO full chain rewrites, measured at 5.9 s + 10.4 s of backend work
     * on a 110-package device, and neither instance could ever see the other had just done it.
     * Sharing one object cut the backend total to 6.2 s.
     *
     * Everything else in this file is a singleton for the same reason; this one was missed.
     */
    val iptablesBackend: IptablesFirewallBackend by lazy {
        IptablesFirewallBackend(context, rootManager, shizukuManager, errorHandler)
    }

    val firewallManager: FirewallManager by lazy {
        FirewallManager(
            context = context,
            iptablesBackend = iptablesBackend,
            rootManager = rootManager,
            shizukuManager = shizukuManager,
            errorHandler = errorHandler,
            firewallRepository = firewallRepository,
            networkStateMonitor = networkStateMonitor,
            screenStateMonitor = screenStateMonitor
        )
    }


    fun provideGetNetworkPackagesUseCase(): GetNetworkPackagesUseCase {
        return GetNetworkPackagesUseCase(networkPackageRepository)
    }

    fun provideManageNetworkAccessUseCase(): ManageNetworkAccessUseCase {
        return ManageNetworkAccessUseCase(networkPackageRepository)
    }

    fun provideGetPackagesUseCase(): GetPackagesUseCase {
        return GetPackagesUseCase(packageRepository)
    }

    fun provideManagePackageUseCase(): ManagePackageUseCase {
        return ManagePackageUseCase(packageRepository)
    }

    fun provideSmartPolicySwitchUseCase(): SmartPolicySwitchUseCase {
        return SmartPolicySwitchUseCase(firewallRepository, context)
    }

    fun provideGetFirewallRulesUseCase(): GetFirewallRulesUseCase {
        return GetFirewallRulesUseCase(firewallRepository)
    }

    fun provideHandleNewAppInstallUseCase(): HandleNewAppInstallUseCase {
        return HandleNewAppInstallUseCase(context, firewallRepository, errorHandler)
    }

    fun provideUpdateFirewallRuleUseCase(): UpdateFirewallRuleUseCase {
        return UpdateFirewallRuleUseCase(firewallRepository)
    }

    fun provideEnsureSystemRecommendedRulesUseCase(): EnsureSystemRecommendedRulesUseCase {
        return EnsureSystemRecommendedRulesUseCase(context, firewallRepository)
    }
}
