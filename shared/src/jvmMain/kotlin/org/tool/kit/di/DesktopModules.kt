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
    single<org.tool.kit.domain.repository.ImageProcessor> { org.tool.kit.data.source.JvmImageProcessor(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.IconOutputs> { org.tool.kit.data.source.JvmIconOutputs(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.ApkToolRepository> { org.tool.kit.data.source.JvmApkToolDataSource(org.tool.kit.constant.ConfigConstant.APKTOOL_FILE, get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.ApkBuildWorkspaces> { org.tool.kit.data.source.JvmApkBuildWorkspaces(java.io.File(System.getProperty("java.io.tmpdir")), get<AppDispatchers>().io) }
    single { org.tool.kit.feature.signature.SigningPresets(
        org.tool.kit.constant.ConfigConstant.APK.entries.map { org.tool.kit.feature.signature.SigningPreset(it.title, it.path) },
        org.tool.kit.constant.ConfigConstant.APK.All.path, org.tool.kit.constant.ConfigConstant.APK.Huawei.path) }
    single<org.tool.kit.domain.repository.ApkSigningRepository> { org.tool.kit.data.source.JvmApkSignerDataSource(get<AppDispatchers>().io) }
    single<org.tool.kit.core.process.ProcessRunner> { org.tool.kit.data.process.JvmProcessRunner(get<AppDispatchers>().io) }
    single { org.tool.kit.data.source.Aapt2Locator() }
    single { org.tool.kit.data.source.Aapt2DataSource(get(), get(), get<AppDispatchers>().io) }
    single { org.tool.kit.data.source.ApkIconDataSource(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.ApkInformationRepository> { org.tool.kit.data.repository.JvmApkInformationRepository(get(), get(), get<AppDispatchers>().io) }
    single<org.tool.kit.feature.apk.ApkIconDecoder> { org.tool.kit.platform.JvmApkIconDecoder(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.UpdateRepository> { org.tool.kit.data.repository.JvmUpdateRepository(get()) }
    single<org.tool.kit.feature.app.ClipboardWriter> { org.tool.kit.platform.JvmClipboardWriter(get<AppDispatchers>().main) }
    single<StorageRepository> { JvmStorageRepository(get<AppDispatchers>().io) }
    single<org.tool.kit.domain.repository.SignatureRepository> { org.tool.kit.data.repository.JvmSignatureRepository(get<AppDispatchers>().io) }
    single<KeyStoreRepository> { JvmKeyStoreRepository(get<AppDispatchers>().io) }
    single<org.tool.kit.feature.app.DesktopActionHandler> { org.tool.kit.platform.JvmDesktopActionHandler(get()) }
    single { DesktopFileSelection(get<AppDispatchers>().io) }
}
