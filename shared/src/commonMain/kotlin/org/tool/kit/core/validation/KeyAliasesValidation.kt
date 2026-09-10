package org.tool.kit.core.validation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tool.kit.domain.repository.KeyStoreRepository

/** 密钥别名校验快照；pending 表示正在读取，aliases 为 null 表示未取得可用结果。 */
data class KeyAliasesState(val aliases: List<String>? = null, val pending: Boolean = false, val revision: Long = 0)

/** 随路径和密码变化重新读取别名，通过请求版本隔离过期结果。 */
class KeyAliasesValidation(scope: CoroutineScope, private val keyStores: KeyStoreRepository) {
    private val request = LatestRequest(scope)
    private val _state = MutableStateFlow(KeyAliasesState())
    val state = _state.asStateFlow()

    /** 清空别名并使进行中的读取失效，同时推进状态版本。 */
    fun reset() {
        request.cancel()
        _state.value = KeyAliasesState(revision = _state.value.revision + 1)
    }

    /** 发布加载状态后读取密钥库；成功的空库返回空列表，读取失败返回 null。 */
    fun validate(path: String, password: String) {
        val revision = _state.value.revision + 1
        _state.value = KeyAliasesState(pending = true, revision = revision)
        request.launch(block = { keyStores.loadAliases(path, password) }) { aliases ->
            _state.value = KeyAliasesState(aliases = aliases?.toList(), revision = revision)
        }
    }
}
