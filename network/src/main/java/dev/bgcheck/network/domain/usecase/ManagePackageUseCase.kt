package dev.bgcheck.network.domain.usecase

import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.PackageIdentity
import dev.bgcheck.network.domain.model.ReinstallBatchResult
import dev.bgcheck.network.domain.model.UninstallBatchResult
import dev.bgcheck.network.domain.repository.PackageRepository

class ManagePackageUseCase constructor(
    private val packageRepository: PackageRepository
) {

    suspend fun setPackageEnabled(packageName: String, userId: Int, enabled: Boolean, expected: PackageIdentity? = null): Result<Unit> {
        return packageRepository.setPackageEnabled(packageName, userId, enabled, expected = expected)
    }

    suspend fun uninstallPackage(packageName: String, userId: Int, expected: PackageIdentity? = null): Result<Unit> {
        return packageRepository.uninstallPackage(packageName, userId, expected = expected)
    }

    suspend fun uninstallMultiplePackages(packages: List<Pair<String, Int>>, expected: Map<PackageId, PackageIdentity> = emptyMap()): Result<UninstallBatchResult> {
        return packageRepository.uninstallMultiplePackages(packages, expected)
    }

    suspend fun reinstallPackage(packageName: String, userId: Int, expected: PackageIdentity? = null): Result<Unit> {
        return packageRepository.reinstallPackage(packageName, userId, expected = expected)
    }

    suspend fun reinstallMultiplePackages(packages: List<Pair<String, Int>>, expected: Map<PackageId, PackageIdentity> = emptyMap()): Result<ReinstallBatchResult> {
        return packageRepository.reinstallMultiplePackages(packages, expected)
    }

    suspend fun forceStopPackage(packageName: String, userId: Int, expected: PackageIdentity? = null): Result<Unit> {
        return packageRepository.forceStopPackage(packageName, userId, expected = expected)
    }
}
