package org.tool.kit.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.domain.repository.PathMetadata
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import java.io.File

class JvmStorageRepository(private val io: CoroutineDispatcher) : StorageRepository {
    override suspend fun readCapacity(): StorageCapacity = withContext(io) {
        var total = 0L
        var usable = 0L
        File.listRoots()?.forEach { root ->
            total += root.totalSpace
            usable += root.usableSpace
        }
        StorageCapacity(total, usable)
    }

    override suspend fun inspectPath(path: String): PathMetadata = withContext(io) {
        val file = File(path)
        PathMetadata(file.isFile, file.isDirectory, file.name)
    }
}
