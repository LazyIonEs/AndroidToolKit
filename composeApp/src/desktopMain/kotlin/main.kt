import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication, title = "AndroidToolKit", icon = painterResource(Res.drawable.icon)
    ) {
        App()
    }
}