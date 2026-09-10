package org.tool.kit.domain.repository

import kotlinx.coroutines.flow.Flow
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.cleaner.DeleteBuildCacheResult

interface BuildCachesRepository {
    fun scan(root: String): Flow<BuildDirectory>
    suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult
}
