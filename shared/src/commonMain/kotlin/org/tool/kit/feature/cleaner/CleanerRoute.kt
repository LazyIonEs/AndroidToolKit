package org.tool.kit.feature.cleaner

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.LocalIsAppDarkTheme
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

/** Connect the page to the existing desktop picker and refresh capacity on window focus. */
@Composable
fun CleanerRoute(viewModel: CleanerViewModel = koinViewModel(), useDarkTheme: Boolean = LocalIsAppDarkTheme.current) {
    val rulesViewModel: CleanerRulesViewModel = koinViewModel()
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
    CleanerContent(viewModel, rulesViewModel, useDarkTheme, selectDirectory,
        onOpenDirectory = { path -> scope.launch { actions.openDirectory(path) } })
}

@Composable
internal fun CleanerContent(
    viewModel: CleanerViewModel,
    rulesViewModel: CleanerRulesViewModel,
    useDarkTheme: Boolean,
    selectDirectory: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    rulesWindow: @Composable (CleanerRulesUiState, () -> Unit, (Boolean) -> Unit, Int) -> Unit = { rulesState, dismiss, saved, focus ->
        CleanerRulesWindow(rulesState, rulesViewModel, useDarkTheme, focus, dismiss, saved)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rulesState by rulesViewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var runAfterClose by remember { mutableStateOf(false) }
    val currentSelectDirectory by rememberUpdatedState(selectDirectory)
    CleanerScreen(state, viewModel::onIntent, selectDirectory, onOpenDirectory,
        onManageRules = {
            if (state.phase == CleanerPhase.Idle && state.rulesReady) {
                if (editing) focusRequest++
                else { rulesViewModel.open(); editing = true }
            }
        })
    if (editing) rulesWindow(rulesState, { editing = false }, { tryRun ->
        editing = false
        runAfterClose = tryRun
    }, focusRequest)
    // Run after composition has disposed the editor window, so the picker belongs to the main UI.
    LaunchedEffect(editing, runAfterClose) {
        if (!editing && runAfterClose) {
            runAfterClose = false
            currentSelectDirectory()
        }
    }
}
