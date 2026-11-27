package org.tool.kit.tests.feature.junk

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
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
                            state = state.copy(mode = intent.value, estimatedSize = "25.0–75.0 MB")
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
                onNodeWithText("25.0–75.0 MB").assertIsDisplayed()
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
                    assertTrue(configuration.right < size.left)
                    val modeGapCenter = (single.right + multi.left) / 2
                    val panelGapCenter = (configuration.right + size.left) / 2
                    assertEquals(modeGapCenter, panelGapCenter)
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
            onNodeWithText("100.0 MB").assertExists()
            onNodeWithText("0.5 MB 步进").assertDoesNotExist()
            runOnIdle {
                listOf(MinimumSizeChanged(""), MaximumSizeChanged(""), ResPrefixChanged(""), PackageNameChanged(""), SuffixChanged("")).forEach(vm::onIntent)
            }
            multiMode.assertIsDisplayed().performClick().assertIsOn()
            checkPanels("批量配置")
            minimumField.assertTextContains("0.5")
            maximumField.assertTextContains("1.5")
            onNodeWithText("10.0 MB").assertExists()
            onNodeWithText("25.0–75.0 MB").assertIsDisplayed()
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
            minimumField.assertTextContains("4,375")
            onNodeWithText("AAR 数量").performScrollTo().performTextReplacement("30")
            minimumField.assertTextContains("4.4")
            onNodeWithText("132.0–165.0 MB").assertIsDisplayed()
            thumbs.assertCountEquals(2)
            thumbs.onLast().performScrollTo().performMouseInput { moveTo(center); press(); moveBy(Offset(40f, 0f)) }
            val draggedMaximum = runOnIdle {
                val state = vm.uiState.value
                val form = state.multi.size
                val bytes = checkNotNull(form.range(state.mode)).maximumBytes
                assertEquals("4.4", form.minimumMb)
                assertTrue(bytes > 5_500_000L)
                assertEquals(0L, bytes % BYTES_PER_TENTH_MB)
                assertTrue(state.estimatedSize != "132.0–165.0 MB")
                form.maximumMb
            }
            maximumField.assertTextContains(draggedMaximum)
            thumbs.onLast().performMouseInput { release() }

            thumbs.onLast().performSemanticsAction(SemanticsActions.SetProgress) { it(5.85f) }
            maximumField.assertTextContains("5.9")
            thumbs.onLast().performSemanticsAction(SemanticsActions.SetProgress) { it(4.5f) }
            maximumField.assertTextContains("4.9")
            minimumField.assertTextContains("4.4")

            maximumField.performScrollTo().performTextReplacement("5.625003")
            maximumField.performImeAction()
            maximumField.assertTextContains("5.6")
            thumbs.onFirst().performScrollTo().performMouseInput { moveTo(center); press(); moveBy(Offset(-40f, 0f)); release() }
            runOnIdle {
                val state = vm.uiState.value
                val range = checkNotNull(state.multi.size.range(state.mode))
                assertTrue(range.minimumBytes < 4_400_000L)
                assertEquals(5_600_000L, range.maximumBytes)
            }

            minimumField.performScrollTo().performTextReplacement("4.375")
            maximumField.performScrollTo().performTextReplacement("4.849999")
            onNodeWithText("最大值须至少比最小值大 0.5 MB", substring = true).assertExists()
            onNodeWithText("—").assertExists()
            maximumField.performScrollTo().performTextReplacement("9.5")
            onNodeWithText("132.0–285.0 MB").assertIsDisplayed()
            maximumField.performScrollTo().performTextReplacement("12.25")
            maximumField.performKeyInput { pressKey(Key.Enter) }
            maximumField.assertTextContains("12.3")
            onNodeWithText("大小须在 0.5–10.0 MB 内", substring = true).assertExists()
            onNodeWithText("—").assertExists()
            assertEquals(10f, thumbs.onLast().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive)
            runOnIdle { assertEquals(JunkAarSizeRange(4_400_000, 9_500_000), vm.uiState.value.multi.size.sliderSelection(JunkMode.MULTI)) }

            maximumField.performScrollTo().performTextReplacement("9.95")
            minimumField.performScrollTo().performTextReplacement("0.125")
            minimumField.performKeyInput { pressKey(Key.Enter) }
            minimumField.assertTextContains("0.1")
            onNodeWithText("大小须在 0.5–10.0 MB 内", substring = true).assertExists()
            onNodeWithText("—").assertExists()
            assertEquals(0.5f, thumbs.onFirst().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.start)
            minimumField.performScrollTo().performTextReplacement("0.45")
            onNodeWithText("15.0–300.0 MB").assertIsDisplayed()

            singleMode.assertIsDisplayed().performClick().assertIsOn()
            runOnIdle { assertEquals("0.5", vm.uiState.value.multi.size.minimumMb) }
            onNodeWithText("包名").performScrollTo().performTextReplacement("org.fixture.ui")
            onNodeWithText("后缀").performScrollTo().performTextReplacement("part.one")
            onNodeWithText("junk_org_fixture_ui_part.one_TT3.1.0.aar").assertIsDisplayed()
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            onNodeWithText("后缀").performScrollTo().assertTextContains("part.one")
            onNodeWithText("批量总大小范围").assertDoesNotExist()
            onNodeWithText("100.0 MB").assertExists()
            minimumField.performScrollTo().performTextReplacement("17.55")
            maximumField.performScrollTo().performTextReplacement("100.05")
            maximumField.performKeyInput { pressKey(Key.Enter) }
            maximumField.assertTextContains("100.1")
            onNodeWithText("大小须在 0.5–100.0 MB 内", substring = true).assertExists()
            assertEquals(100f, thumbs.onLast().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive)
            maximumField.performScrollTo().performTextReplacement("22.5")
            thumbs.onFirst().performSemanticsAction(SemanticsActions.SetProgress) { it(21.5f) }
            minimumField.assertTextContains("20.5")
            maximumField.assertTextContains("22.5")
            minimumField.performScrollTo().performTextReplacement("17.55")
            onNodeWithText("生成策略", substring = true).assertDoesNotExist()
            onNodeWithText("生成 DTO 文本适配方法").assertDoesNotExist()
            generate.assertIsDisplayed().performClick()
            waitUntil(timeoutMillis = 10_000) { vm.uiState.value.busy && requests.size == 1 }
            onAllNodesWithContentDescription("Lottie animation").onFirst().assertExists()
            val request = requests.single()
            assertEquals(JunkConfiguration.SingleBySize("org.fixture.ui.part.one", JunkAarSizeRange(17_600_000, 22_500_000), "fixture_"),
                request.configuration)
            assertEquals(JunkGenerationStrategy(), request.strategy)
            runOnIdle { gate.complete(GeneratedJunkCode("fixture-result.aar", listOf("fixture-result.aar"), 123)) }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.busy }
            onNodeWithText("跳转").assertExists()
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
