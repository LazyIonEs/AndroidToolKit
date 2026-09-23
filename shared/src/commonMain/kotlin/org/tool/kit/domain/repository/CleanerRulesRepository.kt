package org.tool.kit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import org.tool.kit.domain.cleaner.CleanerRuleConfig

data class CleanerRuleConfigSnapshot(
    val config: CleanerRuleConfig = CleanerRuleConfig(),
    val ready: Boolean = false,
    val recovered: Boolean = false,
    val unsupportedVersion: Boolean = false,
)

interface CleanerRulesRepository {
    val state: StateFlow<CleanerRuleConfigSnapshot>
    suspend fun awaitReady(): CleanerRuleConfigSnapshot
    suspend fun save(config: CleanerRuleConfig)
    fun beginDeletion(): Boolean
    fun endDeletion()
}
