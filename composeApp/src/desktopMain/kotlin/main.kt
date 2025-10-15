import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.painterResource
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.icon

private val logger = KotlinLogging.logger("main")
fun main() = application {
    Window(
        onCloseRequest = {
            logger.info { "onCloseRequest 退出应用" }
            exitApplication()
        }, title = "AndroidToolKit", icon = painterResource(Res.drawable.icon)
    ) {
        App()
    }
}