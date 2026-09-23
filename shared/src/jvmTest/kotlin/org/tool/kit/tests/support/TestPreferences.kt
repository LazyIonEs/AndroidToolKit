package org.tool.kit.tests.support

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.serialization.encodeValue
import java.io.File
import java.util.prefs.Preferences
import kotlinx.serialization.ExperimentalSerializationApi
import org.tool.kit.model.DestStoreSize
import org.tool.kit.model.DestStoreType
import org.tool.kit.model.UserData

@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
internal fun prepareTestPreferences(fixtureRoot: File, theme: String) {
    check(System.getProperty("java.util.prefs.PreferencesFactory") == IsolatedPreferencesFactory::class.java.name)
    fixtureRoot.mkdirs()
    val preferences = PreferencesSettings(Preferences.userRoot().node("toolkit"))
    preferences.clear()
    preferences.encodeValue(UserData.serializer(), "user_data", UserData(
        fixtureRoot.resolve("output").apply { mkdirs() }.path, true, "-sign", true,
        DestStoreType.JKS, DestStoreSize.TWO_THOUSAND_FORTY_EIGHT
    ))
    preferences.putBoolean("start_check_update", false)
    preferences.putBoolean("junk_code", true)
    preferences.putBoolean("always_show_label", true)
    preferences.putString("theme_config", theme)
}
