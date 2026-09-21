package org.tool.kit.tests.feature.cleaner

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.data.repository.DefaultCleanerRulesRepository
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.cleaner.CleanerCondition
import org.tool.kit.domain.cleaner.CleanerRuleConfig
import org.tool.kit.domain.cleaner.CleanerRuleGroup
import org.tool.kit.domain.cleaner.CleanerScanRequest
import org.tool.kit.domain.cleaner.CleanerTarget
import org.tool.kit.domain.cleaner.ConditionRelation
import org.tool.kit.domain.cleaner.DeleteBuildCacheResult
import org.tool.kit.domain.cleaner.SizeUnit
import org.tool.kit.domain.cleaner.TextOperator
import org.tool.kit.domain.cleaner.defaultBuildRule
import org.tool.kit.domain.repository.BuildCachesRepository
import org.tool.kit.domain.usecase.DeleteBuildCachesUseCase
import org.tool.kit.domain.usecase.ScanBuildCachesUseCase
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.cleaner.CleanerContent
import org.tool.kit.feature.cleaner.CleanerIntent
import org.tool.kit.feature.cleaner.CleanerItemUi
import org.tool.kit.feature.cleaner.CleanerPhase
import org.tool.kit.feature.cleaner.CleanerRulesScreen
import org.tool.kit.feature.cleaner.CleanerRulesUiState
import org.tool.kit.feature.cleaner.CleanerRulesViewModel
import org.tool.kit.feature.cleaner.CleanerRulesWindow
import org.tool.kit.feature.cleaner.CleanerRulesWindowContent
import org.tool.kit.feature.cleaner.CleanerScreen
import org.tool.kit.feature.cleaner.CleanerUiState
import org.tool.kit.feature.cleaner.CleanerViewModel
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.theme.AppTheme
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSettingsApi::class, ExperimentalComposeUiApi::class)
class CleanerRulesUiTest {
    @Test
    fun onlyHeadersToggleSectionsAndExpandedContentKeepsItsOwnActions() =
        runDesktopComposeUiTest(width = 800, height = 1000) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            repo.save(CleanerRuleConfig(rules = listOf(defaultBuildRule())))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                val id = defaultBuildRule().id
                val card = onNodeWithTag("rule-$id")
                val header = onNodeWithTag("rule-header-$id")
                val options = onNodeWithTag("rule-options-$id")
                fun headerClick() = header.performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            24f,
                            centerY
                        )
                    )
                }
                card.assert(hasClickAction().not())
                headerClick(); options.assertIsDisplayed()
                card.assert(hasClickAction().not())
                card.performMouseInput { click(androidx.compose.ui.geometry.Offset(4f, centerY)) }
                options.assertIsDisplayed()
                val enabled = vm.uiState.value.draft.rules.single().enabled
                onNodeWithTag("rule-enabled-$id").performClick()
                assertEquals(!enabled, vm.uiState.value.draft.rules.single().enabled)
                val condition = vm.uiState.value.conditionRows.getValue(id).single().id
                onNodeWithTag("condition-edit-$condition").performClick()
                onNodeWithText("条件值", substring = false).performTextReplacement("updated-build")
                card.performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            12f,
                            height - 28f
                        )
                    )
                }
                options.assertIsDisplayed()
                onNodeWithText("条件值", substring = false).assertTextContains("updated-build")
                options.performClick()
                val optionsBody = onNodeWithTag("rule-options-surface-$id")
                optionsBody.assert(hasClickAction().not())
                optionsBody.performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            width - 4f,
                            height - 20f
                        )
                    )
                }
                onNodeWithText("命中结果默认勾选", substring = false).assertIsDisplayed()
                options.performClick()
                onNodeWithText("命中结果默认勾选", substring = false).assertDoesNotExist()
                headerClick(); options.assertDoesNotExist()
                headerClick(); options.assertIsDisplayed()
                onNodeWithText("条件值", substring = false).assertTextContains("updated-build")
                headerClick()
                val settings = onNodeWithTag("cleaner-scan-settings")
                val settingsHeader = onNodeWithTag("cleaner-scan-settings-header")
                settings.assert(hasClickAction().not())
                settingsHeader.performScrollTo().performClick()
                onNodeWithTag("cleaner-max-depth").performTextReplacement("12")
                settings.performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            4f,
                            centerY
                        )
                    )
                }
                onNodeWithTag("cleaner-max-depth").assertIsDisplayed().assertTextContains("12")
                settingsHeader.performClick()
                onNodeWithTag("cleaner-max-depth").assertDoesNotExist()
            } finally {
                store.clear()
            }
        }

    @Test
    fun wholeRuleChangesAnimateNeighborAndScanSettingsPositions() =
        runDesktopComposeUiTest(width = 800, height = 1200) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            repo.save(
                CleanerRuleConfig(
                    rules = listOf(
                        defaultBuildRule(),
                        defaultBuildRule().copy(
                            id = "second",
                            name = "第二条",
                            conditions = listOf(CleanerCondition.Text(value = "cache"))
                        ),
                        defaultBuildRule().copy(
                            id = "third",
                            name = "第三条",
                            conditions = listOf(CleanerCondition.Text(value = "temp"))
                        )
                    )
                )
            )
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                val settings = onNodeWithTag("cleaner-scan-settings-header", useUnmergedTree = true)
                val third = onNodeWithTag("rule-third")
                fun top(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.top
                fun checkMovedThroughIntermediatePositions(
                    start: Float,
                    samples: List<Float>,
                    downward: Boolean
                ) {
                    val end = samples.last()
                    assertTrue(
                        if (downward) end > start + 2f else end < start - 2f,
                        "$start -> $samples"
                    )
                    assertTrue(
                        samples.any { it > minOf(start, end) + 1f && it < maxOf(start, end) - 1f },
                        "Expected a visible placement transition, not a jump: $start -> $samples"
                    )
                }

                val beforeAdd = top(settings)
                mainClock.autoAdvance = false
                onNodeWithText("添加规则", substring = false).performClick()
                val addedPositions = buildList {
                    repeat(70) { mainClock.advanceTimeByFrame(); add(top(settings)) }
                }
                checkMovedThroughIntermediatePositions(beforeAdd, addedPositions, downward = true)
                val beforeDelete = top(settings)
                val beforeNeighbor = top(third)
                runOnIdle { vm.deleteRule("second") }
                val settingsPositions = mutableListOf<Float>()
                val neighborPositions = mutableListOf<Float>()
                repeat(40) {
                    mainClock.advanceTimeByFrame()
                    settingsPositions.add(top(settings))
                    neighborPositions.add(top(third))
                }
                checkMovedThroughIntermediatePositions(
                    beforeDelete,
                    settingsPositions,
                    downward = false
                )
                checkMovedThroughIntermediatePositions(
                    beforeNeighbor,
                    neighborPositions,
                    downward = false
                )
            } finally {
                mainClock.autoAdvance = true; store.clear()
            }
        }

    @Test
    fun connectedChoicesKeepBoundsStableThroughCheckmarkExit() =
        runDesktopComposeUiTest(width = 800, height = 800) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            repo.save(
                CleanerRuleConfig(
                    rules = listOf(
                        defaultBuildRule().copy(
                            target = CleanerTarget.FILE,
                            conditions = listOf(
                                CleanerCondition.Text(value = "build"),
                                CleanerCondition.Text(value = "cache")
                            )
                        )
                    )
                )
            )
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onNodeWithContentDescription("展开规则").performClick()
                val ruleId = defaultBuildRule().id
                for ((tag, choices) in listOf(
                    "rule-target-$ruleId" to listOf("文件夹", "文件"),
                    "rule-relation-$ruleId" to listOf("满足任意条件", "满足全部条件")
                )) {
                    val group = onNodeWithTag(tag, useUnmergedTree = true)
                    val initial = group.fetchSemanticsNode().boundsInRoot
                    mainClock.autoAdvance = false
                    for (choice in choices) {
                        onNode(
                            hasText(
                                choice,
                                substring = false
                            ) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true
                        ).performClick()
                        val label = onNode(
                            hasText(choice, substring = false) and hasAnyAncestor(
                                hasTestTag(tag)
                            ), useUnmergedTree = true
                        )
                        val labelLefts = mutableListOf<Float>()
                        val widths = buildList {
                            repeat(60) {
                                mainClock.advanceTimeByFrame()
                                add(group.fetchSemanticsNode().boundsInRoot.width)
                                labelLefts.add(label.fetchSemanticsNode().boundsInRoot.left)
                            }
                        }
                        val jumps = widths.zipWithNext().map { (a, b) -> kotlin.math.abs(b - a) }
                        assertTrue(
                            labelLefts.drop(20).zipWithNext()
                                .all { (a, b) -> kotlin.math.abs(b - a) <= 2f },
                            "$choice label jumps near the end of its animation: $labelLefts"
                        )
                        assertTrue(
                            widths.all { kotlin.math.abs(it - initial.width) <= 1f },
                            "$choice widths changed during checkmark animation; largest frame jump=${jumps.maxOrNull()}: $widths"
                        )
                    }
                    mainClock.autoAdvance = true
                }
            } finally {
                mainClock.autoAdvance = true; store.clear()
            }
        }

    @Test
    fun inputMethodSeesChineseCompositionAndCursorBeforeTheNextFrame() =
        runDesktopComposeUiTest(width = 800, height = 900) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            var input: PlatformTextInputMethodRequest? = null
            var heldDraft by mutableStateOf<CleanerRulesUiState?>(null)
            try {
                runOnIdle { vm.open() }
                setContent {
                    InterceptPlatformTextInput({ request, _ ->
                        input = request; awaitCancellation()
                    }) {
                        AppTheme(false) {
                            val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                            heldDraft ?: state,
                            vm,
                            {})
                        }
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onNodeWithContentDescription("展开规则").performClick()
                val ruleId = defaultBuildRule().id
                val rowId = vm.uiState.value.conditionRows.getValue(ruleId).single().id
                onNodeWithTag("condition-edit-$rowId").performClick()
                onNodeWithTag("rule-rename-$ruleId").performClick()
                for (label in listOf("规则名称", "条件值")) {
                    onNodeWithText(label, substring = false).performTextClearance()
                    // Hold the page snapshot for one render to reproduce a delayed StateFlow echo.
                    runOnIdle { heldDraft = vm.uiState.value }
                    runOnIdle {
                        val request = checkNotNull(input)
                        // A desktop IME may read the cursor and composing range between commands,
                        // before Compose has rendered another frame or collected the draft flow.
                        for (pinyin in listOf("h", "hu", "huan")) {
                            request.onEditCommand(listOf(SetComposingTextCommand(pinyin, 1)))
                            assertEquals(pinyin, request.state.text)
                            assertEquals(TextRange(pinyin.length), request.state.selection)
                            assertEquals(TextRange(0, pinyin.length), request.state.composition)
                        }
                    }
                    waitForIdle()
                    runOnIdle {
                        val request = checkNotNull(input)
                        assertEquals("huan", request.state.text)
                        assertEquals(TextRange(4), request.state.selection)
                        assertEquals(TextRange(0, 4), request.state.composition)
                        // An intermediate echo must not overwrite characters entered after it.
                        heldDraft = vm.uiState.value
                        request.onEditCommand(listOf(SetComposingTextCommand("huancun", 1)))
                    }
                    waitForIdle()
                    runOnIdle {
                        val request = checkNotNull(input)
                        assertEquals("huancun", request.state.text)
                        assertEquals(TextRange(7), request.state.selection)
                        assertEquals(TextRange(0, 7), request.state.composition)
                        heldDraft = null
                    }
                    waitForIdle()
                    runOnIdle {
                        val request = checkNotNull(input)
                        assertEquals(TextRange(0, 7), request.state.composition)
                        request.onEditCommand(listOf(CommitTextCommand("缓存", 1)))
                        assertEquals("缓存", request.state.text)
                        assertEquals(TextRange(2), request.state.selection)
                        assertNull(request.state.composition)
                        request.onEditCommand(
                            listOf(
                                SetSelectionCommand(1, 1),
                                CommitTextCommand("临时", 1)
                            )
                        )
                        assertEquals("缓临时存", request.state.text)
                        assertEquals(TextRange(3), request.state.selection)
                        request.onEditCommand(
                            listOf(
                                SetSelectionCommand(0, 4),
                                CommitTextCommand("中文缓存", 1)
                            )
                        )
                        assertEquals("中文缓存", request.state.text)
                        assertEquals(TextRange(4), request.state.selection)
                    }
                    waitForIdle()
                }
                assertEquals("中文缓存", vm.uiState.value.draft.rules.single().name)
                assertEquals(
                    "中文缓存",
                    (vm.uiState.value.draft.rules.single().conditions.single() as CleanerCondition.Text).value
                )
                assertEquals(
                    "build",
                    (repo.state.value.config.rules.single().conditions.single() as CleanerCondition.Text).value
                )
            } finally {
                store.clear()
            }
        }

    @Test
    fun numericInputsKeepSelectionAndRestoreUpdatesTheVisibleBuffer() =
        runDesktopComposeUiTest(width = 800, height = 1000) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            repo.save(
                CleanerRuleConfig(
                    rules = listOf(
                        defaultBuildRule().copy(
                            target = CleanerTarget.FILE,
                            conditions = listOf(
                                CleanerCondition.FileSizeGreaterThan(
                                    1024,
                                    "1",
                                    SizeUnit.KB
                                )
                            )
                        )
                    )
                )
            )
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            var input: PlatformTextInputMethodRequest? = null
            try {
                runOnIdle { vm.open() }
                setContent {
                    InterceptPlatformTextInput({ request, _ ->
                        input = request; awaitCancellation()
                    }) {
                        AppTheme(false) {
                            val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                            state,
                            vm,
                            {})
                        }
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onNodeWithContentDescription("展开规则").performClick()
                val rowId =
                    vm.uiState.value.conditionRows.getValue(defaultBuildRule().id).single().id
                onNodeWithTag("condition-edit-$rowId").performClick()
                onNodeWithText("超过", substring = false).performTextClearance()
                runOnIdle {
                    val request = checkNotNull(input)
                    for (digit in "128") request.onEditCommand(
                        listOf(
                            CommitTextCommand(
                                digit.toString(),
                                1
                            )
                        )
                    )
                    assertEquals("128", request.state.text)
                    assertEquals(TextRange(3), request.state.selection)
                    request.onEditCommand(
                        listOf(
                            SetSelectionCommand(1, 2),
                            CommitTextCommand("0", 1)
                        )
                    )
                    assertEquals("108", request.state.text)
                    assertEquals(TextRange(2), request.state.selection)
                }
                waitForIdle()
                onNodeWithText("KB", substring = false).performClick()
                onNodeWithText("MB", substring = false).performClick()
                val size =
                    vm.uiState.value.draft.rules.single().conditions.single() as CleanerCondition.FileSizeGreaterThan
                assertEquals("108", size.displayValue)
                assertEquals(108L * 1024 * 1024, size.bytes)
                onNodeWithTag(
                    "cleaner-scan-settings-header",
                    useUnmergedTree = true
                ).performScrollTo().performClick()
                onNodeWithTag("cleaner-max-depth").performScrollTo().performTextReplacement("42")
                onNodeWithTag("cleaner-max-depth").performTextInputSelection(TextRange(1, 2))
                onNodeWithTag("cleaner-max-depth").performTextInput("0")
                waitForIdle()
                assertEquals("40", vm.uiState.value.depthText)
                runOnIdle { assertEquals(TextRange(2), checkNotNull(input).state.selection) }
                onNodeWithTag("cleaner-rules-options").performClick()
                onNodeWithText("恢复默认", substring = false).performClick()
                onAllNodesWithText("恢复默认", substring = false).onLast().performClick()
                onNodeWithTag("cleaner-max-depth").performScrollTo().assertTextContains("10")
                assertEquals(defaultBuildRule(), vm.uiState.value.draft.rules.single())
                assertEquals("10", vm.uiState.value.depthText)
                onNodeWithText("保存", substring = false).performClick()
                waitUntil { vm.uiState.value.saved }
                assertEquals(10, repo.state.value.config.maxDepth)
                assertEquals(defaultBuildRule(), repo.state.value.config.rules.single())
            } finally {
                store.clear()
            }
        }

    @Test
    fun conditionEditorHeightAnimatesInBothDirectionsWithoutLosingInput() =
        runDesktopComposeUiTest(width = 800, height = 800) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onNodeWithContentDescription("展开规则").performClick()
                val id = vm.uiState.value.conditionRows.getValue(defaultBuildRule().id).single().id
                val row = onNodeWithTag("condition-$id", useUnmergedTree = true)
                val collapsedHeight = row.fetchSemanticsNode().size.height
                mainClock.autoAdvance = false
                onNodeWithTag("condition-edit-$id").performClick()
                mainClock.advanceTimeBy(112)
                val openingHeight = row.fetchSemanticsNode().size.height
                mainClock.advanceTimeBy(400)
                val expandedHeight = row.fetchSemanticsNode().size.height
                assertTrue(openingHeight > collapsedHeight && openingHeight < expandedHeight)
                onNodeWithText("条件值", substring = false).performTextReplacement("retained-cache")
                mainClock.advanceTimeByFrame()
                onNodeWithContentDescription("完成编辑").performClick()
                mainClock.advanceTimeBy(112)
                val closingHeight = row.fetchSemanticsNode().size.height
                assertTrue(closingHeight > collapsedHeight && closingHeight < expandedHeight)
                mainClock.advanceTimeBy(400)
                assertEquals(collapsedHeight, row.fetchSemanticsNode().size.height)
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
                assertEquals(
                    "retained-cache",
                    (vm.uiState.value.draft.rules.single().conditions.single() as CleanerCondition.Text).value
                )
            } finally {
                mainClock.autoAdvance = true; store.clear()
            }
        }

    @Test
    fun allExistingRulesStartCollapsedAndEditingFollowsOnlyTheOpenRule() =
        runDesktopComposeUiTest(width = 800, height = 800) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            repo.save(
                CleanerRuleConfig(
                    rules = listOf(
                        defaultBuildRule(),
                        CleanerRuleGroup(
                            "disabled",
                            "临时文件",
                            enabled = false,
                            target = CleanerTarget.FILE,
                            conditions = listOf(
                                CleanerCondition.Text(
                                    operator = TextOperator.ENDS_WITH,
                                    value = ".tmp"
                                )
                            )
                        )
                    )
                )
            )
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onAllNodesWithContentDescription("展开规则").assertCountEquals(2)
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
                onNodeWithTag("rule-enabled-disabled").performClick()
                onAllNodesWithContentDescription("展开规则").assertCountEquals(2)
                val originalId = defaultBuildRule().id
                onNode(hasContentDescription("展开规则") and hasAnyAncestor(hasTestTag("rule-$originalId"))).performClick()
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
                val conditionId = vm.uiState.value.conditionRows.getValue(originalId).single().id
                onNodeWithTag("condition-edit-$conditionId").performClick()
                onNodeWithText("条件值", substring = false).performTextReplacement("edited-build")
                onNode(hasContentDescription("展开规则") and hasAnyAncestor(hasTestTag("rule-disabled"))).performScrollTo()
                    .performClick()
                onAllNodesWithContentDescription("折叠规则").assertCountEquals(1)
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
                assertEquals(
                    "edited-build",
                    (vm.uiState.value.draft.rules.first().conditions.single() as CleanerCondition.Text).value
                )
                assertEquals(
                    "build",
                    (repo.state.value.config.rules.first().conditions.single() as CleanerCondition.Text).value
                )
                onNodeWithText("添加规则", substring = false).performClick()
                val added = vm.uiState.value.draft.rules.last()
                onAllNodesWithContentDescription("折叠规则").assertCountEquals(1)
                onNodeWithText("条件值", substring = false).performScrollTo()
                    .performTextReplacement("cache")
                onNode(hasContentDescription("规则操作") and hasAnyAncestor(hasTestTag("rule-${added.id}"))).performScrollTo()
                    .performClick()
                onNodeWithText("复制规则", substring = false).performClick()
                val copied = vm.uiState.value.draft.rules.last()
                assertNotEquals(added.id, copied.id)
                onNode(hasContentDescription("折叠规则") and hasAnyAncestor(hasTestTag("rule-${copied.id}"))).assertExists()
                onAllNodesWithContentDescription("折叠规则").assertCountEquals(1)
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
            } finally {
                store.clear()
            }
        }

    @Test
    fun materialEditorFiltersSizeFieldsAndConfirmsDestructiveTargetChanges() =
        runDesktopComposeUiTest(width = 1000, height = 900) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                onNodeWithText("规则名称").assertDoesNotExist()
                onNodeWithText("清理规则", substring = false).assertDoesNotExist()
                onNodeWithContentDescription("取消", substring = false).assertDoesNotExist()
                onNodeWithText("添加规则", substring = false).assertIsDisplayed()
                onNodeWithContentDescription("展开规则").performClick()
                onNodeWithText("添加条件").performClick()
                onAllNodesWithText("文件大小").assertCountEquals(0)
                onNodeWithText("相对路径").performClick()
                assertTrue(vm.uiState.value.draft.rules.single().conditions.last() is CleanerCondition.Text)
                runOnIdle { vm.restoreDefault() }
                onNodeWithText("文件", substring = false).performClick()
                onNodeWithText("添加条件").performClick()
                onNodeWithText("文件大小", substring = false).performClick()
                onNodeWithText("保存", substring = false).assertIsNotEnabled()
                onNodeWithText("超过", substring = false).performTextInput("100")
                onNodeWithText("保存", substring = false).assertIsEnabled()
                onNodeWithText("满足任意条件", substring = false).performClick()
                onNodeWithText("文件夹", substring = false).performClick()
                onNodeWithText("切换命中对象").assertExists()
                onAllNodesWithText("取消", substring = false).onLast().performClick()
                assertEquals(CleanerTarget.FILE, vm.uiState.value.draft.rules.single().target)
                assertTrue(vm.uiState.value.draft.rules.single().conditions.any { it is CleanerCondition.FileSizeGreaterThan })
                onNodeWithText("文件夹", substring = false).performClick()
                onNodeWithText("继续", substring = false).performClick()
                assertEquals(CleanerTarget.DIRECTORY, vm.uiState.value.draft.rules.single().target)
                assertFalse(vm.uiState.value.draft.rules.single().conditions.any { it is CleanerCondition.FileSizeGreaterThan })
                assertEquals(ConditionRelation.ANY, vm.uiState.value.draft.rules.single().relation)
                assertEquals(CleanerRuleConfig(), repo.state.value.config)
            } finally {
                store.clear()
            }
        }

    @Test
    fun restoreOnlyChangesDraftAndSaveTryRunPublishesSuccess() =
        runDesktopComposeUiTest(width = 1000, height = 900) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                runOnIdle { vm.updateRule(defaultBuildRule().copy(name = "Changed")) }
                onNodeWithTag("cleaner-rules-options").performClick()
                onNodeWithText("恢复默认", substring = false).performClick()
                onAllNodesWithText("恢复默认", substring = false).onLast().performClick()
                assertEquals(0, repo.state.value.config.revision)
                assertEquals(defaultBuildRule(), vm.uiState.value.draft.rules.single())
                onNodeWithText("保存并试运行", substring = false).performClick()
                waitUntil { vm.uiState.value.saved }
                assertTrue(vm.uiState.value.tryRun); assertEquals(
                    1,
                    repo.state.value.config.revision
                )
            } finally {
                store.clear()
            }
        }

    @Test
    fun resultExplainsBothTypesAndDeletionShowsFrozenCounts() =
        runDesktopComposeUiTest(width = 1000, height = 700) {
            val log = CleanerItemUi(
                "file",
                "/root/a.log",
                "a.log",
                1024,
                0,
                false,
                true,
                matchedRuleNames = listOf("大型日志", "临时文件")
            )
            val folder = CleanerItemUi(
                "folder",
                "/root/build",
                "build",
                2048,
                0,
                true,
                true,
                matchedRuleNames = listOf("Android 构建缓存"),
                deleteFailed = true,
                safetyFailure = true
            )
            var state by mutableStateOf(
                CleanerUiState(
                    scanRoot = "/root",
                    items = listOf(log, folder)
                )
            )
            setContent {
                AppTheme(false) {
                    CleanerScreen(
                        state,
                        {
                            if (it == CleanerIntent.RequestDelete) state = state.copy(
                                deleteConfirmVisible = true,
                                confirmationItems = state.items
                            )
                        },
                        {},
                        {})
                }
            }
            onNodeWithContentDescription("文件", substring = false).assertExists()
            onNodeWithContentDescription("文件夹", substring = false).assertExists()
            onNodeWithText("命中：大型日志、临时文件").assertExists()
            onNodeWithText("路径已变化，未删除").assertExists()
            onNodeWithText("1 个文件 · 1 个文件夹").assertExists()
            onNodeWithTag("cleaner-delete-selected").performClick()
            onNodeWithText("将永久删除 1 个文件和 1 个文件夹", substring = true).assertExists()
            onNodeWithText("扫描根目录：/root", substring = true).assertExists()
        }

    @Test
    fun scanningCountsDiscoveriesSeparatelyFromSelectionAndAllowsCancellation() =
        runDesktopComposeUiTest(width = 800, height = 572) {
            val item = CleanerItemUi(
                "file",
                "/root/a.log",
                "a.log",
                1024,
                0,
                false,
                true,
                checked = false,
                matchedRuleNames = listOf("Logs")
            )
            val intents = mutableListOf<CleanerIntent>()
            setContent {
                AppTheme(false) {
                    CleanerScreen(
                        CleanerUiState(
                            phase = CleanerPhase.Scanning,
                            items = listOf(item)
                        ), intents::add, {}, {})
                }
            }
            onNodeWithText("正在扫描，已发现 1 个项目").assertExists()
            onNodeWithText("0 个文件 · 0 个文件夹").assertExists()
            onNodeWithText("管理规则").assertDoesNotExist()
            onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
            onNodeWithText("取消扫描").performClick()
            assertEquals(CleanerIntent.CloseSelection, intents.single())
        }

    @Test
    fun saveTryRunReturnsToCleanerRequestsPickerAndScansWithCommittedRules() =
        runDesktopComposeUiTest(width = 800, height = 572) {
            val rules =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val requests = mutableListOf<CleanerScanRequest>()
            val repo = object : BuildCachesRepository {
                override fun scan(root: String): Flow<BuildDirectory> = error("Snapshot required")
                override fun scan(
                    request: CleanerScanRequest,
                    onIssue: (String) -> Unit
                ): Flow<BuildDirectory> {
                    requests += request
                    return flowOf(
                        BuildDirectory(
                            request.root,
                            "${request.root}/build",
                            "build",
                            10,
                            0,
                            true,
                            true
                        )
                    )
                }

                override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult =
                    error("Unexpected deletion")
            }
            val effects = AppEffectSink()
            val cleaner = CleanerViewModel(
                ScanBuildCachesUseCase(repo),
                DeleteBuildCachesUseCase(repo),
                AllPathsExist,
                effects,
                rules = rules
            )
            val editor = CleanerRulesViewModel(rules)
            val store =
                ViewModelStore().also { it.put("cleaner", cleaner); it.put("rules", editor) }
            var pickerRequests = 0
            try {
                setContent {
                    AppTheme(false) {
                        CleanerContent(cleaner, editor, false, {
                            pickerRequests++
                            cleaner.onIntent(CleanerIntent.Rescan("chosen-root"))
                        }, {}, rulesWindow = { state, dismiss, saved, _ ->
                            CleanerRulesWindowContent(state, editor, dismiss, saved)
                        })
                    }
                }
                waitUntil { cleaner.uiState.value.rulesReady }
                onNodeWithText("管理规则").performClick()
                waitUntil { !editor.uiState.value.loading }
                onNodeWithText("保存并试运行").assertIsDisplayed().performClick()
                waitUntil { requests.isNotEmpty() }
                onNodeWithTag("cleaner-rules-window-content").assertDoesNotExist()
                assertEquals(1, pickerRequests)
                assertEquals("chosen-root", requests.single().root)
                assertEquals(1, requests.single().configRevision)
                waitUntil { cleaner.uiState.value.items.size == 1 }
                onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
                onNodeWithTag("cleaner-toolbar-rules").assertExists()
                onNodeWithText("build", substring = false).assertExists()
            } finally {
                store.clear(); effects.close()
            }
        }

    @Test
    fun toolbarWindowKeepsResultsAndCancelDiscardsDraft() =
        runDesktopComposeUiTest(width = 800, height = 650) {
            val rules =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val repo = object : BuildCachesRepository {
                override fun scan(root: String) =
                    flowOf(BuildDirectory(root, "$root/build", "build", 10, 0, true, true))

                override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult =
                    error("Unexpected deletion")
            }
            val effects = AppEffectSink()
            val cleaner = CleanerViewModel(
                ScanBuildCachesUseCase(repo),
                DeleteBuildCachesUseCase(repo),
                AllPathsExist,
                effects,
                rules = rules
            )
            val editor = CleanerRulesViewModel(rules)
            val store =
                ViewModelStore().also { it.put("cleaner", cleaner); it.put("editor", editor) }
            try {
                setContent {
                    AppTheme(false) {
                        CleanerContent(
                            cleaner, editor, false, {}, {},
                            rulesWindow = { state, dismiss, saved, _ ->
                                CleanerRulesWindowContent(
                                    state,
                                    editor,
                                    dismiss,
                                    saved
                                )
                            })
                    }
                }
                waitUntil { cleaner.uiState.value.rulesReady }
                runOnIdle { cleaner.onIntent(CleanerIntent.Rescan("root")) }
                waitUntil { cleaner.uiState.value.items.isNotEmpty() }
                onNodeWithTag("cleaner-toolbar-rules").assertIsDisplayed().performClick()
                onNodeWithTag("cleaner-rules-window-content").assertExists()
                val contentBounds =
                    onNodeWithTag("cleaner-rules-window-content").fetchSemanticsNode().boundsInRoot
                assertTrue(
                    kotlin.math.abs(contentBounds.bottom - 650f) <= 1f,
                    "Editor bottom: $contentBounds"
                )
                assertEquals(0f, contentBounds.top, 1f)
                assertEquals(0f, contentBounds.left, 1f)
                assertEquals(800f, contentBounds.right, 1f)
                // The main result remains composed while the editor is open; selection and scan state are retained.
                onNode(hasText("build", substring = false) and !hasSetTextAction()).assertExists()
                onNodeWithContentDescription("展开规则").performClick()
                onNodeWithTag("rule-rename-${defaultBuildRule().id}").performClick()
                onNodeWithText("规则名称").performTextReplacement("Unsaved name")
                // Reopening the existing window must preserve its draft.
                onNodeWithTag("cleaner-toolbar-rules").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                onNodeWithText("规则名称").assertTextContains("Unsaved name")
                onNodeWithText("取消", substring = false).performClick()
                waitUntil {
                    onAllNodesWithTag("cleaner-rules-window-content").fetchSemanticsNodes()
                        .isEmpty()
                }
                assertEquals(1, cleaner.uiState.value.items.size)
                assertEquals(0, rules.state.value.config.revision)
                onNodeWithTag("cleaner-toolbar-rules").performClick()
                waitUntil { !editor.uiState.value.loading }
                assertEquals(
                    defaultBuildRule().name,
                    editor.uiState.value.draft.rules.single().name
                )
            } finally {
                store.clear(); effects.close()
            }
        }

    @Test
    fun collapsibleSectionsKeepEditsAndSavingBlocksWindowDismissal() =
        runDesktopComposeUiTest(width = 850, height = 850) {
            val gate = CompletableDeferred<Unit>()
            val base = MapSettings().toFlowSettings(Dispatchers.Unconfined)
            val rules = DefaultCleanerRulesRepository(object : FlowSettings by base {
                override suspend fun putString(key: String, value: String) {
                    gate.await(); base.putString(key, value)
                }
            })
            val editor = CleanerRulesViewModel(rules)
            val store = ViewModelStore().also { it.put("editor", editor) }
            var dismissed = false
            var saved = false
            try {
                runOnIdle { editor.open() }
                setContent {
                    AppTheme(false) {
                        val state by editor.uiState.collectAsState()
                        CleanerRulesWindowContent(
                            state,
                            editor,
                            { dismissed = true },
                            { saved = true })
                    }
                }
                waitUntil { !editor.uiState.value.loading }
                val ruleId = defaultBuildRule().id
                // Functional buttons consume their own clicks without toggling the surrounding card.
                onNodeWithTag("rule-enabled-$ruleId").performClick()
                onNodeWithText("规则名称").assertDoesNotExist()
                onNodeWithTag("rule-enabled-$ruleId").performClick()
                onNodeWithContentDescription("规则操作").performClick()
                onNodeWithText("规则名称").assertDoesNotExist()
                onNodeWithText("上移", substring = false).assertExists()
                // Close the menu by clicking outside it.
                onNodeWithTag("cleaner-rules-window-content").performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            4f,
                            4f
                        )
                    )
                }
                // Card padding is inert; only the header and arrow advertise expansion.
                onNodeWithTag("rule-$ruleId").performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            8f,
                            height - 8f
                        )
                    )
                }
                onNodeWithTag("rule-rename-$ruleId").assertDoesNotExist()
                onNodeWithTag("rule-header-$ruleId").performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            24f,
                            centerY
                        )
                    )
                }
                onNodeWithTag("rule-rename-$ruleId").performClick()
                onNodeWithText("规则名称").performTextReplacement("Retained name")
                onNodeWithTag("rule-header-$ruleId").performMouseInput {
                    click(
                        androidx.compose.ui.geometry.Offset(
                            24f,
                            centerY
                        )
                    )
                }
                onNodeWithText("规则名称").assertTextContains("Retained name")
                onNodeWithContentDescription("完成编辑").performClick()
                onNodeWithTag(
                    "rule-header-$ruleId",
                    useUnmergedTree = true
                ).performMouseInput { click(androidx.compose.ui.geometry.Offset(24f, centerY)) }
                onNodeWithText("规则名称").assertDoesNotExist()
                onNodeWithContentDescription("展开规则").performClick()
                onNodeWithText("Retained name").assertExists()
                onNodeWithText("其他选项").performScrollTo().performClick()
                onNodeWithText("命中结果默认勾选", substring = false).assertExists()
                onNodeWithText("其他选项").performClick()
                onNodeWithText("命中结果默认勾选", substring = false).assertDoesNotExist()
                onNodeWithTag(
                    "cleaner-scan-settings-header",
                    useUnmergedTree = true
                ).performScrollTo().performClick()
                onNodeWithTag("cleaner-max-depth").performScrollTo().performTextReplacement("12")
                onNodeWithTag("cleaner-scan-settings-toggle").performClick()
                onNodeWithText("最大扫描深度", substring = false).assertDoesNotExist()
                onNodeWithText("保存", substring = false).performClick()
                waitUntil { editor.uiState.value.saving }
                onNodeWithText("取消", substring = false).assertIsNotEnabled()
                onNodeWithContentDescription("取消", substring = false).assertDoesNotExist()
                onNodeWithTag("cleaner-rules-window-content").assertExists()
                assertFalse(dismissed)
                runOnIdle { gate.complete(Unit) }
                waitUntil { saved }
                assertEquals(12, rules.state.value.config.maxDepth)
            } finally {
                gate.complete(Unit); store.clear()
            }
        }

    @Test
    fun summaryMovesSmoothlyThroughFinalCollapseFrames() =
        runDesktopComposeUiTest(width = 1000, height = 1050) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                for (target in CleanerTarget.entries) {
                    runOnIdle { vm.updateRule(defaultBuildRule().copy(target = target)) }
                    val summary = onNodeWithTag(
                        "rule-summary-${defaultBuildRule().id}",
                        useUnmergedTree = true
                    )
                    val restingTop = summary.fetchSemanticsNode().boundsInRoot.top
                    onNodeWithContentDescription("展开规则").performClick()
                    waitForIdle()
                    mainClock.autoAdvance = false
                    onNodeWithContentDescription("折叠规则").performClick()
                    val positions = buildList {
                        repeat(80) {
                            mainClock.advanceTimeByFrame()
                            add(summary.fetchSemanticsNode().boundsInRoot.top)
                        }
                    }
                    val tail = positions.zipWithNext()
                        .filter { (before, _) -> kotlin.math.abs(before - restingTop) <= 16f }
                    assertTrue(tail.isNotEmpty())
                    assertTrue(
                        tail.all { (before, after) -> kotlin.math.abs(after - before) <= 4f },
                        "$target summary jumps near rest ($restingTop): $tail"
                    )
                    assertEquals(restingTop, positions.last(), 1f)
                    mainClock.autoAdvance = true
                }
            } finally {
                mainClock.autoAdvance = true; store.clear()
            }
        }

    @Test
    fun conditionRemovalAnimatesAndKeepsRemainingRowEditsInPlace() =
        runDesktopComposeUiTest(width = 1000, height = 1050) {
            val repo =
                DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
            val vm = CleanerRulesViewModel(repo)
            val store = ViewModelStore().also { it.put("rules", vm) }
            try {
                runOnIdle { vm.open() }
                setContent {
                    AppTheme(false) {
                        val state by vm.uiState.collectAsState(); CleanerRulesScreen(
                        state,
                        vm,
                        {})
                    }
                }
                waitUntil { !vm.uiState.value.loading }
                runOnIdle {
                    vm.updateRule(
                        defaultBuildRule().copy(
                            conditions = listOf(
                                CleanerCondition.Text(value = "one"),
                                CleanerCondition.Text(value = "two"),
                                CleanerCondition.Text(value = "three")
                            )
                        )
                    )
                }
                onNodeWithContentDescription("展开规则").performClick()
                val ruleId = defaultBuildRule().id
                val rows = vm.uiState.value.conditionRows.getValue(ruleId)
                waitForIdle()
                mainClock.autoAdvance = false
                onNode(
                    hasContentDescription("删除条件") and hasAnyAncestor(hasTestTag("condition-${rows[1].id}")),
                    useUnmergedTree = true
                ).performClick()
                mainClock.advanceTimeBy(32)
                onNodeWithTag("condition-${rows[1].id}", useUnmergedTree = true).assertExists()
                assertEquals(
                    listOf("one", "three"),
                    vm.uiState.value.draft.rules.single().conditions.map { (it as CleanerCondition.Text).value })
                mainClock.advanceTimeBy(1000)
                mainClock.autoAdvance = true
                onNodeWithTag(
                    "condition-${rows[1].id}",
                    useUnmergedTree = true
                ).assertDoesNotExist()
                onNodeWithTag("condition-edit-${rows[2].id}").performClick()
                onNode(
                    hasSetTextAction() and hasAnyAncestor(hasTestTag("condition-${rows[2].id}")),
                    useUnmergedTree = true
                ).performTextReplacement("edited")
                assertEquals(
                    listOf("one", "edited"),
                    vm.uiState.value.draft.rules.single().conditions.map { (it as CleanerCondition.Text).value })
                onNodeWithText("添加条件").performScrollTo().performClick()
                onNodeWithText("相对路径", substring = false).performClick()
                val added = vm.uiState.value.conditionRows.getValue(ruleId).last()
                onNodeWithTag("condition-${added.id}", useUnmergedTree = true).assertExists()
                assertEquals(rows[2].id, vm.uiState.value.conditionRows.getValue(ruleId)[1].id)
            } finally {
                mainClock.autoAdvance = true; store.clear()
            }
        }

    @Test
    fun idleStepsAndOverviewReturnAfterSelectionCloses() =
        runDesktopComposeUiTest(width = 800, height = 650) {
            var state by mutableStateOf(CleanerUiState())
            setContent { AppTheme(false) { CleanerScreen(state, {}, {}, {}) } }
            onNodeWithTag("cleaner-welcome-steps").assertExists()
            onNodeWithTag("cleaner-rules-overview").assertExists()
            runOnIdle { state = state.copy(phase = CleanerPhase.Scanning, scanIssueCount = 1) }
            onNodeWithTag("cleaner-welcome-steps").assertDoesNotExist()
            onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
            runOnIdle {
                state = state.copy(
                    phase = CleanerPhase.Idle,
                    items = listOf(
                        CleanerItemUi(
                            "build",
                            "/root/build",
                            "build",
                            100,
                            0,
                            true,
                            true
                        )
                    )
                )
            }
            onNodeWithTag("cleaner-rules-overview").assertDoesNotExist()
            onNodeWithTag("cleaner-toolbar-rules").assertExists()
            runOnIdle { state = CleanerUiState() }
            onNodeWithTag("cleaner-welcome-steps").assertExists()
            onNodeWithTag("cleaner-rules-overview").assertExists()
        }

    @Test
    fun nativeWindowCanBeResizedAndTitleBarCloseRespectsSaving() = runDesktopComposeUiTest(
        width = 800, height = 650, testTimeout = kotlin.time.Duration.parse("30s"),
    ) {
        val repo =
            DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        val editor = CleanerRulesViewModel(repo)
        val store = ViewModelStore().also { it.put("editor", editor) }
        var visible by mutableStateOf(true)
        var saving by mutableStateOf(true)
        var native: java.awt.Frame? = null
        try {
            setContent {
                if (visible) CleanerRulesWindow(
                    CleanerRulesUiState(loading = false, saving = saving), editor, false,
                    onDismiss = { visible = false }, onSaved = {},
                )
            }
            waitUntil(timeoutMillis = 10000) {
                java.awt.EventQueue.invokeAndWait {
                    native = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                        .firstOrNull { it.title == "清理规则" && it.isDisplayable }
                }
                native?.isShowing == true && native?.size == java.awt.Dimension(800, 600)
            }
            val frame = assertNotNull(native)
            java.awt.EventQueue.invokeAndWait {
                assertTrue(frame.isResizable)
                assertEquals(java.awt.Frame.NORMAL, frame.extendedState)
                assertEquals(java.awt.Dimension(720, 520), frame.minimumSize)
                frame.dispatchEvent(
                    java.awt.event.WindowEvent(
                        frame,
                        java.awt.event.WindowEvent.WINDOW_CLOSING
                    )
                )
            }
            assertTrue(visible)
            runOnIdle { saving = false }
            waitForIdle()
            java.awt.EventQueue.invokeAndWait {
                frame.dispatchEvent(
                    java.awt.event.WindowEvent(
                        frame,
                        java.awt.event.WindowEvent.WINDOW_CLOSING
                    )
                )
            }
            waitUntil { !visible }
            waitUntil { !frame.isDisplayable }
        } finally {
            runOnIdle { visible = false }
            store.clear()
        }
    }

    @Test
    fun captureMaterialRulesEditorLightAndDark() = captureEditor(false)

    @Test
    fun captureEnglishMaterialRulesEditor() = captureEditor(true)

    private fun captureEditor(english: Boolean) {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE)
            runDesktopComposeUiTest(width = 800, height = 600) {
                val repo =
                    DefaultCleanerRulesRepository(MapSettings().toFlowSettings(Dispatchers.Unconfined))
                val vm = CleanerRulesViewModel(repo)
                val store = ViewModelStore().also { it.put("rules", vm) }
                var dark by mutableStateOf(false)
                try {
                    runOnIdle { vm.open() }
                    setContent {
                        AppTheme(dark) {
                            val state by vm.uiState.collectAsState()
                            CleanerRulesWindowContent(state, vm, {}, {})
                        }
                    }
                    waitUntil { !vm.uiState.value.loading }
                    runOnIdle {
                        vm.edit {
                            it.copy(
                                rules = it.rules + CleanerRuleGroup(
                                    "logs",
                                    if (english) "Large logs" else "大型日志",
                                    target = CleanerTarget.FILE,
                                    enabled = false,
                                    conditions = listOf(
                                        CleanerCondition.Text(
                                            operator = TextOperator.ENDS_WITH,
                                            value = ".log"
                                        ),
                                        CleanerCondition.FileSizeGreaterThan(
                                            104857600,
                                            "100",
                                            SizeUnit.MB
                                        )
                                    )
                                )
                            )
                        }
                    }
                    waitForIdle()
                    onNodeWithContentDescription(if (english) "Collapse rule" else "折叠规则").performClick()
                    onNodeWithTag("cleaner-rule-list").performScrollToIndex(0)
                    // Show the compact overview at the native window's default content size.
                    onAllNodesWithContentDescription(if (english) "Expand rule" else "展开规则").assertCountEquals(
                        2
                    )
                    val contentBounds =
                        onNodeWithTag("cleaner-rules-window-content").fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        kotlin.math.abs(contentBounds.bottom - 600f) <= 1f,
                        "Editor bottom: $contentBounds"
                    )
                    assertEquals(0f, contentBounds.top, 1f)
                    assertEquals(0f, contentBounds.left, 1f)
                    assertEquals(800f, contentBounds.right, 1f)
                    val output = File(
                        checkNotNull(System.getProperty("test.fixtureRoot")),
                        "cleaner-screenshots"
                    ).apply { mkdirs() }
                    waitForIdle()
                    val suffix = if (english) "-en" else ""
                    fun capture(name: String) {
                        waitForIdle()
                        val bitmap = onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()
                        output.resolve("$name$suffix.png").writeBytes(
                            assertNotNull(
                                Image.makeFromBitmap(bitmap).encodeToData()
                            ).bytes
                        )
                    }
                    for (theme in listOf(false, true)) {
                        runOnIdle { dark = theme }
                        val mode = if (theme) "dark" else "light"
                        capture("rules-$mode")
                        onNode(
                            hasContentDescription(if (english) "Expand rule" else "展开规则") and hasAnyAncestor(
                                hasTestTag("rule-logs")
                            )
                        ).performClick()
                        onNodeWithTag("rule-logs").performScrollTo()
                        capture("rules-expanded-$mode")
                        val row = vm.uiState.value.conditionRows.getValue("logs").first()
                        onNodeWithTag("condition-edit-${row.id}").performScrollTo().performClick()
                        capture("rules-editing-$mode")
                        onNodeWithContentDescription(if (english) "Done editing" else "完成编辑").performClick()
                        onNode(
                            hasContentDescription(if (english) "Collapse rule" else "折叠规则") and hasAnyAncestor(
                                hasTestTag("rule-logs")
                            )
                        ).performScrollTo().performClick()
                        onNodeWithTag("cleaner-rule-list").performScrollToIndex(0)
                        onNodeWithTag(
                            "cleaner-scan-settings-header",
                            useUnmergedTree = true
                        ).performScrollTo().performClick()
                        onNodeWithTag("cleaner-scan-settings").performScrollTo()
                        capture("rules-scan-settings-$mode")
                        onNodeWithTag("cleaner-scan-settings-toggle").performClick()
                        onNodeWithTag("cleaner-rule-list").performScrollToIndex(0)
                    }

                } finally {
                    store.clear()
                }
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
