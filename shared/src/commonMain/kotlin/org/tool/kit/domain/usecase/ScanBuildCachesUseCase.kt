package org.tool.kit.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.repository.BuildCachesRepository

class ScanBuildCachesUseCase(private val repository: BuildCachesRepository) {
    /** 返回逐项扫描结果，由收集方控制扫描的启动和取消。 */
    operator fun invoke(root: String) = repository.scan(root)
    operator fun invoke(request: org.tool.kit.domain.cleaner.CleanerScanRequest, onIssue: (String) -> Unit = {}) = repository.scan(request, onIssue)
}

class DeleteBuildCachesUseCase(private val repository: BuildCachesRepository) {
    /** 固定本次选择列表后逐个删除，逐项发出结果以便 UI 更新进度。 */
    operator fun invoke(directories: List<BuildDirectory>): kotlinx.coroutines.flow.Flow<org.tool.kit.domain.cleaner.DeleteBuildCacheResult> {
        // 在创建 Flow 时复制选择，后续页面勾选变化不会改变这批删除任务。
        val selected = directories.toList()
        return flow {
            for (directory in selected) {
                currentCoroutineContext().ensureActive()
                val result = repository.delete(directory)
                currentCoroutineContext().ensureActive()
                emit(result)
            }
        }
    }
}
