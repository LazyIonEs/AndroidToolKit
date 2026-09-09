package org.tool.kit.feature.signature

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest
import org.tool.kit.model.FileSelectorType
import kotlin.io.path.pathString

@Composable
fun ApkSigningRoute(viewModel: ApkSigningViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(ApkSigningIntent.Refresh) }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> viewModel.onIntent(ApkSigningIntent.FilesDropped(files.map { it.toAbsolutePath().pathString })) }
    })
    val pickApk = rememberFilePickerRequest(FileSelectorType.APK) { viewModel.onIntent(ApkSigningIntent.ApkPathChanged(it)) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(ApkSigningIntent.OutputPathChanged(it)) }
    val pickKey = rememberFilePickerRequest(FileSelectorType.KEY) { viewModel.onIntent(ApkSigningIntent.KeyPathChanged(it)) }
    ApkSigningScreen(state, viewModel.presets.items, viewModel::onIntent, pickApk, pickOutput, pickKey, dragging, target)
}
