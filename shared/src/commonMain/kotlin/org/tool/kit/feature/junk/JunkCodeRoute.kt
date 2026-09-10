package org.tool.kit.feature.junk

import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel
import org.tool.kit.feature.ui.FeaturePage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest

/** 绑定垃圾代码页面状态和输出目录选择，在页面进入时刷新路径校验。 */
@Composable
fun JunkCodeRoute(viewModel: JunkCodeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(JunkCodeIntent.Refresh) }
    val pickOutput = rememberDirectoryPickerRequest { viewModel.onIntent(JunkCodeIntent.OutputPathChanged(it)) }
    FeaturePage(busy = state.busy) {
        JunkCodeScreen(state, viewModel::onIntent, pickOutput)
    }
}
