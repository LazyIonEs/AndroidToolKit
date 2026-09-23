package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkBuildWorkspaces
import java.io.File
import java.nio.file.Files

/** Serializes builds, including signing, across window containers in this process. */
class JvmApkBuildWorkspaces(private val cacheRoot: File, private val io: CoroutineDispatcher) : ApkBuildWorkspaces {
    /** 持有进程级构建锁直到构建及可选签名结束，随后清理本次独占的工作目录。 */
    override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T = withContext(io) {
        buildMutex.withLock {
            ensureActive()
            Files.createDirectories(cacheRoot.toPath())
            // 解码目录和 framework 缓存同属本次临时根目录，防止并发构建复用中间文件。
            val owned = Files.createTempDirectory(cacheRoot.toPath(), "AndroidToolKit-apktool-").toFile()
            try {
                block(ApkBuildWorkspace(File(owned, "decoded").path,
                    File(request.outputDirectory, request.outputFileName).path, File(owned, "framework").path))
            } finally {
                // Allocation and cleanup stay in this context, even if return dispatch is cancelled.
                // Never remove the cache root, outputs or another operation's files.
                withContext(NonCancellable) { check(owned.deleteRecursively()) { "Cannot clean build workspace: ${owned.path}" } }
            }
        }
    }

    companion object { private val buildMutex = Mutex() }
}
