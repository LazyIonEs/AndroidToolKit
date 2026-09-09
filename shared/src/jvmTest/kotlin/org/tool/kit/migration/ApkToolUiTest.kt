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
import org.tool.kit.feature.signature.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class ApkToolUiTest {
    @Test fun lightResult() = result("LIGHT")
    @Test fun darkResult() = result("DARK")
    private fun result(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 60.seconds) {
        prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val fixture = File(System.getProperty("migration.fixtureRoot"), "building fixture.png").apply { writeText("fixture") }
        val requests = mutableListOf<org.tool.kit.domain.signing.SignApkRequest>()
        val container = koinApplication { modules(desktopModules() + module {
            single<org.tool.kit.domain.repository.ApkSigningRepository> { org.tool.kit.domain.repository.ApkSigningRepository { request ->
                requests += request
                org.tool.kit.domain.signing.SignApkOutcome.Success("/fixture/signed.apk", true)
            } }
            single<org.tool.kit.domain.repository.KeyStoreRepository> { object : org.tool.kit.domain.repository.KeyStoreRepository {
                override suspend fun loadAliases(path: String, password: String) = listOf("fixture", "second")
                override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String) = password == "fixture-only"
                override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest) = error("unused")
            } }
        }) }
        lateinit var vm: org.tool.kit.vm.MainViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<org.tool.kit.vm.MainViewModel>()
                    App()
                    SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            onNode(hasText("APK生成") and hasClickAction()).performClick()
            runOnIdle {
                vm.updateApkToolInfo(vm.apkToolInfoState.copy(enableSign = true, icon = fixture.path,
                    appName = "中文 Fixture", packageName = "org.fixture.phase6", targetSdkVersion = "32",
                    minSdkVersion = "23", versionCode = "12", versionName = "2.3",
                    _keyStorePath = fixture.path, keyStorePassword = "fixture-only",
                    keyStoreAlisaList = arrayListOf("fixture", "second"), keyStoreAlisaPassword = "fixture-only"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.apkToolValidation.value.pending &&
                !vm.hasPendingPathChecks(org.tool.kit.vm.LegacyPathField.APK_TOOL_OUTPUT,
                    org.tool.kit.vm.LegacyPathField.APK_TOOL_ICON, org.tool.kit.vm.LegacyPathField.APK_TOOL_KEYSTORE) }
            waitForIdle()
            fun capture(name: String) {
                val bitmap = onRoot().captureToImage()
                val pixels = bitmap.toPixelMap()
                val output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) output.setRGB(x, y, pixels[x, y].toArgb())
                val file = File(System.getProperty("migration.renderOutput"), "phase6/${theme.lowercase()}/$name.png")
                file.parentFile.mkdirs(); ImageIO.write(output, "png", file)
            }
            capture("enabled-top")
            onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("开始生成"))
            capture("enabled-bottom")
            onNode(hasText("密钥别名") and hasClickAction()).performClick()
            onNodeWithText("second").assertExists().performClick()
            runOnIdle { assertEquals("", vm.apkToolInfoState.keyStoreAlisaPassword) }
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("APK生成") and hasClickAction()).performClick()
            runOnIdle {
                vm.updateApkToolInfo(vm.apkToolInfoState.copy(outputPath = "/missing output", icon = "/missing icon.png"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.hasPendingPathChecks(
                org.tool.kit.vm.LegacyPathField.APK_TOOL_OUTPUT, org.tool.kit.vm.LegacyPathField.APK_TOOL_ICON) }
            capture("errors-top")
            onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("开始生成"))
            onNodeWithText("开始生成").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("请检查Error项").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(requests.isEmpty())
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
