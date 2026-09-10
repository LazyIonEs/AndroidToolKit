package org.tool.kit.feature.iconfactory

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import org.tool.kit.LocalIsAppDarkTheme
import org.tool.kit.feature.ui.*
import org.tool.kit.model.FileSelectorType
import org.tool.kit.utils.getImageRequest
import java.io.File
import kotlin.io.path.pathString

data class IconImageUi(val path: String, val request: ImageRequest)

@Composable
fun IconFactoryRoute(viewModel: IconFactoryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.onIntent(IconFactoryIntent.PageEntered)
        onDispose { viewModel.onIntent(IconFactoryIntent.PageLeft) }
    }
    var dragging by remember { mutableStateOf(false) }
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> viewModel.onIntent(IconFactoryIntent.FilesDropped(files.map { it.toAbsolutePath().pathString })) }
    })
    val pickIcon = rememberFilePickerRequest(FileSelectorType.IMAGE) { viewModel.onIntent(IconFactoryIntent.FileSelected(it)) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(IconFactoryIntent.OutputPathChanged(it)) }
    val inputImage = remember(state.form.inputPath) { state.form.inputPath?.let { IconImageUi(it, getImageRequest(File(it))) } }
    val resultImages = remember(state.result) { state.result?.map { result ->
        if (result.previewAvailable) IconImageUi(result.path, getImageRequest(File(result.path))) else null
    } }
    IconFactoryScreen(state, inputImage, resultImages, LocalIsAppDarkTheme.current,
        viewModel::onIntent, pickOutput, pickIcon, dragging, target)
}
