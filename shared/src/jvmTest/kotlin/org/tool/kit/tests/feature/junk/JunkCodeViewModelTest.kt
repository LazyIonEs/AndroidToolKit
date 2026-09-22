package org.tool.kit.tests.feature.junk

import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import org.tool.kit.model.JunkMode
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.junkViewModel
import org.tool.kit.utils.formatFileSize

@OptIn(ExperimentalCoroutinesApi::class)
class JunkCodeViewModelTest {
    @Test fun bothModesCaptureEveryFieldBeforeSuspensionAndDuplicateSubmitsAreIgnored() = runTest {
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler)); val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent()
            listOf(OutputPathChanged(" output "), PackageNameChanged("com.fixture"), SuffixChanged("a.b"), PackageCountChanged("0002"), ActivityCountChanged("3"), ResPrefixChanged(" prefix ")).forEach(f.vm::onIntent)
            runCurrent(); f.vm.onIntent(Submit); f.vm.onIntent(Submit)
            assertTrue(f.vm.uiState.value.busy)
            assertEquals(GenerateJunkCodeRequest(" output ", JunkConfiguration.Single("com.fixture.a.b", 2, 3, " prefix ")), f.requests.single())
            listOf(ModeChanged(JunkMode.MULTI), OutputDirChanged(" batch "), AarCountChanged("2"), LeastPackagesChanged("1"), MaximumPackagesChanged("3"), LeastActivitiesChanged("2"), MaximumActivitiesChanged("4")).forEach(f.vm::onIntent)
            f.pending.single().complete(GeneratedJunkCode("original-single.aar", listOf("original-single.aar"), 12345)); runCurrent(); f.vm.uiState.first { !it.busy }
            assertFalse(f.vm.uiState.value.busy)
            assertEquals(SnackbarAction.OpenDirectory("original-single.aar"), events.single().snackbar.action)
            assertEquals(UiMessage.Text(getString(Res.string.build_end, 12345L.formatFileSize())), events.single().snackbar.message)
            f.vm.onIntent(Submit)
            assertEquals(JunkConfiguration.Multi(" batch ", 2, 1, 3, 2, 4), f.requests.last().configuration)
            assertEquals("0002", f.vm.uiState.value.single.packageCount)
            f.pending.last().complete(GeneratedJunkCode(" output / batch ", emptyList(), 0)); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(SnackbarAction.OpenDirectory(" output / batch "), events.last().snackbar.action)
        } finally { f.close() }
    }

    @Test fun validationKeepsErrorPriorityAndChecksBothHiddenDrafts() = runTest {
        val gates = mutableListOf<CompletableDeferred<PathMetadata>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String) = CompletableDeferred<PathMetadata>().also { gates += it }.await()
        }
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler), storage); val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(SuffixChanged("")); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            gates.last().complete(PathMetadata(false, true)); runCurrent(); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_empty), events.last().snackbar.message)
            f.vm.onIntent(SuffixChanged("fixed")); f.vm.onIntent(AarCountChanged("")); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_empty), events.last().snackbar.message)
            f.vm.onIntent(AarCountChanged("2")); f.vm.onIntent(ModeChanged(JunkMode.MULTI)); f.vm.onIntent(ResPrefixChanged(" ")); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_empty), events.last().snackbar.message)
            assertTrue(f.requests.isEmpty()); assertFalse(f.vm.uiState.value.busy)
            // A pending path created in the same event turn must gate submit immediately.
            f.vm.onIntent(ResPrefixChanged("f_")); f.vm.onIntent(OutputPathChanged("new")); f.vm.onIntent(Submit); runCurrent()
            assertTrue(f.requests.isEmpty()); assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            gates.last().complete(PathMetadata(false, false)); runCurrent(); assertTrue(f.vm.uiState.value.outputValidation.isError)
        } finally { f.close() }
    }

    @Test fun randomEventsCallTheInjectedGeneratorExactlyOnceAndModesKeepBothDrafts() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler), tokens = JunkTokenGenerator { a, b -> calls += a to b; "random${calls.size}" })
        try {
            runCurrent(); f.vm.onIntent(RandomSuffix); f.vm.onIntent(RandomPrefix)
            assertEquals(listOf(3 to 8, 2 to 6), calls)
            assertEquals("random1", f.vm.uiState.value.single.suffix); assertEquals("random2_", f.vm.uiState.value.single.resPrefix)
            assertEquals("junk_com_dev_junk_random1_TT3.0.0.aar", f.vm.uiState.value.single.aarName)
            val single = f.vm.uiState.value.single
            f.vm.onIntent(ModeChanged(JunkMode.MULTI)); f.vm.onIntent(OutputDirChanged(" batch ")); f.vm.onIntent(ModeChanged(JunkMode.SINGLE)); runCurrent()
            assertEquals(single, f.vm.uiState.value.single); assertEquals(" batch ", f.vm.uiState.value.multi.outputDir)
            assertEquals(JunkPreference.SINGLE, f.preferences.state.value.junkMode); assertEquals(2, calls.size)
            f.preferences.change(PreferenceChange.JunkModeChanged(JunkPreference.MULTI)); runCurrent()
            assertEquals(JunkMode.MULTI, f.vm.uiState.value.mode)
        } finally { f.close() }
    }

    @Test fun failuresPreserveNullAndEmptyMessagesAndAllowRetry() = runTest {
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler)); val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent()
            for (message in listOf(null, "", "archive failure")) {
                f.vm.onIntent(Submit); f.pending.last().completeExceptionally(Exception(message)); runCurrent()
                assertFalse(f.vm.uiState.value.busy)
                assertEquals(message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.build_failure), events.last().snackbar.message)
            }
            f.vm.onIntent(Submit); f.pending.last().complete(GeneratedJunkCode("ok", listOf("ok"), 1)); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(4, events.size); assertEquals(4, f.requests.size)
        } finally { f.close() }
    }

    @Test fun cancellationAndWindowClosureCannotPublishLateCompletionOrKeepLoading() = runTest {
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler)); val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(Submit); f.pending.last().completeExceptionally(CancellationException("cancelled")); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertTrue(events.isEmpty())
            f.nonCooperative = true; f.vm.onIntent(Submit); f.store.clear(); assertFalse(f.vm.uiState.value.busy)
            f.pending.last().complete(GeneratedJunkCode("late", listOf("late"), 1)); runCurrent()
            assertTrue(events.isEmpty()); assertFalse(f.vm.uiState.value.busy)
        } finally { f.close() }
    }

    @Test fun outputExceptionsAreInlineAndRefreshCanRecoverWithoutUnrelatedPreferenceOverwrite() = runTest {
        var fail = true
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String): PathMetadata { if (fail) error("io error"); return PathMetadata(false, true) }
        }
        val f = JunkVmFixture(StandardTestDispatcher(testScheduler), storage)
        try {
            runCurrent(); assertTrue(f.vm.uiState.value.outputValidation.isError); assertFalse(f.vm.uiState.value.outputValidation.pending)
            fail = false; f.vm.onIntent(Refresh); runCurrent(); assertFalse(f.vm.uiState.value.outputValidation.isError)
            f.vm.onIntent(OutputPathChanged("custom")); f.preferences.change(PreferenceChange.Theme(ThemePreference.DARK)); runCurrent()
            assertEquals("custom", f.vm.uiState.value.outputPath)
            f.preferences.change(PreferenceChange.OutputPath(" new default ")); runCurrent(); assertEquals(" new default ", f.vm.uiState.value.outputPath)
        } finally { f.close() }
    }
}

