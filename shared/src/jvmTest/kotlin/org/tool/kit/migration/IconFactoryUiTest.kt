package org.tool.kit.migration

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinApplication
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.vm.MainViewModel
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** The same fixture and interactions capture both sides of the Phase 7A cutover. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class IconFactoryUiTest {
    @Test fun lightPreviewAndSheet() = previewAndSheet("LIGHT")
    @Test fun darkPreviewAndSheet() = previewAndSheet("DARK")

    private fun previewAndSheet(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        val fixtureRoot = File(checkNotNull(System.getProperty("migration.fixtureRoot")))
        prepareBaselinePreferences(fixtureRoot, theme)
        val fixture = File(fixtureRoot, "phase7a-ui").apply { mkdirs() }
        fun image(name: String, size: Int): File {
            val pixels = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until size) for (x in 0 until size) {
                pixels.setRGB(x, y, (255 shl 24) or ((x * 255 / size) shl 16) or ((y * 255 / size) shl 8) or 90)
            }
            return File(fixture, name).also { ImageIO.write(pixels, "png", it) }
        }
        val input = image("中文 icon.png", 256)
        val outputs = listOf(48, 72, 96, 144, 192).map { image("result-$it.png", it) }
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val container = koinApplication { modules(desktopModules()) }
        lateinit var vm: MainViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<MainViewModel>()
                    App()
                    SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            onNode(hasText("图标生成") and hasClickAction()).performClick()
            runOnIdle { vm.updateIconFactoryInfo(vm.iconFactoryInfoState.copy(icon = input, result = null)) }
            fun capture(name: String) {
                waitForIdle()
                // Coil decoding is external to the Compose test clock. Wait for stable rendered pixels.
                var previous: IntArray? = null
                var consecutive = 0
                var output: BufferedImage? = null
                waitUntil(timeoutMillis = 10_000) {
                    val bitmap = onAllNodes(isRoot()).onLast().captureToImage()
                    val pixels = bitmap.toPixelMap()
                    val values = IntArray(bitmap.width * bitmap.height) { i -> pixels[i % bitmap.width, i / bitmap.width].toArgb() }
                    consecutive = if (previous?.contentEquals(values) == true) consecutive + 1 else 0
                    previous = values
                    output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB).apply {
                        setRGB(0, 0, bitmap.width, bitmap.height, values, 0, bitmap.width)
                    }
                    consecutive >= 3
                }
                val file = File(System.getProperty("migration.renderOutput"), "phase7a/${theme.lowercase()}/$name.png")
                file.parentFile.mkdirs(); ImageIO.write(output, "png", file)
            }
            capture("preview-null")
            runOnIdle { vm.updateIconFactoryInfo(vm.iconFactoryInfoState.copy(result = mutableListOf())) }
            capture("preview-empty")
            runOnIdle { vm.updateIconFactoryInfo(vm.iconFactoryInfoState.copy(result = outputs.toMutableList())) }
            capture("preview-results")
            onNodeWithText("更多设置", useUnmergedTree = true).performClick()
            capture("sheet-partial")
            onNodeWithContentDescription("KeyboardArrowUp").performClick()
            capture("sheet-expanded-lossless")
            onNodeWithText("有损压缩").performClick()
            capture("sheet-expanded-lossy")
            onAllNodes(hasScrollAction() and !hasSetTextAction()).onLast().performScrollToNode(hasText("PNG 缩放算法"))
            capture("sheet-algorithms")
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
