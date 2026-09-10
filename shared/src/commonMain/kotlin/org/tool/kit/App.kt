package org.tool.kit

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
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.apk.navigation.apkInformationEntry
import org.tool.kit.feature.apk.navigation.apkToolEntry
import org.tool.kit.feature.cleaner.navigation.cleanerEntry
import org.tool.kit.feature.iconfactory.navigation.iconFactoryEntry
import org.tool.kit.feature.junk.navigation.JunkCodeNavKey
import org.tool.kit.feature.junk.navigation.junkCodeEntry
import org.tool.kit.feature.app.rememberAppState
import org.tool.kit.feature.setting.navigation.settingEntry
import org.tool.kit.feature.signature.navigation.apkSignatureEntry
import org.tool.kit.feature.signature.navigation.signatureGenerationEntry
import org.tool.kit.feature.signature.navigation.signatureInformationEntry
import org.tool.kit.feature.update.UpdateRoute
import org.tool.kit.feature.update.UpdateViewModel
import org.tool.kit.feature.update.UpdateIntent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle


/** 将已创建的 Koin 容器接入 Compose，随后组合应用根页面。 */
@Suppress("DEPRECATION") // The root wrapper supplies Koin's Compose context.
@Composable
fun App() {
    KoinContext(koin = getKoin()) {
        AppRoute()
    }
}

/** 持有根级主题与更新状态，初始设置就绪后按配置触发一次静默更新检查。 */
@Composable
private fun AppRoute() {
    val appViewModel = koinViewModel<AppViewModel>()
    val updateViewModel = koinViewModel<UpdateViewModel>()
    val shell by appViewModel.uiState.collectAsStateWithLifecycle()
    val themeConfig = shell.themeConfig
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    LaunchedEffect(Unit) {
        org.tool.kit.platform.logAppStartup(BuildConfig.APP_VERSION)
    }

    AppTheme(useDarkTheme) {
        CompositionLocalProvider(LocalIsAppDarkTheme provides useDarkTheme) {
            MainContentScreen(shell, updateViewModel, koinInject(), koinInject())
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
 * 应用窗口布局：侧栏和消息宿主常驻，导航条目负责页面内容，更新弹窗位于根级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(
    shell: AppUiState,
    updateViewModel: UpdateViewModel,
    effects: AppEffectSink,
    actions: DesktopActionHandler,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val appState = rememberAppState()

    val navigator = remember { Navigator(appState.navigationState) }

    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAlwaysShowLabel = shell.isAlwaysShowLabel
            val isShowJunkCode = shell.isShowJunkCode
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

            val entryProvider = entryProvider {
                signatureInformationEntry()
                apkInformationEntry()
                apkSignatureEntry()
                signatureGenerationEntry()
                apkToolEntry()
                junkCodeEntry()
                iconFactoryEntry()
                cleanerEntry()
                settingEntry(updateViewModel, actions)
            }

            NavDisplay(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                entries = appState.navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() },
                transitionSpec = defaultTransitionSpec()
            )
        }
    }
    AppEffectHost(effects, snackbarHostState, actions)
    UpdateRoute(updateViewModel, actions)
}

/** 优先把侧栏提示放在锚点右侧，空间不足时改放左侧或居中。 */
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