private class JunkVmPreferences : PreferencesRepository {
    override val state = MutableStateFlow(PreferencesSnapshot(ready = true).let { it.copy(userData = it.userData.copy(defaultOutputPath = "/initial")) })
    override fun change(change: PreferenceChange): PreferencesSnapshot {
        val old = state.value; val next = old.changed(change)
        state.value = next.copy(outputPathVersion = old.outputPathVersion + if (next.userData.defaultOutputPath != old.userData.defaultOutputPath) 1 else 0)
        return state.value
    }
    override suspend fun awaitReady() = state.value
}

@OptIn(ExperimentalCoroutinesApi::class)
private class JunkVmFixture(dispatcher: TestDispatcher, storage: StorageRepository = AllPathsExist,
    tokens: JunkTokenGenerator = JunkTokenGenerator { _, _ -> "fixture" }) : AutoCloseable {
    val preferences = JunkVmPreferences(); val effects = AppEffectSink(); val store = ViewModelStore()
    val requests = mutableListOf<GenerateJunkCodeRequest>(); val pending = mutableListOf<CompletableDeferred<GeneratedJunkCode>>()
    var nonCooperative = false
    val vm: JunkCodeViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = junkViewModel(preferences, storage, effects, { request ->
            requests += request; val gate = CompletableDeferred<GeneratedJunkCode>(); pending += gate
            if (nonCooperative) withContext(NonCancellable) { gate.await() } else gate.await()
        }, tokens)
        store.put("junk", vm)
    }
    override fun close() { store.clear(); effects.close(); Dispatchers.resetMain() }
}
