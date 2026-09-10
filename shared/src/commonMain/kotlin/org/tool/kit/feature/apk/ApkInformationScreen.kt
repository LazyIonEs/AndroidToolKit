package org.tool.kit.feature.apk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FileButton
import org.tool.kit.feature.ui.UploadAnimate
import org.tool.kit.shared.generated.resources.ABIs
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.app_name
import org.tool.kit.shared.generated.resources.channel
import org.tool.kit.shared.generated.resources.compile_sdk_version
import org.tool.kit.shared.generated.resources.file_md5
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.shared.generated.resources.let_go
import org.tool.kit.shared.generated.resources.minimum_sdk_version
import org.tool.kit.shared.generated.resources.package_name
import org.tool.kit.shared.generated.resources.permissions
import org.tool.kit.shared.generated.resources.size
import org.tool.kit.shared.generated.resources.target_sdk_version
import org.tool.kit.shared.generated.resources.upload_apk
import org.tool.kit.shared.generated.resources.version
import org.tool.kit.shared.generated.resources.version_code
import org.tool.kit.theme.AppTheme
import org.tool.kit.utils.LottieAnimation
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.getImageRequest

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/8 16:13
 * @Description : 渲染 APK 信息、图标和拖放提示，读取与复制操作通过回调提交
 * @Version     : 1.0
 */
@Composable
fun ApkInformationScreen(
    state: ApkInformationUiState,
    useDarkTheme: Boolean,
    onIntent: (ApkInformationIntent) -> Unit,
    onPickFile: () -> Unit,
    dragging: Boolean,
    dropTarget: DragAndDropTarget,
) {
    if (state.phase == ApkInformationPhase.Idle) ApkInformationLottie(useDarkTheme)
    ApkInformationBox(state, useDarkTheme) { onIntent(ApkInformationIntent.CopyText(it)) }
    ApkDraggingBox(state.phase == ApkInformationPhase.Idle, onPickFile, dragging, dropTarget)
}

/**
 * 主页动画
 */
@Composable
private fun ApkInformationLottie(useDarkTheme: Boolean) {
    Box(
        modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
    ) {
        if (useDarkTheme) {
            LottieAnimation("files/lottie_main_2_dark.json")
        } else {
            LottieAnimation("files/lottie_main_2_light.json")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkDraggingBox(expanded: Boolean, onPickFile: () -> Unit, dragging: Boolean, dropTarget: DragAndDropTarget) {
    UploadAnimate(dragging)
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = accept@{ true },
                target = dropTarget
            )
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FileButton(
                value = if (dragging) {
                    stringResource(Res.string.let_go)
                } else {
                    stringResource(Res.string.upload_apk)
                }, expanded = expanded, onClick = onPickFile
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkInformationBox(
    state: ApkInformationUiState,
    useDarkTheme: Boolean,
    onCopy: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = state.phase == ApkInformationPhase.Result, enter = fadeIn(), exit = fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 14.dp, end = 14.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)
            ) {
                state.result?.let { apkInformation ->
                    LazyColumn {
                        item {
                            AppInfoItem(
                                stringResource(Res.string.app_name),
                                apkInformation.label,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.version),
                                apkInformation.versionName,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.version_code),
                                apkInformation.versionCode,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.package_name),
                                apkInformation.packageName,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.compile_sdk_version),
                                apkInformation.compileSdkVersion,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.minimum_sdk_version),
                                apkInformation.minSdkVersion,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.target_sdk_version),
                                apkInformation.targetSdkVersion,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.ABIs),
                                apkInformation.nativeCode,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.file_md5),
                                apkInformation.md5,
                                onCopy
                            )
                        }
                        item {
                            AppInfoItem(
                                stringResource(Res.string.size),
                                apkInformation.size.formatFileSize(scale = 1, withInterval = true),
                                onCopy
                            )
                        }
                        apkInformation.channel?.let { channel ->
                            item {
                                AppInfoItem(stringResource(Res.string.channel), channel, onCopy)
                            }
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
                                AppTheme(useDarkTheme) {
                                    Surface(color = MaterialTheme.colorScheme.background) {
                                        CoilZoomAsyncImage(
                                            model = getImageRequest(image),
                                            contentDescription = "zoom image",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                        AsyncImage(
                            model = getImageRequest(image),
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
private fun AppInfoItem(title: String, value: String, onCopy: (String) -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp).height(36.dp), onClick = {
        onCopy(value)
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
private fun PermissionsList(permissions: List<String>?) {
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