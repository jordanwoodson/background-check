package dev.bgcheck.network.domain.usecase

import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.repository.FirewallRepository

class UpdateFirewallRuleUseCase constructor(
    private val firewallRepository: FirewallRepository
) {
    suspend operator fun invoke(rule: FirewallRule) {
        val updatedRule = rule.copy(updatedAt = System.currentTimeMillis())
        firewallRepository.updateRule(updatedRule)
    }

    suspend fun insertOrUpdate(rule: FirewallRule) {
        val updatedRule = rule.copy(updatedAt = System.currentTimeMillis())
        firewallRepository.insertRule(updatedRule)
    }
}
