package org.tool.kit.tests.feature.apk

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.feature.apk.*
import org.tool.kit.theme.AppTheme
import java.io.File
import java.util.Locale
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ApkInformationLayoutUiTest {
    private val target = ApkInformationTestData.target
    private val sample = ApkInformationTestData.sample.copy(channel = "official", sha256 = "0123456789abcdef".repeat(4))
    private fun ComposeUiTest.tab(index: Int) { onNodeWithTag("apk-tab-$index").performClick().assertIsSelected() }
    private fun ComposeUiTest.back() { onNodeWithTag("apk-detail-back").performClick() }

    @Test fun allApprovedPagesRenderAt800By600InBothThemes() = runDesktopComposeUiTest(width = 800, height = 600) {
        var state by mutableStateOf(ApkInformationUiState())
        var dark by mutableStateOf(false)
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) { AppTheme(dark) {
            ApkInformationScreen(state, dark, {}, {}, false, target)
        } } }
        for (theme in listOf(false, true)) {
            runOnIdle { dark = theme; state = ApkInformationUiState() }
            onNodeWithTag("apk-pick-file").assertIsDisplayed()
            onNodeWithText("APK 信息").assertDoesNotExist()
            capture("empty", theme)
            runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Result, "/Downloads/example.apk", sample) }
            onNodeWithTag("apk-overview-checksums").assertIsDisplayed()
            onNodeWithText("APK 信息").assertDoesNotExist()
            capture("overview", theme)
            tab(1); capture("permissions", theme)
            onNodeWithTag("apk-permission-search").assertDoesNotExist()
            onNodeWithTag("apk-permission-search-toggle").performClick(); capture("permissions-search", theme)
            onNodeWithTag("apk-permission-search-toggle").performClick()
            onNodeWithTag("apk-permission-android.permission.WAKE_LOCK").assertIsDisplayed()
            tab(2); capture("components", theme)
            val chip = onNodeWithTag("apk-component-filter-Activity")
            val chipBounds = chip.getUnclippedBoundsInRoot()
            chip.performMouseInput { enter(center) }
            capture("components-hover", theme)
            assertEquals(chipBounds, chip.getUnclippedBoundsInRoot())
            chip.performMouseInput { exit() }
            onNodeWithTag("apk-detail-search").assertDoesNotExist()
            onNodeWithTag("apk-detail-search-toggle").performClick(); capture("components-search", theme)
            onNodeWithTag("apk-detail-search-toggle").performClick()
            onNodeWithTag("apk-component-Activity:com.example.app.MainActivity").performClick(); capture("component-detail", theme)
            back(); tab(3); capture("libraries", theme)
            onNodeWithTag("apk-detail-search").assertDoesNotExist()
            onNodeWithTag("apk-detail-search-toggle").performClick(); capture("libraries-search", theme)
            onNodeWithTag("apk-detail-search-toggle").performClick()
            onNodeWithTag("apk-library-lib/arm64-v8a/libbroken.so").performClick(); capture("library-detail", theme)
            back(); tab(4); capture("profile", theme)
            onNodeWithTag("apk-open-checksums").assertIsDisplayed().performClick(); capture("checksums", theme)
            onNodeWithText(sample.sha256).assertIsDisplayed()
            back(); tab(0); onNodeWithTag("apk-open-Package").performClick(); capture("files", theme)
            onNodeWithTag("apk-detail-search").assertDoesNotExist()
            onNodeWithTag("apk-detail-search-toggle").performClick(); capture("files-search", theme)
            onNodeWithTag("apk-detail-search-toggle").performClick()
            back(); onNodeWithTag("apk-open-breakdown").performClick(); capture("breakdown", theme)
            onNodeWithText("ZIP 结构与签名区").assertIsDisplayed()
        }
    }

    @Test fun permissionsStayLazyAndCopyFullNamesAfterFiltering() = runDesktopComposeUiTest(width = 800, height = 600) {
        val permissions = List(2000) { "com.example.permission.PERMISSION_$it" }
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/large.apk", sample.copy(usesPermissionList = permissions)), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        tab(1)
        assertTrue(onAllNodes(hasText("PERMISSION_", substring = true)).fetchSemanticsNodes().size < 30)
        onNodeWithTag("apk-permission-${permissions.last()}").assertDoesNotExist()
        onNodeWithTag("apk-results-list").performScrollToNode(hasTestTag("apk-permission-${permissions.last()}"))
        onNodeWithTag("apk-permission-${permissions.last()}").performClick()
        assertEquals(listOf(permissions.last()), copied)
        onNodeWithTag("apk-permission-search-toggle").performClick()
        onNodeWithTag("apk-permission-search").performTextInput("permission_1999")
        onNodeWithText("1 / 2000 项").assertExists()
        onNodeWithTag("apk-permission-${permissions.last()}").assertIsDisplayed()
        tab(3); tab(1)
        onNodeWithTag("apk-permission-search").assertTextContains("permission_1999")
        onNodeWithTag("apk-permission-search").performTextReplacement("no-such-permission")
        onNodeWithText("没有匹配的权限").assertIsDisplayed()
        onNodeWithContentDescription("清空搜索").performClick()
        onNodeWithTag("apk-permission-${permissions.first()}").assertIsDisplayed()
    }

    @Test fun nullAndEmptyPermissionsAndMissingFieldsRemainDistinct() = runDesktopComposeUiTest(width = 800, height = 600) {
        var result by mutableStateOf(sample.copy(label = "", packageName = "", md5 = "", sha256 = "", usesPermissionList = null))
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/empty.apk", result), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        onNodeWithTag("apk-app-name").assertIsNotEnabled()
        onNodeWithContentDescription("复制包名").assertIsNotEnabled()
        tab(1); onNodeWithText("未读取到权限信息").assertIsDisplayed()
        onNodeWithTag("apk-permission-search-toggle").assertIsNotEnabled()
        runOnIdle { result = result.copy(usesPermissionList = emptyList()) }
        tab(1); onNodeWithText("未声明权限").assertIsDisplayed()
        tab(4); onNodeWithTag("apk-open-checksums").performClick()
        onNodeWithContentDescription("复制MD5").assertIsNotEnabled()
        onNodeWithContentDescription("复制文件 SHA-256").assertIsNotEnabled()
        assertTrue(copied.isEmpty())
    }

    @Test fun checksumsReturnToTheirEntryPageAndCopyWithoutTruncation() = runDesktopComposeUiTest(width = 800, height = 600) {
        val result = sample.copy(packageName = "com.example." + "longpackage".repeat(12), launchableActivity = "com.example." + "VeryLongNamespace.".repeat(8) + "MainActivity")
        val copied = mutableListOf<String>()
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/Downloads/long name.apk", result), false,
            { if (it is ApkInformationIntent.CopyText) copied += it.value }, {}, false, target) } }
        onNodeWithContentDescription("复制包名").performClick()
        onNodeWithTag("apk-overview-checksums").performClick()
        onNodeWithContentDescription("复制MD5").performClick()
        onNodeWithContentDescription("复制文件 SHA-256").performClick()
        assertEquals(listOf(result.packageName, result.md5, result.sha256), copied)
        back(); onNodeWithTag("apk-tab-0").assertIsSelected()
        tab(4)
        onNodeWithContentDescription("复制启动 Activity").performScrollTo().performClick()
        onNodeWithTag("apk-open-checksums").performScrollTo().performClick()
        back(); onNodeWithTag("apk-tab-4").assertIsSelected()
        assertEquals(result.launchableActivity, copied.last())
        val header = onNodeWithTag("apk-overview").fetchSemanticsNode().boundsInRoot
        val packageBounds = onNodeWithTag("apk-package-name").fetchSemanticsNode().boundsInRoot
        assertTrue(packageBounds.right <= header.right)
    }

    @Test fun sharedDropCoversAllTabsAndBlocksClickThrough() = runDesktopComposeUiTest(width = 800, height = 600) {
        var dragging by mutableStateOf(false)
        var picks = 0
        setContent { AppTheme(false) { ApkInformationScreen(ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample), false, {}, { picks++ }, dragging, target) } }
        for (page in 0..4) {
            tab(page)
            val header = onNodeWithTag("apk-overview").fetchSemanticsNode().boundsInRoot
            runOnIdle { dragging = true }
            mainClock.advanceTimeBy(500)
            assertEquals(onNodeWithTag("apk-information-page").fetchSemanticsNode().boundsInRoot, onNodeWithTag("apk-drop-animation").fetchSemanticsNode().boundsInRoot)
            onNodeWithContentDescription("Lottie animation").assertIsDisplayed()
            onNodeWithTag("apk-pick-file").performClick()
            onNodeWithTag("apk-tab-${(page + 1) % 5}").performClick()
            onNodeWithTag("apk-tab-$page").assertIsSelected()
            assertEquals(0, picks)
            runOnIdle { dragging = false }; mainClock.advanceTimeBy(500)
            assertEquals(header, onNodeWithTag("apk-overview").fetchSemanticsNode().boundsInRoot)
        }
        onNodeWithTag("apk-pick-file").performClick(); assertEquals(1, picks)
    }

    @Test fun loadingUsesSharedAnimationAndLeavesStatusUncovered() = runDesktopComposeUiTest(width = 800, height = 600) {
        var state by mutableStateOf(ApkInformationUiState())
        var dragging by mutableStateOf(false)
        setContent { AppTheme(true) { ApkInformationScreen(state, true, {}, {}, dragging, target) } }
        val position = onNodeWithTag("apk-pick-file").fetchSemanticsNode().boundsInRoot
        runOnIdle { dragging = true }; mainClock.advanceTimeBy(500)
        assertEquals(position, onNodeWithTag("apk-pick-file").fetchSemanticsNode().boundsInRoot)
        runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Loading, "/Downloads/example.apk") }; mainClock.advanceTimeBy(600)
        onNodeWithTag("apk-drop-animation").assertDoesNotExist()
        onNodeWithTag("apk-loading-animation").assertIsDisplayed()
        onNodeWithContentDescription("Lottie animation").assertIsDisplayed()
        onNodeWithTag("apk-pick-file").assertDoesNotExist()
        assertTrue(onNodeWithTag("apk-loading-animation").fetchSemanticsNode().boundsInRoot.bottom < onNodeWithText("example.apk").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun englishPagesAndLongValuesRemainReachableAt800() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            runDesktopComposeUiTest(width = 800, height = 600) {
                var state by mutableStateOf(ApkInformationUiState())
                setContent { AppTheme(false) { ApkInformationScreen(state, false, {}, {}, false, target) } }
                onNodeWithTag("apk-pick-file").assertIsDisplayed()
                onNodeWithTag("apk-usage-hint").performScrollTo().assertIsDisplayed()
                runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Result, "/example.apk", sample) }
                for (page in 0..4) { tab(page); onNodeWithTag("apk-pick-file").assertIsDisplayed() }
                onNodeWithTag("apk-open-checksums").performScrollTo().performClick()
                onNodeWithText(sample.sha256).assertIsDisplayed()
                capture("checksums-en", false)
            }
        } finally { Locale.setDefault(locale) }
    }

    @Test fun iconPreviewClosesWhenAnotherFileStartsLoading() = runDesktopComposeUiTest(width = 800, height = 600) {
        var state by mutableStateOf(ApkInformationUiState(ApkInformationPhase.Result, "/icon.apk", sample.copy(icon = ImageBitmap(64, 64))))
        setContent { AppTheme(false) { ApkInformationScreen(state, false, {}, {}, false, target) } }
        onNodeWithTag("apk-app-icon").performClick()
        waitUntil(timeoutMillis = 10_000) { java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>().any { it.isVisible && it.title == "应用图标预览" } }
        runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Loading, "/next.apk") }
        waitUntil(timeoutMillis = 10_000) { java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>().none { it.isVisible && it.title == "应用图标预览" } }
    }

    private fun ComposeUiTest.capture(name: String, dark: Boolean) {
        mainClock.advanceTimeBy(1200); waitForIdle()
        val dir = File(checkNotNull(System.getProperty("test.fixtureRoot")), "apk-information-n").apply { mkdirs() }
        val bitmap = onNodeWithTag("apk-information-page").captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(bitmap).use { image ->
            assertEquals(800, image.width); assertEquals(600, image.height)
            image.encodeToData()!!.use { dir.resolve("n-$name${if (dark) "-dark" else ""}.png").writeBytes(it.bytes) }
        }
    }
}
