import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VpnKey
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import model.DarkThemeConfig
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.composeapp.generated.resources.APK信息
import org.tool.kit.composeapp.generated.resources.APK签名
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.图标生成
import org.tool.kit.composeapp.generated.resources.垃圾代码
import org.tool.kit.composeapp.generated.resources.签名信息
import org.tool.kit.composeapp.generated.resources.签名生成
import org.tool.kit.composeapp.generated.resources.缓存清理
import org.tool.kit.composeapp.generated.resources.设置
import platform.createFlowSettings
import theme.AppTheme
import ui.ApkInformation
import ui.ApkSignature
import ui.ClearBuild
import ui.ClearBuildBottom
import ui.IconFactory
import ui.JunkCode
import ui.LoadingAnimate
import ui.SetUp
import ui.SignatureGeneration
import ui.SignatureInformation
import vm.MainViewModel
import vm.UIState

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
    AppTheme(useDarkTheme) {
        Surface {
            MainContentScreen(viewModel)
        }
    }
}

/**
 * 主要模块
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    scope.launch(Dispatchers.IO) {
        collectOutputPath(viewModel)
    }
    Scaffold(bottomBar = {
        AnimatedVisibility(
            visible = viewModel.uiPageIndex == Page.CLEAR_BUILD && viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ClearBuildBottom(viewModel)
        }
    }, snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding), verticalAlignment = Alignment.CenterVertically
        ) {
            val junkCode by viewModel.junkCode.collectAsState()
            val pages = if (junkCode) {
                Page.entries.toMutableList()
            } else {
                Page.entries.toMutableList().also { list -> list.remove(Page.JUNK_CODE) }
            }
            // 导航栏
            AnimatedVisibility(viewModel.pendingDeletionFileList.isEmpty()) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        pages.forEachIndexed { _, page ->
                            TooltipBox(
                                positionProvider = rememberRichTooltipPositionProvider(), tooltip = {
                                    PlainTooltip {
                                        Text(
                                            stringResource(page.title), style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }, state = rememberTooltipState(), enableUserInput = viewModel.uiPageIndex != page
                            ) {
                                val icon = if (viewModel.uiPageIndex == page) page.selectedIcon else page.unSelectedIcon
                                NavigationRailItem(
                                    label = { Text(stringResource(page.title)) },
                                    icon = { Icon(icon, contentDescription = stringResource(page.title)) },
                                    selected = viewModel.uiPageIndex == page,
                                    onClick = { viewModel.updateUiState(page) },
                                    alwaysShowLabel = false,
                                )
                            }
                        }
                    }
                }
            }
            // 主界面
            val content: @Composable (Page) -> Unit = { page ->
                when (page) {
                    Page.SIGNATURE_INFORMATION -> SignatureInformation(viewModel)
                    Page.APK_INFORMATION -> ApkInformation(viewModel)
                    Page.APK_SIGNATURE -> ApkSignature(viewModel)
                    Page.SIGNATURE_GENERATION -> SignatureGeneration(viewModel)
                    Page.JUNK_CODE -> JunkCode(viewModel)
                    Page.ICON_FACTORY -> IconFactory(viewModel)
                    Page.CLEAR_BUILD -> ClearBuild(viewModel)
                    Page.SET_UP -> SetUp(viewModel)
                }
            }
            // 淡入淡出切换页面
            Crossfade(targetState = viewModel.uiPageIndex, modifier = Modifier.fillMaxSize(), content = content)
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
    LoadingAnimate(isShowLoading(viewModel), viewModel, scope)
}

private fun isShowLoading(viewModel: MainViewModel) =
    viewModel.junkCodeUIState == UIState.Loading || viewModel.iconFactoryUIState == UIState.Loading
            || viewModel.apkSignatureUIState == UIState.Loading || viewModel.apkInformationState == UIState.Loading
            || viewModel.keyStoreInfoUIState == UIState.Loading || viewModel.verifierState == UIState.Loading

suspend fun collectOutputPath(viewModel: MainViewModel) {
    val userData = viewModel.userData.drop(0).first()
    val outputPath = userData.defaultOutputPath
    viewModel.apply {
        updateApkSignature(viewModel.apkSignatureState.copy(outputPath = outputPath))
        updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStorePath = outputPath))
        updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(outputPath = outputPath))
        updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(outputPath = outputPath))
    }
}

enum class Page(val title: StringResource, val selectedIcon: ImageVector, val unSelectedIcon: ImageVector) {
    SIGNATURE_INFORMATION(Res.string.签名信息, Icons.AutoMirrored.Rounded.Article, Icons.AutoMirrored.Outlined.Article),
    APK_INFORMATION(Res.string.APK信息, Icons.Rounded.Assessment, Icons.Outlined.Assessment),
    APK_SIGNATURE(Res.string.APK签名, Icons.Rounded.VpnKey, Icons.Outlined.VpnKey),
    SIGNATURE_GENERATION(Res.string.签名生成, Icons.Rounded.Verified, Icons.Outlined.Verified),
    JUNK_CODE(Res.string.垃圾代码, Icons.Rounded.Cookie, Icons.Outlined.Cookie),
    ICON_FACTORY(Res.string.图标生成, Icons.Rounded.DesignServices, Icons.Outlined.DesignServices),
    CLEAR_BUILD(Res.string.缓存清理, Icons.Rounded.FolderDelete, Icons.Outlined.FolderDelete),
    SET_UP(Res.string.设置, Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@Composable
private fun rememberRichTooltipPositionProvider(): PopupPositionProvider {
    val tooltipAnchorSpacing = with(LocalDensity.current) { 4.dp.roundToPx() }
    return remember(tooltipAnchorSpacing) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
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