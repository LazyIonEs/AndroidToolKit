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
    override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T = withContext(io) {
        buildMutex.withLock {
            ensureActive()
            Files.createDirectories(cacheRoot.toPath())
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
