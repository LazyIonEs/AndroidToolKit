package org.tool.kit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.context.startKoin
import org.tool.kit.app.shutdownAppSession
import org.tool.kit.di.desktopModules

private val logger = KotlinLogging.logger("org.tool.kit.main")
fun main() {
    val container = startKoin { modules(desktopModules()) }
    try {
        // main() is outside the EDT/composition; physical initialization runs on IO before Window creation.
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            container.koin.get<org.tool.kit.app.AppBootstrap>().prepare()
        }
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
