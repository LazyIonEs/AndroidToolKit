package org.tool.kit.feature.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tool.kit.domain.apk.ApkRuleUpdateStatus
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.LibRulesRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.lib_rules_up_to_date
import org.tool.kit.shared.generated.resources.lib_rules_update_failed
import org.tool.kit.shared.generated.resources.lib_rules_update_success
import org.tool.kit.shared.generated.resources.toolkit_extension_mode_is_enabled

/** 将设置事件映射为单字段修改，立即回显仓库快照并异步校验输出路径。 */
class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val storage: StorageRepository,
    private val effects: AppEffectSink,
    private val actions: DesktopActionHandler,
    private val libRules: LibRulesRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(preferences.state.value))
    val uiState = _uiState.asStateFlow()
    private val pathRequest = LatestRequest(viewModelScope)
    private var validatedPath: String? = null
    private var ruleOperationId = 0L
    init {
        viewModelScope.launch {
            preferences.state.collect { snapshot ->
                // 仓库流可能稍晚于同步事件返回；只接收不落后的快照，防止输入回跳。
                if (snapshot.revision >= _uiState.value.preferences.revision) {
                    _uiState.value = _uiState.value.copy(preferences = snapshot)
                    if (snapshot.ready) validatePath()
                }
            }
        }
    }
    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: SettingsIntent) {
        if (intent == SettingsIntent.Refresh) {
            validatePath(force = true)
            viewModelScope.launch {
                val logPath = actions.logFilePath()
                val metadata = if (libRules == null) null else try {
                    libRules.metadata()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                _uiState.update {
                    it.copy(
                        logFilePath = logPath,
                        rulesMetadata = metadata ?: it.rulesMetadata,
                    )
                }
            }
            return
        }
        if (intent == SettingsIntent.UpdateLibRules) {
            val repo = libRules ?: return
            if (_uiState.value.updatingRules) return
            val opId = ++ruleOperationId
            _uiState.update { it.copy(updatingRules = true) }
            viewModelScope.launch {
                val result = try {
                    repo.checkAndUpdate()
                } catch (cancelled: CancellationException) {
                    _uiState.update { it.copy(updatingRules = false) }
                    throw cancelled
                }
                _uiState.update {
                    it.copy(
                        updatingRules = false,
                        rulesMetadata = result.metadata ?: it.rulesMetadata,
                    )
                }
                val message = when (result.status) {
                    ApkRuleUpdateStatus.Updated -> Res.string.lib_rules_update_success
                    ApkRuleUpdateStatus.AlreadyUpToDate -> Res.string.lib_rules_up_to_date
                    ApkRuleUpdateStatus.Failed -> Res.string.lib_rules_update_failed
                }
                effects.send("settings", SnackbarMessage(UiMessage.Resource(message)), opId)
            }
            return
        }
        val wasDeveloper = _uiState.value.preferences.isEnableDeveloperMode
        val change = when (intent) {
            is SettingsIntent.Theme -> PreferenceChange.Theme(ThemePreference.valueOf(intent.value.name))
            is SettingsIntent.OutputPath -> PreferenceChange.OutputPath(intent.value)
            is SettingsIntent.SignerSuffix -> PreferenceChange.SignerSuffix(intent.value)
            is SettingsIntent.DuplicateRemoval -> PreferenceChange.DuplicateRemoval(intent.value)
            is SettingsIntent.AlignFileSize -> PreferenceChange.AlignFileSize(intent.value)
            is SettingsIntent.SaveSigningProfiles -> PreferenceChange.SaveSigningProfiles(intent.value)
            is SettingsIntent.StoreType -> PreferenceChange.StoreType(intent.value)
            is SettingsIntent.StoreSize -> PreferenceChange.StoreSize(intent.value)
            is SettingsIntent.ShowJunkCode -> PreferenceChange.ShowJunkCode(intent.value)
            is SettingsIntent.AlwaysShowLabel -> PreferenceChange.AlwaysShowLabel(intent.value)
            is SettingsIntent.HuaweiAlignment -> PreferenceChange.HuaweiAlignment(intent.value)
            is SettingsIntent.DeveloperMode -> PreferenceChange.DeveloperMode(intent.value)
            is SettingsIntent.StartCheckUpdate -> PreferenceChange.StartCheckUpdate(intent.value)
            SettingsIntent.Refresh, SettingsIntent.UpdateLibRules -> error("Handled above")
        }
        val snapshot = preferences.change(change)
        _uiState.value = _uiState.value.copy(preferences = snapshot)
        validatePath()
        if (change is PreferenceChange.DeveloperMode && change.value && !wasDeveloper) viewModelScope.launch {
            if (!effects.send("settings", SnackbarMessage(UiMessage.Resource(Res.string.toolkit_extension_mode_is_enabled)), snapshot.revision)) return@launch
        }
    }
    /** 按路径去重异步校验；页面恢复时可强制复查同一路径。 */
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
