package org.tool.kit.domain.preferences

import kotlinx.coroutines.flow.StateFlow
import org.tool.kit.model.UserData
import org.tool.kit.model.IconFactoryData
import org.tool.kit.model.DestStoreType
import org.tool.kit.model.DestStoreSize
import org.tool.kit.model.PngAlgorithm
import org.tool.kit.model.JpegAlgorithm

enum class ThemePreference { FOLLOW_SYSTEM, LIGHT, DARK }
enum class CopyPreference { UPPERCASE_WITH_COLON, LOWERCASE_WITH_COLON, UPPERCASE_WITHOUT_COLON, LOWERCASE_WITHOUT_COLON }
enum class JunkPreference { SINGLE, MULTI }

// Legacy serializer DTOs remain the storage compatibility types during the feature migration.
data class PreferencesSnapshot(
    val ready: Boolean = false,
    val revision: Long = 0,
    val persistedRevision: Long = 0,
    val outputPathVersion: Long = 0,
    val writeFailure: String? = null,
    val userData: UserData = UserData("", true, "-sign", true, DestStoreType.JKS, DestStoreSize.TWO_THOUSAND_FORTY_EIGHT),
    val iconFactoryData: IconFactoryData = IconFactoryData(PngAlgorithm.Lanczos3, JpegAlgorithm.Lanczos3, true, 70, 100, 1, 6, 1f, 85f),
    val themeConfig: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    val isShowJunkCode: Boolean = false,
    val isAlwaysShowLabel: Boolean = false,
    val isHuaweiAlignFileSize: Boolean = true,
    val isEnableDeveloperMode: Boolean = false,
    val isStartCheckUpdate: Boolean = true,
    val copyMode: CopyPreference = CopyPreference.UPPERCASE_WITH_COLON,
    val junkMode: JunkPreference = JunkPreference.SINGLE,
)

sealed interface PreferenceChange {
    data class Theme(val value: ThemePreference) : PreferenceChange
    data class OutputPath(val value: String) : PreferenceChange
    data class SignerSuffix(val value: String) : PreferenceChange
    data class DuplicateRemoval(val value: Boolean) : PreferenceChange
    data class AlignFileSize(val value: Boolean) : PreferenceChange
    data class StoreType(val value: DestStoreType) : PreferenceChange
    data class StoreSize(val value: DestStoreSize) : PreferenceChange
    data class ShowJunkCode(val value: Boolean) : PreferenceChange
    data class AlwaysShowLabel(val value: Boolean) : PreferenceChange
    data class HuaweiAlignment(val value: Boolean) : PreferenceChange
    data class DeveloperMode(val value: Boolean) : PreferenceChange
    data class StartCheckUpdate(val value: Boolean) : PreferenceChange
    data class CopyModeChanged(val value: CopyPreference) : PreferenceChange
    data class JunkModeChanged(val value: JunkPreference) : PreferenceChange
    data class IconSettings(val value: IconFactoryData) : PreferenceChange
}

fun PreferencesSnapshot.changed(change: PreferenceChange): PreferencesSnapshot = when (change) {
    is PreferenceChange.Theme -> copy(themeConfig = change.value)
    is PreferenceChange.OutputPath -> copy(userData = userData.copy(defaultOutputPath = change.value))
    is PreferenceChange.SignerSuffix -> copy(userData = userData.copy(defaultSignerSuffix = change.value))
    is PreferenceChange.DuplicateRemoval -> copy(userData = userData.copy(duplicateFileRemoval = change.value))
    is PreferenceChange.AlignFileSize -> copy(userData = userData.copy(alignFileSize = change.value))
    is PreferenceChange.StoreType -> copy(userData = userData.copy(destStoreType = change.value))
    is PreferenceChange.StoreSize -> copy(userData = userData.copy(destStoreSize = change.value))
    is PreferenceChange.ShowJunkCode -> copy(isShowJunkCode = change.value)
    is PreferenceChange.AlwaysShowLabel -> copy(isAlwaysShowLabel = change.value)
    is PreferenceChange.HuaweiAlignment -> copy(isHuaweiAlignFileSize = change.value)
    is PreferenceChange.DeveloperMode -> copy(isEnableDeveloperMode = change.value)
    is PreferenceChange.StartCheckUpdate -> copy(isStartCheckUpdate = change.value)
    is PreferenceChange.CopyModeChanged -> copy(copyMode = change.value)
    is PreferenceChange.JunkModeChanged -> copy(junkMode = change.value)
    is PreferenceChange.IconSettings -> copy(iconFactoryData = change.value)
}

interface PreferencesRepository {
    val state: StateFlow<PreferencesSnapshot>
    /** Main-thread event entry: publishes accepted values immediately; disk writes are serialized. */
    fun change(change: PreferenceChange): PreferencesSnapshot
    suspend fun awaitReady(): PreferencesSnapshot
}
