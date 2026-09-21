package org.tool.kit.tests.feature.apk

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import org.jetbrains.skia.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.junit.Test
import org.tool.kit.feature.apk.*
import org.tool.kit.domain.apk.*
import org.tool.kit.theme.AppTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ApkInformationPolishUiTest {
    private val sample = ApkInformationTestData.sample
    private val target = ApkInformationTestData.target

    @Test fun searchesStartHiddenFocusOnOpenAndClearWhenClosedOrEscaped() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target) } }
        for (page in 0..3) {
            onNodeWithTag("apk-tab-$page").performClick()
            if (page == 0) onNodeWithTag("apk-open-Package").performClick()
            val tag = if (page == 1) "apk-permission-search" else "apk-detail-search"
            onNodeWithTag(tag).assertDoesNotExist()
            onNodeWithTag("$tag-toggle").assertIsDisplayed().performClick()
            onNodeWithTag(tag).assertIsFocused().performTextInput("no-matches")
            onNodeWithText(if (page == 1) "没有匹配的权限" else "没有匹配的条目").assertIsDisplayed()
            onNodeWithTag("$tag-toggle").performClick()
            onNodeWithTag(tag).assertDoesNotExist()
            onNodeWithText(if (page == 1) "没有匹配的权限" else "没有匹配的条目").assertDoesNotExist()
            onNodeWithTag("$tag-toggle").performClick()
            onNodeWithTag(tag).performTextInput("missing-again")
            onNodeWithTag(tag).performKeyInput { pressKey(Key.Escape) }
            onNodeWithTag(tag).assertDoesNotExist()
            onNodeWithTag("$tag-toggle").performClick()
            assertEquals("", onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            onNodeWithTag("$tag-toggle").performClick()
        }
    }

    @Test fun searchAnimationReversesAndKeepsQueryVisibleAcrossTabs() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target) } }
        onNodeWithTag("apk-tab-1").performClick()
        onNodeWithTag("apk-permission-search-toggle").assertIsDisplayed()
        mainClock.autoAdvance = false
        try {
            onNodeWithTag("apk-permission-search-toggle").performClick()
            mainClock.advanceTimeBy(48)
            val partial = onNodeWithTag("apk-permission-search-reveal").getUnclippedBoundsInRoot().let { it.bottom - it.top }
            mainClock.advanceTimeBy(1000)
            val full = onNodeWithTag("apk-permission-search-reveal").getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue(partial > androidx.compose.ui.unit.Dp(0f) && partial < full)
            onNodeWithTag("apk-permission-search-toggle").performClick()
            mainClock.advanceTimeBy(48)
            onNodeWithTag("apk-permission-search-toggle").performClick()
            mainClock.advanceTimeBy(1000)
            onNodeWithTag("apk-permission-search").assertIsDisplayed()
        } finally { mainClock.autoAdvance = true }
        onNodeWithTag("apk-permission-search").performTextInput("CAMERA")
        onNodeWithTag("apk-tab-2").performClick()
        onNodeWithTag("apk-detail-search").assertDoesNotExist()
        onNodeWithTag("apk-tab-1").performClick()
        onNodeWithTag("apk-permission-search").assertTextContains("CAMERA")
        onNodeWithTag("apk-permission-android.permission.CAMERA").assertIsDisplayed()
    }


    @Test fun compactHeaderAndProfileFieldsKeepAlignedTextEdges() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target) } }
        val header = onNodeWithTag("apk-overview").getUnclippedBoundsInRoot()
        assertTrue(header.bottom - header.top <= androidx.compose.ui.unit.Dp(96f))
        onNodeWithTag("apk-tab-4").performClick()
        fun left(label: String) = onNode(hasText(label) and hasAnyAncestor(hasTestTag("apk-file-information")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left
        assertEquals(left("启动 Activity"), left("文件大小"))
        assertEquals(left("文件名"), left("文件大小"))
        onNodeWithTag("apk-open-checksums").assertIsDisplayed()
    }

    @Test fun listsHaveContinuousRowsAndPermissionGroupClipsItsCorners() = runDesktopComposeUiTest(width = 800, height = 600) {
        var surface = Color.Unspecified
        var group = Color.Unspecified
        setContent { AppTheme(false) {
            val scheme = MaterialTheme.colorScheme
            SideEffect { surface = scheme.surface; group = scheme.surfaceContainerLow }
            ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), false, {}, {}, false, target)
        } }
        onNodeWithTag("apk-tab-2").performClick()
        val first = onNodeWithTag("apk-component-Activity:com.example.app.MainActivity").getUnclippedBoundsInRoot()
        val second = onNodeWithTag("apk-component-ActivityAlias:com.example.app.Shortcut").getUnclippedBoundsInRoot()
        assertEquals(first.bottom, second.top, "Adjacent rows must not be separated by card margins")
        onNodeWithTag("apk-tab-1").performClick()
        val list = onNodeWithTag("apk-results-list").fetchSemanticsNode().boundsInRoot
        val pixels = onNodeWithTag("apk-information-page").captureToImage().toPixelMap()
        assertEquals(surface.toArgb(), pixels[list.left.toInt() + 1, list.top.toInt() + 1].toArgb(), "Outer corner should be clipped")
        assertEquals(group.toArgb(), pixels[list.left.toInt() + 1, (list.top + list.bottom).toInt() / 2].toArgb(), "The list shares one background")
    }

    @Test fun overviewShowsResourcesEvenWhenFifthAndRuntimeFitsWithTheAppNavigation() = runDesktopComposeUiTest(width = 720, height = 572) {
        val archive = ApkArchiveInformation(listOf(
            ApkArchiveFile("lib/arm64-v8a/libsample.so", 151_000_000, 151_000_000, ApkFileCategory.Native),
            ApkArchiveFile("classes.dex", 74_000_000, 74_000_000, ApkFileCategory.Dex),
            ApkArchiveFile("assets/model.bin", 33_000_000, 33_000_000, ApkFileCategory.Assets),
            ApkArchiveFile("other.bin", 12_000_000, 12_000_000, ApkFileCategory.Other),
            ApkArchiveFile("resources.arsc", 8_000_000, 8_000_000, ApkFileCategory.Resources),
            ApkArchiveFile("AndroidManifest.xml", 2_000_000, 2_000_000, ApkFileCategory.Metadata)
        ), listOf(ApkInformationTestData.libraries.first()), 1_000_000)
        var result by mutableStateOf(sample.copy(size = 281_000_000, archive = archive,
            nativeCode = "arm64-v8a fonts images magicbrush_extension.js"))
        setContent { AppTheme(false) {
            ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", result), false, {}, {}, false, target)
        } }
        onNodeWithText("资源", substring = false).assertIsDisplayed()
        onNodeWithText("8.0 MB · 3%", substring = false).assertIsDisplayed()
        onNodeWithText("清单元数据与 ZIP", substring = false).assertIsDisplayed()
        onNodeWithText("3.0 MB", substring = false).assertIsDisplayed()
        onNodeWithTag("apk-open-breakdown").assertIsDisplayed()
        onNodeWithTag("apk-overview-checksums").assertIsDisplayed()
        onNode(hasText("arm64-v8a", substring = false) and hasAnyAncestor(hasTestTag("apk-runtime"))).assertIsDisplayed()
        onNodeWithText("fonts", substring = true).assertDoesNotExist()
        runOnIdle { result = result.copy(archive = archive.copy(nativeLibraries = emptyList())) }
        onNodeWithText("未包含原生库").assertIsDisplayed()
    }

    @Test fun footerActionsStayFullyVisibleWithVerticalInsetsAndLongRuntimeContent() = runDesktopComposeUiTest(width = 720, height = 572) {
        var result by mutableStateOf(sample)
        setContent { AppTheme(false) {
            Box(Modifier.fillMaxSize().padding(vertical = 20.dp)) {
                ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", result), false, {}, {}, false, target)
            }
        } }
        fun checkFullyVisible(tag: String) {
            val page = onNodeWithTag("apk-information-page").getUnclippedBoundsInRoot()
            val button = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(button.top >= page.top && button.bottom <= page.bottom - 20.dp,
                "$tag must fit completely above the page bottom inset: $button inside $page")
            assertTrue(button.bottom - button.top >= 40.dp, "The native button must keep its full height")
        }
        checkFullyVisible("apk-overview-checksums")
        checkFullyVisible("apk-open-breakdown")
        val categories = onNodeWithTag("apk-size-categories").getUnclippedBoundsInRoot()
        val bars = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(5, bars.fetchSemanticsNodes().size)
        // Native progress indicators expand their accessibility bounds beyond the painted track.
        // Compare the full category row's actual layout instead of that virtual hit region.
        assertTrue(onNodeWithTag("apk-overview-size-Other").getUnclippedBoundsInRoot().bottom <= categories.bottom,
            "All five category rows must fit without scrolling")
        assertTrue(onNodeWithTag("apk-runtime").getUnclippedBoundsInRoot().bottom <=
            onNodeWithTag("apk-overview-details").getUnclippedBoundsInRoot().bottom, "The normal runtime panel must fit in full")
        val fixedAction = onNodeWithTag("apk-overview-checksums").getUnclippedBoundsInRoot()
        val directory = File(checkNotNull(System.getProperty("test.fixtureRoot")), "apk-information-n").apply { mkdirs() }
        Image.makeFromBitmap(onNodeWithTag("apk-information-page").captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData()!!.use { directory.resolve("n-overview-insets.png").writeBytes(it.bytes) }
        }
        runOnIdle { result = sample.copy(archive = null, nativeCode = List(15) { "architecture-$it" }.joinToString(", ")) }
        checkFullyVisible("apk-overview-checksums")
        assertEquals(fixedAction, onNodeWithTag("apk-overview-checksums").getUnclippedBoundsInRoot())
        onNodeWithText(result.nativeCode, substring = false).performScrollTo()
        assertEquals(fixedAction, onNodeWithTag("apk-overview-checksums").getUnclippedBoundsInRoot())
        onNodeWithTag("apk-overview-checksums").performClick()
        onNodeWithTag("apk-checksums").assertIsDisplayed()
    }

    @Test fun detailCardsAndOverviewDistributeRemainingHeightWithoutMovingActions() = runDesktopComposeUiTest(width = 720, height = 572) {
        val copied = mutableListOf<String>()
        val longName = "com.example." + "VeryLongNamespace.".repeat(90) + "Activity"
        val longComponent = sample.components!!.first().copy(name = longName, process = "com.example.longprocess")
        val result = sample.copy(components = sample.components.orEmpty() + longComponent)
        setContent { AppTheme(false) {
            ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", result), false,
                { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target)
        } }
        fun bounds(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val bottom = bounds("apk-information-page").bottom - 20.dp
        // Native buttons reserve a 48 dp interaction layout around their 40 dp visual surface.
        val runtimeGap = bounds("apk-overview-checksums").top - bounds("apk-runtime").bottom
        assertTrue(runtimeGap in 12.dp..16.dp)
        assertEquals(bounds("apk-size-categories").bottom, bounds("apk-overview-size-Other").bottom)
        onNodeWithTag("apk-open-breakdown").performClick()
        assertTrue(bottom - bounds("apk-breakdown-files").bottom in 0.dp..4.dp)
        val actionGap = bounds("apk-breakdown-files").top - bounds("apk-size-breakdown").bottom
        assertTrue(actionGap in 8.dp..12.dp)
        onNodeWithTag("apk-tab-2").performClick()
        onNodeWithTag("apk-component-Activity:com.example.app.MainActivity").performClick()
        assertEquals(bottom, bounds("apk-component-detail").bottom)
        onNodeWithTag("apk-detail-back").performClick()
        onNodeWithTag("apk-component-Activity:$longName").performScrollTo().performClick()
        assertEquals(bottom, bounds("apk-component-detail").bottom)
        onNodeWithContentDescription("复制运行进程").performScrollTo().performClick()
        assertEquals(listOf(longComponent.process), copied)
        onNodeWithTag("apk-detail-back").assertIsDisplayed().performClick()
        onNodeWithTag("apk-components-list").assertIsDisplayed()
    }

    @Test fun nativeProgressTracksDifferFromCardBackgroundInBothThemes() = runDesktopComposeUiTest(width = 800, height = 600) {
        var dark by mutableStateOf(false)
        var background = Color.Unspecified
        setContent { AppTheme(dark) {
            val color = MaterialTheme.colorScheme.secondaryContainer
            SideEffect { background = color }
            ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/test.apk", sample), dark, {}, {}, false, target)
        } }
        for (theme in listOf(false, true)) {
            runOnIdle { dark = theme }
            val bars = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            assertEquals(5, bars.fetchSemanticsNodes().size)
            // The largest fixture category fills 67%; 85% lies on the remaining native track.
            val pixels = bars[0].captureToImage().toPixelMap()
            val track = pixels[(pixels.width * .85f).toInt(), pixels.height / 2]
            assertNotEquals(background.toArgb(), track.toArgb(), "The unfilled track must not disappear into the card")
        }
    }
}
