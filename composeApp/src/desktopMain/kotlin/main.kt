import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AndroidToolKit",
        icon = painterResource(Res.drawable.icon)
    ) {
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}