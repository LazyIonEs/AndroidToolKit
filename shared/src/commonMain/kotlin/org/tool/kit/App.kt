package org.tool.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.apk.navigation.apkInformationEntry
import org.tool.kit.feature.apk.navigation.apkToolEntry
import org.tool.kit.feature.cleaner.ClearBuildBottom
import org.tool.kit.feature.cleaner.navigation.CleanerNavKey
import org.tool.kit.feature.cleaner.navigation.cleanerEntry
import org.tool.kit.feature.iconfactory.navigation.iconFactoryEntry
import org.tool.kit.feature.junk.navigation.JunkCodeNavKey
import org.tool.kit.feature.junk.navigation.junkCodeEntry
import org.tool.kit.feature.rememberAppState
import org.tool.kit.feature.setting.navigation.settingEntry
import org.tool.kit.feature.signature.navigation.apkSignatureEntry
import org.tool.kit.feature.signature.navigation.signatureGenerationEntry
import org.tool.kit.feature.signature.navigation.signatureInformationEntry
import org.tool.kit.feature.ui.LoadingAnimate
import org.tool.kit.feature.update.UpdateRoute
import org.tool.kit.feature.update.UpdateViewModel
import org.tool.kit.feature.update.UpdateIntent
import org.tool.kit.feature.settings.SettingsViewModel
import org.tool.kit.feature.app.*
import org.koin.compose.koinInject
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.navigation.Navigator
import org.tool.kit.navigation.TOP_LEVEL_NAV_ITEMS
import org.tool.kit.navigation.defaultTransitionSpec
import org.tool.kit.navigation.toEntries
import org.koin.compose.KoinContext
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme
import org.tool.kit.feature.signature.SignatureInformationViewModel
import org.tool.kit.feature.keystore.KeyStoreGenerationViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.vm.MainViewModel
import org.tool.kit.vm.UIState

private val logger = KotlinLogging.logger("App")

@Suppress("DEPRECATION") // Keep the explicit root wrapper required by the Koin 4.2.2 migration.
@Composable
fun App() {
    KoinContext(koin = getKoin()) {
        AppRoute()
    }
}

@Composable
private fun AppRoute() {
    val windowOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel = koinViewModel<MainViewModel>(viewModelStoreOwner = windowOwner)
    val junkCodeViewModel = koinViewModel<org.tool.kit.feature.junk.JunkCodeViewModel>(viewModelStoreOwner = windowOwner)
    val iconFactoryViewModel = koinViewModel<org.tool.kit.feature.iconfactory.IconFactoryViewModel>(viewModelStoreOwner = windowOwner)
    val apkToolViewModel = koinViewModel<org.tool.kit.feature.apk.ApkToolViewModel>(viewModelStoreOwner = windowOwner)
    val apkSigningViewModel = koinViewModel<org.tool.kit.feature.signature.ApkSigningViewModel>(viewModelStoreOwner = windowOwner)
    val apkInformationViewModel = koinViewModel<org.tool.kit.feature.apk.ApkInformationViewModel>(viewModelStoreOwner = windowOwner)
    val signatureViewModel = koinViewModel<SignatureInformationViewModel>(viewModelStoreOwner = windowOwner)
    val keyStoreViewModel = koinViewModel<KeyStoreGenerationViewModel>(viewModelStoreOwner = windowOwner)
    val appViewModel = koinViewModel<AppViewModel>(viewModelStoreOwner = windowOwner)
    val settingsViewModel = koinViewModel<SettingsViewModel>(viewModelStoreOwner = windowOwner)
    val updateViewModel = koinViewModel<UpdateViewModel>(viewModelStoreOwner = windowOwner)
    val shell by appViewModel.uiState.collectAsState()
    val themeConfig = shell.themeConfig
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    LaunchedEffect(Unit) {
        logger.info { "启动App, 应用版本号: ${BuildConfig.APP_VERSION}" }
    }

    AppTheme(useDarkTheme) {
        CompositionLocalProvider(LocalIsAppDarkTheme provides useDarkTheme) {
            MainContentScreen(viewModel, useDarkTheme, shell, settingsViewModel, updateViewModel, keyStoreViewModel, signatureViewModel, apkInformationViewModel, apkSigningViewModel, apkToolViewModel, iconFactoryViewModel, junkCodeViewModel, koinInject(), koinInject())
        }
    }

    LaunchedEffect(Unit) {
        if (appViewModel.startupUpdateEnabled()) {
            updateViewModel.onIntent(UpdateIntent.Check(showMessage = false))
        }
    }
}

val LocalIsAppDarkTheme = compositionLocalOf<Boolean> {
    error("LocalIsAppDarkTheme state should be initialized at runtime")
}

@Composable
fun WindowIcon() = painterResource(Res.drawable.icon)

