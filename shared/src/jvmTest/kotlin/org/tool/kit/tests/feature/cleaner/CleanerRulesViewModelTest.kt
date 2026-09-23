package org.tool.kit.tests.feature.cleaner

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.*
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.data.repository.DefaultCleanerRulesRepository
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.*
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.cleaner.*
import org.tool.kit.tests.support.AllPathsExist

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class CleanerRulesViewModelTest {
    @Test fun draftCancelRestoreReorderAndSaveRemainIndependentFromStoredSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        val vm = CleanerRulesViewModel(repo)
        val store = ViewModelStore().also { it.put("rules", vm) }
        try {
            vm.open(); runCurrent()
            vm.addRule("Logs")
            val added = vm.uiState.value.draft.rules.last()
            assertTrue(added.defaultSelected); assertFalse(vm.uiState.value.canSave)
            vm.updateRule(added.copy(enabled = false))
            assertTrue(vm.uiState.value.canSave)
            vm.copyRule(defaultBuildRule(), "Copy")
            val copy = vm.uiState.value.draft.rules.last()
            assertNotEquals(defaultBuildRule().id, copy.id)
            vm.moveRule(copy.id, -1)
            assertEquals(copy.id, vm.uiState.value.draft.rules[1].id)
            assertEquals(CleanerRuleConfig(), repo.state.value.config)
            vm.open(); runCurrent() // Cancel discards all draft changes.
            assertEquals(CleanerRuleConfig(), vm.uiState.value.draft)
            vm.edit { it.copy(includeHidden = false) }; vm.changeDepth("12")
            vm.save(true); runCurrent()
            assertTrue(vm.uiState.value.saved && vm.uiState.value.tryRun)
            assertEquals(12, repo.state.value.config.maxDepth); assertFalse(repo.state.value.config.includeHidden)
            vm.open(); runCurrent(); vm.restoreDefault()
            assertEquals(12, repo.state.value.config.maxDepth)
            assertEquals("10", vm.uiState.value.depthText)
            assertEquals(1, vm.uiState.value.draft.revision)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun duplicateConditionRowsKeepIdentityAndSaveImmediatelyAfterRemoval() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        val vm = CleanerRulesViewModel(repo)
        val store = ViewModelStore().also { it.put("rules", vm) }
        try {
            vm.open(); runCurrent(); vm.addRule("New rule")
            val ruleId = vm.uiState.value.draft.rules.last().id
            val first = vm.uiState.value.conditionRows.getValue(ruleId).single().id
            vm.updateCondition(ruleId, first, CleanerCondition.Text(value = "same"))
            vm.addCondition(ruleId, CleanerCondition.Text(value = "same"))
            val second = vm.uiState.value.conditionRows.getValue(ruleId).last().id
            assertNotEquals(first, second)
            vm.removeCondition(ruleId, first)
            // A callback from the disappearing row cannot change a surviving duplicate.
            vm.updateCondition(ruleId, first, CleanerCondition.Text(value = "stale"))
            assertEquals(second, vm.uiState.value.conditionRows.getValue(ruleId).single().id)
            vm.updateCondition(ruleId, second, CleanerCondition.Text(value = "kept"))
            vm.save(false); runCurrent()
            val saved = repo.state.value.config.rules.last()
            assertTrue(saved.defaultSelected)
            assertEquals(listOf(CleanerCondition.Text(value = "kept")), saved.conditions)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun savingCancelsOldScanClearsResultsAndNextScanUsesNewSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val rules = DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        val requests = mutableListOf<CleanerScanRequest>()
        val discoveries = mutableListOf<Channel<BuildDirectory>>()
        var cancelled = false
        val repo = object : BuildCachesRepository {
            override fun scan(root: String): Flow<BuildDirectory> = error("Snapshot required")
            override fun scan(request: CleanerScanRequest, onIssue: (String) -> Unit): Flow<BuildDirectory> {
                requests += request
                val channel = Channel<BuildDirectory>(Channel.UNLIMITED).also { discoveries += it }
                return flow { try { emitAll(channel.receiveAsFlow()) } finally { cancelled = true } }
            }
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult = error("Unexpected deletion")
        }
        val effects = AppEffectSink()
        val vm = CleanerViewModel(ScanBuildCachesUseCase(repo), DeleteBuildCachesUseCase(repo), AllPathsExist, effects, rules = rules)
        val store = ViewModelStore().also { it.put("cleaner", vm) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { effects.effects.collect() }
        try {
            runCurrent(); vm.onIntent(CleanerIntent.Rescan("root")); runCurrent()
            discoveries[0].send(BuildDirectory("root", "root/build", "build", 10, 0, true, true)); runCurrent()
            assertEquals(1, vm.uiState.value.items.size)
            val config = rules.state.value.config.copy(maxDepth = 2, rules = listOf(defaultBuildRule().copy(name = "Updated")))
            rules.save(config); runCurrent()
            assertTrue(cancelled); assertTrue(vm.uiState.value.items.isEmpty()); assertTrue(vm.uiState.value.needsRescan)
            discoveries[0].trySend(BuildDirectory("root", "root/stale", "stale", 10, 0, true, true)); runCurrent()
            assertTrue(vm.uiState.value.items.isEmpty())
            vm.onIntent(CleanerIntent.Rescan("new-root")); runCurrent()
            assertEquals(0, requests[0].configRevision); assertEquals(10, requests[0].maxDepth)
            assertEquals(1, requests[1].configRevision); assertEquals(2, requests[1].maxDepth)
            assertEquals("Updated", requests[1].rules.single().name)
        } finally { store.clear(); discoveries.forEach { it.close() }; effects.close(); Dispatchers.resetMain() }
    }

    @Test fun countsDefaultSelectionConfirmationAndDeletionLockUseTheScannedItems() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val rules = DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        val file = BuildDirectory("root", "root/a.log", "a.log", 20, 0, false, true, defaultSelected = false)
        val directory = BuildDirectory("root", "root/build", "build", 30, 0, true, true)
        val deleted = mutableListOf<BuildDirectory>()
        val gate = CompletableDeferred<Unit>()
        val repo = object : BuildCachesRepository {
            override fun scan(root: String) = flowOf(file, directory)
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult {
                deleted += directory; gate.await()
                return DeleteBuildCacheResult(directory, false, false, true, safetyFailure = true)
            }
        }
        val effects = AppEffectSink()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { effects.effects.collect() }
        val vm = CleanerViewModel(ScanBuildCachesUseCase(repo), DeleteBuildCachesUseCase(repo), AllPathsExist, effects, rules = rules)
        val store = ViewModelStore().also { it.put("vm", vm) }
        try {
            runCurrent(); vm.onIntent(CleanerIntent.Rescan("root")); runCurrent()
            assertEquals(1, vm.uiState.value.checkedDirectories); assertEquals(0, vm.uiState.value.checkedFiles)
            vm.onIntent(CleanerIntent.ItemCheckedChanged(file.path, true))
            assertEquals(1, vm.uiState.value.checkedFiles); assertEquals(50, vm.uiState.value.checkedBytes)
            vm.onIntent(CleanerIntent.RequestDelete)
            vm.onIntent(CleanerIntent.ToggleAll) // A later selection cannot mutate the confirmation snapshot.
            assertEquals(2, vm.uiState.value.confirmationItems.size)
            vm.onIntent(CleanerIntent.ConfirmDelete); runCurrent()
            assertFailsWith<IllegalStateException> { rules.save(rules.state.value.config) }
            gate.complete(Unit); runCurrent()
            assertEquals(setOf(file.path, directory.path), deleted.map { it.path }.toSet())
            assertTrue(vm.uiState.value.items.all { it.safetyFailure })
            assertTrue(vm.uiState.value.items.first { it.id == directory.path }.isDirectory)
            rules.save(rules.state.value.config); runCurrent()
            assertTrue(vm.uiState.value.items.isEmpty())
        } finally { gate.complete(Unit); store.clear(); effects.close(); Dispatchers.resetMain() }
    }
}
