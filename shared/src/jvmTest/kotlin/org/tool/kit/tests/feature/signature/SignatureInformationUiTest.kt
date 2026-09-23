package org.tool.kit.tests.feature.signature

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
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.SignatureRepository
import org.tool.kit.domain.signature.*
import org.tool.kit.feature.app.ClipboardWriter
import org.tool.kit.feature.signature.*
import org.tool.kit.tests.support.EmptyKeys
import org.tool.kit.tests.support.prepareTestPreferences
import org.tool.kit.tests.support.signatureFixture

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class SignatureInformationUiTest {
    @Test fun lightResult() = result("LIGHT")
    @Test fun darkResult() = result("DARK")
    private fun result(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 60.seconds) {
        prepareTestPreferences(File(checkNotNull(System.getProperty("test.fixtureRoot"))), theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val copied = mutableListOf<String>()
        val visible = mutableStateOf(true)
        val container = koinApplication { modules(desktopModules() + module {
            single<ClipboardWriter> { ClipboardWriter { copied += it } }
            single<KeyStoreRepository> { object : KeyStoreRepository by EmptyKeys {
                override suspend fun loadAliases(path: String, password: String): List<String>? =
                    if (password == "fixture-only") listOf("first", "second") else null
            } }
            single<SignatureRepository> { object : SignatureRepository {
                override suspend fun verifyApk(path: String) = Result.success(signatureFixture)
                override suspend fun verifyCertificate(path: String, password: String, alias: String) = Result.success(signatureFixture.copy(isApk = false, isSuccess = true))
            } }
        }) }
        lateinit var vm: SignatureInformationViewModel
        container.koin.loadModules(listOf(module { viewModel { SignatureInformationViewModel(get(), get(), get(), get(), get()).also { vm = it } } }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    if (visible.value) App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { vm.onIntent(SignatureInformationIntent.VerifyApk("/fixture.apk")) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Valid APK signature V1 found").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            waitForIdle()
            onNode(hasScrollAction()).performScrollToNode(hasText("SHA-256"))
            waitForIdle()
            onNodeWithText("SHA-256").performClick()
            waitUntil(timeoutMillis = 5_000) { copied.isNotEmpty() }
            assertEquals(signatureFixture.data.single().sha256, copied.single())
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("签名信息") and hasClickAction()).performClick()
            onNodeWithText("Valid APK signature V1 found").assertExists()
            runOnIdle { vm.onIntent(SignatureInformationIntent.KeyStoreSelected("fixture.jks")) }
            onNodeWithText("密钥库密码验证").assertExists()
            onNode(hasSetTextAction() and hasText("密钥库密码")).performTextReplacement("fixture-only")
            waitUntil(timeoutMillis = 5_000) { vm.uiState.value.passwordDialog?.selectedAlias == "first" }
            onNodeWithText("密钥别名").assertTextContains("first")
            runOnIdle { visible.value = false }
            waitForIdle()
            assertNull(vm.uiState.value.passwordDialog)
            assertEquals(signatureFixture.path, vm.uiState.value.result!!.path)
            runOnIdle { visible.value = true }
            waitForIdle()
            runOnIdle { vm.onIntent(SignatureInformationIntent.KeyStoreSelected("fixture.jks")) }
            onNode(hasSetTextAction() and hasText("密钥库密码")).assertTextContains("")
            assertEquals("", vm.uiState.value.passwordDialog!!.password)
            onNode(hasSetTextAction() and hasText("密钥库密码")).performTextReplacement("fixture-only")
            waitUntil(timeoutMillis = 5_000) { vm.uiState.value.passwordDialog?.selectedAlias == "first" }
            onNodeWithText("确认").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Valid KeyStore Signature V1 found").fetchSemanticsNodes().isNotEmpty() }
            assertNull(vm.uiState.value.passwordDialog)
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
