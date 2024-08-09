package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.unit.dp
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import model.ApkInformation
import model.DarkThemeConfig
import utils.LottieAnimation
import utils.copy
import utils.formatFileSize
import utils.isApk
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

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
    LoadingAnimate(viewModel.apkInformationState == UIState.Loading, viewModel, scope)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApkDraggingBox(viewModel: MainViewModel, scope: CoroutineScope) {
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize()
            .onExternalDrag(onDragStart = { dragging = true }, onDragExit = { dragging = false }, onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    dragData.readFiles().first().let {
                        if (it.isApk) {
                            val path = File(URI.create(it)).path
                            viewModel.apkInformation(path)
                        }
                    }
                }
                dragging = false
            })
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FileButton(
                value = if (dragging) {
                    "愣着干嘛，还不松手"
                } else {
                    "点击选择或拖拽上传APK"
                }, expanded = viewModel.apkInformationState == UIState.WAIT, FileSelectorType.APK
            ) { path ->
                viewModel.apkInformation(path)
            }
        }
    }
}

@Composable
private fun ApkInformationBox(
    viewModel: MainViewModel
) {
    val uiState = viewModel.apkInformationState
    AnimatedVisibility(
        visible = uiState is UIState.Success, enter = fadeIn(), exit = fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)
            ) {
                if (uiState is UIState.Success) {
                    val apkInformation = uiState.result as ApkInformation
                    LazyColumn {
                        item {
                            AppInfoItem("应用名称：", apkInformation.label, viewModel)
                        }
                        item {
                            AppInfoItem("版本：", apkInformation.versionName, viewModel)
                        }
                        item {
                            AppInfoItem("版本号：", apkInformation.versionCode, viewModel)
                        }
                        item {
                            AppInfoItem("包名：", apkInformation.packageName, viewModel)
                        }
                        item {
                            AppInfoItem("编译SDK版本：", apkInformation.compileSdkVersion, viewModel)
                        }
                        item {
                            AppInfoItem("最小SDK版本：", apkInformation.minSdkVersion, viewModel)
                        }
                        item {
                            AppInfoItem("目标SDK版本：", apkInformation.targetSdkVersion, viewModel)
                        }
                        item {
                            AppInfoItem("ABIs：", apkInformation.nativeCode, viewModel)
                        }
                        item {
                            AppInfoItem("文件MD5：", apkInformation.md5, viewModel)
                        }
                        item {
                            AppInfoItem("大小：", formatFileSize(apkInformation.size, 1, true), viewModel)
                        }
                        item {
                            PermissionsList(apkInformation.usesPermissionList)
                        }
                    }
                    apkInformation.icon?.let { imageBitmap ->
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Apk Icon",
                            modifier = Modifier.padding(top = 6.dp, end = 18.dp).align(Alignment.TopEnd).size(128.dp)
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
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
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
                    "应用权限列表：", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium
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