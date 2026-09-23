package org.tool.kit.feature.junk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.StorageRepository

data class JunkOutputCheck(val path: String = "", val valid: Boolean? = true) {
    val pending: Boolean get() = path.isNotBlank() && valid == null
    val isError: Boolean get() = path.isNotBlank() && valid == false
}

class JunkOutputValidation(scope: CoroutineScope, private val storage: StorageRepository) {
    private val request = LatestRequest(scope)
    private val _state = MutableStateFlow(JunkOutputCheck())
    val state = _state.asStateFlow()
    /** 路径变化时重新校验目录；force 用于路径未变但文件系统可能变化的刷新。 */
    fun validate(path: String, force: Boolean = false) {
        if (!force && _state.value.path == path) return
        request.cancel()
        val check = JunkOutputCheck(path, if (path.isBlank()) true else null)
        _state.value = check
        if (path.isBlank()) return
        request.launch(block = {
            try { storage.inspectPath(path).isDirectory }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        }) { _state.value = check.copy(valid = it) }
    }
    /** 强制复查当前路径，不复用之前的可用性判断。 */
    fun refresh() = validate(_state.value.path, force = true)
    /** 使当前路径查询失效，并尝试取消底层任务。 */
    fun close() = request.cancel()
}
