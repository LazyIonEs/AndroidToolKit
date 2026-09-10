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
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.model.JunkMode
import org.tool.kit.vm.MainViewModel
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
        val container = koinApplication { modules(desktopModules()) }
        lateinit var vm: MainViewModel
        try {
            setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                KoinIsolatedContext(container) {
                    val current = koinViewModel<MainViewModel>()
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
                val form = vm.junkCodeInfoState.copy(packageCount = "", activityCountPerPackage = "", resPrefix = "")
                form.packageName = ""; form.suffix = ""
                vm.updateJunkCodeInfo(form)
            }
            capture("single-errors")
            runOnIdle { vm.saveJunkMode(JunkMode.MULTI) }
            capture("multi-top")
            bottom(); capture("multi-bottom")
            top()
            runOnIdle { vm.updateJunkCodeInfo(vm.junkCodeInfoState.copy(outputDir = "", aarCount = "", leastPackageCount = "", maximumPackageCount = "", leastActivityCountPerPackage = "", maximumActivityCountPerPackage = "")) }
            capture("multi-errors")
        } finally { owner.viewModelStore.clear(); container.close() }
    }
}
