package org.tool.kit.migration

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import org.tool.kit.App
import org.tool.kit.WindowIcon
import org.tool.kit.model.DestStoreSize
import org.tool.kit.model.DestStoreType
import org.tool.kit.model.UserData
import java.io.File
import java.util.prefs.Preferences
import org.koin.core.context.startKoin
import org.tool.kit.app.shutdownAppSession
import org.tool.kit.di.desktopModules

/** Run with :shared:baselineDesktop. No changes to the production entry point or Window parameters. */
@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
fun main() {
    check(System.getProperty("java.util.prefs.PreferencesFactory") == IsolatedPreferencesFactory::class.java.name)
    val fixtureRoot = File(checkNotNull(System.getProperty("migration.fixtureRoot"))).canonicalFile
    prepareBaselinePreferences(fixtureRoot, System.getProperty("migration.theme", "LIGHT"))
    startKoin { modules(desktopModules()) }
    try {
        application {
            Window(onCloseRequest = { shutdownAppSession(); exitApplication() }, title = "AndroidToolKit", icon = WindowIcon()) {
                App()
            }
        }
    } finally {
        shutdownAppSession()
    }
}

@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
internal fun prepareBaselinePreferences(fixtureRoot: File, theme: String) {
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
