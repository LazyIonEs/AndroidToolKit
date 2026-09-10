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
    fun refresh() = validate(_state.value.path, force = true)
    fun close() = request.cancel()
}
