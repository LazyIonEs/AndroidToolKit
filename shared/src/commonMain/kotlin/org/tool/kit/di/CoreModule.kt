package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import org.koin.dsl.module
import org.tool.kit.core.coroutine.AppDispatchers

@OptIn(ExperimentalSettingsApi::class)
fun coreModule(dispatchers: AppDispatchers, settingsFactory: () -> FlowSettings) = module {
    single { dispatchers }
    single<FlowSettings> { settingsFactory() }
}
