package org.tool.kit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.toolkit_extension_mode_is_enabled

class SettingsViewModel(private val preferences: PreferencesRepository, private val storage: StorageRepository,
    private val effects: AppEffectSink, private val actions: DesktopActionHandler) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(preferences.state.value))
    val uiState = _uiState.asStateFlow()
    private val pathRequest = LatestRequest(viewModelScope)
    private var validatedPath: String? = null
    init {
        viewModelScope.launch {
            preferences.state.collect { snapshot ->
                if (snapshot.revision >= _uiState.value.preferences.revision) {
                    _uiState.value = _uiState.value.copy(preferences = snapshot)
                    if (snapshot.ready) validatePath()
                }
            }
        }
    }
    fun onIntent(intent: SettingsIntent) {
        if (intent == SettingsIntent.Refresh) {
            validatePath(force = true)
            viewModelScope.launch { _uiState.value = _uiState.value.copy(logFilePath = actions.logFilePath()) }
            return
        }
        val wasDeveloper = _uiState.value.preferences.isEnableDeveloperMode
        val change = when (intent) {
            is SettingsIntent.Theme -> PreferenceChange.Theme(ThemePreference.valueOf(intent.value.name))
            is SettingsIntent.OutputPath -> PreferenceChange.OutputPath(intent.value)
            is SettingsIntent.SignerSuffix -> PreferenceChange.SignerSuffix(intent.value)
            is SettingsIntent.DuplicateRemoval -> PreferenceChange.DuplicateRemoval(intent.value)
            is SettingsIntent.AlignFileSize -> PreferenceChange.AlignFileSize(intent.value)
            is SettingsIntent.StoreType -> PreferenceChange.StoreType(intent.value)
            is SettingsIntent.StoreSize -> PreferenceChange.StoreSize(intent.value)
            is SettingsIntent.ShowJunkCode -> PreferenceChange.ShowJunkCode(intent.value)
            is SettingsIntent.AlwaysShowLabel -> PreferenceChange.AlwaysShowLabel(intent.value)
            is SettingsIntent.HuaweiAlignment -> PreferenceChange.HuaweiAlignment(intent.value)
            is SettingsIntent.DeveloperMode -> PreferenceChange.DeveloperMode(intent.value)
            is SettingsIntent.StartCheckUpdate -> PreferenceChange.StartCheckUpdate(intent.value)
            SettingsIntent.Refresh -> error("Handled above")
        }
        val snapshot = preferences.change(change)
        _uiState.value = _uiState.value.copy(preferences = snapshot)
        validatePath()
        if (change is PreferenceChange.DeveloperMode && change.value && !wasDeveloper) viewModelScope.launch {
            if (!effects.send("settings", SnackbarMessage(UiMessage.Resource(Res.string.toolkit_extension_mode_is_enabled)), snapshot.revision)) return@launch
        }
    }
    private fun validatePath(force: Boolean = false) {
        val path = _uiState.value.preferences.userData.defaultOutputPath
        if (!force && path == validatedPath) return
        validatedPath = path
        pathRequest.cancel()
        _uiState.value = _uiState.value.copy(outputPathError = false)
        if (path.isBlank()) return
        pathRequest.launch(block = { storage.inspectPath(path).isDirectory }) {
            _uiState.value = _uiState.value.copy(outputPathError = !it)
        }
    }
}
