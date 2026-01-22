package org.tool.kit.feature.cleaner.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.cleaner.CleanerNavKey
import org.tool.kit.ui.ClearBuild
import org.tool.kit.vm.MainViewModel

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.cleanerEntry(viewModel: MainViewModel) {
    entry<CleanerNavKey> {
        ClearBuild(viewModel = viewModel)
    }
}