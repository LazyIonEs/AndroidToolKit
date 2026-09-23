package org.tool.kit.feature.signature

import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.feature.ui.FeaturePage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest
import org.tool.kit.model.FileSelectorType

/** 连接签名表单、预设和文件选择，将拖放结果交给 ViewModel 分类处理。 */
@Composable
fun ApkSigningRoute(viewModel: ApkSigningViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(ApkSigningIntent.Refresh) }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> viewModel.onIntent(ApkSigningIntent.FilesDropped(files)) }
    })
    val pickApk = rememberFilePickerRequest(FileSelectorType.APK) { viewModel.onIntent(ApkSigningIntent.ApkPathChanged(it)) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(ApkSigningIntent.OutputPathChanged(it)) }
    val pickKey = rememberFilePickerRequest(FileSelectorType.KEY) { viewModel.onIntent(ApkSigningIntent.KeyPathChanged(it)) }
    FeaturePage(busy = state.busy) {
        ApkSigningScreen(state, viewModel.presets.items, viewModel::onIntent, pickApk, pickOutput, pickKey, dragging, target)
    }
}