/**
 * 主要模块
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainContentScreen(viewModel: MainViewModel, useDarkTheme: Boolean, shell: AppUiState,
    settingsViewModel: SettingsViewModel, updateViewModel: UpdateViewModel, keyStoreViewModel: KeyStoreGenerationViewModel, signatureViewModel: SignatureInformationViewModel, apkInformationViewModel: org.tool.kit.feature.apk.ApkInformationViewModel, apkSigningViewModel: org.tool.kit.feature.signature.ApkSigningViewModel, apkToolViewModel: org.tool.kit.feature.apk.ApkToolViewModel, iconFactoryViewModel: org.tool.kit.feature.iconfactory.IconFactoryViewModel, junkCodeViewModel: org.tool.kit.feature.junk.JunkCodeViewModel, effects: AppEffectSink, actions: DesktopActionHandler) {
    val junkBusy by junkCodeViewModel.busy.collectAsStateWithLifecycle()
    val iconFactoryBusy by iconFactoryViewModel.busy.collectAsStateWithLifecycle()
    val apkToolBusy by apkToolViewModel.busy.collectAsStateWithLifecycle()
    val signingBusy by apkSigningViewModel.busy.collectAsStateWithLifecycle()
    val signatureHasResult by signatureViewModel.hasResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val appState = rememberAppState()

    val navigator = remember { Navigator(appState.navigationState) }

    Scaffold(bottomBar = {
        AnimatedVisibility(
            visible = appState.navigationState.currentTopLevelKey == CleanerNavKey && viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ClearBuildBottom(viewModel)
        }
    }, snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAlwaysShowLabel = shell.isAlwaysShowLabel
            val isShowJunkCode = shell.isShowJunkCode
            AnimatedVisibility(viewModel.pendingDeletionFileList.isEmpty()) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TOP_LEVEL_NAV_ITEMS.forEach { (navKey, navItem) ->
                            if (navKey == JunkCodeNavKey && !isShowJunkCode) {
                                return@forEach
                            }
                            val selected = navKey == appState.navigationState.currentTopLevelKey
                            TooltipBox(
                                positionProvider = rememberRichTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(
                                            stringResource(navItem.tooltip),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                state = rememberTooltipState(),
                                enableUserInput = !selected
                            ) {
                                val icon = if (selected) navItem.selectedIcon else navItem.unSelectedIcon
                                NavigationRailItem(
                                    label = { Text(stringResource(navItem.title)) },
                                    icon = {
                                        Icon(
                                            icon,
                                            contentDescription = stringResource(navItem.title)
                                        )
                                    },
                                    selected = selected,
                                    onClick = { navigator.navigate(navKey) },
                                    alwaysShowLabel = isAlwaysShowLabel,
                                )
                            }
                        }
                    }
                }
            }

            val entryProvider = entryProvider {
                signatureInformationEntry(signatureViewModel)
                apkInformationEntry(apkInformationViewModel)
                apkSignatureEntry(apkSigningViewModel)
                signatureGenerationEntry(keyStoreViewModel)
                apkToolEntry(apkToolViewModel)
                junkCodeEntry(junkCodeViewModel)
                iconFactoryEntry(iconFactoryViewModel)
                cleanerEntry(viewModel, signatureHasResult)
                settingEntry(settingsViewModel, updateViewModel, actions)
            }

            NavDisplay(
                entries = appState.navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() },
                transitionSpec = defaultTransitionSpec()
            )
        }
    }
    AppEffectHost(effects, snackbarHostState, actions)
    val signatureBusy by signatureViewModel.busy.collectAsStateWithLifecycle()
    val keyStoreBusy by keyStoreViewModel.busy.collectAsStateWithLifecycle()
    val apkInformationBusy by apkInformationViewModel.busy.collectAsStateWithLifecycle()
    LoadingAnimate(isShowLoading(viewModel) || keyStoreBusy || signatureBusy || apkInformationBusy || signingBusy || apkToolBusy || iconFactoryBusy || junkBusy, useDarkTheme)
    UpdateRoute(updateViewModel, actions)
}

private fun isShowLoading(viewModel: MainViewModel) =
    viewModel.fileClearUIState == UIState.Loading && viewModel.isClearing

@Composable
private fun rememberRichTooltipPositionProvider(): PopupPositionProvider {
    val tooltipAnchorSpacing = with(LocalDensity.current) { 4.dp.roundToPx() }
    return remember(tooltipAnchorSpacing) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = anchorBounds.right
                if (x + popupContentSize.width > windowSize.width) {
                    x = anchorBounds.left - popupContentSize.width
                    if (x < 0) x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                }
                x -= tooltipAnchorSpacing
                val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
                return IntOffset(x, y)
            }
        }
    }
}
