package org.tool.kit.feature.iconfactory.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.iconfactory.IconFactoryRoute

/**
 * @author      : LazyIonEs
 * @description : 图标生成页的导航条目注册，将导航键连接到 Route
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.iconFactoryEntry() {
    entry<IconFactoryNavKey> {
        IconFactoryRoute()
    }
}
