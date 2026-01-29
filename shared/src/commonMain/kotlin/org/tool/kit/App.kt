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
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.russhwolf.settings.ExperimentalSettingsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.apk.navigation.apkInformationEntry
import org.tool.kit.feature.apk.navigation.apkToolEntry
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
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.UserData
import org.tool.kit.navigation.Navigator
import org.tool.kit.navigation.TOP_LEVEL_NAV_ITEMS
import org.tool.kit.navigation.defaultTransitionSpec
import org.tool.kit.navigation.toEntries
import org.tool.kit.platform.createFlowSettings
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme
import org.tool.kit.feature.cleaner.ClearBuildBottom
import org.tool.kit.feature.ui.UpdateDialog
import org.tool.kit.vm.MainViewModel
import org.tool.kit.vm.UIState

private val logger = KotlinLogging.logger("App")

@OptIn(ExperimentalSettingsApi::class)
@Composable
fun App() {
    val viewModel = viewModel { MainViewModel(settings = createFlowSettings()) }
    val themeConfig by viewModel.themeConfig.collectAsState()
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    logger.info { "启动App, 应用版本号: ${BuildConfig.APP_VERSION}" }

    AppTheme(useDarkTheme) {
        CompositionLocalProvider(LocalIsAppDarkTheme provides useDarkTheme) {
            MainContentScreen(viewModel)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.isStartCheckUpdate.value) {
            viewModel.checkUpdate(showMessage = false)
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
fun MainContentScreen(viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val userData by viewModel.userData.collectAsState()
    collectOutputPath(viewModel, userData)

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
            val isAlwaysShowLabel by viewModel.isAlwaysShowLabel.collectAsState()
            val isShowJunkCode by viewModel.isShowJunkCode.collectAsState()
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
                signatureInformationEntry(viewModel)
                apkInformationEntry(viewModel)
                apkSignatureEntry(viewModel)
                signatureGenerationEntry(viewModel)
                apkToolEntry(viewModel)
                junkCodeEntry(viewModel)
                iconFactoryEntry(viewModel)
                cleanerEntry(viewModel)
                settingEntry(viewModel)
            }

            NavDisplay(
                entries = appState.navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() },
                transitionSpec = defaultTransitionSpec()
            )
        }
    }
    val snackbarVisuals by viewModel.snackbarVisuals.collectAsState()
    LaunchedEffect(snackbarVisuals) {
        if (snackbarVisuals.message.isBlank()) return@LaunchedEffect
        val snackbarResult = snackbarHostState.showSnackbar(snackbarVisuals)
        when (snackbarResult) {
            SnackbarResult.ActionPerformed -> snackbarVisuals.action?.invoke()
            SnackbarResult.Dismissed -> Unit
        }
    }
    LoadingAnimate(isShowLoading(viewModel), viewModel)
    UpdateDialog(viewModel)
}

private fun isShowLoading(viewModel: MainViewModel) =
    viewModel.junkCodeUIState == UIState.Loading || viewModel.iconFactoryUIState == UIState.Loading
            || viewModel.apkSignatureUIState == UIState.Loading || viewModel.apkInformationState == UIState.Loading
            || viewModel.keyStoreInfoUIState == UIState.Loading || viewModel.verifierState == UIState.Loading
            || (viewModel.fileClearUIState == UIState.Loading && viewModel.isClearing
            || viewModel.apkToolInfoUIState == UIState.Loading)

fun collectOutputPath(viewModel: MainViewModel, userData: UserData) {
    val outputPath = userData.defaultOutputPath
    viewModel.apply {
        updateApkSignature(viewModel.apkSignatureState.copy(outputPath = outputPath))
        updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStorePath = outputPath))
        updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(outputPath = outputPath))
        updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(outputPath = outputPath))
        updateApkToolInfo(viewModel.apkToolInfoState.copy(outputPath = outputPath))
    }
}

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