package org.tool.kit.migration

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.jetbrains.compose.resources.getString
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.GenerateIconsUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.iconfactory.*
import org.tool.kit.feature.iconfactory.IconFactoryIntent.*
import org.tool.kit.model.*
import org.tool.kit.shared.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IconFactoryViewModelTest {
    @Test fun submitCapturesAllFieldsAndPreferencesBeforeSuspensionAndIgnoresDuplicates() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent()
            listOf(InputChanged(" source.png"), OutputPathChanged(" output "), FileDirChanged(" res "),
                IconDirChanged("drawable"), IconNameChanged("中文 icon")).forEach(f.vm::onIntent)
            val options = IconFactoryData(PngAlgorithm.Catrom, JpegAlgorithm.Gaussian, false, 17, 83, 7, 2, .37f, 43f)
            f.preferences.change(PreferenceChange.IconSettings(options))
            f.vm.onIntent(Submit); f.vm.onIntent(Submit); runCurrent()
            val request = f.pipeline.requests.single()
            assertEquals(GenerateIconsRequest(" source.png", " output ", " res ", "drawable", "中文 icon",
                IconProcessingOptions(1, 4, false, 17, 83, 7, 2, 43f)), request)
            f.vm.onIntent(OutputPathChanged("next output")); f.vm.onIntent(FileDirChanged("next res"))
            f.vm.onIntent(IconDirChanged("mipmap")); f.vm.onIntent(IconNameChanged("next icon"))
            f.preferences.change(PreferenceChange.IconSettings(PreferencesSnapshot().iconFactoryData))
            f.pipeline.pending.single().complete(Unit); runCurrent()
            f.vm.uiState.first { !it.busy }
            assertEquals(5, f.vm.uiState.value.result!!.size)
            assertTrue(f.vm.uiState.value.result!!.all { it.path.startsWith(" output / res /drawable-") })
            assertEquals("next icon", f.vm.uiState.value.form.iconName)
            assertEquals("next output", f.vm.uiState.value.form.outputPath)
            assertEquals(List(5) { "quantize:17:83:7:2" }, f.pipeline.calls.filter { it.startsWith("quantize") })
            assertEquals(SnackbarAction.OpenDirectory(" output / res "), events.single().snackbar.action)
            assertEquals(UiMessage.Text(getString(Res.string.icon_generation_completed)), events.single().snackbar.message)
            assertEquals("icon-factory", events.single().originFeature)
        } finally { f.close() }
    }

    @Test fun errorsRetainOnlyCompletedOutputsUseOriginalMessagesAndPermitRetry() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(InputChanged("input.png"))
            for (message in listOf(null, "", "native error")) {
                f.pipeline.failAt = 4; f.pipeline.failure = Exception(message)
                f.vm.onIntent(Submit); runCurrent(); f.pipeline.pending.last().complete(Unit); runCurrent()
                assertFalse(f.vm.uiState.value.busy)
                assertEquals(listOf("/initial/res/mipmap-mdpi/ic_launcher.png"), f.vm.uiState.value.result!!.map { it.path })
                assertEquals(message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.icon_creation_failed), events.last().snackbar.message)
            }
            f.pipeline.failAt = null
            f.vm.onIntent(Submit); runCurrent(); assertNull(f.vm.uiState.value.result)
            f.pipeline.pending.last().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(5, f.vm.uiState.value.result!!.size)
            assertEquals(4, events.map { it.effectId }.distinct().size)
        } finally { f.close() }
    }

    @Test fun blankFieldsOpenOriginalSheetButBlankIconDirectoryStillGenerates() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(InputChanged("input.png"))
            for ((empty, restore) in listOf(OutputPathChanged(" ") to OutputPathChanged("/initial"),
                FileDirChanged("") to FileDirChanged("res"), IconNameChanged(" ") to IconNameChanged("ic_launcher"))) {
                f.vm.onIntent(SheetClosed); f.vm.onIntent(empty); f.vm.onIntent(Submit); runCurrent()
                assertTrue(f.vm.uiState.value.sheetOpen); assertFalse(f.vm.uiState.value.busy)
                assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
                f.vm.onIntent(restore)
            }
            assertTrue(f.pipeline.requests.isEmpty())
            f.vm.onIntent(IconDirChanged("")); f.vm.onIntent(Submit); runCurrent()
            f.pipeline.pending.single().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals("/initial/res/-mdpi/ic_launcher.png", f.vm.uiState.value.result!!.first().path)
        } finally { f.close() }
    }

    @Test fun unsupportedInputReleasesLoadingWithoutANewNotificationAndNullInputDoesNothing() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(Submit); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertTrue(f.pipeline.requests.isEmpty())
            // Internal/state-restoration defense for the old early-return branch; pickers still filter it out.
            f.vm.onIntent(InputChanged("unsupported.webp")); f.vm.onIntent(Submit); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertNull(f.vm.uiState.value.result)
            assertTrue(events.isEmpty()); assertTrue(f.pipeline.requests.isEmpty())
        } finally { f.close() }
    }

    @Test fun pickerAndDropUseTheFirstItemAndReselectingTheSameInputClearsResults() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        try {
            runCurrent(); f.vm.onIntent(FileSelected("input.png")); f.vm.onIntent(Submit); runCurrent()
            f.pipeline.pending.single().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(5, f.vm.uiState.value.result!!.size)
            f.vm.onIntent(FilesDropped(listOf("readme.txt", "later.png")))
            f.vm.onIntent(FileSelected("UPPER.PNG"))
            assertEquals("input.png", f.vm.uiState.value.form.inputPath); assertNotNull(f.vm.uiState.value.result)
            f.vm.onIntent(FileSelected("input.png")); assertNull(f.vm.uiState.value.result)
            f.vm.onIntent(FilesDropped(listOf("first.jpeg", "later.png")))
            assertEquals("first.jpeg", f.vm.uiState.value.form.inputPath)
        } finally { f.close() }
    }

    @Test fun settingsDraftsCommitOnlyOnReleaseAndFollowOriginalEditorLifetimes() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        try {
            runCurrent(); f.vm.onIntent(SheetOpened)
            val original = f.preferences.state.value.iconFactoryData
            f.vm.onIntent(CompressionSpeedChanged(5.555f)); f.vm.onIntent(PngRangeChanged(12.4f, 20f)); f.vm.onIntent(JpegQualityChanged(43.6f))
            assertEquals(original, f.preferences.state.value.iconFactoryData)
            assertTrue(f.preferences.changes.isEmpty())
            assertEquals(IconSettingsDraft(5.555f, 12.4f, 30f, 43.6f), f.vm.uiState.value.draft)
            f.vm.onIntent(CompressionSpeedCommitted); f.vm.onIntent(PngRangeCommitted); f.vm.onIntent(JpegQualityCommitted); runCurrent()
            assertEquals(original.copy(percentage = .56f, speed = 5, preset = 3, minimum = 12, target = 30, quality = 44f), f.preferences.state.value.iconFactoryData)
            assertEquals(5.555f, f.vm.uiState.value.draft.compressionSpeed, "Original slider keeps its precise thumb position during this editor session")
            f.vm.onIntent(JpegQualityChanged(7f)); f.vm.onIntent(PngRangeChanged(0f, 90f))
            f.vm.onIntent(LossyEditorEntered)
            assertEquals(44f, f.vm.uiState.value.draft.jpegQuality); assertEquals(12f, f.vm.uiState.value.draft.minimum)
            f.vm.onIntent(CompressionEditorEntered)
            assertEquals(5.6f, f.vm.uiState.value.draft.compressionSpeed)
            f.vm.onIntent(CompressionSpeedChanged(2f)); f.vm.onIntent(PageLeft)
            assertFalse(f.vm.uiState.value.sheetOpen)
            f.vm.onIntent(PageEntered); assertFalse(f.vm.uiState.value.sheetOpen)
            f.vm.onIntent(SheetOpened)
            assertEquals(5.6f, f.vm.uiState.value.draft.compressionSpeed)
            assertEquals(3, f.preferences.changes.size, "Closing or remounting never commits abandoned drags")
            f.vm.onIntent(LosslessChanged(false)); f.vm.onIntent(PngAlgorithmChanged(PngAlgorithm.Mitchell)); f.vm.onIntent(JpegAlgorithmChanged(JpegAlgorithm.Bilinear))
            assertEquals(original.copy(percentage = .56f, speed = 5, preset = 3, minimum = 12, target = 30, quality = 44f,
                lossless = false, pngTypIdx = PngAlgorithm.Mitchell, jpegTypIdx = JpegAlgorithm.Bilinear), f.preferences.state.value.iconFactoryData)
        } finally { f.close() }
    }

    @Test fun defaultOutputUpdatesDoNotResetDraftsForUnrelatedChangesOrDuplicateValues() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        try {
            runCurrent(); f.vm.onIntent(OutputPathChanged("custom")); f.vm.onIntent(FileDirChanged("my res")); f.vm.onIntent(SheetOpened)
            f.vm.onIntent(CompressionSpeedChanged(4.2f))
            f.preferences.change(PreferenceChange.Theme(ThemePreference.DARK)); runCurrent()
            assertEquals("custom", f.vm.uiState.value.form.outputPath)
            assertEquals(4.2f, f.vm.uiState.value.draft.compressionSpeed)
            f.preferences.change(PreferenceChange.OutputPath("new default")); runCurrent()
            assertEquals("new default", f.vm.uiState.value.form.outputPath)
            assertEquals("my res", f.vm.uiState.value.form.fileDir)
            f.vm.onIntent(OutputPathChanged("custom again"))
            f.preferences.change(PreferenceChange.OutputPath("new default")); runCurrent()
            assertEquals("custom again", f.vm.uiState.value.form.outputPath)
            val changed = f.preferences.state.value.iconFactoryData.copy(percentage = .2f, quality = 33f)
            f.preferences.change(PreferenceChange.IconSettings(changed)); runCurrent()
            assertEquals(changed, f.vm.uiState.value.settings)
            assertEquals(4.2f, f.vm.uiState.value.draft.compressionSpeed)
            f.vm.onIntent(SheetClosed); f.vm.onIntent(SheetOpened)
            assertEquals(2f, f.vm.uiState.value.draft.compressionSpeed); assertEquals(33f, f.vm.uiState.value.draft.jpegQuality)
        } finally { f.close() }
    }

    @Test fun changedInputAndClosedWindowsRejectLateNativeResultsButStillClean() = runTest {
        val f = IconVmFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.pipeline.nonCooperative = true
            f.vm.onIntent(InputChanged("old.png")); f.vm.onIntent(Submit); runCurrent()
            f.vm.onIntent(InputChanged("new.png"))
            f.pipeline.pending.last().complete(Unit); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertNull(f.vm.uiState.value.result); assertTrue(events.isEmpty())
            assertEquals(5, f.pipeline.cleaned.size)
            f.vm.onIntent(Submit); runCurrent(); f.store.clear(); runCurrent()
            assertFalse(f.vm.uiState.value.busy)
            f.pipeline.pending.last().complete(Unit); runCurrent()
            assertTrue(events.isEmpty()); assertFalse(f.vm.uiState.value.busy); assertNull(f.vm.uiState.value.result)
            assertEquals(6, f.pipeline.cleaned.size)
        } finally { f.close() }
    }

    @Test fun previewChecksRunOutsideRenderingAndLateRefreshCannotRestoreAnOldImage() = runTest {
        var delayed = false
        val pending = mutableListOf<CompletableDeferred<PathMetadata>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String): PathMetadata {
                if (!delayed) return PathMetadata(path.contains("mipmap-mdpi/"), false)
                return withContext(NonCancellable) { CompletableDeferred<PathMetadata>().also { pending += it }.await() }
            }
        }
        val f = IconVmFixture(StandardTestDispatcher(testScheduler), storage)
        try {
            runCurrent(); f.vm.onIntent(InputChanged("old.png")); f.vm.onIntent(Submit); runCurrent()
            f.pipeline.pending.single().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(listOf(true, false, false, false, false), f.vm.uiState.value.result!!.map { it.previewAvailable })
            delayed = true; f.vm.onIntent(PageEntered); runCurrent()
            assertEquals(1, pending.size)
            f.vm.onIntent(InputChanged("new.png")); pending.single().complete(PathMetadata(true, false)); runCurrent()
            assertEquals("new.png", f.vm.uiState.value.form.inputPath); assertNull(f.vm.uiState.value.result)
        } finally { pending.forEach { it.complete(PathMetadata(false, false)) }; f.close() }
    }
}

