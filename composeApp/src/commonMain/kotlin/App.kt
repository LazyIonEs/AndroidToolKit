import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.russhwolf.settings.ExperimentalSettingsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.Asset
import model.DarkThemeConfig
import model.DownloadState
import model.Update
import model.UserData
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.BuildConfig
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.apk_information_rail
import org.tool.kit.composeapp.generated.resources.apk_information_tooltip
import org.tool.kit.composeapp.generated.resources.apk_signature_rail
import org.tool.kit.composeapp.generated.resources.apk_signature_tooltip
import org.tool.kit.composeapp.generated.resources.apk_tool_rail
import org.tool.kit.composeapp.generated.resources.apk_tool_tooltip
import org.tool.kit.composeapp.generated.resources.cache_cleanup_rail
import org.tool.kit.composeapp.generated.resources.cache_cleanup_tooltip
import org.tool.kit.composeapp.generated.resources.cancel
import org.tool.kit.composeapp.generated.resources.connecting
import org.tool.kit.composeapp.generated.resources.download_success
import org.tool.kit.composeapp.generated.resources.download_tips
import org.tool.kit.composeapp.generated.resources.downloading
import org.tool.kit.composeapp.generated.resources.exit_and_install
import org.tool.kit.composeapp.generated.resources.garbage_code_rail
import org.tool.kit.composeapp.generated.resources.garbage_code_tooltip
import org.tool.kit.composeapp.generated.resources.icon_generation_rail
import org.tool.kit.composeapp.generated.resources.icon_generation_tooltip
import org.tool.kit.composeapp.generated.resources.prepare_for_installation
import org.tool.kit.composeapp.generated.resources.release_time
import org.tool.kit.composeapp.generated.resources.setting_rail
import org.tool.kit.composeapp.generated.resources.setting_tooltip
import org.tool.kit.composeapp.generated.resources.signature_generation_rail
import org.tool.kit.composeapp.generated.resources.signature_generation_tooltip
import org.tool.kit.composeapp.generated.resources.signature_information_rail
import org.tool.kit.composeapp.generated.resources.signature_information_tooltip
import org.tool.kit.composeapp.generated.resources.update
import platform.createFlowSettings
import theme.AppTheme
import ui.ApkInformation
import ui.ApkSignature
import ui.ApkTool
import ui.ClearBuild
import ui.ClearBuildBottom
import ui.IconFactory
import ui.JunkCode
import ui.LoadingAnimate
import ui.SetUp
import ui.SignatureGeneration
import ui.SignatureInformation
import utils.downloadFile
import utils.formatFileSize
import vm.MainViewModel
import vm.UIState
import java.awt.Desktop
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.exitProcess

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
        MainContentScreen(viewModel)
    }

    LaunchedEffect(Unit) {
        if (viewModel.isStartCheckUpdate.value) {
            viewModel.checkUpdate(showMessage = false)
        }
    }
}

