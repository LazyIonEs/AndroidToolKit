package org.tool.kit.feature.junk.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.junk.JunkCodeNavKey
import org.tool.kit.ui.JunkCode
import org.tool.kit.vm.MainViewModel

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.junkCodeEntry(viewModel: MainViewModel) {
    entry<JunkCodeNavKey> {
        JunkCode(viewModel = viewModel)
    }
}