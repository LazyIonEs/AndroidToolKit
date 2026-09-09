package org.tool.kit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.context.startKoin
import org.tool.kit.app.shutdownAppSession
import org.tool.kit.di.desktopModules

private val logger = KotlinLogging.logger("org.tool.kit.main")
fun main() {
    startKoin { modules(desktopModules()) }
    try {
        application {
            Window(
                onCloseRequest = {
                    logger.info { "onCloseRequest 退出应用" }
                    shutdownAppSession()
                    exitApplication()
                },
                title = "AndroidToolKit",
                icon = WindowIcon()
            ) {
                App()
            }
        }
    } finally {
        shutdownAppSession()
    }
}
