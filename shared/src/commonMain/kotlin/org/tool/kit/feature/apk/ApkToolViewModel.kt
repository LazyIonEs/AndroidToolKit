package org.tool.kit.feature.apk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.BuildApkUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.signature.SigningCredentialsValidation
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.isImage
import org.tool.kit.utils.isKey

class ApkToolViewModel(
    private val build: BuildApkUseCase,
    private val preferences: PreferencesRepository,
    private val storage: StorageRepository,
    keyStores: KeyStoreRepository,
    private val effects: AppEffectSink,
    private val huaweiPresetPath: String,
) : ViewModel() {
    private val initial = preferences.state.value
    private val _uiState = MutableStateFlow(ApkToolUiState(form = ApkToolForm(outputPath = initial.userData.defaultOutputPath)))
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val outputRequest = LatestRequest(viewModelScope)
    private val iconRequest = LatestRequest(viewModelScope)
    private val keyRequest = LatestRequest(viewModelScope)
    private var outputVersion = initial.takeIf { it.ready }?.outputPathVersion
    private var operationId = 0L
    private val credentials = SigningCredentialsValidation(viewModelScope, storage, keyStores) { aliases ->
        val form = _uiState.value.form
        setForm(form.copy(credentials = form.credentials.copy(aliases = aliases?.toList())))
    }

    init {
        viewModelScope.launch { credentials.state.collect { result -> validation { it.copy(credentials = result) } } }
        refreshPaths()
        viewModelScope.launch {
            preferences.state.filter { it.ready }.collect { snapshot ->
                if (snapshot.outputPathVersion != outputVersion) {
                    outputVersion = snapshot.outputPathVersion
                    onIntent(ApkToolIntent.OutputPathChanged(snapshot.userData.defaultOutputPath))
                }
            }
        }
        addCloseable {
            operationId++
            outputRequest.cancel(); iconRequest.cancel(); keyRequest.cancel()
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun onIntent(intent: ApkToolIntent) {
        when (intent) {
            ApkToolIntent.Submit -> { submit(); return }
            ApkToolIntent.Refresh -> { refreshPaths(); credentials.refreshAliasPassword(_uiState.value.form.credentials); return }
            is ApkToolIntent.FilesDropped -> {
                // The platform has filtered the entire list for existence; this page uses only its first item.
                intent.paths.firstOrNull()?.let { path ->
                    if (path.isImage) onIntent(ApkToolIntent.IconPathChanged(path))
                    else if (path.isKey) onIntent(ApkToolIntent.KeyPathChanged(path))
                }
                return
            }
            else -> Unit
        }
        val old = _uiState.value.form
        val form = ApkToolFormReducer.field(old, intent)
        setForm(form)
        if (form.outputPath != old.outputPath) validateOutput()
        if (form.icon != old.icon) validateIcon()
        if (form.credentials.path != old.credentials.path) validateKey()
        if (intent is ApkToolIntent.StorePasswordChanged) credentials.passwordChanged(form.credentials)
    }

    private fun setForm(form: ApkToolForm) {
        _uiState.update { it.copy(form = form) }
        credentials.formChanged(form.credentials)
    }
    private fun validation(update: (ApkToolValidation) -> ApkToolValidation) {
        _uiState.update { it.copy(validation = update(it.validation)) }
    }
    private fun refreshPaths() { validateOutput(); validateIcon(); validateKey() }
    private fun validateOutput() {
        outputRequest.cancel()
        val path = _uiState.value.form.outputPath
        validation { it.copy(outputError = false, outputPending = path.isNotBlank()) }
        if (path.isNotBlank()) outputRequest.launch(block = { storage.inspectPath(path).isDirectory }) { valid ->
            validation { it.copy(outputError = !valid, outputPending = false) }
        }
    }
    private fun validateIcon() {
        iconRequest.cancel()
        val path = _uiState.value.form.icon
        validation { it.copy(iconError = false, iconPending = path.isNotBlank()) }
        if (path.isNotBlank()) iconRequest.launch(block = { storage.inspectPath(path).isFile }) { valid ->
            validation { it.copy(iconError = !valid, iconPending = false) }
        }
    }
    private fun validateKey() {
        keyRequest.cancel()
        val path = _uiState.value.form.credentials.path
        validation { it.copy(keyError = false, keyPending = path.isNotBlank()) }
        if (path.isNotBlank()) keyRequest.launch(block = { storage.inspectPath(path).isFile }) { valid ->
            validation { it.copy(keyError = !valid, keyPending = false) }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.busy) return
        val form = state.form
        val validation = state.validation
        // Preserve Generate's original validation order and scope. Completed key/password errors
        // are displayed inline but optional-signing failure still belongs to the build outcome.
        if (validation.outputError || validation.iconError || validation.outputPending || validation.iconPending ||
            (form.enableSign && (validation.keyPending || credentials.state.value.pending))) {
            notify(UiMessage.Resource(Res.string.check_error)); return
        }
        if (listOf(form.outputPath, form.packageName, form.targetSdkVersion, form.minSdkVersion,
                form.versionName, form.appName).any { it.isBlank() } || form.versionCode.isEmpty()) {
            notify(UiMessage.Resource(Res.string.check_empty)); return
        }
        val settings = preferences.state.value
        val keys = form.credentials
        val signing = if (form.enableSign) SignApkRequest("", form.outputPath, "",
            settings.userData.defaultSignerSuffix, settings.userData.duplicateFileRemoval,
            settings.userData.alignFileSize, settings.isHuaweiAlignFileSize, huaweiPresetPath,
            ApkSigningPolicy.valueOf(form.policy.name), "apk-name.apk.idsig",
            SigningCredentials(keys.path, keys.storePassword, keys.aliases?.getOrNull(keys.aliasIndex), keys.aliasPassword)) else null
        val request = BuildApkRequest(form.outputPath, form.icon, form.packageName, form.targetSdkVersion,
            form.minSdkVersion, form.versionCode, form.versionName, form.appName, signing)
        val id = ++operationId
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = build(request)
                currentCoroutineContext().ensureActive()
                if (id != operationId) return@launch
                val message = when (result) {
                    is BuildApkOutcome.Success -> SnackbarMessage(UiMessage.Text(getString(Res.string.build_end, result.sizeBytes.formatFileSize())),
                        actionLabel = getString(Res.string.jump), withDismissAction = true,
                        action = SnackbarAction.OpenDirectory(result.outputPath))
                    is BuildApkOutcome.Failure -> SnackbarMessage(result.message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.build_failure))
                }
                effects.send("apk-tool", message, id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (id == operationId) effects.send("apk-tool", SnackbarMessage(error.message?.let(UiMessage::Text)
                    ?: UiMessage.Resource(Res.string.build_failure)), id)
            } finally {
                if (id == operationId) _uiState.update { it.copy(busy = false) }
            }
        }
    }
    private fun notify(message: UiMessage) { viewModelScope.launch { effects.send("apk-tool", SnackbarMessage(message)) } }
}
