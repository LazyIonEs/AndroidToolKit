package org.tool.kit.tests.support

import org.tool.kit.feature.app.DesktopActionHandler

internal class RecordingDesktopActions : DesktopActionHandler {
    val events = mutableListOf<String>()
    var openSucceeds = true
    override suspend fun openDirectory(path: String?) { events += "directory:$path" }
    override suspend fun openInstaller(path: String): Boolean { events += "install:$path"; return openSucceeds }
    override suspend fun logFilePath(): String? = null
    override fun browse(url: String) { events += "browse:$url" }
    override fun exitAfterInstall() { events += "exit" }
}
