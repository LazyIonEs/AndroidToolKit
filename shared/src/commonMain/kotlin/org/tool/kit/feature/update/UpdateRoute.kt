package org.tool.kit.feature.update

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.feature.ui.UpdateDialog

@Composable
fun UpdateRoute(viewModel: UpdateViewModel, actions: DesktopActionHandler) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UpdateDialog(state, viewModel::onIntent)
    val request = state.installRequest
    LaunchedEffect(request?.id) {
        if (request != null) {
            val opened = actions.openInstaller(request.path)
            viewModel.onIntent(UpdateIntent.InstallHandled(request.id))
            if (opened) actions.exitAfterInstall()
        }
    }
}
