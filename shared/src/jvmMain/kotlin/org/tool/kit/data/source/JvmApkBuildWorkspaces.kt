package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkBuildWorkspaces
import java.io.File

/** Phase 6 extraction bridge: replace the fixed resource directory in the next commit. */
class JvmApkBuildWorkspaces(private val resources: File, private val io: CoroutineDispatcher) : ApkBuildWorkspaces {
    private val mutex = Mutex()
    override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T = withContext(io) { mutex.withLock {
        val directory = File(resources, "apktool")
        check(!directory.exists()) { "Build workspace already exists: ${directory.path}" }
        check(directory.mkdirs()) { "Cannot create build workspace: ${directory.path}" }
        try {
            block(ApkBuildWorkspace(directory.path, File(request.outputDirectory, request.outputFileName).path))
        } finally {
            withContext(NonCancellable + io) { directory.deleteRecursively() }
        }
    } }
}
