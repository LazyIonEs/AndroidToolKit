package org.tool.kit.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.model.DarkThemeConfig

data class AppUiState(val themeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    val isAlwaysShowLabel: Boolean = false, val isShowJunkCode: Boolean = false,
    val isStartCheckUpdate: Boolean = false, val ready: Boolean = false)

/** Shell-only projection; startup reads the fully loaded preference once per root session. */
class AppViewModel(private val preferences: PreferencesRepository) : ViewModel() {
    suspend fun startupUpdateEnabled(): Boolean = preferences.awaitReady().isStartCheckUpdate
    val uiState = preferences.state.map { AppUiState(DarkThemeConfig.valueOf(it.themeConfig.name), it.isAlwaysShowLabel, it.isShowJunkCode, it.ready && it.isStartCheckUpdate, it.ready) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.state.value.let { AppUiState(DarkThemeConfig.valueOf(it.themeConfig.name), it.isAlwaysShowLabel, it.isShowJunkCode, false, it.ready) })
}
