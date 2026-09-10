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
import kotlinx.coroutines.CompletableDeferred
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import kotlin.test.*
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.model.JunkMode
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

/** Same window, data and scroll positions before and after the Phase 7B cutover. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalSettingsApi::class)
class JunkCodeUiTest {
    @Test fun lightForms() = forms("LIGHT")
    @Test fun darkForms() = forms("DARK")

    private fun forms(theme: String) = runDesktopComposeUiTest(width = 800, height = 572, testTimeout = 90.seconds) {
        val fixture = File(checkNotNull(System.getProperty("migration.fixtureRoot")))
        prepareBaselinePreferences(fixture, theme)
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val requests = mutableListOf<GenerateJunkCodeRequest>()
        val gate = CompletableDeferred<GeneratedJunkCode>()
        val container = koinApplication { modules(desktopModules() + module {
            single<JunkCodeRepository> { JunkCodeRepository { requests += it; gate.await() } }
        }) }
        lateinit var vm: JunkCodeViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<JunkCodeViewModel>()
                    App()
                    SideEffect { vm = current }
                }
            } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("垃圾代码").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("垃圾代码") and hasClickAction()).performClick()
            fun capture(name: String) {
                waitForIdle()
                val bitmap = onAllNodes(isRoot()).onLast().captureToImage()
                val pixels = bitmap.toPixelMap()
                val out = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) out.setRGB(x, y, pixels[x, y].toArgb())
                val file = File(System.getProperty("migration.renderOutput"), "phase7b/${theme.lowercase()}/$name.png")
                file.parentFile.mkdirs(); ImageIO.write(out, "png", file)
            }
            fun bottom() { onAllNodes(hasScrollAction() and !hasSetTextAction()).onLast().performScrollToNode(hasText("开始生成")) }
            fun top() { onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(0) }
            capture("single-top")
            bottom(); capture("single-bottom")
            top()
            runOnIdle {
                listOf(PackageCountChanged(""), ActivityCountChanged(""), ResPrefixChanged(""), PackageNameChanged(""), SuffixChanged("")).forEach(vm::onIntent)
            }
            capture("single-errors")
            runOnIdle { vm.onIntent(ModeChanged(JunkMode.MULTI)) }
            capture("multi-top")
            bottom(); capture("multi-bottom")
            top()
            runOnIdle { listOf(OutputDirChanged(""), AarCountChanged(""), LeastPackagesChanged(""), MaximumPackagesChanged(""), LeastActivitiesChanged(""), MaximumActivitiesChanged("")).forEach(vm::onIntent) }
            capture("multi-errors")
            // Drive the real form callbacks and global Loading after the unchanged baseline scenes.
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
