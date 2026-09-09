package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.tool.kit.data.source.PreferencesDataSource

@OptIn(ExperimentalSettingsApi::class)
fun dataModule() = module {
    single { org.tool.kit.app.AppBootstrap(get(), get()) }
    single { PreferencesDataSource(get(), get<org.tool.kit.core.coroutine.AppDispatchers>().io) }
    single { org.tool.kit.data.repository.DefaultPreferencesRepository(get<PreferencesDataSource>(), get()) } onClose { it?.close() }
    single<org.tool.kit.domain.preferences.PreferencesRepository> { get<org.tool.kit.data.repository.DefaultPreferencesRepository>() }
}
