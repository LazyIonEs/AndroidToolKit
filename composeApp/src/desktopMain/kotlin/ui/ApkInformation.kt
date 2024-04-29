package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.ApkInformation
import toast.ToastModel
import toast.ToastUIState
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
fun ApkInformation(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    val uiState = viewModel.apkInformationState
    if (uiState == UIState.WAIT || uiState is UIState.Error) {
        if (uiState is UIState.Error) {
            scope.launch {
                toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
            }
        }
        Box(
            modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
        ) {
            LottieAnimation(scope, "files/lottie_main_1.json")
        }
    }
    ApkInformationBox(viewModel, toastState, scope)
    LoadingAnimate(viewModel.apkInformationState == UIState.Loading, scope)
    ApkDraggingBox(viewModel, scope)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApkDraggingBox(viewModel: MainViewModel, scope: CoroutineScope) {
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize().padding(6.dp)
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
            }), contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FileButton(
                value = if (dragging) {
                    "愣着干嘛，还不松手"
                } else {
                    "点击选择或拖拽上传APK"
                }, expanded = viewModel.apkInformationState == UIState.WAIT,
                FileSelectorType.APK
            ) { path ->
                viewModel.apkInformation(path)
            }
        }
    }
}

@Composable
private fun ApkInformationBox(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
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
                            AppInfoItem("应用名称：", apkInformation.label, toastState, scope)
                        }
                        item {
                            AppInfoItem("版本：", apkInformation.versionName, toastState, scope)
                        }
                        item {
                            AppInfoItem("版本号：", apkInformation.versionCode, toastState, scope)
                        }
                        item {
                            AppInfoItem("包名：", apkInformation.packageName, toastState, scope)
                        }
                        item {
                            AppInfoItem("编译SDK版本：", apkInformation.compileSdkVersion, toastState, scope)
                        }
                        item {
                            AppInfoItem("最小SDK版本：", apkInformation.minSdkVersion, toastState, scope)
                        }
                        item {
                            AppInfoItem("目标SDK版本：", apkInformation.targetSdkVersion, toastState, scope)
                        }
                        item {
                            AppInfoItem("ABIs：", apkInformation.nativeCode, toastState, scope)
                        }
                        item {
                            AppInfoItem("文件MD5：", apkInformation.md5, toastState, scope)
                        }
                        item {
                            AppInfoItem("大小：", formatFileSize(apkInformation.size, 2, true), toastState, scope)
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
private fun AppInfoItem(title: String, value: String, toastState: ToastUIState, scope: CoroutineScope) {
    Card(modifier = Modifier.padding(horizontal = 12.dp).height(36.dp), onClick = {
        scope.launch {
            copy(value, toastState)
        }
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