package org.tool.kit.tests.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlin.test.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.tool.kit.feature.apk.navigation.ApkInformationNavKey
import org.tool.kit.feature.cleaner.navigation.CleanerNavKey
import org.tool.kit.feature.signature.navigation.SignatureInformationNavKey
import org.tool.kit.navigation.Navigator
import org.tool.kit.navigation.rememberNavigationState
import org.tool.kit.navigation.toEntries
import org.tool.kit.tests.support.release

/** Exercises the production multi-stack decorators, including returning to the start page. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class PageNavigationLifecycleTest {
    @Test fun entriesRetainDraftsAndJobsAcrossTabsButClearWhenPoppedOrWindowCloses() {
        val window = DefaultArchitectureComponentsOwner(enforceMainThread = false)
        window.setLifecycleState(Lifecycle.State.RESUMED)
        val created = mutableListOf<NavigationProbeViewModel>()
        val observed = mutableMapOf<NavKey, NavigationProbeViewModel>()
        val owners = mutableMapOf<NavKey, ViewModelStoreOwner>()
        val container = koinApplication {
            modules(module { viewModel { NavigationProbeViewModel().also { created += it } } })
        }
        try {
            runDesktopComposeUiTest(width = 800, height = 572) {
                lateinit var navigator: Navigator
                val windowOpen = mutableStateOf(true)
                @Composable fun Page(key: NavKey) {
                    val vm = koinViewModel<NavigationProbeViewModel>()
                    val owner = checkNotNull(LocalViewModelStoreOwner.current)
                    var text by rememberSaveable { mutableStateOf("") }
                    Column {
                        Text(key.toString())
                        BasicTextField(text, { text = it }, Modifier.testTag("draft"))
                    }
                    SideEffect { observed[key] = vm; owners[key] = owner }
                }
                setContent {
                    if (!windowOpen.value) return@setContent
                    CompositionLocalProvider(LocalViewModelStoreOwner provides window) {
                        KoinIsolatedContext(container) {
                            val state = rememberNavigationState(SignatureInformationNavKey,
                                linkedSetOf(SignatureInformationNavKey, CleanerNavKey))
                            val currentNavigator = remember(state) { Navigator(state) }
                            val entries = entryProvider {
                                entry<SignatureInformationNavKey> { Page(it) }
                                entry<CleanerNavKey> { Page(it) }
                                entry<ApkInformationNavKey> { Page(it) }
                            }
                            NavDisplay(entries = state.toEntries(entries), onBack = currentNavigator::goBack)
                            SideEffect { navigator = currentNavigator }
                        }
                    }
                }
                waitForIdle()
                assertEquals(1, created.size, "Unvisited entries must not instantiate a ViewModel")
                val start = observed.getValue(SignatureInformationNavKey)
                assertNotSame(window, owners.getValue(SignatureInformationNavKey))
                onNodeWithTag("draft").performTextReplacement("start draft")
                runOnIdle { navigator.navigate(CleanerNavKey) }
                waitForIdle()
                val cleaner = observed.getValue(CleanerNavKey)
                assertNotSame(start, cleaner)
                assertNotSame(owners.getValue(SignatureInformationNavKey), owners.getValue(CleanerNavKey))
                onNodeWithTag("draft").performTextReplacement("cleaner draft")
                runOnIdle { navigator.navigate(ApkInformationNavKey) }
                waitForIdle()
                val detail = observed.getValue(ApkInformationNavKey)
                onNodeWithTag("draft").performTextReplacement("detail draft")
                runOnIdle { navigator.navigate(SignatureInformationNavKey) }
                waitForIdle()
                onNodeWithTag("draft").assertTextEquals("start draft")
                assertSame(start, observed.getValue(SignatureInformationNavKey))
                assertTrue(created.none { it.cleared || it.cancelled })
                runOnIdle { navigator.navigate(CleanerNavKey) }
                waitForIdle()
                onNodeWithTag("draft").assertTextEquals("detail draft")
                assertSame(detail, observed.getValue(ApkInformationNavKey))
                assertEquals(3, created.size)
                runOnIdle { navigator.goBack() }
                waitUntil { detail.cleared && detail.cancelled }
                onNodeWithTag("draft").assertTextEquals("cleaner draft")
                assertSame(cleaner, observed.getValue(CleanerNavKey))
                assertFalse(cleaner.cleared)
                runOnIdle { navigator.navigate(SignatureInformationNavKey) }
                waitForIdle()
                runOnIdle { navigator.navigate(CleanerNavKey) }
                waitForIdle()
                onNodeWithTag("draft").assertTextEquals("cleaner draft")
                assertEquals(3, created.size)
                // Closing a desktop window also disposes its composition. Active entries release
                // their store tokens on disposal, after the parent has requested clearing.
                runOnIdle {
                    window.setLifecycleState(Lifecycle.State.DESTROYED)
                    windowOpen.value = false
                }
                waitUntil { created.all { it.cleared && it.cancelled } }
            }
        } finally {
            window.setLifecycleState(Lifecycle.State.DESTROYED)
            container.close()
        }
    }
}

class NavigationProbeViewModel : ViewModel() {
    var cleared = false
        private set
    var cancelled = false
        private set
    init {
        addCloseable { cleared = true }
        viewModelScope.launch {
            try { awaitCancellation() } finally { cancelled = true }
        }
    }
}
