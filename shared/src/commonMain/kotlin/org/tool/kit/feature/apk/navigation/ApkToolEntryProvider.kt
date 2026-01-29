package org.tool.kit.feature.apk.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.apk.ApkTool
import org.tool.kit.vm.MainViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.apkToolEntry(viewModel: MainViewModel) {
    entry<ApkToolNavKey> {
        ApkTool(viewModel = viewModel)
    }
}