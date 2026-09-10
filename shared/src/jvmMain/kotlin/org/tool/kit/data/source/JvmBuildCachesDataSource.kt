package org.tool.kit.data.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.withContext
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.cleaner.DeleteBuildCacheResult
import org.tool.kit.domain.repository.BuildCachesRepository
import java.io.File

class JvmBuildCachesDataSource(private val io: CoroutineDispatcher) : BuildCachesRepository {
    /** 在 IO 线程遍历最多十层目录，逐项发出 build 目录快照；目录大小另行递归统计。 */
    override fun scan(root: String) = flow {
        val context = currentCoroutineContext()
        val directory = File(root)
        directory.walk().maxDepth(10)
            // 进入 build 后不再向它的子目录递归查找，避免把内部缓存重复列为独立项目。
            .onEnter { file -> context.ensureActive(); file.parentFile?.nameWithoutExtension != "build" }
            .filter { file -> context.ensureActive(); file.isDirectory && file.nameWithoutExtension == "build" }
            .forEach { file ->
                // Same getFileLength rule: count directory entries as well as files, without a depth limit.
                val length = if (file.isDirectory) {
                    var sum = 0L
                    file.walk().forEach { entry -> context.ensureActive(); sum += entry.length() }
                    sum
                } else file.length()
                context.ensureActive()
                emit(BuildDirectory(directory.absolutePath, file.absolutePath,
                    file.absolutePath.replace(directory.absolutePath + File.separatorChar, ""),
                    length, file.lastModified(), file.isDirectory, file.exists()))
            }
    }.flowOn(io).buffer(0)

    /** 删除扫描项后再次读取存在性和目录类型，供页面反映删除后的真实状态。 */
    override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult = withContext(io) {
        currentCoroutineContext().ensureActive()
        val file = File(directory.path)
        val deleted = try { file.deleteRecursively() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        currentCoroutineContext().ensureActive()
        DeleteBuildCacheResult(directory, deleted,
            runCatching { file.isDirectory }.getOrDefault(false),
            runCatching { file.exists() }.getOrDefault(false))
    }
}
