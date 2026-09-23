package org.tool.kit.tests.feature.cleaner

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.feature.cleaner.*
import org.tool.kit.theme.AppTheme
import java.io.File
import java.util.Locale
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CleanerLayoutUiTest {
    @Test fun detailsAreOptionalAndCheckboxDoesNotToggleThem() = runDesktopComposeUiTest(width = 720, height = 572) {
        val item = CleanerItemUi("log", "/projects/app/output.log", "app/output.log", 2048, 1_700_000_000_000,
            isDirectory = false, exists = true, matchedRuleNames = listOf("Logs"))
        var state by mutableStateOf(CleanerUiState(scanRoot = "/projects", items = listOf(item)))
        val opened = mutableListOf<String>()
        setContent { AppTheme(false) { CleanerScreen(state, { intent ->
            if (intent is CleanerIntent.ItemCheckedChanged) state = state.copy(items = listOf(item.copy(checked = intent.checked)))
        }, {}, opened::add) } }
        onNodeWithText("命中：Logs").assertExists()
        onNodeWithText(item.path).assertDoesNotExist()
        onNodeWithText("修改时间：", substring = true).assertDoesNotExist()
        onNodeWithTag("cleaner-result-log").performClick()
        onNodeWithText(item.path).assertExists()
        onNodeWithText("修改时间：", substring = true).assertExists()
        onNode(isToggleable()).performClick()
        onNodeWithText(item.path).assertExists()
        onNodeWithTag("cleaner-delete-selected").assertIsNotEnabled()
        onNodeWithContentDescription("文件", substring = false).performClick()
        assertEquals(listOf("/projects/app"), opened)
        onNodeWithContentDescription("收起项目详情").performClick()
        onNodeWithText(item.path).assertDoesNotExist()
    }

    @Test fun captureCompactCleanerStages() = captureStages(false)
    @Test fun captureEnglishCompactCleanerStages() = captureStages(true)

    @Test fun returningHomeKeepsStepsAndWelcomeSizesStable() = runDesktopComposeUiTest(width = 720, height = 572) {
        val idle = CleanerUiState()
        val item = CleanerItemUi("build", "/project/build", "build", 2048, 0, true, true)
        var state by mutableStateOf(idle)
        mainClock.autoAdvance = false
        setContent { AppTheme(false) { CleanerScreen(state, { intent ->
            if (intent == CleanerIntent.CloseSelection) state = idle
        }, {}, {}) } }
        mainClock.advanceTimeBy(400)
        val welcomeSize = onNodeWithTag("cleaner-welcome").fetchSemanticsNode().size
        val titleSize = onNodeWithTag("cleaner-welcome-title").fetchSemanticsNode().size
        val stepsSize = onNodeWithTag("cleaner-welcome-steps").fetchSemanticsNode().size
        for (phase in listOf(CleanerPhase.Scanning, CleanerPhase.Idle)) {
            runOnIdle { state = idle.copy(phase = phase, items = listOf(item)) }
            mainClock.advanceTimeBy(400)
            if (phase == CleanerPhase.Scanning) onNodeWithText("取消扫描").performClick()
            else onNodeWithTag("cleaner-close-selection").performClick()
            repeat(24) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(welcomeSize, onNodeWithTag("cleaner-welcome").fetchSemanticsNode().size,
                    "$phase return frame $frame must not reflow the welcome content")
                assertEquals(titleSize, onNodeWithTag("cleaner-welcome-title").fetchSemanticsNode().size,
                    "$phase return frame $frame must not change the title size")
                assertEquals(stepsSize, onNodeWithTag("cleaner-welcome-steps").fetchSemanticsNode().size,
                    "$phase return frame $frame must not resize the step cards")
            }
        }
    }

    private fun captureStages(english: Boolean) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE)
            // An 800 × 600 window leaves 720 × 572 after the rail and title bar.
            runDesktopComposeUiTest(width = 720, height = 572) {
                val idle = CleanerUiState(capacity = StorageCapacity(512L * 1024 * 1024 * 1024, 184L * 1024 * 1024 * 1024))
                val items = listOf(
                    CleanerItemUi("app", "/Projects/AndroidApp/app/build", "AndroidApp/app/build", 1258291200, 1_700_000_000_000,
                        true, true, matchedRuleNames = listOf(if (english) "Android build caches" else "Android 构建缓存")),
                    CleanerItemUi("library", "/Projects/AndroidApp/library/build", "AndroidApp/library/build", 524288000, 1_700_000_000_000,
                        true, true, matchedRuleNames = listOf(if (english) "Android build caches" else "Android 构建缓存")),
                    CleanerItemUi("logs", "/Projects/Server/output.log", "Server/output.log", 209715200, 1_700_000_000_000,
                        false, true, checked = false, matchedRuleNames = listOf(if (english) "Large logs" else "大型日志")),
                )
                var state by mutableStateOf(idle)
                var dark by mutableStateOf(false)
                var pickerRequests = 0
                // Advance phase transitions explicitly for deterministic captures.
                mainClock.autoAdvance = false
                setContent { AppTheme(dark) { CleanerScreen(state, {}, { pickerRequests++ }, {}) } }
                mainClock.advanceTimeBy(1000)
                val output = File(checkNotNull(System.getProperty("test.fixtureRoot")), "cleaner-screenshots").apply { mkdirs() }
                fun capture(name: String) {
                    waitForIdle()
                    val bitmap = onNode(isRoot()).captureToImage().asSkiaBitmap()
                    output.resolve("page-$name${if (english) "-en" else ""}.png")
                        .writeBytes(assertNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
                }
                for (theme in listOf(false, true)) {
                    runOnIdle { dark = theme; state = idle }
                    mainClock.advanceTimeBy(400)
                    onNodeWithTag("cleaner-start-scan").assertIsDisplayed()
                    onNodeWithTag("cleaner-rules-overview").assertIsDisplayed()
                    val overview = onNodeWithTag("cleaner-overview").fetchSemanticsNode().boundsInRoot
                    val welcome = onNodeWithTag("cleaner-welcome").fetchSemanticsNode().boundsInRoot
                    assertTrue(overview.bottom < welcome.top, "Space and rules must appear above the scan action")
                    assertTrue(overview.top <= 24f, "The overview must be aligned to the top of the page")
                    val picker = onNodeWithTag("cleaner-start-scan").fetchSemanticsNode().boundsInRoot
                    val page = onNode(isRoot()).fetchSemanticsNode().boundsInRoot
                    assertEquals(page.right - 16f, picker.right, 1f)
                    assertEquals(page.bottom - 16f, picker.bottom, 1f)
                    assertTrue(welcome.bottom < picker.top, "The welcome content must leave room for the floating action")
                    onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                    val cards = (1..3).map { index ->
                        onNodeWithTag("cleaner-welcome-step-0$index").assertIsDisplayed()
                            .fetchSemanticsNode().boundsInRoot
                    }
                    cards.zipWithNext().forEach { (left, right) ->
                        assertEquals(left.width, right.width, 1f)
                        assertEquals(left.top, right.top, 1f)
                        assertEquals(left.bottom, right.bottom, 1f)
                        assertEquals(12f, right.left - left.right, 1f)
                    }
                    assertTrue(cards.first().left >= 24f)
                    assertTrue(cards.last().right <= page.right - 24f)
                    (1..3).forEach { index ->
                        val description = onNodeWithTag("cleaner-step-description-0$index")
                            .fetchSemanticsNode().boundsInRoot
                        val card = cards[index - 1]
                        assertTrue(description.left >= card.left + 19f)
                        assertTrue(description.right <= card.right - 19f)
                        assertTrue(description.bottom <= card.bottom - 19f)
                    }
                    capture("idle-${if (theme) "dark" else "light"}")
                    onNodeWithTag("cleaner-start-scan").performClick()
                    val reveal = onNodeWithTag("cleaner-overview-reveal").fetchSemanticsNode().boundsInRoot
                    runOnIdle { state = idle.copy(phase = CleanerPhase.Scanning, scanRoot = "/Projects", items = items) }
                    mainClock.advanceTimeBy(112)
                    val closing = onNodeWithTag("cleaner-overview-reveal").fetchSemanticsNode().boundsInRoot
                    assertEquals(reveal.top, closing.top, 1f, "Collapse must keep the top edge fixed")
                    assertTrue(closing.height > 0f && closing.height < reveal.height,
                        "The overview must shrink from the bottom toward the top")
                    mainClock.advanceTimeBy(400)
                    onNodeWithTag("cleaner-progress").assertIsDisplayed()
                    onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
                    onNodeWithTag("cleaner-toolbar").assertDoesNotExist()
                    capture("scanning-${if (theme) "dark" else "light"}")
                    runOnIdle { state = state.copy(phase = CleanerPhase.Idle) }
                    mainClock.advanceTimeBy(400)
                    onNodeWithTag("cleaner-delete-selected").assertIsDisplayed()
                    onNodeWithTag("cleaner-toolbar-rules").assertIsDisplayed()
                    onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
                    capture("results-${if (theme) "dark" else "light"}")
                    runOnIdle { state = idle }
                    mainClock.advanceTimeBy(112)
                    val opening = onNodeWithTag("cleaner-overview-reveal").fetchSemanticsNode().boundsInRoot
                    assertEquals(reveal.top, opening.top, 1f, "Expansion must keep the top edge fixed")
                    assertTrue(opening.height > 0f && opening.height < reveal.height,
                        "The overview must expand downward from the top")
                    mainClock.advanceTimeBy(400)
                }
                mainClock.autoAdvance = true
                assertEquals(2, pickerRequests)
                val title = if (english) "Scan caches, clear what you choose" else "扫描缓存，按需清理"
                val descriptions = (1..3).map { index ->
                    onNodeWithTag("cleaner-step-description-0$index")
                        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
                }
                for (next in listOf(idle.copy(hasScanned = true), idle.copy(scanFailed = true),
                    idle.copy(needsRescan = true), idle.copy(rulesRecovered = true))) {
                    runOnIdle { state = next }
                    onNodeWithTag("cleaner-welcome-title").assertTextEquals(title)
                    descriptions.forEachIndexed { index, description ->
                        onNodeWithTag("cleaner-step-description-0${index + 1}").assertTextEquals(description)
                    }
                }
            }
        } finally { Locale.setDefault(originalLocale) }
    }
}
