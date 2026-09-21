package org.tool.kit.tests.feature.cleaner

import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.cleaner.*
import org.tool.kit.feature.cleaner.CleanerIntent.*
import org.tool.kit.model.Sequence
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.utils.formatFileSize

private fun cache(path: String, bytes: Long = 10, modifiedAt: Long = 0) =
    BuildDirectory("root", "root/$path", path, bytes, modifiedAt, true, true)

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class CleanerViewModelTest {
    @Test fun discoveriesAreIncrementalAndAllSixSortsSelectionAndStatisticsUseOneSnapshot() = runTest {
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler))
        try {
            assertEquals(0, f.capacityReads)
            f.vm.onIntent(Rescan("root")); assertEquals(CleanerPhase.Scanning, f.vm.uiState.value.phase); runCurrent()
            val first = cache("b/build", 30, 1); val second = cache("a/build", 10, 3); val third = cache("c/build.foo", 20, 2)
            f.scans.getValue("root").send(first); runCurrent()
            assertEquals(listOf(first.path), f.vm.uiState.value.items.map { it.id })
            f.vm.onIntent(ItemCheckedChanged(first.path, false))
            f.scans.getValue("root").send(second); runCurrent()
            assertEquals(CleanerPhase.Scanning, f.vm.uiState.value.phase)
            f.vm.onIntent(SortChanged(Sequence.DATE_NEW_TO_OLD))
            f.scans.getValue("root").send(third); runCurrent()
            assertEquals(listOf(second.path, third.path, first.path), f.vm.uiState.value.items.map { it.id })
            assertEquals(2, f.vm.uiState.value.checkedCount); assertEquals(30, f.vm.uiState.value.checkedBytes)
            f.scans.getValue("root").close(); runCurrent()
            assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase)
            val expectations = listOf(listOf(second, third, first), listOf(first, third, second), listOf(first, third, second),
                listOf(second, third, first), listOf(second, first, third), listOf(third, first, second))
            Sequence.entries.zip(expectations).forEach { (sort, expected) ->
                f.vm.onIntent(SortChanged(sort)); val state = f.vm.uiState.value
                assertEquals(sort, state.sort); assertEquals(expected.map { it.path }, state.items.map { it.path }); assertStatistics(state)
            }
            f.vm.onIntent(ToggleAll); assertTrue(f.vm.uiState.value.allSelected); assertEquals(60, f.vm.uiState.value.checkedBytes)
            f.vm.onIntent(ToggleAll); assertEquals(0, f.vm.uiState.value.checkedCount); assertStatistics(f.vm.uiState.value)
            f.vm.onIntent(ItemCheckedChanged(second.path, true)); assertStatistics(f.vm.uiState.value)
            f.vm.onIntent(CloseSelection); assertTrue(f.vm.uiState.value.items.isEmpty())
            assertEquals(Sequence.NAME_Z_TO_A, f.vm.uiState.value.sort)
        } finally { f.close() }
    }

    @Test fun oldNonCooperativeScanCannotOverwriteANewerScanSortOrClosedSelection() = runTest {
        val old = CompletableDeferred<Unit>(); val current = Channel<BuildDirectory>(Channel.UNLIMITED)
        val repo = object : BuildCachesRepository {
            override fun scan(root: String): Flow<BuildDirectory> = if (root == "old") object : Flow<BuildDirectory> {
                override suspend fun collect(collector: FlowCollector<BuildDirectory>) {
                    withContext(NonCancellable) { old.await(); collector.emit(cache("stale/build")) }
                }
            } else current.receiveAsFlow()
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult = error("Unexpected delete")
        }
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler), repo)
        try {
            f.vm.onIntent(Rescan("old")); runCurrent()
            f.vm.onIntent(Rescan("new")); runCurrent()
            f.vm.onIntent(SortChanged(Sequence.NAME_Z_TO_A))
            current.send(cache("a/build")); current.send(cache("z/build")); runCurrent()
            old.complete(Unit); runCurrent()
            assertEquals(listOf("root/z/build", "root/a/build"), f.vm.uiState.value.items.map { it.id })
            assertEquals(CleanerPhase.Scanning, f.vm.uiState.value.phase)
            f.vm.onIntent(CloseSelection); current.trySend(cache("late/build")); runCurrent()
            assertTrue(f.vm.uiState.value.items.isEmpty()); assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase)
        } finally { old.complete(Unit); current.close(); f.close() }
    }

    @Test fun confirmationUsesSelectedSnapshotAndCancelledOrStaleConfirmationNeverDeletes() = runTest {
        // Resource loading uses real IO. Let runTest's wall-clock timeout bound it and the effect waits.
        val completed = UiMessage.Text(getString(Res.string.cleanup_complete, 12L.formatFileSize()))
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler))
        try {
            f.vm.onIntent(RequestDelete); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.select_delete_director), f.effects.effects.first().snackbar.message)
            f.scanItems(this, listOf(cache("a/build", 12), cache("b/build", 30)))
            f.vm.onIntent(RequestDelete); assertTrue(f.vm.uiState.value.deleteConfirmVisible)
            f.vm.onIntent(DismissDelete); f.vm.onIntent(ConfirmDelete); runCurrent(); assertTrue(f.deleted.isEmpty())
            f.vm.onIntent(RequestDelete); f.vm.onIntent(Rescan("new")); f.vm.onIntent(ConfirmDelete); runCurrent(); assertTrue(f.deleted.isEmpty())
            f.scans.getValue("new").close(); runCurrent()
            f.scanItems(this, listOf(cache("a/build", 12), cache("b/build", 30)))
            f.vm.onIntent(ItemCheckedChanged("root/b/build", false))
            f.vm.onIntent(RequestDelete)
            f.vm.onIntent(ConfirmDelete); f.vm.onIntent(ConfirmDelete)
            assertEquals(CleanerPhase.Deleting, f.vm.uiState.value.phase)
            f.vm.onIntent(ToggleAll); f.vm.onIntent(Rescan("ignored")); f.vm.onIntent(CloseSelection)
            runCurrent(); assertEquals(listOf("root/a/build"), f.deleted.map { it.path })
            f.pending.single().complete(DeleteBuildCacheResult(f.deleted.single(), true, false, false)); runCurrent()
            f.effects.effects.first { it.snackbar.message == completed }
            assertEquals(listOf("root/b/build"), f.vm.uiState.value.items.map { it.id })
            assertFalse(f.vm.uiState.value.items.single().checked)
            assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase)
        } finally { f.close() }
    }

    @Test fun deletionRemovesSuccessesProgressivelyRetainsFailuresAndCountsScannedBytes() = runTest {
        val failedMessage = UiMessage.Text(getString(Res.string.file_deletion_exception, 1))
        val completed = UiMessage.Text(getString(Res.string.cleanup_complete, 30L.formatFileSize()))
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler))
        try {
            f.scanItems(this, listOf(cache("a/build", 40), cache("b/build", 30), cache("c/build", 20)))
            f.vm.onIntent(ItemCheckedChanged("root/c/build", false))
            f.vm.onIntent(RequestDelete); f.vm.onIntent(ConfirmDelete); runCurrent()
            f.pending[0].complete(DeleteBuildCacheResult(f.deleted[0], true, false, false)); runCurrent()
            assertEquals(CleanerPhase.Deleting, f.vm.uiState.value.phase)
            assertEquals(listOf("root/b/build", "root/c/build"), f.vm.uiState.value.items.map { it.id })
            f.pending[1].complete(DeleteBuildCacheResult(f.deleted[1], false, false, true)); runCurrent()
            f.effects.effects.first { it.snackbar.message == failedMessage }
            val failed = f.vm.uiState.value.items.first()
            assertTrue(failed.deleteFailed && failed.checked && failed.exists); assertTrue(failed.isDirectory) // Keep the scan type for deletion revalidation.
            assertEquals(30, failed.bytes); assertStatistics(f.vm.uiState.value)
            f.vm.onIntent(RequestDelete); f.vm.onIntent(ConfirmDelete); runCurrent()
            f.pending.last().complete(DeleteBuildCacheResult(f.deleted.last(), true, false, false)); runCurrent()
            f.effects.effects.first { it.snackbar.message == completed }
            assertEquals(listOf("root/c/build"), f.vm.uiState.value.items.map { it.id })
        } finally { f.close() }
    }

    @Test fun scanAndDeleteExceptionsReleaseBusyAndKeepRetryableItems() = runTest {
        val a = cache("a/build")
        val repo = object : BuildCachesRepository {
            override fun scan(root: String) = flow { emit(a); error("scan failure") }
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult = error("delete failure")
        }
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler), repo); val messages = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(messages) }
        try {
            f.vm.onIntent(Rescan("root")); runCurrent()
            assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase); assertEquals(1, f.vm.uiState.value.items.size)
            assertEquals(UiMessage.Resource(Res.string.scanning_anomalies), messages.single().snackbar.message)
            f.vm.onIntent(RequestDelete); f.vm.onIntent(ConfirmDelete); runCurrent()
            assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase); assertTrue(f.vm.uiState.value.items.single().deleteFailed)
        } finally { f.close() }
    }

    @Test fun closingWindowDiscardsNonCooperativeDeleteResultsAndStopsTheNextDeletion() = runTest {
        val gate = CompletableDeferred<Unit>(); val calls = mutableListOf<String>()
        val repo = object : BuildCachesRepository {
            override fun scan(root: String) = flowOf(cache("a/build", 20), cache("b/build", 10))
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult {
                calls += directory.path
                withContext(NonCancellable) { gate.await() }
                return DeleteBuildCacheResult(directory, true, false, false)
            }
        }
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler), repo); val messages = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(messages) }
        try {
            f.vm.onIntent(Rescan("root")); runCurrent(); f.vm.onIntent(RequestDelete); f.vm.onIntent(ConfirmDelete); runCurrent()
            f.store.clear(); gate.complete(Unit); runCurrent()
            assertEquals(listOf("root/a/build"), calls); assertTrue(messages.isEmpty())
            assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase)
            assertEquals(2, f.vm.uiState.value.items.size)
            f.vm.onIntent(Rescan("after-close")); assertEquals(CleanerPhase.Idle, f.vm.uiState.value.phase)
        } finally { gate.complete(Unit); f.close() }
    }

    @Test fun capacityRefreshKeepsTheLastGoodValueAndRejectsStaleReads() = runTest {
        val reads = mutableListOf<CompletableDeferred<StorageCapacity>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun readCapacity(): StorageCapacity = withContext(NonCancellable) {
                CompletableDeferred<StorageCapacity>().also { reads += it }.await()
            }
        }
        val f = CleanerVmFixture(StandardTestDispatcher(testScheduler), storage = storage)
        try {
            assertEquals(StorageCapacity(100, 20), f.vm.uiState.value.capacity)
            f.vm.onIntent(RefreshCapacity); runCurrent(); f.vm.onIntent(RefreshCapacity); runCurrent()
            reads[1].complete(StorageCapacity(200, 80)); runCurrent(); reads[0].complete(StorageCapacity(150, 10)); runCurrent()
            assertEquals(StorageCapacity(200, 80), f.vm.uiState.value.capacity)
            f.vm.onIntent(RefreshCapacity); runCurrent(); reads.last().completeExceptionally(IllegalStateException("unavailable")); runCurrent()
            assertEquals(StorageCapacity(200, 80), f.vm.uiState.value.capacity)
        } finally { reads.forEach { it.complete(StorageCapacity(1, 0)) }; f.close() }
    }

    private fun assertStatistics(state: CleanerUiState) {
        assertEquals(state.items.count { it.checked }, state.checkedCount)
        assertEquals(state.items.filter { it.checked }.sumOf { it.bytes }, state.checkedBytes)
        assertEquals(state.items.none { !it.checked }, state.allSelected)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class CleanerVmFixture(dispatcher: TestDispatcher, repository: BuildCachesRepository? = null, storage: StorageRepository? = null) {
    val scans = mutableMapOf<String, Channel<BuildDirectory>>()
    val deleted = mutableListOf<BuildDirectory>()
    val pending = mutableListOf<CompletableDeferred<DeleteBuildCacheResult>>()
    var capacityReads = 0
    val effects = AppEffectSink()
    val store = ViewModelStore()
    val vm: CleanerViewModel
    init {
        Dispatchers.setMain(dispatcher)
        val repo = repository ?: object : BuildCachesRepository {
            override fun scan(root: String) = Channel<BuildDirectory>(Channel.UNLIMITED).also { scans[root] = it }.receiveAsFlow()
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult {
                deleted += directory
                return CompletableDeferred<DeleteBuildCacheResult>().also { pending += it }.await()
            }
        }
        val storageSource = storage ?: object : StorageRepository by AllPathsExist {
            override suspend fun readCapacity() = AllPathsExist.readCapacity().also { capacityReads++ }
        }
        vm = CleanerViewModel(ScanBuildCachesUseCase(repo), DeleteBuildCachesUseCase(repo), storageSource, effects, StorageCapacity(100, 20))
        store.put("cleaner", vm)
    }
    suspend fun scanItems(scope: TestScope, items: List<BuildDirectory>) {
        vm.onIntent(Rescan("root")); scope.runCurrent()
        items.forEach { scans.getValue("root").send(it) }; scans.getValue("root").close(); scope.runCurrent()
    }
    fun close() { store.clear(); scans.values.forEach { it.close() }; effects.close(); Dispatchers.resetMain() }
}
