package org.tool.kit.feature.signature

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.LocalIsAppDarkTheme
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.feature.ui.rememberFilePickerRequest
import org.tool.kit.model.FileSelectorType
import org.tool.kit.utils.isApk
import org.tool.kit.utils.isKey

@Composable
fun SignatureInformationRoute(viewModel: SignatureInformationViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    val selectFile: (String) -> Unit = { path ->
        signatureFileIntent(path)?.let(viewModel::onIntent)
    }
    val picker = rememberFilePickerRequest(FileSelectorType.KEY, FileSelectorType.APK, onSelected = selectFile)
    val target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
        result.onSuccess { files -> files.firstOrNull()?.let { selectFile(it) } }
    })
    DisposableEffect(viewModel) {
        onDispose { viewModel.onIntent(SignatureInformationIntent.DismissPasswordDialog) }
    }
    SignatureInformationScreen(state, LocalIsAppDarkTheme.current, viewModel::onIntent, picker, dragging, target)
}

/** Called only after the platform has filtered the complete drop list for existence. */
internal fun signatureFileIntent(path: String): SignatureInformationIntent? = when {
    path.isApk -> SignatureInformationIntent.VerifyApk(path)
    path.isKey -> SignatureInformationIntent.KeyStoreSelected(path)
    else -> null
}
