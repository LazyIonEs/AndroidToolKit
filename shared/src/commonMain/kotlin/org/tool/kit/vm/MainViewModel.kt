package org.tool.kit.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import org.tool.kit.model.DarkThemeConfig

/** Unused UI compatibility projections, retained until Phase 9 removes the legacy binding. */
class MainViewModel(private val preferences: org.tool.kit.domain.preferences.PreferencesRepository) : ViewModel() {
    // Read-only legacy projections; remove each when its last feature migrates.
    val themeConfig = preferences.state.map { DarkThemeConfig.valueOf(it.themeConfig.name) }.stateIn(viewModelScope, Eagerly, DarkThemeConfig.valueOf(preferences.state.value.themeConfig.name))
    val userData = preferences.state.map { it.userData }.stateIn(viewModelScope, Eagerly, preferences.state.value.userData)
    val isHuaweiAlignFileSize = preferences.state.map { it.isHuaweiAlignFileSize }.stateIn(viewModelScope, Eagerly, preferences.state.value.isHuaweiAlignFileSize)

}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
}
