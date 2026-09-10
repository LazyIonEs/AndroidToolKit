package org.tool.kit.tests.feature.update

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.*
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.update.*
import org.tool.kit.feature.update.DownloadState
import org.tool.kit.tests.support.release

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    @Test fun checksKeepManualSilentLatestErrorAndOrderedAssetsSemantics() = runTest {
        val fixture = UpdateFixture(StandardTestDispatcher(testScheduler))
        val messages = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(messages) }
        try {
            fixture.vm.onIntent(UpdateIntent.Check(false)); runCurrent()
            assertTrue(messages.isEmpty())
            fixture.vm.onIntent(UpdateIntent.Check()); runCurrent()
            assertEquals(1, messages.size)
            fixture.transport.checkResult = UpdateCheckResult.Failed(UpdateError.RATE_LIMIT)
            fixture.vm.onIntent(UpdateIntent.Check(false)); runCurrent()
            assertEquals(1, messages.size)
            fixture.vm.onIntent(UpdateIntent.Check()); runCurrent()
            assertEquals(2, messages.size)
            assertNotEquals(messages[0].effectId, messages[1].effectId)
            fixture.transport.checkResult = UpdateCheckResult.Available(release)
            fixture.vm.onIntent(UpdateIntent.Check()); runCurrent()
            assertEquals(release, fixture.vm.uiState.value.release)
            assertEquals(release.assets.first(), fixture.vm.uiState.value.selectedAsset)
            assertTrue(fixture.vm.uiState.value.visible)
            assertFalse(fixture.vm.uiState.value.checking)
            fixture.vm.onIntent(UpdateIntent.SelectAsset(release.assets.last()))
            assertEquals(release.assets.last(), fixture.vm.uiState.value.selectedAsset)
            fixture.vm.onIntent(UpdateIntent.SelectAsset(UpdateAsset("missing", "unused")))
            assertEquals(release.assets.last(), fixture.vm.uiState.value.selectedAsset)
        } finally { fixture.close() }
    }

    @Test fun duplicateChecksAndDownloadsRunOnceUnknownLengthAndFailureCanRetry() = runTest {
        val fixture = UpdateFixture(StandardTestDispatcher(testScheduler))
        val checkGate = CompletableDeferred<Unit>()
        fixture.transport.checkGate = checkGate
        fixture.transport.checkResult = UpdateCheckResult.Available(release)
        try {
            fixture.vm.onIntent(UpdateIntent.Check())
            fixture.vm.onIntent(UpdateIntent.Check())
            runCurrent()
            assertEquals(1, fixture.transport.checks)
            checkGate.complete(Unit); runCurrent()
            fixture.vm.onIntent(UpdateIntent.Download)
            fixture.vm.onIntent(UpdateIntent.Download)
            runCurrent()
            assertEquals(1, fixture.transport.downloads.size)
            val first = fixture.transport.downloads[0]
            first.progress(320, 0); runCurrent()
            assertEquals(0f, fixture.vm.uiState.value.progress)
            assertEquals(320L, fixture.vm.uiState.value.downloadedBytes)
            first.result.complete(UpdateDownloadResult.Failed(UpdateError.CONNECTION)); runCurrent()
            assertEquals(DownloadState.START, fixture.vm.uiState.value.downloadState)
            fixture.vm.onIntent(UpdateIntent.Download); runCurrent()
            fixture.transport.downloads[1].progress(150, 100); runCurrent()
            assertEquals(1f, fixture.vm.uiState.value.progress)
            fixture.transport.downloads[1].result.complete(UpdateDownloadResult.Downloaded("/finished fixture")); runCurrent()
            assertEquals(DownloadState.FINISH, fixture.vm.uiState.value.downloadState)
            assertEquals("/finished fixture", fixture.vm.uiState.value.downloadedPath)
            fixture.vm.onIntent(UpdateIntent.Install)
            val request = fixture.vm.uiState.value.installRequest!!
            fixture.vm.onIntent(UpdateIntent.Install)
            assertEquals(request, fixture.vm.uiState.value.installRequest)
            assertFalse(fixture.vm.uiState.value.visible)
            fixture.vm.onIntent(UpdateIntent.InstallHandled(request.id + 1))
            assertEquals(request, fixture.vm.uiState.value.installRequest)
            fixture.vm.onIntent(UpdateIntent.InstallHandled(request.id))
            assertNull(fixture.vm.uiState.value.installRequest)
        } finally { fixture.close() }
    }

    @Test fun cancellationWaitsForOldCleanupAndRejectsNonCooperativeProgressBeforeReusingOutput() = runTest {
        val fixture = UpdateFixture(StandardTestDispatcher(testScheduler))
        val releaseCleanup = CompletableDeferred<Unit>()
        fixture.transport.cleanupGate = releaseCleanup
        fixture.transport.checkResult = UpdateCheckResult.Available(release)
        try {
            fixture.vm.onIntent(UpdateIntent.Check()); runCurrent()
            fixture.preferences.change(PreferenceChange.OutputPath("/first output"))
            fixture.vm.onIntent(UpdateIntent.Download); runCurrent()
            val old = fixture.transport.downloads.single()
            fixture.preferences.change(PreferenceChange.OutputPath("/next output"))
            assertEquals("/first output", old.output)
            fixture.vm.onIntent(UpdateIntent.Cancel); runCurrent()
            assertEquals(DownloadState.START, fixture.vm.uiState.value.downloadState)
            fixture.vm.onIntent(UpdateIntent.Download); runCurrent()
            assertEquals(1, fixture.transport.downloads.size, "Retry must wait for the previous transport to release its file")
            old.progress(99, 100); runCurrent()
            assertEquals(0f, fixture.vm.uiState.value.progress)
            releaseCleanup.complete(Unit); runCurrent()
            assertEquals(2, fixture.transport.downloads.size)
            val next = fixture.transport.downloads.last()
            assertEquals("/next output", next.output)
            next.result.complete(UpdateDownloadResult.Downloaded("/next output/file")); runCurrent()
            assertEquals(DownloadState.FINISH, fixture.vm.uiState.value.downloadState)
            assertEquals("/next output/file", fixture.vm.uiState.value.downloadedPath)
        } finally { releaseCleanup.complete(Unit); fixture.close() }
    }
}

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
internal class UpdateFixture(dispatcher: TestDispatcher) : AutoCloseable {
    val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
    val effects = AppEffectSink()
    val preferences = DefaultPreferencesRepository(PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), dispatcher), dispatchers)
    val transport = ControlledUpdates()
    val vm: UpdateViewModel
    private val store = ViewModelStore()
    init { Dispatchers.setMain(dispatcher); vm = UpdateViewModel(transport, preferences, effects, dispatchers); store.put("updates", vm) }
    override fun close() { store.clear(); preferences.close(); effects.close(); Dispatchers.resetMain() }
}

internal class ControlledUpdates : UpdateRepository {
    data class Download(val asset: UpdateAsset, val output: String, val progress: suspend (Long, Long) -> Unit,
        val result: CompletableDeferred<UpdateDownloadResult> = CompletableDeferred())
    var checkResult: UpdateCheckResult = UpdateCheckResult.Latest
    var checkGate: CompletableDeferred<Unit>? = null
    var cleanupGate: CompletableDeferred<Unit>? = null
    var checks = 0
    val downloads = mutableListOf<Download>()
    override suspend fun check(): UpdateCheckResult { checks++; checkGate?.await(); return checkResult }
    override suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult {
        val call = Download(asset, outputDirectory, progress).also { downloads += it }
        try { return call.result.await() }
        finally { withContext(NonCancellable) { cleanupGate?.await() } }
    }
}
