package dev.bgcheck.network.domain.model

data class UninstallBatchResult(
    val succeeded: List<String>,
    val failed: List<Pair<String, String>>
)
