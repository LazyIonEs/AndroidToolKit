package org.tool.kit.migration

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.apk.*
import org.tool.kit.feature.apk.ApkInformationIntent.*
import org.tool.kit.shared.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApkInformationViewModelTest {
    @Test fun latestInputWinsAfterSlowUncooperativeResultAndBusyIsReleased() = runTest {
        ApkVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            runCurrent()
            f.vm.onIntent(ReadApk("A.apk")); runCurrent()
            assertTrue(f.vm.busy.value)
            f.vm.onIntent(ReadApk("B.apk")); runCurrent()
            f.repository.outputs[1].complete("application: label='B'"); runCurrent()
            assertEquals("B", f.vm.uiState.value.result!!.label)
            assertEquals("B.apk", f.vm.uiState.value.inputFile)
            assertFalse(f.vm.busy.value)
            f.repository.outputs[0].complete("application: label='A'"); runCurrent()
            assertEquals("B", f.vm.uiState.value.result!!.label)
        }
    }
    @Test fun emptyCommandAndExceptionFailuresReturnIdleAndRepeatOriginalSnackbar() = runTest {
        ApkVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            f.vm.onIntent(ReadApk("empty")); runCurrent()
            f.repository.outputs.last().complete("sdkVersion:'23'"); runCurrent()
            for (error in listOf(ApkCommandFailed(), Exception(null as String?), Exception(""), Exception("broken"), Exception("broken"))) {
                f.vm.onIntent(ReadApk("failed")); runCurrent()
                f.repository.outputs.last().completeExceptionally(error); runCurrent()
                assertEquals(ApkInformationPhase.Idle, f.vm.uiState.value.phase)
                assertNull(f.vm.uiState.value.result); assertFalse(f.vm.busy.value)
            }
            assertEquals(listOf(UiMessage.Resource(Res.string.apk_parsing_failed), UiMessage.Resource(Res.string.exec_command_error),
                UiMessage.Resource(Res.string.apk_parsing_failed), UiMessage.Text(""), UiMessage.Text("broken"), UiMessage.Text("broken")), events.map { it.snackbar.message })
            assertEquals(6, events.map { it.effectId }.distinct().size)
        }
    }
    @Test fun rawCopyPreservesEveryCharacterAndAcknowledgesOnlySuccessfulWrite() = runTest {
        ApkVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            repeat(2) {
                f.vm.onIntent(CopyText(" 测试 ' Ab:cD\n")); runCurrent()
                assertEquals(" 测试 ' Ab:cD\n", f.clipboard.values.last()); assertEquals(it, events.size)
                f.clipboard.done.last().complete(Unit); runCurrent()
            }
            assertEquals(2, events.map { it.effectId }.distinct().size)
            assertTrue(events.all { it.snackbar.message == UiMessage.Resource(Res.string.copied_to_clipboard) })
            f.vm.onIntent(CopyText("fail")); runCurrent()
            f.clipboard.done.last().completeExceptionally(Exception("clipboard fixture")); runCurrent()
            assertEquals(2, events.size)
        }
    }
    @Test fun clearCancelsProcessRequestAndRejectsLateCopyAndResult() = runTest {
        ApkVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            f.vm.onIntent(ReadApk("slow")); f.vm.onIntent(CopyText("late")); runCurrent()
            f.store.clear(); runCurrent()
            f.repository.outputs.last().complete("application: label='late'")
            f.clipboard.done.last().complete(Unit); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertNull(f.vm.uiState.value.result); assertTrue(events.isEmpty())
        }
    }
    @Test fun dropKeepsOriginalCaseSensitiveSuffixRule() {
        assertEquals(ReadApk("/中文 空格.apk"), apkInformationFileIntent("/中文 空格.apk"))
        assertNull(apkInformationFileIntent("/a.APK")); assertNull(apkInformationFileIntent("/a.jks"))
    }
}
internal class ControlledApkRepository : FixtureApkRepository() {
    val outputs = mutableListOf<CompletableDeferred<String>>()
    override suspend fun badging(path: String): String = withContext(NonCancellable) {
        CompletableDeferred<String>().also { outputs += it }.await()
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
internal class ApkVmFixture(dispatcher: TestDispatcher) : AutoCloseable {
    val effects = AppEffectSink()
    val repository = ControlledApkRepository()
    val clipboard = ControlledClipboard()
    val store = ViewModelStore()
    val vm: ApkInformationViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = ApkInformationViewModel(ReadApkInformationUseCase(repository), ApkIconDecoder { error("No icon expected") }, clipboard, effects)
        store.put("apk-information", vm)
    }
    override fun close() {
        store.clear(); repository.outputs.forEach { it.complete("") }; clipboard.done.forEach { it.complete(Unit) }
        effects.close(); Dispatchers.resetMain()
    }
}
