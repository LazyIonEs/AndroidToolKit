package org.tool.kit.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.JvmKeyStoreRepository
import org.tool.kit.data.repository.JvmStorageRepository
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.platform.createFlowSettings
import org.tool.kit.platform.DesktopFileSelection

@OptIn(ExperimentalSettingsApi::class)
fun desktopModules(
    settingsFactory: () -> FlowSettings = ::createFlowSettings,
    dispatchers: AppDispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main.immediate),
): List<Module> = listOf(coreModule(dispatchers, settingsFactory), desktopDataModule(), domainModule(), viewModelModule())

private fun desktopDataModule() = module {
    includes(dataModule())
    single<org.tool.kit.domain.repository.UpdateRepository> { org.tool.kit.data.repository.JvmUpdateRepository(get()) }
    single<org.tool.kit.feature.app.ClipboardWriter> { org.tool.kit.platform.JvmClipboardWriter(get<AppDispatchers>().main) }
    single<StorageRepository> { JvmStorageRepository(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.SignatureRepository> { org.tool.kit.data.repository.JvmSignatureRepository(get<AppDispatchers>().io) }
    single<KeyStoreRepository> { JvmKeyStoreRepository(get<AppDispatchers>().io) }
    single<org.tool.kit.feature.app.DesktopActionHandler> { org.tool.kit.platform.JvmDesktopActionHandler(get()) }
    single { DesktopFileSelection(get<AppDispatchers>().io) }
}
