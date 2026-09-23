package org.tool.kit.tests.feature.junk

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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import org.tool.kit.model.JunkMode
import org.tool.kit.tests.support.prepareTestPreferences

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class JunkCodeUiTest {
    @Test fun lightForms() = forms("LIGHT")
    @Test fun darkForms() = forms("DARK")

    private fun forms(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        val fixture = File(checkNotNull(System.getProperty("test.fixtureRoot")))
        prepareTestPreferences(fixture, theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val requests = mutableListOf<GenerateJunkCodeRequest>()
        val gate = CompletableDeferred<GeneratedJunkCode>()
        val container = koinApplication { modules(desktopModules() + module {
            single<JunkCodeRepository> { JunkCodeRepository { requests += it; gate.await() } }
        }) }
        lateinit var vm: JunkCodeViewModel
        container.koin.loadModules(listOf(module { viewModel { JunkCodeViewModel(get(), get(), get(), get(), get(), get()).also { vm = it } } }))
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    App()
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("垃圾代码").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            fun bottom() { onAllNodes(hasScrollAction() and !hasSetTextAction()).onLast().performScrollToNode(hasText("开始生成")) }
            fun top() { onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(0) }
            waitForIdle()
            bottom(); waitForIdle()
            top()
            runOnIdle {
                listOf(PackageCountChanged(""), ActivityCountChanged(""), ResPrefixChanged(""), PackageNameChanged(""), SuffixChanged("")).forEach(vm::onIntent)
            }
            waitForIdle()
            runOnIdle { vm.onIntent(ModeChanged(JunkMode.MULTI)) }
            waitForIdle()
            bottom(); waitForIdle()
            top()
            runOnIdle { listOf(OutputDirChanged(""), AarCountChanged(""), LeastPackagesChanged(""), MaximumPackagesChanged(""), LeastActivitiesChanged(""), MaximumActivitiesChanged("")).forEach(vm::onIntent) }
            waitForIdle()
            // Exercise form callbacks, retained drafts, and the page loading state.
            runOnIdle {
                listOf(PackageNameChanged("com.fixture"), SuffixChanged("plugin"), ResPrefixChanged("fixture_"), PackageCountChanged("1"), ActivityCountChanged("1"),
                    OutputDirChanged("batch"), AarCountChanged("2"), LeastPackagesChanged("1"), MaximumPackagesChanged("1"), LeastActivitiesChanged("1"), MaximumActivitiesChanged("1")).forEach(vm::onIntent)
            }
            onNode(hasText("单AAR模式") and hasClickAction()).performClick()
            onNodeWithText("包名").performTextReplacement("org.fixture.ui")
            onNodeWithText("后缀").performTextReplacement("part.one")
            onNode(hasText("APK信息") and hasClickAction()).performClick()
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            onNodeWithText("后缀").assertTextContains("part.one")
            bottom()
            onNodeWithText("开始生成").performClick()
            waitUntil(timeoutMillis = 10_000) { vm.uiState.value.busy && requests.size == 1 }
            onAllNodesWithContentDescription("Lottie animation").onFirst().assertExists()
            val request = requests.single()
            assertEquals(JunkConfiguration.Single("org.fixture.ui.part.one", 1, 1, "fixture_"), request.configuration)
            runOnIdle { gate.complete(GeneratedJunkCode("fixture-result.aar", listOf("fixture-result.aar"), 123)) }
            waitUntil(timeoutMillis = 10_000) { !vm.uiState.value.busy }
            onNodeWithText("跳转").assertExists()
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
