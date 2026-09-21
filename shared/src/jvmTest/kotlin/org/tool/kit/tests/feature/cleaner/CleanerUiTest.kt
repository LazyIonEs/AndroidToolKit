package org.tool.kit.tests.feature.cleaner

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.BuildCachesRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.usecase.*
import org.tool.kit.feature.cleaner.*
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.prepareTestPreferences

/** Captures the cleaner page bottom bar and its menus with deterministic scanned metadata. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class CleanerUiTest {
    @Test fun lightSelection() = selection("LIGHT")
    @Test fun darkSelection() = selection("DARK")

    @Test fun floatingToolbarOverlaysTheListAndTheLastItemScrollsClearOfIt() =
        runDesktopComposeUiTest(width = 800, height = 572) {
            val items = List(30) { index -> CleanerItemUi(
                id = "item-$index", path = "project-$index/build", displayPath = "project-$index/build",
                bytes = 1024, modifiedAt = 0, isDirectory = true, exists = true,
            ) }
            val intents = mutableListOf<CleanerIntent>()
            setContent {
                org.tool.kit.theme.AppTheme(false) {
                    CleanerScreen(CleanerUiState(items = items), intents::add, {}, {})
                }
            }
            val list = onNode(hasScrollToIndexAction())
            val rootBottom = onNode(isRoot()).fetchSemanticsNode().boundsInRoot.bottom
            assertEquals(rootBottom, list.fetchSemanticsNode().boundsInRoot.bottom,
                "The list viewport must extend behind the floating toolbar")
            list.performScrollToIndex(items.lastIndex)
            onNodeWithText(items.last().displayPath).assertIsDisplayed()
            val lastCheckbox = onAllNodes(isToggleable()).onLast()
            lastCheckbox.assertIsDisplayed()
            val toolbarTop = onNodeWithTag("cleaner-toolbar").fetchSemanticsNode().boundsInRoot.top
            assertTrue(lastCheckbox.fetchSemanticsNode().boundsInRoot.bottom < toolbarTop,
                "The last row must scroll above the toolbar, not remain hidden under it")
            lastCheckbox.performTouchInput { click() }
            assertEquals(CleanerIntent.ItemCheckedChanged(items.last().id, false), intents.last())
        }

    @Test fun navigationRemainsAvailableDuringScanningAfterResultsAndDuringPageLocalDeletion() = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        prepareTestPreferences(File(checkNotNull(System.getProperty("test.fixtureRoot"))), "LIGHT")
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val discoveries = Channel<BuildDirectory>(Channel.UNLIMITED)
        val requests = mutableListOf<BuildDirectory>()
        val first = CompletableDeferred<DeleteBuildCacheResult>(); val second = CompletableDeferred<DeleteBuildCacheResult>()
        var creations = 0
        lateinit var vm: CleanerViewModel
        val repo = object : BuildCachesRepository {
            override fun scan(root: String) = discoveries.receiveAsFlow()
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult {
                requests += directory
                return (if (requests.size == 1) first else second).await()
            }
        }
        val container = koinApplication { modules(desktopModules() + module {
            single<BuildCachesRepository> { repo }
            single<StorageRepository> { AllPathsExist }
            viewModel { CleanerViewModel(ScanBuildCachesUseCase(get()), DeleteBuildCachesUseCase(get()), get(), get()).also { creations++; vm = it } }
        }) }
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("缓存清理").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("缓存清理") and hasClickAction()).performClick()
            runOnIdle { vm.onIntent(CleanerIntent.Rescan("fixture")) }
            val a = BuildDirectory("fixture", "fixture/a/build", "a/build", 20, 0, true, true)
            val b = a.copy(path = "fixture/b/build", displayPath = "b/build", bytes = 10)
            runOnIdle { discoveries.trySend(a) }
            waitUntil { vm.uiState.value.items.size == 1 }
            onNode(hasText("设置") and hasClickAction()).assertIsDisplayed()
            onNodeWithTag("cleaner-toolbar").assertDoesNotExist()
            onAllNodesWithContentDescription("Lottie animation").assertCountEquals(0)
            onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
            runOnIdle { discoveries.trySend(b); discoveries.close() }
            waitUntil { vm.uiState.value.phase == CleanerPhase.Idle && vm.uiState.value.items.size == 2 }
            onNodeWithTag("cleaner-toolbar").assertIsDisplayed()
            onNodeWithTag("cleaner-delete-selected").performClick()
            onNodeWithText("取消").performClick()
            assertTrue(requests.isEmpty())
            onNodeWithTag("cleaner-delete-selected").performClick()
            onNodeWithText("确认删除").performClick()
            waitUntil { requests.size == 1 && vm.uiState.value.phase == CleanerPhase.Deleting }
            onNodeWithTag("cleaner-progress").assertExists()
            val original = vm
            // Use pointer input: a window-wide overlay would swallow this click.
            onNode(hasText("设置") and hasClickAction()).performTouchInput { click() }
            waitUntil { onAllNodesWithText("默认输出路径").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithContentDescription("Lottie animation").assertCountEquals(0)
            onNodeWithTag("cleaner-toolbar").assertDoesNotExist()
            onNode(hasText("签名信息") and hasClickAction()).performTouchInput { click() }
            onNode(hasText("缓存清理") and hasClickAction()).performTouchInput { click() }
            waitUntil { onAllNodesWithTag("cleaner-progress").fetchSemanticsNodes().isNotEmpty() }
            assertSame(original, vm)
            assertEquals(CleanerPhase.Deleting, vm.uiState.value.phase)
            runOnIdle { first.complete(DeleteBuildCacheResult(a, true, false, false)) }
            waitUntil { requests.size == 2 && vm.uiState.value.items.size == 1 }
            assertEquals(CleanerPhase.Deleting, vm.uiState.value.phase)
            runOnIdle { second.complete(DeleteBuildCacheResult(b, false, true, true)) }
            waitUntil { vm.uiState.value.phase == CleanerPhase.Idle }
            assertTrue(vm.uiState.value.items.single().deleteFailed)
            onAllNodesWithContentDescription("Lottie animation").assertCountEquals(0)
            onNodeWithTag("cleaner-close-selection").performClick()
            onNode(hasText("设置") and hasClickAction()).assertExists()
            assertEquals(1, creations)
        } finally { first.cancel(); second.cancel(); discoveries.close(); owner.viewModelStore.clear(); container.close() }
    }

    private fun selection(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        val fixture = File(checkNotNull(System.getProperty("test.fixtureRoot")))
        prepareTestPreferences(fixture, theme)
        val root = fixture.resolve("cleaner-ui").apply { mkdirs() }
        val first = root.resolve("project a/build").apply { mkdirs() }
        val second = root.resolve("工程乙/build.foo").apply { mkdirs() }
        val third = root.resolve("changed/build").apply { parentFile.mkdirs(); writeText("changed after scanning") }
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val container = koinApplication { modules(desktopModules() + module { single<StorageRepository> { AllPathsExist } }) }
        lateinit var vm: CleanerViewModel
        container.koin.loadModules(listOf(module {
            viewModel { CleanerViewModel(get(), get(), get(), get()).also { vm = it } }
        }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("缓存清理").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("缓存清理") and hasClickAction()).performClick()
            val items = listOf(first, second, third).mapIndexed { index, file ->
                CleanerItemUi(file.absolutePath, file.absolutePath, file.absolutePath.replace(root.absolutePath + File.separatorChar, ""),
                    (3 - index) * 1234L, 1_700_000_000_000L + index * 86_400_000L, file.isDirectory, file.exists())
            }
            @Suppress("UNCHECKED_CAST")
            fun seed(items: List<CleanerItemUi>) {
                val state = CleanerViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }.get(vm) as MutableStateFlow<CleanerUiState>
                state.value = state.value.copy(scanRoot = root.absolutePath, items = items)
            }
            runOnIdle { seed(items) }
            onNode(hasText("设置") and hasClickAction()).assertIsDisplayed()
            waitForIdle()
            onAllNodes(isToggleable())[1].performClick()
            waitForIdle()
            runOnIdle { seed(vm.uiState.value.items.mapIndexed { index, item -> if (index == 2) item.copy(deleteFailed = true) else item }) }
            waitForIdle()
            onNodeWithTag("cleaner-sort").performClick()
            waitForIdle()
            onNodeWithText("名称（从 A 到 Z）").performClick()
            onNodeWithTag("cleaner-delete-selected").performClick()
            waitForIdle()
            onNodeWithText("取消").performClick()
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
