package org.tool.kit.tests.feature.apk

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.repository.ApkInformationRepository
import org.tool.kit.feature.apk.*
import org.tool.kit.tests.support.FixtureApkRepository
import org.tool.kit.tests.support.apkFixtureIcon
import org.tool.kit.tests.support.prepareTestPreferences

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class ApkInformationUiTest {
    @Test fun lightResult() = result("LIGHT")
    @Test fun darkResult() = result("DARK")
    private fun result(theme: String) = runDesktopComposeUiTest(width = 1400, height = 840, testTimeout = 60.seconds) {
        prepareTestPreferences(File(checkNotNull(System.getProperty("test.fixtureRoot"))), theme)
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
        container.koin.loadModules(listOf(module { viewModel { ApkInformationViewModel(get(), get(), get(), get()).also { vm = it } } }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            runOnIdle { vm.onIntent(ApkInformationIntent.ReadApk("/中文 空格.apk")) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("测试 APK").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("查看应用图标").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            waitForIdle()
            onNodeWithTag("apk-tab-1").performClick()
            onNodeWithTag("apk-permission-android.permission.CAMERA").assertIsDisplayed()
            waitForIdle()
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNodeWithTag("apk-results-list").performScrollToIndex(0)
            onNodeWithText("测试 APK").assertExists()
            runOnIdle {
                repository.output = "application: label='无图标 APK' icon='adaptive.xml'"
                repository.xml = null
                vm.onIntent(ApkInformationIntent.ReadApk("/no-icon.apk"))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("无图标 APK").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithContentDescription("查看应用图标").assertDoesNotExist()
            onNodeWithText("android.permission.CAMERA").assertDoesNotExist()
            waitForIdle()
            onNodeWithText("无图标 APK").performClick()
            waitUntil(timeoutMillis = 5_000) { copied.isNotEmpty() }
            assertEquals(listOf("无图标 APK"), copied)
            assertEquals(1, decodedIcons, "Recomposition, scrolling and navigation do not decode icons again")
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
