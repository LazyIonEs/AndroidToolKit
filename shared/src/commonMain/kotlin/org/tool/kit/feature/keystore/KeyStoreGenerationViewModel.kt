package org.tool.kit.feature.keystore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.keystore.*
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.*

/** 管理密钥库生成表单、输出路径校验和生成状态，凭据仅随本次请求交给用例。 */
class KeyStoreGenerationViewModel(
    private val generate: GenerateKeyStoreUseCase,
    private val preferences: PreferencesRepository,
    private val storage: StorageRepository,
    private val effects: AppEffectSink,
) : ViewModel() {
    private val initialPreferences = preferences.state.value
    private val _uiState = MutableStateFlow(KeyStoreGenerationUiState(
        form = KeyStoreForm(keyStorePath = initialPreferences.userData.defaultOutputPath)))
    val uiState = _uiState.asStateFlow()
    val busy: StateFlow<Boolean> = uiState.map { it.busy }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val pathRequest = LatestRequest(viewModelScope)
    private var outputPathVersion = initialPreferences.takeIf { it.ready }?.outputPathVersion
    private var operationId = 0L
    private var generationJob: Job? = null

    init {
        validatePath()
        viewModelScope.launch {
            preferences.state.filter { it.ready }.collect { snapshot ->
                if (outputPathVersion != snapshot.outputPathVersion) {
                    outputPathVersion = snapshot.outputPathVersion
                    onIntent(KeyStoreGenerationIntent.OutputPathChanged(snapshot.userData.defaultOutputPath))
                }
            }
        }
    }

    /** All field events reduce synchronously on Main; no full-form write API is exposed. */
    fun onIntent(intent: KeyStoreGenerationIntent) {
        when (intent) {
            KeyStoreGenerationIntent.Submit -> { submit(); return }
            KeyStoreGenerationIntent.Refresh -> { validatePath(); return }
            else -> Unit
        }
        val old = _uiState.value.form
        val form = when (intent) {
            is KeyStoreGenerationIntent.OutputPathChanged -> old.copy(keyStorePath = intent.value)
            is KeyStoreGenerationIntent.FileNameChanged -> old.copy(keyStoreName = intent.value)
            is KeyStoreGenerationIntent.StorePasswordChanged -> old.copy(keyStorePassword = intent.value)
            is KeyStoreGenerationIntent.StoreConfirmationChanged -> old.copy(keyStoreConfirmPassword = intent.value)
            is KeyStoreGenerationIntent.AliasChanged -> old.copy(keyStoreAlisa = intent.value)
            is KeyStoreGenerationIntent.AliasPasswordChanged -> old.copy(keyStoreAlisaPassword = intent.value)
            is KeyStoreGenerationIntent.AliasConfirmationChanged -> old.copy(keyStoreAlisaConfirmPassword = intent.value)
            is KeyStoreGenerationIntent.ValidityChanged -> old.copy(validityPeriod = intent.value)
            is KeyStoreGenerationIntent.AuthorNameChanged -> old.copy(authorName = intent.value)
            is KeyStoreGenerationIntent.OrganizationalUnitChanged -> old.copy(organizationalUnit = intent.value)
            is KeyStoreGenerationIntent.OrganizationChanged -> old.copy(organizational = intent.value)
            is KeyStoreGenerationIntent.CityChanged -> old.copy(city = intent.value)
            is KeyStoreGenerationIntent.ProvinceChanged -> old.copy(province = intent.value)
            is KeyStoreGenerationIntent.CountryCodeChanged -> old.copy(countryCode = intent.value)
            KeyStoreGenerationIntent.Submit, KeyStoreGenerationIntent.Refresh -> error("Handled above")
        }
        val validation = _uiState.value.validation.copy(
            fileNameError = form.keyStoreName.isNotBlank() &&
                !(form.keyStoreName.endsWith(".jks") || form.keyStoreName.endsWith(".keystore")),
            storeConfirmationError = form.keyStoreConfirmPassword.isNotBlank() && form.keyStorePassword != form.keyStoreConfirmPassword,
            aliasConfirmationError = form.keyStoreAlisaConfirmPassword.isNotBlank() && form.keyStoreAlisaPassword != form.keyStoreAlisaConfirmPassword)
        _uiState.value = _uiState.value.copy(form = form, validation = validation)
        if (form.keyStorePath != old.keyStorePath) validatePath()
    }

    /** 查询当前输出目录并发布 pending 状态，较早路径的结果不能覆盖新路径。 */
    private fun validatePath() {
        pathRequest.cancel()
        val path = _uiState.value.form.keyStorePath
        _uiState.value = _uiState.value.copy(validation = _uiState.value.validation.copy(
            outputPathError = false, outputPathPending = path.isNotBlank()))
        if (path.isBlank()) return
        pathRequest.launch(block = { storage.inspectPath(path).isDirectory }) { valid ->
            _uiState.value = _uiState.value.copy(validation = _uiState.value.validation.copy(
                outputPathError = !valid, outputPathPending = false))
        }
    }

    /** 固定表单与密钥设置，在生成前再次确认输出目录可用，期间忽略重复提交。 */
    private fun submit() {
        if (_uiState.value.busy) return
        val state = _uiState.value
        if (state.validation.hasError) { notify(UiMessage.Resource(Res.string.check_error)); return }
        val form = state.form
        if (listOf(form.keyStorePath, form.keyStoreName, form.keyStorePassword, form.keyStoreConfirmPassword,
                form.keyStoreAlisa, form.keyStoreAlisaPassword, form.keyStoreAlisaConfirmPassword,
                form.validityPeriod, form.authorName, form.organizationalUnit, form.organizational,
                form.city, form.province, form.countryCode).any { it.isBlank() }) {
            notify(UiMessage.Resource(Res.string.check_empty)); return
        }
        val settings = preferences.state.value.userData
        val request = GenerateKeyStoreRequest(form.keyStorePath, form.keyStoreName,
            form.keyStorePassword, form.keyStoreAlisaPassword, form.keyStoreAlisa, form.validityPeriod,
            form.authorName, form.organizationalUnit, form.organizational, form.city, form.province,
            form.countryCode, KeyStoreFormat.valueOf(settings.destStoreType.name), settings.destStoreSize.size)
        val id = ++operationId
        _uiState.value = state.copy(busy = true)
        generationJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Recheck this captured request, including if display validation is still pending.
                if (!storage.inspectPath(request.outputDirectory).isDirectory) {
                    effects.send("keystore", SnackbarMessage(UiMessage.Resource(Res.string.check_error)), id)
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                val result = generate(request)
                currentCoroutineContext().ensureActive()
                val message = when (result) {
                    is GenerateKeyStoreOutcome.Success -> SnackbarMessage(
                        UiMessage.Resource(Res.string.create_signature_successfully),
                        actionLabel = getString(Res.string.jump), withDismissAction = true,
                        action = SnackbarAction.OpenDirectory(result.outputPath))
                    is GenerateKeyStoreOutcome.Failure -> SnackbarMessage(result.message?.let(UiMessage::Text)
                        ?: UiMessage.Resource(Res.string.signature_creation_failed))
                }
                effects.send("keystore", message, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effects.send("keystore", SnackbarMessage(e.message?.let(UiMessage::Text)
                    ?: UiMessage.Resource(Res.string.signature_creation_failed)), id)
            } finally {
                if (id == operationId) _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }

    private fun notify(message: UiMessage) {
        viewModelScope.launch { effects.send("keystore", SnackbarMessage(message)) }
    }
}
