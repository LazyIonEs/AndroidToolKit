package org.tool.kit.feature.iconfactory.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.iconfactory.IconFactoryRoute
import org.tool.kit.feature.iconfactory.IconFactoryViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.iconFactoryEntry(viewModel: IconFactoryViewModel) {
    entry<IconFactoryNavKey> {
        IconFactoryRoute(viewModel = viewModel)
    }
}