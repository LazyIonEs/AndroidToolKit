import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.icon
import utils.isMac
import utils.isWindows

fun main() = application {
    val applicationState = remember { ApplicationState() }
    if (isWindows && applicationState.windows.isNotEmpty()) {
        ApplicationTray(applicationState)
    }
    for (window in applicationState.windows) {
        key(window) {
            Window(window)
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ApplicationScope.ApplicationTray(state: ApplicationState) {
    Tray(
        painterResource(Res.drawable.icon),
        tooltip = "AndroidToolKit",
        menu = { ApplicationMenu(state) }
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun Window(
    state: WindowState
) = Window(
    onCloseRequest = state::close,
    title = state.title,
    icon = painterResource(Res.drawable.icon)
) {
    if (isMac) {
        MenuBar {
            Menu("文件") {
                Item("打开新的窗口", onClick = state.openNewWindow)
                Item("关闭窗口", onClick = { state.close() })
                Separator()
                Item("全部关闭", onClick = state.exit)
            }
        }
    }
    App()
}

@Composable
private fun MenuScope.ApplicationMenu(state: ApplicationState) {
    val icon = rememberVectorPainter(Icons.Rounded.Add)
    Item(text = "打开新的窗口", onClick = state::openNewWindow, icon = icon)
}

private class ApplicationState {

    private val _windows = mutableStateListOf<WindowState>()
    val windows: List<WindowState> get() = _windows

    init {
        _windows.add(WindowState())
    }

    fun openNewWindow() {
        _windows.add(WindowState())
    }

    fun exit() {
        _windows.clear()
    }

    private fun WindowState() = WindowState(
        title = "AndroidToolKit",
        openNewWindow = ::openNewWindow,
        exit = ::exit,
        _windows::remove
    )
}

private class WindowState(
    val title: String = "AndroidToolKit",
    val openNewWindow: () -> Unit,
    val exit: () -> Unit,
    private val close: (WindowState) -> Unit
) {
    fun close() = close(this)
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}