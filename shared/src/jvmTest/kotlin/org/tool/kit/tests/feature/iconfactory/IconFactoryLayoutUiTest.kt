package org.tool.kit.tests.feature.iconfactory

import androidx.compose.runtime.*
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.domain.preferences.PreferencesSnapshot
import org.tool.kit.feature.iconfactory.*
import org.tool.kit.theme.AppTheme
import org.tool.kit.utils.getFileImageRequest
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class IconFactoryLayoutUiTest {
    @Test fun bothLayoutsKeepStateAndActionsAt800By600() = exerciseLayout()
    @Test fun englishLayoutsKeepLongNamesAndLabelsAt800By600() = exerciseLayout(english = true)

    @Test fun interruptedTransitionsKeepTheDensityAndIgnoreRetiringActions() = runDesktopComposeUiTest(width = 800, height = 600) {
        val state = IconFactoryUiState(IconFactoryForm(inputPath = "launcher.png", outputPath = "/tmp/icons"),
            PreferencesSnapshot().iconFactoryData)
        val intents = mutableListOf<IconFactoryIntent>()
        var picks = 0
        val target = object : DragAndDropTarget { override fun onDrop(event: DragAndDropEvent) = false }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                AppTheme(false) { IconFactoryScreen(state, null, null, intents::add, {}, { picks++ }, false, target) }
            }
        }
        val buttons = listOf("icon-layout-overview", "icon-layout-detail")
        val initialBounds = buttons.map { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val retiredGenerate = onNodeWithTag("icon-generate").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val retiredPick = onNodeWithTag("icon-pick-image").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        mainClock.autoAdvance = false
        onNodeWithTag("icon-result-xhdpi").performClick()
        mainClock.advanceTimeBy(64)
        onNodeWithTag("icon-overview-content").assertDoesNotExist()
        onNodeWithTag("icon-detail-content").assertExists()
        assertEquals(initialBounds, buttons.map { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot },
            "The header controls must not move with the content")
        val enteringLeft = onNodeWithTag("icon-detail-content").fetchSemanticsNode().boundsInRoot.left
        // A callback queued by retiring content must not start work or reopen a picker.
        runOnIdle { retiredGenerate(); retiredPick() }
        assertTrue(intents.isEmpty())
        assertEquals(0, picks)
        onNodeWithTag("icon-layout-overview").performClick()
        mainClock.advanceTimeBy(32)
        onNodeWithTag("icon-layout-detail").performClick()
        mainClock.advanceTimeBy(1000)
        onNodeWithTag("icon-overview-content").assertDoesNotExist()
        onNodeWithTag("icon-layout-detail").assertIsOn()
        onNodeWithTag("icon-density-xhdpi").assertIsSelected()
        onNodeWithText("96 × 96 px").assertIsDisplayed()
        val settledLeft = onNodeWithTag("icon-detail-content").fetchSemanticsNode().boundsInRoot.left
        assertTrue(enteringLeft > settledLeft, "The incoming layout must slide into its final position")
        assertEquals(initialBounds, buttons.map { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot })
        onNodeWithTag("icon-generate").performClick()
        onNodeWithTag("icon-pick-image").performClick()
        assertEquals(listOf<IconFactoryIntent>(IconFactoryIntent.Submit), intents)
        assertEquals(1, picks)
    }

    private fun exerciseLayout(english: Boolean = false) {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE)
        try {
            runDesktopComposeUiTest(width = 800, height = 600) {
                val output = File(checkNotNull(System.getProperty("test.fixtureRoot")), "icon-factory-layout").apply { mkdirs() }
                val input = output.resolve(if (english) "launcher-with-a-long-descriptive-filename.png" else "launcher.png")
                val pixels = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until 256) for (x in 0 until 256) {
                    pixels.setRGB(x, y, (255 shl 24) or ((60 + x / 2) shl 16) or ((100 + y / 2) shl 8) or 70)
                }
                ImageIO.write(pixels, "png", input)
                val loaded = AtomicBoolean(false)
                val image = IconImageUi(input.path, getFileImageRequest(input.path).newBuilder()
                    .listener(onSuccess = { _, _ -> loaded.set(true) }).build())
                val idle = IconFactoryUiState(IconFactoryForm(outputPath = "/Users/demo/Downloads"), PreferencesSnapshot().iconFactoryData)
                var state by mutableStateOf(idle)
                var dark by mutableStateOf(false)
                var dragging by mutableStateOf(false)
                var picks = 0
                val intents = mutableListOf<IconFactoryIntent>()
                val target = object : DragAndDropTarget { override fun onDrop(event: DragAndDropEvent) = false }
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        AppTheme(dark) {
                            IconFactoryScreen(state, image.takeIf { state.form.inputPath != null },
                                state.result?.map { image.takeIf { _ -> it.previewAvailable } },
                                intents::add, {}, { picks++ }, dragging, target)
                        }
                    }
                }
                fun overview() = onNodeWithTag("icon-layout-overview").performClick()
                fun detail() = onNodeWithTag("icon-layout-detail").performClick()
                fun verifyActions(enabled: Boolean) {
                    listOf("icon-generate", "icon-more-settings", "icon-pick-image").forEach {
                        val node = onNodeWithTag(it).assertIsDisplayed()
                        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
                    }
                    onNodeWithTag("icon-layout-overview").assertIsEnabled()
                    onNodeWithTag("icon-layout-detail").assertIsEnabled()
                }
                fun capture(stage: String) {
                    onNodeWithTag("icon-workspace").performMouseInput { moveTo(Offset.Zero) }
                    mainClock.advanceTimeBy(500)
                    waitForIdle()
                    val bounds = onNodeWithTag("icon-workspace").fetchSemanticsNode().boundsInRoot
                    assertEquals(800f, bounds.width)
                    assertEquals(600f, bounds.height)
                    onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach {
                        val b = it.boundsInRoot
                        assertTrue(b.left >= bounds.left && b.right <= bounds.right && b.top >= bounds.top && b.bottom <= bounds.bottom,
                            "Text must fit the 800 × 600 page: $b")
                    }
                    onNodeWithTag("icon-generate").assertIsDisplayed()
                    val bitmap = onNodeWithTag("icon-workspace").captureToImage().asSkiaBitmap()
                    output.resolve("800-600${if (english) "-en" else ""}-$stage.png")
                        .writeBytes(assertNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
                }
                fun countLabel(count: Int) = if (english) "$count / 5 previews available" else "$count / 5 个预览可用"

                onNodeWithTag("icon-layout-overview").assertIsOn()
                listOf(if (english) "Overview" else "总览", if (english) "Detail" else "大图").forEach { label ->
                    onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                        val results = mutableListOf<TextLayoutResult>()
                        assertTrue(get(results))
                        assertTrue(results.isNotEmpty())
                        results.forEach {
                            assertEquals(1, it.lineCount, "Layout labels must stay on one line")
                            assertEquals(label.length, it.getLineEnd(0, visibleEnd = true),
                                "The icon must not crowd out the layout label: $label")
                            assertFalse(it.isLineEllipsized(0))
                        }
                    }
                }
                overview() // An already selected button must not leave both layouts unchecked.
                onNodeWithTag("icon-layout-overview").assertIsOn()
                onNodeWithTag("icon-layout-detail").assertIsOff()
                onNodeWithTag("icon-generate").assertIsNotEnabled()
                onNodeWithTag("icon-pick-image").performClick()
                assertEquals(1, picks)
                val sourceBounds = onNodeWithTag("icon-source").fetchSemanticsNode().boundsInRoot
                val resultBounds = onNodeWithTag("icon-results").fetchSemanticsNode().boundsInRoot
                assertTrue(sourceBounds.bottom < resultBounds.top, "Overview puts the source above all five previews")
                capture("overview-empty-light")
                detail()
                onNodeWithTag("icon-density-xxxhdpi").assertIsSelected()
                onNodeWithTag("icon-generate").assertIsNotEnabled()
                onNodeWithTag("icon-more-settings").assertIsEnabled()
                capture("detail-empty-light")

                runOnIdle { state = idle.copy(form = idle.form.copy(inputPath = input.path)) }
                waitUntil(timeoutMillis = 10_000) { mainClock.advanceTimeByFrame(); loaded.get() }
                overview()
                onNodeWithTag("icon-result-xhdpi").performClick()
                onNodeWithTag("icon-layout-detail").assertIsOn()
                onNodeWithTag("icon-density-xhdpi").assertIsSelected()
                onNodeWithText("96 × 96 px").assertIsDisplayed()
                overview(); detail()
                onNodeWithTag("icon-density-xhdpi").assertIsSelected()
                assertTrue(intents.isEmpty(), "Layout and density changes must not dispatch business actions")
                onNodeWithTag("icon-generate").performClick()
                onNodeWithTag("icon-more-settings").performClick()
                assertEquals(listOf(IconFactoryIntent.Submit, IconFactoryIntent.SheetOpened), intents)

                mainClock.autoAdvance = false
                runOnIdle { state = state.copy(busy = true, result = null) }
                mainClock.advanceTimeBy(400)
                verifyActions(false)
                onNodeWithTag("icon-progress", useUnmergedTree = true).assertExists()
                capture("detail-busy-light")
                overview()
                mainClock.advanceTimeBy(400)
                verifyActions(false)
                capture("overview-busy-light")
                runOnIdle { state = state.copy(busy = false, result = List(5) { IconResultUi(input.path, true) }) }
                mainClock.advanceTimeBy(400)
                mainClock.autoAdvance = true
                val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
                val sizes = listOf(48, 72, 96, 144, 192)
                densities.forEach { onNodeWithText(it).assertIsDisplayed() }
                sizes.forEach { onNodeWithText("$it × $it px").assertIsDisplayed() }
                verifyActions(true)
                onNodeWithText(countLabel(5)).assertIsDisplayed()
                capture("overview-results-light")
                detail()
                densities.zip(sizes).forEach { (density, size) ->
                    onNodeWithTag("icon-density-$density").performClick()
                    onNodeWithText("$size × $size px").assertIsDisplayed()
                }
                val loadedState = state
                overview(); detail()
                assertSame(loadedState, state, "Switching layout must preserve input, settings and results")
                onNodeWithTag("icon-density-xxxhdpi").assertIsSelected()
                verifyActions(true)
                capture("detail-results-light")
                runOnIdle { dark = true }
                capture("detail-results-dark")
                overview(); capture("overview-results-dark")

                runOnIdle { state = state.copy(result = listOf(IconResultUi(input.path, false)) + state.result!!.drop(1)) }
                onNodeWithText(countLabel(4)).assertIsDisplayed()
                onNodeWithContentDescription(if (english) "Preview unavailable" else "预览不可用").assertExists()
                onNodeWithTag("icon-result-mdpi").performClick()
                onNodeWithText("48 × 48 px").assertIsDisplayed()
                onNodeWithText(countLabel(4)).assertIsDisplayed()
                capture("detail-partial-dark")
                runOnIdle { state = state.copy(result = emptyList()) }
                onNodeWithText(countLabel(0)).assertIsDisplayed()
                onNodeWithTag("icon-generate").assertIsEnabled()
                capture("detail-error-dark")

                runOnIdle { state = state.copy(form = state.form.copy(outputPath = "/Users/demo/Desktop", iconName = "new_icon")) }
                overview(); detail()
                onNodeWithText("/Users/demo/Desktop/res").assertIsDisplayed()
                onNodeWithText("new_icon", substring = true).assertIsDisplayed()
                runOnIdle { state = state.copy(form = state.form.copy(inputPath = null), result = null) }
                onNodeWithTag("icon-generate").assertIsNotEnabled()
                onNodeWithText(countLabel(0)).assertDoesNotExist()
                overview()
                onNodeWithTag("icon-generate").assertIsNotEnabled()
                assertEquals(listOf(IconFactoryIntent.Submit, IconFactoryIntent.SheetOpened), intents)

                // Existing drag feedback remains available in the new layouts and suppressed while busy.
                onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                mainClock.autoAdvance = false
                runOnIdle { dragging = true }
                mainClock.advanceTimeBy(400)
                onNodeWithContentDescription("Lottie animation").assertIsDisplayed()
                runOnIdle { dragging = false }
                mainClock.advanceTimeBy(500)
                onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                runOnIdle { state = state.copy(busy = true); dragging = true }
                mainClock.advanceTimeBy(500)
                onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
