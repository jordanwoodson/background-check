package dev.bgcheck.network.domain.usecase

import dev.bgcheck.network.domain.model.ReinstallBatchResult
import dev.bgcheck.network.domain.model.UninstallBatchResult
import dev.bgcheck.network.domain.repository.PackageRepository

class ManagePackageUseCase constructor(
    private val packageRepository: PackageRepository
) {

    suspend fun setPackageEnabled(packageName: String, userId: Int, enabled: Boolean): Result<Unit> {
        return packageRepository.setPackageEnabled(packageName, userId, enabled)
    }

    suspend fun uninstallPackage(packageName: String, userId: Int): Result<Unit> {
        return packageRepository.uninstallPackage(packageName, userId)
    }

    suspend fun uninstallMultiplePackages(packages: List<Pair<String, Int>>): Result<UninstallBatchResult> {
        return packageRepository.uninstallMultiplePackages(packages)
    }

    suspend fun reinstallPackage(packageName: String, userId: Int): Result<Unit> {
        return packageRepository.reinstallPackage(packageName, userId)
    }

    suspend fun reinstallMultiplePackages(packages: List<Pair<String, Int>>): Result<ReinstallBatchResult> {
        return packageRepository.reinstallMultiplePackages(packages)
    }

    suspend fun forceStopPackage(packageName: String, userId: Int): Result<Unit> {
        return packageRepository.forceStopPackage(packageName, userId)
    }
}
