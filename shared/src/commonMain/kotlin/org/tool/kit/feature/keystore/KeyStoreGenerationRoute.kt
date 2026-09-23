package org.tool.kit.feature.keystore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.feature.ui.FeaturePage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

/** 连接密钥库页面状态和输出目录选择，并在进入页面时复查目录。 */
@Composable
fun KeyStoreGenerationRoute(viewModel: KeyStoreGenerationViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberDirectoryPickerRequest { viewModel.onIntent(KeyStoreGenerationIntent.OutputPathChanged(it)) }
    LaunchedEffect(viewModel) { viewModel.onIntent(KeyStoreGenerationIntent.Refresh) }
    FeaturePage(busy = state.busy) {
        KeyStoreGenerationScreen(state, viewModel::onIntent, picker)
    }
}
