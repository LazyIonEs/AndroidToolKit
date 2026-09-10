package org.tool.kit.feature.cleaner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * @author      : LazyIonEs
 * @description : 缓存清理页的可序列化导航键，包名参与保存状态恢复
 * @createDate  : 2026/1/20 16:49
 */
@Serializable
object CleanerNavKey: NavKey