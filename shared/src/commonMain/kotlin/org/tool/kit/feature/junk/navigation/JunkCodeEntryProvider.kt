package org.tool.kit.feature.junk.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.junk.JunkCodeRoute

/**
 * @author      : LazyIonEs
 * @description : 垃圾代码生成页的导航条目注册，将导航键连接到 Route
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.junkCodeEntry() {
    entry<JunkCodeNavKey> {
        JunkCodeRoute()
    }
}
