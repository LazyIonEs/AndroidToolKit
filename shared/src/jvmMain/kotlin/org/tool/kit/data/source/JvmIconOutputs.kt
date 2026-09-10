package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.repository.IconOutputs
import org.tool.kit.domain.repository.IconOutputSession
import java.io.File
import java.io.IOException

class JvmIconOutputs(private val io: CoroutineDispatcher) : IconOutputs {
    /** 串行管理一次完整图标输出，避免多个窗口同时覆盖同名目标或缩放临时文件。 */
    override suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T = withContext(io) {
        outputMutex.withLock {
            ensureActive()
            val directory = File(request.outputPath, request.fileDir)
            block(object : IconOutputSession {
                override val outputDirectory = directory.path
                /** 为一种密度准备输出和缩放临时路径，退出时仅回收本次预留的临时文件。 */
                override suspend fun <R> density(name: String, suffix: String, block: suspend (IconOutputFiles) -> R): R {
                    val output = File(directory, "${request.iconDir}-$name/${request.iconName}$suffix")
                    val temporary = File(directory, "${request.iconDir}-$name/${request.iconName}_resize$suffix")
                    var reserved = false
                    try {
                        currentCoroutineContext().ensureActive()
                        output.parentFile.mkdirs()
                        output.delete()
                        if (temporary.exists() && !temporary.delete()) {
                            throw IOException("Cannot replace temporary icon: ${temporary.path}")
                        }
                        // 只有成功取得临时路径后才承担清理责任，避免异常时误删不属于本次操作的文件。
                        reserved = true
                        return block(IconOutputFiles(output.path, temporary.path))
                    } finally {
                        // A synchronous FFI call returns before this finally runs, even after cancellation.
                        // Never remove its parent directory or any final/previously completed image.
                        if (reserved) withContext(NonCancellable) {
                            if (temporary.exists() && !temporary.delete()) {
                                throw IOException("Cannot clean temporary icon: ${temporary.path}")
                            }
                        }
                    }
                }
            })
        }
    }

    companion object { private val outputMutex = Mutex() }
}