/**
 * 主要模块
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val userData by viewModel.userData.collectAsState()
    collectOutputPath(viewModel, userData)
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAlwaysShowLabel by viewModel.isAlwaysShowLabel.collectAsState()
            val isShowApktool by viewModel.isShowApktool.collectAsState()
            val isShowJunkCode by viewModel.isShowJunkCode.collectAsState()
            val isShowIconFactory by viewModel.isShowIconFactory.collectAsState()
            val isShowClearBuild by viewModel.isShowClearBuild.collectAsState()
            val isShowSignatureGeneration by viewModel.isShowSignatureGeneration.collectAsState()
            val pages = Page.entries.toMutableList().also { list ->
                if (!isShowApktool) {
                    list.remove(Page.APK_TOOL)
                }
                if (!isShowJunkCode) {
                    list.remove(Page.JUNK_CODE)
                }
                if (!isShowSignatureGeneration) {
                    list.remove(Page.SIGNATURE_GENERATION)
                }
                if (!isShowIconFactory) {
                    list.remove(Page.ICON_FACTORY)
                }
                if (!isShowClearBuild) {
                    list.remove(Page.CLEAR_BUILD)
                }
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
                                positionProvider = rememberRichTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(
                                            stringResource(page.tooltip),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                state = rememberTooltipState(),
                                enableUserInput = viewModel.uiPageIndex != page
                            ) {
                                val icon =
                                    if (viewModel.uiPageIndex == page) page.selectedIcon else page.unSelectedIcon
                                NavigationRailItem(
                                    label = { Text(stringResource(page.title)) },
                                    icon = {
                                        Icon(
                                            icon,
                                            contentDescription = stringResource(page.title)
                                        )
                                    },
                                    selected = viewModel.uiPageIndex == page,
                                    onClick = { viewModel.updateUiState(page) },
                                    alwaysShowLabel = isAlwaysShowLabel,
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
                    Page.APK_TOOL -> ApkTool(viewModel)
                    Page.JUNK_CODE -> JunkCode(viewModel)
                    Page.ICON_FACTORY -> IconFactory(viewModel)
                    Page.CLEAR_BUILD -> ClearBuild(viewModel)
                    Page.SET_UP -> SetUp(viewModel)
                }
            }
            // 淡入淡出切换页面
            Crossfade(
                targetState = viewModel.uiPageIndex,
                modifier = Modifier.fillMaxSize(),
                content = content
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

/**
 * 检查更新弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val update by vm.checkUpdateResult.collectAsState()
    var downloadState by remember { mutableStateOf(DownloadState.START) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedByte by remember { mutableStateOf(0L) }
    var totalByte by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    var job: Job? = null
    var downloadFile: File? = null
    update?.let { update ->
        if (update.assets.isNotEmpty()) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(update.assets[0]) }
            val download: () -> Unit = {
                job = coroutineScope.launch {
                    val destFile = File(vm.userData.value.defaultOutputPath, selectedOption.name)
                    downloadState = DownloadState.DOWNLOADING
                    val result = downloadFile(
                        selectedOption.browserDownloadUrl,
                        destFile
                    ) { downloaded, total ->
                        downloadedByte = downloaded
                        totalByte = total
                        downloadProgress = if (total > 0) {
                            (downloaded.toFloat() / total).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                    if (result) {
                        downloadFile = destFile
                        downloadState = DownloadState.FINISH
                    } else {
                        downloadState = DownloadState.START
                    }
                }
            }
            AlertDialog(
                icon = null, title = {
                    val title = when (downloadState) {
                        DownloadState.START -> "${BuildConfig.APP_NAME}-${update.version}"
                        DownloadState.DOWNLOADING -> if (downloadProgress <= 0f) {
                            stringResource(Res.string.connecting)
                        } else {
                            stringResource(Res.string.downloading, (downloadProgress * 100).roundToInt())
                        }
                        DownloadState.FINISH -> stringResource(Res.string.download_success)
                    }
                    Text(text = title)
                }, text = {
                    AnimatedContent(downloadState) { state ->
                        when (state) {
                            DownloadState.START -> DownloadStartUI(
                                update,
                                selectedOption,
                                onOptionSelected
                            )

                            DownloadState.DOWNLOADING, DownloadState.FINISH -> DownloadUI(
                                downloadProgress,
                                downloadedByte,
                                totalByte,
                                state
                            )
                        }
                    }
                }, onDismissRequest = {
                    if (downloadState != DownloadState.DOWNLOADING) {
                        vm.cancelUpdate()
                    }
                }, confirmButton = {
                    AnimatedContent(downloadState) { state ->
                        when (state) {
                            DownloadState.START -> TextButton(onClick = download) {
                                Text(text = stringResource(Res.string.update))
                            }

                            DownloadState.DOWNLOADING -> Unit
                            DownloadState.FINISH -> TextButton(onClick = {
                                vm.cancelUpdate()
                                if (downloadFile != null && downloadFile.exists()) {
                                    runCatching {
                                        Desktop.getDesktop().open(downloadFile)
                                    }.onFailure { e ->
                                        logger.error(e) { "UpdateDialog 打开安装文件异常, 异常信息: ${e.message}" }
                                    }.onSuccess {
                                        exitProcess(0)
                                    }
                                }
                            }) {
                                Text(text = stringResource(Res.string.exit_and_install))
                            }
                        }
                    }
                }, dismissButton = {
                    TextButton(onClick = {
                        if (downloadState == DownloadState.DOWNLOADING) {
                            job?.cancel()
                            job = null
                            downloadProgress = 0f
                            downloadedByte = 0L
                            totalByte = 0L
                            downloadState = DownloadState.START
                        } else {
                            vm.cancelUpdate()
                        }
                    }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                })
        }
    }
}

@Composable
private fun DownloadStartUI(
    update: Update,
    selectedOption: Asset,
    onOptionSelected: (Asset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val scrollState = rememberScrollState()
        HorizontalDivider(thickness = 2.dp)
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(0.dp, 160.dp)
                .padding(vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            update.body?.let { body ->
                val regex = Regex("(?<!#)#{1,2}(?!#)")
                val state = rememberRichTextState()
                state.setMarkdown(body.replace(regex, "###"))
                state.config.linkColor = MaterialTheme.colorScheme.primary
                state.config.preserveStyleOnEmptyLine = false
                state.config.exitListOnEmptyItem = false
                RichText(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = stringResource(Res.string.release_time, update.createdAt),
                modifier = Modifier.padding(vertical = 4.dp)
                    .align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            thickness = 2.dp
        )
        Column(Modifier.selectableGroup()) {
            update.assets.forEach { asset ->
                val interactionSource =
                    remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RadioButton(
                        selected = (asset == selectedOption),
                        onClick = { onOptionSelected(asset) },
                        interactionSource = interactionSource,
                    )
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.selectable(
                            selected = (asset == selectedOption),
                            onClick = { onOptionSelected(asset) },
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = null
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadUI(
    downloadProgress: Float,
    downloadedByte: Long,
    totalByte: Long,
    state: DownloadState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val tips = when (state) {
            DownloadState.START -> stringResource(Res.string.download_tips)
            DownloadState.DOWNLOADING -> stringResource(Res.string.download_tips)
            DownloadState.FINISH -> stringResource(Res.string.prepare_for_installation)
        }
        Text(
            text = tips,
            modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        AnimatedContent(
            downloadProgress == 0f,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) { unspecified ->
            if (unspecified) {
                LinearWavyProgressIndicator()
            } else {
                LinearWavyProgressIndicator(progress = { downloadProgress })
            }
        }
        AnimatedVisibility(
            totalByte != 0L && state == DownloadState.DOWNLOADING,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "${
                    downloadedByte.formatFileSize(1)
                } / ${
                    totalByte.formatFileSize(1)
                }",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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

enum class Page(
    val title: StringResource,
    val tooltip: StringResource,
    val selectedIcon: ImageVector,
    val unSelectedIcon: ImageVector
) {
    SIGNATURE_INFORMATION(
        Res.string.signature_information_rail,
        Res.string.signature_information_tooltip,
        Icons.AutoMirrored.Rounded.Article,
        Icons.AutoMirrored.Outlined.Article
    ),
    APK_INFORMATION(
        Res.string.apk_information_rail,
        Res.string.apk_information_tooltip,
        Icons.Rounded.Assessment,
        Icons.Outlined.Assessment
    ),
    APK_SIGNATURE(
        Res.string.apk_signature_rail,
        Res.string.apk_signature_tooltip,
        Icons.Rounded.VpnKey,
        Icons.Outlined.VpnKey
    ),
    SIGNATURE_GENERATION(
        Res.string.signature_generation_rail,
        Res.string.signature_generation_tooltip,
        Icons.Rounded.Verified,
        Icons.Outlined.Verified
    ),
    APK_TOOL(
        Res.string.apk_tool_rail,
        Res.string.apk_tool_tooltip,
        Icons.Rounded.BrightnessAuto,
        Icons.Outlined.BrightnessAuto
    ),
    JUNK_CODE(
        Res.string.garbage_code_rail,
        Res.string.garbage_code_tooltip,
        Icons.Rounded.Cookie,
        Icons.Outlined.Cookie
    ),
    ICON_FACTORY(
        Res.string.icon_generation_rail,
        Res.string.icon_generation_tooltip,
        Icons.Rounded.DesignServices,
        Icons.Outlined.DesignServices
    ),
    CLEAR_BUILD(
        Res.string.cache_cleanup_rail,
        Res.string.cache_cleanup_tooltip,
        Icons.Rounded.FolderDelete,
        Icons.Outlined.FolderDelete
    ),
    SET_UP(
        Res.string.setting_rail,
        Res.string.setting_tooltip,
        Icons.Rounded.Settings,
        Icons.Outlined.Settings
    )
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
                    if (x < 0) x =
                        anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                }
                x -= tooltipAnchorSpacing
                val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
                return IntOffset(x, y)
            }
        }
    }
}