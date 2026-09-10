package org.tool.kit.migration

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.repository.BuildCachesRepository
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.usecase.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import kotlin.test.*
import org.tool.kit.feature.cleaner.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

/** Captures the actual root bottom bar and its menus with deterministic scanned metadata. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class CleanerUiTest {
    @Test fun lightSelection() = selection("LIGHT")
    @Test fun darkSelection() = selection("DARK")

    @Test fun actualRootDistinguishesProgressiveScanningFromDeletingAndSharesOneWindowOwner() = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), "LIGHT")
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val discoveries = Channel<BuildDirectory>(Channel.UNLIMITED)
        val requests = mutableListOf<BuildDirectory>()
        val first = CompletableDeferred<DeleteBuildCacheResult>(); val second = CompletableDeferred<DeleteBuildCacheResult>()
        var creations = 0
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
            viewModel { CleanerViewModel(ScanBuildCachesUseCase(get()), DeleteBuildCachesUseCase(get()), get(), get()).also { creations++ } }
        }) }
        lateinit var vm: CleanerViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<CleanerViewModel>()
                    App(); SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("缓存清理").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("缓存清理") and hasClickAction()).performClick()
            runOnIdle { vm.onIntent(CleanerIntent.Rescan("fixture")) }
            val a = BuildDirectory("fixture", "fixture/a/build", "a/build", 20, 0, true, true)
            val b = a.copy(path = "fixture/b/build", displayPath = "b/build", bytes = 10)
            runOnIdle { discoveries.trySend(a) }
            waitUntil { vm.uiState.value.items.size == 1 }
            onNodeWithText("设置").assertDoesNotExist()
            onAllNodesWithContentDescription("Localized description").assertCountEquals(0)
            onAllNodesWithContentDescription("Lottie animation").assertCountEquals(0)
            onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
            runOnIdle { discoveries.trySend(b); discoveries.close() }
            waitUntil { vm.uiState.value.phase == CleanerPhase.Idle && vm.uiState.value.items.size == 2 }
            onAllNodesWithContentDescription("Localized description").assertCountEquals(5)
            onAllNodesWithContentDescription("Localized description")[4].performClick()
            onNodeWithText("取消").performClick()
            assertTrue(requests.isEmpty())
            onAllNodesWithContentDescription("Localized description")[4].performClick()
            onNodeWithText("确认删除").performClick()
            waitUntil { requests.size == 1 && vm.uiState.value.phase == CleanerPhase.Deleting }
            onAllNodesWithContentDescription("Lottie animation").onFirst().assertExists()
            runOnIdle { first.complete(DeleteBuildCacheResult(a, true, false, false)) }
            waitUntil { requests.size == 2 && vm.uiState.value.items.size == 1 }
            assertEquals(CleanerPhase.Deleting, vm.uiState.value.phase)
            runOnIdle { second.complete(DeleteBuildCacheResult(b, false, true, true)) }
            waitUntil { vm.uiState.value.phase == CleanerPhase.Idle }
            assertTrue(vm.uiState.value.items.single().deleteFailed)
            onAllNodesWithContentDescription("Lottie animation").assertCountEquals(0)
            onAllNodesWithContentDescription("Localized description")[0].performClick()
            onNode(hasText("设置") and hasClickAction()).assertExists()
            assertEquals(1, creations)
        } finally { first.cancel(); second.cancel(); discoveries.close(); owner.viewModelStore.clear(); container.close() }
    }

    private fun selection(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        val fixture = File(checkNotNull(System.getProperty("migration.fixtureRoot")))
        prepareBaselinePreferences(fixture, theme)
        val root = fixture.resolve("phase8-visual").apply { mkdirs() }
        val first = root.resolve("project a/build").apply { mkdirs() }
        val second = root.resolve("工程乙/build.foo").apply { mkdirs() }
        val third = root.resolve("changed/build").apply { parentFile.mkdirs(); writeText("changed after scanning") }
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val container = koinApplication { modules(desktopModules() + module { single<StorageRepository> { AllPathsExist } }) }
        lateinit var vm: CleanerViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<CleanerViewModel>()
                    App()
                    SideEffect { vm = current }
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
            fun capture(name: String) {
                waitForIdle()
                val bitmap = onAllNodes(isRoot()).onLast().captureToImage()
                val pixels = bitmap.toPixelMap()
                val out = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) out.setRGB(x, y, pixels[x, y].toArgb())
                val file = File(System.getProperty("migration.renderOutput"), "phase8/${theme.lowercase()}/$name.png")
                file.parentFile.mkdirs(); ImageIO.write(out, "png", file)
            }
            onNodeWithText("设置").assertDoesNotExist()
            capture("all-selected")
            onAllNodes(isToggleable())[1].performClick()
            capture("mixed-selection")
            runOnIdle { seed(vm.uiState.value.items.mapIndexed { index, item -> if (index == 2) item.copy(deleteFailed = true) else item }) }
            capture("failed-item")
            onAllNodesWithContentDescription("Localized description")[1].performClick()
            capture("sort-menu")
            onNodeWithText("名称（从 A 到 Z）").performClick()
            onAllNodesWithContentDescription("Localized description")[4].performClick()
            capture("delete-dialog")
            onNodeWithText("取消").performClick()
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
