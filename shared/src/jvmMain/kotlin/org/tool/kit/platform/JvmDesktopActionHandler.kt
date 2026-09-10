package org.tool.kit.platform

import kotlinx.coroutines.withContext
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.app.shutdownAppSession
import org.tool.kit.utils.browseFileDirectory
import org.tool.kit.utils.getLogFile
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

class JvmDesktopActionHandler(private val dispatchers: AppDispatchers) : DesktopActionHandler {
    override suspend fun openDirectory(path: String?) = withContext(dispatchers.io) { browseFileDirectory(path?.let(::File)) }
    override suspend fun logFilePath(): String? = withContext(dispatchers.io) { getLogFile()?.takeIf { it.exists() }?.path }
    override fun browse(url: String) { Desktop.getDesktop().browse(URI(url)) }
    /** 先确认文件存在，再在主线程交给系统打开；失败时返回 false，应用继续运行。 */
    override suspend fun openInstaller(path: String): Boolean {
        if (!withContext(dispatchers.io) { File(path).exists() }) return false
        return withContext(dispatchers.main) {
            try { Desktop.getDesktop().open(File(path)); true }
            catch (failure: Exception) {
                io.github.oshai.kotlinlogging.KotlinLogging.logger("UpdateDialog").error(failure) { "org.tool.kit.UpdateDialog 打开安装文件异常, 异常信息: ${failure.message}" }
                false
            }
        }
    }
    /** 释放应用会话后退出进程，由安装包打开成功的路径调用。 */
    override fun exitAfterInstall() { shutdownAppSession(); exitProcess(0) }
}
