package org.tool.kit.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.StorageRepository

enum class LegacyPathField {
    SIGNING_APK, SIGNING_OUTPUT, SIGNING_KEYSTORE,
    APK_TOOL_OUTPUT, APK_TOOL_ICON, APK_TOOL_KEYSTORE, JUNK_OUTPUT
}

enum class PathKind { FILE, DIRECTORY }

data class PathCheck(val path: String, val kind: PathKind, val valid: Boolean? = null) {
    val pending: Boolean get() = path.isNotBlank() && valid == null
    val isError: Boolean get() = path.isNotBlank() && valid == false
}

/** Temporary per-form validation slots; feature VMs will take over their own slots. */
class LegacyPathChecks(private val scope: CoroutineScope, private val storage: StorageRepository) {
    private val requests = mutableMapOf<LegacyPathField, LatestRequest>()
    private val _state = MutableStateFlow<Map<LegacyPathField, PathCheck>>(emptyMap())
    val state = _state.asStateFlow()

    fun refresh(vararg fields: LegacyPathField) {
        fields.forEach { field ->
            _state.value[field]?.let { validate(field, it.path, it.kind, force = true) }
        }
    }

    fun validate(field: LegacyPathField, path: String, kind: PathKind, force: Boolean = false) {
        val previous = _state.value[field]
        if (!force && previous?.path == path && previous.kind == kind) return
        val request = requests.getOrPut(field) { LatestRequest(scope) }
        request.cancel()
        val check = PathCheck(path, kind, if (path.isBlank()) true else null)
        _state.update { it + (field to check) }
        if (path.isBlank()) return
        request.launch(block = { storage.inspectPath(path) }) { metadata ->
            val valid = if (kind == PathKind.FILE) metadata.isFile else metadata.isDirectory
            _state.update { it + (field to check.copy(valid = valid)) }
        }
    }
}
