package org.tool.kit.domain.repository

import kotlinx.coroutines.flow.Flow
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.cleaner.DeleteBuildCacheResult

interface BuildCachesRepository {
    /** 以可取消的流逐项报告构建目录及其大小、修改时间快照。 */
    fun scan(root: String): Flow<BuildDirectory>
    /** 尝试删除指定扫描项，并返回删除结果及操作后的文件状态。 */
    suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult
}
