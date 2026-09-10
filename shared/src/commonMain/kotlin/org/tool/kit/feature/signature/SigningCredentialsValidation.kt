package org.tool.kit.feature.signature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository

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

/** 分别管理密钥库别名查询和别名密码验证，任一输入变化只接受新凭据的结果。 */
class SigningCredentialsValidation(
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

    /** 强制丢弃已缓存的别名密码判断，用于页面恢复后重新检查外部密钥文件。 */
    fun refreshAliasPassword(form: SigningCredentialsUi) {
        passwordRequest.cancel()
        key = null
        _state.update { it.copy(aliasPasswordValid = null, aliasPasswordPending = false) }
        formChanged(form)
    }

    /** 根据密钥库和别名凭据的变化取消旧验证；凭据相同时复用当前验证状态。 */
    fun formChanged(form: SigningCredentialsUi) {
        val nextStore = StoreCredentials(form.path, form.storePassword)
        if (nextStore != store) {
            store = nextStore
            aliasesRequest.cancel()
            _state.update { it.copy(aliasesPending = false) }
        }
        val nextKey = if (!form.aliases.isNullOrEmpty() && form.aliasPassword.isNotBlank()) {
            AliasCredentials(nextStore, form.aliases?.getOrNull(form.aliasIndex), form.aliasPassword)
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

    /** An unavailable path leaves the loaded aliases unchanged. */
    fun passwordChanged(form: SigningCredentialsUi) {
        refreshAliasPassword(form)
        val credentials = StoreCredentials(form.path, form.storePassword)
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
