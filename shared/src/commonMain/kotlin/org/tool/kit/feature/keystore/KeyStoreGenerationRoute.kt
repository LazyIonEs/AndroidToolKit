package org.tool.kit.feature.keystore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

@Composable
fun KeyStoreGenerationRoute(viewModel: KeyStoreGenerationViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberDirectoryPickerRequest { viewModel.onIntent(KeyStoreGenerationIntent.OutputPathChanged(it)) }
    LaunchedEffect(viewModel) { viewModel.onIntent(KeyStoreGenerationIntent.Refresh) }
    KeyStoreGenerationScreen(state, viewModel::onIntent, picker)
}
