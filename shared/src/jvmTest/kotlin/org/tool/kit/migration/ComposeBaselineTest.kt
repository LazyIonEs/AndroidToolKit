package org.tool.kit.migration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.koin.core.KoinApplication
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.tool.kit.di.desktopModules
import org.tool.kit.App
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Software-rendered client-area evidence; native window/picker/input tests remain separate. */
@OptIn(ExperimentalTestApi::class, com.russhwolf.settings.ExperimentalSettingsApi::class)
class ComposeBaselineTest {
    private val fixtureRoot = File(checkNotNull(System.getProperty("migration.fixtureRoot")))
    private val renderOutput = File(checkNotNull(System.getProperty("migration.renderOutput")))
    private lateinit var container: KoinApplication

    @Before fun startIsolatedContainer() {
        container = koinApplication { modules(desktopModules()) }
    }

    @After fun closeIsolatedContainer() = container.close()

    @Test fun lightNavigationRendersAllNinePages() = captureNavigation("LIGHT")

    @Test fun darkNavigationRendersAllNinePages() = captureNavigation("DARK")

    private fun captureNavigation(theme: String) = runDesktopComposeUiTest(
        width = 800, height = 572, testTimeout = 45.seconds
    ) {
        prepareBaselinePreferences(fixtureRoot, theme)
        setContent { KoinIsolatedContext(container) { App() } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
        val pages = listOf(
            "signature-information" to "签名信息", "apk-information" to "APK信息",
            "apk-signature" to "APK签名", "keystore-generation" to "签名生成",
            "apk-tool" to "APK生成", "junk-code" to "垃圾代码",
            "icon-factory" to "图标生成", "cleaner" to "缓存清理", "settings" to "设置"
        )
        pages.forEach { (name, label) ->
            onNode(hasText(label) and hasClickAction()).performClick().assertIsSelected()
            val destination = renderOutput.resolve("${theme.lowercase()}/$name.png")
            savePng(onRoot().captureToImage(), destination)
            destination.resolveSibling("$name.txt").writeText(onRoot().printToString())
        }
    }

    @Test fun signingAndKeyStoreDraftsSurviveTopLevelNavigation() = runDesktopComposeUiTest(
        width = 800, height = 572, testTimeout = 45.seconds
    ) {
        prepareBaselinePreferences(fixtureRoot, "LIGHT")
        setContent { KoinIsolatedContext(container) { App() } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("APK签名") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("输出文件前缀(选填)"))
            .performTextReplacement("phase0-draft")
        onNode(hasText("签名生成") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("密钥文件名称"))
            .performTextReplacement("phase0-test.jks")
        onNode(hasText("APK签名") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("输出文件前缀(选填)"))
            .assertTextContains("phase0-draft")
        savePng(onRoot().captureToImage(), renderOutput.resolve("interactions/signing-draft-returned.png"))
        onNode(hasText("签名生成") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("密钥文件名称"))
            .assertTextContains("phase0-test.jks")
        savePng(onRoot().captureToImage(), renderOutput.resolve("interactions/keystore-draft-returned.png"))
    }

    private fun savePng(image: ImageBitmap, file: File) {
        assertEquals(800, image.width)
        assertEquals(572, image.height)
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        file.parentFile.mkdirs()
        check(ImageIO.write(output, "png", file))
    }
}
