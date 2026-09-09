package org.tool.kit.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.SignApkUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.isApk
import org.tool.kit.utils.isKey

class ApkSigningViewModel(
    private val sign: SignApkUseCase,
    private val preferences: PreferencesRepository,
    private val storage: StorageRepository,
    keyStores: KeyStoreRepository,
    private val effects: AppEffectSink,
    val presets: SigningPresets,
) : ViewModel() {
    private val initial = preferences.state.value
    private val _uiState = MutableStateFlow(ApkSigningUiState(form = ApkSignatureForm(outputPath = initial.userData.defaultOutputPath)))
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val apkRequest = LatestRequest(viewModelScope)
    private val outputRequest = LatestRequest(viewModelScope)
    private val keyRequest = LatestRequest(viewModelScope)
    private val nameRequest = LatestRequest(viewModelScope)
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
                    onIntent(ApkSigningIntent.OutputPathChanged(snapshot.userData.defaultOutputPath))
                }
            }
        }
        addCloseable {
            operationId++
            apkRequest.cancel(); outputRequest.cancel(); keyRequest.cancel(); nameRequest.cancel()
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun onIntent(intent: ApkSigningIntent) {
        when (intent) {
            ApkSigningIntent.Submit -> { submit(); return }
            ApkSigningIntent.Refresh -> { refreshPaths(); credentials.refreshAliasPassword(_uiState.value.form.credentials); return }
            is ApkSigningIntent.FilesDropped -> {
                intent.paths.firstOrNull { it.isApk }?.let { onIntent(ApkSigningIntent.ApkPathChanged(it)) }
                intent.paths.firstOrNull { it.isKey }?.let { onIntent(ApkSigningIntent.KeyPathChanged(it)) }
                return
            }
            else -> Unit
        }
        val old = _uiState.value.form
        val form = SigningFormReducer.field(old, intent)
        setForm(form)
        if (form.apkPath != old.apkPath) validateApk()
        if (form.outputPath != old.outputPath) validateOutput()
        if (form.credentials.path != old.credentials.path) validateKey()
        if (form.apkPath != old.apkPath || form.outputPrefix != old.outputPrefix) deriveName()
        if (intent is ApkSigningIntent.V4NameChanged) { nameRequest.cancel(); validation { it.copy(namePending = false) } }
        if (intent is ApkSigningIntent.StorePasswordChanged) credentials.passwordChanged(form.credentials)
        if (intent is ApkSigningIntent.PolicyChanged && intent.value == org.tool.kit.model.SignaturePolicy.V2Only)
            notify(UiMessage.Resource(Res.string.v2_tips))
    }

    private fun setForm(form: ApkSignatureForm) {
        _uiState.update { it.copy(form = form) }
        credentials.formChanged(form.credentials)
    }
    private fun validation(update: (ApkSigningValidation) -> ApkSigningValidation) {
        _uiState.update { it.copy(validation = update(it.validation)) }
    }
    private fun refreshPaths() { validateApk(); validateOutput(); validateKey() }
    private fun validateApk() {
        apkRequest.cancel()
        val path = _uiState.value.form.apkPath
        val pending = path.isNotBlank() && path != presets.allPath
        validation { it.copy(apkError = false, apkPending = pending) }
        if (pending) apkRequest.launch(block = { storage.inspectPath(path).isFile }) { valid ->
            validation { it.copy(apkError = !valid, apkPending = false) }
        }
    }
    private fun validateOutput() {
        outputRequest.cancel()
        val path = _uiState.value.form.outputPath
        validation { it.copy(outputError = false, outputPending = path.isNotBlank()) }
        if (path.isNotBlank()) outputRequest.launch(block = { storage.inspectPath(path).isDirectory }) { valid ->
            validation { it.copy(outputError = !valid, outputPending = false) }
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
    private fun deriveName() {
        nameRequest.cancel()
        val form = _uiState.value.form
        val pending = form.apkPath.isNotBlank() && form.apkPath != presets.allPath
        validation { it.copy(namePending = pending) }
        if (pending) nameRequest.launch(block = {
            storage.inspectPath(form.apkPath).let { if (it.isFile || it.isDirectory) it.fileName else null }
        }) { name ->
            // LatestRequest rejects an earlier path/prefix revision; preserve unrelated field edits.
            _uiState.update { it.copy(form = SigningFormReducer.resolvedName(it.form, name)) }
            validation { it.copy(namePending = false) }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.busy) return
        val form = state.form
        val keys = form.credentials
        if (state.hasError || state.validation.pending || credentials.state.value.pending ||
            listOf(form.apkPath, form.outputPath, keys.path, keys.storePassword, keys.aliasPassword).any { it.isBlank() } || keys.aliases.isNullOrEmpty()) {
            notify(UiMessage.Resource(Res.string.check_error)); return
        }
        val settings = preferences.state.value
        val request = SignApkRequest(form.apkPath, form.outputPath, form.outputPrefix,
            settings.userData.defaultSignerSuffix, settings.userData.duplicateFileRemoval,
            settings.userData.alignFileSize, settings.isHuaweiAlignFileSize, presets.huaweiPath,
            ApkSigningPolicy.valueOf(form.policy.name), form.v4FileName,
            SigningCredentials(keys.path, keys.storePassword, keys.aliases.getOrNull(keys.aliasIndex), keys.aliasPassword))
        val batch = form.apkPath == presets.allPath
        val requests = if (batch) presets.batchPaths.map { request.copy(inputPath = it) } else listOf(request)
        val id = ++operationId
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val outcomes = if (batch) sign.batch(requests) else listOf(sign(request))
                currentCoroutineContext().ensureActive()
                if (id != operationId) return@launch
                val outcome = if (batch && outcomes.any { it !is SignApkOutcome.Success || !it.outputExists })
                    SignApkOutcome.Failure(null) else outcomes.last()
                val message = when (outcome) {
                    is SignApkOutcome.Success -> SnackbarMessage(UiMessage.Resource(Res.string.apk_is_signed_successfully),
                        actionLabel = getString(Res.string.jump), withDismissAction = true,
                        action = SnackbarAction.OpenDirectory(outcome.outputPath))
                    is SignApkOutcome.OutputAlreadyExists -> SnackbarMessage(UiMessage.Text(getString(Res.string.output_file_already_exists, outcome.fileName)))
                    is SignApkOutcome.Failure -> SnackbarMessage(outcome.message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.signature_failed))
                }
                effects.send("apk-signing", message, id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (id == operationId) effects.send("apk-signing", SnackbarMessage(error.message?.let(UiMessage::Text)
                    ?: UiMessage.Resource(Res.string.signature_failed)), id)
            } finally {
                if (id == operationId) _uiState.update { it.copy(busy = false) }
            }
        }
    }
    private fun notify(message: UiMessage) { viewModelScope.launch { effects.send("apk-signing", SnackbarMessage(message)) } }
}
