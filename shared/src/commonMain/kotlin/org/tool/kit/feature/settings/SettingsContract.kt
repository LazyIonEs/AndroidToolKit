package org.tool.kit.feature.settings

import org.tool.kit.domain.preferences.PreferencesSnapshot
import org.tool.kit.model.*

data class SettingsUiState(val preferences: PreferencesSnapshot = PreferencesSnapshot(), val outputPathError: Boolean = false, val logFilePath: String? = null)
sealed interface SettingsIntent {
    data class Theme(val value: DarkThemeConfig) : SettingsIntent
    data class OutputPath(val value: String) : SettingsIntent
    data class SignerSuffix(val value: String) : SettingsIntent
    data class DuplicateRemoval(val value: Boolean) : SettingsIntent
    data class AlignFileSize(val value: Boolean) : SettingsIntent
    data class StoreType(val value: DestStoreType) : SettingsIntent
    data class StoreSize(val value: DestStoreSize) : SettingsIntent
    data class ShowJunkCode(val value: Boolean) : SettingsIntent
    data class AlwaysShowLabel(val value: Boolean) : SettingsIntent
    data class HuaweiAlignment(val value: Boolean) : SettingsIntent
    data class DeveloperMode(val value: Boolean) : SettingsIntent
    data class StartCheckUpdate(val value: Boolean) : SettingsIntent
    data object Refresh : SettingsIntent
}
