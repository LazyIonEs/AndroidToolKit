package org.tool.kit.data.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.*

@OptIn(ExperimentalSettingsApi::class)
class DefaultCleanerRulesRepository(private val settings: FlowSettings) : CleanerRulesRepository {
    private val mutableState = MutableStateFlow(CleanerRuleConfigSnapshot())
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val operation = MutableStateFlow("idle")
    private val json = Json { encodeDefaults = true }

    override suspend fun awaitReady(): CleanerRuleConfigSnapshot = mutex.withLock {
        if (!state.value.ready) {
            var unsupported = false
            mutableState.value = try {
                val raw = settings.getStringOrNull(KEY)
                val config = if (raw == null) CleanerRuleConfig() else {
                    val tree = json.parseToJsonElement(raw).jsonObject
                    val version = tree["schemaVersion"]?.jsonPrimitive?.intOrNull ?: error("Missing schema")
                    unsupported = version != 1
                    check(!unsupported)
                    json.decodeFromString<CleanerRuleConfig>(raw).also { check(validateCleanerRules(it).valid) }
                }
                CleanerRuleConfigSnapshot(config.normalized(), ready = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { CleanerRuleConfigSnapshot(ready = true, recovered = true, unsupportedVersion = unsupported) }
        }
        state.value
    }

    override suspend fun save(config: CleanerRuleConfig) {
        awaitReady()
        check(operation.compareAndSet("idle", "saving")) { "Cleaner is busy" }
        try { mutex.withLock {
            check(!state.value.unsupportedVersion) { "Unsupported schema version" }
            check(config.revision == state.value.config.revision) { "Stale draft" }
            val normalized = config.normalized()
            require(validateCleanerRules(normalized).valid)
            val committed = normalized.copy(revision = state.value.config.revision + 1)
            withContext(NonCancellable) {
                settings.putString(KEY, json.encodeToString(committed))
                mutableState.value = CleanerRuleConfigSnapshot(committed, ready = true)
            }
        } } finally { operation.value = "idle" }
    }

    override fun beginDeletion() = operation.compareAndSet("idle", "deleting")
    override fun endDeletion() { operation.compareAndSet("deleting", "idle") }
    companion object { const val KEY = "cleaner_rule_config_v1" }
}
