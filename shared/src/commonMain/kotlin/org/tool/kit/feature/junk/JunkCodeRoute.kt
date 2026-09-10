package org.tool.kit.feature.junk

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

@Composable
fun JunkCodeRoute(viewModel: JunkCodeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(JunkCodeIntent.Refresh) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(JunkCodeIntent.OutputPathChanged(it)) }
    JunkCodeScreen(state, viewModel::onIntent, pickOutput)
}
