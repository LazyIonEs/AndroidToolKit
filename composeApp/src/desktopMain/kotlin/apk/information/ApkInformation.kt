package apk.information

import utils.LottieAnimation
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showFileSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.ApkInformation
import toast.ToastModel
import toast.ToastUIState
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
fun ApkInformation(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    when (val uiState = viewModel.apkInformationState) {
        UIState.WAIT, is UIState.Error -> {
            Box(
                modifier = modifier.padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(scope, "files/lottie_main_1.json", modifier)
            }
            if (uiState is UIState.Error) {
                scope.launch {
                    toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
                }
            }
        }

        UIState.Loading -> {
            Box(
                modifier = modifier.padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(scope, "files/lottie_loading.json", modifier)
            }
        }

        is UIState.Success -> ApkInformationBox(modifier, viewModel, toastState, scope)
    }
    ApkDraggingBox(modifier, viewModel)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApkDraggingBox(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var isDragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.padding(6.dp).onExternalDrag(
            onDragStart = { isDragging = true },
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
            }),
        contentAlignment = Alignment.TopCenter
    ) {
        ApkFloatingButton(modifier, viewModel, isDragging)
    }
}

/**
 * 选择文件按钮
 */
@Composable
private fun ApkFloatingButton(modifier: Modifier = Modifier, viewModel: MainViewModel, isDragging: Boolean) {
    var showFilePickerApk by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = modifier.align(Alignment.BottomEnd).padding(end = 8.dp)
        ) {
            AnimatedVisibility(
                visible = viewModel.apkInformationState != UIState.Loading
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isWindows) {
                            showFilePickerApk = true
                        } else {
                            showFileSelector { path ->
                                viewModel.apkInformation(path)
                            }
                        }
                    },
                    icon = { Icon(Icons.Rounded.DriveFolderUpload, "准备选择文件") },
                    text = {
                        Text(if (isDragging) {
                            "愣着干嘛，还不松手"
                        } else {
                            "点击选择或拖拽上传APK"
                        })
                    }
                )
            }
        }
    }
    if (isWindows) {
        FilePicker(
            show = showFilePickerApk,
            fileExtensions = listOf("apk")
        ) { platformFile ->
            showFilePickerApk = false
            if (platformFile?.path?.isNotBlank() == true && platformFile.path.endsWith(".apk")) {
                viewModel.apkInformation(platformFile.path)
            }
        }
    }
}

@Composable
private fun ApkInformationBox(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    val uiState = (viewModel.apkInformationState as UIState.Success).result as ApkInformation
    Card(
        modifier = modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = modifier.fillMaxSize().padding(vertical = 12.dp)
        ) {
            LazyColumn {
                item {
                    AppInfoItem(modifier, "应用名称：", uiState.label, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "版本：", uiState.versionName, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "版本号：", uiState.versionCode, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "包名：", uiState.packageName, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "编译SDK版本：", uiState.compileSdkVersion, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "最小SDK版本：", uiState.minSdkVersion, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "目标SDK版本：", uiState.targetSdkVersion, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "ABIs：", uiState.nativeCode, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "文件MD5：", uiState.md5, toastState, scope)
                }
                item {
                    AppInfoItem(modifier, "大小：", formatFileSize(uiState.size, 2, true), toastState, scope)
                }
                item {
                    PermissionsList(modifier, uiState.usesPermissionList)
                }
            }
            uiState.icon?.let { imageBitmap ->
                Image(bitmap = imageBitmap, contentDescription = "Apk Icon", modifier = modifier.padding(top = 6.dp, end = 18.dp).align(Alignment.TopEnd).size(128.dp))
            }
        }
    }
}

@Composable
fun AppInfoItem(modifier: Modifier, title: String, value: String, toastState: ToastUIState, scope: CoroutineScope) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp).height(36.dp),
        onClick = {
            scope.launch {
                copy(value, toastState)
            }
        }
    ) {
        Row(
            modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = modifier.weight(1f).align(Alignment.CenterVertically))
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = modifier.weight(4f).align(Alignment.CenterVertically))
        }
    }
}

@Composable
fun PermissionsList(modifier: Modifier, permissions: ArrayList<String>?) {
    permissions?.let {
        Column(
            modifier = modifier.padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)
            ) {
                Text("应用权限列表：", modifier = modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Column(
                    modifier = modifier.weight(4f)
                ) {
                    it.forEach { permission ->
                        Text(text = permission, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}