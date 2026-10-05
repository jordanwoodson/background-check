package dev.bgcheck.network.data.repository

import dev.bgcheck.network.data.history.NetworkChangeHistoryStore
import dev.bgcheck.network.data.history.NetworkChangeIdentityReader
import dev.bgcheck.network.data.history.NetworkChangeTransaction
import dev.bgcheck.network.data.history.NetworkChangeUnverifiedException
import dev.bgcheck.network.data.history.NetworkUndoPreflightException
import dev.bgcheck.network.domain.model.NetworkChangeGuard
import dev.bgcheck.network.domain.model.NetworkChangeKind
import dev.bgcheck.network.domain.model.NetworkChangeRecord
import dev.bgcheck.network.domain.model.NetworkChangeValue
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.PackageIdentity
import android.content.Context
import android.util.Log
import dev.bgcheck.network.R
import dev.bgcheck.network.data.common.De1984Error
import dev.bgcheck.network.data.datasource.PackageDataSource
import dev.bgcheck.network.data.model.toDomain
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.ReinstallBatchResult
import dev.bgcheck.network.domain.model.UninstallBatchResult
import dev.bgcheck.network.domain.repository.PackageRepository
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class PackageRepositoryImpl(
    private val context: Context,
    private val packageDataSource: PackageDataSource,
    private val accessMissing: suspend () -> Boolean,
    private val history: NetworkChangeHistoryStore? = null,
    private val onDataChanged: (() -> Unit)? = null
) : PackageRepository {

    private val enabledMutationMutex = Mutex()
    private val identityReader = NetworkChangeIdentityReader(context)

    // Only a known lack of root and Shizuku may be reported as missing access: it opens the access banner and dialog.
    private suspend fun actionFailed(noAccessMessage: Int, failedMessage: Int, operation: String): Result<Unit> =
        if (accessMissing()) {
            Result.failure(SecurityException(context.getString(noAccessMessage)))
        } else {
            Result.failure(De1984Error.SystemAccessFailed(context.getString(failedMessage), operation))
        }

    override fun getPackages(): Flow<List<Package>> {
        return packageDataSource.getPackages()
            .map { entities ->
                entities
                    .filter { !Constants.App.isOwnApp(it.packageName) }
                    .map { it.toDomain() }
            }
    }

    override suspend fun getUninstalledSystemPackages(): Result<List<Package>> {
        return try {
            val entities = packageDataSource.getUninstalledSystemPackages()
            val packages = entities.map { it.toDomain() }
            Result.success(packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setPackageEnabled(packageName: String, userId: Int, enabled: Boolean, expected: PackageIdentity?): Result<Unit> =
        withContext(Dispatchers.IO) {
            enabledMutationMutex.withLock {
                try {
                    val before = identityReader.read(packageName, userId)
                    val selected = (expected ?: PackageIdentity(before.identity.uid, before.identity.userSerial))
                        .let { if (it.firstInstallTime == null) it.copy(firstInstallTime = before.identity.firstInstallTime) else it }
                    selected.requireCurrent(userId, before.identity.uid, before.identity.userSerial, before.available,
                        before.identity.firstInstallTime)
                    val desired = NetworkChangeValue(packageEnabled = enabled, packageEnabledSetting = if (enabled) 1 else 3)
                    val prior = NetworkChangeValue(packageEnabled = before.enabled, packageEnabledSetting = before.enabledSetting, packageState = true)
                    val pending = NetworkChangeRecord(
                        id = UUID.randomUUID().toString(), packageName = packageName, label = before.label,
                        userId = userId, uid = before.identity.uid, userSerial = before.identity.userSerial,
                        firstInstallTime = before.identity.firstInstallTime, profileName = before.profileName,
                        timestamp = System.currentTimeMillis(), kind = NetworkChangeKind.PACKAGE_ENABLED,
                        before = prior, after = desired, recovery = true,
                    )
                    val operation: suspend () -> Unit = { changeEnabled(packageName, userId, enabled, selected).getOrThrow() }
                    if (history == null) operation()
                    else NetworkChangeTransaction(history).execute(listOf(pending), operation = operation, verify = { completed ->
                        val after = runCatching { identityReader.read(packageName, userId) }.getOrNull()
                        val observed = after?.let { NetworkChangeValue(packageEnabled = it.enabled,
                            packageEnabledSetting = it.enabledSetting, packageState = true) }
                        val verified = completed && NetworkChangeGuard.sameInstallation(before.identity, after?.identity) &&
                            before.enabledSetting != null && observed != null && desired.sameSetting(observed)
                        if (verified && prior.sameSetting(observed!!)) emptyList()
                        else listOf(pending.copy(after = observed ?: NetworkChangeValue(packageState = true), recovery = !verified, recoveryReason = when {
                            !completed -> "Package operation was interrupted or failed; inspect Android's current enabled setting."
                            !verified -> "Command completed, but the exact enabled setting or installation identity could not be verified. Undo is unavailable."
                            else -> null
                        }))
                    })
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (error is NetworkChangeUnverifiedException) {
                        packageDataSource.invalidateCache()
                        onDataChanged?.invoke()
                    }
                    Result.failure(error)
                }
            }
        }

    private suspend fun changeEnabled(packageName: String, userId: Int, enabled: Boolean, expected: PackageIdentity): Result<Unit> {
        val success = packageDataSource.setPackageEnabled(packageName, userId, enabled, expected = expected)
        return if (success) {
            onDataChanged?.invoke()
            Result.success(Unit)
        } else if (enabled) {
            actionFailed(R.string.error_unable_to_enable_package, R.string.error_package_enable_failed, "enable")
        } else {
            actionFailed(R.string.error_unable_to_disable_package, R.string.error_package_disable_failed, "disable")
        }
    }

    suspend fun restoreHistoryChange(record: NetworkChangeRecord): String = enabledMutationMutex.withLock {
        require(record.kind == NetworkChangeKind.PACKAGE_ENABLED)
        val snapshot = try {
            val current = identityReader.read(record.packageName, record.userId)
            NetworkChangeGuard.requireUndo(record, current.identity, current.available,
                NetworkChangeValue(packageEnabled = current.enabled, packageEnabledSetting = current.enabledSetting, packageState = true))
            current
        } catch (error: Exception) { throw NetworkUndoPreflightException(error) }
        val setting = requireNotNull(record.before.packageEnabledSetting)
        check(packageDataSource.setPackageEnabledSetting(record.packageName, record.userId, setting,
            PackageIdentity(record.uid, record.userSerial, record.firstInstallTime))) { "Android rejected the package enabled-state restoration" }
        onDataChanged?.invoke()
        val restored = identityReader.read(record.packageName, record.userId)
        val actual = NetworkChangeValue(packageEnabled = restored.enabled, packageEnabledSetting = restored.enabledSetting, packageState = true)
        check(restored.available && restored.identity == snapshot.identity && record.before.sameSetting(actual)) {
            "Package restoration could not be verified; inspect the recovery entry before trying again"
        }
        "Package enabled setting restored and verified."
    }

    override suspend fun getPackage(packageName: String, userId: Int): Result<Package> {
        return try {
            val entity = packageDataSource.getPackage(packageName, userId)
            if (entity != null) {
                Result.success(entity.toDomain())
            } else {
                Result.failure(Exception("Package not found: $packageName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uninstallPackage(packageName: String, userId: Int, expected: PackageIdentity?): Result<Unit> {
        return try {
            val success = packageDataSource.uninstallPackage(packageName, userId, expected = expected)
            if (success) {
                Result.success(Unit)
            } else {
                actionFailed(R.string.error_unable_to_uninstall_package, R.string.error_package_uninstall_failed, "uninstall")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uninstallMultiplePackages(packages: List<Pair<String, Int>>, expected: Map<PackageId, PackageIdentity>): Result<UninstallBatchResult> {
        return try {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<Pair<String, String>>()

            packages.forEach { (packageName, userId) ->
                val result = uninstallPackage(packageName, userId, expected[PackageId(packageName, userId)])
                result.fold(
                    onSuccess = { succeeded.add(packageName) },
                    onFailure = { error -> failed.add(packageName to (error.message ?: context.getString(R.string.error_unknown))) }
                )
            }

            Result.success(UninstallBatchResult(succeeded, failed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reinstallPackage(packageName: String, userId: Int, expected: PackageIdentity?): Result<Unit> {
        return try {
            val success = packageDataSource.reinstallPackage(packageName, userId, expected = expected)
            if (success) {
                Result.success(Unit)
            } else {
                actionFailed(R.string.error_unable_to_reinstall_package, R.string.error_package_reinstall_failed, "reinstall")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reinstallMultiplePackages(packages: List<Pair<String, Int>>, expected: Map<PackageId, PackageIdentity>): Result<ReinstallBatchResult> {
        return try {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<Pair<String, String>>()

            packages.forEach { (packageName, userId) ->
                val result = reinstallPackage(packageName, userId, expected[PackageId(packageName, userId)])
                result.fold(
                    onSuccess = { succeeded.add(packageName) },
                    onFailure = { error -> failed.add(packageName to (error.message ?: context.getString(R.string.error_unknown))) }
                )
            }

            Result.success(ReinstallBatchResult(succeeded, failed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int, expected: PackageIdentity?): Result<Unit> {
        return try {
            val success = packageDataSource.forceStopPackage(packageName, userId, expected = expected)
            if (success) {
                Result.success(Unit)
            } else {
                actionFailed(R.string.error_unable_to_force_stop_package, R.string.error_package_force_stop_failed, "force stop")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
