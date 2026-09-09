package org.tool.kit.feature.apk

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest
import org.tool.kit.model.FileSelectorType
import kotlin.io.path.pathString

@Composable
fun ApkToolRoute(viewModel: ApkToolViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(ApkToolIntent.Refresh) }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> viewModel.onIntent(ApkToolIntent.FilesDropped(files.map { it.toAbsolutePath().pathString })) }
    })
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(ApkToolIntent.OutputPathChanged(it)) }
    val pickIcon = rememberFilePickerRequest(FileSelectorType.IMAGE) { viewModel.onIntent(ApkToolIntent.IconPathChanged(it)) }
    val pickKey = rememberFilePickerRequest(FileSelectorType.KEY) { viewModel.onIntent(ApkToolIntent.KeyPathChanged(it)) }
    ApkToolScreen(state, viewModel::onIntent, pickOutput, pickIcon, pickKey, dragging, target)
}
