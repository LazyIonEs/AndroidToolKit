package database

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toBlockingSettings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import model.DarkThemeConfig
import model.DestStoreSize
import model.DestStoreType
import model.IconFactoryData
import model.JpegAlgorithm
import model.PngAlgorithm
import model.UserData
import utils.getDownloadDirectory

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 19:36
 * @Description : 用户偏好设置
 * @Version     : 1.0
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class PreferencesDataSource @OptIn(ExperimentalSettingsApi::class) constructor(private val settings: FlowSettings) {

    private val blockingSettings = settings.toBlockingSettings()

    companion object {
        private const val THEME_CONFIG = "theme_config"
        val DEFAULT_THEME_CONFIG = DarkThemeConfig.FOLLOW_SYSTEM
        private const val USER_DATA = "user_data"
        val DEFAULT_USER_DATA = UserData(
            defaultOutputPath = getDownloadDirectory(),
            duplicateFileRemoval = true,
            defaultSignerSuffix = "_sign",
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
        private const val JUNK_CODE = "junk_code"
    }

    private val _userData = MutableStateFlow(DEFAULT_USER_DATA)
    val userData = _userData.asStateFlow()

    private val _iconFactoryData = MutableStateFlow(DEFAULT_ICON_FACTORY_DATA)
    val iconFactoryData = _iconFactoryData.asStateFlow()

    val themeConfig = settings.getStringOrNullFlow(THEME_CONFIG).map { string ->
        string?.let {
            when (it) {
                DarkThemeConfig.FOLLOW_SYSTEM.name -> DarkThemeConfig.FOLLOW_SYSTEM
                DarkThemeConfig.DARK.name -> DarkThemeConfig.DARK
                else -> DarkThemeConfig.LIGHT
            }
        } ?: let {
            DEFAULT_THEME_CONFIG
        }
    }

    init {
        _userData.value = blockingSettings.decodeValue(UserData.serializer(), USER_DATA, DEFAULT_USER_DATA)
        _iconFactoryData.value =
            blockingSettings.decodeValue(IconFactoryData.serializer(), ICON_FACTORY_DATA, DEFAULT_ICON_FACTORY_DATA)
    }

    suspend fun saveThemeConfig(themeConfig: DarkThemeConfig) {
        settings.putString(THEME_CONFIG, themeConfig.name)
    }

    fun saveUserData(userData: UserData) {
        blockingSettings.encodeValue(UserData.serializer(), USER_DATA, userData)
        _userData.value = userData
    }

    fun saveIconFactoryData(iconFactoryData: IconFactoryData) {
        blockingSettings.encodeValue(IconFactoryData.serializer(), ICON_FACTORY_DATA, iconFactoryData)
        _iconFactoryData.value = iconFactoryData
    }

    val junkCode = blockingSettings.getBoolean(JUNK_CODE, false)

    fun saveJunkCode(show: Boolean) {
        blockingSettings.putBoolean(JUNK_CODE, show)
    }
}