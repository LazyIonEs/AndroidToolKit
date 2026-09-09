package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.platform.createFlowSettings

@OptIn(ExperimentalSettingsApi::class)
fun desktopModules(
    settingsFactory: () -> FlowSettings = ::createFlowSettings,
    dispatchers: AppDispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main.immediate),
): List<Module> = listOf(coreModule(dispatchers, settingsFactory), dataModule(), domainModule(), viewModelModule())
