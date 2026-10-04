package dev.bgcheck.network.domain.usecase

import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.repository.FirewallRepository
import kotlinx.coroutines.flow.Flow

class GetFirewallRulesUseCase constructor(
    private val firewallRepository: FirewallRepository
) {
    operator fun invoke(): Flow<List<FirewallRule>> {
        return firewallRepository.getAllRules()
    }

    fun getBlockedRules(): Flow<List<FirewallRule>> {
        return firewallRepository.getBlockedRules()
    }

    fun getAllowedRules(): Flow<List<FirewallRule>> {
        return firewallRepository.getAllowedRules()
    }

    fun getUserAppRules(): Flow<List<FirewallRule>> {
        return firewallRepository.getUserAppRules()
    }

    fun getSystemAppRules(): Flow<List<FirewallRule>> {
        return firewallRepository.getSystemAppRules()
    }
}
