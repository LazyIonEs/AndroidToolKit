package org.tool.kit.feature.apk

import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.feature.ui.FeaturePage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.LocalIsAppDarkTheme
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.model.FileSelectorType
import org.tool.kit.utils.isApk

/** 连接 APK 信息状态、文件选择和拖放事件，Screen 仅接收可展示数据与回调。 */
@Composable
fun ApkInformationRoute(viewModel: ApkInformationViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    val selectFile: (String) -> Unit = { path -> apkInformationFileIntent(path)?.let(viewModel::onIntent) }
    val picker = rememberFilePickerRequest(FileSelectorType.APK, onSelected = selectFile)
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> files.firstOrNull()?.let { selectFile(it) } }
    })
    FeaturePage(busy = state.busy) {
        ApkInformationScreen(state, LocalIsAppDarkTheme.current, viewModel::onIntent, picker, dragging, target)
    }
}

/** Receives the first item after platform existence filtering of the complete list. */
internal fun apkInformationFileIntent(path: String): ApkInformationIntent? =
    if (path.isApk) ApkInformationIntent.ReadApk(path) else null
