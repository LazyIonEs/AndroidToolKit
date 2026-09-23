package org.tool.kit.feature.apk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.feature.ui.FeaturePage
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.model.FileSelectorType

/** 绑定构建页面的生命周期状态、路径选择和拖放，页面进入时重新校验外部路径。 */
@Composable
fun ApkToolRoute(viewModel: ApkToolViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(ApkToolIntent.Refresh) }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> viewModel.onIntent(ApkToolIntent.FilesDropped(files)) }
    })
    val pickOutput =
        rememberDirectoryPickerRequest { viewModel.onIntent(ApkToolIntent.OutputPathChanged(it)) }
    val pickIcon = rememberFilePickerRequest(FileSelectorType.IMAGE) {
        viewModel.onIntent(
            ApkToolIntent.IconPathChanged(it)
        )
    }
    val pickKey = rememberFilePickerRequest(FileSelectorType.KEY) {
        viewModel.onIntent(
            ApkToolIntent.KeyPathChanged(it)
        )
    }
    FeaturePage(busy = state.busy) {
        ApkToolScreen(state, viewModel::onIntent, pickOutput, pickIcon, pickKey, dragging, target)
    }
}
