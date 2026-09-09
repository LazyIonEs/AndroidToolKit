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
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.repository.ApkInformationRepository
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.feature.apk.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

internal fun apkFixtureIcon(): ApkIconSource {
    val bitmap = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until 64) for (x in 0 until 64) bitmap.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xff448aff.toInt() else 0xffeeeeee.toInt())
    return ApkIconSource(ByteArrayOutputStream().also { ImageIO.write(bitmap, "png", it) }.toByteArray())
}
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class ApkInformationUiTest {
    @Test fun lightResult() = result("LIGHT")
    @Test fun darkResult() = result("DARK")
    private fun result(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 60.seconds) {
        prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val copied = mutableListOf<String>()
        var decodedIcons = 0
        val repository = FixtureApkRepository().apply { images = mapOf("res/icon.png" to apkFixtureIcon()) }
        val container = koinApplication { modules(desktopModules() + module {
            single<ApkInformationRepository> { repository }
            single<ApkIconDecoder> { ApkIconDecoder { source ->
                decodedIcons++
                org.tool.kit.platform.JvmApkIconDecoder(kotlinx.coroutines.Dispatchers.IO).decode(source)
            } }
            single<org.tool.kit.feature.app.ClipboardWriter> { org.tool.kit.feature.app.ClipboardWriter { copied += it } }
        }) }
        lateinit var vm: ApkInformationViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<ApkInformationViewModel>()
                    App()
                    SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            runOnIdle { vm.onIntent(ApkInformationIntent.ReadApk("/中文 空格.apk")) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("测试 APK").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("app icon").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            fun capture(name: String) {
                val bitmap = onRoot().captureToImage()
                val pixels = bitmap.toPixelMap()
                val output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) output.setRGB(x, y, pixels[x, y].toArgb())
                val file = File(System.getProperty("migration.renderOutput"), "phase4c/${theme.lowercase()}/$name.png")
                file.parentFile.mkdirs(); ImageIO.write(output, "png", file)
            }
            capture("result-top")
            onNode(hasScrollAction()).performScrollToNode(hasText("android.permission.CAMERA"))
            capture("result-bottom")
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNodeWithText("测试 APK").assertExists()
            runOnIdle {
                repository.output = "application: label='无图标 APK' icon='adaptive.xml'"
                repository.xml = null
                vm.onIntent(ApkInformationIntent.ReadApk("/no-icon.apk"))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("无图标 APK").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithContentDescription("app icon").assertDoesNotExist()
            onNodeWithText("android.permission.CAMERA").assertDoesNotExist()
            capture("no-icon-permission-channel")
            onNodeWithText("无图标 APK").performClick()
            waitUntil(timeoutMillis = 5_000) { copied.isNotEmpty() }
            assertEquals(listOf("无图标 APK"), copied)
            assertEquals(1, decodedIcons, "Recomposition, scrolling and navigation do not decode icons again")
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
