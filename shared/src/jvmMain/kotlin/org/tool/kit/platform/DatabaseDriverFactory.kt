package org.tool.kit.platform

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 17:52
 * @Description : 创建基于系统 Preferences 的应用设置存储，读写在 IO 调度器执行
 * @Version     : 1.0
 */
@OptIn(ExperimentalSettingsApi::class)
fun createFlowSettings(): FlowSettings = PreferencesSettings.Factory().create("toolkit").toFlowSettings(Dispatchers.IO)