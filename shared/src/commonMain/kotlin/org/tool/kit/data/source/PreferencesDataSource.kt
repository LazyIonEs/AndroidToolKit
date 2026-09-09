package org.tool.kit.data.source

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toBlockingSettings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import org.tool.kit.model.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.utils.getDownloadDirectory

interface PreferencesStorage {
    suspend fun read(): PreferencesSnapshot
    suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot)
}

/** Physical keys and serializers are unchanged. No in-memory preference state is owned here. */
@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class PreferencesDataSource(private val settings: FlowSettings, private val io: CoroutineDispatcher) : PreferencesStorage {
    private val blockingSettings = settings.toBlockingSettings()
    companion object {
        private const val THEME_CONFIG = "theme_config"
        val DEFAULT_THEME_CONFIG = ThemePreference.FOLLOW_SYSTEM
        private const val USER_DATA = "user_data"
        val DEFAULT_USER_DATA get() = UserData(
            defaultOutputPath = getDownloadDirectory(),
            duplicateFileRemoval = true,
            defaultSignerSuffix = "-sign",
            alignFileSize = true,
            destStoreType = DestStoreType.JKS,
            destStoreSize = DestStoreSize.TWO_THOUSAND_FORTY_EIGHT
        )
        private const val ICON_FACTORY_DATA = "icon_factory_data"
        val DEFAULT_ICON_FACTORY_DATA = IconFactoryData(
            pngTypIdx = PngAlgorithm.Lanczos3,
            jpegTypIdx = JpegAlgorithm.Lanczos3,
            lossless = true,
            minimum = 70,
            target = 100,
            speed = 1,
            preset = 6,
            percentage = 1f,
            quality = 85f
        )
        val DEFAULT_COPY_MODE = CopyPreference.UPPERCASE_WITH_COLON
        private const val JUNK_CODE = "junk_code"
        private const val DEVELOPER_MODE = "developer_mode"
        private const val ALWAYS_SHOW_LABEL = "always_show_label"
        private const val HUAWEI_ALIGN_FILE_SIZE = "huawei_align_file_size"
        private const val START_CHECK_UPDATE = "start_check_update"
        private const val SIGNATURE_COPY_MODE = "signature_copy_mode"
        private const val JUNK_MODE = "junk_mode"
        val DEFAULT_JUNK_MODE = JunkPreference.SINGLE
    }

    override suspend fun read(): PreferencesSnapshot = withContext(io) {
        val theme = settings.getStringOrNull(THEME_CONFIG)
        PreferencesSnapshot(
            ready = true,
            userData = blockingSettings.decodeValue(UserData.serializer(), USER_DATA, DEFAULT_USER_DATA),
            iconFactoryData = blockingSettings.decodeValue(IconFactoryData.serializer(), ICON_FACTORY_DATA, DEFAULT_ICON_FACTORY_DATA),
            themeConfig = when (theme) {
                null, ThemePreference.FOLLOW_SYSTEM.name -> ThemePreference.FOLLOW_SYSTEM
                ThemePreference.DARK.name -> ThemePreference.DARK
                else -> ThemePreference.LIGHT
            },
            isShowJunkCode = settings.getBoolean(JUNK_CODE, false),
            isAlwaysShowLabel = settings.getBoolean(ALWAYS_SHOW_LABEL, false),
            isHuaweiAlignFileSize = settings.getBoolean(HUAWEI_ALIGN_FILE_SIZE, true),
            isEnableDeveloperMode = settings.getBoolean(DEVELOPER_MODE, false),
            isStartCheckUpdate = settings.getBoolean(START_CHECK_UPDATE, true),
            copyMode = CopyPreference.entries.firstOrNull { it.name == settings.getStringOrNull(SIGNATURE_COPY_MODE) } ?: DEFAULT_COPY_MODE,
            junkMode = JunkPreference.entries.firstOrNull { it.name == settings.getStringOrNull(JUNK_MODE) } ?: DEFAULT_JUNK_MODE,
        )
    }

    override suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot) = withContext(io) {
        when (change) {
            is PreferenceChange.Theme -> settings.putString(THEME_CONFIG, change.value.name)
            is PreferenceChange.OutputPath -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.SignerSuffix -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.DuplicateRemoval -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.AlignFileSize -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.StoreType -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.StoreSize -> blockingSettings.encodeValue(UserData.serializer(), USER_DATA, snapshot.userData)
            is PreferenceChange.ShowJunkCode -> settings.putBoolean(JUNK_CODE, change.value)
            is PreferenceChange.AlwaysShowLabel -> settings.putBoolean(ALWAYS_SHOW_LABEL, change.value)
            is PreferenceChange.HuaweiAlignment -> settings.putBoolean(HUAWEI_ALIGN_FILE_SIZE, change.value)
            is PreferenceChange.DeveloperMode -> settings.putBoolean(DEVELOPER_MODE, change.value)
            is PreferenceChange.StartCheckUpdate -> settings.putBoolean(START_CHECK_UPDATE, change.value)
            is PreferenceChange.CopyModeChanged -> settings.putString(SIGNATURE_COPY_MODE, change.value.name)
            is PreferenceChange.JunkModeChanged -> settings.putString(JUNK_MODE, change.value.name)
            is PreferenceChange.IconSettings -> blockingSettings.encodeValue(IconFactoryData.serializer(), ICON_FACTORY_DATA, snapshot.iconFactoryData)
        }
    }
}
