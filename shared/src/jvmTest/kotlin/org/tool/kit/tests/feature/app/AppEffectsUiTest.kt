package org.tool.kit.tests.feature.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.setting.VersionInfo
import org.tool.kit.feature.update.*
import org.tool.kit.tests.support.ImmediatePreferencesRepository
import org.tool.kit.tests.support.RecordingDesktopActions
import org.tool.kit.tests.support.release
import org.tool.kit.theme.AppTheme

@OptIn(ExperimentalTestApi::class)
class AppEffectsUiTest {
    @Test fun finishedDownloadSurvivesRecompositionAndInstallationExitsOnlyAfterOpenSucceeds() = installation(openSucceeds = true)
    @Test fun failedNativeOpenDismissesWithoutExitingOrReplayingOnRecomposition() = installation(openSucceeds = false)

    private fun installation(openSucceeds: Boolean) = runDesktopComposeUiTest(width = 800, height = 572) {
        val repository = object : UpdateRepository {
            override suspend fun check() = UpdateCheckResult.Available(release)
            override suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult {
                progress(100, 100)
                return UpdateDownloadResult.Downloaded("/fixture/downloaded installer.dmg")
            }
        }
        val effects = AppEffectSink()
        val actions = RecordingDesktopActions().also { it.openSucceeds = openSucceeds }
        val vm = UpdateViewModel(repository, ImmediatePreferencesRepository(), effects,
            AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main.immediate))
        val store = ViewModelStore().also { it.put("update", vm) }
        val dark = mutableStateOf(false)
        try {
            setContent { AppTheme(dark.value) { UpdateRoute(vm, actions) } }
            runOnIdle { vm.onIntent(UpdateIntent.Check()) }
            onNodeWithText("更新").performClick()
            onNodeWithText("下载成功").assertExists()
            repeat(3) { runOnIdle { dark.value = !dark.value }; waitForIdle() }
            assertEquals("/fixture/downloaded installer.dmg", vm.uiState.value.downloadedPath)
            onNodeWithText("退出并安装").performClick()
            waitUntil { actions.events.isNotEmpty() }
            runOnIdle { dark.value = !dark.value }
            waitForIdle()
            assertEquals(if (openSucceeds) listOf("install:/fixture/downloaded installer.dmg", "exit") else listOf("install:/fixture/downloaded installer.dmg"), actions.events)
            assertNull(vm.uiState.value.installRequest)
            assertFalse(vm.uiState.value.visible)
        } finally { runOnIdle { store.clear(); effects.close() } }
    }

    @Test fun duplicateSnackbarReplacesCurrentAndNativeActionRunsOnceOutsideReplacementJob() = runDesktopComposeUiTest(width = 800, height = 572) {
        val sink = AppEffectSink()
        val host = SnackbarHostState()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val actions = object : DesktopActionHandler {
            override suspend fun openDirectory(path: String?) { events += "start:$path"; started.complete(Unit); finish.await(); events += "finish:$path" }
            override suspend fun openInstaller(path: String) = false
            override suspend fun logFilePath(): String? = null
            override fun browse(url: String) = Unit
            override fun exitAfterInstall() = Unit
        }
        lateinit var scope: CoroutineScope
        val revision = mutableIntStateOf(0)
        var composed = -1
        setContent {
            scope = rememberCoroutineScope()
            val value = revision.intValue
            AppTheme(false) { SnackbarHost(host); AppEffectHost(sink, host, actions) }
            SideEffect { composed = value }
        }
        fun send(text: String, action: Boolean = false) = runOnIdle {
            scope.launch { assertTrue(sink.send("test", SnackbarMessage(UiMessage.Text(text),
                actionLabel = if (action) "jump" else null, duration = SnackbarDuration.Indefinite,
                action = if (action) SnackbarAction.OpenDirectory("/captured result") else null))) }
        }
        try {
            send("same message")
            waitUntil { host.currentSnackbarData != null }
            val first = host.currentSnackbarData
            send("same message", true)
            waitUntil { host.currentSnackbarData != null && host.currentSnackbarData !== first }
            runOnIdle { host.currentSnackbarData!!.performAction() }
            waitUntil { started.isCompleted }
            send("replacement while action is suspended")
            waitUntil { host.currentSnackbarData?.visuals?.message == "replacement while action is suspended" }
            runOnIdle { revision.intValue++ }
            waitUntil { composed == 1 }
            runOnIdle { finish.complete(Unit) }
            waitUntil { events.size == 2 }
            assertEquals(listOf("start:/captured result", "finish:/captured result"), events)
            runOnIdle { host.currentSnackbarData!!.dismiss() }
            waitForIdle()
            assertEquals(2, events.size)
        } finally { runOnIdle { finish.complete(Unit); sink.close() } }
    }

    @Test fun developerEntryStillRequiresTwoClicksWithinTheDefaultWindow() = runDesktopComposeUiTest(width = 800, height = 572) {
        var activated = 0
        setContent { AppTheme(false) { VersionInfo { activated++ } } }
        val version = onNodeWithText("应用版本")
        version.performClick()
        assertEquals(0, activated)
        version.performClick()
        assertEquals(1, activated)
    }
}
