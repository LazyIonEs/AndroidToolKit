package org.tool.kit.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.core.validation.KeyAliasesValidation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.signature.SignatureVerification
import org.tool.kit.domain.usecase.VerifySignatureUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.signature.SignatureInformationIntent.*
import org.tool.kit.model.CopyMode
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.formatClipboardValue

/** 管理 APK/密钥库证书校验、密码弹窗和指纹复制格式，并隔离过期校验结果。 */
class SignatureInformationViewModel(
    private val verify: VerifySignatureUseCase,
    keyStores: KeyStoreRepository,
    private val preferences: PreferencesRepository,
    private val clipboard: ClipboardWriter,
    private val effects: AppEffectSink,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignatureInformationUiState(
        copyMode = CopyMode.valueOf(preferences.state.value.copyMode.name)))
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasResult = uiState.map { it.phase == VerificationPhase.Result }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val aliasesValidation = KeyAliasesValidation(viewModelScope, keyStores)
    private val verificationRequest = LatestRequest(viewModelScope)
    private var operationId = 0L

    init {
        addCloseable {
            aliasesValidation.reset()
            verificationRequest.cancel()
            _uiState.update { it.copy(passwordDialog = null,
                phase = if (it.busy) VerificationPhase.Idle else it.phase) }
        }
        viewModelScope.launch { aliasesValidation.state.collect { aliases ->
            _uiState.update { state -> state.copy(passwordDialog = state.passwordDialog?.copy(
                aliases = aliases.aliases, selectedAlias = aliases.aliases?.firstOrNull().orEmpty(), pending = aliases.pending)) }
        } }
        viewModelScope.launch { preferences.state.collect { snapshot ->
            _uiState.update { it.copy(copyMode = CopyMode.valueOf(snapshot.copyMode.name)) }
        } }
    }

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: SignatureInformationIntent) {
        when (intent) {
            is VerifyApk -> verify(intent.path, true) { verify.apk(intent.path) }
            is KeyStoreSelected -> {
                aliasesValidation.reset()
                _uiState.update { it.copy(inputFile = intent.path,
                    passwordDialog = PasswordDialogState(intent.path, it.passwordDialog?.password.orEmpty())) }
            }
            is PasswordChanged -> {
                val dialog = _uiState.value.passwordDialog ?: return
                _uiState.update { it.copy(passwordDialog = dialog.copy(password = intent.value,
                    aliases = null, selectedAlias = "", pending = true)) }
                aliasesValidation.validate(dialog.path, intent.value)
            }
            is AliasSelected -> _uiState.update { state ->
                val dialog = state.passwordDialog
                if (dialog != null && intent.value in dialog.aliases.orEmpty()) state.copy(
                    passwordDialog = dialog.copy(selectedAlias = intent.value)) else state
            }
            VerifyCertificate -> {
                val dialog = _uiState.value.passwordDialog ?: return
                if (dialog.pending || dialog.selectedAlias.isBlank()) {
                    notify(UiMessage.Resource(Res.string.wrong_key_store_password)); return
                }
                dismissDialog()
                verify(dialog.path, false) { verify.certificate(dialog.path, dialog.password, dialog.selectedAlias) }
            }
            DismissPasswordDialog -> dismissDialog()
            is CopyModeChanged -> {
                preferences.change(PreferenceChange.CopyModeChanged(CopyPreference.valueOf(intent.value.name)))
                _uiState.update { it.copy(copyMode = intent.value) }
            }
            is CopyFingerprint -> copy(formatClipboardValue(intent.value, _uiState.value.copyMode))
            is CopyText -> copy(intent.value)
        }
    }

    /** 关闭密码弹窗并使尚未完成的别名查询失效。 */
    private fun dismissDialog() {
        aliasesValidation.reset()
        _uiState.update { it.copy(passwordDialog = null) }
    }

    /** 启动最新一次校验，统一管理加载状态、结果映射和失败通知。 */
    private fun verify(path: String, isApk: Boolean, block: suspend () -> Result<SignatureVerification>) {
        val id = ++operationId
        _uiState.update { it.copy(phase = VerificationPhase.Loading, result = null, inputFile = path) }
        verificationRequest.launch(block = {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }) { outcome ->
            outcome.fold(onSuccess = { result ->
                _uiState.update { it.copy(phase = VerificationPhase.Result, result = result.toUi()) }
            }, onFailure = { error ->
                _uiState.update { it.copy(phase = VerificationPhase.Idle, result = null) }
                notify(error.message?.let(UiMessage::Text) ?: UiMessage.Resource(
                    if (isApk) Res.string.apk_signature_verification_failed else Res.string.signature_verification_failed), id)
            })
        }
    }

    /** 写入剪贴板成功且任务仍有效后再发送复制提示。 */
    private fun copy(value: String) {
        val id = ++operationId
        viewModelScope.launch {
            try {
                clipboard.write(value)
                currentCoroutineContext().ensureActive()
                effects.send("signature-information", SnackbarMessage(UiMessage.Resource(Res.string.copied_to_clipboard)), id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                io.github.oshai.kotlinlogging.KotlinLogging.logger("SignatureInformation").error(error) { "Clipboard write failed" }
            }
        }
    }

    /** 通过应用级消息通道发送提示，页面不直接持有 Snackbar。 */
    private fun notify(message: UiMessage, id: Long = ++operationId) {
        viewModelScope.launch { effects.send("signature-information", SnackbarMessage(message), id) }
    }
}
