package database

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
class PreferencesDataSource @OptIn(ExperimentalSettingsApi::class) constructor(private val settings: FlowSettings) {

    companion object {
        private const val USER_DATA = "user_data"
        val DEFAULT_USER_DATA = UserData(
            darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
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
            quality = 85f
        )
    }

    @OptIn(ExperimentalSettingsApi::class)
    val userData = settings.getStringOrNullFlow(USER_DATA).map { string ->
        string?.let {
            Json.decodeFromString<UserData>(it)
        } ?: let {
            DEFAULT_USER_DATA
        }
    }

    @OptIn(ExperimentalSettingsApi::class)
    suspend fun saveUserData(userData: UserData) {
        val string = Json.encodeToString(userData)
        settings.putString(USER_DATA, string)
    }

    @OptIn(ExperimentalSettingsApi::class)
    val iconFactoryData = settings.getStringOrNullFlow(ICON_FACTORY_DATA).map { string ->
        string?.let {
            Json.decodeFromString<IconFactoryData>(it)
        } ?: let {
            DEFAULT_ICON_FACTORY_DATA
        }
    }

    @OptIn(ExperimentalSettingsApi::class)
    suspend fun saveIconFactoryData(iconFactoryData: IconFactoryData) {
        val string = Json.encodeToString(iconFactoryData)
        settings.putString(ICON_FACTORY_DATA, string)
    }
}