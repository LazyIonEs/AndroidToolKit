package org.tool.kit.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.domain.repository.PathMetadata
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import java.io.File

class JvmStorageRepository(private val io: CoroutineDispatcher) : StorageRepository {
    /** 汇总 JVM 可见的文件系统根目录容量，不进行逐文件扫描。 */
    override suspend fun readCapacity(): StorageCapacity = withContext(io) {
        var total = 0L
        var usable = 0L
        File.listRoots()?.forEach { root ->
            total += root.totalSpace
            usable += root.usableSpace
        }
        StorageCapacity(total, usable)
    }

    /** 在 IO 线程读取文件类型和名称，调用方据此做路径校验。 */
    override suspend fun inspectPath(path: String): PathMetadata = withContext(io) {
        val file = File(path)
        PathMetadata(file.isFile, file.isDirectory, file.name)
    }
}
