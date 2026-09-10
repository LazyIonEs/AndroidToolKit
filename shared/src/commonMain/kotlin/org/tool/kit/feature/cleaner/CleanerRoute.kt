package org.tool.kit.feature.cleaner

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

@Composable
fun CleanerRoute(viewModel: CleanerViewModel, signatureHasResult: Boolean, useDarkTheme: Boolean) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectDirectory = rememberDirectoryPickerRequest { viewModel.onIntent(CleanerIntent.Rescan(it)) }
    val actions = koinInject<DesktopActionHandler>()
    val scope = rememberCoroutineScope()
    val window = LocalWindowInfo.current
    LaunchedEffect(viewModel, window) {
        viewModel.onIntent(CleanerIntent.RefreshCapacity)
        snapshotFlow { window.isWindowFocused }.drop(1).filter { it }.collect {
            viewModel.onIntent(CleanerIntent.RefreshCapacity)
        }
    }
    CleanerScreen(state, signatureHasResult, useDarkTheme, viewModel::onIntent, selectDirectory,
        onOpenDirectory = { path -> scope.launch { actions.openDirectory(path) } })
}
