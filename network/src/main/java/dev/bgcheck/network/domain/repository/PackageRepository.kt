package dev.bgcheck.network.domain.repository

import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.ReinstallBatchResult
import dev.bgcheck.network.domain.model.UninstallBatchResult
import kotlinx.coroutines.flow.Flow

interface PackageRepository {

    fun getPackages(): Flow<List<Package>>

    suspend fun getUninstalledSystemPackages(): Result<List<Package>>

    suspend fun setPackageEnabled(packageName: String, userId: Int, enabled: Boolean): Result<Unit>

    suspend fun getPackage(packageName: String, userId: Int): Result<Package>

    suspend fun uninstallPackage(packageName: String, userId: Int): Result<Unit>

    suspend fun uninstallMultiplePackages(packages: List<Pair<String, Int>>): Result<UninstallBatchResult>

    suspend fun reinstallPackage(packageName: String, userId: Int): Result<Unit>

    suspend fun reinstallMultiplePackages(packages: List<Pair<String, Int>>): Result<ReinstallBatchResult>

    suspend fun forceStopPackage(packageName: String, userId: Int): Result<Unit>
}
