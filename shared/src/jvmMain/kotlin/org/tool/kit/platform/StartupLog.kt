package org.tool.kit.platform

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("App")
internal actual fun logAppStartup(version: String) { logger.info { "启动App, 应用版本号: $version" } }
