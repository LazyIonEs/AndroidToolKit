package org.tool.kit.tests.feature.apk

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.junit.Test
import org.tool.kit.domain.apk.*
import org.tool.kit.feature.apk.*
import org.tool.kit.theme.AppTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ApkInformationContentsUiTest {
    private val sample = ApkInformationTestData.sample
    private val target = ApkInformationTestData.target
    private fun ComposeUiTest.tab(index: Int) { onNodeWithTag("apk-tab-$index").performClick().assertIsSelected() }
    private fun ComposeUiTest.back() { onNodeWithTag("apk-detail-back").performClick() }
    private fun ComposeUiTest.search(): SemanticsNodeInteraction {
        if (onAllNodesWithTag("apk-detail-search").fetchSemanticsNodes().isEmpty()) {
            onNodeWithTag("apk-detail-search-toggle").performClick()
        }
        return onNodeWithTag("apk-detail-search")
    }

    @Test fun componentDetailPreservesQueryFilterAndRawCopyAcrossTabs() = runDesktopComposeUiTest(width = 800, height = 600) {
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        tab(2)
        onNodeWithTag("apk-component-filter-Service").performClick().assertIsSelected()
        search().performTextInput(":sync")
        onNodeWithTag("apk-component-Service:com.example.app.SyncService").performClick()
        assertTrue(copied.isEmpty())
        onNodeWithContentDescription("复制运行进程").performClick()
        assertEquals(listOf("com.example.app:sync"), copied)
        tab(1); tab(2)
        onNodeWithTag("apk-component-detail").assertIsDisplayed()
        back()
        search().assertTextContains(":sync")
        onNodeWithTag("apk-component-filter-Service").assertIsSelected()
        onNodeWithText("1 / 5 项").assertExists()
    }

    @Test fun aliasTargetAndMissingSearchRemainDistinct() = runDesktopComposeUiTest(width = 800, height = 600) {
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        tab(2)
        onNodeWithTag("apk-component-filter-ActivityAlias").performClick()
        onNodeWithTag("apk-component-ActivityAlias:com.example.app.Shortcut").performClick()
        onNodeWithContentDescription("复制目标 Activity").performClick()
        assertEquals(listOf(sample.launchableActivity), copied)
        back(); search().performTextInput("missing")
        onNodeWithText("没有匹配的条目").assertIsDisplayed()
        onNodeWithContentDescription("清空搜索").performClick()
        onNodeWithTag("apk-component-filter-ActivityAlias").assertIsSelected()
        onNodeWithText("Shortcut").assertIsDisplayed()
    }

    @Test fun librariesKeepAbiAndShowIndependentAlignmentResults() = runDesktopComposeUiTest(width = 800, height = 600) {
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        onNodeWithTag("apk-open-Libraries").performClick()
        onNodeWithTag("apk-abi-filter-arm64-v8a").performClick()
        search().performTextInput("broken")
        onNodeWithTag("apk-library-lib/arm64-v8a/libbroken.so").performClick()
        onNodeWithText("ELF 16 KB · 未能解析").assertExists()
        onNodeWithText("ZIP 16 KB · 未对齐").assertExists()
        onNodeWithContentDescription("复制文件路径").performClick()
        assertEquals(listOf("lib/arm64-v8a/libbroken.so"), copied)
        back(); search().performTextReplacement("graphics")
        onNodeWithTag("apk-library-lib/arm64-v8a/libgraphics.so").performClick()
        onNodeWithText("ZIP 16 KB · 不适用").assertExists()
        onNodeWithText("ELF 16 KB · 对齐通过").assertExists()
        tab(0); tab(3); back()
        search().assertTextContains("graphics")
        onNodeWithTag("apk-abi-filter-arm64-v8a").assertIsSelected()
    }

    @Test fun breakdownAndFileNavigationPreserveIndependentFileState() = runDesktopComposeUiTest(width = 800, height = 600) {
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        onNodeWithTag("apk-open-breakdown").performClick()
        onNodeWithText("ZIP 结构与签名区").assertIsDisplayed()
        onNodeWithTag("apk-breakdown-files").assertIsDisplayed().performClick()
        onNodeWithTag("apk-file-filter-Dex").performClick().assertIsSelected()
        search().performTextInput("DEX")
        onNodeWithTag("apk-file-classes.dex").performClick()
        assertEquals(listOf("classes.dex"), copied)
        back(); onNodeWithTag("apk-open-Package").performClick()
        search().assertTextContains("DEX")
        onNodeWithTag("apk-file-filter-Dex").assertIsSelected()
        tab(2); tab(0)
        search().assertTextContains("DEX")
        search().performTextReplacement("none")
        onNodeWithText("没有匹配的条目").assertIsDisplayed()
    }

    @Test fun fileOrderUsesCompressedSizeAndCopyKeepsFullPath() = runDesktopComposeUiTest(width = 800, height = 600) {
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        onNodeWithTag("apk-open-Package").performClick()
        val first = onNodeWithTag("apk-file-lib/x86_64/libgraphics.so")
        first.assertIsDisplayed()
        val second = onNodeWithTag("apk-file-lib/arm64-v8a/libgraphics.so")
        assertTrue(first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top)
        first.performClick()
        assertEquals(listOf("lib/x86_64/libgraphics.so"), copied)
    }

    @Test fun largeComponentListRemainsLazyAndReturnsToSameScrollPosition() = runDesktopComposeUiTest(width = 800, height = 600) {
        val components = List(300) { sample.components!!.first().copy(name = "com.example.Activity${it.toString().padStart(3, '0')}") }
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/large.apk", sample.copy(components = components)), false, {}, {}, false, target) } }
        tab(2)
        onNodeWithText("Activity299").assertDoesNotExist()
        assertTrue(onAllNodes(hasText("Activity", substring = true)).fetchSemanticsNodes().size < 30)
        val tag = "apk-component-Activity:com.example.Activity280"
        onNodeWithTag("apk-components-list").performScrollToNode(hasTestTag(tag))
        val before = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        onNodeWithTag(tag).performClick(); back()
        assertEquals(before, onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
        tab(3); tab(2)
        assertEquals(before, onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun replacingApkResetsTabDetailsSearchAndFilters() = runDesktopComposeUiTest(width = 800, height = 600) {
        var state by mutableStateOf(ApkInformationUiState(ApkInformationPhase.Result, "/first.apk", sample))
        setContent { AppTheme(false) { ApkInformationScreen(state, false, {}, {}, false, target) } }
        tab(2); onNodeWithTag("apk-component-filter-Service").performClick(); search().performTextInput("sync")
        onNodeWithTag("apk-component-Service:com.example.app.SyncService").performClick()
        runOnIdle { state = state.copy(inputFile = "/next.apk", result = sample.copy(label = "Next")) }
        onNodeWithTag("apk-tab-0").assertIsSelected()
        tab(2)
        onNodeWithTag("apk-component-detail").assertDoesNotExist()
        search().assertTextEquals("搜索组件名称或进程", "")
        onNodeWithTag("apk-component-filter-All").assertIsSelected()
    }

    @Test fun unavailableAndEmptyAnalysesAreDifferentForEveryList() = runDesktopComposeUiTest(width = 800, height = 600) {
        var result by mutableStateOf(sample.copy(components = null, archive = null))
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", result), false, {}, {}, false, target) } }
        for (page in listOf(2, 3, 0)) {
            tab(page); if (page == 0) onNodeWithTag("apk-open-Package").performClick()
            onNodeWithTag("apk-detail-search-toggle").assertIsNotEnabled(); onNodeWithText("未能解析").assertIsDisplayed()
        }
        runOnIdle { result = sample.copy(components = emptyList(), archive = ApkArchiveInformation(emptyList(), emptyList(), 0)) }
        for (page in listOf(2, 3, 0)) {
            tab(page); if (page == 0) onNodeWithTag("apk-open-Package").performClick()
            onNodeWithTag("apk-detail-search-toggle").assertIsNotEnabled(); onNodeWithText("未发现相关条目").assertIsDisplayed()
        }
    }

    @Test fun rapidTabReversalKeepsLastSelectionAndHeaderStill() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target) } }
        val header = onNodeWithTag("apk-overview").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        try {
            for (page in listOf(2, 3, 2, 1, 4, 1, 2)) {
                onNodeWithTag("apk-tab-$page").performClick()
                mainClock.advanceTimeBy(48)
            }
            mainClock.advanceTimeBy(1500)
            onNodeWithTag("apk-tab-2").assertIsSelected()
            onNodeWithTag("apk-components-list").assertIsDisplayed()
            onNodeWithTag("apk-libraries-list").assertDoesNotExist()
            assertEquals(header, onNodeWithTag("apk-overview").fetchSemanticsNode().boundsInRoot)
        } finally { mainClock.autoAdvance = true }
        search().performTextInput("Main")
        onNodeWithTag("apk-component-Activity:com.example.app.MainActivity").performClick()
        onNodeWithTag("apk-component-detail").assertIsDisplayed()
        mainClock.autoAdvance = false
        try {
            onNodeWithTag("apk-detail-back").performClick()
            mainClock.advanceTimeBy(48)
            onNodeWithTag("apk-tab-3").performClick()
            mainClock.advanceTimeBy(1500)
            onNodeWithTag("apk-libraries-list").assertIsDisplayed()
        } finally { mainClock.autoAdvance = true }
        tab(2); search().assertTextContains("Main")
    }

    @Test fun detailEntryActuallyAnimatesAndThenLeavesOneActivePage() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target) } }
        tab(2)
        mainClock.autoAdvance = false
        try {
            onNodeWithTag("apk-component-Activity:com.example.app.MainActivity").performClick()
            mainClock.advanceTimeBy(32)
            val entering = onNodeWithTag("apk-component-detail").getUnclippedBoundsInRoot().left
            mainClock.advanceTimeBy(1500)
            val settled = onNodeWithTag("apk-component-detail").getUnclippedBoundsInRoot().left
            assertTrue(entering > settled, "Detail should slide into place instead of appearing abruptly")
            onNodeWithTag("apk-components-list").assertDoesNotExist()
            back(); mainClock.advanceTimeBy(1500)
            onNodeWithTag("apk-component-detail").assertDoesNotExist()
            onNodeWithTag("apk-components-list").assertIsDisplayed()
        } finally { mainClock.autoAdvance = true }
    }
}
