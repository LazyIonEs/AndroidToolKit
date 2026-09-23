package org.tool.kit.domain.repository

data class StorageCapacity(val totalBytes: Long, val usableBytes: Long) {
    val usedBytes: Long get() = totalBytes - usableBytes
}

data class PathMetadata(val isFile: Boolean, val isDirectory: Boolean, val fileName: String? = null)

interface StorageRepository {
    /** 取得平台存储容量，所有容量字段均以字节为单位。 */
    suspend fun readCapacity(): StorageCapacity
    /** 读取路径当前的文件类型和名称，不改变文件系统。 */
    suspend fun inspectPath(path: String): PathMetadata
}
