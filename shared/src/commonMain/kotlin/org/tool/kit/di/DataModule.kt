package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.tool.kit.data.source.PreferencesStorage

/** 绑定启动准备与设置仓库，接口和实现共享同一实例及写入队列。 */
@OptIn(ExperimentalSettingsApi::class)
fun dataModule() = module {
    single<org.tool.kit.domain.repository.CleanerRulesRepository> { org.tool.kit.data.repository.DefaultCleanerRulesRepository(get()) }
    single { org.tool.kit.app.AppBootstrap(get(), get()) }
    single { org.tool.kit.data.repository.DefaultPreferencesRepository(get<PreferencesStorage>(), get()) } onClose { it?.close() }
    single<org.tool.kit.domain.preferences.PreferencesRepository> { get<org.tool.kit.data.repository.DefaultPreferencesRepository>() }
}
