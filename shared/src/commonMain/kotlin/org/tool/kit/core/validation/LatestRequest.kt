package org.tool.kit.core.validation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 在主线程管理只接受最新结果的异步请求。
 * 取消旧协程之外还校验版本号，避免不响应取消的底层调用返回后覆盖新输入。
 */
class LatestRequest(private val scope: CoroutineScope) {
    private var revision = 0L
    private var job: Job? = null

    /** 使当前请求失效并尝试取消任务；即使底层调用继续执行，其结果也不能再被接受。 */
    fun cancel() {
        revision++
        job?.cancel()
        job = null
    }

    /** 替换前一个请求，仅当本次任务仍有效且未被取消时回调 [onResult]。 */
    fun <T> launch(block: suspend () -> T, onResult: (T) -> Unit) {
        cancel()
        val requestRevision = revision
        job = scope.launch {
            val result = block()
            // 同时检查协程状态和请求版本：仅依赖 Job.cancel 无法拦住不可取消调用的迟到结果。
            if (isActive && requestRevision == revision) onResult(result)
        }
    }
}
