package org.tool.kit.feature.iconfactory.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.iconfactory.IconFactoryNavKey
import org.tool.kit.ui.IconFactory
import org.tool.kit.vm.MainViewModel

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.iconFactoryEntry(viewModel: MainViewModel) {
    entry<IconFactoryNavKey> {
        IconFactory(viewModel = viewModel)
    }
}