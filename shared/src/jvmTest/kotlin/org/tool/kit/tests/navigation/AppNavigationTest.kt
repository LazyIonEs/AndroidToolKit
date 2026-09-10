package org.tool.kit.tests.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import java.io.File
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.tests.support.prepareTestPreferences

/** Navigation and retained drafts in both application themes. */
@OptIn(ExperimentalTestApi::class, com.russhwolf.settings.ExperimentalSettingsApi::class)
class AppNavigationTest {
    private val fixtureRoot = File(checkNotNull(System.getProperty("test.fixtureRoot")))
    private lateinit var container: KoinApplication

    @Before fun startIsolatedContainer() {
        container = koinApplication { modules(desktopModules()) }
    }

    @After fun closeIsolatedContainer() = container.close()

    @Test fun lightNavigationRendersAllNinePages() = verifyNavigation("LIGHT")

    @Test fun darkNavigationRendersAllNinePages() = verifyNavigation("DARK")

    private fun verifyNavigation(theme: String) = runDesktopComposeUiTest(
        width = 800, height = 572, testTimeout = 45.seconds
    ) {
        prepareTestPreferences(fixtureRoot, theme)
        setContent { KoinIsolatedContext(container) { App() } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
        val pages = listOf(
            "签名信息", "APK信息",
            "APK签名", "签名生成",
            "APK生成", "垃圾代码",
            "图标生成", "缓存清理", "设置"
        )
        pages.forEach { label ->
            onNode(hasText(label) and hasClickAction()).performClick().assertIsSelected()
        }
    }

    @Test fun signingAndKeyStoreDraftsSurviveTopLevelNavigation() = runDesktopComposeUiTest(
        width = 800, height = 572, testTimeout = 45.seconds
    ) {
        prepareTestPreferences(fixtureRoot, "LIGHT")
        setContent { KoinIsolatedContext(container) { App() } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("APK签名") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("输出文件前缀(选填)"))
            .performTextReplacement("navigation-draft")
        onNode(hasText("签名生成") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("密钥文件名称"))
            .performTextReplacement("navigation-test.jks")
        onNode(hasText("APK签名") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("输出文件前缀(选填)"))
            .assertTextContains("navigation-draft")
        onNode(hasText("签名生成") and hasClickAction()).performClick()
        onNode(hasSetTextAction() and hasText("密钥文件名称"))
            .assertTextContains("navigation-test.jks")
    }

}
