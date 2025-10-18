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
        private const val JUNK_CODE = "junk_code"
        private const val DEVELOPER_MODE = "developer_mode"
        private const val APK_TOOL = "apk_tool"
        private const val ALWAYS_SHOW_LABEL = "always_show_label"
        private const val HUAWEI_ALIGN_FILE_SIZE = "huawei_align_file_size"
        private const val SIGNATURE_GENERATION = "signature_generation"
        private const val ICON_FACTORY = "icon_factory"
        private const val CLEAR_BUILD = "clear_build"
        private const val START_CHECK_UPDATE = "start_check_update"
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

    val isShowJunkCode = settings.getBooleanFlow(JUNK_CODE, false)

    suspend fun saveJunkCode(show: Boolean) {
        settings.putBoolean(JUNK_CODE, show)
    }

    val isShowApktool = settings.getBooleanFlow(APK_TOOL, true)

    suspend fun saveApkTool(show: Boolean) {
        settings.putBoolean(APK_TOOL, show)
    }

    val isShowIconFactory = settings.getBooleanFlow(ICON_FACTORY, true)

    suspend fun saveIconFactory(show: Boolean) {
        settings.putBoolean(ICON_FACTORY, show)
    }

    val isShowSignatureGeneration = settings.getBooleanFlow(SIGNATURE_GENERATION, true)

    suspend fun saveSignatureGeneration(show: Boolean) {
        settings.putBoolean(SIGNATURE_GENERATION, show)
    }

    val isShowClearBuild = settings.getBooleanFlow(CLEAR_BUILD, true)

    suspend fun saveClearBuild(show: Boolean) {
        settings.putBoolean(CLEAR_BUILD, show)
    }

    val isAlwaysShowLabel = settings.getBooleanFlow(ALWAYS_SHOW_LABEL, false)

    suspend fun saveIsAlwaysShowLabel(show: Boolean) {
        settings.putBoolean(ALWAYS_SHOW_LABEL, show)
    }

    val isHuaweiAlignFileSize = settings.getBooleanFlow(HUAWEI_ALIGN_FILE_SIZE, true)

    suspend fun saveIsHuaweiAlignFileSize(show: Boolean) {
        settings.putBoolean(HUAWEI_ALIGN_FILE_SIZE, show)
    }

    val isEnableDeveloperMode = settings.getBooleanFlow(DEVELOPER_MODE, false)

    suspend fun saveDeveloperMode(show: Boolean) {
        settings.putBoolean(DEVELOPER_MODE, show)
    }

    val isStartCheckUpdate = settings.getBooleanFlow(START_CHECK_UPDATE, true)

    suspend fun saveStartCheckUpdate(show: Boolean) {
        settings.putBoolean(START_CHECK_UPDATE, show)
    }
}