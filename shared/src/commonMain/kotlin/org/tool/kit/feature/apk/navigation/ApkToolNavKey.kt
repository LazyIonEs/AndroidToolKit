package org.tool.kit.feature.apk.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * @author      : LazyIonEs
 * @description : APK 构建页的可序列化导航键，包名参与保存状态恢复
 * @createDate  : 2026/1/20 16:47
 */
@Serializable
object ApkToolNavKey : NavKey