package database

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.DarkThemeConfig
import model.DestStoreSize
import model.DestStoreType
import model.UserData
import utils.getDownloadDirectory

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 19:36
 * @Description : 描述
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
    }

    @OptIn(ExperimentalSettingsApi::class)
    val userData = settings.getStringOrNullFlow(USER_DATA).map { string ->
        string?.let {
            Json.decodeFromString<UserData>(string)
        } ?: let {
            DEFAULT_USER_DATA
        }
    }

    @OptIn(ExperimentalSettingsApi::class)
    suspend fun saveData(userData: UserData) {
        val string = Json.encodeToString(userData)
        settings.putString(USER_DATA, string)
    }
}