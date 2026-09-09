package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import org.koin.dsl.module
import org.tool.kit.data.source.PreferencesDataSource

@OptIn(ExperimentalSettingsApi::class)
fun dataModule() = module {
    single { PreferencesDataSource(get()) }
}
