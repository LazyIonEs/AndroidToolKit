package org.tool.kit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("org.tool.kit.main")
fun main() = application {
    Window(
        onCloseRequest = {
            logger.info { "onCloseRequest 退出应用" }
            exitApplication()
        },
        title = "AndroidToolKit",
        icon = WindowIcon()
    ) {
        App()
    }
}