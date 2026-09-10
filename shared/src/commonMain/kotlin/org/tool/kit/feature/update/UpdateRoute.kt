package org.tool.kit.feature.update

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.app.DesktopActionHandler

/** 展示更新弹窗并消费带编号的安装请求；仅在系统成功打开安装包后退出。 */
@Composable
fun UpdateRoute(viewModel: UpdateViewModel, actions: DesktopActionHandler) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UpdateDialog(state, viewModel::onIntent)
    val request = state.installRequest
    // 以请求编号作为副作用键，重组不会重复打开同一个安装包。
    LaunchedEffect(request?.id) {
        if (request != null) {
            val opened = actions.openInstaller(request.path)
            viewModel.onIntent(UpdateIntent.InstallHandled(request.id))
            if (opened) actions.exitAfterInstall()
        }
    }
}
