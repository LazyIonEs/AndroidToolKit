package org.tool.kit.migration

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.viewmodel.resolveViewModel
import org.tool.kit.App
import org.tool.kit.app.AppSession
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.preferences.PreferenceChange
import org.tool.kit.di.desktopModules
import org.tool.kit.vm.MainViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.*

@OptIn(ExperimentalSettingsApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    ExperimentalTestApi::class, KoinInternalApi::class, InternalComposeUiApi::class)
class KoinLifecycleTest {
    @Test fun ownerKeysKeepViewModelsWhilePreferencesStayUniquePerContainer() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var settingsCreations = 0
        val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        val first = koinApplication {
            modules(desktopModules(settingsFactory = {
                settingsCreations++
                MapSettings().toFlowSettings(Dispatchers.Unconfined)
            }, dispatchers = dispatchers))
        }
        val second = koinApplication {
            modules(desktopModules(settingsFactory = { MapSettings().toFlowSettings(Dispatchers.Unconfined) },
                dispatchers = dispatchers))
        }
        val store = ViewModelStore()
        try {
            fun resolve(key: String) = resolveViewModel(MainViewModel::class, store, key,
                CreationExtras.Empty, scope = first.koin.scopeRegistry.rootScope)
            val a = resolve("window.a")
            val b = resolve("window.b")
            assertSame(a, resolve("window.a"))
            assertNotSame(a, b)
            val source = first.koin.get<PreferencesRepository>()
            assertSame(source, first.koin.get<PreferencesRepository>())
            assertNotSame(source, second.koin.get<PreferencesRepository>())
            assertSame(dispatchers, first.koin.get<AppDispatchers>())
            assertEquals(1, settingsCreations)
            runCurrent()
            val changed = source.state.value.userData.copy(defaultSignerSuffix = "-shared-di")
            source.change(PreferenceChange.SignerSuffix("-shared-di"))
            runCurrent()
            assertEquals(changed, a.userData.value)
            assertEquals(changed, b.userData.value)
        } finally {
            store.clear()
            first.close()
            second.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun recompositionKeepsTheWindowViewModelAndOwnerClearsItOnce() {
        prepareBaselinePreferences(File(checkNotNull(System.getProperty("migration.fixtureRoot"))), "LIGHT")
        val created = AtomicInteger()
        val cleared = AtomicInteger()
        // The software test runner only sends ON_DESTROY. Desktop windows instead
        // use setLifecycleState(DESTROYED), which also clears their ViewModelStore.
        val windowOwner = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        windowOwner.setLifecycleState(Lifecycle.State.RESUMED)
        val container = koinApplication {
            modules(desktopModules() + module {
                viewModel {
                    created.incrementAndGet()
                    MainViewModel(get(), get(), get(), get(), signApk = org.tool.kit.domain.usecase.SignApkUseCase { error("Unexpected signing") }).also { vm -> vm.addCloseable { cleared.incrementAndGet() } }
                }
            })
        }
        try {
            runDesktopComposeUiTest(width = 800, height = 572) {
                val revision = mutableIntStateOf(0)
                var composedRevision = -1
                val observed = mutableListOf<MainViewModel>()
                setContent {
                    val tick = revision.intValue
                    CompositionLocalProvider(LocalViewModelStoreOwner provides windowOwner) {
                        KoinIsolatedContext(container) {
                            App()
                            val vm = koinViewModel<MainViewModel>()
                            SideEffect {
                                composedRevision = tick
                                observed += vm
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("APK签名").fetchSemanticsNodes().isNotEmpty() }
                runOnIdle { revision.intValue++ }
                waitUntil { composedRevision == 1 }
                runOnIdle {
                    assertTrue(observed.size >= 2)
                    observed.forEach { assertSame(observed.first(), it) }
                    assertEquals(1, created.get())
                    assertEquals(0, cleared.get())
                }
            }
            windowOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            assertEquals(1, cleared.get(), "The window owner, not container shutdown, clears the VM")
        } finally {
            windowOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            container.close()
        }
        assertEquals(1, cleared.get())
    }

    @Test fun shutdownIsIdempotentAcrossCloseAndFinally() {
        val closes = AtomicInteger()
        val session = AppSession { closes.incrementAndGet() }
        List(8) { thread { session.shutdown() } }.forEach { it.join() }
        session.shutdown()
        assertEquals(1, closes.get())
    }
}
