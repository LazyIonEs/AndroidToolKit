package org.tool.kit.feature.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tool.kit.domain.cleaner.CleanerCondition
import org.tool.kit.domain.cleaner.CleanerRuleConfig
import org.tool.kit.domain.cleaner.CleanerRuleGroup
import org.tool.kit.domain.cleaner.validateCleanerRules
import org.tool.kit.domain.repository.CleanerRulesRepository
import kotlin.uuid.Uuid

/** Stable identities belong to the editor draft, never to the persisted rule format. */
data class CleanerConditionRow(val id: String, val condition: CleanerCondition)

private fun conditionRows(config: CleanerRuleConfig) = config.rules.associate { rule ->
    rule.id to rule.conditions.map { CleanerConditionRow(Uuid.random().toString(), it) }
}

/** A page-local draft. Only a successful repository commit changes the scanner configuration. */
data class CleanerRulesUiState(
    val draft: CleanerRuleConfig = CleanerRuleConfig(),
    val conditionRows: Map<String, List<CleanerConditionRow>> = conditionRows(draft),
    val addedRuleIds: Set<String> = emptySet(),
    val depthText: String = "10",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val unsupported: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
    val tryRun: Boolean = false,
) {
    val validation get() = validateCleanerRules(draft.copy(maxDepth = depthText.toIntOrNull() ?: 0))
    val canSave get() = !loading && !saving && !unsupported && validation.valid
}

class CleanerRulesViewModel(private val repository: CleanerRulesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CleanerRulesUiState())
    val uiState = mutableState.asStateFlow()

    fun open() {
        if (uiState.value.saving) return
        mutableState.value = CleanerRulesUiState()
        viewModelScope.launch {
            val snapshot = repository.awaitReady()
            mutableState.value = CleanerRulesUiState(
                draft = snapshot.config.copy(rules = snapshot.config.rules.map {
                    it.copy(conditions = it.conditions.toList())
                }),
                depthText = snapshot.config.maxDepth.toString(),
                loading = false,
                unsupported = snapshot.unsupportedVersion
            )
        }
    }

    fun edit(transform: (CleanerRuleConfig) -> CleanerRuleConfig) {
        if (uiState.value.loading || uiState.value.saving) return
        mutableState.update { state ->
            val draft = transform(state.draft)
            val rows = draft.rules.associate { rule ->
                val previous = state.conditionRows[rule.id].orEmpty()
                val available = previous.toMutableList()
                rule.id to rule.conditions.mapIndexed { index, condition ->
                    val existing = available.firstOrNull { it.condition === condition }
                        ?: available.firstOrNull { it.condition == condition }
                        ?: previous.getOrNull(index)
                            ?.takeIf { previous.size == rule.conditions.size && it in available }
                    if (existing != null) available.remove(existing)
                    CleanerConditionRow(existing?.id ?: Uuid.random().toString(), condition)
                }
            }
            state.copy(
                draft = draft, conditionRows = rows, saveFailed = false,
                addedRuleIds = (state.addedRuleIds + draft.rules.map { it.id }
                    .filter { id -> state.draft.rules.none { it.id == id } }).intersect(draft.rules.map { it.id }
                    .toSet())
            )
        }
    }

    fun updateRule(rule: CleanerRuleGroup) =
        edit { config -> config.copy(rules = config.rules.map { if (it.id == rule.id) rule else it }) }

    fun addRule(name: String) = edit {
        it.copy(
            rules = it.rules + CleanerRuleGroup(
                Uuid.random().toString(),
                name,
                defaultSelected = true,
                conditions = listOf(CleanerCondition.Text())
            )
        )
    }

    fun copyRule(rule: CleanerRuleGroup, name: String) = edit {
        it.copy(
            rules = it.rules + rule.copy(
                id = Uuid.random().toString(),
                name = name,
                conditions = rule.conditions.toList()
            )
        )
    }

    fun addCondition(ruleId: String, condition: CleanerCondition) = edit { config ->
        config.copy(rules = config.rules.map { if (it.id == ruleId) it.copy(conditions = it.conditions + condition) else it })
    }

    fun updateCondition(ruleId: String, rowId: String, condition: CleanerCondition) {
        val state = uiState.value
        if (state.loading || state.saving) return
        val rows = state.conditionRows[ruleId] ?: return
        if (rows.none { it.id == rowId }) return
        val updated = rows.map { if (it.id == rowId) it.copy(condition = condition) else it }
        mutableState.value = state.copy(
            draft = state.draft.copy(rules = state.draft.rules.map {
                if (it.id == ruleId) it.copy(
                    conditions = updated.map { row -> row.condition }) else it
            }),
            conditionRows = state.conditionRows + (ruleId to updated), saveFailed = false,
        )
    }

    fun removeCondition(ruleId: String, rowId: String) {
        val state = uiState.value
        if (state.loading || state.saving) return
        val rows = state.conditionRows[ruleId].orEmpty().filterNot { it.id == rowId }
        mutableState.value = state.copy(
            draft = state.draft.copy(rules = state.draft.rules.map {
                if (it.id == ruleId) it.copy(
                    conditions = rows.map { row -> row.condition }) else it
            }),
            conditionRows = state.conditionRows + (ruleId to rows), saveFailed = false,
        )
    }

    fun deleteRule(id: String) = edit { it.copy(rules = it.rules.filterNot { it.id == id }) }
    fun moveRule(id: String, offset: Int) = edit { config ->
        val rules = config.rules.toMutableList()
        val index = rules.indexOfFirst { it.id == id };
        val destination = index + offset
        if (index >= 0 && destination in rules.indices) {
            val rule = rules.removeAt(index); rules.add(destination, rule)
        }
        config.copy(rules = rules)
    }

    fun changeDepth(value: String) {
        if (!uiState.value.saving) mutableState.update { it.copy(depthText = value) }
    }

    fun restoreDefault() {
        edit { CleanerRuleConfig(revision = it.revision) }
        changeDepth("10")
    }

    fun save(tryRun: Boolean) {
        val state = uiState.value
        if (!state.canSave) return
        mutableState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(state.draft.copy(maxDepth = state.depthText.toInt()))
                mutableState.update { it.copy(saving = false, saved = true, tryRun = tryRun) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
