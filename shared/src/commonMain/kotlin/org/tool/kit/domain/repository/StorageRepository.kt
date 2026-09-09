package org.tool.kit.domain.repository

data class StorageCapacity(val totalBytes: Long, val usableBytes: Long) {
    val usedBytes: Long get() = totalBytes - usableBytes
}

data class PathMetadata(val isFile: Boolean, val isDirectory: Boolean)

interface StorageRepository {
    suspend fun readCapacity(): StorageCapacity
    suspend fun inspectPath(path: String): PathMetadata
}
