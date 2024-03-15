package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showFileSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.ApkInformation
import toast.ToastModel
import toast.ToastUIState
import utils.LottieAnimation
import utils.copy
import utils.formatFileSize
import utils.isWindows
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
    ApkLoadingBox(viewModel, scope)
    ApkDraggingBox(viewModel, scope)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApkDraggingBox(viewModel: MainViewModel, scope: CoroutineScope) {
    var isDragging by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = isDragging,
        enter = fadeIn() + slideIn(
            tween(
                durationMillis = 400, easing = LinearOutSlowInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) },
        exit = slideOut(
            tween(
                durationMillis = 400, easing = FastOutLinearInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) } + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            )
        ) {
            LottieAnimation(scope, "files/upload.json")
        }
    }
    Box(
        modifier = Modifier.padding(6.dp).onExternalDrag(onDragStart = { isDragging = true },
            onDragExit = { isDragging = false },
            onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    dragData.readFiles().first().let {
                        if (it.endsWith(".apk")) {
                            val path = File(URI.create(it)).path
                            viewModel.apkInformation(path)
                        }
                    }
                }
                isDragging = false
            }), contentAlignment = Alignment.TopCenter
    ) {
        ApkFloatingButton(viewModel, isDragging)
    }
}

@Composable
fun ApkLoadingBox(viewModel: MainViewModel, scope: CoroutineScope) {
    AnimatedVisibility(
        visible = viewModel.apkInformationState == UIState.Loading,
        enter = fadeIn() + expandHorizontally(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
        ) {
            LottieAnimation(scope, "files/lottie_loading.json")
        }
    }
}

/**
 * 选择文件按钮
 */
@Composable
private fun ApkFloatingButton(viewModel: MainViewModel, isDragging: Boolean) {
    var showFilePickerApk by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp)
        ) {
            AnimatedVisibility(
                visible = viewModel.apkInformationState != UIState.Loading
            ) {
                ExtendedFloatingActionButton(onClick = {
                    if (isWindows) {
                        showFilePickerApk = true
                    } else {
                        showFileSelector { path ->
                            viewModel.apkInformation(path)
                        }
                    }
                }, icon = { Icon(Icons.Rounded.DriveFolderUpload, "准备选择文件") }, text = {
                    Text(
                        if (isDragging) {
                            "愣着干嘛，还不松手"
                        } else {
                            "点击选择或拖拽上传APK"
                        }
                    )
                })
            }
        }
    }
    if (isWindows) {
        FilePicker(
            show = showFilePickerApk, fileExtensions = listOf("apk")
        ) { platformFile ->
            showFilePickerApk = false
            if (platformFile?.path?.isNotBlank() == true && platformFile.path.endsWith(".apk")) {
                viewModel.apkInformation(platformFile.path)
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
                            modifier = Modifier.padding(top = 6.dp, end = 18.dp).align(Alignment.TopEnd)
                                .size(128.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoItem(title: String, value: String, toastState: ToastUIState, scope: CoroutineScope) {
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
fun PermissionsList(permissions: ArrayList<String>?) {
    permissions?.let {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
            ) {
                Text(
                    "应用权限列表：",
                    modifier = Modifier.weight(1f),
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