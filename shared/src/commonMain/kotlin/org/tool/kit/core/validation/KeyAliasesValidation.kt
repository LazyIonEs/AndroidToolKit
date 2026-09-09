package org.tool.kit.core.validation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tool.kit.domain.repository.KeyStoreRepository

data class KeyAliasesState(val aliases: List<String>? = null, val pending: Boolean = false, val revision: Long = 0)

class KeyAliasesValidation(scope: CoroutineScope, private val keyStores: KeyStoreRepository) {
    private val request = LatestRequest(scope)
    private val _state = MutableStateFlow(KeyAliasesState())
    val state = _state.asStateFlow()

    fun reset() {
        request.cancel()
        _state.value = KeyAliasesState(revision = _state.value.revision + 1)
    }

    fun validate(path: String, password: String) {
        val revision = _state.value.revision + 1
        _state.value = KeyAliasesState(pending = true, revision = revision)
        request.launch(block = { keyStores.loadAliases(path, password) }) { aliases ->
            _state.value = KeyAliasesState(aliases = aliases?.toList(), revision = revision)
        }
    }
}
