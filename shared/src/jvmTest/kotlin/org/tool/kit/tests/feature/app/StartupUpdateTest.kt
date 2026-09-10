package org.tool.kit.tests.feature.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.App
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.EmptyKeys
import org.tool.kit.tests.support.ImmediatePreferencesRepository

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, com.russhwolf.settings.ExperimentalSettingsApi::class)
class StartupUpdateTest {
    @Test fun enabledStartupWaitsForReadyAndRunsOnlyOnceAcrossPreferenceAndThemeChanges() = startup(true)
    @Test fun disabledStartupStaysSilentEvenIfTheSettingIsEnabledLater() = startup(false)

    private fun startup(enabled: Boolean) = runDesktopComposeUiTest(width = 800, height = 572) {
        val preferences = ImmediatePreferencesRepository(PreferencesSnapshot(ready = false))
        val checks = AtomicInteger()
        val owner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        owner.setLifecycleState(Lifecycle.State.RESUMED)
        val container = koinApplication {
            modules(desktopModules() + module {
                single<PreferencesRepository> { preferences }
                single<StorageRepository> { AllPathsExist }
                single<KeyStoreRepository> { EmptyKeys }
                single<UpdateRepository> {
                    object : UpdateRepository {
                        override suspend fun check(): UpdateCheckResult { checks.incrementAndGet(); return UpdateCheckResult.Latest }
                        override suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult = error("Startup must never download")
                    }
                }
            })
        }
        try {
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    KoinIsolatedContext(container) { App() }
                }
            }
            waitForIdle()
            assertEquals(0, checks.get(), "Default true must not start a request before loading")
            runOnIdle { preferences.completeInitialLoad(enabled) }
            if (enabled) waitUntil { checks.get() == 1 } else waitForIdle()
            runOnIdle {
                preferences.change(PreferenceChange.Theme(ThemePreference.DARK))
                preferences.change(PreferenceChange.AlwaysShowLabel(true))
                preferences.change(PreferenceChange.StartCheckUpdate(!enabled))
            }
            waitForIdle()
            onNodeWithText("设置").performClick()
            waitForIdle()
            assertEquals(if (enabled) 1 else 0, checks.get())
        } finally { runOnIdle { owner.setLifecycleState(Lifecycle.State.DESTROYED); container.close() } }
    }
}
