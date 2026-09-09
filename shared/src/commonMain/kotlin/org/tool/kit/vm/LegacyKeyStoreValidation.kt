package org.tool.kit.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.model.Sign

data class SignValidationState(
    val aliasesPending: Boolean = false,
    val aliasPasswordPending: Boolean = false,
    val aliasPasswordValid: Boolean? = null,
) {
    val pending: Boolean get() = aliasesPending || aliasPasswordPending
}

/** Captured credentials never enter public state or logs. */
private data class StoreCredentials(val path: String, val password: String) {
    override fun toString() = "StoreCredentials(redacted)"
}

private data class AliasCredentials(val store: StoreCredentials, val alias: String?, val password: String) {
    override fun toString() = "AliasCredentials(redacted)"
}

class LegacySignValidation(
    scope: CoroutineScope,
    private val storage: StorageRepository,
    private val keyStores: KeyStoreRepository,
    private val onAliases: (List<String>?) -> Unit,
) {
    private val aliasesRequest = LatestRequest(scope)
    private val passwordRequest = LatestRequest(scope)
    private var store: StoreCredentials? = null
    private var key: AliasCredentials? = null
    private val _state = MutableStateFlow(SignValidationState())
    val state = _state.asStateFlow()

    fun refreshAliasPassword(form: Sign) {
        passwordRequest.cancel()
        key = null
        _state.update { it.copy(aliasPasswordValid = null, aliasPasswordPending = false) }
        formChanged(form)
    }

    fun formChanged(form: Sign) {
        val nextStore = StoreCredentials(form.keyStorePath, form.keyStorePassword)
        if (nextStore != store) {
            store = nextStore
            aliasesRequest.cancel()
            _state.update { it.copy(aliasesPending = false) }
        }
        val nextKey = if (!form.keyStoreAlisaList.isNullOrEmpty() && form.keyStoreAlisaPassword.isNotBlank()) {
            AliasCredentials(nextStore, form.keyStoreAlisaList?.getOrNull(form.keyStoreAlisaIndex), form.keyStoreAlisaPassword)
        } else null
        if (nextKey == key) return
        key = nextKey
        passwordRequest.cancel()
        _state.update { it.copy(aliasPasswordValid = null, aliasPasswordPending = nextKey != null) }
        if (nextKey != null) {
            passwordRequest.launch(block = {
                keyStores.validateAliasPassword(nextKey.store.path, nextKey.store.password, nextKey.alias, nextKey.password)
            }) { valid ->
                _state.update { it.copy(aliasPasswordValid = valid, aliasPasswordPending = false) }
            }
        }
    }

    /** Mirrors the old password-field callback: an unavailable path leaves its aliases alone. */
    fun passwordChanged(form: Sign) {
        refreshAliasPassword(form)
        val credentials = StoreCredentials(form.keyStorePath, form.keyStorePassword)
        if (credentials.path.isBlank()) return
        _state.update { it.copy(aliasesPending = true) }
        aliasesRequest.launch(block = {
            if (storage.inspectPath(credentials.path).isFile) {
                true to keyStores.loadAliases(credentials.path, credentials.password)
            } else false to null
        }) { (fileAvailable, aliases) ->
            _state.update { it.copy(aliasesPending = false) }
            if (fileAvailable) onAliases(aliases)
        }
    }
}

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
