package dev.bgcheck.network.data.mapper

import dev.bgcheck.network.data.database.entity.FirewallRuleEntity
import dev.bgcheck.network.domain.model.FirewallRule

fun FirewallRuleEntity.toDomain(): FirewallRule {
    return FirewallRule(
        packageName = packageName,
        userId = userId,
        uid = uid,
        appName = appName,
        wifiBlocked = wifiBlocked,
        mobileBlocked = mobileBlocked,
        blockWhenBackground = blockWhenBackground,
        blockWhenRoaming = blockWhenRoaming,
        lanBlocked = lanBlocked,
        enabled = enabled,
        isSystemApp = isSystemApp,
        hasInternetPermission = hasInternetPermission,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun FirewallRule.toEntity(): FirewallRuleEntity {
    return FirewallRuleEntity(
        packageName = packageName,
        userId = userId,
        uid = uid,
        appName = appName,
        wifiBlocked = wifiBlocked,
        mobileBlocked = mobileBlocked,
        blockWhenBackground = blockWhenBackground,
        blockWhenRoaming = blockWhenRoaming,
        lanBlocked = lanBlocked,
        enabled = enabled,
        isSystemApp = isSystemApp,
        hasInternetPermission = hasInternetPermission,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun List<FirewallRuleEntity>.toDomain(): List<FirewallRule> {
    return map { it.toDomain() }
}

fun List<FirewallRule>.toEntity(): List<FirewallRuleEntity> {
    return map { it.toEntity() }
}
