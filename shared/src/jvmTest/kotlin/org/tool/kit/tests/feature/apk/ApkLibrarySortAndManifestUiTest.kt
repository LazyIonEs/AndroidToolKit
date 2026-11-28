package org.tool.kit.tests.feature.apk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.tool.kit.domain.apk.ApkArchiveEntryContent
import org.tool.kit.domain.apk.ApkArchiveFile
import org.tool.kit.domain.apk.ApkArchiveInformation
import org.tool.kit.domain.apk.ApkArchivePreviewKind
import org.tool.kit.domain.apk.ApkFileCategory
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.feature.apk.ApkArchivePreviewContent
import org.tool.kit.feature.apk.ApkIconDecoder
import org.tool.kit.feature.apk.ApkInformationIntent
import org.tool.kit.feature.apk.ApkInformationPhase
import org.tool.kit.feature.apk.ApkInformationScreen
import org.tool.kit.feature.apk.ApkInformationUiState
import org.tool.kit.feature.apk.ApkInformationViewModel
import org.tool.kit.feature.apk.ApkManifestPreviewContent
import org.tool.kit.feature.apk.ArchiveEntryPreviewState
import org.tool.kit.feature.apk.ManifestPreviewState
import org.tool.kit.tests.support.ControlledClipboard
import org.tool.kit.tests.support.FixtureApkRepository
import org.tool.kit.theme.AppTheme
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ApkLibrarySortAndManifestUiTest {
    @Test
    fun libraryListDefaultsToSizeDescendingAndTogglesWithNameSort() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent {
            AppTheme(false) {
                ApkInformationScreen(
                    state = ApkInformationUiState(ApkInformationPhase.Result, "/app.apk", ApkInformationTestData.sample),
                    useDarkTheme = false,
                    onIntent = {},
                    onPickFile = {},
                    dragging = false,
                    dropTarget = ApkInformationTestData.target,
                )
            }
        }
        onNodeWithTag("apk-tab-3").performClick()

        // Default: size descending -> 6MB x86_64/libgraphics.so -> 5MB arm64-v8a/libgraphics.so -> 128B libbroken.so
        fun topOf(path: String) = onNodeWithTag("apk-library-$path").getUnclippedBoundsInRoot().top
        assertTrue(topOf("lib/x86_64/libgraphics.so") < topOf("lib/arm64-v8a/libgraphics.so"))
        assertTrue(topOf("lib/arm64-v8a/libgraphics.so") < topOf("lib/arm64-v8a/libbroken.so"))
        onNodeWithText("按大小排序").assertIsDisplayed()

        // Toggle -> name ascending -> libbroken.so before libgraphics.so (arm64-v8a before x86_64)
        onNodeWithTag("apk-library-sort").performClick()
        onNodeWithText("按名称排序").assertIsDisplayed()
        assertTrue(topOf("lib/arm64-v8a/libbroken.so") < topOf("lib/arm64-v8a/libgraphics.so"))
        assertTrue(topOf("lib/arm64-v8a/libgraphics.so") < topOf("lib/x86_64/libgraphics.so"))

        // Toggle back -> size descending
        onNodeWithTag("apk-library-sort").performClick()
        onNodeWithText("按大小排序").assertIsDisplayed()
        assertTrue(topOf("lib/x86_64/libgraphics.so") < topOf("lib/arm64-v8a/libbroken.so"))
    }

    @Test
    fun viewModelLoadsManifestOnDemandAndResetsOnNewApk() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val pendingXml = CompletableDeferred<String?>()
            val repo = object : FixtureApkRepository() {
                override suspend fun decodedManifestXml(path: String): String? = pendingXml.await()
            }
            val vm = ApkInformationViewModel(
                read = ReadApkInformationUseCase(repo),
                decoder = ApkIconDecoder { error("No icon") },
                clipboard = ControlledClipboard(),
                effects = org.tool.kit.feature.app.AppEffectSink(),
            )
            store.put("vm", vm)

            vm.onIntent(ApkInformationIntent.ReadApk("first.apk"))
            runCurrent()
            assertEquals(ManifestPreviewState.Idle, vm.uiState.value.manifestPreview)

            vm.onIntent(ApkInformationIntent.LoadManifestPreview)
            runCurrent()
            assertEquals(ManifestPreviewState.Loading, vm.uiState.value.manifestPreview)

            pendingXml.complete("<manifest package=\"com.example.app\" />")
            runCurrent()
            val loaded = vm.uiState.value.manifestPreview
            assertIs<ManifestPreviewState.Loaded>(loaded)
            assertTrue(loaded.xml.contains("com.example.app"))

            vm.onIntent(ApkInformationIntent.ReadApk("second.apk"))
            runCurrent()
            assertEquals(ManifestPreviewState.Idle, vm.uiState.value.manifestPreview)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun profileOpensManifestPreviewWindowAndDisposesWhenReplacingApk() = runDesktopComposeUiTest(width = 800, height = 600) {
        val manifestXml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name" />
            </manifest>
        """.trimIndent()
        val copied = mutableListOf<String>()
        var state by mutableStateOf(
            ApkInformationUiState(
                phase = ApkInformationPhase.Result,
                inputFile = "/example.apk",
                result = ApkInformationTestData.sample,
            )
        )
        setContent {
            AppTheme(false) {
                ApkInformationScreen(
                    state = state,
                    useDarkTheme = false,
                    onIntent = { intent ->
                        when (intent) {
                            ApkInformationIntent.LoadManifestPreview -> {
                                state = state.copy(manifestPreview = ManifestPreviewState.Loaded(manifestXml))
                            }
                            is ApkInformationIntent.CopyText -> copied += intent.value
                            else -> Unit
                        }
                    },
                    onPickFile = {},
                    dragging = false,
                    dropTarget = ApkInformationTestData.target,
                )
            }
        }

        onNodeWithTag("apk-open-manifest").assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 10_000) {
            java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                .any { it.isVisible && it.title.contains("AndroidManifest.xml 预览") }
        }

        runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Loading, "/next.apk") }
        waitUntil(timeoutMillis = 10_000) {
            java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                .none { it.isVisible && it.title.contains("AndroidManifest.xml 预览") }
        }
        assertTrue(copied.isEmpty())
    }

    @Test
    fun manifestPreviewContentSearchesAndCopiesXml() = runDesktopComposeUiTest(width = 860, height = 640) {
        val manifestXml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name" />
            </manifest>
        """.trimIndent()
        val copied = mutableListOf<String>()
        setContent {
            AppTheme(false) {
                ApkManifestPreviewContent(
                    state = ManifestPreviewState.Loaded(manifestXml),
                    dark = false,
                    onCopy = { copied += it },
                )
            }
        }
        onNodeWithTag("apk-manifest-content").assertIsDisplayed()
        onNodeWithText("3 行").assertIsDisplayed()
        onNodeWithTag("apk-manifest-search").performTextInput("app_name")
        onNodeWithText("匹配 1 / 3 行").assertIsDisplayed()
        onNodeWithTag("apk-manifest-copy").performClick()
        assertEquals(listOf(manifestXml), copied)
    }

    @Test
    fun fileBrowserKeepsRowCopyAndShowsPreviewOnlyForSupportedImagesAndXml() = runDesktopComposeUiTest(width = 800, height = 600) {
        val archive = ApkArchiveInformation(
            files = listOf(
                ApkArchiveFile("res/drawable/icon.png", 1000, 800, ApkFileCategory.Resources),
                ApkArchiveFile("res/drawable/bg.9.png", 900, 700, ApkFileCategory.Resources),
                ApkArchiveFile("res/layout/activity_main.xml", 800, 500, ApkFileCategory.Resources),
                ApkArchiveFile("classes.dex", 600, 400, ApkFileCategory.Dex),
            ),
            nativeLibraries = emptyList(),
            overheadBytes = 0,
        )
        var state by mutableStateOf(
            ApkInformationUiState(
                phase = ApkInformationPhase.Result,
                inputFile = "/preview.apk",
                result = ApkInformationTestData.sample.copy(archive = archive),
            )
        )
        val copied = mutableListOf<String>()
        val previews = mutableListOf<Pair<String, ApkArchivePreviewKind>>()
        setContent {
            AppTheme(false) {
                ApkInformationScreen(
                    state = state,
                    useDarkTheme = false,
                    onIntent = { intent ->
                        when (intent) {
                            is ApkInformationIntent.CopyText -> copied += intent.value
                            is ApkInformationIntent.PreviewArchiveEntry -> {
                                previews += intent.entryPath to intent.kind
                                state = state.copy(entryPreview = ArchiveEntryPreviewState.XmlLoaded(intent.entryPath, "<LinearLayout />"))
                            }
                            ApkInformationIntent.CloseArchiveEntryPreview -> {
                                state = state.copy(entryPreview = ArchiveEntryPreviewState.Idle)
                            }
                            else -> Unit
                        }
                    },
                    onPickFile = {},
                    dragging = false,
                    dropTarget = ApkInformationTestData.target,
                )
            }
        }
        onNodeWithTag("apk-open-Package").performClick()
        onNodeWithTag("apk-file-preview-res/drawable/icon.png").assertIsDisplayed()
        onNodeWithTag("apk-file-preview-res/layout/activity_main.xml").assertIsDisplayed()
        onNodeWithTag("apk-file-preview-res/drawable/bg.9.png").assertDoesNotExist()
        onNodeWithTag("apk-file-preview-classes.dex").assertDoesNotExist()

        // Row click still copies the path instead of opening preview.
        onNodeWithTag("apk-file-res/drawable/icon.png").performClick()
        assertEquals(listOf("res/drawable/icon.png"), copied)
        assertTrue(previews.isEmpty())

        // Preview button opens dedicated window and clears on APK switch.
        onNodeWithTag("apk-file-preview-res/layout/activity_main.xml").performClick()
        assertEquals(listOf("res/layout/activity_main.xml" to ApkArchivePreviewKind.Xml), previews)
        waitUntil(timeoutMillis = 10_000) {
            java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                .any { it.isVisible && it.title.contains("activity_main.xml") }
        }
        runOnIdle { state = ApkInformationUiState(ApkInformationPhase.Loading, "/other.apk") }
        waitUntil(timeoutMillis = 10_000) {
            java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                .none { it.isVisible && it.title.contains("activity_main.xml") }
        }
    }

    @Test
    fun viewModelLoadsArchiveEntryPreviewAndContentRendersXmlAndImage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val pending = CompletableDeferred<ApkArchiveEntryContent?>()
            val repo = object : FixtureApkRepository() {
                override suspend fun archiveEntryPreview(path: String, entryPath: String): ApkArchiveEntryContent? =
                    pending.await()
            }
            val vm = ApkInformationViewModel(
                read = ReadApkInformationUseCase(repo),
                decoder = ApkIconDecoder { ImageBitmap(32, 24) },
                clipboard = ControlledClipboard(),
                effects = org.tool.kit.feature.app.AppEffectSink(),
            )
            store.put("vm", vm)

            vm.onIntent(ApkInformationIntent.ReadApk("a.apk"))
            runCurrent()
            vm.onIntent(ApkInformationIntent.PreviewArchiveEntry("res/icon.png", ApkArchivePreviewKind.Image))
            runCurrent()
            assertIs<ArchiveEntryPreviewState.Loading>(vm.uiState.value.entryPreview)

            pending.complete(ApkArchiveEntryContent.Image(ApkIconSource(byteArrayOf(1))))
            runCurrent()
            val imageLoaded = assertIs<ArchiveEntryPreviewState.ImageLoaded>(vm.uiState.value.entryPreview)
            assertEquals(32, imageLoaded.bitmap.width)

            vm.onIntent(ApkInformationIntent.CloseArchiveEntryPreview)
            runCurrent()
            assertEquals(ArchiveEntryPreviewState.Idle, vm.uiState.value.entryPreview)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun archivePreviewContentDisplaysImageDimensionsXmlSearchAndUnavailable() = runDesktopComposeUiTest(width = 680, height = 560) {
        var state by mutableStateOf<ArchiveEntryPreviewState>(
            ArchiveEntryPreviewState.ImageLoaded("res/drawable/sample.png", ImageBitmap(64, 48))
        )
        val copied = mutableListOf<String>()
        setContent {
            AppTheme(false) {
                ApkArchivePreviewContent(
                    state = state,
                    dark = false,
                    onCopy = { copied += it },
                )
            }
        }
        onNodeWithTag("apk-archive-preview-path").assertIsDisplayed()
        onNodeWithText("64 × 48 px").assertIsDisplayed()
        onNodeWithTag("apk-archive-image-preview").assertIsDisplayed()

        runOnIdle { state = ArchiveEntryPreviewState.XmlLoaded("res/layout/main.xml", "<FrameLayout />") }
        onNodeWithTag("apk-manifest-content").assertIsDisplayed()
        onNodeWithTag("apk-manifest-copy").performClick()
        assertEquals(listOf("<FrameLayout />"), copied)

        runOnIdle { state = ArchiveEntryPreviewState.Unavailable("res/raw/bad.xml", ApkArchivePreviewKind.Xml) }
        onNodeWithText("无法预览该资源文件").assertIsDisplayed()
    }
}
