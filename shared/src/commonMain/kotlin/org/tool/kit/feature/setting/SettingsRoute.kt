package org.tool.kit.feature.setting

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.icon

import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tool.kit.feature.ui.rememberDirectoryPickerRequest
import org.tool.kit.feature.update.UpdateViewModel
import org.tool.kit.feature.update.UpdateIntent
import org.tool.kit.feature.app.DesktopActionHandler

/** 组合设置、更新检查和桌面操作回调，负责打开许可证窗口。 */
@Composable
fun SettingsRoute(updateViewModel: UpdateViewModel, actions: DesktopActionHandler, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by updateViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.onIntent(SettingsIntent.Refresh) }
    val picker = rememberDirectoryPickerRequest { viewModel.onIntent(SettingsIntent.OutputPath(it)) }
    SettingsScreen(state, update.checking, viewModel::onIntent,
        onCheckUpdate = { updateViewModel.onIntent(UpdateIntent.Check()) }, onPickOutput = picker,
        onBrowse = actions::browse, onOpenLog = { scope.launch { actions.openDirectory(state.logFilePath) } },
        librariesWindow = { AboutLibrariesWindow(it) })
}

/** 创建独立许可证窗口，通过挂起加载器读取随应用打包的依赖元数据。 */
@Composable
internal fun AboutLibrariesWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(size = DpSize(800.dp, 600.dp))
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "Open Source Licenses",
        icon = painterResource(Res.drawable.icon),
        alwaysOnTop = true
    ) {
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            detailMode = LibraryDetailMode.Sheet
        )
    }
}

