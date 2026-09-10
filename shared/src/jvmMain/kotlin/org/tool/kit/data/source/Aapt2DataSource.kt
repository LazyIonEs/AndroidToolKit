package org.tool.kit.data.source

import java.io.File
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.core.process.*
import org.tool.kit.domain.apk.ApkCommandFailed
import org.tool.kit.utils.resourcesDirWithOs
import org.tool.kit.utils.isWindows

class Aapt2Locator(
    private val resourceDirectory: () -> String = { resourcesDirWithOs },
    private val windows: Boolean = isWindows,
) {
    /** 定位随应用打包的当前系统 aapt2，并尝试补齐执行权限。 */
    fun locate(): File = File(resourceDirectory(), if (windows) "aapt2.exe" else "aapt2").apply {
        if (!canExecute()) setExecutable(true)
    }
}
class Aapt2DataSource(
    private val locator: Aapt2Locator,
    private val runner: ProcessRunner,
    private val io: CoroutineDispatcher,
) {
    /** 执行 dump badging；非零退出视为读取失败，不能把错误输出用于信息解析。 */
    suspend fun badging(path: String): String = withContext(io) {
        val result = runner.run(ProcessRequest(locator.locate().absolutePath, listOf("dump", "badging", path)))
        if (result.exitCode != 0) throw ApkCommandFailed()
        result.stdout
    }
    /** 提取 AndroidManifest.xml 的文本树；清单提取失败不阻断其余 APK 信息读取。 */
    suspend fun manifest(path: String): String? = withContext(io) {
        try {
            val result = runner.run(ProcessRequest(locator.locate().absolutePath, listOf("dump", "xmltree", path, "--file", "AndroidManifest.xml")))
            if (result.exitCode == 0) result.stdout.trimIndent() else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            KotlinLogging.logger("Aapt2DataSource").error(error) { "Manifest extraction failed" }
            null
        }
    }
}