private class IconVmPreferences : PreferencesRepository {
    override val state = MutableStateFlow(PreferencesSnapshot(ready = true).let { it.copy(userData = it.userData.copy(defaultOutputPath = "/initial")) })
    val changes = mutableListOf<PreferenceChange>()
    override fun change(change: PreferenceChange): PreferencesSnapshot {
        changes += change
        val old = state.value; val next = old.changed(change)
        state.value = next.copy(outputPathVersion = old.outputPathVersion + if (next.userData.defaultOutputPath != old.userData.defaultOutputPath) 1 else 0)
        return state.value
    }
    override suspend fun awaitReady() = state.value
}

private class IconVmPipeline : ImageProcessor, IconOutputs {
    val requests = mutableListOf<GenerateIconsRequest>()
    val pending = mutableListOf<CompletableDeferred<Unit>>()
    val calls = mutableListOf<String>()
    val cleaned = mutableListOf<String>()
    var nonCooperative = false
    var failAt: Int? = null
    var failure: Exception = Exception("native error")
    private var step = 0
    private suspend fun call(text: String) {
        calls += text
        if (++step == 1) {
            if (nonCooperative) withContext(NonCancellable) { pending.last().await() } else pending.last().await()
        }
        if (step == failAt) throw failure
    }
    override suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int) = call("resizePng:$size:$algorithm")
    override suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int) = call("resizeFir:$size:$algorithm")
    override suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int) = call("oxipng:$preset")
    override suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int) = call("quantize:$minimum:$target:$speed:$preset")
    override suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float) = call("mozJpeg:$quality")
    override suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T {
        requests += request; step = 0; pending += CompletableDeferred<Unit>()
        return block(object : IconOutputSession {
            override val outputDirectory = "${request.outputPath}/${request.fileDir}"
            override suspend fun <R> density(name: String, suffix: String, block: suspend (IconOutputFiles) -> R): R {
                val base = "$outputDirectory/${request.iconDir}-$name/${request.iconName}"
                return try { block(IconOutputFiles("$base$suffix", "${base}_resize$suffix")) }
                finally { cleaned += name }
            }
        })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class IconVmFixture(dispatcher: TestDispatcher, storage: StorageRepository = AllPathsExist) : AutoCloseable {
    val preferences = IconVmPreferences()
    val pipeline = IconVmPipeline()
    val effects = AppEffectSink()
    val store = ViewModelStore()
    val vm: IconFactoryViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = IconFactoryViewModel(GenerateIconsUseCase(pipeline, pipeline), preferences, storage, effects)
        store.put("icons", vm)
    }
    override fun close() { store.clear(); effects.close(); Dispatchers.resetMain() }
}
