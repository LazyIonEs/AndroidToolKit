package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.onClick
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import kotlinx.coroutines.CoroutineScope
import model.ApkInformation
import model.DarkThemeConfig
import model.FileSelectorType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.composeapp.generated.resources.ABIs
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.app_name
import org.tool.kit.composeapp.generated.resources.compile_sdk_version
import org.tool.kit.composeapp.generated.resources.file_md5
import org.tool.kit.composeapp.generated.resources.icon
import org.tool.kit.composeapp.generated.resources.let_go
import org.tool.kit.composeapp.generated.resources.minimum_sdk_version
import org.tool.kit.composeapp.generated.resources.package_name
import org.tool.kit.composeapp.generated.resources.permissions
import org.tool.kit.composeapp.generated.resources.size
import org.tool.kit.composeapp.generated.resources.target_sdk_version
import org.tool.kit.composeapp.generated.resources.upload_apk
import org.tool.kit.composeapp.generated.resources.version
import org.tool.kit.composeapp.generated.resources.version_code
import theme.AppTheme
import utils.LottieAnimation
import utils.copy
import utils.formatFileSize
import utils.getImageRequest
import utils.isApk
import vm.MainViewModel
import vm.UIState
import kotlin.io.path.pathString

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/8 16:13
 * @Description : APK信息
 * @Version     : 1.0
 */
@Composable
fun ApkInformation(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    if (viewModel.apkInformationState == UIState.WAIT) {
        ApkInformationLottie(viewModel, scope)
    }
    ApkInformationBox(viewModel)
    ApkDraggingBox(viewModel, scope)
}

/**
 * 主页动画
 */
@Composable
private fun ApkInformationLottie(viewModel: MainViewModel, scope: CoroutineScope) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    Box(
        modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
    ) {
        if (useDarkTheme) {
            LottieAnimation(scope, "files/lottie_main_2_dark.json")
        } else {
            LottieAnimation(scope, "files/lottie_main_2_light.json")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkDraggingBox(viewModel: MainViewModel, scope: CoroutineScope) {
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
                dragging = it
            }, onFinish = { result ->
                result.onSuccess { fileList ->
                    fileList.firstOrNull()?.let {
                        val path = it.toAbsolutePath().pathString
                        if (path.isApk) {
                            viewModel.apkInformation(path)
                        }
                    }
                }
            }))
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FileButton(
                value = if (dragging) {
                    stringResource(Res.string.let_go)
                } else {
                    stringResource(Res.string.upload_apk)
                }, expanded = viewModel.apkInformationState == UIState.WAIT, FileSelectorType.APK
            ) { path ->
                viewModel.apkInformation(path)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkInformationBox(
    viewModel: MainViewModel
) {
    val uiState = viewModel.apkInformationState
    AnimatedVisibility(
        visible = uiState is UIState.Success, enter = fadeIn(), exit = fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 14.dp, end = 14.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)
            ) {
                if (uiState is UIState.Success) {
                    val apkInformation = uiState.result as ApkInformation
                    LazyColumn {
                        item {
                            AppInfoItem(stringResource(Res.string.app_name), apkInformation.label, viewModel)
                        }
                        item {
                            AppInfoItem(stringResource(Res.string.version), apkInformation.versionName, viewModel)
                        }
                        item {
                            AppInfoItem(stringResource(Res.string.version_code), apkInformation.versionCode, viewModel)
                        }
                        item {
                            AppInfoItem(stringResource(Res.string.package_name), apkInformation.packageName, viewModel)
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.compile_sdk_version),
                                apkInformation.compileSdkVersion,
                                viewModel
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.minimum_sdk_version),
                                apkInformation.minSdkVersion,
                                viewModel
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.target_sdk_version),
                                apkInformation.targetSdkVersion,
                                viewModel
                            )
                        }
                        item {
                            AppInfoItem(stringResource(Res.string.ABIs), apkInformation.nativeCode, viewModel)
                        }
                        item {
                            AppInfoItem(stringResource(Res.string.file_md5), apkInformation.md5, viewModel)
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.size),
                                apkInformation.size.formatFileSize(scale = 1, withInterval = true),
                                viewModel
                            )
                        }
                        item {
                            PermissionsList(apkInformation.usesPermissionList)
                        }
                    }
                    apkInformation.icon?.let { image ->
                        var isOpenImage by remember { mutableStateOf(false) }
                        if (isOpenImage) {
                            val windowState = rememberWindowState(size = DpSize(450.dp, 450.dp))
                            Window(
                                onCloseRequest = { isOpenImage = false },
                                state = windowState,
                                title = "Zoom Image",
                                icon = painterResource(Res.drawable.icon),
                                alwaysOnTop = true
                            ) {
                                val themeConfig by viewModel.themeConfig.collectAsState()
                                val useDarkTheme = when (themeConfig) {
                                    DarkThemeConfig.LIGHT -> false
                                    DarkThemeConfig.DARK -> true
                                    DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                                }
                                AppTheme(useDarkTheme) {
                                    Surface(color = MaterialTheme.colorScheme.background) {
                                        CoilZoomAsyncImage(
                                            model = getImageRequest(image.asSkiaBitmap()),
                                            contentDescription = "zoom image",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                        AsyncImage(
                            model = getImageRequest(image.asSkiaBitmap()),
                            contentDescription = "app icon",
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 18.dp)
                                .size(128.dp)
                                .onClick {
                                    isOpenImage = !isOpenImage
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInfoItem(title: String, value: String, viewModel: MainViewModel) {
    Card(modifier = Modifier.padding(horizontal = 12.dp).height(36.dp), onClick = {
        copy(value, viewModel)
    }) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1.2f).align(Alignment.CenterVertically)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(4f).align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun PermissionsList(permissions: ArrayList<String>?) {
    permissions?.let {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
            ) {
                Text(
                    stringResource(Res.string.permissions),
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.titleMedium
                )
                Column(
                    modifier = Modifier.weight(4f)
                ) {
                    it.forEach { permission ->
                        Text(text = permission, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}