package org.tool.kit.feature.setting.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.tool.kit.feature.settings.*
import org.tool.kit.feature.update.UpdateViewModel
import org.tool.kit.feature.app.DesktopActionHandler

fun EntryProviderScope<NavKey>.settingEntry(viewModel: SettingsViewModel, updates: UpdateViewModel, actions: DesktopActionHandler) {
    entry<SettingNavKey> { SettingsRoute(viewModel, updates, actions) }
}
