package org.tool.kit.feature.junk.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.junk.JunkCodeRoute
import org.tool.kit.feature.junk.JunkCodeViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.junkCodeEntry(viewModel: JunkCodeViewModel) {
    entry<JunkCodeNavKey> {
        JunkCodeRoute(viewModel = viewModel)
    }
}