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
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.*
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
        val builds = mutableListOf<BuildApkRequest>()
        val container = koinApplication { modules(desktopModules() + module {
            single<ApkBuildWorkspaces> { object : ApkBuildWorkspaces {
                override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T =
                    block(ApkBuildWorkspace("fixture-workspace", "${request.outputDirectory}/${request.outputFileName}"))
            } }
            single<ApkToolRepository> { object : ApkToolRepository {
                override suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession {
                    builds += request
                    return object : ApkBuildSession {
                        override suspend fun updateManifest() {}
                        override suspend fun updateAppName() {}
                        override suspend fun copyIcon() {}
                        override suspend fun saveMetadata(versionCode: Int) {}
                        override suspend fun build() {}
                        override suspend fun outputSize() = 1000L
                    }
                }
            } }
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
        lateinit var vm: org.tool.kit.feature.apk.ApkToolViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<org.tool.kit.feature.apk.ApkToolViewModel>()
                    App()
                    SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            onNode(hasText("APK生成") and hasClickAction()).performClick()
            runOnIdle {
                listOf(ApkToolIntent.EnableSignChanged(true), ApkToolIntent.IconPathChanged(fixture.path),
                    ApkToolIntent.AppNameChanged("中文 Fixture"), ApkToolIntent.PackageNameChanged("org.fixture.phase6"),
                    ApkToolIntent.TargetSdkChanged("32"), ApkToolIntent.MinSdkChanged("23"), ApkToolIntent.VersionCodeChanged("12"),
                    ApkToolIntent.VersionNameChanged("2.3"), ApkToolIntent.KeyPathChanged(fixture.path),
                    ApkToolIntent.StorePasswordChanged("fixture-only"), ApkToolIntent.AliasPasswordChanged("fixture-only")).forEach(vm::onIntent)
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
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
            runOnIdle { assertEquals("", vm.uiState.value.form.credentials.aliasPassword) }
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("APK生成") and hasClickAction()).performClick()
            runOnIdle {
                vm.onIntent(ApkToolIntent.OutputPathChanged("/missing output"))
                vm.onIntent(ApkToolIntent.IconPathChanged("/missing icon.png"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
            capture("errors-top")
            onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("开始生成"))
            onNodeWithText("开始生成").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("请检查Error项").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(requests.isEmpty()); assertTrue(builds.isEmpty())
            runOnIdle {
                vm.onIntent(ApkToolIntent.OutputPathChanged(fixture.parentFile.resolve("output").path))
                vm.onIntent(ApkToolIntent.IconPathChanged(fixture.path))
                vm.onIntent(ApkToolIntent.AliasPasswordChanged("fixture-only"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("请检查Error项").fetchSemanticsNodes().isEmpty() }
            onNodeWithText("开始生成").performClick()
            waitUntil(timeoutMillis = 10_000) { requests.size == 1 && !vm.uiState.value.busy }
            assertEquals("中文 Fixture", builds.single().appName)
            assertEquals("12", builds.single().versionCode)
            assertEquals("second", requests.single().credentials.alias)
            assertEquals(org.tool.kit.domain.signing.ApkSigningPolicy.V3, requests.single().policy)
            assertTrue(requests.single().inputPath.endsWith("中文 Fixture.apk"))
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
