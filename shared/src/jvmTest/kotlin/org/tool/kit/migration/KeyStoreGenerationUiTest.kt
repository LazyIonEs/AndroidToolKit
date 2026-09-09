package org.tool.kit.migration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.InternalComposeUiApi
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
import org.tool.kit.domain.keystore.GenerateKeyStoreOutcome
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.keystore.*
import java.io.File
import kotlin.math.abs
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class KeyStoreGenerationUiTest {
    @Test fun actualFieldsSubmitTheExpectedRequestAndWindowOwnerKeepsTheDraftAcrossThemeAndNavigation() =
        runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 60.seconds) {
            prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), "LIGHT")
            val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
            owner.setLifecycleState(Lifecycle.State.RESUMED)
            val keys = ControlledKeyGeneration()
            val container = koinApplication {
                modules(desktopModules() + module {
                    single<KeyStoreRepository> { keys }
                    single<StorageRepository> { AllPathsExist }
                })
            }
            lateinit var vm: KeyStoreGenerationViewModel
            try {
                setContent {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        KoinIsolatedContext(container) {
                            val current = koinViewModel<KeyStoreGenerationViewModel>()
                            App()
                            SideEffect { vm = current }
                        }
                    }
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("签名生成").fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("签名生成") and hasClickAction()).performClick()
                val original = vm
                fun enter(label: String, value: String) {
                    val selector = hasSetTextAction() and hasText(label)
                    onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(selector)
                    onNode(selector).performTextReplacement(value)
                }
                fun confirmNear(label: String, value: String) {
                    val rowY = onNode(hasText(label) and hasSetTextAction()).fetchSemanticsNode().boundsInRoot.center.y
                    val confirmations = onAllNodes(hasText("确认密码") and hasSetTextAction())
                    val nodes = confirmations.fetchSemanticsNodes()
                    val index = nodes.indices.minBy { abs(nodes[it].boundsInRoot.center.y - rowY) }
                    confirmations[index].performTextReplacement(value)
                }
                enter("密钥输出路径", "/ui fixture")
                enter("密钥文件名称", " ui fixture.keystore")
                enter("密钥密码", "ui store password")
                confirmNear("密钥密码", "ui store password")
                enter("密钥别名", "ui alias")
                enter("别名密码", "ui alias password")
                confirmNear("别名密码", "ui alias password")
                enter("有效期（单位：年）", "002")
                enter("作者名称", "UI Author")
                enter("组织单位", "UI Unit")
                enter("组织", "UI Organization")
                enter("城市或地区", "UI City")
                enter("省份", "UI Province")
                enter("国家编码", "CN")
                onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("创建密钥库"))
                onNodeWithText("创建密钥库").performClick()
                waitUntil { keys.requests.size == 1 }
                runOnIdle { assertTrue(vm.uiState.value.busy) }
                val request = keys.requests.single()
                assertEquals("/ui fixture", request.outputDirectory)
                assertEquals(" ui fixture.keystore", request.fileName)
                assertEquals("ui store password", request.storePassword)
                assertEquals("ui alias password", request.aliasPassword)
                assertEquals("ui alias", request.alias)
                assertEquals("002", request.validityYears)
                assertEquals("UI Author", request.authorName)
                assertEquals("UI Unit", request.organizationalUnit)
                assertEquals("UI Organization", request.organization)
                assertEquals("UI City", request.city)
                assertEquals("UI Province", request.province)
                assertEquals("CN", request.countryCode)
                runOnIdle { keys.results.single().complete(GenerateKeyStoreOutcome.Success("/ui fixture/ ui fixture.keystore")) }
                waitUntil { onAllNodesWithText("创建签名成功，点击跳转至签名文件").fetchSemanticsNodes().isNotEmpty() }
                runOnIdle {
                    assertFalse(vm.uiState.value.busy)
                    container.koin.get<PreferencesRepository>().change(PreferenceChange.Theme(ThemePreference.DARK))
                }
                onNode(hasText("APK签名") and hasClickAction()).performClick()
                onNode(hasText("签名生成") and hasClickAction()).performClick()
                runOnIdle { assertSame(original, vm) }
                onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(hasText("密钥文件名称") and hasSetTextAction())
                onNode(hasText("密钥文件名称") and hasSetTextAction()).assertTextContains(" ui fixture.keystore")
                assertEquals("ui alias password", vm.uiState.value.form.keyStoreAlisaPassword)
                assertEquals("UI Province", vm.uiState.value.form.province)
                assertEquals(1, keys.requests.size)
            } finally {
                runOnIdle { owner.setLifecycleState(Lifecycle.State.DESTROYED); container.close() }
            }
        }
}
