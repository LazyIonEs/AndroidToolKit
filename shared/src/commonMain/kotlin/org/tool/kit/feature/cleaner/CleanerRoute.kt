package org.tool.kit.feature.cleaner

import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.LocalIsAppDarkTheme
import org.tool.kit.feature.ui.FeaturePage
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

/** 连接扫描选择与桌面目录操作，并在窗口重新获得焦点时刷新容量。 */
@Composable
fun CleanerRoute(viewModel: CleanerViewModel = koinViewModel(), useDarkTheme: Boolean = LocalIsAppDarkTheme.current) {
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
    FeaturePage(busy = state.phase == CleanerPhase.Deleting, useDarkTheme = useDarkTheme) {
        CleanerScreen(state, useDarkTheme, viewModel::onIntent, selectDirectory,
            onOpenDirectory = { path -> scope.launch { actions.openDirectory(path) } })
    }
}
