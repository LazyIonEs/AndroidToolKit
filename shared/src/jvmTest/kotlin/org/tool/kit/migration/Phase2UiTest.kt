package org.tool.kit.migration

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.feature.cleaner.CleanerRoute
import org.tool.kit.feature.cleaner.CleanerViewModel
import org.tool.kit.feature.cleaner.CleanerIntent
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.platform.DesktopFileSelection
import org.tool.kit.theme.AppTheme
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@OptIn(ExperimentalSettingsApi::class, ExperimentalTestApi::class, InternalComposeUiApi::class,
    ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Phase2UiTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var container: KoinApplication
    private lateinit var owner: DefaultArchitectureComponentsOwner
    private val capacityReads = AtomicInteger()

    @Before fun prepare() {
        prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), "LIGHT")
        container = koinApplication {
            modules(desktopModules() + module {
                single<StorageRepository> {
                    object : StorageRepository by AllPathsExist {
                        override suspend fun readCapacity() = AllPathsExist.readCapacity().also { capacityReads.incrementAndGet() }
                    }
                }
                single<KeyStoreRepository> { EmptyKeys }
            })
        }
        owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
    }

    @After fun close() {
        owner.setLifecycleState(Lifecycle.State.DESTROYED)
        container.close()
    }

    @Composable private fun TestContext(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            KoinIsolatedContext(container) { content() }
        }
    }

    @Test fun settingsInputsSurviveRecompositionThemeChangesAndNavigation() = runDesktopComposeUiTest(width = 800, height = 572) {
        val revision = mutableIntStateOf(0)
        var composed = -1
        setContent {
            TestContext {
                val tick = revision.intValue
                App()
                SideEffect { composed = tick }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("设置") and hasClickAction()).performClick()
        val path = onNode(hasSetTextAction() and hasText("默认输出路径"))
        val suffix = onNode(hasSetTextAction() and hasText("签名后缀"))
        path.performTextReplacement("phase2 output path")
        suffix.performTextReplacement("-phase2-draft")
        for (index in 1..3) {
            runOnIdle {
                container.koin.get<org.tool.kit.domain.preferences.PreferencesRepository>().change(org.tool.kit.domain.preferences.PreferenceChange.Theme(if (index % 2 == 1) org.tool.kit.domain.preferences.ThemePreference.DARK else org.tool.kit.domain.preferences.ThemePreference.LIGHT))
                revision.intValue = index
            }
            waitUntil { composed == index }
            path.assertTextContains("phase2 output path")
            suffix.assertTextContains("-phase2-draft")
        }
        onNode(hasText("APK签名") and hasClickAction()).performClick()
        onNode(hasText("设置") and hasClickAction()).performClick()
        path.assertTextContains("phase2 output path")
        suffix.assertTextContains("-phase2-draft")
    }

    @Test fun capacityIsReadOnEntryAndExplicitRefreshButNotThemeRecomposition() = runDesktopComposeUiTest(width = 800, height = 572) {
        lateinit var vm: CleanerViewModel
        val dark = mutableStateOf(false)
        val focused = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val revision = mutableIntStateOf(0)
        var composed = -1
        setContent {
            TestContext {
                val current = koinViewModel<CleanerViewModel>()
                val tick = revision.intValue
                val originalWindow = LocalWindowInfo.current
                val window = remember { object : WindowInfo by originalWindow { override val isWindowFocused get() = focused.value } }
                CompositionLocalProvider(LocalWindowInfo provides window) {
                    AppTheme(dark.value) { if (visible.value) CleanerRoute(current, signatureHasResult = false, useDarkTheme = dark.value) }
                }
                SideEffect { vm = current; composed = tick }
            }
        }
        waitUntil { capacityReads.get() == 1 }
        for (index in 1..5) {
            runOnIdle { dark.value = !dark.value; revision.intValue = index }
            waitUntil { composed == index }
        }
        assertEquals(1, capacityReads.get())
        runOnIdle { vm.onIntent(CleanerIntent.RefreshCapacity) }
        waitUntil { capacityReads.get() == 2 }
        assertEquals(1_000L, vm.uiState.value.capacity.totalBytes)
        runOnIdle { focused.value = false }; waitForIdle()
        assertEquals(2, capacityReads.get())
        runOnIdle { focused.value = true }; waitUntil { capacityReads.get() == 3 }
        runOnIdle { visible.value = false }; waitForIdle()
        runOnIdle { visible.value = true }; waitUntil { capacityReads.get() == 4 }
    }

    @Test fun stableDropTargetUsesLatestCallbacksAndKeepsSynchronousAcceptance() = runDesktopComposeUiTest(width = 800, height = 572) {
        val ioScheduler = TestCoroutineScheduler()
        container.koin.loadModules(listOf(module {
            single { DesktopFileSelection(StandardTestDispatcher(ioScheduler)) }
        }))
        val version = mutableIntStateOf(0)
        var composed = -1
        lateinit var target: DragAndDropTarget
        val dragging = mutableListOf<Pair<Int, Boolean>>()
        val results = mutableListOf<Pair<Int, Result<List<Path>>>>()
        setContent {
            TestContext {
                val current = version.intValue
                val currentTarget = dragAndDropTarget(
                    dragging = { dragging += current to it },
                    onFinish = { results += current to it })
                SideEffect { target = currentTarget; composed = current }
            }
        }
        waitUntil { composed == 0 }
        val original = target
        runOnIdle { version.intValue = 1 }
        waitUntil { composed == 1 }
        assertSame(original, target)
        val textEvent = event(StringSelection("not a file list"))
        runOnIdle {
            target.onEntered(textEvent)
            target.onExited(textEvent)
            target.onEnded(textEvent)
            assertFalse(target.onDrop(textEvent))
            assertEquals(listOf(1 to true, 1 to false, 1 to false, 1 to false), dragging)
            assertEquals("file list not obtained", results.single().second.exceptionOrNull()?.message)
        }
        val file = temporary.newFile("valid after missing.apk")
        val candidates = listOf(temporary.root.resolve("missing.apk"), file)
        val fileEvent = event(object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: DataFlavor): Any = candidates
        })
        runOnIdle {
            assertTrue(target.onDrop(fileEvent))
            assertEquals(1, results.size, "IO filtering has not completed when native acceptance returns")
        }
        waitForIdle()
        runOnIdle { version.intValue = 2 }
        waitUntil { composed == 2 }
        assertSame(original, target)
        ioScheduler.runCurrent()
        waitUntil { results.size == 2 }
        assertEquals(2, results.last().first)
        assertEquals(listOf(file.toPath()), results.last().second.getOrThrow())
        assertEquals(1 to false, dragging.last())
    }

    private fun event(transferable: Transferable): DragAndDropEvent {
        val native = object : DropTargetDropEvent(DropTarget().dropTargetContext, Point(0, 0),
            DnDConstants.ACTION_COPY, DnDConstants.ACTION_COPY) {
            override fun getTransferable() = transferable
        }
        return DragAndDropEvent(DragAndDropTransferAction.Copy, native, Offset.Zero)
    }
}
