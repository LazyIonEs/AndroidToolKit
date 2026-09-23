package org.tool.kit.feature.setting.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * @author      : LazyIonEs
 * @description : 设置页的可序列化导航键，包名参与保存状态恢复
 * @createDate  : 2026/1/20 16:52
 */
@Serializable
object SettingNavKey: NavKey