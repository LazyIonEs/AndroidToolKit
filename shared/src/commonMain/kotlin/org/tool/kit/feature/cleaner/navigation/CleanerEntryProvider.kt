package org.tool.kit.feature.cleaner.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.cleaner.CleanerRoute

/**
 * @author      : LazyIonEs
 * @description : 缓存清理页的导航条目注册，将导航键连接到 Route
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.cleanerEntry() {
    entry<CleanerNavKey> {
        CleanerRoute()
    }
}
