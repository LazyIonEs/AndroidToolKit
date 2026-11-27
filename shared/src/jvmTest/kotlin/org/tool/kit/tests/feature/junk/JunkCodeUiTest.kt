package org.tool.kit.tests.feature.junk

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import org.tool.kit.model.JunkMode
import org.tool.kit.tests.support.prepareTestPreferences
import org.tool.kit.theme.AppTheme

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class JunkCodeUiTest {
    @Test fun lightForms() = forms("LIGHT", width = 800)
    @Test fun darkCompactForms() = forms("DARK", width = 540)

    @Test fun switchingModesAnimatesTheFormAndOnlyTheCurrentModeAcceptsActions() =
        runDesktopComposeUiTest(width = 800, height = 572) {
            var state by mutableStateOf(JunkCodeUiState("/tmp", JunkMode.SINGLE))
            val intents = mutableListOf<JunkCodeIntent>()
            setContent {
                AppTheme(false) {
                    JunkCodeScreen(state, onIntent = { intent ->
                        intents += intent
                        if (intent is ModeChanged) {
                            state = state.copy(mode = intent.value, estimatedSize = "25–75 MB")
                        }
                    }, pickOutput = {})
                }
            }
            val singleMode = onNode(hasText("单AAR模式") and hasClickAction())
            val multiMode = onNode(hasText("多AAR模式") and hasClickAction())
            val generate = onNodeWithText("开始生成")
            val singleTitle = onNodeWithText("包名与资源")
            val multiTitle = onNodeWithText("批量配置")
            val header = listOf(onNodeWithText("AAR输出路径"), singleMode, multiMode)
            val headerBounds = header.map { it.getUnclippedBoundsInRoot() }
            val settledLeft = singleTitle.getUnclippedBoundsInRoot().left
            val retiredSubmit = checkNotNull(generate.fetchSemanticsNode().config[SemanticsActions.OnClick].action)

            mainClock.autoAdvance = false
            try {
                multiMode.performClick()
                mainClock.advanceTimeBy(48)
                assertTrue(multiTitle.getUnclippedBoundsInRoot().left > settledLeft)
                singleTitle.assertDoesNotExist()
                runOnIdle { retiredSubmit() }
                assertEquals(listOf<JunkCodeIntent>(ModeChanged(JunkMode.MULTI)), intents)

                mainClock.advanceTimeBy(1000)
                singleMode.performClick()
                mainClock.advanceTimeBy(48)
                assertTrue(singleTitle.getUnclippedBoundsInRoot().left < settledLeft)
                multiTitle.assertDoesNotExist()
                multiMode.performClick()
                mainClock.advanceTimeBy(1000)

                multiMode.assertIsOn()
                assertEquals(settledLeft, multiTitle.getUnclippedBoundsInRoot().left)
                assertEquals(headerBounds, header.map { it.getUnclippedBoundsInRoot() })
                onNodeWithText("25–75 MB").assertIsDisplayed()
                generate.performClick()
                assertEquals(
                    listOf(ModeChanged(JunkMode.MULTI), ModeChanged(JunkMode.SINGLE), ModeChanged(JunkMode.MULTI), Submit),
                    intents,
                )
            } finally {
                mainClock.autoAdvance = true
            }
        }

    private fun forms(theme: String, width: Int) = runDesktopComposeUiTest(width = width, height = 572, testTimeout = 90.seconds) {
        val fixture = File(checkNotNull(System.getProperty("test.fixtureRoot")))
        prepareTestPreferences(fixture, theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val requests = mutableListOf<GenerateJunkCodeRequest>()
        val gate = CompletableDeferred<GeneratedJunkCode>()
        val container = koinApplication { modules(desktopModules() + module {
            single<JunkCodeRepository> { JunkCodeRepository { requests += it; gate.await() } }
        }) }
        lateinit var vm: JunkCodeViewModel
        container.koin.loadModules(listOf(module { viewModel { JunkCodeViewModel(get(), get(), get(), get(), get(), get()).also { vm = it } } }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("垃圾代码").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            val outputPath = onNodeWithText("AAR输出路径")
            val singleMode = onNode(hasText("单AAR模式") and hasClickAction())
            val multiMode = onNode(hasText("多AAR模式") and hasClickAction())
            val generate = onNodeWithText("开始生成")
            val minimumField = onNodeWithText("最小大小")
            val maximumField = onNodeWithText("最大大小")
            val slider = onNodeWithTag("aar-size-range")
            val formScroll = onNodeWithTag("aar-form-scroll")
            val thumbs = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)
            fun checkPanels(title: String) {
                val single = singleMode.getUnclippedBoundsInRoot()
                val multi = multiMode.getUnclippedBoundsInRoot()
                assertEquals(outputPath.getUnclippedBoundsInRoot().left, single.left)
                assertEquals(generate.getUnclippedBoundsInRoot().right, multi.right)
                val configuration = onNodeWithTag("aar-config-panel").getUnclippedBoundsInRoot()
                val size = onNodeWithTag("aar-size-panel").getUnclippedBoundsInRoot()
                assertEquals(single.left, configuration.left)
                assertEquals(multi.right, size.right)
                val scrollRange = formScroll.fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
                if (width == 800) {
                    assertEquals(configuration.top, size.top)
                    assertEquals(single.right, configuration.right)
                    assertEquals(multi.left, size.left)
                    assertEquals(0f, scrollRange)
                    listOf(minimumField, maximumField, slider, generate).forEach { it.assertIsDisplayed() }
                } else {
                    assertEquals(configuration.left, size.left)
                    assertTrue(configuration.bottom < size.top)
                    assertTrue(scrollRange > 0f)
                }
                val maximum = maximumField.getUnclippedBoundsInRoot()
                val track = slider.getUnclippedBoundsInRoot()
                assertTrue(track.top > maximum.bottom)
                assertEquals(onNodeWithText(title).getUnclippedBoundsInRoot().left, track.left)
                assertEquals(maximum.right, track.right)
            }
            waitForIdle()
            singleMode.assertIsOn()
            checkPanels("包名与资源")
            val fixedPath = outputPath.assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(fixedPath.bottom < singleMode.getUnclippedBoundsInRoot().top)
            assertTrue(fixedPath.bottom < multiMode.getUnclippedBoundsInRoot().top)
            onNodeWithText("junk_com_dev_junk_plugin_TT3.1.0.aar").assertIsDisplayed()
            val fixedAction = generate.assertIsDisplayed().getUnclippedBoundsInRoot()
            slider.performScrollTo().assertIsDisplayed()
            assertEquals(fixedPath, outputPath.getUnclippedBoundsInRoot())
            assertEquals(fixedAction, generate.getUnclippedBoundsInRoot())
            minimumField.assertTextContains("17.5")
            maximumField.assertTextContains("22.5")
            onNodeWithText("100 MB").assertExists()
            runOnIdle {
                listOf(MinimumSizeChanged(""), MaximumSizeChanged(""), ResPrefixChanged(""), PackageNameChanged(""), SuffixChanged("")).forEach(vm::onIntent)
            }
            multiMode.assertIsDisplayed().performClick().assertIsOn()
            checkPanels("批量配置")
            minimumField.assertTextContains("0.5")
            maximumField.assertTextContains("1.5")
            onNodeWithText("10 MB").assertExists()
            onNodeWithText("25–75 MB").assertIsDisplayed()
            generate.assertIsDisplayed()
            onNodeWithText("生成策略", substring = true).assertDoesNotExist()
            runOnIdle { listOf(OutputDirChanged(""), AarCountChanged(""), MinimumSizeChanged(""), MaximumSizeChanged("")).forEach(vm::onIntent) }
            waitForIdle()
            // Exercise form callbacks, retained drafts, and the page loading state.
            runOnIdle {
                listOf(PackageNameChanged("com.fixture"), SuffixChanged("plugin"), ResPrefixChanged("fixture_"),
                    OutputDirChanged("batch"), AarCountChanged("2"), MinimumSizeChanged("1.5"), MaximumSizeChanged("2.5")).forEach(vm::onIntent)
            }
            maximumField.performScrollTo().performTextReplacement("5.5")
            minimumField.performScrollTo().performTextReplacement("4,375")
            onNodeWithText("AAR 数量").performScrollTo().performTextReplacement("30")
            onNodeWithText("131.25–165 MB").assertIsDisplayed()
            thumbs.assertCountEquals(2)
            thumbs.onLast().performScrollTo().performMouseInput { moveTo(center); press(); moveBy(Offset(40f, 0f)) }
            val draggedMaximum = runOnIdle {
                val state = vm.uiState.value
                val form = state.multi.size
                val bytes = checkNotNull(form.range).maximumBytes
                assertEquals("4,375", form.minimumMb)
                assertTrue(bytes in 6_000_000L..6_500_000L && bytes % 500_000L == 0L)
                assertTrue(state.estimatedSize != "131.25–165 MB")
                form.maximumMb
            }
            maximumField.assertTextContains(draggedMaximum)
            thumbs.onLast().performMouseInput { release() }

            maximumField.performScrollTo().performTextReplacement("5.625")
            thumbs.onFirst().performScrollTo().performMouseInput { moveTo(center); press(); moveBy(Offset(-40f, 0f)); release() }
            runOnIdle {
                val range = checkNotNull(vm.uiState.value.multi.size.range)
                assertTrue(range.minimumBytes < 4_375_000 && range.minimumBytes % 500_000 == 0L)
                assertEquals(5_625_000L, range.maximumBytes)
            }

            minimumField.performScrollTo().performTextReplacement("4.375")
            maximumField.performScrollTo().performTextReplacement("4")
            onNodeWithText("请输入大于 0 的数值", substring = true).assertExists()
            onNodeWithText("—").assertExists()
            maximumField.performScrollTo().performTextReplacement("12.25")
            onNodeWithText("50 MB").assertExists()
            onNodeWithText("131.25–367.5 MB").assertExists()
            minimumField.performScrollTo().performTextReplacement("0.125")
            onNodeWithText("0.125 MB").assertExists()
            onNodeWithText("50.125 MB").assertExists()
            onNodeWithText("3.75–367.5 MB").assertExists()
            assertEquals(0.125f, thumbs.onFirst().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.start)
            assertEquals(50.125f, thumbs.onLast().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive)

            singleMode.assertIsDisplayed().performClick().assertIsOn()
            onNodeWithText("包名").performScrollTo().performTextReplacement("org.fixture.ui")
            onNodeWithText("后缀").performScrollTo().performTextReplacement("part.one")
            onNodeWithText("junk_org_fixture_ui_part.one_TT3.1.0.aar").assertIsDisplayed()
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            onNodeWithText("后缀").performScrollTo().assertTextContains("part.one")
            onNodeWithText("批量总大小范围").assertDoesNotExist()
            onNodeWithText("100 MB").assertExists()
            minimumField.performScrollTo().performTextReplacement("17.500123")
            maximumField.performScrollTo().performTextReplacement("22.5")
            onNodeWithText("生成策略", substring = true).assertDoesNotExist()
            onNodeWithText("生成 DTO 文本适配方法").assertDoesNotExist()
            generate.assertIsDisplayed().performClick()
            waitUntil(timeoutMillis = 10_000) { vm.uiState.value.busy && requests.size == 1 }
            onAllNodesWithContentDescription("Lottie animation").onFirst().assertExists()
            val request = requests.single()
            assertEquals(JunkConfiguration.SingleBySize("org.fixture.ui.part.one", JunkAarSizeRange(17_500_123, 22_500_000), "fixture_"),
                request.configuration)
            assertEquals(JunkGenerationStrategy(), request.strategy)
            runOnIdle { gate.complete(GeneratedJunkCode("fixture-result.aar", listOf("fixture-result.aar"), 123)) }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.busy }
            onNodeWithText("跳转").assertExists()
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
