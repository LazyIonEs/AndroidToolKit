package org.tool.kit.feature.iconfactory

import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import org.tool.kit.feature.ui.*
import org.tool.kit.model.FileSelectorType
import org.tool.kit.utils.getFileImageRequest

data class IconImageUi(val path: String, val request: ImageRequest)

/** 将图标路径转换为图片请求，管理设置面板进出事件，并向纯 UI 传递状态。 */
@Composable
fun IconFactoryRoute(viewModel: IconFactoryViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.onIntent(IconFactoryIntent.PageEntered)
        // 页面 ViewModel 可以继续存活，但离开页面时应丢弃未提交的面板草稿。
        onDispose { viewModel.onIntent(IconFactoryIntent.PageLeft) }
    }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files ->
            if (!state.busy && !state.sheetOpen) viewModel.onIntent(IconFactoryIntent.FilesDropped(files))
        }
    })
    val pickIcon = rememberFilePickerRequest(FileSelectorType.IMAGE) { viewModel.onIntent(IconFactoryIntent.FileSelected(it)) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(IconFactoryIntent.OutputPathChanged(it)) }
    val inputImage = remember(state.form.inputPath) { state.form.inputPath?.let { IconImageUi(it, getFileImageRequest(it)) } }
    val resultImages = remember(state.result) { state.result?.map { result ->
        if (result.previewAvailable) IconImageUi(result.path, getFileImageRequest(result.path)) else null
    } }
    IconFactoryScreen(state, inputImage, resultImages,
        viewModel::onIntent, pickOutput, pickIcon, dragging, target)
}
