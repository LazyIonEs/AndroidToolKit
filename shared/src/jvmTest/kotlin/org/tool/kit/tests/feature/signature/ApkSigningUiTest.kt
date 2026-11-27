package org.tool.kit.tests.feature.signature

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.toPixelMap
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
import org.tool.kit.domain.repository.SigningProfileRepository
import org.tool.kit.domain.signing.SigningCredentials
import org.tool.kit.feature.apk.*
import org.tool.kit.feature.signature.*
import org.tool.kit.tests.support.TestSigningProfiles
import org.tool.kit.tests.support.prepareTestPreferences

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class ApkSigningUiTest {
    @Test fun lightResult() = result("LIGHT")
    @Test fun darkResult() = result("DARK")
    private fun result(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 60.seconds) {
        prepareTestPreferences(File(checkNotNull(System.getProperty("test.fixtureRoot"))), theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val fixture = File(System.getProperty("test.fixtureRoot"), "signing fixture.apk").apply { writeText("fixture") }
        val profiles = TestSigningProfiles(listOf(SigningCredentials("/another-key.jks", "store", "alternate", "alias")))
        val requests = mutableListOf<org.tool.kit.domain.signing.SignApkRequest>()
        val container = koinApplication { modules(desktopModules() + module {
            single<SigningProfileRepository> { profiles }
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
        lateinit var vm: org.tool.kit.feature.signature.ApkSigningViewModel
        container.koin.loadModules(listOf(module { viewModel { org.tool.kit.feature.signature.ApkSigningViewModel(get(), get(), get(), get(), get(), get(), get()).also { vm = it } } }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            val scroll = onNode(hasScrollAction() and !hasSetTextAction())
            val saveProfileLabel = "签名后保存当前签名"
            val saveToggle = hasContentDescription(saveProfileLabel) and isToggleable()
            onNode(hasText("设置") and hasClickAction()).performClick()
            scroll.performScrollToNode(saveToggle)
            onNode(saveToggle).assertIsDisplayed().assertIsOff().performClick().assertIsOn()
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            val apkPicker = onAllNodesWithContentDescription("FolderOpen").onFirst()
            val keyPicker = onNodeWithContentDescription("选择密钥文件")
            fun buttonFill(picker: SemanticsNodeInteraction) = picker.assertIsDisplayed().captureToImage().toPixelMap().let {
                it[it.width / 2, it.height / 5]
            }
            assertEquals(buttonFill(apkPicker), buttonFill(keyPicker))
            assertEquals(apkPicker.getUnclippedBoundsInRoot().right, keyPicker.getUnclippedBoundsInRoot().right)
            runOnIdle {
                vm.onIntent(ApkSigningIntent.PolicyChanged(org.tool.kit.model.SignaturePolicy.V4))
                vm.onIntent(ApkSigningIntent.ApkPathChanged(fixture.path))
                vm.onIntent(ApkSigningIntent.PrefixChanged("中文 prefix"))
                vm.onIntent(ApkSigningIntent.KeyPathChanged(fixture.path))
                vm.onIntent(ApkSigningIntent.StorePasswordChanged("fixture-only"))
                vm.onIntent(ApkSigningIntent.AliasPasswordChanged("fixture-only"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
            waitForIdle()
            waitForIdle()
            onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("开始签名"))
            waitForIdle()
            onNode(hasText("密钥别名") and hasClickAction()).performClick()
            onNodeWithText("second").assertExists().performClick()
            runOnIdle { assertEquals("", vm.uiState.value.form.credentials.aliasPassword) }
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("APK签名") and hasClickAction()).performClick()
            runOnIdle {
                vm.onIntent(ApkSigningIntent.ApkPathChanged("/missing signing.apk"))
                vm.onIntent(ApkSigningIntent.OutputPathChanged("/missing output"))
                vm.onIntent(ApkSigningIntent.KeyPathChanged("/missing key.jks"))
                vm.onIntent(ApkSigningIntent.StorePasswordChanged("wrong"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
            waitForIdle()
            waitForIdle()
            onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("开始签名"))
            onNodeWithText("开始签名").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("请检查Error项").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(requests.isEmpty())
            runOnIdle {
                vm.onIntent(ApkSigningIntent.ApkPathChanged(fixture.path))
                vm.onIntent(ApkSigningIntent.OutputPathChanged(fixture.parentFile.resolve("output").path))
                vm.onIntent(ApkSigningIntent.KeyPathChanged(fixture.path))
                vm.onIntent(ApkSigningIntent.StorePasswordChanged("fixture-only"))
                vm.onIntent(ApkSigningIntent.AliasPasswordChanged("fixture-only"))
            }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.validation.pending }
            onNodeWithText("开始签名").performClick()
            waitUntil(timeoutMillis = 10_000) { requests.size == 1 && !vm.uiState.value.busy }
            assertEquals(fixture.path, requests.single().inputPath)
            assertEquals("中文 prefix", requests.single().prefix)
            assertEquals("fixture", requests.single().credentials.alias)
            assertEquals(org.tool.kit.domain.signing.ApkSigningPolicy.V4, requests.single().policy)

            onNodeWithText(saveProfileLabel).assertDoesNotExist()
            onNode(hasText("设置") and hasClickAction()).performClick()
            scroll.performScrollToNode(saveToggle)
            onNode(saveToggle).assertIsDisplayed().assertIsOn().performClick().assertIsOff()
            onNode(hasText("APK签名") and hasClickAction()).performClick()

            val keyPath = hasSetTextAction() and hasText("密钥文件路径")
            val savedItem = hasText("fixture") and hasText(fixture.path) and hasClickAction()
            scroll.performScrollToNode(keyPath)
            onNode(keyPath).performMouseInput { click(centerRight.copy(x = centerRight.x - 24f)) }
            onNodeWithText("alternate").assertIsDisplayed()
            onNode(savedItem).assertIsDisplayed()
            onNode(keyPath).performTextReplacement("ALTERNATE")
            onNode(savedItem).assertDoesNotExist()
            onNodeWithText("alternate").assertIsDisplayed()
            onNode(keyPath).performTextReplacement("SIGNING FIXTURE")
            onNodeWithText("alternate").assertDoesNotExist()
            onNode(savedItem).performClick()
            waitUntil(timeoutMillis = 10_000) {
                val state = vm.uiState.value
                state.form.credentials.path == fixture.path && !state.validation.pending
            }
            assertEquals(requests.single().credentials.storePassword, vm.uiState.value.form.credentials.storePassword)
            assertEquals(requests.single().credentials.aliasPassword, vm.uiState.value.form.credentials.aliasPassword)

            scroll.performScrollToNode(hasText("开始签名"))
            onNodeWithText("开始签名").performClick()
            waitUntil(timeoutMillis = 10_000) { requests.size == 2 && !vm.uiState.value.busy }
            assertEquals(listOf(requests.first().credentials), profiles.saves)
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
