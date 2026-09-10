package org.tool.kit.feature.cleaner.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.cleaner.CleanerRoute
import org.tool.kit.feature.cleaner.CleanerViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.cleanerEntry(viewModel: CleanerViewModel, signatureHasResult: Boolean, useDarkTheme: Boolean) {
    entry<CleanerNavKey> {
        CleanerRoute(viewModel, signatureHasResult, useDarkTheme)
    }
}